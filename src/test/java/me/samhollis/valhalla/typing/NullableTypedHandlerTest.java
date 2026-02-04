package me.samhollis.valhalla.typing;

import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;

/**
 * Tests for NullableTypedHandler.
 * Tests typing assistance when ? is typed after types.
 */
public class NullableTypedHandlerTest extends LightJavaCodeInsightFixtureTestCase {

    @Override
    protected String getTestDataPath() {
        return "src/test/testData";
    }

    public void testTypingQuestionMarkAfterFieldType() {
        myFixture.configureByText("Test.java", """
            public class Test {
                private String<caret> name;
            }
            """);

        myFixture.type('?');

        myFixture.checkResult("""
            import org.jspecify.annotations.Nullable;
            
            public class Test {
                private @Nullable String name;
            }
            """);
    }

    public void testTypingQuestionMarkAfterParameterType() {
        myFixture.configureByText("Test.java", """
            public class Test {
                public void setName(String<caret> name) {
                }
            }
            """);

        myFixture.type('?');

        myFixture.checkResult("""
            import org.jspecify.annotations.Nullable;
            
            public class Test {
                public void setName(@Nullable String name) {
                }
            }
            """);
    }

    public void testTypingQuestionMarkAfterReturnType() {
        myFixture.configureByText("Test.java", """
            public class Test {
                public String<caret> getName() {
                    return null;
                }
            }
            """);

        myFixture.type('?');

        myFixture.checkResult("""
            import org.jspecify.annotations.Nullable;
            
            public class Test {
                public @Nullable String getName() {
                    return null;
                }
            }
            """);
    }

    public void testTypingQuestionMarkAfterPrimitiveType() {
        myFixture.configureByText("Test.java", """
            public class Test {
                private int<caret> count;
            }
            """);

        myFixture.type('?');

        // Should show error and not insert annotation
        myFixture.checkResult("""
            public class Test {
                private int count;
            }
            """);
    }

    public void testTypingQuestionMarkForArrayType() {
        myFixture.configureByText("Test.java", """
            public class Test {
                private String[]<caret> names;
            }
            """);

        myFixture.type('?');

        myFixture.checkResult("""
            import org.jspecify.annotations.Nullable;
            
            public class Test {
                private @Nullable String[] names;
            }
            """);
    }

    public void testImportAlreadyExists() {
        myFixture.configureByText("Test.java", """
            import org.jspecify.annotations.Nullable;
            
            public class Test {
                private @Nullable String first;
                private String<caret> second;
            }
            """);

        myFixture.type('?');

        // Should not duplicate import
        myFixture.checkResult("""
            import org.jspecify.annotations.Nullable;
            
            public class Test {
                private @Nullable String first;
                private @Nullable String second;
            }
            """);
    }

    public void testTypingQuestionMarkInGeneric() {
        myFixture.configureByText("Test.java", """
            import java.util.List;
            
            public class Test {
                private List<String<caret>> names;
            }
            """);

        myFixture.type('?');

        myFixture.checkResult("""
            import org.jspecify.annotations.Nullable;
            
            import java.util.List;
            
            public class Test {
                private List<@Nullable String> names;
            }
            """);
    }

    public void testRejectsAllPrimitiveTypes() {
        String[] primitives = {"int", "long", "double", "float", "boolean", "char", "byte", "short"};

        for (String primitive : primitives) {
            myFixture.configureByText("Test.java",
                "public class Test {\n" +
                "    private " + primitive + "<caret> value;\n" +
                "}");

            myFixture.type('?');

            // Should not insert annotation
            myFixture.checkResult(
                "public class Test {\n" +
                "    private " + primitive + " value;\n" +
                "}");
        }
    }
}
