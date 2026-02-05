package me.samhollis.valhalla.folding;

import com.intellij.lang.ASTNode;
import com.intellij.lang.folding.FoldingBuilderEx;
import com.intellij.lang.folding.FoldingDescriptor;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Folding builder that transforms JSpecify @Nullable annotations into Valhalla-style ? syntax.
 * Examples:
 * - @Nullable String → String?
 * - List<@Nullable String> → List<String?>
 * - @Nullable String[] → String?[] (array of nullable elements - JSpecify semantics)
 * - String @Nullable [] → String[]? (nullable array - the array itself is nullable)
 */
public class NullableFoldingBuilder extends FoldingBuilderEx {

    private static final String NULLABLE_ANNOTATION = "org.jspecify.annotations.Nullable";
    private static final String NULLABLE_SHORT_NAME = "Nullable";

    @NotNull
    @Override
    public FoldingDescriptor[] buildFoldRegions(@NotNull PsiElement root, @NotNull Document document, boolean quick) {
        List<FoldingDescriptor> descriptors = new ArrayList<>();

        if (!(root instanceof PsiJavaFile)) {
            return FoldingDescriptor.EMPTY_ARRAY;
        }

        // Ensure PSI is valid and synchronized with document
        if (!root.isValid()) {
            return FoldingDescriptor.EMPTY_ARRAY;
        }

        // Store document length for validation
        int documentLength = document.getTextLength();

        // Find all annotations in the file
        PsiTreeUtil.processElements(root, element -> {
            if (element instanceof PsiAnnotation annotation) {
                // Validate the annotation is still valid and in bounds
                if (annotation.isValid() && isInBounds(annotation.getTextRange(), documentLength)) {
                    processAnnotation(annotation, descriptors, document);
                }
            }
            return true;
        });

        return descriptors.toArray(FoldingDescriptor.EMPTY_ARRAY);
    }

    /**
     * Check if a text range is within document bounds.
     */
    private boolean isInBounds(TextRange range, int documentLength) {
        return range.getStartOffset() >= 0 &&
               range.getEndOffset() <= documentLength &&
               range.getStartOffset() < range.getEndOffset();
    }

    /**
     * Validate that a folding range is valid for the document.
     */
    private boolean isValidFoldingRange(TextRange range, @NotNull Document document) {
        if (range == null) {
            return false;
        }
        int docLength = document.getTextLength();
        return range.getStartOffset() >= 0 &&
               range.getEndOffset() <= docLength &&
               range.getStartOffset() < range.getEndOffset();
    }

    private void processAnnotation(@NotNull PsiAnnotation annotation, @NotNull List<FoldingDescriptor> descriptors, @NotNull Document document) {
        // Check if this is a @Nullable annotation
        if (!isNullableAnnotation(annotation)) {
            return;
        }

        // Validate that the annotation text matches the document text
        // This ensures we're not using stale PSI
        TextRange annotationRange = annotation.getTextRange();
        if (!isValidFoldingRange(annotationRange, document)) {
            return; // PSI is out of sync with document
        }

        String psiText = annotation.getText();
        String docText = document.getText(annotationRange);
        if (!psiText.equals(docText)) {
            return; // PSI text doesn't match document - stale data
        }

        PsiElement parent = annotation.getParent();

        // Handle type-use annotations (direct child of PsiTypeElement)
        if (parent instanceof PsiTypeElement typeElement) {
            // This is a type-use annotation like List<@Nullable String> or @Nullable inside array component
            // Check if there's an outer @Nullable annotation that would handle this
            if (!hasOuterNullableAnnotation(annotation)) {
                handleTypeUseAnnotation(annotation, typeElement, descriptors, document);
            }
            return;
        }

        if (!(parent instanceof PsiModifierList modifierList)) {
            return;
        }

        PsiElement typeOwner = modifierList.getParent();

        // Handle different contexts
        if (typeOwner instanceof PsiVariable variable) {
            // Field, parameter, or local variable
            handleVariable(annotation, variable, modifierList, descriptors, document);
        } else if (typeOwner instanceof PsiMethod method) {
            // Return type
            handleMethodReturnType(annotation, method, modifierList, descriptors, document);
        } else if (typeOwner instanceof PsiTypeElement) {
            // Type argument in generics or array component
            handleTypeElement(annotation, (PsiTypeElement) typeOwner, descriptors, document);
        }
    }

