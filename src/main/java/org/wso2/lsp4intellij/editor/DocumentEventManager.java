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

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.event.DocumentListener;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.util.text.StringUtil;
import org.eclipse.lsp4j.DidChangeTextDocumentParams;
import org.eclipse.lsp4j.DidCloseTextDocumentParams;
import org.eclipse.lsp4j.DidOpenTextDocumentParams;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.TextDocumentContentChangeEvent;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.eclipse.lsp4j.TextDocumentItem;
import org.eclipse.lsp4j.TextDocumentSyncKind;
import org.eclipse.lsp4j.VersionedTextDocumentIdentifier;
import org.wso2.lsp4intellij.client.languageserver.requestmanager.RequestManager;
import org.wso2.lsp4intellij.client.languageserver.wrapper.LanguageServerWrapper;
import org.wso2.lsp4intellij.utils.ApplicationUtils;
import org.wso2.lsp4intellij.utils.DocumentUtils;
import org.wso2.lsp4intellij.utils.FileUtils;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class DocumentEventManager {
    private final Document document;
    private final DocumentListener documentListener;
    private final TextDocumentSyncKind syncKind;
    private final LanguageServerWrapper wrapper;
    private final TextDocumentIdentifier identifier;
    private int version = -1;
    protected Logger LOG = Logger.getInstance(EditorEventManager.class);
    private static final Map<String, DocumentEventManager> uriToDocumentEventManager = new HashMap<>();

    private final Set<Document> openDocuments = new HashSet<>();

    DocumentEventManager(Document document, DocumentListener documentListener, TextDocumentSyncKind syncKind, LanguageServerWrapper wrapper) {
        this.document = document;
        this.documentListener = documentListener;
        this.syncKind = syncKind;
        this.wrapper = wrapper;
        this.identifier = new TextDocumentIdentifier(FileUtils.documentToUri(document));
    }

    public static void clearState() {
        uriToDocumentEventManager.clear();
    }

    public static DocumentEventManager getOrCreateDocumentManager(Document document, DocumentListener listener, TextDocumentSyncKind syncKind, LanguageServerWrapper wrapper) {
        DocumentEventManager manager = uriToDocumentEventManager.get(FileUtils.documentToUri(document));
        if (manager != null) {
            return manager;
        }

        manager = new DocumentEventManager(document, listener, syncKind, wrapper);

        uriToDocumentEventManager.put(FileUtils.documentToUri(document), manager);
        return manager;
    }

    public void removeListeners() {
        document.removeDocumentListener(documentListener);
    }

    public void registerListeners() {
        document.addDocumentListener(documentListener);
    }

    public int getDocumentVersion() {
        return this.version;
    }

    public void documentChanged(DocumentEvent event) {

        DidChangeTextDocumentParams changesParams = new DidChangeTextDocumentParams(new VersionedTextDocumentIdentifier(),
                Collections.singletonList(new TextDocumentContentChangeEvent()));
        changesParams.getTextDocument().setUri(identifier.getUri());


        changesParams.getTextDocument().setVersion(++version);

        if (syncKind == TextDocumentSyncKind.Incremental) {
            TextDocumentContentChangeEvent changeEvent = changesParams.getContentChanges().get(0);
            CharSequence newText = event.getNewFragment();
            int offset = event.getOffset();
            int newTextLength = event.getNewLength();
            Set<EditorEventManager> managersForUri = EditorEventManagerBase.managersForUri(FileUtils.documentToUri(document));
            if (managersForUri == null || managersForUri.isEmpty()) {
                LOG.warn("no manager associated with uri");
                return;
            }
            EditorEventManager editorEventManager = EditorEventManagerBase.managersForUri(FileUtils.documentToUri(document)).iterator().next();
            if (editorEventManager == null) {
                LOG.warn("no editor associated with document");
                return;
            }
            Editor editor = editorEventManager.editor;
            Position lspPosition = DocumentUtils.offsetToLSPPos(editor, offset);
            if (lspPosition == null) {
                return;
            }
            int startLine = lspPosition.getLine();
            int startColumn = lspPosition.getCharacter();
            CharSequence oldText = event.getOldFragment();

            //if text was deleted/replaced, calculate the end position of inserted/deleted text
            int endLine, endColumn;
            if (oldText.length() > 0) {
                endLine = startLine + StringUtil.countNewLines(oldText);
                String content = oldText.toString();
                String[] oldLines = content.split("\n");
                int oldTextLength = oldLines.length == 0 ? 0 : oldLines[oldLines.length - 1].length();
                endColumn = content.endsWith("\n") ? 0 : oldLines.length == 1 ? startColumn + oldTextLength : oldTextLength;
            } else { //if insert or no text change, the end position is the same
                endLine = startLine;
                endColumn = startColumn;
            }
            Range range = new Range(new Position(startLine, startColumn), new Position(endLine, endColumn));
            changeEvent.setRange(range);
            changeEvent.setRangeLength(newTextLength);
            changeEvent.setText(newText.toString());
        } else if (syncKind == TextDocumentSyncKind.Full) {
            changesParams.getContentChanges().get(0).setText(document.getText());
        }
        ApplicationUtils.pool(() -> wrapper.getRequestManager().didChange(changesParams));
    }

    public void documentOpened() {
        if (openDocuments.contains(document)) {
            LOG.warn("trying to send open notification for document which was already opened!");
        } else {
            openDocuments.add(document);
            final String extension = FileDocumentManager.getInstance().getFile(document).getExtension();
            wrapper.getRequestManager().didOpen(new DidOpenTextDocumentParams(new TextDocumentItem(identifier.getUri(),
                    wrapper.serverDefinition.languageIdFor(extension),
                    ++version,
                    document.getText())));
        }
    }

    public void documentClosed() {
        if (!openDocuments.contains(document)) {
            LOG.warn("trying to close document which is not open");
        } else if (EditorEventManagerBase.managersForUri(FileUtils.documentToUri(document)).size() > 1) {
            LOG.warn("trying to close document which is still open in another editor!");
        } else {
            openDocuments.remove(document);
            wrapper.getRequestManager().didClose(new DidCloseTextDocumentParams(identifier));
        }
    }
}
