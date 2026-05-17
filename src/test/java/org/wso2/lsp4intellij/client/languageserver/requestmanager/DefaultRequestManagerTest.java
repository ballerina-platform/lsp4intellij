/*
 * Copyright (c) 2026, WSO2 LLC. (http://www.wso2.org) All Rights Reserved.
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

import org.eclipse.lsp4j.CodeActionOptions;
import org.eclipse.lsp4j.CodeActionParams;
import org.eclipse.lsp4j.CompletionItem;
import org.eclipse.lsp4j.CompletionList;
import org.eclipse.lsp4j.CompletionOptions;
import org.eclipse.lsp4j.CompletionParams;
import org.eclipse.lsp4j.DidChangeTextDocumentParams;
import org.eclipse.lsp4j.DidCloseTextDocumentParams;
import org.eclipse.lsp4j.DidOpenTextDocumentParams;
import org.eclipse.lsp4j.DidSaveTextDocumentParams;
import org.eclipse.lsp4j.FoldingRangeRequestParams;
import org.eclipse.lsp4j.Hover;
import org.eclipse.lsp4j.HoverOptions;
import org.eclipse.lsp4j.HoverParams;
import org.eclipse.lsp4j.InitializeParams;
import org.eclipse.lsp4j.InitializeResult;
import org.eclipse.lsp4j.RenameOptions;
import org.eclipse.lsp4j.RenameParams;
import org.eclipse.lsp4j.SaveOptions;
import org.eclipse.lsp4j.ServerCapabilities;
import org.eclipse.lsp4j.TextDocumentSyncKind;
import org.eclipse.lsp4j.TextDocumentSyncOptions;
import org.eclipse.lsp4j.WorkspaceEdit;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.eclipse.lsp4j.services.LanguageClient;
import org.eclipse.lsp4j.services.LanguageServer;
import org.eclipse.lsp4j.services.TextDocumentService;
import org.eclipse.lsp4j.services.WorkspaceService;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.wso2.lsp4intellij.client.languageserver.ServerStatus;
import org.wso2.lsp4intellij.client.languageserver.wrapper.LanguageServerWrapper;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link DefaultRequestManager}: the capability-gating logic that decides whether
 * each LSP request is dispatched to the server or dropped (returns null).
 *
 * <p>All IntelliJ platform dependencies are absent — the only collaborators are mocked LSP-layer
 * objects (wrapper, server, its services, and client).
 *
 * <p>The dominant pattern under test: each method calls {@link DefaultRequestManager#checkStatus()}
 * first (requires {@link ServerStatus#INITIALIZED}), then checks the relevant
 * {@link ServerCapabilities} flag, and only then delegates to the underlying service.
 */
public class DefaultRequestManagerTest {

    private LanguageServerWrapper wrapper;
    private LanguageServer server;
    private TextDocumentService textDocumentService;
    private WorkspaceService workspaceService;
    private LanguageClient client;

    @Before
    public void setUp() {
        wrapper = mock(LanguageServerWrapper.class);
        server = mock(LanguageServer.class);
        textDocumentService = mock(TextDocumentService.class);
        workspaceService = mock(WorkspaceService.class);
        client = mock(LanguageClient.class);
        when(server.getTextDocumentService()).thenReturn(textDocumentService);
        when(server.getWorkspaceService()).thenReturn(workspaceService);
    }

    /** Builds a manager using a sync-kind-based (Either left) text document sync configuration. */
    private DefaultRequestManager managerWithSyncKind(TextDocumentSyncKind kind) {
        ServerCapabilities caps = new ServerCapabilities();
        caps.setTextDocumentSync(kind);
        return new DefaultRequestManager(wrapper, server, client, caps);
    }

    /** Builds a manager using a granular TextDocumentSyncOptions (Either right) configuration. */
    private DefaultRequestManager managerWithSyncOptions(TextDocumentSyncOptions opts) {
        ServerCapabilities caps = new ServerCapabilities();
        caps.setTextDocumentSync(opts);
        return new DefaultRequestManager(wrapper, server, client, caps);
    }