    /**
     * Check if there's an outer @Nullable annotation that covers this annotation.
     * For example, in @Nullable List<@Nullable String>, the inner @Nullable is covered by the outer one.
     */
    private boolean hasOuterNullableAnnotation(@NotNull PsiAnnotation annotation) {
        // Walk up the PSI tree to find if we're inside another type element that has @Nullable
        PsiElement current = annotation.getParent();
        while (current != null) {
            if (current instanceof PsiTypeElement) {
                // Check the parent of this type element
                PsiElement typeParent = current.getParent();

                // Check if parent is a type element with @Nullable
                if (typeParent instanceof PsiTypeElement parentTypeElement) {
                    for (PsiElement child : parentTypeElement.getChildren()) {
                        if (child instanceof PsiAnnotation && isNullableAnnotation((PsiAnnotation) child)) {
                            // There's an outer @Nullable annotation
                            return true;
                        }
                    }
                }

                // Check if the type element is part of a variable/method that has @Nullable on its modifier list
                PsiElement typeOwner = typeParent;
                if (typeOwner instanceof PsiVariable || typeOwner instanceof PsiMethod) {
                    PsiModifierList modList = null;
                    if (typeOwner instanceof PsiVariable var) {
                        modList = var.getModifierList();
                    } else if (typeOwner instanceof PsiMethod method) {
                        modList = method.getModifierList();
                    }

                    if (modList != null) {
                        for (PsiAnnotation ann : modList.getAnnotations()) {
                            if (isNullableAnnotation(ann)) {
                                return true;
                            }
                        }
                    }
                }
            }
            current = current.getParent();
        }
        return false;
    }

    private void handleVariable(@NotNull PsiAnnotation annotation,
                               @NotNull PsiVariable variable,
                               @NotNull PsiModifierList modifierList,
                               @NotNull List<FoldingDescriptor> descriptors,
                               @NotNull Document document) {
        PsiTypeElement typeElement = variable.getTypeElement();
        if (typeElement == null) {
            return;
        }

        PsiType type = typeElement.getType();
        if (type instanceof PsiArrayType) {
            // For arrays, determine how to fold based on annotation position:
            // @Nullable String[] → String?[] (annotation before type = array of nullable)
            // String @Nullable [] → String[]? (annotation between type and brackets = nullable array)
            if (isArrayComponentAnnotation(annotation, typeElement)) {
                // String @Nullable [] → String[]? (nullable array - array itself is nullable)
                handleTypeNullability(annotation, typeElement, descriptors);
            } else {
                // @Nullable String[] → String?[] (array of nullable elements)
                handleArrayComponentNullability(annotation, typeElement, descriptors, document);
            }
        } else {
            // Non-array type: @Nullable String → String?
            handleTypeNullability(annotation, typeElement, descriptors);
        }
    }

    private void handleMethodReturnType(@NotNull PsiAnnotation annotation,
                                       @NotNull PsiMethod method,
                                       @NotNull PsiModifierList modifierList,
                                       @NotNull List<FoldingDescriptor> descriptors,
                                       @NotNull Document document) {
        PsiTypeElement returnTypeElement = method.getReturnTypeElement();
        if (returnTypeElement == null) {
            return;
        }

        handleTypeNullability(annotation, returnTypeElement, descriptors);
    }

    private void handleTypeElement(@NotNull PsiAnnotation annotation,
                                   @NotNull PsiTypeElement typeElement,
                                   @NotNull List<FoldingDescriptor> descriptors,
                                   @NotNull Document document) {
        handleTypeNullability(annotation, typeElement, descriptors);
    }

