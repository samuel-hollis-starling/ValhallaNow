package me.samhollis.valhalla.typing;

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.codeInsight.editorActions.TypedHandlerDelegate;
import com.intellij.codeInsight.folding.CodeFoldingManager;
import com.intellij.codeInsight.hint.HintManager;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.FoldRegion;
import com.intellij.openapi.editor.FoldingModel;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.TextEditor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiArrayType;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementFactory;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiImportList;
import com.intellij.psi.PsiImportStatement;
import com.intellij.psi.PsiJavaCodeReferenceElement;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifierList;
import com.intellij.psi.PsiModifierListOwner;
import com.intellij.psi.PsiType;
import com.intellij.psi.PsiTypeElement;
import com.intellij.psi.PsiVariable;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.util.PsiTreeUtil;
import me.samhollis.valhalla.util.TypeValidator;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * Typed handler that intercepts '?' character and converts it to @Nullable annotation.
 * Examples:
 * - String? → @Nullable String (with import)
 * - List<String?> → List<@Nullable String>
 * - String?[] → @Nullable String[] (array of nullable elements, folds to String?[])
 * - String[]? → String @Nullable [] (nullable array, folds to String[]?)
 *
 * Rejects primitive types with error hint.
 */
public class NullableTypedHandler extends TypedHandlerDelegate {

    private static final String NULLABLE_ANNOTATION_FQN = "org.jspecify.annotations.Nullable";
    private static final String NULLABLE_ANNOTATION = "@Nullable";
    private static final String PRIMITIVE_ERROR_MESSAGE = "Primitive types cannot be nullable";

    @NotNull
    @Override
    public Result charTyped(char c, @NotNull Project project, @NotNull Editor editor, @NotNull PsiFile file) {
        if (c != '?' || !(file instanceof PsiJavaFile)) {
            return Result.CONTINUE;
        }

        int offset = editor.getCaretModel().getOffset();

        // The '?' was just typed, so offset points right after it
        // We need to look at the element before the '?', which is at offset - 2
        PsiElement element = file.findElementAt(offset - 2);

        if (element == null) {
            return Result.CONTINUE;
        }

        // Check if we're right after ']' - this means String[]? (nullable array)
        // In this case, we want to insert annotation between type and brackets
        String textBeforeQuestion = editor.getDocument().getText(
            new TextRange(Math.max(0, offset - 2), offset - 1)
        );
        boolean afterCloseBracket = "]".equals(textBeforeQuestion);

        // Find the type context
        TypeContext context = findTypeContext(element, afterCloseBracket);
        if (context == null) {
            return Result.CONTINUE;
        }

        // Remove the typed '?'
        editor.getDocument().deleteString(offset - 1, offset);

        // Validate not a primitive type
        if (TypeValidator.isPrimitiveType(context.type())) {
            // Show error hint
            HintManager.getInstance().showErrorHint(editor, PRIMITIVE_ERROR_MESSAGE);
            return Result.STOP;
        }

        // Commit the document to update PSI
        PsiDocumentManager.getInstance(project).commitDocument(editor.getDocument());

        // Insert @Nullable annotation
        insertNullableAnnotation(project, context, (PsiJavaFile) file);

        return Result.STOP;
    }

    private TypeContext findTypeContext(@NotNull PsiElement element, boolean afterCloseBracket) {
        // Look for the nearest type element or reference
        PsiElement current = element;

        while (current != null) {
            // Check if we're in or right after a type reference element
            if (current instanceof PsiJavaCodeReferenceElement ref) {
                return analyzeTypeReference(ref, afterCloseBracket);
            }

            // Check if parent is a type reference
            if (current.getParent() instanceof PsiJavaCodeReferenceElement ref) {
                return analyzeTypeReference(ref, afterCloseBracket);
            }

            // Check if we're in a type element
            PsiTypeElement typeElement = PsiTreeUtil.getParentOfType(current, PsiTypeElement.class, false);
            if (typeElement != null) {
                PsiType type = typeElement.getType();

                // If we're after ] and it's an array type, we want ARRAY_COMPONENT position
                if (afterCloseBracket && type instanceof PsiArrayType) {
                    return new TypeContext(typeElement, type, AnnotationPosition.ARRAY_COMPONENT);
                }

                return new TypeContext(typeElement, type, AnnotationPosition.BEFORE_TYPE);
            }

            current = current.getParent();
        }

        return null;
    }

