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
package org.wso2.lsp4intellij.client.languageserver.wrapper;

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.MessageType;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.impl.status.widget.StatusBarWidgetsManager;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.remoteServer.util.CloudNotifier;
import com.intellij.util.PlatformIcons;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.eclipse.lsp4j.ClientCapabilities;
import org.eclipse.lsp4j.CodeActionCapabilities;
import org.eclipse.lsp4j.CodeActionKindCapabilities;
import org.eclipse.lsp4j.CodeActionLiteralSupportCapabilities;
import org.eclipse.lsp4j.CompletionCapabilities;
import org.eclipse.lsp4j.CompletionItemCapabilities;
import org.eclipse.lsp4j.DefinitionCapabilities;
import org.eclipse.lsp4j.DidChangeWatchedFilesCapabilities;
import org.eclipse.lsp4j.DocumentHighlightCapabilities;
import org.eclipse.lsp4j.ExecuteCommandCapabilities;
import org.eclipse.lsp4j.FormattingCapabilities;
import org.eclipse.lsp4j.HoverCapabilities;
import org.eclipse.lsp4j.InitializeParams;
import org.eclipse.lsp4j.InitializeResult;
import org.eclipse.lsp4j.InitializedParams;
import org.eclipse.lsp4j.OnTypeFormattingCapabilities;
import org.eclipse.lsp4j.RangeFormattingCapabilities;
import org.eclipse.lsp4j.ReferencesCapabilities;
import org.eclipse.lsp4j.RenameCapabilities;
import org.eclipse.lsp4j.ServerCapabilities;
import org.eclipse.lsp4j.SignatureHelpCapabilities;
import org.eclipse.lsp4j.SymbolCapabilities;
import org.eclipse.lsp4j.SynchronizationCapabilities;
import org.eclipse.lsp4j.TextDocumentClientCapabilities;
import org.eclipse.lsp4j.TextDocumentSyncKind;
import org.eclipse.lsp4j.TextDocumentSyncOptions;
import org.eclipse.lsp4j.WorkspaceClientCapabilities;
import org.eclipse.lsp4j.WorkspaceEditCapabilities;
import org.eclipse.lsp4j.jsonrpc.Launcher;
import org.eclipse.lsp4j.jsonrpc.ResponseErrorException;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.eclipse.lsp4j.jsonrpc.messages.Message;
import org.eclipse.lsp4j.jsonrpc.messages.ResponseErrorCode;
import org.eclipse.lsp4j.jsonrpc.messages.ResponseMessage;
import org.eclipse.lsp4j.services.LanguageClient;
import org.eclipse.lsp4j.services.LanguageServer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.wso2.lsp4intellij.IntellijLanguageClient;
import org.wso2.lsp4intellij.client.DefaultLanguageClient;
import org.wso2.lsp4intellij.client.ServerWrapperBaseClientContext;
import org.wso2.lsp4intellij.statusbar.LSPServerStatusWidget;
import org.wso2.lsp4intellij.client.languageserver.ServerOptions;
import org.wso2.lsp4intellij.client.languageserver.ServerStatus;
import org.wso2.lsp4intellij.client.languageserver.requestmanager.DefaultRequestManager;
import org.wso2.lsp4intellij.client.languageserver.requestmanager.RequestManager;
import org.wso2.lsp4intellij.client.languageserver.serverdefinition.LanguageServerDefinition;
import org.wso2.lsp4intellij.editor.DocumentEventManager;
import org.wso2.lsp4intellij.editor.EditorEventManager;
import org.wso2.lsp4intellij.editor.EditorEventManagerBase;
import org.wso2.lsp4intellij.extensions.LSPExtensionManager;
import org.wso2.lsp4intellij.listeners.DocumentListenerImpl;
import org.wso2.lsp4intellij.listeners.EditorMouseListenerImpl;
import org.wso2.lsp4intellij.listeners.EditorMouseMotionListenerImpl;
import org.wso2.lsp4intellij.listeners.LSPCaretListenerImpl;
import org.wso2.lsp4intellij.requests.Timeouts;
import org.wso2.lsp4intellij.statusbar.LSPServerStatusWidgetFactory;
import org.wso2.lsp4intellij.utils.FileUtils;
import org.wso2.lsp4intellij.utils.LSPException;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.wso2.lsp4intellij.client.languageserver.ServerStatus.INITIALIZED;
import static org.wso2.lsp4intellij.client.languageserver.ServerStatus.STARTED;
import static org.wso2.lsp4intellij.client.languageserver.ServerStatus.STARTING;
import static org.wso2.lsp4intellij.client.languageserver.ServerStatus.STOPPED;
import static org.wso2.lsp4intellij.client.languageserver.ServerStatus.STOPPING;
import static org.wso2.lsp4intellij.requests.Timeout.getTimeout;
import static org.wso2.lsp4intellij.requests.Timeouts.INIT;
import static org.wso2.lsp4intellij.requests.Timeouts.SHUTDOWN;
import static org.wso2.lsp4intellij.utils.ApplicationUtils.computableReadAction;
import static org.wso2.lsp4intellij.utils.ApplicationUtils.invokeLater;
import static org.wso2.lsp4intellij.utils.ApplicationUtils.pool;
import static org.wso2.lsp4intellij.utils.FileUtils.editorToProjectFolderUri;
import static org.wso2.lsp4intellij.utils.FileUtils.editorToURIString;
import static org.wso2.lsp4intellij.utils.FileUtils.reloadEditors;
import static org.wso2.lsp4intellij.utils.FileUtils.sanitizeURI;

