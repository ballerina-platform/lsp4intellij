package com.github.lsp4intellij.client.languageserver.wrapper;

import com.github.lsp4intellij.PluginMain;
import com.github.lsp4intellij.client.DynamicRegistrationMethods;
import com.github.lsp4intellij.client.LanguageClientImpl;
import com.github.lsp4intellij.client.languageserver.LSPServerStatusWidget;
import com.github.lsp4intellij.client.languageserver.ServerOptions;
import com.github.lsp4intellij.client.languageserver.ServerStatus;
import com.github.lsp4intellij.client.languageserver.requestmanager.DefaultRequestManager;
import com.github.lsp4intellij.client.languageserver.requestmanager.RequestManager;
import com.github.lsp4intellij.client.languageserver.serverdefinition.LanguageServerDefinition;
import com.github.lsp4intellij.editor.EditorEventManager;
import com.github.lsp4intellij.editor.listeners.DocumentListenerImpl;
import com.github.lsp4intellij.extensions.LSPExtensionManager;
import com.github.lsp4intellij.requests.Timeout;
import com.github.lsp4intellij.requests.Timeouts;
import com.github.lsp4intellij.utils.ApplicationUtils;
import com.github.lsp4intellij.utils.FileUtils;
import com.github.lsp4intellij.utils.LSPException;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.TextEditor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.MutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.eclipse.lsp4j.ClientCapabilities;
import org.eclipse.lsp4j.CodeActionCapabilities;
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
import org.eclipse.lsp4j.OnTypeFormattingCapabilities;
import org.eclipse.lsp4j.RangeFormattingCapabilities;
import org.eclipse.lsp4j.ReferencesCapabilities;
import org.eclipse.lsp4j.RegistrationParams;
import org.eclipse.lsp4j.RenameCapabilities;
import org.eclipse.lsp4j.SemanticHighlightingCapabilities;
import org.eclipse.lsp4j.ServerCapabilities;
import org.eclipse.lsp4j.SignatureHelpCapabilities;
import org.eclipse.lsp4j.SymbolCapabilities;
import org.eclipse.lsp4j.SynchronizationCapabilities;
import org.eclipse.lsp4j.TextDocumentClientCapabilities;
import org.eclipse.lsp4j.TextDocumentSyncKind;
import org.eclipse.lsp4j.TextDocumentSyncOptions;
import org.eclipse.lsp4j.Unregistration;
import org.eclipse.lsp4j.UnregistrationParams;
import org.eclipse.lsp4j.WorkspaceClientCapabilities;
import org.eclipse.lsp4j.WorkspaceEditCapabilities;
import org.eclipse.lsp4j.jsonrpc.Launcher;
import org.eclipse.lsp4j.jsonrpc.ResponseErrorException;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.eclipse.lsp4j.jsonrpc.messages.Message;
import org.eclipse.lsp4j.jsonrpc.messages.ResponseErrorCode;
import org.eclipse.lsp4j.jsonrpc.messages.ResponseMessage;
import org.eclipse.lsp4j.services.LanguageServer;
import org.jetbrains.annotations.Nullable;

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
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static com.github.lsp4intellij.client.languageserver.ServerStatus.STARTED;
import static com.github.lsp4intellij.client.languageserver.ServerStatus.STARTING;
import static com.github.lsp4intellij.client.languageserver.ServerStatus.STOPPED;

/**
 * The implementation of a LanguageServerWrapper (specific to a serverDefinition and a project)
 */
public class LanguageServerWrapperImpl extends LanguageServerWrapper {

    private Logger LOG = Logger.getInstance(LanguageServerWrapperImpl.class);