    private void handleTypeUseAnnotation(@NotNull PsiAnnotation annotation,
                                         @NotNull PsiTypeElement typeElement,
                                         @NotNull List<FoldingDescriptor> descriptors,
                                         @NotNull Document document) {
        // This handles annotations that are direct children of PsiTypeElement
        // Examples: List<@Nullable String>, Map<String, @Nullable Integer>, String @Nullable[]

        PsiType type = typeElement.getType();

        // Check if this is an array with a type-use annotation (String @Nullable[])
        if (type instanceof PsiArrayType) {
            // For array type-use annotations: String @Nullable[] → String[]?
            handleArrayTypeUseAnnotation(annotation, typeElement, descriptors);
            return;
        }

        // Calculate the folding range from annotation to end of the type element
        TextRange annotationRange = annotation.getTextRange();
        TextRange typeRange = typeElement.getTextRange();

        int startOffset = annotationRange.getStartOffset();
        int endOffset = typeRange.getEndOffset();

        TextRange foldingRange = new TextRange(startOffset, endOffset);

        // Build the type text without the annotation, properly handling nested nullables
        String placeholderText = buildTypeTextWithNullableStripped(typeElement) + "?";

        descriptors.add(new FoldingDescriptor(
            annotation.getNode(),
            foldingRange,
            null,
            placeholderText,
            true, // collapsed by default
            java.util.Collections.emptySet()
        ));
    }

    /**
     * Handle array type-use annotation like String @Nullable[] → String[]? (nullable array)
     * This is when the annotation is between the component type and brackets.
     * Also handles @Nullable String @Nullable[] → String?[]?
     */
    private void handleArrayTypeUseAnnotation(@NotNull PsiAnnotation annotation,
                                              @NotNull PsiTypeElement typeElement,
                                              @NotNull List<FoldingDescriptor> descriptors) {
        // Find the component element (could be PsiTypeElement or PsiJavaCodeReferenceElement)
        PsiElement componentElement = getArrayComponentElement(typeElement);
        if (componentElement == null) {
            return;
        }

        // Fold from component type start to end of array type
        TextRange componentRange = componentElement.getTextRange();
        TextRange arrayRange = typeElement.getTextRange();
        TextRange foldingRange = new TextRange(componentRange.getStartOffset(), arrayRange.getEndOffset());

        // Build placeholder: componentType + brackets + ? (nullable array)
        String componentText = getArrayComponentText(typeElement);
        PsiType type = typeElement.getType();
        int arrayDimensions = getArrayDimensions(type);
        String brackets = "[]".repeat(arrayDimensions);

        // Check if there's also a modifier list @Nullable (e.g., @Nullable String @Nullable[])
        // In this case, we need to add ? after the component type as well
        boolean hasModifierListNullable = hasModifierListNullableAnnotation(typeElement);
        String placeholderText = componentText + (hasModifierListNullable ? "?" : "") + brackets + "?";

        descriptors.add(new FoldingDescriptor(
            annotation.getNode(),
            foldingRange,
            null,
            placeholderText,
            true, // collapsed by default
            java.util.Collections.emptySet()
        ));
    }

    /**
     * Check if the type element's parent variable/method has a @Nullable annotation on its modifier list.
     * This is for cases like "@Nullable String @Nullable[]" where the first @Nullable is on the modifier list.
     */
    private boolean hasModifierListNullableAnnotation(@NotNull PsiTypeElement typeElement) {
        PsiElement parent = typeElement.getParent();
        PsiModifierList modifierList = null;

        if (parent instanceof PsiVariable variable) {
            modifierList = variable.getModifierList();
        } else if (parent instanceof PsiMethod method) {
            modifierList = method.getModifierList();
        }

        if (modifierList != null) {
            for (PsiAnnotation ann : modifierList.getAnnotations()) {
                if (isNullableAnnotation(ann)) {
                    return true;
                }
            }
        }
        return false;
    }