/**
 * The implementation of a LanguageServerWrapper (specific to a serverDefinition and a project)
 */
public class LanguageServerWrapper {

    public LanguageServerDefinition serverDefinition;
    private final LSPExtensionManager extManager;
    private final Project project;
    private final HashSet<Editor> toConnect = new HashSet<>();
    private final String projectRootPath;
    private final HashSet<String> urisUnderLspControl = new HashSet<>();
    private final HashSet<Editor> connectedEditors = new HashSet<>();
    private final Map<String, Set<EditorEventManager>> uriToEditorManagers = new HashMap<>();
    private LanguageServer languageServer;
    private LanguageClient client;
    private RequestManager requestManager;
    private InitializeResult initializeResult;
    private Future<?> launcherFuture;
    private CompletableFuture<InitializeResult> initializeFuture;
    private boolean capabilitiesAlreadyRequested = false;
    private int crashCount = 0;
    private volatile boolean alreadyShownTimeout = false;
    private volatile boolean alreadyShownCrash = false;
    private volatile ServerStatus status = STOPPED;
    private static final Map<Pair<String, String>, LanguageServerWrapper> uriToLanguageServerWrapper =
            new ConcurrentHashMap<>();
    private static final Map<Project, LanguageServerWrapper> projectToLanguageServerWrapper = new ConcurrentHashMap<>();
    private static final Logger LOG = Logger.getInstance(LanguageServerWrapper.class);
    private static final CloudNotifier notifier = new CloudNotifier("Language Server Protocol client");

    public LanguageServerWrapper(@NotNull LanguageServerDefinition serverDefinition, @NotNull Project project) {
        this(serverDefinition, project, null);
    }

    public LanguageServerWrapper(@NotNull LanguageServerDefinition serverDefinition, @NotNull Project project,
                                 @Nullable LSPExtensionManager extManager) {
        this.serverDefinition = serverDefinition;
        this.project = project;
        // We need to keep the project rootPath in addition to the project instance, since we cannot get the project
        // base path if the project is disposed.
        this.projectRootPath = project.getBasePath();
        this.extManager = extManager;
        projectToLanguageServerWrapper.put(project, this);
    }

    /**
     * @param uri     A file uri
     * @param project The related project
     * @return The wrapper for the given uri, or None
     */
    public static LanguageServerWrapper forUri(String uri, Project project) {
        return uriToLanguageServerWrapper.get(new ImmutablePair<>(uri, FileUtils.projectToUri(project)));
    }

    public static LanguageServerWrapper forVirtualFile(VirtualFile file, Project project) {
        return uriToLanguageServerWrapper.get(new ImmutablePair<>(FileUtils.VFSToURI(file), FileUtils.projectToUri(project)));
    }

    /**
     * @param editor An editor
     * @return The wrapper for the given editor, or None
     */
    public static LanguageServerWrapper forEditor(Editor editor) {
        return uriToLanguageServerWrapper.get(new ImmutablePair<>(editorToURIString(editor), editorToProjectFolderUri(editor)));
    }