    /**
     * Builds a manager with the supplied capabilities. Defaults text-document sync to
     * {@link TextDocumentSyncKind#Incremental} if not already set, because the
     * {@link DefaultRequestManager} constructor NPEs on a null sync field.
     */
    private DefaultRequestManager managerWithCaps(ServerCapabilities caps) {
        if (caps.getTextDocumentSync() == null) {
            caps.setTextDocumentSync(TextDocumentSyncKind.Incremental);
        }
        return new DefaultRequestManager(wrapper, server, client, caps);
    }

    // ── checkStatus ──────────────────────────────────────────────────────────

    /**
     * Verifies that {@link DefaultRequestManager#checkStatus()} returns true only when the
     * wrapper is in {@link ServerStatus#INITIALIZED} state, and false for every other state.
     */
    @Test
    public void checkStatusReturnsTrueOnlyForInitialized() {
        DefaultRequestManager mgr = managerWithSyncKind(TextDocumentSyncKind.Incremental);
        for (ServerStatus status : ServerStatus.values()) {
            when(wrapper.getStatus()).thenReturn(status);
            if (status == ServerStatus.INITIALIZED) {
                Assert.assertTrue("Expected true for INITIALIZED", mgr.checkStatus());
            } else {
                Assert.assertFalse("Expected false for " + status, mgr.checkStatus());
            }
        }
    }

    /**
     * Verifies that notification methods respect the status gate: when the server is not
     * INITIALIZED, the underlying text document service is never invoked.
     */
    @Test
    public void notificationMethodsAreSkippedWhenNotInitialized() {
        when(wrapper.getStatus()).thenReturn(ServerStatus.STOPPED);
        DefaultRequestManager mgr = managerWithSyncKind(TextDocumentSyncKind.Incremental);
        mgr.didOpen(new DidOpenTextDocumentParams());
        verify(textDocumentService, never()).didOpen(any());
    }

    // ── initialize / shutdown ─────────────────────────────────────────────────

    /**
     * Verifies that {@link DefaultRequestManager#initialize(InitializeParams)} returns null
     * when the server is not INITIALIZED.
     */
    @Test
    public void initializeReturnsNullWhenNotInitialized() {
        when(wrapper.getStatus()).thenReturn(ServerStatus.STOPPED);
        Assert.assertNull(managerWithSyncKind(TextDocumentSyncKind.Incremental)
                .initialize(new InitializeParams()));
    }

    /**
     * Verifies that {@link DefaultRequestManager#initialize(InitializeParams)} delegates to the
     * server and returns the same future when status is INITIALIZED.
     */
    @Test
    public void initializeDelegatesWhenInitialized() {
        when(wrapper.getStatus()).thenReturn(ServerStatus.INITIALIZED);
        CompletableFuture<InitializeResult> future = CompletableFuture.completedFuture(new InitializeResult());
        when(server.initialize(any())).thenReturn(future);

        Assert.assertSame(future, managerWithSyncKind(TextDocumentSyncKind.Incremental)
                .initialize(new InitializeParams()));
    }

    /**
     * Verifies that when the server throws during initialize, the wrapper's
     * {@link LanguageServerWrapper#crashed(Exception)} method is invoked and the call returns null.
     */
    @Test
    public void initializeCrashesWrapperOnException() {
        when(wrapper.getStatus()).thenReturn(ServerStatus.INITIALIZED);
        RuntimeException ex = new RuntimeException("boom");
        when(server.initialize(any())).thenThrow(ex);

        Assert.assertNull(managerWithSyncKind(TextDocumentSyncKind.Incremental)
                .initialize(new InitializeParams()));
        verify(wrapper).crashed(ex);
    }

    /**
     * Verifies that {@link DefaultRequestManager#shutdown()} returns null when not INITIALIZED.
     */
    @Test
    public void shutdownReturnsNullWhenNotInitialized() {
        when(wrapper.getStatus()).thenReturn(ServerStatus.STOPPED);
        Assert.assertNull(managerWithSyncKind(TextDocumentSyncKind.Incremental).shutdown());
    }

