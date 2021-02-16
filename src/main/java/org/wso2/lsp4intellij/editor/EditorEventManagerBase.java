/*
 * Copyright (c) 2019, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.wso2.lsp4intellij.editor;

import com.intellij.openapi.editor.Editor;
import org.wso2.lsp4intellij.utils.FileUtils;
import org.wso2.lsp4intellij.utils.OSUtils;

import java.awt.KeyboardFocusManager;
import java.awt.event.KeyEvent;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class EditorEventManagerBase {

    private static final Map<String, Set<EditorEventManager>> uriToManagers = new ConcurrentHashMap<>();
    private static final Map<Editor, EditorEventManager> editorToManager = new ConcurrentHashMap<>();
    private static final int CTRL_KEY_CODE = OSUtils.isMac() ? KeyEvent.VK_META : KeyEvent.VK_CONTROL;
    private volatile static boolean isKeyPressed = false;
    private volatile static boolean isCtrlDown = false;
    private volatile static CtrlRangeMarker ctrlRange;

    static {
        KeyboardFocusManager.getCurrentKeyboardFocusManager().addKeyEventDispatcher((KeyEvent e) -> {
            int eventId = e.getID();
            if (eventId == KeyEvent.KEY_PRESSED) {
                setIsKeyPressed(true);
                if (e.getKeyCode() == CTRL_KEY_CODE) {
                    setIsCtrlDown(true);
                }
            } else if (eventId == KeyEvent.KEY_RELEASED) {
                setIsKeyPressed(false);
                if (e.getKeyCode() == CTRL_KEY_CODE) {
                    setIsCtrlDown(false);
                    if (getCtrlRange() != null) {
                        getCtrlRange().dispose();
                        setCtrlRange(null);
                    }
                }
            }
            return false;
        });
    }

    static synchronized CtrlRangeMarker getCtrlRange() {
        return ctrlRange;
    }

    static synchronized void setCtrlRange(CtrlRangeMarker ctrlRange) {
        EditorEventManagerBase.ctrlRange = ctrlRange;
    }

    static synchronized boolean getIsCtrlDown() {
        return isCtrlDown;
    }

    static synchronized void setIsCtrlDown(boolean isCtrlDown) {
        EditorEventManagerBase.isCtrlDown = isCtrlDown;
    }

    static synchronized boolean getIsKeyPressed() {
        return isKeyPressed;
    }

    static synchronized void setIsKeyPressed(boolean isKeyPressed) {
        EditorEventManagerBase.isKeyPressed = isKeyPressed;
    }

    public static Set<EditorEventManager> managersForUri(String uri) {
        return uriToManagers.get(uri);
    }

    /**
     * WARNING: avoid using this function! It only gives you one editorEventManager, not all and not the one of the current editor.
     * Only use for operations which are file-level (save, open, close,...) otherwise use {@link #managersForUri(String)} or {@link #forEditor(Editor)}
     */
    public static EditorEventManager forUri(String uri) {
        if (uri == null) {
            return null;
        }
        Set<EditorEventManager> managers = managersForUri(uri);
        if (managers != null && !managers.isEmpty()) {
            return (EditorEventManager) managers.toArray()[0];
        }
        return null;
    }


    private static void prune() {
        new HashMap<>(editorToManager).forEach((key, value) -> {
            if (!value.wrapper.isActive()) {
                editorToManager.remove(key);
            }
        });
        new HashMap<>(uriToManagers).forEach((key, value) -> {
            value.forEach((manager) -> {
                if (!manager.wrapper.isActive()) {
                    uriToManagers.get(key).remove(manager);
                }
            });
            if (value.isEmpty()) {
                uriToManagers.remove(key);
            }
        });
    }

    public static void registerManager(EditorEventManager manager) {
        String uri = FileUtils.editorToURIString(manager.editor);
        if (EditorEventManagerBase.uriToManagers.containsKey(uri)) {
            EditorEventManagerBase.uriToManagers.get(uri).add(manager);
        } else {
            HashSet<EditorEventManager> set = new HashSet<>();
            set.add(manager);
            EditorEventManagerBase.uriToManagers.put(uri, set);
        }
        EditorEventManagerBase.editorToManager.put(manager.editor, manager);
    }

    public static void unregisterManager(EditorEventManager manager) {
        EditorEventManagerBase.editorToManager.remove(manager.editor);

        String uri = FileUtils.editorToURIString(manager.editor);
        Set<EditorEventManager> set = EditorEventManagerBase.uriToManagers.get(uri);
        set.remove(manager);
        if (set.isEmpty()) {
            EditorEventManagerBase.uriToManagers.remove(uri);
        }
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
