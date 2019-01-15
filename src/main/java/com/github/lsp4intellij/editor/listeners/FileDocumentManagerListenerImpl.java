package com.github.lsp4intellij.editor.listeners;

import com.github.lsp4intellij.requests.FileEventManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManagerListener;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

public class FileDocumentManagerListenerImpl implements FileDocumentManagerListener {

    @Override
    public void beforeDocumentSaving(@NotNull Document document) {
        FileEventManager.willSave(document);
    }

    @Override
    public void unsavedDocumentsDropped() {

    }

    @Override
    public void beforeAllDocumentsSaving(){
        FileEventManager.willSaveAllDocuments();
    }

    @Override
    public void beforeFileContentReload( VirtualFile virtualFile,  @NotNull Document document){

    }

    @Override
    public void fileWithNoDocumentChanged(@NotNull VirtualFile virtualFile) {

    }

    @Override
    public void fileContentReloaded(@NotNull VirtualFile virtualFile, @NotNull Document  document){

    }

    @Override
    public void fileContentLoaded( @NotNull VirtualFile virtualFile,  @NotNull Document document) {

    }
}
