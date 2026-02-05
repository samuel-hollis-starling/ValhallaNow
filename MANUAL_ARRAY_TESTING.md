# Manual Testing Guide for Array Typing

## Setup
1. Build the plugin: `./gradlew build -x test`
2. Run the sandbox IDE: `./gradlew runIde`
3. Create a new Java file in the sandbox

## Test Cases

### Test 1: Nullable Array (String[]?)
1. Type: `private String[] names;`
2. Place cursor after the `]`: `private String[]|names;`
3. Type `?`
4. **Expected Result**: `private String @Nullable [] names;`
5. **Folded Display**: `private String[]? names;`
6. **Meaning**: The array variable can be null, elements cannot

### Test 2: Array of Nullable Elements (String?[])
1. Type: `private String[] names;`
2. Place cursor after `String`: `private String|[] names;`
3. Type `?`
4. **Expected Result**: `private @Nullable String[] names;`
5. **Folded Display**: `private String?[] names;`
6. **Meaning**: The array variable cannot be null, but elements can

### Test 3: Generic Array (List<String>[]?)
1. Type: `private java.util.List<String>[] lists;`
2. Place cursor after the `]`: `private java.util.List<String>[]|lists;`
3. Type `?`
4. **Expected Result**: `private java.util.List<String> @Nullable [] lists;`
5. **Folded Display**: `private java.util.List<String>[]? lists;`

### Test 4: Nullable Elements in Generic Array (List<String>?[])
1. Type: `private java.util.List<String>[] lists;`
2. Place cursor after `>`: `private java.util.List<String>|[] lists;`
3. Type `?`
4. **Expected Result**: `private @Nullable java.util.List<String>[] lists;`
5. **Folded Display**: `private java.util.List<String>?[] lists;`

### Test 5: Both Nullable (String?[]?)
1. Type: `private String[] names;`
2. Place cursor after `String`: `private String|[] names;`
3. Type `?`
4. Result: `private @Nullable String[] names;`
5. Place cursor after `]`: `private @Nullable String[]|names;`
6. Type `?`
7. **Expected Result**: `private @Nullable String @Nullable [] names;`
8. **Folded Display**: `private String?[]? names;`
9. **Meaning**: Both array and elements can be null

### Test 6: Primitive Type Rejection (int[]?)
1. Type: `private int[] numbers;`
2. Place cursor after `]`: `private int[]|numbers;`
3. Type `?`
4. **Expected Result**: 
   - The `?` is removed
   - Error hint appears: "Primitive types cannot be nullable"
   - Code remains: `private int[] numbers;`

### Test 7: Multi-dimensional Array (String[][]?)
1. Type: `private String[][] matrix;`
2. Place cursor after the last `]`: `private String[][]|matrix;`
3. Type `?`
4. **Expected Result**: `private String[] @Nullable [] matrix;`
5. **Folded Display**: `private String[][]? matrix;`
6. **Meaning**: The outer array can be null

## Verification Checklist
- [ ] Test 1: String[]? works correctly
- [ ] Test 2: String?[] works correctly
- [ ] Test 3: Generic arrays work
- [ ] Test 4: Generic element nullability works
- [ ] Test 5: Double nullability works
- [ ] Test 6: Primitives are rejected
- [ ] Test 7: Multi-dimensional arrays work
- [ ] Import for org.jspecify.annotations.Nullable is added automatically
- [ ] Code folding displays correctly
- [ ] Formatting is correct (proper spacing)

## Common Issues to Watch For
1. **Wrong Position**: Make sure cursor position determines the behavior
2. **Import Missing**: Check that the Nullable import is added
3. **No Folding**: The folding might need to be enabled or refreshed
4. **Spacing**: Check that there's proper spacing around @Nullable
5. **Primitive Arrays**: Ensure error appears for int[], long[], etc.

## Success Criteria
All test cases should produce the expected results with:
- Correct annotation placement
- Automatic import addition
- Proper code folding display
- Appropriate error handling for primitives
