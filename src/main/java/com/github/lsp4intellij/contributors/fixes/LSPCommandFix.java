package com.github.lsp4intellij.contributors.fixes;

import com.github.lsp4intellij.contributors.psi.LSPPsiElement;
import com.github.lsp4intellij.editor.EditorEventManager;
import com.github.lsp4intellij.editor.EditorEventManagerBase;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import org.eclipse.lsp4j.Command;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import static java.util.Collections.singletonList;

public class LSPCommandFix implements LocalQuickFix {

    private String uri;
    private Command command;

    public LSPCommandFix(String uri, Command command) {
        this.uri = uri;
        this.command = command;
    }

    @Override
    public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
        PsiElement element = descriptor.getPsiElement();
        if (element instanceof LSPPsiElement) {
            EditorEventManager manager = EditorEventManagerBase.forUri(uri);
            if (manager != null) {
                manager.executeCommands(singletonList(command));
            }
        }
    }

    @Nls
    @NotNull
    @Override
    public String getFamilyName() {
        return "LSP Fixes";
    }

    @NotNull
    @Override
    public String getName() {
        return command.getTitle();
    }
}

