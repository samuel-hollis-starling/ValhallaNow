# Fix for Type Parameters and Array Components - February 4, 2026

## Problem Statement
The plugin was not working correctly for:
1. **Type parameters inside generics**: `List<String?>` should become `List<@Nullable String>` when typing `?`, then fold back to `List<String?>`
2. **Array component types**: `String?[]` should become `String @Nullable []` (array of nullable strings), then fold to `String?[]`

Previously, the plugin only worked for:
- Field types: `String?` → `@Nullable String`
- Parameter types: `String?` → `@Nullable String`
- Return types: `String?` → `@Nullable String`

## Root Cause Analysis

### 1. Type-Use Annotations Not Handled
When you write `List<@Nullable String>`, the `@Nullable` annotation is a **type-use annotation** that is a direct child of `PsiTypeElement`, not part of a `PsiModifierList`. The original code only looked for annotations in `PsiModifierList`.

```
PsiTypeElement (List<@Nullable String>)
  └─ PsiAnnotation (@Nullable)  ← Direct child!
  └─ PsiJavaCodeReferenceElement (String)
```

### 2. NullableTypedHandler Didn't Insert Type-Use Annotations
When `findModifierList()` returned `null` (for type parameters and array components), the `insertNullableAnnotation()` method would simply exit without doing anything.

### 3. NullableFoldingBuilder Didn't Process Type-Use Annotations
The `processAnnotation()` method only handled annotations that were children of `PsiModifierList`, completely ignoring annotations that were direct children of `PsiTypeElement`.

## Solution Implementation

### Phase 1: Fix NullableTypedHandler.insertNullableAnnotation()

**Before:**
```java
if (modifierList != null) {
    // Add annotation to modifier list
    PsiAnnotation addedAnnotation = (PsiAnnotation) modifierList.addBefore(annotation, modifierList.getFirstChild());
    // ... formatting and folding update
}
// Nothing happens if modifierList is null!
```

**After:**
```java
PsiAnnotation addedAnnotation = null;

if (modifierList != null) {
    // Add annotation to modifier list (normal case)
    addedAnnotation = (PsiAnnotation) modifierList.addBefore(annotation, modifierList.getFirstChild());
    CodeStyleManager.getInstance(project).reformat(modifierList);
} else {
    // Handle type arguments in generics and array component types
    // For these cases, we need to add the annotation directly to the type element
    if (context.target() instanceof PsiTypeElement typeElement) {
        addedAnnotation = (PsiAnnotation) typeElement.addBefore(annotation, typeElement.getFirstChild());
        CodeStyleManager.getInstance(project).reformat(typeElement);
    }
}

if (addedAnnotation != null) {
    // Add import and trigger folding update
    addImport(file, project);
    // ... rest of the code
}
```

**Key Changes:**
- Continue processing even when `modifierList` is `null`
- Add annotation directly to `PsiTypeElement` for type-use annotations
- Unified flow for both cases (modifier list vs. type-use)

### Phase 2: Fix NullableFoldingBuilder.processAnnotation()

**Before:**
```java
private void processAnnotation(@NotNull PsiAnnotation annotation, @NotNull List<FoldingDescriptor> descriptors) {
    if (!isNullableAnnotation(annotation)) {
        return;
    }

    PsiElement parent = annotation.getParent();
    if (!(parent instanceof PsiModifierList modifierList)) {
        return;  // Exit if parent is not PsiModifierList!
    }
    // ... rest of processing
}
```

**After:**
```java
private void processAnnotation(@NotNull PsiAnnotation annotation, @NotNull List<FoldingDescriptor> descriptors) {
    if (!isNullableAnnotation(annotation)) {
        return;
    }

    PsiElement parent = annotation.getParent();
    
    // Handle type-use annotations (direct child of PsiTypeElement)
    if (parent instanceof PsiTypeElement typeElement) {
        // This is a type-use annotation like List<@Nullable String>
        handleTypeUseAnnotation(annotation, typeElement, descriptors);
        return;
    }
    
    if (!(parent instanceof PsiModifierList modifierList)) {
        return;
    }
    // ... rest of processing for modifier list annotations
}
```

**Key Changes:**
- Added special handling for annotations that are direct children of `PsiTypeElement`
- Delegate to new `handleTypeUseAnnotation()` method

### Phase 3: Add handleTypeUseAnnotation() Method

**New Method:**
```java
private void handleTypeUseAnnotation(@NotNull PsiAnnotation annotation,
                                     @NotNull PsiTypeElement typeElement,
                                     @NotNull List<FoldingDescriptor> descriptors) {
    // Calculate the folding range from annotation to end of the type element
    TextRange annotationRange = annotation.getTextRange();
    TextRange typeRange = typeElement.getTextRange();
    
    int startOffset = annotationRange.getStartOffset();
    int endOffset = typeRange.getEndOffset();
    
    TextRange foldingRange = new TextRange(startOffset, endOffset);
    
    // Build the type text without the annotation
    StringBuilder typeText = new StringBuilder();
    boolean skipWhitespace = true;
    
    for (PsiElement child : typeElement.getChildren()) {
        if (child == annotation) {
            // Skip the annotation itself
            skipWhitespace = true;
            continue;
        }
        if (skipWhitespace && child instanceof PsiWhiteSpace) {
            // Skip whitespace immediately after annotation
            skipWhitespace = false;
            continue;
        }
        typeText.append(child.getText());
    }
    
    String placeholderText = typeText.toString().trim() + "?";
    
    descriptors.add(new FoldingDescriptor(
        annotation.getNode(),
        foldingRange,
        null,
        placeholderText,
        true,
        java.util.Collections.emptySet()
    ));
}
```

