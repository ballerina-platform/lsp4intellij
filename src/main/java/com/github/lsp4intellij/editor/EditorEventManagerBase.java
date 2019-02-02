package com.github.lsp4intellij.editor;

import com.intellij.openapi.editor.Editor;

import java.awt.*;
import java.awt.event.KeyEvent;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class EditorEventManagerBase {

    private static final EditorEventManagerBase EDITOR_EVENT_MANAGER_BASE = new EditorEventManagerBase();
    public static Map<String, EditorEventManager> uriToManager = new ConcurrentHashMap<>();
    public static Map<Editor, EditorEventManager> editorToManager = new ConcurrentHashMap<>();

    volatile private boolean isKeyPressed = false;
    volatile private boolean isCtrlDown = false;

    private EditorEventManagerBase() {
        KeyboardFocusManager.getCurrentKeyboardFocusManager().addKeyEventDispatcher((KeyEvent e) -> {
            synchronized (this) {
                int eventId = e.getID();
                if (eventId == KeyEvent.KEY_PRESSED) {
                    isKeyPressed = true;
                    if (e.getKeyCode() == KeyEvent.VK_CONTROL) {
                        isCtrlDown = true;
                    }
                } else if (eventId == KeyEvent.KEY_RELEASED) {
                    isKeyPressed = false;
                    if (e.getKeyCode() == KeyEvent.VK_CONTROL) {
                        isCtrlDown = false;
                    }
                }
            }
            return false;
        });
    }

    public static EditorEventManagerBase getInstance() {
        return EDITOR_EVENT_MANAGER_BASE;
    }

    /**
     * @param uri A file uri
     * @return The manager for the given uri, or None
     */
    public static EditorEventManager forUri(String uri) {
        prune();
        return uriToManager.get(uri);
    }

    private static void prune() {
        editorToManager.forEach((key, value) -> {
            if (!value.wrapper.isActive()) {
                editorToManager.remove(key);
            }
        });
        uriToManager.forEach((key, value) -> {
            if (!value.wrapper.isActive()) {
                editorToManager.remove(key);
            }
        });
    }

    /**
     * @param editor An editor
     * @return The manager for the given editor, or None
     */
    public static EditorEventManager forEditor(Editor editor) {
        prune();
        return editorToManager.get(editor);
    }

    /**
     * Tells the server that all the documents will be saved
     */
    public static void willSaveAll() {
        prune();
        editorToManager.forEach((key, value) -> value.willSave());
    }

}