    /**
     * Verifies that {@link DefaultRequestManager#shutdown()} delegates to the server and returns
     * the same future when status is INITIALIZED.
     */
    @Test
    public void shutdownDelegatesWhenInitialized() {
        when(wrapper.getStatus()).thenReturn(ServerStatus.INITIALIZED);
        CompletableFuture<Object> future = CompletableFuture.completedFuture(null);
        when(server.shutdown()).thenReturn(future);

        Assert.assertSame(future, managerWithSyncKind(TextDocumentSyncKind.Incremental).shutdown());
    }

    // ── didOpen ──────────────────────────────────────────────────────────────

    /**
     * Verifies that {@link DefaultRequestManager#didOpen(DidOpenTextDocumentParams)} calls the
     * text document service when the sync kind is Incremental (not None).
     */
    @Test
    public void didOpenDelegatesWhenSyncKindIsIncremental() {
        when(wrapper.getStatus()).thenReturn(ServerStatus.INITIALIZED);
        managerWithSyncKind(TextDocumentSyncKind.Incremental).didOpen(new DidOpenTextDocumentParams());
        verify(textDocumentService).didOpen(any());
    }

    /**
     * Verifies that {@link DefaultRequestManager#didOpen(DidOpenTextDocumentParams)} skips the
     * service when the sync kind is None.
     */
    @Test
    public void didOpenSkipsWhenSyncKindIsNone() {
        when(wrapper.getStatus()).thenReturn(ServerStatus.INITIALIZED);
        managerWithSyncKind(TextDocumentSyncKind.None).didOpen(new DidOpenTextDocumentParams());
        verify(textDocumentService, never()).didOpen(any());
    }

    /**
     * Verifies that {@link DefaultRequestManager#didOpen(DidOpenTextDocumentParams)} delegates
     * when {@link TextDocumentSyncOptions#getOpenClose()} is true.
     */
    @Test
    public void didOpenDelegatesWhenOpenCloseOptionIsTrue() {
        when(wrapper.getStatus()).thenReturn(ServerStatus.INITIALIZED);
        TextDocumentSyncOptions opts = new TextDocumentSyncOptions();
        opts.setOpenClose(true);
        managerWithSyncOptions(opts).didOpen(new DidOpenTextDocumentParams());
        verify(textDocumentService).didOpen(any());
    }

    /**
     * Verifies that {@link DefaultRequestManager#didOpen(DidOpenTextDocumentParams)} skips
     * when {@link TextDocumentSyncOptions#getOpenClose()} is false.
     */
    @Test
    public void didOpenSkipsWhenOpenCloseOptionIsFalse() {
        when(wrapper.getStatus()).thenReturn(ServerStatus.INITIALIZED);
        TextDocumentSyncOptions opts = new TextDocumentSyncOptions();
        opts.setOpenClose(false);
        managerWithSyncOptions(opts).didOpen(new DidOpenTextDocumentParams());
        verify(textDocumentService, never()).didOpen(any());
    }

    // ── didChange ─────────────────────────────────────────────────────────────

    /**
     * Verifies that {@link DefaultRequestManager#didChange(DidChangeTextDocumentParams)} always
     * delegates in sync-kind mode (textDocumentOptions is null).
     */
    @Test
    public void didChangeDelegatesInSyncKindMode() {
        when(wrapper.getStatus()).thenReturn(ServerStatus.INITIALIZED);
        managerWithSyncKind(TextDocumentSyncKind.Incremental).didChange(new DidChangeTextDocumentParams());
        verify(textDocumentService).didChange(any());
    }

    /**
     * Verifies that {@link DefaultRequestManager#didChange(DidChangeTextDocumentParams)} skips
     * the service when {@link TextDocumentSyncOptions#getChange()} is null.
     */
    @Test
    public void didChangeSkipsWhenChangeOptionIsNull() {
        when(wrapper.getStatus()).thenReturn(ServerStatus.INITIALIZED);
        // opts.change is null by default
        managerWithSyncOptions(new TextDocumentSyncOptions()).didChange(new DidChangeTextDocumentParams());
        verify(textDocumentService, never()).didChange(any());
    }

