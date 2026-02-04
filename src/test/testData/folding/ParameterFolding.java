import org.jspecify.annotations.Nullable;

public class ParameterFolding {
    public void setName(<fold text='String?'>@Nullable String</fold> name) {
        this.name = name;
    }

    public void setData(<fold text='Integer?'>@Nullable Integer</fold> id, <fold text='String?'>@Nullable String</fold> value) {
    }

    private String name;
}