    private void handleTypeNullability(@NotNull PsiAnnotation annotation,
                                      @NotNull PsiTypeElement typeElement,
                                      @NotNull List<FoldingDescriptor> descriptors) {
        // Create folding region from annotation to end of type
        TextRange annotationRange = annotation.getTextRange();
        TextRange typeRange = typeElement.getTextRange();

        // Include any whitespace between annotation and type
        int startOffset = annotationRange.getStartOffset();
        int endOffset = typeRange.getEndOffset();

        TextRange foldingRange = new TextRange(startOffset, endOffset);

        // Get the type text and append ?
        // Build type text recursively to handle nested @Nullable annotations
        String typeText = buildTypeTextWithNullable(typeElement);
        String placeholderText = typeText + "?";

        descriptors.add(new FoldingDescriptor(
            annotation.getNode(),
            foldingRange,
            null,
            placeholderText,
            true, // collapsed by default
            java.util.Collections.emptySet()
        ));
    }

    /**
     * Build type text, replacing any @Nullable annotations with ? suffix.
     * This ensures nested @Nullable annotations are properly represented.
     * For example: List<@Nullable String> becomes List<String?>
     *              @Nullable String @Nullable[] becomes String?[]?
     */
    private String buildTypeTextWithNullable(@NotNull PsiTypeElement typeElement) {
        StringBuilder result = new StringBuilder();

        // Check if this is an array type with a component annotation
        // (e.g., String @Nullable[] where @Nullable is between type and brackets)
        PsiType type = typeElement.getType();
        if (type instanceof PsiArrayType) {
            return buildArrayTypeTextWithNullable(typeElement);
        }

        for (PsiElement child : typeElement.getChildren()) {
            if (child instanceof PsiJavaCodeReferenceElement refElement) {
                // Handle the main type reference (e.g., List, String, Map)
                // Use getReferenceName() to get just the class name without generics
                result.append(refElement.getReferenceName());

                // Now handle the type parameters if present
                PsiReferenceParameterList paramList = refElement.getParameterList();
                if (paramList != null && paramList.getTypeParameterElements().length > 0) {
                    result.append(buildGenericArgsText(paramList));
                }
            } else if (child instanceof PsiTypeElement) {
                // Recursively handle nested type elements (e.g., type arguments)
                result.append(buildTypeTextWithNullable((PsiTypeElement) child));
            } else if (child instanceof PsiAnnotation) {
                PsiAnnotation annotation = (PsiAnnotation) child;
                if (isNullableAnnotation(annotation)) {
                    // Skip @Nullable - it will be replaced with ? suffix
                    continue;
                } else {
                    // Keep other annotations
                    result.append(child.getText());
                }
            } else if (child instanceof PsiReferenceParameterList) {
                // Handle generic type arguments <...> - this is a fallback,
                // usually handled via PsiJavaCodeReferenceElement above
                result.append(buildGenericArgsText((PsiReferenceParameterList) child));
            } else if (child instanceof PsiKeyword || child instanceof PsiWhiteSpace) {
                // Include keywords and whitespace, but trim whitespace around @Nullable
                if (!(child instanceof PsiWhiteSpace) || result.length() == 0 ||
                    !Character.isWhitespace(result.charAt(result.length() - 1))) {
                    result.append(child.getText());
                }
            }
        }

        return result.toString().trim();
    }

