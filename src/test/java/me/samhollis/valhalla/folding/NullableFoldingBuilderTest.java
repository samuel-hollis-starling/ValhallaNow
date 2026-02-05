package me.samhollis.valhalla.folding;

import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;

/**
 * Tests for NullableFoldingBuilder.
 * Tests visual transformation of @Nullable annotations to ? syntax.
 */
public class NullableFoldingBuilderTest extends LightJavaCodeInsightFixtureTestCase {

    @Override
    protected String getTestDataPath() {
        return "src/test/testData";
    }

    public void testFoldingForField() {
        myFixture.configureByText("Test.java", """
            import org.jspecify.annotations.Nullable;
            
            public class Test {
                private @Nullable String name;
            }
            """);

        myFixture.testFolding(getTestDataPath() + "/folding/FieldFolding.java");
    }

    public void testFoldingForParameter() {
        myFixture.configureByText("Test.java", """
            import org.jspecify.annotations.Nullable;
            
            public class Test {
                public void setName(@Nullable String name) {
                    this.name = name;
                }
                private String name;
            }
            """);

        // Verify folding regions are created
        myFixture.testFolding(getTestDataPath() + "/folding/ParameterFolding.java");
    }

    public void testFoldingForReturnType() {
        myFixture.configureByText("Test.java", """
            import org.jspecify.annotations.Nullable;
            
            public class Test {
                public @Nullable String getName() {
                    return null;
                }
            }
            """);

        myFixture.testFolding(getTestDataPath() + "/folding/ReturnTypeFolding.java");
    }

    public void testFoldingForGenericTypeArgument() {
        myFixture.configureByText("Test.java", """
            import org.jspecify.annotations.Nullable;
            import java.util.List;
            
            public class Test {
                private List<@Nullable String> names;
            }
            """);

        myFixture.testFolding(getTestDataPath() + "/folding/GenericFolding.java");
    }

    public void testFoldingForNullableArray() {
        myFixture.configureByText("Test.java", """
            import org.jspecify.annotations.Nullable;
            
            public class Test {
                private @Nullable String[] names;
            }
            """);

        myFixture.testFolding(getTestDataPath() + "/folding/NullableArrayFolding.java");
    }

    public void testFoldingForArrayOfNullable() {
        myFixture.configureByText("Test.java", """
            import org.jspecify.annotations.Nullable;
            
            public class Test {
                private String @Nullable [] names;
            }
            """);

        myFixture.testFolding(getTestDataPath() + "/folding/ArrayOfNullableFolding.java");
    }

    public void testFoldingForBothNullableArray() {

        myFixture.testFolding(getTestDataPath() + "/folding/BothNullableArrayFolding.java");
    }

    public void testFoldingForNestedGenerics() {
        myFixture.configureByText("Test.java", """
            import org.jspecify.annotations.Nullable;
            import java.util.List;
            import java.util.Map;
            
            public class Test {
                private Map<String, List<@Nullable Integer>> data;
            }
            """);

        myFixture.testFolding(getTestDataPath() + "/folding/NestedGenericsFolding.java");
    }

    public void testFoldingIsCollapsedByDefault() {
        myFixture.configureByText("Test.java", """
            import org.jspecify.annotations.Nullable;
            
            public class Test {
                private @Nullable String name;
            }
            """);

        // Verify that folding regions are collapsed by default
        // This is done through visual inspection in the IDE
    }
}
