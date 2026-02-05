# Threading Fix Explanation

## The EDT Violation Error

### What Happened
When typing `?` to trigger folding updates, the code was calling:
```java
foldingManager.buildInitialFoldings(editor);
```

From the EDT (Event Dispatch Thread), which caused:
```
com.intellij.openapi.diagnostic.RuntimeExceptionWithAttachments: 
Access from Event Dispatch Thread (EDT) is not allowed
```

### Why It Failed
IntelliJ's threading model has strict rules:
- **EDT**: Safe for UI updates, fold region state changes, editor operations
- **Background Threads**: Required for file I/O, parsing, building fold regions
- `buildInitialFoldings()` is a background operation that cannot run on EDT

### The Solution
Instead of using `buildInitialFoldings()`, we now use:

1. **Daemon Restart** (triggers background analysis):
```java
DaemonCodeAnalyzer.getInstance(project).restart(file);
```
This runs on a background thread and rebuilds the folding provider's descriptors.

2. **EDT-Safe Updates** (multiple calls to update the UI):
```java
foldingManager.updateFoldRegions(editor);
```
This runs on EDT and refreshes the fold regions based on the new descriptors.

## Updated Flow

```
1. User types '?'
   ↓
2. TypedHandler removes '?' and adds @Nullable annotation
   ↓
3. PSI is committed (background thread)
   ↓
4. triggerFoldingUpdate() is called
   ↓
5. invokeLater() schedules EDT work:
   - DaemonCodeAnalyzer.restart(file)  [Background: rebuilds descriptors]
   - updateFoldRegions(editor)          [EDT: applies new descriptors]
   ↓
6. After 200ms (alarm):
   - updateFoldRegions(editor)          [EDT: refresh again]
   - Collapse all fold regions          [EDT: UI updates]
```

## Why This Works Better

1. **Thread-Safe**: All EDT operations happen on EDT
2. **Complete Rebuild**: Daemon restart forces `buildFoldRegions()` to be called in background
3. **Guaranteed Update**: Double update ensures fold regions reflect current PSI state
4. **Stale Region Removal**: New descriptors replace old ones automatically

## Technical Details

### Method Responsibilities
- `DaemonCodeAnalyzer.restart()`: Triggers async background analysis → calls `buildFoldRegions()` in background
- `CodeFoldingManager.updateFoldRegions()`: Synchronously updates fold regions on EDT based on current descriptors
- `FoldRegion.setExpanded()`: Collapses/expands regions on EDT

### Why Multiple Updates
1. **First `updateFoldRegions()`**: Applies initial rebuild from daemon
2. **Second `updateFoldRegions()` (delayed)**: Ensures any stale regions are removed
3. **Fold Collapsing**: Collapses newly created regions

## Key Insight
The folding system automatically:
- Calls `buildFoldRegions()` on background threads (via daemon)
- Updates the UI on EDT (via `updateFoldRegions()`)

We just need to:
1. Trigger the rebuild (`DaemonCodeAnalyzer.restart()`)
2. Request UI updates (`updateFoldRegions()`)
3. Configure the UI state (collapse regions)

This respects IntelliJ's threading model while ensuring reliable fold region updates.