    private TypeContext analyzeTypeReference(@NotNull PsiJavaCodeReferenceElement ref, boolean afterCloseBracket) {
        PsiElement parent = ref.getParent();

        // Check if we're in a type element
        if (parent instanceof PsiTypeElement typeElement) {
            PsiType type = typeElement.getType();

            // Check if this is an array type and determine position
            if (type instanceof PsiArrayType) {
                // If ? typed after ], means String[]? → String @Nullable [] (nullable array)
                if (afterCloseBracket) {
                    return new TypeContext(typeElement, type, AnnotationPosition.ARRAY_COMPONENT);
                }
                // If ? typed after type name (before []), means String?[] → @Nullable String[] (nullable elements)
                return new TypeContext(typeElement, type, AnnotationPosition.BEFORE_TYPE);
            }

            return new TypeContext(typeElement, type, AnnotationPosition.BEFORE_TYPE);
        }

        return null;
    }

    private void insertNullableAnnotation(@NotNull Project project, @NotNull TypeContext context, @NotNull PsiJavaFile file) {
        PsiElementFactory factory = JavaPsiFacade.getElementFactory(project);

        PsiAnnotation addedAnnotation = null;

        // Handle ARRAY_COMPONENT position specially (String @Nullable [])
        if (context.position() == AnnotationPosition.ARRAY_COMPONENT && context.target() instanceof PsiTypeElement typeElement) {
            PsiType type = typeElement.getType();

            if (type instanceof PsiArrayType arrayType) {
                // Get the component type (for String[], this is String)
                PsiType componentType = arrayType.getComponentType();

                // Count array dimensions
                int dimensions = 1;
                PsiType innerType = componentType;
                while (innerType instanceof PsiArrayType inner) {
                    innerType = inner.getComponentType();
                    dimensions++;
                }

                // Create new type text: "InnerType @Nullable [][]..."
                // For String[], we want: String @Nullable []
                // For String[][], we want: String[] @Nullable []
                StringBuilder newTypeText = new StringBuilder();

                if (componentType instanceof PsiArrayType) {
                    // Multi-dimensional: component is already an array
                    newTypeText.append(componentType.getCanonicalText());
                } else {
                    // Single dimension: component is not an array
                    newTypeText.append(componentType.getCanonicalText());
                }

                newTypeText.append(" ").append(NULLABLE_ANNOTATION).append(" []");

                PsiTypeElement newTypeElement = factory.createTypeElementFromText(newTypeText.toString(), typeElement);
                PsiElement replaced = typeElement.replace(newTypeElement);

                // Find the added annotation in the new type element
                if (replaced instanceof PsiTypeElement replacedTypeElement) {
                    addedAnnotation = PsiTreeUtil.findChildOfType(replacedTypeElement, PsiAnnotation.class);
                }

                // Format the code
                CodeStyleManager.getInstance(project).reformat(replaced);
            }
        } else {
            // Create @Nullable annotation
            PsiAnnotation annotation = factory.createAnnotationFromText(NULLABLE_ANNOTATION, context.target());

            // For type-use annotations like @Nullable, add them directly to the type element
            // This ensures correct PSI structure and folding behavior
            if (context.target() instanceof PsiTypeElement typeElement) {
                addedAnnotation = (PsiAnnotation) typeElement.addBefore(annotation, typeElement.getFirstChild());

                // Format the code
                CodeStyleManager.getInstance(project).reformat(typeElement);
            } else {
                // Fallback: try to find modifier list for other cases
                PsiModifierList modifierList = findModifierList(context.target());
                if (modifierList != null) {
                    addedAnnotation = (PsiAnnotation) modifierList.add(annotation);
                    CodeStyleManager.getInstance(project).reformat(modifierList);
                }
            }
        }

        if (addedAnnotation != null) {
            // Add import for JSpecify Nullable
            addImport(file, project);

            // Commit all changes and wait for PSI synchronization
            PsiDocumentManager psiDocumentManager = PsiDocumentManager.getInstance(project);
            psiDocumentManager.performWhenAllCommitted(() -> {
                // Trigger folding update after PSI is fully committed
                triggerFoldingUpdate(project, file);
            });
            psiDocumentManager.commitAllDocuments();
        }
    }