    private static final Map<Pair<String, String>, LanguageServerWrapper> uriToLanguageServerWrapper = new ConcurrentHashMap<>();
    private static LSPExtensionManager extManager = null;
    public LanguageServerDefinition serverDefinition;
    private Project project;
    private final HashSet<Editor> toConnect = new HashSet<>();
    private String rootPath;
    private final Map<String, EditorEventManager> connectedEditors = new ConcurrentHashMap<>();
    private LSPServerStatusWidget statusWidget;
    private Map<String, DynamicRegistrationMethods> registrations = new ConcurrentHashMap<>();
    private int crashCount = 0;
    volatile private boolean alreadyShownTimeout = false;
    volatile private boolean alreadyShownCrash = false;
    volatile private ServerStatus status = STOPPED;
    private LanguageServer languageServer;
    private LanguageClientImpl client;
    private RequestManager requestManager;
    private InitializeResult initializeResult;
    private Future<?> launcherFuture;
    private CompletableFuture<InitializeResult> initializeFuture;
    private boolean capabilitiesAlreadyRequested = false;
    private long initializeStartTime = 0L;

    public LanguageServerWrapperImpl(LanguageServerDefinition serverDefinition, Project project) {
        this.serverDefinition = serverDefinition;
        this.project = project;
        this.rootPath = project.getBasePath();
        this.statusWidget = LSPServerStatusWidget.createWidgetFor(this);
    }

    public LanguageServerWrapperImpl(LanguageServerDefinition serverDefinition, Project project,
            LSPExtensionManager extManager) {
        this.serverDefinition = serverDefinition;
        this.project = project;
        this.rootPath = project.getBasePath();
        this.statusWidget = LSPServerStatusWidget.createWidgetFor(this);
        this.extManager = extManager;
    }

    /**
     * @param uri A file uri
     * @return The wrapper for the given uri, or None
     */
    public static LanguageServerWrapper forUri(String uri, Project project) {
        return uriToLanguageServerWrapper.get(new MutablePair<>(uri, FileUtils.projectToUri(project)));
    }

    /**
     * @param editor An editor
     * @return The wrapper for the given editor, or None
     */
    public static LanguageServerWrapper forEditor(Editor editor) {
        return uriToLanguageServerWrapper.get(new MutablePair<>(FileUtils.editorToURIString(editor),
                FileUtils.editorToProjectFolderUri(editor)));
    }

    public LanguageServerDefinition getServerDefinition() {
        return serverDefinition;
    }

    /**
     * @return if the server supports willSaveWaitUntil
     */
    public boolean isWillSaveWaitUntil() {
        Either<TextDocumentSyncKind, TextDocumentSyncOptions> capabilities =
                getServerCapabilities() != null ? getServerCapabilities().getTextDocumentSync() : null;
        if (capabilities == null) {
            return false;
        }
        if (capabilities.isLeft()) {
            return false;
        } else {
            return capabilities.getRight().getWillSaveWaitUntil();
        }
    }

