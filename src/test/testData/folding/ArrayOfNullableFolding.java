import org.jspecify.annotations.Nullable;

public class ArrayOfNullableFolding {
    // Nullable array - @Nullable between type and brackets means the array itself is nullable
    private <fold text='String[]?'>String @Nullable []</fold> names;
    private <fold text='Integer[]?'>Integer @Nullable []</fold> numbers;
}
