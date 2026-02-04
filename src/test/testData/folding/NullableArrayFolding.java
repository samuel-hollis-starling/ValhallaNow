import org.jspecify.annotations.Nullable;

public class NullableArrayFolding {
    // Nullable array - the array itself can be null
    private <fold text='String[]?'>@Nullable String[]</fold> names;
    private <fold text='Integer[]?'>@Nullable Integer[]</fold> numbers;
}
