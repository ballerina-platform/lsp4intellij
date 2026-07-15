/*
 * Copyright (c) 2026, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
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
package org.wso2.lsp4intellij.integration;

import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import org.eclipse.lsp4j.DidChangeConfigurationParams;
import org.eclipse.lsp4j.DidChangeTextDocumentParams;
import org.eclipse.lsp4j.DidChangeWatchedFilesParams;
import org.eclipse.lsp4j.DidCloseTextDocumentParams;
import org.eclipse.lsp4j.DidOpenTextDocumentParams;
import org.eclipse.lsp4j.DidSaveTextDocumentParams;
import org.eclipse.lsp4j.InitializeParams;
import org.eclipse.lsp4j.InitializeResult;
import org.eclipse.lsp4j.ServerCapabilities;
import org.eclipse.lsp4j.TextDocumentSyncKind;
import org.eclipse.lsp4j.jsonrpc.Launcher;
import org.eclipse.lsp4j.launch.LSPLauncher;
import org.eclipse.lsp4j.services.LanguageClient;
import org.eclipse.lsp4j.services.LanguageServer;
import org.eclipse.lsp4j.services.TextDocumentService;
import org.eclipse.lsp4j.services.WorkspaceService;
import org.wso2.lsp4intellij.IntellijLanguageClient;
import org.wso2.lsp4intellij.client.connection.StreamConnectionProvider;
import org.wso2.lsp4intellij.client.languageserver.ServerStatus;
import org.wso2.lsp4intellij.client.languageserver.serverdefinition.LanguageServerDefinition;
import org.wso2.lsp4intellij.client.languageserver.wrapper.LanguageServerWrapper;
import org.wso2.lsp4intellij.utils.FileUtils;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.util.HashSet;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

/**
 * End-to-end tests which run the library against an in-process stub language server connected
 * over piped streams. The tests drive the same entry points a consuming plugin uses
 * ({@code addServerDefinition}, {@code editorOpened}, {@code editorClosed}) and assert on the
 * protocol messages the stub server receives.
 */
public class LspServerIntegrationTest extends BasePlatformTestCase {

    private static final int TIMEOUT_SECONDS = 30;
    private static final String FILE_CONTENT = "hello lsp";

    private StubLanguageServer stubServer;

    @Override
    protected boolean runInDispatchThread() {
        // The library completes connections on background threads; the test thread must be able
        // to block on latches while the event dispatch thread stays free.
        return false;
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        stubServer = new StubLanguageServer();
    }

    @Override
    protected void tearDown() throws Exception {
        try {
            String projectUri = FileUtils.projectToUri(getProject());
            if (projectUri != null) {
                for (LanguageServerWrapper wrapper
                        : new HashSet<>(IntellijLanguageClient.getAllServerWrappersFor(projectUri))) {
                    wrapper.stop(true);
                    IntellijLanguageClient.removeWrapper(wrapper);
                }
            }
        } finally {
            super.tearDown();
        }
    }

    public void testServerInitializesAndReceivesDidOpen() throws Exception {
        Editor editor = openEditorFor("stuba");

        assertTrue("initialize was not received", stubServer.initialized.await(TIMEOUT_SECONDS, TimeUnit.SECONDS));
        assertTrue("didOpen was not received", stubServer.didOpen.await(TIMEOUT_SECONDS, TimeUnit.SECONDS));
        assertEquals(FILE_CONTENT, stubServer.openParams.getTextDocument().getText());

        LanguageServerWrapper wrapper = LanguageServerWrapper.forEditor(editor);
        assertNotNull(wrapper);
        waitFor("server status must become INITIALIZED",
                () -> wrapper.getStatus() == ServerStatus.INITIALIZED);
    }

    public void testDocumentEditSendsDidChange() throws Exception {
        Editor editor = openEditorFor("stubb");
        assertTrue("didOpen was not received", stubServer.didOpen.await(TIMEOUT_SECONDS, TimeUnit.SECONDS));

        EdtTestUtil.runInEdtAndWait(() -> WriteCommandAction.runWriteCommandAction(getProject(),
                () -> editor.getDocument().insertString(0, "x")));

        assertTrue("didChange was not received", stubServer.didChange.await(TIMEOUT_SECONDS, TimeUnit.SECONDS));
        // The stub server declares full document sync, so the change event carries the whole text.
        assertEquals("x" + FILE_CONTENT, stubServer.changeParams.getContentChanges().get(0).getText());
        assertTrue(stubServer.changeParams.getTextDocument().getVersion() >= 1);
    }

    public void testEditorCloseSendsDidCloseAndStopsServer() throws Exception {
        Editor editor = openEditorFor("stubc");
        assertTrue("didOpen was not received", stubServer.didOpen.await(TIMEOUT_SECONDS, TimeUnit.SECONDS));

        LanguageServerWrapper wrapper = LanguageServerWrapper.forEditor(editor);
        assertNotNull(wrapper);

        IntellijLanguageClient.editorClosed(editor);

        assertTrue("didClose was not received", stubServer.didClose.await(TIMEOUT_SECONDS, TimeUnit.SECONDS));
        assertTrue("shutdown was not received", stubServer.shutdown.await(TIMEOUT_SECONDS, TimeUnit.SECONDS));
        waitFor("server status must become STOPPED", () -> wrapper.getStatus() == ServerStatus.STOPPED);
    }

