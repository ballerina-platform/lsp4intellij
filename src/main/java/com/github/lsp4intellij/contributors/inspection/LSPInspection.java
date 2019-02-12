package com.github.lsp4intellij.contributors.inspection;

import com.github.lsp4intellij.PluginMain;
import com.github.lsp4intellij.contributors.psi.LSPPsiElement;
import com.github.lsp4intellij.editor.EditorEventManager;
import com.github.lsp4intellij.editor.EditorEventManagerBase;
import com.github.lsp4intellij.utils.DocumentUtils;
import com.github.lsp4intellij.utils.FileUtils;
import com.intellij.codeInspection.InspectionManager;
import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.DiagnosticSeverity;
import org.eclipse.lsp4j.Range;
import org.intellij.lang.annotations.Pattern;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import javax.swing.*;

public class LSPInspection extends LocalInspectionTool {

    @Nullable
    @Override
    public ProblemDescriptor[] checkFile(@NotNull PsiFile file, @NotNull InspectionManager manager,
            boolean isOnTheFly) {

        VirtualFile virtualFile = file.getVirtualFile();
        if (PluginMain.isExtensionSupported(virtualFile.getExtension())) {
            String uri = FileUtils.VFSToURI(virtualFile);
            EditorEventManager eventManager = EditorEventManagerBase.forUri(uri);
            if (eventManager != null) {
                return descriptorsForManager(eventManager, file, manager, isOnTheFly);
            } else {
                if (isOnTheFly) {
                    return super.checkFile(file, manager, isOnTheFly);
                } else {
                    //TODO need dispatch thread
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
    private ProblemDescriptor[] descriptorsForManager(EditorEventManager m, PsiFile file, InspectionManager manager,
            boolean isOnTheFly) {
        List<ProblemDescriptor> descriptors = new ArrayList<>();

        Set<Diagnostic> diagnostics = m.getDiagnostics();
        for (Diagnostic diagnostic : diagnostics) {
            String code = diagnostic.getCode();
            String message = diagnostic.getMessage();
            String source = diagnostic.getSource();
            Range range = diagnostic.getRange();
            DiagnosticSeverity severity = diagnostic.getSeverity();
            int start = DocumentUtils.LSPPosToOffset(m.editor, range.getStart());
            int end = DocumentUtils.LSPPosToOffset(m.editor, range.getEnd());

            if (start < end) {
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
                descriptors.add(manager
                        .createProblemDescriptor(element, (TextRange) null, message, highlightType, isOnTheFly));
            }
        }

        ProblemDescriptor[] descArray = new ProblemDescriptor[descriptors.size()];
        return descriptors.toArray(descArray);
    }

    @Override
    public boolean runForWholeFile() {
        return true;
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