    private void triggerFoldingUpdate(@NotNull Project project, @NotNull PsiJavaFile file) {
        // Get all editors for this file
        FileEditor[] fileEditors = FileEditorManager.getInstance(project).getEditors(file.getVirtualFile());

        if (fileEditors.length == 0) {
            return;
        }

        // Collect all text editors
        List<Editor> editors = new ArrayList<>();
        for (FileEditor fileEditor : fileEditors) {
            if (fileEditor instanceof TextEditor textEditor) {
                editors.add(textEditor.getEditor());
            }
        }

        if (editors.isEmpty()) {
            return;
        }

        CodeFoldingManager foldingManager = CodeFoldingManager.getInstance(project);
        PsiDocumentManager psiDocumentManager = PsiDocumentManager.getInstance(project);

        // Use invokeLater to update folding after PSI changes are processed
        ApplicationManager.getApplication().invokeLater(() -> {
            if (project.isDisposed()) {
                return;
            }

            // Commit document to ensure PSI is up to date
            psiDocumentManager.commitAllDocuments();

            // Restart daemon to trigger folding rebuild
            DaemonCodeAnalyzer.getInstance(project).restart(file);

            // Update fold regions for each editor
            for (Editor editor : editors) {
                if (!editor.isDisposed()) {
                    foldingManager.updateFoldRegions(editor);

                    // Collapse nullable fold regions
                    FoldingModel foldingModel = editor.getFoldingModel();
                    foldingModel.runBatchFoldingOperation(() -> {
                        for (FoldRegion region : foldingModel.getAllFoldRegions()) {
                            String placeholder = region.getPlaceholderText();
                            if (placeholder != null && placeholder.endsWith("?") && region.isExpanded()) {
                                region.setExpanded(false);
                            }
                        }
                    });
                }
            }
        });
    }

    private PsiModifierList findModifierList(@NotNull PsiElement typeElement) {
        PsiElement parent = typeElement.getParent();

        // Check if parent is a variable, method, or other declaration
        if (parent instanceof PsiVariable variable) {
            return variable.getModifierList();
        } else if (parent instanceof PsiMethod method) {
            return method.getModifierList();
        } else if (parent instanceof PsiModifierListOwner owner) {
            return owner.getModifierList();
        }

        // For type arguments, we need to create a modifier list or handle differently
        // In generics context, annotations are applied directly to type
        return null;
    }

    private void addImport(@NotNull PsiJavaFile file, @NotNull Project project) {
        // Check if import already exists
        PsiImportList importList = file.getImportList();
        if (importList == null) {
            return;
        }

        // Check if import is already present
        for (PsiImportStatement importStatement : importList.getImportStatements()) {
            String qualifiedName = importStatement.getQualifiedName();
            if (NULLABLE_ANNOTATION_FQN.equals(qualifiedName)) {
                return; // Already imported
            }
        }

        // Add import
        PsiElementFactory factory = JavaPsiFacade.getElementFactory(project);
        PsiImportStatement importStatement = factory.createImportStatement(
            JavaPsiFacade.getInstance(project)
                .findClass(NULLABLE_ANNOTATION_FQN, file.getResolveScope())
        );

        if (importStatement != null) {
            importList.add(importStatement);
            CodeStyleManager.getInstance(project).reformat(importList);
        }
    }

    private enum AnnotationPosition {
        BEFORE_TYPE,        // @Nullable String or @Nullable String[]
        ARRAY_COMPONENT     // String @Nullable []
    }

    private record TypeContext(
        PsiElement target,
        PsiType type,
        AnnotationPosition position
    ) {}
}