    /**
     * Build array type text, handling component @Nullable annotations.
     * For example: String @Nullable[] becomes String[]? (nullable array)
     *              @Nullable String[] stays as String[] (outer fold will add ? after component → String?[])
     */
    private String buildArrayTypeTextWithNullable(@NotNull PsiTypeElement typeElement) {
        StringBuilder result = new StringBuilder();
        boolean hasComponentNullable = false;
        StringBuilder brackets = new StringBuilder();

        for (PsiElement child : typeElement.getChildren()) {
            if (child instanceof PsiTypeElement) {
                // This is a nested type element (e.g., for generics or complex types)
                result.append(buildTypeTextWithNullable((PsiTypeElement) child));
            } else if (child instanceof PsiJavaCodeReferenceElement refElement) {
                // This is a simple type reference (e.g., String in String[])
                // Use getReferenceName() to avoid including any type parameters twice
                result.append(refElement.getReferenceName());

                // Handle type parameters if present
                PsiReferenceParameterList paramList = refElement.getParameterList();
                if (paramList != null && paramList.getTypeParameterElements().length > 0) {
                    result.append(buildGenericArgsText(paramList));
                }
            } else if (child instanceof PsiAnnotation) {
                PsiAnnotation annotation = (PsiAnnotation) child;
                if (isNullableAnnotation(annotation)) {
                    // This is a type-use annotation like String @Nullable[]
                    // The ? should go at the END (String[]?)
                    hasComponentNullable = true;
                } else {
                    result.append(child.getText());
                }
            } else if (child instanceof PsiJavaToken) {
                String text = child.getText();
                if (text.equals("[") || text.equals("]")) {
                    brackets.append(text);
                }
            } else if (child instanceof PsiWhiteSpace) {
                // Skip whitespace between elements
            }
        }

        // Add the array brackets first
        result.append(brackets);

        // If there was a type-use @Nullable (String @Nullable[]), add ? at the end
        if (hasComponentNullable) {
            result.append("?");
        }

        return result.toString();
    }

    /**
     * Build generic type arguments text.
     * For example: <@Nullable String, Integer> becomes <String?, Integer>
     */
    private String buildGenericArgsText(@NotNull PsiReferenceParameterList paramList) {
        StringBuilder result = new StringBuilder();
        result.append("<");
        PsiTypeElement[] typeArgs = paramList.getTypeParameterElements();
        for (int i = 0; i < typeArgs.length; i++) {
            if (i > 0) {
                result.append(", ");
            }

            PsiTypeElement typeArg = typeArgs[i];
            // Check if this type argument has a @Nullable annotation
            boolean hasNullable = hasDirectNullableAnnotation(typeArg);

            if (hasNullable) {
                // Build the type text recursively (handles nested generics), then add ? suffix
                String typeArgText = buildTypeTextWithNullableStripped(typeArg);
                result.append(typeArgText).append("?");
            } else {
                // Recursively process in case there are nested generics
                result.append(buildTypeTextWithNullable(typeArg));
            }
        }
        result.append(">");
        return result.toString();
    }

