# Fix for Record Component Nullable Type Handling

## Issue
When typing `String?` in a record component (e.g., `record User(String? name)`), the `NullableTypedHandler` was not detecting the type correctly and wasn't inserting the `@Nullable` annotation.

## Root Cause
The issue was in how we were finding the PSI element to analyze:

**Before (incorrect):**
```java
int offset = editor.getCaretModel().getOffset();
PsiElement element = file.findElementAt(offset - 1); // Position before '?'
```

When you type `String?`, the sequence is:
1. User types 'S', 't', 'r', 'i', 'n', 'g'
2. Caret is at position after 'g' (let's say position 6)
3. User types '?'
4. Caret is now at position 7 (after the '?')

The code was looking at `offset - 1` which is position 6, but at that position, the PSI structure sees the '?' character itself, not the type identifier before it.

## Solution
Changed to look at `offset - 2` to get the element **before** where the '?' was typed:

**After (correct):**
```java
int offset = editor.getCaretModel().getOffset();

// The '?' was just typed, so offset points right after it
// We need to look at the element before the '?', which is at offset - 2
PsiElement element = file.findElementAt(offset - 2);
```

This correctly identifies the last character of the type identifier (the 'g' in 'String'), which allows the PSI traversal to find the proper `PsiTypeElement`.

## Additional Improvements

### Enhanced Type Context Detection
Also improved the `findTypeContext` method to handle more cases:

```java
private TypeContext findTypeContext(@NotNull PsiElement element) {
    PsiElement current = element;

    while (current != null) {
        // Check if we're in or right after a type reference element
        if (current instanceof PsiJavaCodeReferenceElement) {
            PsiJavaCodeReferenceElement ref = (PsiJavaCodeReferenceElement) current;
            return analyzeTypeReference(ref);
        }
        
        // Check if parent is a type reference
        if (current.getParent() instanceof PsiJavaCodeReferenceElement) {
            PsiJavaCodeReferenceElement ref = (PsiJavaCodeReferenceElement) current.getParent();
            return analyzeTypeReference(ref);
        }

        // Check if we're in a type element
        PsiTypeElement typeElement = PsiTreeUtil.getParentOfType(current, PsiTypeElement.class, false);
        if (typeElement != null) {
            return new TypeContext(typeElement, typeElement.getType(), AnnotationPosition.BEFORE_TYPE);
        }

        current = current.getParent();
    }

    return null;
}
```

This now checks both if the current element is a type reference AND if its parent is, which handles more edge cases in the PSI structure.

## Testing

### Test Case 1: Record Component
```java
record User(String? name)
```

**Expected Result:**
```java
import org.jspecify.annotations.Nullable;

record User(@Nullable String name)
```

### Test Case 2: Regular Field
```java
class User {
    private String? name;
}
```

**Expected Result:**
```java
import org.jspecify.annotations.Nullable;

class User {
    private @Nullable String name;
}
```

### Test Case 3: Method Parameter
```java
void setName(String? name) { }
```

**Expected Result:**
```java
import org.jspecify.annotations.Nullable;

void setName(@Nullable String name) { }
```

### Test Case 4: Generic Type
```java
List<String?> names;
```

**Expected Result:**
```java
import org.jspecify.annotations.Nullable;

List<@Nullable String> names;
```

## How to Test

1. **Rebuild the plugin:**
   ```bash
   ./gradlew buildPlugin
   ```

2. **Run the plugin in a development IDE:**
   ```bash
   ./gradlew runIde
   ```

3. **In the dev IDE, create a test file:**
   - Create a new Java file
   - Add JSpecify dependency to your project
   - Type: `record User(String name)`
   - Place cursor after `String` and type `?`
   - Verify that it becomes `@Nullable String` and the import is added

4. **Test with the provided TestRecords.java:**
   - Open `TestRecords.java` in the dev IDE
   - Try modifying the record components by adding `?` after types
   - Verify the annotations are inserted correctly

## Files Modified

- `src/main/java/me/samhollis/valhalla/typing/NullableTypedHandler.java`
  - Line 38: Changed `offset - 1` to `offset - 2`
  - Lines 73-97: Enhanced `findTypeContext` method

## Why This Works

The PSI (Program Structure Interface) in IntelliJ represents code as a tree structure. When you're at a particular text offset, `findElementAt(offset)` returns the PSI element at that position.

For the text `String?`:
- Position 0-5: 'String' (identifier token)
- Position 6: '?' (question mark token)
- Position 7: (cursor after typing '?')

When the `charTyped` method is called:
- `offset` = 7 (current caret position after '?')
- `offset - 1` = 6 (the '?' character position)
- `offset - 2` = 5 (last character of 'String')

By looking at `offset - 2`, we correctly identify the type identifier, which allows the traversal up the PSI tree to find the `PsiTypeElement` containing it.

## Verification

The fix has been compiled and the plugin has been rebuilt successfully:
```
BUILD SUCCESSFUL in 1s
```

The updated plugin is available at:
```
build/distributions/ValhallaNow2-1.0-SNAPSHOT.zip
```
