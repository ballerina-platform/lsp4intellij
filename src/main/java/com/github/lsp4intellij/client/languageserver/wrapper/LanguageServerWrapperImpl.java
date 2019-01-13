package com.github.lsp4intellij.client.languageserver.wrapper;

import com.github.lsp4intellij.client.DynamicRegistrationMethods;
import com.github.lsp4intellij.client.LanguageClientImpl;
import com.github.lsp4intellij.client.languageserver.LSPServerStatusWidget;
import com.github.lsp4intellij.client.languageserver.ServerOptions;
import com.github.lsp4intellij.client.languageserver.ServerStatus;
import com.github.lsp4intellij.client.languageserver.requestmanager.RequestManager;
import com.github.lsp4intellij.client.languageserver.requestmanager.SimpleRequestManager;
import com.github.lsp4intellij.client.languageserver.serverdefinition.LanguageServerDefinition;
import com.github.lsp4intellij.editor.EditorEventManager;
import com.github.lsp4intellij.editor.listeners.DocumentListenerImpl;
import com.github.lsp4intellij.editor.listeners.EditorMouseListenerImpl;
import com.github.lsp4intellij.editor.listeners.EditorMouseMotionListenerImpl;
import com.github.lsp4intellij.editor.listeners.SelectionListenerImpl;
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
import org.eclipse.lsp4j.launch.LSPLauncher;
import org.eclipse.lsp4j.services.LanguageServer;
import org.jetbrains.annotations.Nullable;
import sun.plugin2.main.client.PluginMain;

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
    private LanguageServerDefinition serverDefinition;
    private Project project;
    private final HashSet<Editor> toConnect = new HashSet<>();
    private String rootPath;
    private final Map<String, EditorEventManager> connectedEditors = new HashMap();
    private LSPServerStatusWidget statusWidget;
    private Map<String, DynamicRegistrationMethods> registrations = new HashMap();
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
        statusWidget = LSPServerStatusWidget.createWidgetFor(this);
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
    public ServerCapabilities getServerCapabilities() {
        if (this.initializeResult != null)
            this.initializeResult.getCapabilities();
        else {
            try {
                start();
                if (this.initializeFuture != null) {
                    this.initializeFuture
                            .get((capabilitiesAlreadyRequested ? 0 : Timeout.INIT_TIMEOUT()), TimeUnit.MILLISECONDS);
                    notifySuccess(Timeouts.INIT);
                }
            } catch (TimeoutException e) {

                notifyFailure(Timeouts.INIT);
                String msg = "LanguageServer for definition\n " + serverDefinition + "\nnot initialized after "
                        + Timeout.INIT_TIMEOUT() / 1000 + "s\nCheck settings";
                LOG.warn(msg, e);

                ApplicationUtils.invokeLater(() -> {
                    if (!alreadyShownTimeout) {
                        Messages.showErrorDialog(msg, "LSP Error");
                        alreadyShownTimeout = true;
                    }
                });
                stop();
            } catch (Exception e) {
                LOG.warn(e);
                stop();
            }
        }
        this.capabilitiesAlreadyRequested = true;
        return initializeResult != null ? this.initializeResult.getCapabilities() : null;
    }

    public void notifyResult(Timeouts timeout, Boolean success) {
        statusWidget.notifyResult(timeout, success);
    }

    /**
     * Returns the EditorEventManager for a given uri
     *
     * @param uri the URI as a string
     * @return the EditorEventManager (or null)
     */
    public EditorEventManager getEditorManagerFor(String uri) {
        return connectedEditors.get(uri);
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
        return this.launcherFuture != null && !this.launcherFuture.isDone() && !this.launcherFuture.isCancelled()
                && !alreadyShownTimeout && !alreadyShownCrash;
    }

    /**
     * Connects an editor to the languageServer
     *
     * @param editor the editor
     */
    public void connect(Editor editor) throws IOException {

        if (editor == null) {
            LOG.warn("editor is null for " + serverDefinition);
        } else {
            String uri = FileUtils.editorToURIString(editor);
            synchronized (uriToLanguageServerWrapper) {
                uriToLanguageServerWrapper
                        .put(new MutablePair<>(uri, FileUtils.editorToProjectFolderUri(editor)), this);
            }

            if (!this.connectedEditors.containsKey(uri)) {
                start();
                if (this.initializeFuture != null) {
                    ServerCapabilities capabilities = getServerCapabilities();
                    if (capabilities != null) {
                        initializeFuture.thenRun(() -> {
                            if (!this.connectedEditors.containsKey(uri)) {
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
                                        EditorMouseListenerImpl mouseListener = new EditorMouseListenerImpl();
                                        EditorMouseMotionListenerImpl mouseMotionListener = new EditorMouseMotionListenerImpl();
                                        DocumentListenerImpl documentListener = new DocumentListenerImpl();
                                        SelectionListenerImpl selectionListener = new SelectionListenerImpl();
                                        ServerOptions serverOptions = new ServerOptions(syncKind,
                                                capabilities.getCompletionProvider(),
                                                capabilities.getSignatureHelpProvider(),
                                                capabilities.getCodeLensProvider(),
                                                capabilities.getDocumentOnTypeFormattingProvider(),
                                                capabilities.getDocumentLinkProvider(),
                                                capabilities.getExecuteCommandProvider(),
                                                capabilities.getSemanticHighlighting());
                                        EditorEventManager manager = new EditorEventManager(editor, mouseListener,
                                                mouseMotionListener, documentListener, selectionListener,
                                                requestManager, serverOptions, this);
                                        mouseListener.setManager(manager);
                                        mouseMotionListener.setManager(manager);
                                        documentListener.setManager(manager);
                                        selectionListener.setManager(manager);
                                        manager.registerListeners();
                                        synchronized (this.connectedEditors) {
                                            this.connectedEditors.put(uri, manager);
                                        }
                                        manager.documentOpened();
                                        LOG.info("Created a manager for " + uri);
                                        toConnect.remove(editor);
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
                    synchronized (this.toConnect) {
                        toConnect.add(editor);
                    }
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
        synchronized (this.connectedEditors) {
            synchronized (uriToLanguageServerWrapper) {
                this.connectedEditors.remove(uri);
                for (Map.Entry<String, EditorEventManager> ed : this.connectedEditors.entrySet()) {
                    uriToLanguageServerWrapper.remove(new MutablePair<>(uri, FileUtils.projectToUri(project)));
                    ed.removeListeners();
                    ed.documentClosed();
                }
            }
        }
        if (this.connectedEditors.isEmpty()) {
            stop();
        }
    }

    public void stop() {
        if (this.initializeFuture != null) {
            this.initializeFuture.cancel(true);
            this.initializeFuture = null;
        }
        this.initializeResult = null;
        this.capabilitiesAlreadyRequested = false;
        if (this.languageServer != null) {
            try {
                CompletableFuture<Object> shutdown = this.languageServer.shutdown();
                shutdown.get(Timeout.SHUTDOWN_TIMEOUT(), TimeUnit.MILLISECONDS);
                notifySuccess(Timeouts.SHUTDOWN);
            } catch (Exception e) {
                notifyFailure(Timeouts.SHUTDOWN);
                // most likely closed externally
            }
        }
        if (this.launcherFuture != null) {
            this.launcherFuture.cancel(true);
            this.launcherFuture = null;
        }
        if (this.serverDefinition != null) {
            this.serverDefinition.stop(rootPath);
        }

        for (Map.Entry<String, EditorEventManager> ed : this.connectedEditors.entrySet()) {
            disconnect(ed.getKey());
            this.languageServer = null;
            setStatus(STOPPED);
        }
    }

    /**
     * Checks if the wrapper is already connected to the document at the given path
     */
    public boolean isConnectedTo(String location) {
        return this.connectedEditors.containsKey(location);
    }

    /**
     * @return the LanguageServer
     */
    @Nullable
    public LanguageServer getServer() {
        try {
            start();
        } catch (IOException e) {
            LOG.error("Failed to start server:" + e);
        }
        if (initializeFuture != null && !this.initializeFuture.isDone()) {
            this.initializeFuture.join();
        }
        return this.languageServer;
    }

    /**
     * Starts the LanguageServer
     */
    public void start() throws IOException {
        if (status == STOPPED && !alreadyShownCrash && !alreadyShownTimeout) {
            setStatus(STARTING);
            try {
                Pair<InputStream, OutputStream> streams = serverDefinition.start(rootPath);
                InputStream inputStream = streams.getKey();
                OutputStream outputStream = streams.getValue();
                client = serverDefinition.createLanguageClient();
                InitializeParams initParams = new InitializeParams();
                initParams.setRootUri(FileUtils.pathToUri(rootPath));
                Launcher<LanguageServer> launcher = LSPLauncher.createClientLauncher(client, inputStream, outputStream);
                this.languageServer = launcher.getRemoteProxy();
                client.connect(languageServer, this);
                this.launcherFuture = launcher.startListening();
                //TODO update capabilities when implemented
                WorkspaceClientCapabilities workspaceClientCapabilities = new WorkspaceClientCapabilities();
                workspaceClientCapabilities.setApplyEdit(true);
                workspaceClientCapabilities.setDidChangeWatchedFiles(new DidChangeWatchedFilesCapabilities());
                workspaceClientCapabilities.setExecuteCommand(new ExecuteCommandCapabilities());
                workspaceClientCapabilities.setWorkspaceEdit(new WorkspaceEditCapabilities(true));
                workspaceClientCapabilities.setSymbol(new SymbolCapabilities());
                workspaceClientCapabilities.setWorkspaceFolders(false);
                workspaceClientCapabilities.setConfiguration(false);

                TextDocumentClientCapabilities textDocumentClientCapabilities = new TextDocumentClientCapabilities();
                textDocumentClientCapabilities.setCodeAction(new CodeActionCapabilities());
                textDocumentClientCapabilities
                        .setCompletion(new CompletionCapabilities(new CompletionItemCapabilities(false)));
                textDocumentClientCapabilities.setDefinition(new DefinitionCapabilities());
                textDocumentClientCapabilities.setDocumentHighlight(new DocumentHighlightCapabilities());
                textDocumentClientCapabilities.setFormatting(new FormattingCapabilities());
                textDocumentClientCapabilities.setHover(new HoverCapabilities());
                textDocumentClientCapabilities.setOnTypeFormatting(new OnTypeFormattingCapabilities());
                textDocumentClientCapabilities.setRangeFormatting(new RangeFormattingCapabilities());
                textDocumentClientCapabilities.setReferences(new ReferencesCapabilities());
                textDocumentClientCapabilities.setRename(new RenameCapabilities());
                textDocumentClientCapabilities
                        .setSemanticHighlightingCapabilities(new SemanticHighlightingCapabilities(false));
                textDocumentClientCapabilities.setSignatureHelp(new SignatureHelpCapabilities());
                textDocumentClientCapabilities.setSynchronization(new SynchronizationCapabilities(true, true, true));
                initParams.setCapabilities(
                        new ClientCapabilities(workspaceClientCapabilities, textDocumentClientCapabilities, null));
                initParams.setInitializationOptions(
                        this.serverDefinition.getInitializationOptions(URI.create(initParams.getRootUri())));
                initializeFuture = languageServer.initialize(initParams).thenApply(res -> {
                    initializeResult = res;
                    LOG.info("Got initializeResult for " + serverDefinition + " ; " + rootPath);
                    requestManager = new SimpleRequestManager(this, languageServer, client, res.getCapabilities());
                    setStatus(STARTED);
                    return res;
                });
                initializeStartTime = System.currentTimeMillis();
            } catch (LSPException | IOException e) {
                LOG.warn(e);
                ApplicationUtils.invokeLater(() -> Messages
                        .showErrorDialog("Can't start server, please check " + "settings\n" + e.getMessage(),
                                "LSP Error"));
                stop();
                removeServerWrapper();
            }
        }
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
        return CompletableFuture.runAsync(() -> {
            params.getRegistrations().forEach(r -> {
                String id = r.getId();
                DynamicRegistrationMethods method = DynamicRegistrationMethods.forName(r.getMethod());
                Object options = r.getRegisterOptions();
                registrations.put(id, method);
            });
        });
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
            stop();
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
        stop();
        removeWidget();
        PluginMain.removeWrapper(this);
    }

    private void connect(String uri) {
        FileEditor[] fileEditors = FileEditorManager.getInstance(project).getAllEditors(FileUtils.URIToVFS(uri));
        List<Editor> editors = new ArrayList<>();

        for (FileEditor ed : fileEditors) {
            if (ed instanceof TextEditor) {
                editors.add(((TextEditor) ed).getEditor());
            }

        }
        if (!editors.isEmpty()) {
            try {
                connect(editors.get(0));
            } catch (IOException e) {
                LOG.warn(e);
            }
        }
    }
}

