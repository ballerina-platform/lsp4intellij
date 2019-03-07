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
package com.github.lsp4intellij.client.languageserver.requestmanager;

import org.eclipse.lsp4j.ApplyWorkspaceEditParams;
import org.eclipse.lsp4j.ApplyWorkspaceEditResponse;
import org.eclipse.lsp4j.CodeAction;
import org.eclipse.lsp4j.CodeActionParams;
import org.eclipse.lsp4j.CodeLens;
import org.eclipse.lsp4j.CodeLensParams;
import org.eclipse.lsp4j.ColorInformation;
import org.eclipse.lsp4j.ColorPresentation;
import org.eclipse.lsp4j.ColorPresentationParams;
import org.eclipse.lsp4j.Command;
import org.eclipse.lsp4j.CompletionItem;
import org.eclipse.lsp4j.CompletionList;
import org.eclipse.lsp4j.CompletionParams;
import org.eclipse.lsp4j.DidChangeConfigurationParams;
import org.eclipse.lsp4j.DidChangeTextDocumentParams;
import org.eclipse.lsp4j.DidChangeWatchedFilesParams;
import org.eclipse.lsp4j.DidCloseTextDocumentParams;
import org.eclipse.lsp4j.DidOpenTextDocumentParams;
import org.eclipse.lsp4j.DidSaveTextDocumentParams;
import org.eclipse.lsp4j.DocumentColorParams;
import org.eclipse.lsp4j.DocumentFormattingParams;
import org.eclipse.lsp4j.DocumentHighlight;
import org.eclipse.lsp4j.DocumentLink;
import org.eclipse.lsp4j.DocumentLinkParams;
import org.eclipse.lsp4j.DocumentOnTypeFormattingParams;
import org.eclipse.lsp4j.DocumentRangeFormattingParams;
import org.eclipse.lsp4j.DocumentSymbol;
import org.eclipse.lsp4j.DocumentSymbolParams;
import org.eclipse.lsp4j.ExecuteCommandParams;
import org.eclipse.lsp4j.FoldingRange;
import org.eclipse.lsp4j.FoldingRangeRequestParams;
import org.eclipse.lsp4j.Hover;
import org.eclipse.lsp4j.InitializeParams;
import org.eclipse.lsp4j.InitializeResult;
import org.eclipse.lsp4j.InitializedParams;
import org.eclipse.lsp4j.Location;
import org.eclipse.lsp4j.MessageActionItem;
import org.eclipse.lsp4j.MessageParams;
import org.eclipse.lsp4j.PublishDiagnosticsParams;
import org.eclipse.lsp4j.ReferenceParams;
import org.eclipse.lsp4j.RegistrationParams;
import org.eclipse.lsp4j.RenameParams;
import org.eclipse.lsp4j.SemanticHighlightingParams;
import org.eclipse.lsp4j.ShowMessageRequestParams;
import org.eclipse.lsp4j.SignatureHelp;
import org.eclipse.lsp4j.SymbolInformation;
import org.eclipse.lsp4j.TextDocumentPositionParams;
import org.eclipse.lsp4j.TextEdit;
import org.eclipse.lsp4j.UnregistrationParams;
import org.eclipse.lsp4j.WillSaveTextDocumentParams;
import org.eclipse.lsp4j.WorkspaceEdit;
import org.eclipse.lsp4j.WorkspaceSymbolParams;
import org.eclipse.lsp4j.jsonrpc.messages.CancelParams;
import org.eclipse.lsp4j.jsonrpc.messages.Either;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Handles requests between server and client
 */
public interface RequestManager {

    //Client
    void showMessage(MessageParams messageParams);

    CompletableFuture<MessageActionItem> showMessageRequest(ShowMessageRequestParams showMessageRequestParams);

    void logMessage(MessageParams messageParams);

    void telemetryEvent(Object o);

    CompletableFuture<Void> registerCapability(RegistrationParams params);

    CompletableFuture<Void> unregisterCapability(UnregistrationParams params);

    CompletableFuture<ApplyWorkspaceEditResponse> applyEdit(ApplyWorkspaceEditParams params);

    void publishDiagnostics(PublishDiagnosticsParams publishDiagnosticsParams);

    //Server
    //General
    CompletableFuture<InitializeResult> initialize(InitializeParams params);

    void initialized(InitializedParams params);

    CompletableFuture<Object> shutdown();

    void exit();

    void cancelRequest(CancelParams params);

    //Workspace
    void didChangeConfiguration(DidChangeConfigurationParams params);

    void didChangeWatchedFiles(DidChangeWatchedFilesParams params);

    CompletableFuture<List<? extends SymbolInformation>> symbol(WorkspaceSymbolParams params);

    CompletableFuture<Object> executeCommand(ExecuteCommandParams params);

    //Document
    void didOpen(DidOpenTextDocumentParams params);

    void didChange(DidChangeTextDocumentParams params);

    void willSave(WillSaveTextDocumentParams params);

    CompletableFuture<List<TextEdit>> willSaveWaitUntil(WillSaveTextDocumentParams params);

    void didSave(DidSaveTextDocumentParams params);

    void didClose(DidCloseTextDocumentParams params);

    CompletableFuture<Either<List<CompletionItem>, CompletionList>> completion(CompletionParams params);

    CompletableFuture<CompletionItem> completionItemResolve(CompletionItem unresolved);

    CompletableFuture<Hover> hover(TextDocumentPositionParams params);

    CompletableFuture<SignatureHelp> signatureHelp(TextDocumentPositionParams params);

    CompletableFuture<List<? extends Location>> references(ReferenceParams params);

    CompletableFuture<List<? extends DocumentHighlight>> documentHighlight(TextDocumentPositionParams params);

    CompletableFuture<List<Either<SymbolInformation, DocumentSymbol>>> documentSymbol(DocumentSymbolParams params);

    CompletableFuture<List<? extends TextEdit>> formatting(DocumentFormattingParams params);

    CompletableFuture<List<? extends TextEdit>> rangeFormatting(DocumentRangeFormattingParams params);

    CompletableFuture<List<? extends TextEdit>> onTypeFormatting(DocumentOnTypeFormattingParams params);

    CompletableFuture<List<? extends Location>> definition(TextDocumentPositionParams params);

    CompletableFuture<List<Either<Command, CodeAction>>> codeAction(CodeActionParams params);

    CompletableFuture<List<? extends CodeLens>> codeLens(CodeLensParams params);

    CompletableFuture<CodeLens> resolveCodeLens(CodeLens unresolved);

    CompletableFuture<List<DocumentLink>> documentLink(DocumentLinkParams params);

    CompletableFuture<DocumentLink> documentLinkResolve(DocumentLink unresolved);

    CompletableFuture<WorkspaceEdit> rename(RenameParams params);

    CompletableFuture<List<? extends Location>> implementation(TextDocumentPositionParams params);

    CompletableFuture<List<? extends Location>> typeDefinition(TextDocumentPositionParams params);

    CompletableFuture<List<ColorInformation>> documentColor(DocumentColorParams params);

    CompletableFuture<List<ColorPresentation>> colorPresentation(ColorPresentationParams params);

    CompletableFuture<List<FoldingRange>> foldingRange(FoldingRangeRequestParams params);

    void semanticHighlighting(SemanticHighlightingParams params);
}
