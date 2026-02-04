package me.samhollis.valhalla.util;

import com.intellij.psi.*;
import org.jetbrains.annotations.NotNull;
import org.jspecify.annotations.Nullable;

/**
 * Utility class for validating Java types in the context of nullable type annotations.
 */
public class TypeValidator {

    private TypeValidator() {
        // Utility class - prevent instantiation
    }

    /**
     * Checks if the given PsiType represents a Java primitive type.
     * Primitive types cannot be nullable in Project Valhalla.
     *
     * @param type the type to check
     * @return true if the type is a primitive type (int, long, double, etc.), false otherwise
     */
    public static boolean isPrimitiveType(@Nullable PsiType type) {
        if (type == null) {
            return false;
        }

        return type instanceof PsiPrimitiveType;
    }

    /**
     * Checks if the given type name represents a primitive type keyword.
     *
     * @param typeName the name of the type
     * @return true if the name is a primitive type keyword
     */
    public static boolean isPrimitiveTypeName(@NotNull String typeName) {
        return switch (typeName) {
            case "byte", "short", "int", "long", "float", "double", "char", "boolean" -> true;
            default -> false;
        };
    }

    /**
     * Validates whether a nullable annotation can be applied to the given element.
     *
     * @param element the element to validate
     * @return true if nullable can be applied, false if it's a primitive type
     */
    public static boolean canBeNullable(@Nullable PsiElement element) {
        if (element == null) {
            return false;
        }

        // Check if element is a type element
        if (element instanceof PsiTypeElement typeElement) {
            return !isPrimitiveType(typeElement.getType());
        }

        // Check if element has a text representation that matches a primitive
        String text = element.getText();
        if (text != null) {
            return !isPrimitiveTypeName(text.trim());
        }

        return true;
    }
}
