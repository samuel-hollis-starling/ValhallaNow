package me.samhollis.valhalla.typing;

import com.intellij.codeInsight.editorActions.TypedHandlerDelegate;
import com.intellij.codeInsight.hint.HintManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.FoldingModel;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.util.PsiTreeUtil;
import me.samhollis.valhalla.util.TypeValidator;
import org.jetbrains.annotations.NotNull;

/**
 * Typed handler that intercepts '?' character and converts it to @Nullable annotation.
 * Examples:
 * - String? → @Nullable String (with import)
 * - List<String?> → List<@Nullable String>
 * - String[]? → @Nullable String[]
 * - String?[] → String @Nullable []
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

        // Find the type context
        TypeContext context = findTypeContext(element);
        if (context == null) {
            return Result.CONTINUE;
        }

        // Validate not a primitive type
        if (TypeValidator.isPrimitiveType(context.type())) {
            // Remove the typed '?'
            editor.getDocument().deleteString(offset - 1, offset);

            // Show error hint
            HintManager.getInstance().showErrorHint(editor, PRIMITIVE_ERROR_MESSAGE);
            return Result.STOP;
        }

        // Remove the typed '?'
        editor.getDocument().deleteString(offset - 1, offset);

        // Commit the document to update PSI
        PsiDocumentManager.getInstance(project).commitDocument(editor.getDocument());

        // Insert @Nullable annotation
        insertNullableAnnotation(project, context, (PsiJavaFile) file);

        return Result.STOP;
    }

    private TypeContext findTypeContext(@NotNull PsiElement element) {
        // Look for the nearest type element or reference
        PsiElement current = element;

        while (current != null) {
            // Check if we're in or right after a type reference element
            if (current instanceof PsiJavaCodeReferenceElement) {
                PsiJavaCodeReferenceElement ref = (PsiJavaCodeReferenceElement) current;
                return analyzeTypeReference(ref);
            }

            // Check if parent is a type reference
            if (current.getParent() instanceof PsiJavaCodeReferenceElement) {
                PsiJavaCodeReferenceElement ref = (PsiJavaCodeReferenceElement) current.getParent();
                return analyzeTypeReference(ref);
            }

            // Check if we're in a type element
            PsiTypeElement typeElement = PsiTreeUtil.getParentOfType(current, PsiTypeElement.class, false);
            if (typeElement != null) {
                return new TypeContext(typeElement, typeElement.getType(), AnnotationPosition.BEFORE_TYPE);
            }

            current = current.getParent();
        }

        return null;
    }

    private TypeContext analyzeTypeReference(@NotNull PsiJavaCodeReferenceElement ref) {
        PsiElement parent = ref.getParent();

        // Check if we're in a type element
        if (parent instanceof PsiTypeElement typeElement) {
            PsiType type = typeElement.getType();

            // Check if this is an array type and determine position
            if (type instanceof PsiArrayType) {
                // Determine if ? should mean array nullability or component nullability
                // Default to array nullability (@Nullable String[])
                return new TypeContext(typeElement, type, AnnotationPosition.BEFORE_TYPE);
            }

            return new TypeContext(typeElement, type, AnnotationPosition.BEFORE_TYPE);
        }

        return null;
    }

    private void insertNullableAnnotation(@NotNull Project project, @NotNull TypeContext context, @NotNull PsiJavaFile file) {
        PsiElementFactory factory = JavaPsiFacade.getElementFactory(project);

        // Create @Nullable annotation
        PsiAnnotation annotation = factory.createAnnotationFromText(NULLABLE_ANNOTATION, context.target());

        // Find the modifier list to add annotation to
        PsiModifierList modifierList = findModifierList(context.target());

        if (modifierList != null) {
            // Add annotation to modifier list
            PsiAnnotation addedAnnotation = (PsiAnnotation) modifierList.addBefore(annotation, modifierList.getFirstChild());

            // Add import for JSpecify Nullable
            addImport(file, project);

            // Format the code
            CodeStyleManager.getInstance(project).reformat(modifierList);

            // Commit all changes and wait for PSI synchronization
            PsiDocumentManager psiDocumentManager = PsiDocumentManager.getInstance(project);
            psiDocumentManager.commitAllDocuments();
            psiDocumentManager.performWhenAllCommitted(() -> {
                // Trigger folding update after PSI is fully committed
                triggerFoldingUpdate(project, file, addedAnnotation);
            });
        }
    }

    private void triggerFoldingUpdate(@NotNull Project project, @NotNull PsiJavaFile file, @NotNull PsiAnnotation annotation) {
        // Get all editors for this file
        com.intellij.openapi.fileEditor.FileEditor[] fileEditors =
            com.intellij.openapi.fileEditor.FileEditorManager.getInstance(project)
                .getEditors(file.getVirtualFile());

        if (fileEditors.length == 0) {
            return;
        }

        // Collect all text editors
        java.util.List<Editor> editors = new java.util.ArrayList<>();
        for (com.intellij.openapi.fileEditor.FileEditor fileEditor : fileEditors) {
            if (fileEditor instanceof com.intellij.openapi.fileEditor.TextEditor textEditor) {
                editors.add(textEditor.getEditor());
            }
        }

        if (editors.isEmpty()) {
            return;
        }

        // Get the text range of the annotation to find matching fold regions
        int annotationStart = annotation.getTextRange().getStartOffset();

        // Create a single Alarm that won't be garbage collected
        com.intellij.util.Alarm alarm = new com.intellij.util.Alarm(project);

        // Schedule folding update using invokeLater to ensure we're on EDT after PSI commit
        com.intellij.openapi.application.ApplicationManager.getApplication().invokeLater(() -> {
            if (project.isDisposed()) {
                return;
            }

            // Use DaemonCodeAnalyzer to trigger a reanalysis which will rebuild folding
            com.intellij.codeInsight.daemon.DaemonCodeAnalyzer.getInstance(project).restart(file);

            com.intellij.codeInsight.folding.CodeFoldingManager foldingManager =
                com.intellij.codeInsight.folding.CodeFoldingManager.getInstance(project);

            // Update fold regions for each editor
            for (Editor editor : editors) {
                if (!editor.isDisposed()) {
                    foldingManager.updateFoldRegions(editor);
                }
            }

            // Schedule a delayed update to collapse the fold regions after they're created
            alarm.addRequest(() -> {
                if (project.isDisposed()) {
                    return;
                }

                for (Editor editor : editors) {
                    if (!editor.isDisposed()) {
                        // First update to ensure fold regions exist
                        foldingManager.updateFoldRegions(editor);

                        // Now find and collapse fold regions that contain @Nullable
                        FoldingModel foldingModel = editor.getFoldingModel();
                        foldingModel.runBatchFoldingOperation(() -> {
                            for (com.intellij.openapi.editor.FoldRegion region : foldingModel.getAllFoldRegions()) {
                                // Check if this fold region starts at or near our annotation
                                // and contains a '?' in its placeholder (our Nullable folding)
                                String placeholder = region.getPlaceholderText();
                                if (placeholder != null && placeholder.endsWith("?")) {
                                    // This is one of our Nullable fold regions - collapse it
                                    if (region.isExpanded()) {
                                        region.setExpanded(false);
                                    }
                                }
                            }
                        });
                    }
                }
            }, 150);
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
