package com.github.lsp4intellij.contributors.inspection;

import com.github.lsp4intellij.PluginMain;
import com.github.lsp4intellij.contributors.fixes.LSPCodeActionFix;
import com.github.lsp4intellij.contributors.fixes.LSPCommandFix;
import com.github.lsp4intellij.contributors.psi.LSPPsiElement;
import com.github.lsp4intellij.editor.EditorEventManager;
import com.github.lsp4intellij.editor.EditorEventManagerBase;
import com.github.lsp4intellij.utils.DocumentUtils;
import com.github.lsp4intellij.utils.FileUtils;
import com.intellij.codeInspection.InspectionManager;
import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import org.eclipse.lsp4j.CodeAction;
import org.eclipse.lsp4j.Command;
import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.DiagnosticSeverity;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.intellij.lang.annotations.Pattern;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import javax.swing.*;

/**
 * Triggered by {@link EditorEventManager#diagnostics} to run the local inspection tool manually.
 */
public class LSPInspection extends LocalInspectionTool implements DumbAware {

    @Nullable
    @Override
    public ProblemDescriptor[] checkFile(@NotNull PsiFile file, @NotNull InspectionManager manager,
            boolean isOnTheFly) {

        VirtualFile virtualFile = file.getVirtualFile();
        if (PluginMain.isExtensionSupported(virtualFile.getExtension())) {
            String uri = FileUtils.VFSToURI(virtualFile);
            EditorEventManager eventManager = EditorEventManagerBase.forUri(uri);
            if (eventManager != null) {
                return descriptorsForManager(uri, eventManager, file, manager, isOnTheFly);
            } else {
                if (isOnTheFly) {
                    return super.checkFile(file, manager, isOnTheFly);
                } else {
                    return super.checkFile(file, manager, isOnTheFly);
                }
            }
        } else {
            return super.checkFile(file, manager, isOnTheFly);
        }
    }

    /**
     * Gets all the ProblemDescriptor given an EditorEventManager
     * Looks at the diagnostics, creates dummy PsiElement for each, create descriptor using it
     */
    private ProblemDescriptor[] descriptorsForManager(String uri, EditorEventManager m, PsiFile file,
            InspectionManager manager, boolean isOnTheFly) {
        List<ProblemDescriptor> descriptors = new ArrayList<>();
        List<Diagnostic> diagnostics = m.getDiagnostics();
        for (Diagnostic diagnostic : diagnostics) {
            String message = diagnostic.getMessage();
            Range range = diagnostic.getRange();
            DiagnosticSeverity severity = diagnostic.getSeverity();
            int start = DocumentUtils.LSPPosToOffset(m.editor, range.getStart());
            int end = DocumentUtils.LSPPosToOffset(m.editor, range.getEnd());

            if (start >= end) {
                continue;
            }

            String name = m.editor.getDocument().getText(new TextRange(start, end));
            ProblemHighlightType highlightType;
            if (severity == DiagnosticSeverity.Error) {
                highlightType = ProblemHighlightType.GENERIC_ERROR;
            } else if (severity == DiagnosticSeverity.Warning) {
                highlightType = ProblemHighlightType.GENERIC_ERROR_OR_WARNING;
            } else if (severity == DiagnosticSeverity.Information) {
                highlightType = ProblemHighlightType.INFORMATION;
            } else if (severity == DiagnosticSeverity.Hint) {
                highlightType = ProblemHighlightType.INFORMATION;
            } else {
                highlightType = null;
            }

            LSPPsiElement element = new LSPPsiElement(name, m.editor.getProject(), start, end, file);
            List<Either<Command, CodeAction>> codeActionResults = m.codeAction(element);
            if (codeActionResults != null) {
                List<LSPCommandFix> commands = new ArrayList<>();
                List<LSPCodeActionFix> codeActions = new ArrayList<>();
                for (Either<Command, CodeAction> item : codeActionResults) {
                    if (item == null) {
                        continue;
                    }
                    if (item.isLeft()) {
                        commands.add(new LSPCommandFix(uri, item.getLeft()));
                    } else if (item.isRight()) {
                        codeActions.add(new LSPCodeActionFix(uri, item.getRight()));
                    }
                }
                List<LocalQuickFix> fixes = new ArrayList<>();
                fixes.addAll(commands);
                fixes.addAll(codeActions);
                try {
                    descriptors.add(manager
                            .createProblemDescriptor(element, (TextRange) null, message, highlightType, isOnTheFly,
                                    fixes.toArray(new LocalQuickFix[fixes.size()])));
                } catch (Exception ignored) {
                    // Occurred only at plugin start, due to the dummy inspection tool.
                    // Todo
                }
            } else {
                try {
                    descriptors.add(manager
                            .createProblemDescriptor(element, (TextRange) null, message, highlightType, isOnTheFly));
                } catch (Exception ignored) {
                    // Occurred only at plugin start, due to the dummy inspection tool.
                    // Todo
                }
            }
        }

        ProblemDescriptor[] descArray = new ProblemDescriptor[descriptors.size()];
        return descriptors.toArray(descArray);
    }

    @NotNull
    @Override
    public String getDisplayName() {
        return getShortName();
    }

    @Override
    public JComponent createOptionsPanel() {
        return new LSPInspectionPanel("LSP", this);
    }

    @NotNull
    @Override
    public String getShortName() {
        return "LSP";
    }

    @NotNull
    @Override
    @Pattern("[a-zA-Z_0-9.-]+")
    public String getID() {
        return "LSP";
    }

    @NotNull
    @Override
    public String getGroupDisplayName() {
        return "LSP";
    }

    @Override
    public String getStaticDescription() {
        return "Reports errors by the LSP server";
    }

    @Override
    public boolean isEnabledByDefault() {
        return true;
    }
}
