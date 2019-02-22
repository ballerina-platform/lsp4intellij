package com.github.lsp4intellij.editor.listeners;

import com.github.lsp4intellij.editor.EditorEventManager;
import com.github.lsp4intellij.editor.EditorEventManagerBase;
import com.intellij.codeInsight.editorActions.TypedHandlerDelegate;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;

/**
 * This class notifies an EditorEventManager that a character has been typed in the editor
 */
class LSPTypedHandler extends TypedHandlerDelegate {

    @Override
    public Result charTyped(char c, Project project, Editor editor, PsiFile file) {
        EditorEventManager eventManager = EditorEventManagerBase.forEditor(editor);
        if (eventManager != null) {
            eventManager.characterTyped(c);
        }
        return Result.CONTINUE;
    }
}
