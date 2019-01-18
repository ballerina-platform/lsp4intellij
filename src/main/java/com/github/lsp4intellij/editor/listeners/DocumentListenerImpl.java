package com.github.lsp4intellij.editor.listeners;

import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.event.DocumentListener;

public class DocumentListenerImpl extends LSPListener implements DocumentListener  {

    /**
     * Called before the text of the document is changed.
     *
     * @param event the event containing the information about the change.
     */
    @Override
    public void beforeDocumentChange( DocumentEvent event) {
    }

    /**
     * Called after the text of the document has been changed.
     *
     * @param event the event containing the information about the change.
     */
    @Override
    public void documentChanged(DocumentEvent event) {
        if (checkManager()) {
            manager.documentChanged(event);
        }
    }
}