    /**
     * Warning: this is a long running operation
     *
     * @return the languageServer capabilities, or null if initialization job didn't complete
     */
    @Nullable
    @Override
    public ServerCapabilities getServerCapabilities() {
        if (initializeResult != null)
            return initializeResult.getCapabilities();
        else {
            try {
                start();
                if (initializeFuture != null) {
                    initializeFuture
                            .get((capabilitiesAlreadyRequested ? 0 : Timeout.INIT_TIMEOUT), TimeUnit.MILLISECONDS);
                    notifySuccess(Timeouts.INIT);
                }
            } catch (TimeoutException e) {
                notifyFailure(Timeouts.INIT);
                String msg = "LanguageServer for definition\n " + serverDefinition + "\nnot initialized after "
                        + Timeout.INIT_TIMEOUT / 1000 + "s\nCheck settings";
                LOG.warn(msg, e);
                ApplicationUtils.invokeLater(() -> {
                    if (!alreadyShownTimeout) {
                        Messages.showErrorDialog(msg, "LSP Error");
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

    @Override
    public void notifyResult(Timeouts timeouts, boolean success) {
        statusWidget.notifyResult(timeouts, success);
    }

    /**
     * Returns the EditorEventManager for a given uri
     *
     * @param uri the URI as a string
     * @return the EditorEventManager (or null)
     */
    @Override
    public EditorEventManager getEditorManagerFor(String uri) {
        return connectedEditors.get(uri);
    }

    /**
     * @return The request manager for this wrapper
     */
    @Override
    public RequestManager getRequestManager() {
        return requestManager;
    }

    /**
     * @return whether the underlying connection to language languageServer is still active
     */
    @Override
    public boolean isActive() {
        return launcherFuture != null && !launcherFuture.isDone() && !launcherFuture.isCancelled()
                && !alreadyShownTimeout && !alreadyShownCrash;
    }

    /**
     * Connects an editor to the languageServer
     *
     * @param editor the editor
     */
    @Override
    public void connect(Editor editor) {
        if (editor == null) {
            LOG.warn("editor is null for " + serverDefinition);
        }
        String uri = FileUtils.editorToURIString(editor);
        uriToLanguageServerWrapper.put(new MutablePair<>(uri, FileUtils.editorToProjectFolderUri(editor)), this);
        if (!connectedEditors.containsKey(uri)) {
            start();
            if (initializeFuture != null) {
                ServerCapabilities capabilities = getServerCapabilities();
                if (capabilities != null) {
                    initializeFuture.thenRun(() -> {
                        if (!connectedEditors.containsKey(uri)) {
                            try {
                                Either<TextDocumentSyncKind, TextDocumentSyncOptions> syncOptions = capabilities
                                        .getTextDocumentSync();
                                TextDocumentSyncKind syncKind = null;
                                if (syncOptions != null) {
                                    if (syncOptions.isRight()) {
                                        syncKind = syncOptions.getRight().getChange();
                                    } else if (syncOptions.isLeft()) {
                                        syncKind = syncOptions.getLeft();
                                    }

                                    //Todo - Implement
                                    //  EditorMouseListenerImpl mouseListener = new EditorMouseListenerImpl();
                                    //  EditorMouseMotionListenerImpl mouseMotionListener = new EditorMouseMotionListenerImpl();
                                    //  SelectionListenerImpl selectionListener = new SelectionListenerImpl();

                                    DocumentListenerImpl documentListener = new DocumentListenerImpl();

                                    ServerOptions serverOptions = new ServerOptions(syncKind,
                                            capabilities.getCompletionProvider(),
                                            capabilities.getSignatureHelpProvider(), capabilities.getCodeLensProvider(),
                                            capabilities.getDocumentOnTypeFormattingProvider(),
                                            capabilities.getDocumentLinkProvider(),
                                            capabilities.getExecuteCommandProvider(),
                                            capabilities.getSemanticHighlighting());
                                    EditorEventManager manager;
                                    if (extManager != null) {
                                        manager = extManager.getExtendedEditorEventManagerFor(editor, documentListener,
                                                requestManager, serverOptions, this);
                                    } else {
                                        manager = new EditorEventManager(editor, documentListener, requestManager,
                                                serverOptions, this);
                                    }
                                    //                                        mouseListener.setManager(manager);
                                    //                                        mouseMotionListener.setManager(manager);
                                    //                                        selectionListener.setManager(manager);
                                    documentListener.setManager(manager);
                                    manager.registerListeners();
                                    connectedEditors.put(uri, manager);
                                    manager.documentOpened();
                                    LOG.info("Created a manager for " + uri);
                                    synchronized (toConnect) {
                                        toConnect.remove(editor);
                                    }
                                    for (Editor ed : toConnect) {
                                        connect(ed);
                                    }
                                }
                            } catch (Exception e) {
                                LOG.error(e);
                            }
                        }
                    });
                } else {
                    LOG.warn("Capabilities are null for " + serverDefinition);
                }
            } else {
                synchronized (toConnect) {
                    toConnect.add(editor);
                }
            }
        }
    }

    /**
     * Disconnects an editor from the LanguageServer
     *
     * @param uri The uri of the editor
     */
    public void disconnect(String uri) {
        connectedEditors.remove(uri);
        connectedEditors.forEach((key, value) -> {
            uriToLanguageServerWrapper.remove(new ImmutablePair<>(uri, FileUtils.projectToUri(project)));
            value.removeListeners();
            value.documentClosed();
        });

        if (connectedEditors.isEmpty()) {
            stop(true);
        }
    }

    /*
     * The shutdown request is sent from the client to the server. It asks the server to shut down, but to not exit \
     * (otherwise the response might not be delivered correctly to the client).
     * Only if the exit flag is true, particular server instance will exit.
     */
    public void stop(boolean exit) {
        try {
            if (initializeFuture != null) {
                initializeFuture.cancel(true);
                initializeFuture = null;
            }
            initializeResult = null;
            capabilitiesAlreadyRequested = false;
            if (languageServer != null) {
                CompletableFuture<Object> shutdown = languageServer.shutdown();
                shutdown.get(Timeout.SHUTDOWN_TIMEOUT, TimeUnit.MILLISECONDS);
                notifySuccess(Timeouts.SHUTDOWN);
                if (exit) {
                    languageServer.exit();
                }
            }
        } catch (Exception e) {
            // most likely closed externally.
            notifyFailure(Timeouts.SHUTDOWN);
        } finally {
            if (launcherFuture != null) {
                launcherFuture.cancel(true);
                launcherFuture = null;
            }
            if (serverDefinition != null) {
                serverDefinition.stop(rootPath);
            }
            for (Map.Entry<String, EditorEventManager> ed : connectedEditors.entrySet()) {
                disconnect(ed.getKey());
            }
            languageServer = null;
            setStatus(STOPPED);
        }
    }

    /**
     * Checks if the wrapper is already connected to the document at the given path
     */
    public boolean isConnectedTo(String location) {
        return connectedEditors.containsKey(location);
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
                Pair<InputStream, OutputStream> streams = serverDefinition.start(rootPath);
                InputStream inputStream = streams.getKey();
                OutputStream outputStream = streams.getValue();
                client = serverDefinition.createLanguageClient();
                InitializeParams initParams = getInitParams();
                if (extManager != null) {
                    Class<? extends LanguageServer> remoteServerInterFace = extManager.getExtendedServerInterface();
                    Launcher<? extends LanguageServer> launcher = Launcher
                            .createLauncher(client, remoteServerInterFace, inputStream, outputStream);
                    languageServer = launcher.getRemoteProxy();
                    launcherFuture = launcher.startListening();
                } else {
                    Launcher<LanguageServer> launcher = Launcher
                            .createLauncher(client, LanguageServer.class, inputStream, outputStream);
                    languageServer = launcher.getRemoteProxy();
                    launcherFuture = launcher.startListening();
                }
                client.connect(this);

                initializeFuture = languageServer.initialize(initParams).thenApply(res -> {
                    initializeResult = res;
                    LOG.info("Got initializeResult for " + serverDefinition + " ; " + rootPath);
                    if (extManager != null) {
                        requestManager = extManager
                                .getExtendedRequestManagerFor(this, languageServer, client, res.getCapabilities());
                    } else {
                        requestManager = new DefaultRequestManager(this, languageServer, client, res.getCapabilities());
                    }
                    setStatus(STARTED);
                    return res;
                });
                initializeStartTime = System.currentTimeMillis();
            } catch (LSPException | IOException e) {
                LOG.warn(e);
                ApplicationUtils.invokeLater(() -> Messages
                        .showErrorDialog("Can't start server, please check " + "settings\n" + e.getMessage(),
                                "LSP Error"));
                removeServerWrapper();
            }
        }
    }

    private InitializeParams getInitParams() {
        InitializeParams initParams = new InitializeParams();
        initParams.setRootUri(FileUtils.pathToUri(rootPath));
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
        textDocumentClientCapabilities.setCompletion(new CompletionCapabilities(new CompletionItemCapabilities(false)));
        textDocumentClientCapabilities.setDefinition(new DefinitionCapabilities());
        textDocumentClientCapabilities.setDocumentHighlight(new DocumentHighlightCapabilities());
        textDocumentClientCapabilities.setFormatting(new FormattingCapabilities());
        textDocumentClientCapabilities.setHover(new HoverCapabilities());
        textDocumentClientCapabilities.setOnTypeFormatting(new OnTypeFormattingCapabilities());
        textDocumentClientCapabilities.setRangeFormatting(new RangeFormattingCapabilities());
        textDocumentClientCapabilities.setReferences(new ReferencesCapabilities());
        textDocumentClientCapabilities.setRename(new RenameCapabilities());
        textDocumentClientCapabilities.setSemanticHighlightingCapabilities(new SemanticHighlightingCapabilities(false));
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

    public CompletableFuture<Void> registerCapability(RegistrationParams params) {
        return CompletableFuture.runAsync(() -> params.getRegistrations().forEach(r -> {
            String id = r.getId();
            DynamicRegistrationMethods method = DynamicRegistrationMethods.forName(r.getMethod());
            Object options = r.getRegisterOptions();
            registrations.put(id, method);
        }));
    }

    public CompletableFuture<Void> unregisterCapability(UnregistrationParams params) {
        return CompletableFuture.runAsync(() -> params.getUnregisterations().forEach((Unregistration r) -> {
            String id = r.getId();
            DynamicRegistrationMethods method = DynamicRegistrationMethods.forName(r.getMethod());
            if (registrations.containsKey(id)) {
                registrations.remove(id);
            } else {
                Map<DynamicRegistrationMethods, String> inverted = new HashMap<>();
                for (Map.Entry<String, DynamicRegistrationMethods> entry : registrations.entrySet()) {
                    inverted.put(entry.getValue(), entry.getKey());
                }
                if (inverted.containsKey(method)) {
                    registrations.remove(inverted.get(method));
                }
            }
        }));
    }

    public Project getProject() {
        return project;
    }

    public ServerStatus getStatus() {
        return status;
    }

    private void setStatus(ServerStatus status) {
        this.status = status;
        statusWidget.setStatus(status);
    }

    public void crashed(Exception e) {
        crashCount += 1;
        if (crashCount < 2) {
            final Set<String> editors = connectedEditors.keySet();
            stop(false);
            for (String uri : editors) {
                connect(uri);
            }
        } else {
            removeServerWrapper();
            if (!alreadyShownCrash) {
                ApplicationUtils.invokeLater(() -> {
                    if (!alreadyShownCrash) {
                        Messages.showErrorDialog(
                                "LanguageServer for definition " + serverDefinition + ", project " + project
                                        + " keeps crashing due to \n" + e.getMessage() + "\nCheck settings.",
                                "LSP Error");
                        alreadyShownCrash = true;
                    }
                });
            }
        }
    }

    public List<String> getConnectedFiles() {
        List<String> connected = new ArrayList<>();
        connectedEditors.keySet().forEach(s -> {
            try {
                connected.add(new URI(FileUtils.sanitizeURI(s)).toString());
            } catch (URISyntaxException e) {
                LOG.warn(e);
            }
        });
        return connected;
    }

    public void removeWidget() {
        statusWidget.dispose();
    }

    /**
     * Disconnects an editor from the LanguageServer
     *
     * @param editor The editor
     */
    public void disconnect(Editor editor) {
        disconnect(FileUtils.editorToURIString(editor));
    }

    private void removeServerWrapper() {
        stop(false);
        removeWidget();
        PluginMain.removeWrapper(this);
    }

    private void connect(String uri) {
        FileEditor[] fileEditors = FileEditorManager.getInstance(project)
                .getAllEditors(Objects.requireNonNull(FileUtils.URIToVFS(uri)));

        List<Editor> editors = new ArrayList<>();
        for (FileEditor ed : fileEditors) {
            if (ed instanceof TextEditor) {
                editors.add(((TextEditor) ed).getEditor());
            }
        }
        if (!editors.isEmpty()) {
            connect(editors.get(0));
        }
    }
}

