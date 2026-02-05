# Manual Testing Guide for Array and Nested Nullable Folding

## Test Setup

1. Build the plugin: `./gradlew buildPlugin`
2. Run IDE sandbox: `./gradlew runIde`
3. In the sandbox, open or create `TestFoldingIssues.java`

## Test Cases

### Test 1: Array Component Annotation (String @Nullable[])
**Source Code:**
```java
private String @Nullable[] arrayOfNullable;
```

**Expected Folding:**
```java
private String?[] arrayOfNullable;
```

**What to verify:**
- The `@Nullable` and `[]` together fold to show `?[]`
- The `?` appears AFTER `String`, not after the brackets

---

### Test 2: Nullable Array (@Nullable String[])
**Source Code:**
```java
private @Nullable String[] nullableArray;
```

**Expected Folding:**
```java
private String[]? nullableArray;
```

**What to verify:**
- The `@Nullable` annotation and `String[]` fold together
- The `?` appears AFTER the brackets `[]`

---

### Test 3: Nested Nullable in Generic (@Nullable List<@Nullable String>)
**Source Code:**
```java
private @Nullable List<@Nullable String> nullableListOfNullable;
```

**Expected Folding:**
```java
private List<String?>? nullableListOfNullable;
```

**What to verify:**
- BOTH `@Nullable` annotations are folded
- Inner `@Nullable` becomes `String?`
- Outer `@Nullable` adds `?` after `>`, resulting in `List<String?>?`
- There should be NO visible `@Nullable` when folded

---

### Test 4: Single Nested Nullable (List<@Nullable String>)
**Source Code:**
```java
private List<@Nullable String> listOfNullable;
```

**Expected Folding:**
```java
private List<String?> listOfNullable;
```

**What to verify:**
- Only the inner `@Nullable` is folded
- Result is `List<String?>` with no outer `?`

---

### Test 5: Multi-Dimensional Array (String @Nullable[][])
**Source Code:**
```java
private String @Nullable[][] multiDimArray;
```

**Expected Folding:**
```java
private String?[][] multiDimArray;
```

**What to verify:**
- The `?` appears after `String` but before ALL brackets
- Result should be `String?[][]`, not `String[][]?` or `String[]?[]`

---

### Test 6: Complex Nested Case
**Source Code:**
```java
private @Nullable Map<String, @Nullable List<@Nullable Integer>> complexNested;
```

**Expected Folding:**
```java
private Map<String, List<Integer?>?>? complexNested;
```

**What to verify:**
- Inner `@Nullable Integer` becomes `Integer?`
- Middle `@Nullable List<...>` becomes `List<Integer?>?`
- Outer `@Nullable Map<...>` adds final `?` after the closing `>`

---

## Typing Tests

### Test 7: Type ? after String to create @Nullable String
**Steps:**
1. Create new field: `private String s;`
2. Place cursor after `String` (before space)
3. Type `?`

**Expected Result:**
```java
private @Nullable String s;
```
**Folded Display:**
```java
private String? s;
```

---

### Test 8: Type ? after String[] to create @Nullable String[]
**Steps:**
1. Create new field: `private String[] arr;`
2. Place cursor after `]` (before space)
3. Type `?`

**Expected Result:**
```java
private @Nullable String[] arr;
```
**Folded Display:**
```java
private String[]? arr;
```

---

### Test 9: Type ? after String in generic to create List<@Nullable String>
**Steps:**
1. Create new field: `private List<String> list;`
2. Place cursor inside `<>` after `String`
3. Type `?`

**Expected Result:**
```java
private List<@Nullable String> list;
```
**Folded Display:**
```java
private List<String?> list;
```

---

## Known Limitations

1. **Type Parameters**: Typing `?` after `T` in `<T>` declarations - this may need special handling
2. **Multiple Annotations**: Other annotations mixed with `@Nullable` may affect folding positions
3. **Comments**: Comments between type and `@Nullable` may cause issues

## Debugging Tips

If folding doesn't work as expected:

1. **Unfold and Re-fold**: Right-click on the folded region and select "Expand" then "Collapse" again
2. **Restart Code Analysis**: Use `Ctrl+Shift+F5` (Windows/Linux) or `Cmd+Option+L` (Mac) to restart highlighting
3. **Check PSI Structure**: Use `Tools > View PSI Structure` to see how IntelliJ parses the code
4. **Review Import**: Make sure `import org.jspecify.annotations.Nullable;` is present

## Success Criteria

All test cases should:
- Fold automatically when the file is opened
- Display the correct `?` suffix notation
- Maintain correct positioning (before or after brackets/generics)
- Work seamlessly when typing `?` to insert annotations
- Not show any visible `@Nullable` when folded
