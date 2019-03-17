package com.github.lsp4intellij.actions;

import com.github.lsp4intellij.editor.EditorEventManager;
import com.github.lsp4intellij.editor.EditorEventManagerBase;
import com.intellij.codeInsight.documentation.actions.ShowQuickDocInfoAction;
import com.intellij.lang.Language;
import com.intellij.lang.LanguageDocumentation;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.application.ApplicationInfo;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileTypes.PlainTextLanguage;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiManager;

/**
 * Action overriding QuickDoc (CTRL+Q)
 */
class LSPQuickDocAction extends ShowQuickDocInfoAction implements DumbAware {
    private Logger LOG = Logger.getInstance(LSPQuickDocAction.class);

    @Override
    public void actionPerformed(AnActionEvent e) {
        Editor editor = e.getData(CommonDataKeys.EDITOR);
        VirtualFile file = FileDocumentManager.getInstance().getFile(editor.getDocument());
        Language language = PsiManager.getInstance(editor.getProject()).findFile(file).getLanguage();
        //Hack for IntelliJ 2018 TODO proper way
        if (LanguageDocumentation.INSTANCE.allForLanguage(language).isEmpty()
                || (Integer.parseInt(ApplicationInfo.getInstance().getMajorVersion()) > 2017)
                && language == PlainTextLanguage.INSTANCE) {
            EditorEventManager manager = EditorEventManagerBase.forEditor(editor);
            if (manager != null) {
                manager.quickDoc(editor);
            } else {
                super.actionPerformed(e);
            }
        } else
            super.actionPerformed(e);
    }
}
