package com.github.lsp4intellij.actions;

import com.github.lsp4intellij.IntellijLanguageClient;
import com.github.lsp4intellij.editor.EditorEventManager;
import com.github.lsp4intellij.editor.EditorEventManagerBase;
import com.intellij.codeInsight.actions.LayoutCodeDialog;
import com.intellij.codeInsight.actions.LayoutCodeOptions;
import com.intellij.codeInsight.actions.ShowReformatFileDialog;
import com.intellij.codeInsight.actions.TextRangeType;
import com.intellij.lang.LanguageFormatting;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;

/**
 * Class overriding the default action handling the Reformat dialog event (CTRL+ALT+SHIFT+L by default)
 * Fallback to the default action if the language is already supported or not supported by any language server
 */
public class LSPShowReformatDialogAction extends ShowReformatFileDialog implements DumbAware {
    private String HELP_ID = "editing.codeReformatting";
    private Logger LOG = Logger.getInstance(LSPShowReformatDialogAction.class);

    @Override
    public void actionPerformed(AnActionEvent e) {
        Editor editor = e.getData(CommonDataKeys.EDITOR);
        Project project = e.getData(CommonDataKeys.PROJECT);

        if (editor != null && project != null) {
            PsiFile psiFile = PsiDocumentManager.getInstance(project).getPsiFile(editor.getDocument());
            VirtualFile virFile = FileDocumentManager.getInstance().getFile(editor.getDocument());
            boolean alreadySupported = !LanguageFormatting.INSTANCE.allForLanguage(psiFile.getLanguage()).isEmpty();
            if (!alreadySupported && IntellijLanguageClient.isExtensionSupported(virFile.getExtension())) {
                boolean hasSelection = editor.getSelectionModel().hasSelection();
                LayoutCodeDialog dialog = new LayoutCodeDialog(project, psiFile, hasSelection, HELP_ID);
                dialog.show();
                if (dialog.isOK()) {
                    LayoutCodeOptions options = dialog.getRunOptions();
                    EditorEventManager eventManager = EditorEventManagerBase.forEditor(editor);
                    if (eventManager != null) {
                        if (options.getTextRangeType() == TextRangeType.SELECTED_TEXT) {
                            eventManager.reformatSelection();
                        } else {
                            eventManager.reformat();
                        }
                    }
                } else {
                    super.actionPerformed(e);
                }
            } else {
                super.actionPerformed(e);
            }
        } else {
            super.actionPerformed(e);
        }
    }

    @Override
    public void update(AnActionEvent event) {
        super.update(event);
    }
}

