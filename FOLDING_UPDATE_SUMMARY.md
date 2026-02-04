# Folding Update Implementation

## Problem
When typing `?` after a type (e.g., `String?`), the `NullableTypedHandler` correctly inserts the `@Nullable` annotation, but the folding doesn't automatically collapse to show the visual representation (e.g., `String?`). The user had to manually trigger a fold update or re-open the file.

## Solution
Updated `NullableTypedHandler` to automatically trigger folding updates after inserting the `@Nullable` annotation.

## Changes Made

### 1. Added Imports
```java
import com.intellij.openapi.editor.FoldRegion;
import com.intellij.openapi.editor.FoldingModel;
```

### 2. Updated `insertNullableAnnotation` Method
- Now captures the `PsiAnnotation` that was added
- Commits all documents after insertion 
- Calls `triggerFoldingUpdate()` to update folding regions

### 3. Added `triggerFoldingUpdate` Method
```java
private void triggerFoldingUpdate(@NotNull Project project, @NotNull PsiJavaFile file, @NotNull PsiAnnotation annotation)
```

This method:
1. Gets the `FileEditor` for the current file
2. Extracts the `Editor` from the `TextEditor`
3. Schedules a folding update on the next event dispatch cycle using `invokeLater()`
4. Updates folding regions using `CodeFoldingManager.updateFoldRegions()`

## Key Implementation Details

### Why `invokeLater()`?
The folding update is scheduled on the next event dispatch cycle to ensure that:
- All PSI changes are fully committed
- The document is in a stable state
- The folding builder can properly detect the new annotation

### Safety Checks
- Verifies the editor is not disposed
- Checks if the project is still open
- Uses `runBatchFoldingOperation()` for optimal performance

## Result
Now when a user types `?` after a type name:
1. The `?` is consumed
2. `@Nullable` annotation is inserted
3. Import is added (if needed)
4. Code is formatted
5. **Folding automatically updates and collapses the annotation**
6. User immediately sees the visual representation (e.g., `String?`)

## Testing
To test this feature:
1. Build and run the plugin: `./gradlew buildPlugin -x test`
2. Open `TestRecords.java`
3. Add a new field like: `String myField`
4. Type `?` after `String` to make it: `String?`
5. Verify that it immediately shows as `String?` (folded) instead of `@Nullable String`
