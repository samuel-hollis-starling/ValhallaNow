package me.samhollis.valhalla.util;

import com.intellij.psi.*;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;

/**
 * Tests for TypeValidator utility class.
 */
public class TypeValidatorTest extends LightJavaCodeInsightFixtureTestCase {

    @Override
    protected String getTestDataPath() {
        return "src/test/testData";
    }

    public void testIsPrimitiveType_Int() {
        PsiType intType = PsiType.INT;
        assertTrue("int should be identified as primitive", TypeValidator.isPrimitiveType(intType));
    }

    public void testIsPrimitiveType_Long() {
        PsiType longType = PsiType.LONG;
        assertTrue("long should be identified as primitive", TypeValidator.isPrimitiveType(longType));
    }

    public void testIsPrimitiveType_Double() {
        PsiType doubleType = PsiType.DOUBLE;
        assertTrue("double should be identified as primitive", TypeValidator.isPrimitiveType(doubleType));
    }

    public void testIsPrimitiveType_Float() {
        PsiType floatType = PsiType.FLOAT;
        assertTrue("float should be identified as primitive", TypeValidator.isPrimitiveType(floatType));
    }

    public void testIsPrimitiveType_Boolean() {
        PsiType booleanType = PsiType.BOOLEAN;
        assertTrue("boolean should be identified as primitive", TypeValidator.isPrimitiveType(booleanType));
    }

    public void testIsPrimitiveType_Char() {
        PsiType charType = PsiType.CHAR;
        assertTrue("char should be identified as primitive", TypeValidator.isPrimitiveType(charType));
    }

    public void testIsPrimitiveType_Byte() {
        PsiType byteType = PsiType.BYTE;
        assertTrue("byte should be identified as primitive", TypeValidator.isPrimitiveType(byteType));
    }

    public void testIsPrimitiveType_Short() {
        PsiType shortType = PsiType.SHORT;
        assertTrue("short should be identified as primitive", TypeValidator.isPrimitiveType(shortType));
    }

    public void testIsPrimitiveType_String() {
        myFixture.addClass("package java.lang; public class String {}");
        PsiClass stringClass = myFixture.findClass("java.lang.String");
        PsiType stringType = PsiType.getJavaLangString(stringClass.getManager(), stringClass.getResolveScope());
        assertFalse("String should not be identified as primitive", TypeValidator.isPrimitiveType(stringType));
    }

    public void testIsPrimitiveType_Null() {
        assertFalse("null should not be identified as primitive", TypeValidator.isPrimitiveType(null));
    }

    public void testIsPrimitiveTypeName_AllPrimitives() {
        assertTrue(TypeValidator.isPrimitiveTypeName("int"));
        assertTrue(TypeValidator.isPrimitiveTypeName("long"));
        assertTrue(TypeValidator.isPrimitiveTypeName("double"));
        assertTrue(TypeValidator.isPrimitiveTypeName("float"));
        assertTrue(TypeValidator.isPrimitiveTypeName("boolean"));
        assertTrue(TypeValidator.isPrimitiveTypeName("char"));
        assertTrue(TypeValidator.isPrimitiveTypeName("byte"));
        assertTrue(TypeValidator.isPrimitiveTypeName("short"));
    }

    public void testIsPrimitiveTypeName_NonPrimitives() {
        assertFalse(TypeValidator.isPrimitiveTypeName("String"));
        assertFalse(TypeValidator.isPrimitiveTypeName("Integer"));
        assertFalse(TypeValidator.isPrimitiveTypeName("Object"));
        assertFalse(TypeValidator.isPrimitiveTypeName("List"));
    }

    public void testCanBeNullable_Null() {
        assertFalse("null element cannot be nullable", TypeValidator.canBeNullable(null));
    }
}
