import org.jspecify.annotations.Nullable;
import java.util.List;
import java.util.Map;

public class TestFoldingIssues {
    // String @Nullable[] should become String[]? (nullable array - array itself can be null)
    private String @Nullable[] arrayOfNullable;

    // @Nullable List<@Nullable String> should become List<String?>?
    private @Nullable List<@Nullable String> nullableListOfNullable;

    // Additional test cases

    // @Nullable String[] should become String?[] (array of nullable elements)
    private @Nullable String[] nullableArray;

    // List<@Nullable String> should become List<String?> (no outer nullable)
    private List<@Nullable String> listOfNullable;

    // Map with nested nullable → Map<String, List<Integer?>?>?
    private @Nullable Map<String, @Nullable List<@Nullable Integer>> complexNested;

    // Simple field → String?
    private @Nullable String simpleNullable;

    // Integer @Nullable [] → Integer[]? (nullable array)
    private Integer @Nullable [] intArrayOfNullable;

    // Multi-dimensional array: String @Nullable [][] → String[][]?
    private String @Nullable [][] multiDimArray;

    // Both array and component nullable: @Nullable String @Nullable[] → String?[]?
    private @Nullable String @Nullable[] bothNullable;

    // Should not do any parsing
    private List<? extends Object> extendyThing;
}
