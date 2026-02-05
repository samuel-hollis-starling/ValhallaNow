# Fix Summary: Array and Nested Nullable Folding Issues

## Date: February 4, 2026

## Issues Reported

1. **@Nullable String[] was folding incorrectly** - Should fold to String?[] (array of nullable elements)
2. **String @Nullable[] was folding incorrectly** - Should fold to String[]? (nullable array)
3. **@Nullable List<@Nullable String> was showing List<@Nullable String>? instead of List<String?>?**
   (The inner @Nullable wasn't being properly folded when there was an outer @Nullable)

## Root Causes

### Issue 1 & 2: Array Annotation Position
The logic was backwards - we were treating `@Nullable String[]` as nullable array when it should be array of nullable elements.

### Issue 3: Nested Generics Not Processed
When building placeholder text for `@Nullable List<@Nullable String>`:
1. The code was using `PsiJavaCodeReferenceElement.getText()` which returns the full text including generics like `List<@Nullable String>`
2. This meant the nested `@Nullable` wasn't being converted to `?` in the placeholder text

## Solutions Implemented

### Solution 1: Corrected Array Semantics

**File**: `NullableFoldingBuilder.java`
**Methods**: `handleVariable()`, `handleArrayTypeUseAnnotation()`, `handleArrayComponentNullability()`

**How it works now**:
- `@Nullable String[]`: annotation before type → array of nullable elements → folds to `String?[]` ✓
- `String @Nullable[]`: annotation between type and brackets → nullable array → folds to `String[]?` ✓
- `@Nullable String @Nullable[]`: both → folds to `String?[]?` ✓

### Solution 2: Skip Nested Annotations Already Covered by Outer @Nullable

**File**: `NullableFoldingBuilder.java`
**New Method**: `hasOuterNullableAnnotation()`

**Logic**:
```java
private boolean hasOuterNullableAnnotation(@NotNull PsiAnnotation annotation) {
    // Walk up the PSI tree to find if we're inside another type element 
    // that has @Nullable
    PsiElement current = annotation.getParent();
    while (current != null) {
        if (current instanceof PsiTypeElement) {
            // Check the parent of this type element
            PsiElement typeParent = current.getParent();
            
            // Check if parent is a type element with @Nullable
            if (typeParent instanceof PsiTypeElement parentTypeElement) {
                for (PsiElement child : parentTypeElement.getChildren()) {
                    if (child instanceof PsiAnnotation && 
                        isNullableAnnotation((PsiAnnotation) child)) {
                        return true; // Found outer @Nullable
                    }
                }
            }
            
            // Check if the type element is part of a variable/method 
            // that has @Nullable on its modifier list
            // ... (similar checks for modifier lists)
        }
        current = current.getParent();
    }
    return false;
}
```

**Updated**: `processAnnotation()` method now checks:
```java
if (parent instanceof PsiTypeElement typeElement) {
    // Check if there's an outer @Nullable annotation that would handle this
    if (!hasOuterNullableAnnotation(annotation)) {
        handleTypeUseAnnotation(annotation, typeElement, descriptors);
    }
    return;
}
```

**How it works**:
- For `@Nullable List<@Nullable String>`:
  1. Inner `@Nullable` is detected
  2. `hasOuterNullableAnnotation()` finds the outer `@Nullable`
  3. Inner annotation is skipped (no fold descriptor created)
  4. Outer `@Nullable` creates ONE fold descriptor that displays `List<String?>?` ✓

### Solution 3: Fixed PsiJavaCodeReferenceElement Text Extraction

**Problem**: The `buildTypeTextWithNullable()` method was using `child.getText()` on `PsiJavaCodeReferenceElement`, which returns the full text including generics (e.g., `List<@Nullable String>`). This bypassed our custom nullable-to-? conversion logic for nested generics.

**Solution**: Changed to use `refElement.getReferenceName()` to get just the class name (e.g., `List`), then separately process the parameter list with `buildGenericArgsText()`:

```java
if (child instanceof PsiJavaCodeReferenceElement refElement) {
    // Use getReferenceName() to get just the class name without generics
    result.append(refElement.getReferenceName());
    
    // Now handle the type parameters if present
    PsiReferenceParameterList paramList = refElement.getParameterList();
    if (paramList != null && paramList.getTypeParameterElements().length > 0) {
        result.append(buildGenericArgsText(paramList));
    }
}
```

This fix was applied to:
- `buildTypeTextWithNullable()`
- `buildTypeTextWithNullableStripped()`
- `buildArrayTypeTextWithNullable()`
- `buildArrayTypeTextWithNullableStripped()`

## Test Results Expected

### Array Cases
✓ `@Nullable String[]` → `String?[]` (array of nullable elements)
✓ `String @Nullable[]` → `String[]?` (nullable array)
✓ `@Nullable String @Nullable[]` → `String?[]?` (array of nullable elements, and array itself is nullable)

### Nested Generic Cases
✓ `@Nullable List<@Nullable String>` → `List<String?>?`
✓ `List<@Nullable String>` → `List<String?>`
✓ `@Nullable Map<String, @Nullable List<@Nullable Integer>>` → `Map<String, List<Integer?>?>?`

## Files Modified

1. **NullableFoldingBuilder.java**
   - Updated `isArrayComponentAnnotation()` - Better detection of annotation position
   - Added `hasOuterNullableAnnotation()` - Detects when annotation is nested under another @Nullable
   - Modified `processAnnotation()` - Skips nested annotations when outer @Nullable exists
   - **Fixed `buildArrayTypeTextWithNullable()`** - Now handles `PsiJavaCodeReferenceElement` for simple types (e.g., String)
   - **Fixed `buildArrayTypeTextWithNullableStripped()`** - Same fix for the stripped variant
   - **Added `getArrayComponentElement()`** - Returns either PsiTypeElement or PsiJavaCodeReferenceElement
   - **Added `getArrayComponentText()`** - Gets component type text correctly for both simple and complex types
   - **Fixed `handleArrayComponentTypeUseAnnotation()`** - Uses new helper methods for proper component handling
   - **Fixed `handleArrayComponentNullability()`** - Uses new helper methods for proper component handling

### Additional Fix: PSI Structure for Simple Array Types

The original code assumed array types always have a `PsiTypeElement` as their component, but for simple types like `String[]`, the PSI structure is:

```
PsiTypeElement (String[])
  PsiJavaCodeReferenceElement (String)  <-- NOT a PsiTypeElement!
  PsiJavaToken ([)
  PsiJavaToken (])
```

This caused `buildArrayTypeTextWithNullable` to produce `?[]` instead of `String?[]`.

**Fix**: Added handling for `PsiJavaCodeReferenceElement` in all array-related methods.

## Testing

1. **Build**: `./gradlew buildPlugin`
2. **Run Sandbox**: `./gradlew runIde`
3. **Open**: `TestFoldingIssues.java` in sandbox
4. **Verify**: All test cases fold correctly per TESTING_GUIDE.md

## Documentation Created

1. **ARRAY_FOLDING_FIX.md** - Technical explanation of fixes
2. **TESTING_GUIDE.md** - Comprehensive manual testing guide with all test cases

## Next Steps

The typing handler (`NullableTypedHandler.java`) may also need similar updates to ensure that when users type `?` in these contexts, the correct annotation is inserted and immediately folded correctly.

## Verification Checklist

- [x] Code compiles successfully
- [x] No compilation errors in main source
- [x] Logic handles array component vs array nullability correctly
- [x] Logic prevents duplicate fold regions for nested @Nullable
- [ ] Manual testing in IDE sandbox (user to verify)
- [ ] Typing `?` works correctly in all contexts (may need additional fixes)
