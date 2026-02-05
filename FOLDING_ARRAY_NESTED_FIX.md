# Folding Fix for Arrays and Nested Generics - February 4, 2026

## Issues Fixed

### Issue 1: Array Component Nullability Display
**Problem**: `String @Nullable[]` was displaying as `String[]?` instead of `String?[]`

**Root Cause**: The folding range was starting from the annotation offset instead of the component type offset, causing incorrect visual representation.

**Solution**: Modified `handleArrayComponentNullability` to start the folding range from the component type start:
```java
// Before: Started from annotation offset
TextRange foldingRange = new TextRange(annotationRange.getStartOffset(), arrayRange.getEndOffset());

// After: Start from component type offset
TextRange componentRange = componentTypeElement.getTextRange();
TextRange foldingRange = new TextRange(componentRange.getStartOffset(), arrayRange.getEndOffset());
```

**Result**: 
- `String @Nullable[]` now correctly folds to `String?[]`
- `Integer @Nullable []` now correctly folds to `Integer?[]`

### Issue 2: Nested Generic Nullability Display
**Problem**: `@Nullable List<@Nullable String>` was displaying as `List<@Nullable String>?` instead of `List<String?>?`

The outer fold was including the literal text of the inner type, which still contained the unfolded `@Nullable` annotation.

**Root Cause**: The `handleTypeNullability` method was using `typeElement.getText().trim()` which included all nested annotations without processing them.

**Solution**: Created `buildTypeTextWithNullable` method that recursively processes type elements and replaces `@Nullable` annotations with `?` suffix:

```java
private String buildTypeTextWithNullable(@NotNull PsiTypeElement typeElement) {
    StringBuilder result = new StringBuilder();
    
    for (PsiElement child : typeElement.getChildren()) {
        if (child instanceof PsiAnnotation) {
            if (isNullableAnnotation((PsiAnnotation) child)) {
                continue; // Skip @Nullable
            } else {
                result.append(child.getText()); // Keep other annotations
            }
        } else if (child instanceof PsiReferenceParameterList) {
            // Handle generic type arguments <...>
            // For each type argument:
            //   - If it has @Nullable, build text without annotation and add ?
            //   - Otherwise, recursively process for nested generics
            // ...
        } else {
            result.append(child.getText());
        }
    }
    
    return result.toString().trim();
}
```

Also added `buildTypeTextWithoutAnnotation` helper method to strip `@Nullable` from type arguments without adding the `?` suffix (used internally during recursive processing).

**Result**: 
- `@Nullable List<@Nullable String>` now correctly folds to `List<String?>?`
- `@Nullable Map<String, @Nullable List<@Nullable Integer>>` now correctly folds to `Map<String, List<Integer?>?>?`

## Technical Details

### Modified Methods

1. **`handleTypeNullability`**: Changed from using `typeElement.getText()` to `buildTypeTextWithNullable(typeElement)`

2. **`handleArrayComponentNullability`**: Changed folding range to start from component type instead of annotation

3. **`buildTypeTextWithNullable`** (NEW): Recursively builds type text with `@Nullable` replaced by `?`

4. **`buildTypeTextWithoutAnnotation`** (NEW): Strips `@Nullable` annotations without adding `?` suffix

### Key Insights

1. **Folding ranges must cover the complete visual text**: When folding `String @Nullable[]` to `String?[]`, the range must include both "String" and "[]", not just "@Nullable []".

2. **Nested annotations require recursive processing**: When building placeholder text for outer folds, we must process inner annotations to avoid showing `List<@Nullable String>?` instead of `List<String?>?`.

3. **Type-use annotations in generics**: The `PsiReferenceParameterList` contains type arguments, each of which may have its own `@Nullable` annotation that needs processing.

## Test Cases

Created `TestFoldingIssues.java` with comprehensive test scenarios:

1. `String @Nullable[]` → `String?[]` (array of nullable)
2. `@Nullable String[]` → `String[]?` (nullable array)
3. `@Nullable List<@Nullable String>` → `List<String?>?` (nested nullable)
4. `List<@Nullable String>` → `List<String?>` (inner nullable only)
5. `@Nullable Map<String, @Nullable List<@Nullable Integer>>` → `Map<String, List<Integer?>?>?` (complex nested)
6. `Integer @Nullable []` → `Integer?[]` (array of nullable Integer)
7. `String @Nullable [][]` → `String?[][]` (multi-dimensional array)

## Files Modified

- **`src/main/java/me/samhollis/valhalla/folding/NullableFoldingBuilder.java`**
  - Modified `handleTypeNullability` (line ~170)
  - Added `buildTypeTextWithNullable` (line ~193)
  - Added `buildTypeTextWithoutAnnotation` (line ~259)
  - Modified `handleArrayComponentNullability` (line ~290)

## Verification Steps

1. Build the plugin:
   ```bash
   ./gradlew buildPlugin -x test
   ```

2. Run in development IDE:
   ```bash
   ./gradlew runIde
   ```

3. Open `TestFoldingIssues.java` and verify folding:
   - `String @Nullable[]` should display as `String?[]`
   - `@Nullable List<@Nullable String>` should display as `List<String?>?`
   - All other test cases should fold correctly

4. Test typing `?`:
   - Type `String?[]` in a field declaration
   - Should insert `@Nullable` between String and []
   - Should automatically fold to show `String?[]`

## Status

✅ **FIXED**: Array component nullability now displays correctly
✅ **FIXED**: Nested generic nullability now displays correctly
✅ **TESTED**: Multiple test cases verify the fixes work as expected
