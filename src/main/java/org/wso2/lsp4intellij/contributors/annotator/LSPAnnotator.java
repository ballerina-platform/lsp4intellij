/*
 * Copyright (c) 2018-2019, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
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
package org.wso2.lsp4intellij.contributors.annotator;

import com.intellij.lang.annotation.Annotation;
import com.intellij.lang.annotation.AnnotationHolder;
import com.intellij.lang.annotation.ExternalAnnotator;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import org.eclipse.lsp4j.CodeAction;
import org.eclipse.lsp4j.CodeActionContext;
import org.eclipse.lsp4j.CodeActionParams;
import org.eclipse.lsp4j.Command;
import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.eclipse.lsp4j.jsonrpc.JsonRpcException;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.wso2.lsp4intellij.IntellijLanguageClient;
import org.wso2.lsp4intellij.client.languageserver.requestmanager.RequestManager;
import org.wso2.lsp4intellij.client.languageserver.wrapper.LanguageServerWrapper;
import org.wso2.lsp4intellij.contributors.fixes.LSPCodeActionFix;
import org.wso2.lsp4intellij.contributors.fixes.LSPCommandFix;
import org.wso2.lsp4intellij.editor.EditorEventManager;
import org.wso2.lsp4intellij.editor.EditorEventManagerBase;
import org.wso2.lsp4intellij.utils.DocumentUtils;
import org.wso2.lsp4intellij.utils.FileUtils;

import java.util.ArrayList;
import java.util.ConcurrentModificationException;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.wso2.lsp4intellij.requests.Timeout.getTimeout;
import static org.wso2.lsp4intellij.requests.Timeouts.CODEACTION;

public class LSPAnnotator extends ExternalAnnotator {

    private static final Logger LOG = Logger.getInstance(LSPAnnotator.class);
    private static final Object RESULT = new Object();

    @Nullable
    @Override
    public Object collectInformation(@NotNull PsiFile file, @NotNull Editor editor, boolean hasErrors) {

        try {
            VirtualFile virtualFile = file.getVirtualFile();

            // If the file is not supported, we skips the annotation by returning null.
            if (!FileUtils.isFileSupported(virtualFile) || !IntellijLanguageClient.isExtensionSupported(virtualFile)) {
                return null;
            }
            String uri = FileUtils.VFSToURI(virtualFile);
            EditorEventManager eventManager = EditorEventManagerBase.forUri(uri);

            // If the diagnostics list is locked, we need to skip annotating the file.
            if (eventManager == null || !(eventManager.isDiagnosticSyncRequired() || eventManager.isCodeActionSyncRequired())) {
                return null;
            }
            return RESULT;
        } catch (Exception e) {
            return null;
        }
    }

    @Nullable
    @Override
    public Object doAnnotate(Object collectedInfo) {
        return RESULT;
    }

    @Override
    public void apply(@NotNull PsiFile file, Object annotationResult, @NotNull AnnotationHolder holder) {

        VirtualFile virtualFile = file.getVirtualFile();
        if (FileUtils.isFileSupported(virtualFile) && IntellijLanguageClient.isExtensionSupported(virtualFile)) {
            String uri = FileUtils.VFSToURI(virtualFile);
            EditorEventManager eventManager = EditorEventManagerBase.forUri(uri);
            if (eventManager == null) {
                return;
            }

            if (eventManager.isDiagnosticSyncRequired()) {
                try {
                    createAnnotations(holder, eventManager);
                } catch (ConcurrentModificationException e) {
                    // Todo - Add proper fix to handle concurrent modifications gracefully.
                    LOG.warn("Error occurred when updating LSP code actions due to concurrent modifications.", e);
                } catch (Throwable t) {
                    LOG.warn("Error occurred when updating LSP code actions.", t);
                }
            }
        }
    }

    private void createAnnotations(AnnotationHolder holder, EditorEventManager eventManager) {
        final List<Diagnostic> diagnostics = eventManager.getDiagnostics();
        final Editor editor = eventManager.editor;

        diagnostics.forEach(d -> {
            final int start = DocumentUtils.LSPPosToOffset(editor, d.getRange().getStart());
            final int end = DocumentUtils.LSPPosToOffset(editor, d.getRange().getEnd());

            if (start >= end) {
                return;
            }

            Annotation annotation;
            final TextRange textRange = new TextRange(start, end);
            switch (d.getSeverity()) {
                case Error:
                    annotation = holder.createErrorAnnotation(textRange, d.getMessage());
                    break;
                case Warning:
                    annotation = holder.createWarningAnnotation(textRange, d.getMessage());
                    break;
                case Information:
                    annotation = holder.createInfoAnnotation(textRange, d.getMessage());
                    break;
                default:
                    annotation = holder.createWeakWarningAnnotation(textRange, d.getMessage());
                    break;
            }
            requestAndShowCodeActions(eventManager, annotation);
        });
    }

    /**
     * Retrieves the commands needed to apply a CodeAction
     *
     * @param offset The cursor position(offset) which should be evaluated for code action request.
     * @return The list of commands, or null if none are given / the request times out
     */
    private List<Either<Command, CodeAction>> codeAction(EditorEventManager eventManager, int offset) {
        Editor editor = eventManager.editor;
        LanguageServerWrapper wrapper = eventManager.wrapper;
        List<Diagnostic> diagnostics = eventManager.getDiagnostics();
        TextDocumentIdentifier identifier = eventManager.getIdentifier();
        RequestManager requestManager = eventManager.getRequestManager();

        CodeActionParams params = new CodeActionParams();
        params.setTextDocument(identifier);
        Range range = new Range(DocumentUtils.offsetToLSPPos(editor, offset),
                DocumentUtils.offsetToLSPPos(editor, offset));
        params.setRange(range);

        // Calculates the diagnostic context.
        List<Diagnostic> diagnosticContext = new ArrayList<>();
        diagnostics.forEach(diagnostic -> {
            int startOffset = DocumentUtils.LSPPosToOffset(editor, diagnostic.getRange().getStart());
            int endOffset = DocumentUtils.LSPPosToOffset(editor, diagnostic.getRange().getEnd());
            if (offset >= startOffset && offset <= endOffset) {
                diagnosticContext.add(diagnostic);
            }
        });

        CodeActionContext context = new CodeActionContext(diagnosticContext);
        params.setContext(context);
        CompletableFuture<List<Either<Command, CodeAction>>> future = requestManager.codeAction(params);
        if (future != null) {
            try {
                List<Either<Command, CodeAction>> res = future.get(getTimeout(CODEACTION), TimeUnit.MILLISECONDS);
                wrapper.notifySuccess(CODEACTION);
                return res;
            } catch (TimeoutException e) {
                LOG.warn(e);
                wrapper.notifyFailure(CODEACTION);
                return null;
            } catch (InterruptedException | JsonRpcException | ExecutionException e) {
                LOG.warn(e);
                wrapper.crashed(e);
                return null;
            }
        }
        return null;
    }

    private void requestAndShowCodeActions(EditorEventManager eventManager, Annotation annotation) {
        Editor editor = eventManager.editor;
        List<Either<Command, CodeAction>> codeActionResp = codeAction(eventManager, annotation.getStartOffset());
        if (codeActionResp == null || codeActionResp.isEmpty()) {
            return;
        }

        int start = annotation.getStartOffset();
        int end = annotation.getEndOffset();
        codeActionResp.forEach(element -> {
            if (element == null) {
                return;
            }
            if (element.isLeft()) {
                Command command = element.getLeft();

                annotation.registerFix(new LSPCommandFix(FileUtils.editorToURIString(editor),
                        command), new TextRange(start, end));


            } else if (element.isRight()) {
                CodeAction codeAction = element.getRight();
                List<Diagnostic> diagnosticContext = codeAction.getDiagnostics();

                annotation.registerFix(new LSPCodeActionFix(FileUtils.editorToURIString(editor),
                        codeAction), new TextRange(start, end));

                // If the code actions does not have a diagnostics context, creates an intention action for
                // the current line.
                if ((diagnosticContext == null || diagnosticContext.isEmpty())) {
                    // Calculates text range of the current line.
                    int line = editor.getCaretModel().getCurrentCaret().getLogicalPosition().line;
                    int startOffset = editor.getDocument().getLineStartOffset(line);
                    int endOffset = editor.getDocument().getLineEndOffset(line);
                    TextRange range = new TextRange(startOffset, endOffset);

                    annotation.registerFix(new LSPCodeActionFix(FileUtils.editorToURIString(editor), codeAction), range);
                }
            }
        });
    }
}