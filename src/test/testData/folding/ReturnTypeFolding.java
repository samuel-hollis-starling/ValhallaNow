import org.jspecify.annotations.Nullable;

public class ReturnTypeFolding {
    public <fold text='String?'>@Nullable String</fold> getName() {
        return null;
    }

    public <fold text='Integer?'>@Nullable Integer</fold> getAge() {
        return null;
    }
}
