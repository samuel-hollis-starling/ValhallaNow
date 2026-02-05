# Fix: @Override and public Hidden by Folding (2026-02-05)

## Problem
When typing `?` after `String` in a method declaration like:
```java
@Override
public String toString()
```

The plugin would add `@Nullable`, but the folding would incorrectly hide `@Override` and `public`, showing only:
```java
String? toString()
```

Instead of the expected:
```java
@Override
public String? toString()
```

## Root Cause
The issue was in how `@Nullable` annotations were being added to the PSI structure:

1. **Previous approach**: Added `@Nullable` to the `PsiModifierList` (which contains `@Override`, `public`, etc.)
2. **Problem**: When the folding builder calculated the fold range from the annotation to the type, it included everything from the start of the `@Nullable` annotation to the end of the type
3. **Result**: If `@Nullable` was positioned early in the modifier list (or if the PSI structure was ambiguous), the fold would start too early, hiding preceding modifiers

## Solution
Changed the annotation insertion strategy in `NullableTypedHandler.java`:

**Before**:
```java
// Add annotation to modifier list
PsiModifierList modifierList = findModifierList(context.target());
if (modifierList != null) {
    addedAnnotation = (PsiAnnotation) modifierList.add(annotation);
    CodeStyleManager.getInstance(project).reformat(modifierList);
}
```

**After**:
```java
// Add annotation directly to the type element (type-use annotation)
if (context.target() instanceof PsiTypeElement typeElement) {
    addedAnnotation = (PsiAnnotation) typeElement.addBefore(annotation, typeElement.getFirstChild());
    CodeStyleManager.getInstance(project).reformat(typeElement);
} else {
    // Fallback for other cases
    PsiModifierList modifierList = findModifierList(context.target());
    if (modifierList != null) {
        addedAnnotation = (PsiAnnotation) modifierList.add(annotation);
        CodeStyleManager.getInstance(project).reformat(modifierList);
    }
}
```

## Why This Works
1. **Type-use annotations**: JSpecify's `@Nullable` is a type-use annotation, meaning it annotates the type itself, not the declaration
2. **Correct PSI structure**: By adding `@Nullable` directly to the `PsiTypeElement`, it becomes a child of the type, not the modifier list
3. **Accurate fold range**: The folding builder now correctly calculates the range from `@Nullable` (inside the type element) to the end of the type, without including preceding modifiers

## Additional Improvements
1. **Cleaned up imports**: Replaced wildcard imports with explicit imports for better readability
2. **Simplified folding update**: Removed excessive PSI cache clearing and reparse attempts
3. **Removed unused code**: Removed the `findInsertionPoint` method that was no longer needed

## Testing
Build successful. The plugin should now correctly fold method return types:
- `@Override public @Nullable String toString()` → `@Override public String? toString()` (folded)
- All modifiers and annotations remain visible
- Only the `@Nullable` annotation and type are replaced with the `?` suffix

## Files Modified
- `/src/main/java/me/samhollis/valhalla/typing/NullableTypedHandler.java`
  - Changed annotation insertion to use type element instead of modifier list
  - Cleaned up imports
  - Simplified folding update logic
  - Removed unused methods
