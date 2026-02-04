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
 * - @Nullable String[] → String[]?
 * - String @Nullable [] → String?[]
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

        // Find all annotations in the file
        PsiTreeUtil.processElements(root, element -> {
            if (element instanceof PsiAnnotation annotation) {
                processAnnotation(annotation, descriptors);
            }
            return true;
        });

        return descriptors.toArray(FoldingDescriptor.EMPTY_ARRAY);
    }

    private void processAnnotation(@NotNull PsiAnnotation annotation, @NotNull List<FoldingDescriptor> descriptors) {
        // Check if this is a @Nullable annotation
        if (!isNullableAnnotation(annotation)) {
            return;
        }

        PsiElement parent = annotation.getParent();
        if (!(parent instanceof PsiModifierList modifierList)) {
            return;
        }

        PsiElement typeOwner = modifierList.getParent();

        // Handle different contexts
        if (typeOwner instanceof PsiVariable variable) {
            // Field, parameter, or local variable
            handleVariable(annotation, variable, modifierList, descriptors);
        } else if (typeOwner instanceof PsiMethod method) {
            // Return type
            handleMethodReturnType(annotation, method, modifierList, descriptors);
        } else if (typeOwner instanceof PsiTypeElement) {
            // Type argument in generics or array component
            handleTypeElement(annotation, (PsiTypeElement) typeOwner, descriptors);
        }
    }

    private void handleVariable(@NotNull PsiAnnotation annotation,
                               @NotNull PsiVariable variable,
                               @NotNull PsiModifierList modifierList,
                               @NotNull List<FoldingDescriptor> descriptors) {
        PsiTypeElement typeElement = variable.getTypeElement();
        if (typeElement == null) {
            return;
        }

        // Determine if this is array component nullability or array itself
        if (isArrayComponentAnnotation(annotation, typeElement)) {
            // String @Nullable [] → String?[]
            handleArrayComponentNullability(annotation, typeElement, descriptors);
        } else {
            // @Nullable String or @Nullable String[]
            handleTypeNullability(annotation, typeElement, descriptors);
        }
    }

    private void handleMethodReturnType(@NotNull PsiAnnotation annotation,
                                       @NotNull PsiMethod method,
                                       @NotNull PsiModifierList modifierList,
                                       @NotNull List<FoldingDescriptor> descriptors) {
        PsiTypeElement returnTypeElement = method.getReturnTypeElement();
        if (returnTypeElement == null) {
            return;
        }

        handleTypeNullability(annotation, returnTypeElement, descriptors);
    }

    private void handleTypeElement(@NotNull PsiAnnotation annotation,
                                   @NotNull PsiTypeElement typeElement,
                                   @NotNull List<FoldingDescriptor> descriptors) {
        handleTypeNullability(annotation, typeElement, descriptors);
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
        String typeText = typeElement.getText().trim();
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

    private void handleArrayComponentNullability(@NotNull PsiAnnotation annotation,
                                                @NotNull PsiTypeElement typeElement,
                                                @NotNull List<FoldingDescriptor> descriptors) {
        PsiType type = typeElement.getType();
        if (!(type instanceof PsiArrayType arrayType)) {
            return;
        }

        // For String @Nullable [] → String?[]
        // We need to find the component type and insert ? after it
        PsiTypeElement componentTypeElement = getArrayComponentTypeElement(typeElement);
        if (componentTypeElement == null) {
            return;
        }

        TextRange annotationRange = annotation.getTextRange();
        TextRange arrayRange = typeElement.getTextRange();

        // Create placeholder: componentType + ? + []...
        String componentText = componentTypeElement.getText().trim();
        int arrayDimensions = getArrayDimensions(type);
        String brackets = "[]".repeat(arrayDimensions);
        String placeholderText = componentText + "?" + brackets;

        // Fold from annotation to end of type
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

    private boolean isNullableAnnotation(@NotNull PsiAnnotation annotation) {
        String qualifiedName = annotation.getQualifiedName();
        if (NULLABLE_ANNOTATION.equals(qualifiedName)) {
            return true;
        }
        PsiJavaCodeReferenceElement nameRef = annotation.getNameReferenceElement();
        return nameRef != null && NULLABLE_SHORT_NAME.equals(nameRef.getReferenceName());
    }

    private boolean isArrayComponentAnnotation(@NotNull PsiAnnotation annotation, @NotNull PsiTypeElement typeElement) {
        // Check if the annotation appears after the component type (e.g., String @Nullable [])
        PsiType type = typeElement.getType();
        if (!(type instanceof PsiArrayType)) {
            return false;
        }

        // In PSI, "String @Nullable []" has the annotation on the modifier list,
        // but we need to check its position relative to the type element
        int annotationOffset = annotation.getTextOffset();
        int typeOffset = typeElement.getTextOffset();

        // If annotation comes after type start, it's likely array component annotation
        return annotationOffset > typeOffset;
    }

    private PsiTypeElement getArrayComponentTypeElement(@NotNull PsiTypeElement arrayTypeElement) {
        // Navigate to find the component type element
        PsiElement child = arrayTypeElement.getFirstChild();
        while (child != null) {
            if (child instanceof PsiTypeElement) {
                return (PsiTypeElement) child;
            }
            child = child.getNextSibling();
        }
        return null;
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
