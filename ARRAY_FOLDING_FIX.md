# Array Folding Fix Summary

## Issues Fixed

### 1. Array vs. Array Component Annotation Detection
**Problem**: The code wasn't properly distinguishing between:
- `@Nullable String[]` → should fold to `String[]?` (nullable array - array itself is nullable)
- `String @Nullable[]` → should fold to `String?[]` (array of nullable - component is nullable)

**Solution**: Updated `isArrayComponentAnnotation()` to check if the annotation appears **between** the base type and the first bracket `[`, not just after the type start.

```java
// Old logic:
return annotationOffset > typeOffset;

// New logic:
int firstBracketOffset = typeOffset + typeText.indexOf('[');
return annotationOffset > typeOffset && annotationOffset < firstBracketOffset;
```

This correctly identifies:
- `@Nullable String[]` - annotation at offset BEFORE type → nullable array
- `String @Nullable[]` - annotation at offset BETWEEN "String" and "[" → array of nullable

### 2. Nested @Nullable Annotations in Generics
**Problem**: When folding `@Nullable List<@Nullable String>`, both the outer and inner `@Nullable` annotations were trying to fold, causing conflicts. The expected result is `List<String?>?` but it was showing incorrectly.

**Solution**: Added `hasOuterNullableAnnotation()` method that checks if an annotation is nested inside another type that already has a `@Nullable` annotation. If so, we skip creating a separate fold descriptor for the inner annotation, letting the outer annotation handle the complete folding.

```java
private boolean hasOuterNullableAnnotation(@NotNull PsiAnnotation annotation) {
    // Walk up the PSI tree to find if we're inside another type element that has @Nullable
    // If found, return true to skip creating a duplicate fold descriptor
}
```

This prevents duplicate fold regions and allows the outer `@Nullable` to properly display as `List<String?>?`.

## Expected Behaviors After Fix

### Arrays
- `@Nullable String[]` → `String[]?` (nullable array)
- `String @Nullable[]` → `String?[]` (array of nullable strings)
- `@Nullable String[][]` → `String[][]?` (nullable 2D array)
- `String @Nullable[][]` → `String?[][]` (2D array of nullable strings)

### Generics with Outer Nullable
- `@Nullable List<String>` → `List<String>?`
- `@Nullable List<@Nullable String>` → `List<String?>?`
- `@Nullable Map<String, @Nullable Integer>` → `Map<String, Integer?>?`

### Generics without Outer Nullable
- `List<@Nullable String>` → `List<String?>`
- `Map<String, @Nullable Integer>` → `Map<String, Integer?>`

## Files Modified

1. `/Users/samuel.hollis/IdeaProjects/ValhallaNow2/src/main/java/me/samhollis/valhalla/folding/NullableFoldingBuilder.java`
   - Updated `isArrayComponentAnnotation()` method
   - Added `hasOuterNullableAnnotation()` method
   - Modified `processAnnotation()` to check for outer nullable before creating fold descriptors

## Testing

To test these changes:
1. Build the plugin: `./gradlew buildPlugin`
2. Run the IDE sandbox: `./gradlew runIde`
3. Open `TestFoldingIssues.java` in the sandbox
4. Verify folding displays correctly for all test cases
5. Test typing `?` after types to ensure folding immediately applies

## Next Steps

The typing handler may also need updates to properly handle these cases when the user types `?` in different positions.