    public static LanguageServerWrapper forProject(Project project) {
        return projectToLanguageServerWrapper.get(project);
    }

    public LanguageServerDefinition getServerDefinition() {
        return serverDefinition;
    }

    public String getProjectRootPath() {
        return projectRootPath;
    }

    /**
     * @return if the server supports willSaveWaitUntil
     */
    public boolean isWillSaveWaitUntil() {
        return Optional.ofNullable(getServerCapabilities())
                .map(ServerCapabilities::getTextDocumentSync)
                .map(Either::getRight)
                .map(TextDocumentSyncOptions::getWillSaveWaitUntil)
                .orElse(false);
    }

    /**
     * Warning: this is a long running operation
     *
     * @return the languageServer capabilities, or null if initialization job didn't complete
     */
    @Nullable
    public ServerCapabilities getServerCapabilities() {
        if (initializeResult != null)
            return initializeResult.getCapabilities();
        else {
            try {
                start();
                if (initializeFuture != null) {
                    initializeFuture.get((capabilitiesAlreadyRequested ? 0 : getTimeout(INIT)), TimeUnit.MILLISECONDS);
                    notifySuccess(INIT);
                }
            } catch (TimeoutException e) {
                notifyFailure(INIT);
                String msg = String.format("%s \n is not initialized after %d seconds",
                        serverDefinition.toString(), getTimeout(INIT) / 1000);
                LOG.warn(msg, e);
                invokeLater(() -> {
                    if (!alreadyShownTimeout) {
                        notifier.showMessage(msg, MessageType.WARNING);
                        alreadyShownTimeout = true;
                    }
                });
                stop(false);
            } catch (Exception e) {
                LOG.warn(e);
                stop(false);
            }
        }
        capabilitiesAlreadyRequested = true;
        return initializeResult != null ? initializeResult.getCapabilities() : null;
    }

    public void notifyResult(Timeouts timeouts, boolean success) {
        getWidget().ifPresent(widget -> widget.notifyResult(timeouts, success));
    }

    public void notifySuccess(Timeouts timeouts) {
        notifyResult(timeouts, true);
    }

    public void notifyFailure(Timeouts timeouts) {
        notifyResult(timeouts, false);
    }

    /**
     * Returns the EditorEventManager for a given uri
     * <p>
     * WARNING: actually a file can be present in multiple editors, this function just gives you one editor. use {@link #getEditorManagersFor(String)} instead
     * only use for document level events such as open, close, ...
     *
     * @param uri the URI as a string
     * @return the EditorEventManager (or null)
     */
    public EditorEventManager getEditorManagerFor(String uri) {
        FileEditor selectedEditor = FileEditorManager.getInstance(project).getSelectedEditor();

        if (selectedEditor == null) {
            return null;
        }
        VirtualFile currentOpenFile = selectedEditor.getFile();
        VirtualFile requestedFile = FileUtils.virtualFileFromURI(uri);
        if (currentOpenFile == null || requestedFile == null) {
            return null;
        }
        if (requestedFile.equals(currentOpenFile)) {
            return EditorEventManagerBase.forEditor((Editor) FileEditorManager.getInstance(project).getSelectedEditor());
        }
        if (uriToEditorManagers.containsKey(uri) && !uriToEditorManagers.get(uri).isEmpty()) {
            return (EditorEventManager) uriToEditorManagers.get(uri).toArray()[0];
        }

        return null;
    }

    public Set<EditorEventManager> getEditorManagersFor(String uri) {
        return uriToEditorManagers.get(uri);
    }

    /**
     * @return The request manager for this wrapper
     */
    public RequestManager getRequestManager() {
        return requestManager;
    }

    /**
     * @return whether the underlying connection to language languageServer is still active
     */
    public boolean isActive() {
        return launcherFuture != null && !launcherFuture.isDone() && !launcherFuture.isCancelled()
                && !alreadyShownTimeout && !alreadyShownCrash;
    }

