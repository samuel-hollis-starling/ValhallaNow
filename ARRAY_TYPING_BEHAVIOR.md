# Array Typing Behavior for Nullable Annotations

## Expected Behavior

When typing `?` after array types, the position matters:

### 1. After closing bracket `]` - Makes the array itself nullable
```java
// Type: String[]
// Cursor position: String[]|
// Type '?' → Result: String @Nullable []
// Folds to: String[]?
```

This means the **array reference** is nullable (the array can be null), but elements are non-null.

### 2. After type name (before brackets) - Makes array elements nullable  
```java
// Type: String[]
// Cursor position: String|[]
// Type '?' → Result: @Nullable String[]
// Folds to: String?[]
```

This means the **array elements** are nullable, but the array itself cannot be null.

### 3. Both positions can be used together
```java
// Start with: String[]
// Step 1 - Type '?' after String: @Nullable String[]
// Step 2 - Type '?' after ]: @Nullable String @Nullable []
// Folds to: String?[]?
```

This means both the array and its elements are nullable.

## Implementation Details

The `NullableTypedHandler` class:
1. Detects when '?' is typed
2. Checks if the previous character is ']' 
3. If yes → ARRAY_COMPONENT position → inserts `Type @Nullable []`
4. If no → BEFORE_TYPE position → inserts `@Nullable Type[]`

## JSpecify Semantics

- `@Nullable String[]` - array of nullable Strings (array itself is non-null)
- `String @Nullable[]` - nullable array of non-null Strings  
- `@Nullable String @Nullable[]` - nullable array of nullable Strings

The folding displays these as:
- `String?[]` for `@Nullable String[]`
- `String[]?` for `String @Nullable[]`
- `String?[]?` for `@Nullable String @Nullable[]`