    /**
     * Check if a type element has a direct @Nullable annotation (not in nested generics).
     */
    private boolean hasDirectNullableAnnotation(@NotNull PsiTypeElement typeElement) {
        for (PsiElement child : typeElement.getChildren()) {
            if (child instanceof PsiAnnotation && isNullableAnnotation((PsiAnnotation) child)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Build type text with the direct @Nullable stripped but nested nullables handled.
     * For example: @Nullable List<@Nullable String> becomes List<String?>
     */
    private String buildTypeTextWithNullableStripped(@NotNull PsiTypeElement typeElement) {
        StringBuilder result = new StringBuilder();

        // Check if this is an array type
        PsiType type = typeElement.getType();
        if (type instanceof PsiArrayType) {
            return buildArrayTypeTextWithNullableStripped(typeElement);
        }

        for (PsiElement child : typeElement.getChildren()) {
            if (child instanceof PsiAnnotation) {
                PsiAnnotation annotation = (PsiAnnotation) child;
                if (isNullableAnnotation(annotation)) {
                    // Skip the direct @Nullable annotation
                    continue;
                } else {
                    result.append(child.getText());
                }
            } else if (child instanceof PsiJavaCodeReferenceElement refElement) {
                // Use getReferenceName() to get just the class name without generics
                result.append(refElement.getReferenceName());

                // Now handle the type parameters if present
                PsiReferenceParameterList paramList = refElement.getParameterList();
                if (paramList != null && paramList.getTypeParameterElements().length > 0) {
                    result.append(buildGenericArgsText(paramList));
                }
            } else if (child instanceof PsiTypeElement) {
                result.append(buildTypeTextWithNullable((PsiTypeElement) child));
            } else if (child instanceof PsiReferenceParameterList) {
                // Fallback - usually handled via PsiJavaCodeReferenceElement above
                result.append(buildGenericArgsText((PsiReferenceParameterList) child));
            } else if (child instanceof PsiWhiteSpace) {
                // Skip whitespace after stripped annotation
                if (result.length() > 0 && !Character.isWhitespace(result.charAt(result.length() - 1))) {
                    // Only add if not immediately after nothing/whitespace
                }
            } else if (child instanceof PsiKeyword) {
                result.append(child.getText());
            }
        }

        return result.toString().trim();
    }

    /**
     * Build array type text with direct @Nullable stripped.
     * For type-use annotations (String @Nullable[]), the ? goes at the end → String[]?
     */
    private String buildArrayTypeTextWithNullableStripped(@NotNull PsiTypeElement typeElement) {
        StringBuilder result = new StringBuilder();
        boolean hasComponentNullable = false;
        StringBuilder brackets = new StringBuilder();

        for (PsiElement child : typeElement.getChildren()) {
            if (child instanceof PsiTypeElement) {
                result.append(buildTypeTextWithNullable((PsiTypeElement) child));
            } else if (child instanceof PsiJavaCodeReferenceElement refElement) {
                // Simple type reference (e.g., String in String[])
                // Use getReferenceName() to avoid including type parameters twice
                result.append(refElement.getReferenceName());

                // Handle type parameters if present
                PsiReferenceParameterList paramList = refElement.getParameterList();
                if (paramList != null && paramList.getTypeParameterElements().length > 0) {
                    result.append(buildGenericArgsText(paramList));
                }
            } else if (child instanceof PsiAnnotation) {
                PsiAnnotation annotation = (PsiAnnotation) child;
                if (isNullableAnnotation(annotation)) {
                    // This is a type-use annotation - mark it but don't include it
                    hasComponentNullable = true;
                } else {
                    result.append(child.getText());
                }
            } else if (child instanceof PsiJavaToken) {
                String text = child.getText();
                if (text.equals("[") || text.equals("]")) {
                    brackets.append(text);
                }
            }
        }

        // Add brackets first
        result.append(brackets);

        // If there was a type-use @Nullable, add ? at the end
        if (hasComponentNullable) {
            result.append("?");
        }

        return result.toString();
    }

    private void handleArrayComponentNullability(@NotNull PsiAnnotation annotation,
                                                @NotNull PsiTypeElement typeElement,
                                                @NotNull List<FoldingDescriptor> descriptors,
                                                @NotNull Document document) {
        PsiType type = typeElement.getType();
        if (!(type instanceof PsiArrayType arrayType)) {
            return;
        }

        // For @Nullable String[] → String?[] (array of nullable elements)
        // The annotation is before the type, so it applies to the component type
        PsiElement componentElement = getArrayComponentElement(typeElement);
        if (componentElement == null) {
            return;
        }

        // Calculate the folding range: from annotation to end of array brackets
        TextRange annotationRange = annotation.getTextRange();
        TextRange arrayRange = typeElement.getTextRange();

        // Validate ranges are within document bounds
        if (!isValidFoldingRange(annotationRange, document) || !isValidFoldingRange(arrayRange, document)) {
            return; // PSI is out of sync
        }

        // Create placeholder: componentType + ? + []...
        String componentText = getArrayComponentText(typeElement);
        int arrayDimensions = getArrayDimensions(type);
        String brackets = "[]".repeat(arrayDimensions);

        // Check if there's also a type-use @Nullable between the type and brackets
        // (e.g., @Nullable String @Nullable [] → String?[]?)
        boolean hasArrayNullable = hasTypeUseNullableAnnotation(typeElement);

        // Fallback: use document text instead of PSI text for accurate detection
        // This is important when PSI might not be fully synchronized after edits
        if (!hasArrayNullable) {
            // Get the actual document text for the type element
            String docTypeText = document.getText(arrayRange);
            int bracketIndex = docTypeText.indexOf('[');
            if (bracketIndex > 0) {
                String beforeBracket = docTypeText.substring(0, bracketIndex);
                // Find @Nullable after the type name (not before it)
                // For "String @Nullable []", we want to find @Nullable after String
                int typeNameEnd = findTypeNameEnd(beforeBracket);
                if (typeNameEnd > 0 && typeNameEnd < beforeBracket.length()) {
                    String afterTypeName = beforeBracket.substring(typeNameEnd);
                    if (afterTypeName.contains("@Nullable") || afterTypeName.contains("Nullable")) {
                        hasArrayNullable = true;
                    }
                }
            }
        }

        String placeholderText = componentText + "?" + brackets + (hasArrayNullable ? "?" : "");

        // Fold from annotation start to end of array type
        TextRange foldingRange = new TextRange(annotationRange.getStartOffset(), arrayRange.getEndOffset());

        descriptors.add(new FoldingDescriptor(
            annotation.getNode(),
            foldingRange,
            null,
            placeholderText,
            true, // collapsed by default
            java.util.Collections.emptySet()
        ));
    }

    /**
     * Check if the array type has a type-use @Nullable annotation between the component type and brackets.
     * For example, in "String @Nullable []" or "@Nullable String @Nullable []".
     */
    private boolean hasTypeUseNullableAnnotation(@NotNull PsiTypeElement arrayTypeElement) {
        // Look for @Nullable annotations that are direct children of the array type element
        // (not part of the component type)
        for (PsiElement child : arrayTypeElement.getChildren()) {
            if (child instanceof PsiAnnotation annotation) {
                if (isNullableAnnotation(annotation)) {
                    return true;
                }
            }
        }

        // Also check using PsiTreeUtil to find annotations that might be nested
        for (PsiAnnotation annotation : PsiTreeUtil.findChildrenOfType(arrayTypeElement, PsiAnnotation.class)) {
            if (isNullableAnnotation(annotation)) {
                // Make sure this annotation is within the array type element bounds,
                // not in a nested type element (like a generic type argument)
                PsiElement parent = annotation.getParent();
                if (parent == arrayTypeElement) {
                    return true;
                }
            }
        }

        // Text-based fallback: check the actual text of the type element
        // This is important when PSI might not be fully synchronized after edits
        String typeText = arrayTypeElement.getText();
        int bracketIndex = typeText.indexOf('[');
        if (bracketIndex > 0) {
            String beforeBracket = typeText.substring(0, bracketIndex);
            // Find @Nullable after the type name (not before it)
            // For "String @Nullable []", we want to find @Nullable after String
            // For "@Nullable String []", we don't want to match (that's a different annotation)
            int typeNameEnd = findTypeNameEnd(beforeBracket);
            if (typeNameEnd > 0 && typeNameEnd < beforeBracket.length()) {
                String afterTypeName = beforeBracket.substring(typeNameEnd);
                if (afterTypeName.contains("@Nullable") || afterTypeName.contains("Nullable")) {
                    return true;
                }
            }
        }

        return false;
    }

    /**
     * Find where the type name ends in a string like "String @Nullable" or "List<T> @Nullable".
     * Returns the position after the type name (including any generic parameters).
     */
    private int findTypeNameEnd(String text) {
        int pos = 0;
        int len = text.length();

        // Skip leading whitespace and annotations
        while (pos < len && (Character.isWhitespace(text.charAt(pos)) || text.charAt(pos) == '@')) {
            if (text.charAt(pos) == '@') {
                // Skip annotation
                while (pos < len && !Character.isWhitespace(text.charAt(pos))) {
                    pos++;
                }
            } else {
                pos++;
            }
        }

        // Now we should be at the type name
        int typeStart = pos;

        // Skip the type name (identifier)
        while (pos < len && (Character.isJavaIdentifierPart(text.charAt(pos)))) {
            pos++;
        }

        // Skip generic parameters if any (<...>)
        if (pos < len && text.charAt(pos) == '<') {
            int depth = 1;
            pos++;
            while (pos < len && depth > 0) {
                if (text.charAt(pos) == '<') depth++;
                else if (text.charAt(pos) == '>') depth--;
                pos++;
            }
        }

        return pos;
    }

    private boolean isNullableAnnotation(@NotNull PsiAnnotation annotation) {
        String qualifiedName = annotation.getQualifiedName();
        if (NULLABLE_ANNOTATION.equals(qualifiedName)) {
            return true;
        }
        PsiJavaCodeReferenceElement nameRef = annotation.getNameReferenceElement();
        return nameRef != null && NULLABLE_SHORT_NAME.equals(nameRef.getReferenceName());
    }

    private boolean isArrayComponentAnnotation(@NotNull PsiAnnotation annotation, @NotNull PsiTypeElement typeElement) {
        // Check if the annotation appears between the component type and brackets (e.g., String @Nullable [])
        // Returns true for String @Nullable[] (annotation between type and brackets)
        // Returns false for @Nullable String[] (annotation before type)
        PsiType type = typeElement.getType();
        if (!(type instanceof PsiArrayType)) {
            return false;
        }

        // In our semantics, the position of @Nullable determines its meaning:
        // @Nullable String[] → String?[] (annotation before type = array of nullable elements)
        // String @Nullable [] → String[]? (annotation between type and brackets = nullable array)

        // Get text positions
        int annotationOffset = annotation.getTextOffset();
        int typeOffset = typeElement.getTextOffset();

        // Find where the base component type ends (before the first '[')
        String typeText = typeElement.getText();
        int firstBracketIndex = typeText.indexOf('[');
        if (firstBracketIndex == -1) {
            return false; // No brackets found
        }

        // Calculate the absolute position of the first bracket
        int firstBracketOffset = typeOffset + firstBracketIndex;

        // If annotation is between the base type and the first bracket, it's a "component annotation"
        // String @Nullable [] - annotation comes after "String" but before "[]"
        return annotationOffset > typeOffset && annotationOffset < firstBracketOffset;
    }

    private PsiTypeElement getArrayComponentTypeElement(@NotNull PsiTypeElement arrayTypeElement) {
        // Navigate to find the component type element
        // For complex array types (e.g., List<String>[]), there will be a PsiTypeElement child
        // For simple types (e.g., String[]), the first child is PsiJavaCodeReferenceElement
        PsiElement child = arrayTypeElement.getFirstChild();
        while (child != null) {
            if (child instanceof PsiTypeElement) {
                return (PsiTypeElement) child;
            }
            child = child.getNextSibling();
        }
        return null;
    }

    /**
     * Get the first component element (either PsiTypeElement or PsiJavaCodeReferenceElement) from an array type.
     * Returns the element that represents the component type (e.g., "String" in String[]).
     */
    private PsiElement getArrayComponentElement(@NotNull PsiTypeElement arrayTypeElement) {
        PsiElement child = arrayTypeElement.getFirstChild();
        while (child != null) {
            if (child instanceof PsiTypeElement || child instanceof PsiJavaCodeReferenceElement) {
                return child;
            }
            child = child.getNextSibling();
        }
        return null;
    }

    /**
     * Get the text for the component type of an array type element.
     */
    private String getArrayComponentText(@NotNull PsiTypeElement arrayTypeElement) {
        PsiTypeElement componentTypeElement = getArrayComponentTypeElement(arrayTypeElement);
        if (componentTypeElement != null) {
            return buildTypeTextWithNullable(componentTypeElement);
        }

        // For simple types, find the PsiJavaCodeReferenceElement
        PsiElement child = arrayTypeElement.getFirstChild();
        while (child != null) {
            if (child instanceof PsiJavaCodeReferenceElement) {
                return child.getText();
            }
            child = child.getNextSibling();
        }
        return "";
    }

    private int getArrayDimensions(@NotNull PsiType type) {
        int dimensions = 0;
        PsiType currentType = type;
        while (currentType instanceof PsiArrayType arrayType) {
            dimensions++;
            currentType = arrayType.getComponentType();
        }
        return dimensions;
    }

    @Nullable
    @Override
    public String getPlaceholderText(@NotNull ASTNode node) {
        // Placeholder text is provided in the FoldingDescriptor
        return null;
    }

    @Override
    public boolean isCollapsedByDefault(@NotNull ASTNode node) {
        // Always collapse @Nullable annotations by default
        return true;
    }
}
