# Folding Update Fix

## Problem
After typing `?` to add a `@Nullable` annotation, the folding was not being triggered automatically to display the Valhalla-style syntax (e.g., `String?`).

## Root Causes Identified

### 1. Alarm Garbage Collection Issue
The `Alarm` object was being created inside a loop and going out of scope, which could cause it to be garbage collected before the delayed task executed.

### 2. PSI Commit Timing
The folding update was being triggered before the PSI was fully committed, causing the folding builder to not detect the new annotation.

### 3. Single Editor Update
The original implementation only updated the first editor found, not all open editors for the file.

### 4. Incorrect FoldingDescriptor Constructor
The folding descriptors were created without explicitly setting the `collapsed` flag and `dependencies` parameter.

### 5. getPlaceholderText Override Issue
The `getPlaceholderText` method in `NullableFoldingBuilder` was returning `"?"` instead of `null`, which prevented the placeholder text set in the `FoldingDescriptor` constructor from being used.

## Solutions Applied

### 1. Updated NullableTypedHandler.insertNullableAnnotation()
**File**: `src/main/java/me/samhollis/valhalla/typing/NullableTypedHandler.java`

Changes:
- Use `PsiDocumentManager.performWhenAllCommitted()` to ensure PSI is fully synchronized before triggering folding update
- This guarantees that the annotation is fully committed and visible to the folding builder

```java
// Commit all changes and wait for PSI synchronization
PsiDocumentManager psiDocumentManager = PsiDocumentManager.getInstance(project);
psiDocumentManager.commitAllDocuments();
psiDocumentManager.performWhenAllCommitted(() -> {
    // Trigger folding update after PSI is fully committed
    triggerFoldingUpdate(project, file, addedAnnotation);
});
```

### 2. Fixed triggerFoldingUpdate() Method
**File**: `src/main/java/me/samhollis/valhalla/typing/NullableTypedHandler.java`

Changes:
- Create a single `Alarm` object scoped to the project (not local to prevent garbage collection)
- Collect **all** text editors for the file instead of just the first one
- Update folding for each editor individually
- Added a delayed second update (200ms) to ensure PSI changes are fully committed
- Use `invokeLater()` for immediate update on EDT

```java
private void triggerFoldingUpdate(@NotNull Project project, @NotNull PsiJavaFile file, @NotNull PsiAnnotation annotation) {
    // Collect all text editors
    java.util.List<Editor> editors = new java.util.ArrayList<>();
    for (com.intellij.openapi.fileEditor.FileEditor fileEditor : fileEditors) {
        if (fileEditor instanceof com.intellij.openapi.fileEditor.TextEditor textEditor) {
            editors.add(textEditor.getEditor());
        }
    }
    
    // Create a single Alarm scoped to the project
    com.intellij.util.Alarm alarm = new com.intellij.util.Alarm(project);
    
    // Schedule immediate folding update
    ApplicationManager.getApplication().invokeLater(() -> {
        if (!project.isDisposed()) {
            CodeFoldingManager foldingManager = 
                CodeFoldingManager.getInstance(project);
                
            for (Editor editor : editors) {
                if (!editor.isDisposed()) {
                    FoldingModel foldingModel = editor.getFoldingModel();
                    foldingModel.runBatchFoldingOperation(() -> {
                        foldingManager.updateFoldRegions(editor);
                    });
                }
            }
            
            // Delayed update (200ms) for reliability
            alarm.addRequest(() -> {
                if (!project.isDisposed()) {
                    for (Editor editor : editors) {
                        if (!editor.isDisposed()) {
                            foldingModel.runBatchFoldingOperation(() -> {
                                foldingManager.updateFoldRegions(editor);
                            });
                        }
                    }
                }
            }, 200);
        }
    });
}
```

### 3. Fixed FoldingDescriptor Constructor Calls
**File**: `src/main/java/me/samhollis/valhalla/folding/NullableFoldingBuilder.java`

Changes in `handleTypeNullability()` and `handleArrayComponentNullability()`:
- Use the full constructor: `new FoldingDescriptor(node, range, group, placeholderText, collapsed, dependencies)`
- Set `collapsed` to `true` to ensure automatic collapsing
- Pass `java.util.Collections.emptySet()` for dependencies

Before:
```java
descriptors.add(new FoldingDescriptor(
    annotation.getNode(),
    foldingRange,
    null,
    placeholderText
));
```

After:
```java
descriptors.add(new FoldingDescriptor(
    annotation.getNode(),
    foldingRange,
    null,
    placeholderText,
    true, // collapsed by default
    java.util.Collections.emptySet()
));
```

### 4. Fixed getPlaceholderText() Override
**File**: `src/main/java/me/samhollis/valhalla/folding/NullableFoldingBuilder.java`

Changed from returning `"?"` to returning `null` so the placeholder text from the `FoldingDescriptor` is used:

Before:
```java
@Override
public String getPlaceholderText(@NotNull ASTNode node) {
    return "?";
}
```

After:
```java
@Override
public String getPlaceholderText(@NotNull ASTNode node) {
    // Placeholder text is provided in the FoldingDescriptor
    return null;
}
```

## Expected Behavior After Fix

1. User types `String` in a parameter
2. User types `?` after `String`
3. Plugin immediately:
   - Removes the `?` character
   - Adds `@Nullable` annotation before `String`
   - Adds `import org.jspecify.annotations.Nullable;`
   - Waits for PSI to be fully committed
   - **Triggers folding update for all open editors**
   - Displays as `String?` (with the annotation folded)

## Testing

To test:
1. Run the plugin with `./gradlew runIde`
2. Create a new Java file or method
3. Type a parameter: `public void myMethod(String s)`
4. Position cursor after `String` and type `?`
5. Verify that it immediately displays as `String?` (not `@Nullable String`)
6. Unfold to verify the annotation is actually `@Nullable String`

## Technical Notes

- The dual update strategy (immediate + 200ms delayed) ensures folding works even if PSI commits take slightly longer
- All open editor windows for the same file will have folding updated
- The `Alarm` class is project-scoped to prevent premature garbage collection
- `performWhenAllCommitted()` ensures the annotation is fully visible to the folding builder before updating
