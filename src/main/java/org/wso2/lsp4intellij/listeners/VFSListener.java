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

import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileCopyEvent;
import com.intellij.openapi.vfs.VirtualFileEvent;
import com.intellij.openapi.vfs.VirtualFileListener;
import com.intellij.openapi.vfs.VirtualFileMoveEvent;
import com.intellij.openapi.vfs.VirtualFilePropertyEvent;
import org.jetbrains.annotations.NotNull;

public class VFSListener implements VirtualFileListener {

    /**
     * Fired when a virtual file is renamed from within IDEA, or its writable status is changed.
     * For files renamed externally, {@link #fileCreated} and {@link #fileDeleted} events will be fired.
     *
     * @param event the event object containing information about the change.
     */
    @Override
    public void propertyChanged(@NotNull VirtualFilePropertyEvent event) {
        if (event.getPropertyName().equals(VirtualFile.PROP_NAME)) {
            LSPFileEventManager.fileRenamed((String) event.getOldValue(), (String) event.getNewValue());
        }
    }

    /**
     * Fired when the contents of a virtual file is changed.
     *
     * @param event the event object containing information about the change.
     */
    @Override
    public void contentsChanged(@NotNull VirtualFileEvent event) {
        LSPFileEventManager.fileChanged(event.getFile());
    }

    /**
     * Fired when a virtual file is deleted.
     *
     * @param event the event object containing information about the change.
     */
    @Override
    public void fileDeleted(@NotNull VirtualFileEvent event) {
        LSPFileEventManager.fileDeleted(event.getFile());
    }

    /**
     * Fired when a virtual file is moved from within IDEA.
     *
     * @param event the event object containing information about the change.
     */
    @Override
    public void fileMoved(@NotNull VirtualFileMoveEvent event) {
        LSPFileEventManager.fileMoved(event);
    }

    /**
     * Fired when a virtual file is copied from within IDEA.
     *
     * @param event the event object containing information about the change.
     */
    @Override
    public void fileCopied(@NotNull VirtualFileCopyEvent event) {
        fileCreated(event);
    }

    /**
     * Fired when a virtual file is created. This event is not fired for files discovered during initial VFS initialization.
     *
     * @param event the event object containing information about the change.
     */
    @Override
    public void fileCreated(@NotNull VirtualFileEvent event) {
        LSPFileEventManager.fileCreated(event.getFile());
    }

    /**
     * Fired before the change of a name or writable status of a file is processed.
     *
     * @param event the event object containing information about the change.
     */
    @Override
    public void beforePropertyChange(@NotNull VirtualFilePropertyEvent event) {
    }

    /**
     * Fired before the change of contents of a file is processed.
     *
     * @param event the event object containing information about the change.
     */
    @Override
    public void beforeContentsChange(@NotNull VirtualFileEvent event) {
    }

    /**
     * Fired before the deletion of a file is processed.
     *
     * @param event the event object containing information about the change.
     */
    @Override
    public void beforeFileDeletion(@NotNull VirtualFileEvent event) {
    }

    /**
     * Fired before the movement of a file is processed.
     *
     * @param event the event object containing information about the change.
     */
    @Override
    public void beforeFileMovement(@NotNull VirtualFileMoveEvent event) {
    }
}