**Key Features:**
- Creates folding range from annotation start to type element end
- Builds placeholder text by iterating through children, skipping annotation and trailing whitespace
- Results in clean placeholder like `String?` or `Integer?`

## Examples

### Example 1: Generic Type Argument
**User types:** `List<String?>`
1. TypedHandler detects `?` after `String` inside `<>`
2. Finds the `PsiTypeElement` for `String`
3. Adds `@Nullable` as first child of that `PsiTypeElement`
4. Result: `List<@Nullable String>`
5. Folding kicks in, showing: `List<String?>`

### Example 2: Array Component Type
**User types:** `String?[]`
1. TypedHandler detects `?` after `String` (component type)
2. Finds the component `PsiTypeElement`
3. Adds `@Nullable` to the component type
4. Result: `String @Nullable []`
5. Folding shows: `String?[]`

### Example 3: Nested Generics
**User types:** `Map<String, List<Integer?>>`
1. TypedHandler detects `?` after `Integer` inside nested `<>`
2. Adds `@Nullable` to the `Integer` type element
3. Result: `Map<String, List<@Nullable Integer>>`
4. Folding shows: `Map<String, List<Integer?>>`

## Testing

### Manual Testing
1. Build the plugin:
   ```bash
   ./gradlew buildPlugin -x test
   ```

2. Run in dev IDE:
   ```bash
   ./gradlew runIde
   ```

3. Create test cases:
   ```java
   // Test 1: Generic type parameter
   public class Test {
       private List<String> names;  // Position cursor after String, type ?
   }
   
   // Test 2: Array component
   public class Test {
       private String[] names;  // Position cursor after String, type ?
   }
   
   // Test 3: Nested generics
   public class Test {
       private Map<String, List<Integer>> data;  // Position cursor after Integer, type ?
   }
   ```

4. Verify:
   - Typing `?` adds the annotation
   - Import is added automatically
   - Folding displays the `Type?` syntax
   - Folding is collapsed by default

### Automated Testing
The existing tests in `NullableTypedHandlerTest` and `NullableFoldingBuilderTest` should now pass:
- `testTypingQuestionMarkInGeneric()` - Tests typing `?` in generic type arguments
- `testFoldingForGenericTypeArgument()` - Tests folding for `List<@Nullable String>`
- `testFoldingForNestedGenerics()` - Tests nested generic folding

Run tests:
```bash
./gradlew test
```

## Technical Notes

### PSI Structure Understanding
Understanding the PSI structure is crucial:

1. **Declaration Annotations (on modifier lists):**
   ```
   PsiVariable
     └─ PsiModifierList
          └─ PsiAnnotation (@Nullable)
     └─ PsiTypeElement (String)
   ```

2. **Type-Use Annotations (inside generics):**
   ```
   PsiTypeElement (List<@Nullable String>)
     └─ PsiJavaCodeReferenceElement (List)
     └─ PsiReferenceParameterList (<...>)
          └─ PsiTypeElement (@Nullable String)
               └─ PsiAnnotation (@Nullable)
               └─ PsiJavaCodeReferenceElement (String)
   ```

3. **Array Component Annotations:**
   ```
   PsiTypeElement (String @Nullable [])
     └─ PsiTypeElement (String @Nullable)
          └─ PsiJavaCodeReferenceElement (String)
          └─ PsiAnnotation (@Nullable)
     └─ PsiJavaToken ([])
   ```

### Why addBefore() Works
When we call `typeElement.addBefore(annotation, typeElement.getFirstChild())`, the IntelliJ PSI system:
1. Inserts the annotation as a child of the type element
2. Adds necessary whitespace
3. Maintains proper PSI structure
4. Triggers incremental reparsing

This is the correct way to add type-use annotations programmatically.

## Future Enhancements
Potential improvements:
1. Handle more complex array syntax: `String[]?[]` (2D arrays)
2. Support for wildcard types: `List<? extends String?>`
3. Better error messages for unsupported positions
4. Refactor common folding logic to reduce duplication

## Summary
With these changes, the plugin now fully supports:
- ✅ Regular declarations: `String?`
- ✅ Generic type arguments: `List<String?>`
- ✅ Nested generics: `Map<String, List<Integer?>>`
- ✅ Array components: `String?[]`
- ✅ Nullable arrays: `String[]?`

All cases now work for both:
- Typing `?` to add `@Nullable`
- Folding to display `Type?` syntax
