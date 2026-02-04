import org.jspecify.annotations.Nullable;

/**
 * Test file for verifying nullable type handling in records.
 *
 * Try typing '?' after type names to test the NullableTypedHandler.
 *
 * Examples to try:
 * 1. Add a new record component: String newField
 *    Then type '?' after String to make it: String? newField
 *
 * 2. Change existing types by adding '?':
 *    String name -> String? name (should become @Nullable String name)
 */

// Basic record with nullable fields
public record User(
        @Nullable String name,
        @Nullable Integer age,
        @Nullable String email
) {
    // Try adding '?' after any of the types above
}

// Record with already nullable fields (for comparison)
record Person(
    @Nullable String firstName,
    @Nullable String lastName,
    int age  // Note: primitives cannot be nullable
) {}

// Nested record example
record Address(
    String street,
    String city,
    String zipCode
) {}

record UserWithAddress(
    String name,
    Address address
) {}

// Generic record example
record Container<T>(
    T value,
    String label
) {}

// Record with multiple nullable fields
record Product(
    String id,
    String name,
    String description,
    Double price,
    Integer quantity
) {}
