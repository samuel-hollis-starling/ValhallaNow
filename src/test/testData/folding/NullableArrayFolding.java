import org.jspecify.annotations.Nullable;

public class NullableArrayFolding {
    // Array of nullable elements - @Nullable before type means elements are nullable
    private <fold text='String?[]'>@Nullable String[]</fold> names;
    private <fold text='Integer?[]'>@Nullable Integer[]</fold> numbers;
}