    /**
     * Connects an editor to the languageServer
     *
     * @param editor the editor
     */
    public void connect(Editor editor) {
        if (editor == null) {
            LOG.warn("editor is null for " + serverDefinition);
            return;
        }
        if (!FileUtils.isEditorSupported(editor)) {
            LOG.debug("Editor hosts a unsupported file type by the LS library.");
            return;
        }

        String uri = editorToURIString(editor);
        if (connectedEditors.contains(editor)) {
            return;
        }
        ImmutablePair<String, String> key = new ImmutablePair<>(uri, editorToProjectFolderUri(editor));

        uriToLanguageServerWrapper.put(key, this);

        start();
        if (initializeFuture != null) {
            ServerCapabilities capabilities = getServerCapabilities();
            if (capabilities == null) {
                LOG.warn("Capabilities are null for " + serverDefinition);
                return;
            }

            initializeFuture.thenRun(() -> {
                if (connectedEditors.contains(editor)) {
                    return;
                }
                try {
                    Either<TextDocumentSyncKind, TextDocumentSyncOptions> syncOptions = capabilities.getTextDocumentSync();
                    if (syncOptions != null) {
                        //Todo - Implement
                        //  SelectionListenerImpl selectionListener = new SelectionListenerImpl();
                        DocumentListenerImpl documentListener = new DocumentListenerImpl();
                        EditorMouseListenerImpl mouseListener = new EditorMouseListenerImpl();
                        EditorMouseMotionListenerImpl mouseMotionListener = new EditorMouseMotionListenerImpl();
                        LSPCaretListenerImpl caretListener = new LSPCaretListenerImpl();

                        ServerOptions serverOptions = new ServerOptions(capabilities);
                        EditorEventManager manager;
                        if (extManager != null) {
                            manager = extManager.getExtendedEditorEventManagerFor(editor, documentListener,
                                    mouseListener, mouseMotionListener, caretListener, requestManager, serverOptions,
                                    this);
                            if (manager == null) {
                                manager = new EditorEventManager(editor, documentListener, mouseListener,
                                        mouseMotionListener, caretListener,
                                        requestManager, serverOptions, this);
                            }
                        } else {
                            manager = new EditorEventManager(editor, documentListener, mouseListener,
                                    mouseMotionListener, caretListener,
                                    requestManager, serverOptions, this);
                        }
                        // selectionListener.setManager(manager);
                        documentListener.setManager(manager);
                        mouseListener.setManager(manager);
                        mouseMotionListener.setManager(manager);
                        caretListener.setManager(manager);
                        manager.registerListeners();
                        if (!urisUnderLspControl.contains(uri)) {
                            manager.documentEventManager.registerListeners();
                        }
                        urisUnderLspControl.add(uri);
                        connectedEditors.add(editor);
                        if (uriToEditorManagers.containsKey(uri)) {
                            uriToEditorManagers.get(uri).add(manager);
                        } else {
                            Set<EditorEventManager> set = new HashSet<>();
                            set.add(manager);
                            uriToEditorManagers.put(uri, set);
                            manager.documentOpened();
                        }
                        LOG.info("Created a manager for " + uri);
                        synchronized (toConnect) {
                            toConnect.remove(editor);
                        }
                        for (Editor ed : new HashSet<>(toConnect)) {
                            connect(ed);
                        }
                        // Triggers annotators since this is the first editor which starts the LS
                        // and annotators are executed before LS is bootstrap to provide diagnostics.
                        computableReadAction(() -> {
                            PsiFile psiFile = PsiDocumentManager.getInstance(project).getPsiFile(editor.getDocument());
                            if (psiFile != null) {
                                DaemonCodeAnalyzer.getInstance(project).restart(psiFile);
                            }
                            return null;
                        });
                    }
                } catch (Exception e) {
                    LOG.error(e);
                }
            });

        } else {
            synchronized (toConnect) {
                toConnect.add(editor);
            }
        }
    }