    /**
     * Verifies that {@link DefaultRequestManager#didChange(DidChangeTextDocumentParams)} delegates
     * when {@link TextDocumentSyncOptions#getChange()} is explicitly set.
     */
    @Test
    public void didChangeDelegatesWhenChangeOptionIsSet() {
        when(wrapper.getStatus()).thenReturn(ServerStatus.INITIALIZED);
        TextDocumentSyncOptions opts = new TextDocumentSyncOptions();
        opts.setChange(TextDocumentSyncKind.Incremental);
        managerWithSyncOptions(opts).didChange(new DidChangeTextDocumentParams());
        verify(textDocumentService).didChange(any());
    }

    // ── didSave ───────────────────────────────────────────────────────────────

    /**
     * Verifies that {@link DefaultRequestManager#didSave(DidSaveTextDocumentParams)} skips in
     * sync-kind mode because textDocumentOptions is null.
     */
    @Test
    public void didSaveSkipsInSyncKindMode() {
        when(wrapper.getStatus()).thenReturn(ServerStatus.INITIALIZED);
        managerWithSyncKind(TextDocumentSyncKind.Incremental).didSave(new DidSaveTextDocumentParams());
        verify(textDocumentService, never()).didSave(any());
    }

    /**
     * Verifies that {@link DefaultRequestManager#didSave(DidSaveTextDocumentParams)} skips
     * when {@link TextDocumentSyncOptions#getSave()} is null.
     */
    @Test
    public void didSaveSkipsWhenSaveOptionIsNull() {
        when(wrapper.getStatus()).thenReturn(ServerStatus.INITIALIZED);
        // opts.save is null by default
        managerWithSyncOptions(new TextDocumentSyncOptions()).didSave(new DidSaveTextDocumentParams());
        verify(textDocumentService, never()).didSave(any());
    }

    /**
     * Verifies that {@link DefaultRequestManager#didSave(DidSaveTextDocumentParams)} delegates
     * when {@link TextDocumentSyncOptions#getSave()} is set.
     */
    @Test
    public void didSaveDelegatesWhenSaveOptionIsSet() {
        when(wrapper.getStatus()).thenReturn(ServerStatus.INITIALIZED);
        TextDocumentSyncOptions opts = new TextDocumentSyncOptions();
        opts.setSave(new SaveOptions());
        managerWithSyncOptions(opts).didSave(new DidSaveTextDocumentParams());
        verify(textDocumentService).didSave(any());
    }

    // ── didClose ──────────────────────────────────────────────────────────────

    /**
     * Verifies that {@link DefaultRequestManager#didClose(DidCloseTextDocumentParams)} skips in
     * sync-kind mode because textDocumentOptions is null, making openClose default to false.
     */
    @Test
    public void didCloseSkipsInSyncKindMode() {
        when(wrapper.getStatus()).thenReturn(ServerStatus.INITIALIZED);
        managerWithSyncKind(TextDocumentSyncKind.Incremental).didClose(new DidCloseTextDocumentParams());
        verify(textDocumentService, never()).didClose(any());
    }

    /**
     * Verifies that {@link DefaultRequestManager#didClose(DidCloseTextDocumentParams)} delegates
     * when {@link TextDocumentSyncOptions#getOpenClose()} is true.
     */
    @Test
    public void didCloseDelegatesWhenOpenCloseOptionIsTrue() {
        when(wrapper.getStatus()).thenReturn(ServerStatus.INITIALIZED);
        TextDocumentSyncOptions opts = new TextDocumentSyncOptions();
        opts.setOpenClose(true);
        managerWithSyncOptions(opts).didClose(new DidCloseTextDocumentParams());
        verify(textDocumentService).didClose(any());
    }

    // ── completion ────────────────────────────────────────────────────────────

    /**
     * Verifies that {@link DefaultRequestManager#completion(CompletionParams)} returns null
     * when no completion provider is declared in server capabilities.
     */
    @Test
    public void completionReturnsNullWhenProviderAbsent() {
        when(wrapper.getStatus()).thenReturn(ServerStatus.INITIALIZED);
        Assert.assertNull(managerWithCaps(new ServerCapabilities()).completion(new CompletionParams()));
    }

