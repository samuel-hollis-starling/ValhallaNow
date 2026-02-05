import org.jspecify.annotations.Nullable;

public class TestArrayFolding {
    // Test case 1: String @Nullable [] should fold to String[]?
    private String @Nullable [] case1;

    // Test case 2: @Nullable String[] should fold to String?[]
    private @Nullable String[] case2;

    // Test case 3: @Nullable String @Nullable [] should fold to String?[]?
    private @Nullable String @Nullable [] case3;
}
