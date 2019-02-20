package com.github.lsp4intellij.contributors.fixes;

import com.github.lsp4intellij.contributors.psi.LSPPsiElement;
import com.github.lsp4intellij.editor.EditorEventManager;
import com.github.lsp4intellij.editor.EditorEventManagerBase;
import com.github.lsp4intellij.requests.WorkspaceEditHandler;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import org.eclipse.lsp4j.CodeAction;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;

public class LSPCodeActionFix implements LocalQuickFix {

    private String uri;
    private CodeAction codeAction;

    public LSPCodeActionFix(String uri, CodeAction codeAction) {
        this.uri = uri;
        this.codeAction = codeAction;
    }

    @Override
    public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
        PsiElement element = descriptor.getPsiElement();
        if (element instanceof LSPPsiElement) {
            if (codeAction.getEdit() != null) {
                WorkspaceEditHandler.applyEdit(codeAction.getEdit(), codeAction.getTitle());
            }
            EditorEventManager manager = EditorEventManagerBase.forUri(uri);
            if (manager != null) {
                manager.executeCommands(Collections.singletonList(codeAction.getCommand()));
            }
        }
    }

    @Nls
    @NotNull
    @Override
    public String getFamilyName() {
        return "LSP Fixes";
    }

    @Nls
    @NotNull
    @Override
    public String getName() {
        return codeAction.getTitle();
    }

}
