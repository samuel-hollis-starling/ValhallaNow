import org.jspecify.annotations.Nullable;
import java.util.List;
import java.util.Map;

public class NestedGenericsFolding {
    private Map<String, List<<fold text='Integer?'>@Nullable Integer</fold>>> data;
    private List<List<<fold text='String?'>@Nullable String</fold>>> nested;
}
