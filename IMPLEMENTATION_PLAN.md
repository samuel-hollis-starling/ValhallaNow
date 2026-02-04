# Valhalla Now Plugin Implementation Plan

## Overview
IntelliJ IDEA plugin to provide visual transformation and typing assistance for Project Valhalla nullable types using JSpecify annotations.

## Components to Implement

### 1. Gradle Configuration
**File:** `build.gradle.kts`
- Update to use `org.jetbrains.intellij.platform` plugin v2.x
- Configure IntelliJ SDK 2024.2+
- Add JSpecify annotations dependency (`org.jspecify:jspecify:1.0.0`)
- Configure test fixtures
- Configure `runIde` task for development

### 2. Plugin Descriptor
**File:** `src/main/resources/META-INF/plugin.xml`
- Metadata:
  - Name: "Valhalla Now"
  - Description: Reference Project Valhalla nullable types
  - Vendor information
  - Compatibility: since-build 242 (2024.2)
- Register extensions:
  - `com.intellij.lang.foldingBuilder` → `NullableFoldingBuilder`
  - `com.intellij.typedHandler` → `NullableTypedHandler`

### 3. NullableFoldingBuilder
**File:** `me.samhollis.valhalla.folding.NullableFoldingBuilder`
- Extends `FoldingBuilderEx`
- Identifies JSpecify `@Nullable` annotations
- Folding behavior:
  - Fold annotation to empty string
  - Append `?` after type
  - Handle multiple contexts:
    - Fields
    - Method parameters
    - Return types
    - Generic type arguments: `List<@Nullable String>` → `List<String?>`
    - Arrays:
      - `@Nullable String[]` → `String[]?` (nullable array)
      - `String @Nullable []` → `String?[]` (array of nullable elements)
- Set `isCollapsedByDefault()` to `true`

### 4. NullableTypedHandler
**File:** `me.samhollis.valhalla.typing.NullableTypedHandler`
- Extends `TypedHandlerDelegate`
- Intercepts `?` typed after type identifiers
- Validation:
  - Context must be Java type position
  - NOT a primitive type (int, long, double, etc.)
- Actions:
  - Insert `@Nullable` annotation before type
  - Add import for `org.jspecify.annotations.Nullable`
  - Consume typed `?`
- Support contexts:
  - Regular types
  - Generics: `List<String?>`
  - Arrays: `String[]?` or `String?[]`
- Error handling:
  - Show error hint for primitives

### 5. TypeValidator Utility
**File:** `me.samhollis.valhalla.util.TypeValidator`
- Utility class for type validation
- Method: `isPrimitiveType(PsiType)`
- Check for all Java primitive types
- Used to prevent nullable primitives
- Show `HintManager` error: "Primitive types cannot be nullable"

### 6. Comprehensive Tests
**Directory:** `src/test/java/me/samhollis/valhalla/`

#### Test Classes:
1. **NullableFoldingBuilderTest**
   - Test folding for fields
   - Test folding for parameters
   - Test folding for return types
   - Test folding for generics
   - Test folding for arrays
   - Test folding for nested generics

2. **NullableTypedHandlerTest**
   - Test `?` insertion
   - Test import addition
   - Test primitive rejection
   - Test array contexts

3. **TypeValidatorTest**
   - Test primitive detection for all primitive types

Base: `LightJavaCodeInsightFixtureTestCase`

### 7. Test Data Fixtures
**Directories:**
- `src/test/testData/folding/` - Sample Java files with `@Nullable` patterns
- `src/test/testData/typing/` - Sample typing scenarios

## Further Considerations

### Array Notation Ambiguity
**Issue:** Java allows both:
- `@Nullable String[]` (nullable array)
- `String @Nullable []` (array of nullable elements)

**Question:** Should plugin support typing both `String[]?` and `String?[]`?

**Recommendation:** Support both with PSI analysis to determine correct annotation placement.

### Wildcard Generics
**Issue:** Should `List<? extends @Nullable String>` be supported?

**Recommendation:** Include in scope for complete generic coverage.

### Multi-dimensional Arrays
**Issue:** How should these display?
- `@Nullable String[][]`
- `String @Nullable [][]`

**Recommendation:** Follow Valhalla pattern:
- `String[][]?` (nullable 2D array)
- `String?[][]` (2D array of nullable elements)

## Implementation Order
1. ✅ Create plan document
2. Configure Gradle build file
3. Create plugin descriptor
4. Implement TypeValidator utility
5. Implement NullableFoldingBuilder
6. Implement NullableTypedHandler
7. Create test infrastructure
8. Implement tests
9. Create test data fixtures
10. Test and validate

## Dependencies
- IntelliJ Platform SDK 2024.2+
- JSpecify 1.0.0
- Gradle IntelliJ Platform Plugin v2.x

## Target IDE Version
- Since Build: 242 (IntelliJ IDEA 2024.2+)
