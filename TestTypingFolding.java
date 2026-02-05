import org.jspecify.annotations.Nullable;

/**
 * Test file for verifying typing ? creates correct folding immediately.
 *
 * Instructions:
 * 1. Build and install the plugin
 * 2. Type ? after types to test immediate folding
 * 3. Verify folding appears correct WITHOUT reopening the file
 */
public class TestTypingFolding {

    // Test 1: Basic nullable type
    // Type: String? s
    // Expected fold: String? (from @Nullable String)

    // Test 2: Nullable array elements
    // Type: String?[] arr
    // Expected fold: String?[] (from @Nullable String[])

    // Test 3: Nullable array itself
    // Type: String[]? arr2
    // Expected fold: String[]? (from String @Nullable[])

    // Test 4: Both nullable - start with String @Nullable[]
    // Then type ? after String to get: String? @Nullable[]
    // Expected fold: String?[]? (from @Nullable String @Nullable[])
    @Nullable String @Nullable[] testBothNullable;

    // Test 5: Generic with nullable type parameter
    // Type: List<String?> list
    // Expected fold: List<String?> (from List<@Nullable String>)

    // Test 6: Nullable generic
    // Type: List<String>? list2
    // Expected fold: List<String>? (from @Nullable List<String>)

    // Test 7: Both nullable in generic
    // Type: List<String?>? list3
    // Expected fold: List<String?>? (from @Nullable List<@Nullable String>)
    @Nullable java.util.List<@Nullable String> testBothGeneric;

    // Test 8: Complex array with generics
    // Type: List<String>?[] arr3
    // Expected fold: List<String>?[] (from @Nullable List<String>[])

    void testMethod(@Nullable String @Nullable[] param) {
        // Test parameter typing
    }

    @Nullable String @Nullable[] testReturn() {
        // Test return type typing
        return null;
    }
}
