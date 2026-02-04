import org.jspecify.annotations.Nullable;

public class ArrayOfNullableFolding {
    // Array of nullable elements - elements can be null, but array itself cannot
    private <fold text='String?[]'>String @Nullable []</fold> names;
    private <fold text='Integer?[]'>Integer @Nullable []</fold> numbers;
}
