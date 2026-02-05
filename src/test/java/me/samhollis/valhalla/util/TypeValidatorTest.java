package me.samhollis.valhalla.util;

import com.intellij.psi.*;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import junit.framework.TestCase;

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
        TestCase.assertTrue("int should be identified as primitive", TypeValidator.isPrimitiveType(intType));
    }

    public void testIsPrimitiveType_Long() {
        PsiType longType = PsiType.LONG;
        TestCase.assertTrue("long should be identified as primitive", TypeValidator.isPrimitiveType(longType));
    }

    public void testIsPrimitiveType_Double() {
        PsiType doubleType = PsiType.DOUBLE;
        TestCase.assertTrue("double should be identified as primitive", TypeValidator.isPrimitiveType(doubleType));
    }

    public void testIsPrimitiveType_Float() {
        PsiType floatType = PsiType.FLOAT;
        TestCase.assertTrue("float should be identified as primitive", TypeValidator.isPrimitiveType(floatType));
    }

    public void testIsPrimitiveType_Boolean() {
        PsiType booleanType = PsiType.BOOLEAN;
        TestCase.assertTrue("boolean should be identified as primitive", TypeValidator.isPrimitiveType(booleanType));
    }

    public void testIsPrimitiveType_Char() {
        PsiType charType = PsiType.CHAR;
        TestCase.assertTrue("char should be identified as primitive", TypeValidator.isPrimitiveType(charType));
    }

    public void testIsPrimitiveType_Byte() {
        PsiType byteType = PsiType.BYTE;
        TestCase.assertTrue("byte should be identified as primitive", TypeValidator.isPrimitiveType(byteType));
    }

    public void testIsPrimitiveType_Short() {
        PsiType shortType = PsiType.SHORT;
        TestCase.assertTrue("short should be identified as primitive", TypeValidator.isPrimitiveType(shortType));
    }

    public void testIsPrimitiveType_String() {
        myFixture.addClass("package java.lang; public class String {}");
        PsiClass stringClass = myFixture.findClass("java.lang.String");
        PsiType stringType = PsiType.getJavaLangString(stringClass.getManager(), stringClass.getResolveScope());
        TestCase.assertFalse("String should not be identified as primitive", TypeValidator.isPrimitiveType(stringType));
    }

    public void testIsPrimitiveType_Null() {
        TestCase.assertFalse("null should not be identified as primitive", TypeValidator.isPrimitiveType(null));
    }

    public void testIsPrimitiveTypeName_AllPrimitives() {
        TestCase.assertTrue(TypeValidator.isPrimitiveTypeName("int"));
        TestCase.assertTrue(TypeValidator.isPrimitiveTypeName("long"));
        TestCase.assertTrue(TypeValidator.isPrimitiveTypeName("double"));
        TestCase.assertTrue(TypeValidator.isPrimitiveTypeName("float"));
        TestCase.assertTrue(TypeValidator.isPrimitiveTypeName("boolean"));
        TestCase.assertTrue(TypeValidator.isPrimitiveTypeName("char"));
        TestCase.assertTrue(TypeValidator.isPrimitiveTypeName("byte"));
        TestCase.assertTrue(TypeValidator.isPrimitiveTypeName("short"));
    }

    public void testIsPrimitiveTypeName_NonPrimitives() {
        TestCase.assertFalse(TypeValidator.isPrimitiveTypeName("String"));
        TestCase.assertFalse(TypeValidator.isPrimitiveTypeName("Integer"));
        TestCase.assertFalse(TypeValidator.isPrimitiveTypeName("Object"));
        TestCase.assertFalse(TypeValidator.isPrimitiveTypeName("List"));
    }

    public void testCanBeNullable_Null() {
        TestCase.assertFalse("null element cannot be nullable", TypeValidator.canBeNullable(null));
    }
}
