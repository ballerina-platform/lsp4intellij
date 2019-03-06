package com.github.lsp4intellij.editor.listeners;

import com.intellij.openapi.editor.event.EditorMouseEvent;
import com.intellij.openapi.editor.event.EditorMouseListener;

/**
 * An EditorMouseListener implementation which listens to mouseExited, mouseEntered and mouseClicked events.
 */
public class EditorMouseListenerImpl extends LSPListener implements EditorMouseListener {
    @Override
    public void mousePressed(EditorMouseEvent editorMouseEvent) {

    }

    @Override
    public void mouseClicked(EditorMouseEvent editorMouseEvent) {
        if (checkManager()) {
            manager.mouseClicked(editorMouseEvent);
        }

    }

    @Override
    public void mouseReleased(EditorMouseEvent editorMouseEvent) {

    }

    @Override
    public void mouseEntered(EditorMouseEvent editorMouseEvent) {
        if (checkManager()) {
            manager.mouseEntered();
        }
    }

    @Override
    public void mouseExited(EditorMouseEvent editorMouseEvent) {
        if (checkManager()) {
            manager.mouseExited();
        }
    }
}
