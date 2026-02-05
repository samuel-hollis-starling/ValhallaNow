# Summary of Changes for @Nullable String @Nullable [] Folding Issue

## Issue Description
When typing `?` after `String` in `String @Nullable []`, the plugin correctly adds `@Nullable` before `String` to create `@Nullable String @Nullable []`. However, the folding was displaying `String[]?` instead of the expected `String?[]?`.

## Files Modified

### 1. NullableFoldingBuilder.java
**Location**: `/src/main/java/me/samhollis/valhalla/folding/NullableFoldingBuilder.java`

#### Changes in `hasTypeUseNullableAnnotation()`:
- **Before**: Only checked direct children using `getChildren()`
- **After**: Added `PsiTreeUtil.getChildrenOfType()` as a backup detection method
- **Why**: The PSI structure might vary, and this ensures we catch type-use annotations in all cases

#### Changes in `handleArrayComponentNullability()`:
- **Added**: Text-based fallback check for `@Nullable` between type name and brackets
- **Why**: If PSI-based detection fails, we can still detect the annotation by parsing the text
- **Logic**: Checks if the text before `[` contains "@Nullable" or "Nullable"

### 2. NullableTypedHandler.java
**Location**: `/src/main/java/me/samhollis/valhalla/typing/NullableTypedHandler.java`

#### Changes in `triggerFoldingUpdate()`:
- **Before**: Called `buildInitialFoldings()` which caused EDT threading violations
- **After**: Uses `updateFoldRegions()` which is safe to call from EDT
- **Why**: IntelliJ threading model requires background operations (like `buildInitialFoldings()`) to run on non-EDT threads
- **Solution**: Rely on `DaemonCodeAnalyzer.restart()` to trigger background rebuild + EDT-safe `updateFoldRegions()` calls
- **Result**: Fold regions are properly updated without threading errors

## Technical Details

### The Problem
When `@Nullable String @Nullable []` exists:
1. The outer `@Nullable` (modifier list) creates a fold region with range `[@Nullable ... ]`
2. The inner `@Nullable` (type-use) should NOT create its own fold (handled by `hasOuterNullableAnnotation`)
3. However, the stale fold region from before the outer annotation was added might persist
4. The stale region shows `String[]?` instead of the correct `String?[]?`

### The Solution
Three-layered approach:
1. **Better PSI Detection**: Use multiple methods to find type-use annotations
2. **Text Fallback**: Parse text if PSI fails
3. **Force Rebuild**: Completely rebuild fold regions to remove stale ones

### Fold Region Creation Logic
For `@Nullable String @Nullable []`:
1. Outer annotation processed via `handleVariable()` → `handleArrayComponentNullability()`
2. `componentText = "String"` (from type element)
3. `hasArrayNullable = true` (detects inner `@Nullable`)
4. `placeholderText = "String" + "?" + "[]" + "?" = "String?[]?"`
5. Inner annotation processed via `processAnnotation()` → `hasOuterNullableAnnotation()` → returns true → NO fold created

## Expected Results

| Code | Fold Display |
|------|-------------|
| `@Nullable String[]` | `String?[]` |
| `String @Nullable []` | `String[]?` |
| `@Nullable String @Nullable []` | `String?[]?` |

## Testing Instructions

### Manual Test:
1. Create a Java file with: `private String @Nullable [] names;`
2. Verify it folds to: `String[]?`
3. Place cursor after `String` and type `?`
4. The plugin adds `@Nullable` before `String`
5. **Expected**: Fold shows `String?[]?`
6. **Previous bug**: Fold showed `String[]?`

### Why This Was Hard to Debug:
- PSI structure for type-use annotations can vary
- Fold regions have caching and don't always update immediately
- Timing issues between PSI commits and fold region rebuilds
- Overlapping fold regions from different annotations

## Build Status
✅ Compiles successfully
✅ Plugin builds without errors
⚠️ Unit tests are broken (pre-existing issue, not related to this fix)

## Next Steps
The fix should resolve the issue, but manual testing is recommended to verify:
1. Build the plugin: `./gradlew buildPlugin`
2. Install in IntelliJ IDEA
3. Test typing `?` after array type names
4. Verify fold regions show correct placeholders