    /**
     * Registers a stub server definition for the given extension, opens an editor on a matching
     * file, and routes the open event through the library entry point.
     */
    private Editor openEditorFor(String ext) {
        IntellijLanguageClient.addServerDefinition(new StubServerDefinition(ext, stubServer), getProject());
        EdtTestUtil.runInEdtAndWait(() -> myFixture.configureByText("test." + ext, FILE_CONTENT));
        Editor editor = myFixture.getEditor();
        IntellijLanguageClient.editorOpened(editor);
        return editor;
    }

    private void waitFor(String description, BooleanSupplier condition) throws InterruptedException {
        long deadline = System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(TIMEOUT_SECONDS);
        while (!condition.getAsBoolean()) {
            if (System.currentTimeMillis() > deadline) {
                fail(description);
            }
            Thread.sleep(50);
        }
    }

    /**
     * A server definition which connects to the in-process stub server instead of spawning a process.
     */
    private static class StubServerDefinition extends LanguageServerDefinition {

        private final LanguageServer server;

        StubServerDefinition(String ext, LanguageServer server) {
            this.ext = ext;
            this.server = server;
        }

        @Override
        public StreamConnectionProvider createConnectionProvider(String workingDir) {
            return new StubConnectionProvider(server);
        }
    }

    /**
     * Connects the client side of the library to an in-process lsp4j server over piped streams.
     */
    private static class StubConnectionProvider implements StreamConnectionProvider {

        private final LanguageServer server;
        private InputStream clientInput;
        private OutputStream clientOutput;
        private Future<Void> listening;

        StubConnectionProvider(LanguageServer server) {
            this.server = server;
        }

        @Override
        public void start() throws IOException {
            PipedInputStream serverInput = new PipedInputStream(65536);
            PipedOutputStream clientToServer = new PipedOutputStream(serverInput);
            PipedInputStream serverToClient = new PipedInputStream(65536);
            PipedOutputStream serverOutput = new PipedOutputStream(serverToClient);

            Launcher<LanguageClient> launcher = LSPLauncher.createServerLauncher(server, serverInput, serverOutput);
            listening = launcher.startListening();
            this.clientInput = serverToClient;
            this.clientOutput = clientToServer;
        }

        @Override
        public InputStream getInputStream() {
            return clientInput;
        }

        @Override
        public OutputStream getOutputStream() {
            return clientOutput;
        }

        @Override
        public void stop() {
            if (listening != null) {
                listening.cancel(true);
            }
            try {
                if (clientInput != null) {
                    clientInput.close();
                }
                if (clientOutput != null) {
                    clientOutput.close();
                }
            } catch (IOException ignored) {
                // The pipes are in-memory; nothing to clean up on failure.
            }
        }
    }

    /**
     * A minimal language server which records the notifications it receives.
     */
    private static class StubLanguageServer implements LanguageServer {

        final CountDownLatch initialized = new CountDownLatch(1);
        final CountDownLatch didOpen = new CountDownLatch(1);
        final CountDownLatch didChange = new CountDownLatch(1);
        final CountDownLatch didClose = new CountDownLatch(1);
        final CountDownLatch shutdown = new CountDownLatch(1);
        volatile DidOpenTextDocumentParams openParams;
        volatile DidChangeTextDocumentParams changeParams;

        @Override
        public CompletableFuture<InitializeResult> initialize(InitializeParams params) {
            ServerCapabilities capabilities = new ServerCapabilities();
            capabilities.setTextDocumentSync(TextDocumentSyncKind.Full);
            initialized.countDown();
            return CompletableFuture.completedFuture(new InitializeResult(capabilities));
        }

        @Override
        public CompletableFuture<Object> shutdown() {
            shutdown.countDown();
            return CompletableFuture.completedFuture(new Object());
        }

        @Override
        public void exit() {
        }

        @Override
        public TextDocumentService getTextDocumentService() {
            return new TextDocumentService() {
                @Override
                public void didOpen(DidOpenTextDocumentParams params) {
                    openParams = params;
                    didOpen.countDown();
                }

                @Override
                public void didChange(DidChangeTextDocumentParams params) {
                    changeParams = params;
                    didChange.countDown();
                }

                @Override
                public void didClose(DidCloseTextDocumentParams params) {
                    didClose.countDown();
                }

                @Override
                public void didSave(DidSaveTextDocumentParams params) {
                }
            };
        }

        @Override
        public WorkspaceService getWorkspaceService() {
            return new WorkspaceService() {
                @Override
                public void didChangeConfiguration(DidChangeConfigurationParams params) {
                }

                @Override
                public void didChangeWatchedFiles(DidChangeWatchedFilesParams params) {
                }
            };
        }
    }
}
