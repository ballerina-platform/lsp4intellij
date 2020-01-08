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

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManagerListener;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

public class LSPFileDocumentManagerListener implements FileDocumentManagerListener {

    @Override
    public void beforeDocumentSaving(@NotNull Document document) {
        LSPFileEventManager.willSave(document);
    }

    @Override
    public void unsavedDocumentsDropped() {

    }

    @Override
    public void beforeAllDocumentsSaving() {
        LSPFileEventManager.willSaveAllDocuments();
    }

    @Override
    public void beforeFileContentReload(VirtualFile virtualFile, @NotNull Document document) {

    }

    @Override
    public void fileWithNoDocumentChanged(@NotNull VirtualFile virtualFile) {

    }

    @Override
    public void fileContentReloaded(@NotNull VirtualFile virtualFile, @NotNull Document document) {

    }

    @Override
    public void fileContentLoaded(@NotNull VirtualFile virtualFile, @NotNull Document document) {

    }
}
