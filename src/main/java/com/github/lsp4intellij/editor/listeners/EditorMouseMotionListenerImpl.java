package com.github.lsp4intellij.editor.listeners;

import com.intellij.openapi.editor.event.EditorMouseEvent;
import com.intellij.openapi.editor.event.EditorMouseMotionListener;

/**
 * Class listening for mouse movement in an editor (used for hover)
 */
public class EditorMouseMotionListenerImpl extends LSPListener implements EditorMouseMotionListener {

    @Override
    public void mouseMoved(EditorMouseEvent e) {
        if (checkEnabled()) {
            manager.mouseMoved(e);
        }
    }

    @Override
    public void mouseDragged(EditorMouseEvent editorMouseEvent) {

    }
}
