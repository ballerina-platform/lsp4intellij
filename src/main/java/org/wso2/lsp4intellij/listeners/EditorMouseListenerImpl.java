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
package org.wso2.lsp4intellij.listeners;

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
        if (checkEnabled()) {
            manager.mouseClicked(editorMouseEvent);
        }

    }

    @Override
    public void mouseReleased(EditorMouseEvent editorMouseEvent) {

    }

    @Override
    public void mouseEntered(EditorMouseEvent editorMouseEvent) {
        if (checkEnabled()) {
            manager.mouseEntered();
        }
    }

    @Override
    public void mouseExited(EditorMouseEvent editorMouseEvent) {
        if (checkEnabled()) {
            manager.mouseExited();
        }
    }
}
