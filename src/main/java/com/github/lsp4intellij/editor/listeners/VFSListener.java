package com.github.lsp4intellij.editor.listeners;

import com.github.lsp4intellij.requests.FileEventManager;
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
    public void propertyChanged(@NotNull VirtualFilePropertyEvent event) {
        if (event.getPropertyName().equals(VirtualFile.PROP_NAME)) {
            FileEventManager.fileRenamed((String) event.getOldValue(), (String)event.getNewValue());
        }
    }

    /**
     * Fired when the contents of a virtual file is changed.
     *
     * @param event the event object containing information about the change.
     */
    public void contentsChanged(@NotNull VirtualFileEvent event) {
        FileEventManager.fileChanged(event.getFile());
    }

    /**
     * Fired when a virtual file is deleted.
     *
     * @param event the event object containing information about the change.
     */
    public void fileDeleted(@NotNull VirtualFileEvent event) {
        FileEventManager.fileDeleted(event.getFile());
    }

    /**
     * Fired when a virtual file is moved from within IDEA.
     *
     * @param event the event object containing information about the change.
     */
    public void fileMoved(@NotNull VirtualFileMoveEvent event) {
        FileEventManager.fileMoved(event.getFile());
    }

    /**
     * Fired when a virtual file is copied from within IDEA.
     *
     * @param event the event object containing information about the change.
     */
    public void fileCopied(@NotNull VirtualFileCopyEvent event) {
        fileCreated(event);
    }

    /**
     * Fired when a virtual file is created. This event is not fired for files discovered during initial VFS initialization.
     *
     * @param event the event object containing information about the change.
     */
    public void fileCreated(@NotNull VirtualFileEvent event) {
        FileEventManager.fileCreated(event.getFile());
    }

    /**
     * Fired before the change of a name or writable status of a file is processed.
     *
     * @param event the event object containing information about the change.
     */
    public void beforePropertyChange(@NotNull VirtualFilePropertyEvent event) {
    }

    /**
     * Fired before the change of contents of a file is processed.
     *
     * @param event the event object containing information about the change.
     */
    public void beforeContentsChange(@NotNull VirtualFileEvent event) {
    }

    /**
     * Fired before the deletion of a file is processed.
     *
     * @param event the event object containing information about the change.
     */
    public void beforeFileDeletion(@NotNull VirtualFileEvent event) {
    }

    /**
     * Fired before the movement of a file is processed.
     *
     * @param event the event object containing information about the change.
     */
    public void beforeFileMovement(@NotNull VirtualFileMoveEvent event) {
    }
}
