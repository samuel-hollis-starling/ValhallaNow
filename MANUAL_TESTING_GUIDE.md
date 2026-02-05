# Testing Guide for Generic and Array Component Support

## Summary of Changes

We've successfully implemented support for:
1. **Type parameters in generics**: `List<String?>` → `List<@Nullable String>` with folding
2. **Array component types**: `String?[]` → `String @Nullable []` with folding

## Manual Testing Instructions

Since the automated tests have infrastructure issues, manual testing is required.

### Step 1: Build and Run
```bash
cd /Users/samuel.hollis/IdeaProjects/ValhallaNow2
./gradlew buildPlugin -x test
./gradlew runIde
```

### Step 2: Test Generic Type Parameters

1. Create a new Java file in the development IDE:
```java
import java.util.List;

public class TestGenerics {
    private List<String> names;
}
```

2. Position cursor after `String` (before `>`)
3. Type `?`
4. **Expected Result:**
   - The file should show: `private List<@Nullable String> names;`
   - Import added: `import org.jspecify.annotations.Nullable;`
   - Visual display (folded): `private List<String?> names;`

### Step 3: Test Nested Generics

1. Create:
```java
import java.util.Map;
import java.util.List;

public class TestNested {
    private Map<String, List<Integer>> data;
}
```

2. Position cursor after `Integer` (before `>`)
3. Type `?`
4. **Expected Result:**
   - Code: `private Map<String, List<@Nullable Integer>> data;`
   - Import: `import org.jspecify.annotations.Nullable;`
   - Display: `private Map<String, List<Integer?>> data;`

### Step 4: Test Array Component Type

1. Create:
```java
public class TestArrays {
    private String[] names;
}
```

2. Position cursor after `String` (before `[]`)
3. Type `?`
4. **Expected Result:**
   - Code: `private String @Nullable [] names;`
   - Import: `import org.jspecify.annotations.Nullable;`
   - Display: `private String?[] names;`

### Step 5: Test Nullable Array (Existing Feature)

1. Create:
```java
public class TestNullableArray {
    private String[] names;
}
```

2. Position cursor after `[]`
3. Type `?`
4. **Expected Result:**
   - Code: `private @Nullable String[] names;`
   - Import: `import org.jspecify.annotations.Nullable;`
   - Display: `private String[]? names;`

### Step 6: Test Regular Parameter (Regression Test)

1. Create:
```java
public class TestParameter {
    public void setName(String name) {
    }
}
```

2. Position cursor after `String` (before space)
3. Type `?`
4. **Expected Result:**
   - Code: `public void setName(@Nullable String name) {`
   - Import: `import org.jspecify.annotations.Nullable;`
   - Display: `public void setName(String? name) {`

### Step 7: Test Primitive Rejection (Regression Test)

1. Create:
```java
public class TestPrimitive {
    private int count;
}
```

2. Position cursor after `int`
3. Type `?`
4. **Expected Result:**
   - Error message: "Primitive types cannot be nullable"
   - Code unchanged: `private int count;`
   - No import added

## Verification Checklist

- [ ] Generic type parameters work (`List<String?>`)
- [ ] Nested generics work (`Map<String, List<Integer?>>`)
- [ ] Array component types work (`String?[]`)
- [ ] Nullable arrays still work (`String[]?`)
- [ ] Regular parameters still work (`String?`)
- [ ] Primitives are still rejected (`int?` shows error)
- [ ] Import is added automatically when needed
- [ ] Import is not duplicated if already present
- [ ] Folding is applied immediately after typing `?`
- [ ] Folding displays the `Type?` syntax correctly
- [ ] Unfolding shows the `@Nullable` annotation correctly

## Known Limitations

The following cases are NOT yet supported (future enhancements):
1. Multi-dimensional arrays: `String[]?[]`
2. Wildcard generics: `List<? extends String?>`
3. Method type parameters: `<T?> void method(T t)`

## Troubleshooting

### Folding Not Appearing
If folding doesn't appear after typing `?`:
1. Check that the import was added
2. Check that the annotation appears in the code
3. Try manually triggering folding: Code → Folding → Fold All
4. Check IDE logs for errors

### Annotation in Wrong Place
If the annotation appears in the wrong location:
1. Note the exact code before typing `?`
2. Note the cursor position
3. Check the result
4. Report the issue with these details

### Import Not Added
If the import is missing:
1. Check that the annotation was actually added
2. Check if the import already exists (might be collapsed)
3. Manually add: `import org.jspecify.annotations.Nullable;`

## Files Modified

1. `/src/main/java/me/samhollis/valhalla/typing/NullableTypedHandler.java`
   - Updated `insertNullableAnnotation()` to handle type-use annotations
   - Now adds annotations directly to `PsiTypeElement` when no modifier list exists

2. `/src/main/java/me/samhollis/valhalla/folding/NullableFoldingBuilder.java`
   - Updated `processAnnotation()` to recognize type-use annotations
   - Added `handleTypeUseAnnotation()` method for folding type-use annotations

## Documentation

See `GENERICS_ARRAY_FIX.md` for detailed technical explanation of the changes.