    /*
     * The shutdown request is sent from the client to the server. It asks the server to shut down, but to not exit \
     * (otherwise the response might not be delivered correctly to the client).
     * Only if the exit flag is true, particular server instance will exit.
     */
    public void stop(boolean exit) {
        if (this.status == STOPPED || this.status == STOPPING) {
            return;
        }
        setStatus(STOPPING);

        if (initializeFuture != null) {
            initializeFuture.cancel(true);
        }

        try {
            if (languageServer != null) {
                CompletableFuture<Object> shutdown = languageServer.shutdown();
                shutdown.get(getTimeout(SHUTDOWN), TimeUnit.MILLISECONDS);
                notifySuccess(Timeouts.SHUTDOWN);
                if (exit) {
                    languageServer.exit();
                }
            }
        } catch (Exception e) {
            // most likely closed externally.
            notifyFailure(Timeouts.SHUTDOWN);
            LOG.warn("exception occured while trying to shut down", e);
        } finally {
            if (launcherFuture != null) {
                launcherFuture.cancel(true);
            }
            if (serverDefinition != null) {
                serverDefinition.stop(projectRootPath);
            }
            for (Editor ed : new HashSet<>(connectedEditors)) {
                disconnect(ed);
            }

            // sadly this whole editor closing stuff runs asynchronously, so we cannot be sure the state is really clean here...
            // therefore clear the mapping from here as it should be empty by now.
            DocumentEventManager.clearState();
            uriToEditorManagers.clear();
            urisUnderLspControl.clear();
            launcherFuture = null;
            capabilitiesAlreadyRequested = false;
            initializeResult = null;
            initializeFuture = null;
            languageServer = null;
            setStatus(STOPPED);
        }
    }

    /**
     * Checks if the wrapper is already connected to the document at the given path.
     *
     * @param location file location
     * @return True if the given file is connected.
     */
    public boolean isConnectedTo(String location) {
        return urisUnderLspControl.contains(location);
    }

    /**
     * @return the LanguageServer
     */
    @Nullable
    public LanguageServer getServer() {
        start();
        if (initializeFuture != null && !initializeFuture.isDone()) {
            initializeFuture.join();
        }
        return languageServer;
    }

    /**
     * Starts the LanguageServer
     */
    public void start() {
        if (status == STOPPED && !alreadyShownCrash && !alreadyShownTimeout) {
            setStatus(STARTING);
            try {
                Pair<InputStream, OutputStream> streams = serverDefinition.start(projectRootPath);
                InputStream inputStream = streams.getKey();
                OutputStream outputStream = streams.getValue();
                InitializeParams initParams = getInitParams();
                ExecutorService executorService = Executors.newCachedThreadPool();
                MessageHandler messageHandler = new MessageHandler(serverDefinition.getServerListener(), () -> getStatus() != STOPPED);
                if (extManager != null && extManager.getExtendedServerInterface() != null) {
                    Class<? extends LanguageServer> remoteServerInterFace = extManager.getExtendedServerInterface();
                    client = extManager.getExtendedClientFor(new ServerWrapperBaseClientContext(this));

                    Launcher<? extends LanguageServer> launcher = Launcher
                            .createLauncher(client, remoteServerInterFace, inputStream, outputStream, executorService,
                                    messageHandler);
                    languageServer = launcher.getRemoteProxy();
                    launcherFuture = launcher.startListening();
                } else {
                    client = new DefaultLanguageClient(new ServerWrapperBaseClientContext(this));
                    Launcher<LanguageServer> launcher = Launcher
                            .createLauncher(client, LanguageServer.class, inputStream, outputStream, executorService,
                                    messageHandler);
                    languageServer = launcher.getRemoteProxy();
                    launcherFuture = launcher.startListening();
                }
                messageHandler.setLanguageServer(languageServer);

                initializeFuture = languageServer.initialize(initParams).thenApply(res -> {
                    initializeResult = res;
                    LOG.info("Got initializeResult for " + serverDefinition + " ; " + projectRootPath);
                    if (extManager != null) {
                        requestManager = extManager.getExtendedRequestManagerFor(this, languageServer, client, res.getCapabilities());
                        if (requestManager == null) {
                            requestManager = new DefaultRequestManager(this, languageServer, client, res.getCapabilities());
                        }
                    } else {
                        requestManager = new DefaultRequestManager(this, languageServer, client, res.getCapabilities());
                    }
                    setStatus(STARTED);
                    // send the initialized message since some language servers depends on this message
                    requestManager.initialized(new InitializedParams());
                    setStatus(INITIALIZED);
                    return res;
                });
            } catch (LSPException | IOException e) {
                LOG.warn(e);
                invokeLater(() ->
                        notifier.showMessage(String.format("Can't start server due to %s", e.getMessage()),
                                MessageType.WARNING));
                removeServerWrapper();
            }
        }
    }