    /**
     * Verifies that {@link DefaultRequestManager#completion(CompletionParams)} delegates to the
     * text document service when a completion provider is declared.
     */
    @Test
    public void completionDelegatesWhenProviderPresent() {
        when(wrapper.getStatus()).thenReturn(ServerStatus.INITIALIZED);
        CompletableFuture<Either<List<CompletionItem>, CompletionList>> future = new CompletableFuture<>();
        when(textDocumentService.completion(any())).thenReturn(future);

        ServerCapabilities caps = new ServerCapabilities();
        caps.setCompletionProvider(new CompletionOptions());
        Assert.assertSame(future, managerWithCaps(caps).completion(new CompletionParams()));
    }

    // ── hover ─────────────────────────────────────────────────────────────────

    /**
     * Verifies that {@link DefaultRequestManager#hover(HoverParams)} returns null when no hover
     * provider is declared.
     */
    @Test
    public void hoverReturnsNullWhenProviderAbsent() {
        when(wrapper.getStatus()).thenReturn(ServerStatus.INITIALIZED);
        Assert.assertNull(managerWithCaps(new ServerCapabilities()).hover(new HoverParams()));
    }

    /**
     * Verifies that {@link DefaultRequestManager#hover(HoverParams)} delegates when the hover
     * provider is {@code Either.forLeft(true)}.
     */
    @Test
    public void hoverDelegatesWhenProviderIsLeftTrue() {
        when(wrapper.getStatus()).thenReturn(ServerStatus.INITIALIZED);
        when(textDocumentService.hover(any())).thenReturn(CompletableFuture.completedFuture(new Hover()));

        ServerCapabilities caps = new ServerCapabilities();
        caps.setHoverProvider(Either.forLeft(true));
        Assert.assertNotNull(managerWithCaps(caps).hover(new HoverParams()));
    }

    /**
     * Verifies that {@link DefaultRequestManager#hover(HoverParams)} delegates when the hover
     * provider is declared with {@link HoverOptions} (Either right).
     */
    @Test
    public void hoverDelegatesWhenProviderHasOptions() {
        when(wrapper.getStatus()).thenReturn(ServerStatus.INITIALIZED);
        when(textDocumentService.hover(any())).thenReturn(CompletableFuture.completedFuture(new Hover()));

        ServerCapabilities caps = new ServerCapabilities();
        caps.setHoverProvider(Either.forRight(new HoverOptions()));
        Assert.assertNotNull(managerWithCaps(caps).hover(new HoverParams()));
    }

    // ── foldingRange ──────────────────────────────────────────────────────────

    /**
     * Verifies that {@link DefaultRequestManager#foldingRange(FoldingRangeRequestParams)} returns
     * null when no folding range provider is declared.
     */
    @Test
    public void foldingRangeReturnsNullWhenProviderAbsent() {
        when(wrapper.getStatus()).thenReturn(ServerStatus.INITIALIZED);
        Assert.assertNull(managerWithCaps(new ServerCapabilities())
                .foldingRange(new FoldingRangeRequestParams()));
    }

    /**
     * Verifies that {@link DefaultRequestManager#foldingRange(FoldingRangeRequestParams)} delegates
     * when a folding range provider is declared.
     */
    @Test
    public void foldingRangeDelegatesWhenProviderPresent() {
        when(wrapper.getStatus()).thenReturn(ServerStatus.INITIALIZED);
        when(textDocumentService.foldingRange(any())).thenReturn(CompletableFuture.completedFuture(null));

        ServerCapabilities caps = new ServerCapabilities();
        caps.setFoldingRangeProvider(Either.forLeft(true));
        Assert.assertNotNull(managerWithCaps(caps).foldingRange(new FoldingRangeRequestParams()));
    }

    // ── checkProvider / rename ────────────────────────────────────────────────

    /**
     * Verifies that {@link DefaultRequestManager#checkProvider(Either)} returns false for null.
     */
    @Test
    public void checkProviderReturnsFalseForNull() {
        Assert.assertFalse(managerWithSyncKind(TextDocumentSyncKind.Incremental).checkProvider(null));
    }

