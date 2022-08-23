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
package org.wso2.lsp4intellij.client.languageserver.requestmanager;

import org.eclipse.lsp4j.*;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.eclipse.lsp4j.services.LanguageClient;
import org.eclipse.lsp4j.services.LanguageServer;
import org.eclipse.lsp4j.services.TextDocumentService;
import org.eclipse.lsp4j.services.WorkspaceService;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Base representation of currently supported LSP-based requests and notifications.
 */
public interface RequestManager extends LanguageClient, TextDocumentService, WorkspaceService, LanguageServer {

    //------------------------------------- Server2Client ---------------------------------------------------------//
    @Override
    void showMessage(MessageParams messageParams);

    @Override
    CompletableFuture<MessageActionItem> showMessageRequest(ShowMessageRequestParams showMessageRequestParams);

    @Override
    void logMessage(MessageParams messageParams);

    @Override
    void telemetryEvent(Object o);

    @Override
    CompletableFuture<Void> registerCapability(RegistrationParams params);

    @Override
    CompletableFuture<Void> unregisterCapability(UnregistrationParams params);

    @Override
    CompletableFuture<ApplyWorkspaceEditResponse> applyEdit(ApplyWorkspaceEditParams params);

    @Override
    void publishDiagnostics(PublishDiagnosticsParams publishDiagnosticsParams);

    //--------------------------------------Client2Server-------------------------------------------------------------//

    // General

    @Override
    CompletableFuture<InitializeResult> initialize(InitializeParams params);

    @Override
    void initialized(InitializedParams params);

    @Override
    CompletableFuture<Object> shutdown();

    @Override
    void exit();

    // Workspace Service

    @Override
    void didChangeConfiguration(DidChangeConfigurationParams params);

    @Override
    void didChangeWatchedFiles(DidChangeWatchedFilesParams params);

    @Override
    CompletableFuture<Object> executeCommand(ExecuteCommandParams params);

    // Text Document Service

    @Override
    void didOpen(DidOpenTextDocumentParams params);

    @Override
    void didChange(DidChangeTextDocumentParams params);

    @Override
    void willSave(WillSaveTextDocumentParams params);

    @Override
    CompletableFuture<List<TextEdit>> willSaveWaitUntil(WillSaveTextDocumentParams params);

    @Override
    void didSave(DidSaveTextDocumentParams params);

    @Override
    void didClose(DidCloseTextDocumentParams params);

    @Override
    CompletableFuture<Either<List<CompletionItem>, CompletionList>> completion(CompletionParams params);

    @Override
    CompletableFuture<CompletionItem> resolveCompletionItem(CompletionItem unresolved);

    @Override
    CompletableFuture<Hover> hover(HoverParams params);

    @Deprecated
    CompletableFuture<Hover> hover(TextDocumentPositionParams params);

    @Deprecated
    CompletableFuture<SignatureHelp> signatureHelp(TextDocumentPositionParams params);

    @Override
    CompletableFuture<SignatureHelp> signatureHelp(SignatureHelpParams params);

    @Override
    CompletableFuture<List<? extends Location>> references(ReferenceParams params);

    @Deprecated
    CompletableFuture<List<? extends DocumentHighlight>> documentHighlight(TextDocumentPositionParams params);

    @Override
    CompletableFuture<List<? extends DocumentHighlight>> documentHighlight(DocumentHighlightParams params);

    @Override
    CompletableFuture<List<Either<SymbolInformation, DocumentSymbol>>> documentSymbol(DocumentSymbolParams params);

    @Override
    CompletableFuture<List<? extends TextEdit>> formatting(DocumentFormattingParams params);

    @Override
    CompletableFuture<List<? extends TextEdit>> rangeFormatting(DocumentRangeFormattingParams params);

    @Override
    CompletableFuture<List<? extends TextEdit>> onTypeFormatting(DocumentOnTypeFormattingParams params);

    @Deprecated
    CompletableFuture<Either<List<? extends Location>, List<? extends LocationLink>>> definition(TextDocumentPositionParams params);

    @Override
    CompletableFuture<Either<List<? extends Location>, List<? extends LocationLink>>> definition(DefinitionParams params);

    @Override
    CompletableFuture<List<Either<Command, CodeAction>>> codeAction(CodeActionParams params);

    @Override
    CompletableFuture<List<? extends CodeLens>> codeLens(CodeLensParams params);

    @Override
    CompletableFuture<CodeLens> resolveCodeLens(CodeLens unresolved);

    @Override
    CompletableFuture<List<DocumentLink>> documentLink(DocumentLinkParams params);

    @Override
    CompletableFuture<DocumentLink> documentLinkResolve(DocumentLink unresolved);

    @Override
    CompletableFuture<WorkspaceEdit> rename(RenameParams params);

    @Override
    CompletableFuture<Either<List<? extends Location>, List<? extends LocationLink>>> implementation(ImplementationParams params);

    @Override
    CompletableFuture<Either<List<? extends Location>, List<? extends LocationLink>>> typeDefinition(TypeDefinitionParams params);

    @Override
    CompletableFuture<List<ColorInformation>> documentColor(DocumentColorParams params);

    @Override
    CompletableFuture<List<ColorPresentation>> colorPresentation(ColorPresentationParams params);

    @Override
    CompletableFuture<List<FoldingRange>> foldingRange(FoldingRangeRequestParams params);
}
