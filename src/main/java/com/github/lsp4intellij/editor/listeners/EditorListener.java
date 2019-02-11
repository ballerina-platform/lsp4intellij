package com.github.lsp4intellij.editor.listeners;

import com.github.lsp4intellij.PluginMain;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.event.EditorFactoryEvent;
import com.intellij.openapi.editor.event.EditorFactoryListener;
import org.jetbrains.annotations.NotNull;

public class EditorListener implements EditorFactoryListener{

    private Logger LOG = Logger.getInstance(EditorListener.class);

    public void editorReleased(@NotNull EditorFactoryEvent editorFactoryEvent) {
        PluginMain.editorClosed(editorFactoryEvent.getEditor());
    }

    public void editorCreated(@NotNull EditorFactoryEvent editorFactoryEvent) {
        PluginMain.editorOpened(editorFactoryEvent.getEditor());
    }
}
