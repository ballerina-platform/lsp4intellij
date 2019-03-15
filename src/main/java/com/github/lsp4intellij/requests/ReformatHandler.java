package com.github.lsp4intellij.requests;

import com.github.lsp4intellij.editor.EditorEventManager;
import com.github.lsp4intellij.editor.EditorEventManagerBase;
import com.intellij.openapi.editor.Editor;

public class ReformatHandler {

    /**
     * Reformat a file given its editor
     *
     * @param editor The editor
     */
    public static void reformatFile(Editor editor) {
        EditorEventManager eventManager = EditorEventManagerBase.forEditor(editor);
        if (eventManager != null) {
            eventManager.reformat();
        }
    }

    /**
     * Reformat a selection in a file given its editor
     *
     * @param editor The editor
     */
    public static void reformatSelection(Editor editor) {
        EditorEventManager eventManager = EditorEventManagerBase.forEditor(editor);
        if (eventManager != null) {
            eventManager.reformatSelection();
        }
    }
}