    /**
     * Verifies that {@link DefaultRequestManager#checkProvider(Either)} returns false when the
     * provider is {@code Either.forLeft(false)}.
     */
    @Test
    public void checkProviderReturnsFalseWhenLeftIsFalse() {
        Assert.assertFalse(managerWithSyncKind(TextDocumentSyncKind.Incremental)
                .checkProvider(Either.forLeft(false)));
    }

    /**
     * Verifies that {@link DefaultRequestManager#checkProvider(Either)} returns true when the
     * provider is {@code Either.forLeft(true)}.
     */
    @Test
    public void checkProviderReturnsTrueWhenLeftIsTrue() {
        Assert.assertTrue(managerWithSyncKind(TextDocumentSyncKind.Incremental)
                .checkProvider(Either.forLeft(true)));
    }

    /**
     * Verifies that {@link DefaultRequestManager#checkProvider(Either)} returns true when the
     * provider is a right-side value ({@link RenameOptions}).
     */
    @Test
    public void checkProviderReturnsTrueWhenRight() {
        Assert.assertTrue(managerWithSyncKind(TextDocumentSyncKind.Incremental)
                .checkProvider(Either.forRight(new RenameOptions())));
    }

    /**
     * Verifies that {@link DefaultRequestManager#rename(RenameParams)} returns null when the
     * rename provider is not declared.
     */
    @Test
    public void renameReturnsNullWhenProviderNull() {
        when(wrapper.getStatus()).thenReturn(ServerStatus.INITIALIZED);
        Assert.assertNull(managerWithCaps(new ServerCapabilities()).rename(new RenameParams()));
    }

    /**
     * Verifies that {@link DefaultRequestManager#rename(RenameParams)} delegates when the rename
     * provider is {@code Either.forLeft(true)}.
     */
    @Test
    public void renameDelegatesWhenProviderIsSet() {
        when(wrapper.getStatus()).thenReturn(ServerStatus.INITIALIZED);
        when(textDocumentService.rename(any()))
                .thenReturn(CompletableFuture.completedFuture(new WorkspaceEdit()));

        ServerCapabilities caps = new ServerCapabilities();
        caps.setRenameProvider(Either.forLeft(true));
        Assert.assertNotNull(managerWithCaps(caps).rename(new RenameParams()));
    }

    // ── codeAction ────────────────────────────────────────────────────────────

    /**
     * Verifies that {@link DefaultRequestManager#codeAction(CodeActionParams)} returns null
     * when no code action provider is declared.
     */
    @Test
    public void codeActionReturnsNullWhenProviderNull() {
        when(wrapper.getStatus()).thenReturn(ServerStatus.INITIALIZED);
        Assert.assertNull(managerWithCaps(new ServerCapabilities()).codeAction(new CodeActionParams()));
    }

    /**
     * Verifies that {@link DefaultRequestManager#codeAction(CodeActionParams)} delegates when
     * the provider is {@code Either.forLeft(true)}.
     */
    @Test
    public void codeActionDelegatesWhenProviderIsLeftTrue() {
        when(wrapper.getStatus()).thenReturn(ServerStatus.INITIALIZED);
        when(textDocumentService.codeAction(any())).thenReturn(CompletableFuture.completedFuture(null));

        ServerCapabilities caps = new ServerCapabilities();
        caps.setCodeActionProvider(Either.forLeft(true));
        Assert.assertNotNull(managerWithCaps(caps).codeAction(new CodeActionParams()));
    }

    /**
     * Verifies that {@link DefaultRequestManager#codeAction(CodeActionParams)} delegates when
     * the provider is declared with {@link CodeActionOptions} (Either right).
     */
    @Test
    public void codeActionDelegatesWhenProviderIsRightWithOptions() {
        when(wrapper.getStatus()).thenReturn(ServerStatus.INITIALIZED);
        when(textDocumentService.codeAction(any())).thenReturn(CompletableFuture.completedFuture(null));

        ServerCapabilities caps = new ServerCapabilities();
        caps.setCodeActionProvider(Either.forRight(new CodeActionOptions()));
        Assert.assertNotNull(managerWithCaps(caps).codeAction(new CodeActionParams()));
    }
}