    private InitializeParams getInitParams() {
        InitializeParams initParams = new InitializeParams();
        initParams.setRootUri(FileUtils.pathToUri(projectRootPath));
        //TODO update capabilities when implemented
        WorkspaceClientCapabilities workspaceClientCapabilities = new WorkspaceClientCapabilities();
        workspaceClientCapabilities.setApplyEdit(true);
        workspaceClientCapabilities.setDidChangeWatchedFiles(new DidChangeWatchedFilesCapabilities());
        workspaceClientCapabilities.setExecuteCommand(new ExecuteCommandCapabilities());
        workspaceClientCapabilities.setWorkspaceEdit(new WorkspaceEditCapabilities());
        workspaceClientCapabilities.setSymbol(new SymbolCapabilities());
        workspaceClientCapabilities.setWorkspaceFolders(false);
        workspaceClientCapabilities.setConfiguration(false);

        TextDocumentClientCapabilities textDocumentClientCapabilities = new TextDocumentClientCapabilities();
        textDocumentClientCapabilities.setCodeAction(new CodeActionCapabilities());
        textDocumentClientCapabilities.getCodeAction().setCodeActionLiteralSupport(new CodeActionLiteralSupportCapabilities(new CodeActionKindCapabilities()));
        textDocumentClientCapabilities.setCompletion(new CompletionCapabilities(new CompletionItemCapabilities(true)));
        textDocumentClientCapabilities.setDefinition(new DefinitionCapabilities());
        textDocumentClientCapabilities.setDocumentHighlight(new DocumentHighlightCapabilities());
        textDocumentClientCapabilities.setFormatting(new FormattingCapabilities());
        textDocumentClientCapabilities.setHover(new HoverCapabilities());
        textDocumentClientCapabilities.setOnTypeFormatting(new OnTypeFormattingCapabilities());
        textDocumentClientCapabilities.setRangeFormatting(new RangeFormattingCapabilities());
        textDocumentClientCapabilities.setReferences(new ReferencesCapabilities());
        textDocumentClientCapabilities.setRename(new RenameCapabilities());
        textDocumentClientCapabilities.setSignatureHelp(new SignatureHelpCapabilities());
        textDocumentClientCapabilities.setSynchronization(new SynchronizationCapabilities(true, true, true));
        initParams.setCapabilities(
                new ClientCapabilities(workspaceClientCapabilities, textDocumentClientCapabilities, null));
        initParams.setInitializationOptions(
                serverDefinition.getInitializationOptions(URI.create(initParams.getRootUri())));

        return initParams;
    }

    public void logMessage(Message message) {
        if (message instanceof ResponseMessage) {
            ResponseMessage responseMessage = (ResponseMessage) message;
            if (responseMessage.getError() != null && (responseMessage.getId()
                    .equals(Integer.toString(ResponseErrorCode.RequestCancelled.getValue())))) {
                LOG.error(new ResponseErrorException(responseMessage.getError()));
            }
        }
    }

    public Project getProject() {
        return project;
    }

    public ServerStatus getStatus() {
        return status;
    }

    private void setStatus(ServerStatus status) {
        this.status = status;
        getWidget().ifPresent(widget -> widget.setStatus(status));
    }

    public void crashed(Exception e) {
        crashCount += 1;
        if (crashCount <= 3) {
            reconnect();
        } else {
            invokeLater(() -> {
                if (alreadyShownCrash) {
                    reconnect();
                } else {
                    int response = Messages.showYesNoDialog(String.format(
                            "LanguageServer for definition %s, project %s keeps crashing due to \n%s\n"
                            , serverDefinition.toString(), project.getName(), e.getMessage()),
                            "Language Server Client Warning", "Keep Connected", "Disconnect", PlatformIcons.CHECK_ICON);
                    if (response == Messages.NO) {
                        int confirm = Messages.showYesNoDialog("All the language server based plugin features will be disabled.\n" +
                                "Do you wish to continue?", "", PlatformIcons.WARNING_INTRODUCTION_ICON);
                        if (confirm == Messages.YES) {
                            // Disconnects from the language server.
                            stop(true);
                        } else {
                            reconnect();
                        }
                    } else {
                        reconnect();
                    }
                }
                alreadyShownCrash = true;
                crashCount = 0;
            });
        }
    }

    private void reconnect() {
        // Need to copy by value since connected editors gets cleared during 'stop()' invocation.
        final Set<String> connected = new HashSet<>(urisUnderLspControl);
        stop(true);
        for (String uri : connected) {
            connect(uri);
        }
    }

