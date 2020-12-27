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

        import com.intellij.openapi.components.ServiceManager;
        import com.intellij.openapi.editor.EditorKind;
        import com.intellij.openapi.editor.event.EditorFactoryEvent;
        import com.intellij.openapi.editor.event.EditorFactoryListener;
        import org.jetbrains.annotations.NotNull;
        import org.wso2.lsp4intellij.IntellijLanguageClient;

public class LSPEditorListener implements EditorFactoryListener {

    public void editorReleased(@NotNull EditorFactoryEvent editorFactoryEvent) {
        if(editorFactoryEvent.getEditor().getEditorKind() == EditorKind.MAIN_EDITOR){
            IntellijLanguageClient.editorClosed(editorFactoryEvent.getEditor());
        }
    }

    public void editorCreated(@NotNull EditorFactoryEvent editorFactoryEvent) {
        if(editorFactoryEvent.getEditor().getEditorKind() == EditorKind.MAIN_EDITOR) {
            IntellijLanguageClient.editorOpened(editorFactoryEvent.getEditor());
        }
    }
}