    public List<String> getConnectedFiles() {
        List<String> connected = new ArrayList<>();
        urisUnderLspControl.forEach(s -> {
            try {
                connected.add(new URI(sanitizeURI(s)).toString());
            } catch (URISyntaxException e) {
                LOG.warn(e);
            }
        });
        return connected;
    }

    public void removeWidget() {
        getWidget().ifPresent(LSPServerStatusWidget::dispose);
    }

    /**
     * Disconnects an editor from the LanguageServer
     *
     * @param editor The editor
     */
    public void disconnect(Editor editor) {
        EditorEventManager manager = EditorEventManagerBase.forEditor(editor);
        connectedEditors.remove(editor);
        if (manager != null) {
            manager.removeListeners();
            String uri = editorToURIString(editor);
            Set<EditorEventManager> set = uriToEditorManagers.get(uri);
            if (set != null) {
                set.remove(manager);
                if (set.isEmpty()) {
                    manager.documentClosed();
                    manager.documentEventManager.removeListeners();

                    uriToEditorManagers.remove(uri);
                    urisUnderLspControl.remove(uri);
                    uriToLanguageServerWrapper.remove(new ImmutablePair<>(uri, editorToProjectFolderUri(editor)));
                }
            }
        }

        if (connectedEditors.isEmpty()) {
            stop(true);
        }
    }

    /**
     * Disconnects an editor from the LanguageServer
     * <p>
     * WARNING: only use this method if you have no editor instance and you restart all connections to the language server for all open editors
     * prefer using disconnect(editor)
     *
     * @param uri        The file uri
     * @param projectUri The project root uri
     */
    public void disconnect(String uri, String projectUri) {
        uriToLanguageServerWrapper.remove(new ImmutablePair<>(sanitizeURI(uri), sanitizeURI(projectUri)));

        Set<EditorEventManager> managers = uriToEditorManagers.get(uri);
        if (managers == null) {
            return;
        }
        for (EditorEventManager manager : managers) {
            manager.removeListeners();
            manager.documentEventManager.removeListeners();
            connectedEditors.remove(manager.editor);
            Set<EditorEventManager> editorEventManagers = uriToEditorManagers.get(uri);
            if (editorEventManagers != null) {
                editorEventManagers.remove(manager);
                if (editorEventManagers.isEmpty()) {
                    uriToEditorManagers.remove(uri);
                    manager.documentClosed();
                }
            }
            urisUnderLspControl.remove(uri);
            uriToLanguageServerWrapper.remove(new ImmutablePair<>(sanitizeURI(uri), sanitizeURI(projectUri)));
        }
        if (connectedEditors.isEmpty()) {
            stop(true);
        }
    }

    public void removeServerWrapper() {
        stop(true);
        removeWidget();
        IntellijLanguageClient.removeWrapper(this);
    }

    private void connect(String uri) {
        List<Editor> editors = FileUtils.getAllOpenedEditorsForUri(project, uri);

        for (Editor editor : editors) {
            connect(editor);
        }
    }

    /**
     * Is the language server in a state where it can be restartable. Normally language server is
     * restartable if it has timeout or has a startup error.
     */
    public boolean isRestartable() {
        return status == STOPPED && (alreadyShownTimeout || alreadyShownCrash);
    }

    /**
     * Reset language server wrapper state so it can be started again if it was failed earlier.
     */
    public void restart() {
        pool(() -> {
            if (isRestartable()) {
                alreadyShownCrash = false;
                alreadyShownTimeout = false;
            } else {
                stop(true);
            }
            reloadEditors(project);
        });
    }

    private Optional<LSPServerStatusWidget> getWidget() {
        LSPServerStatusWidgetFactory factory = ((LSPServerStatusWidgetFactory) project.getService(StatusBarWidgetsManager.class).findWidgetFactory("LSP"));
        if (factory != null) {
            return Optional.of(factory.getWidget(project));
        } else {
            return Optional.empty();
        }
    }

    /**
     * Returns the extension manager associated with this language server wrapper.
     *
     * @return The result can be null if there is not extension manager defined.
     */
    @Nullable
    public final LSPExtensionManager getExtensionManager() {
        return extManager;
    }
}

