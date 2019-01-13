package com.github.lsp4intellij.client.languageserver.wrapper;

import com.github.lsp4intellij.client.languageserver.ServerStatus;
import com.github.lsp4intellij.client.languageserver.requestmanager.RequestManager;
import com.github.lsp4intellij.client.languageserver.serverdefinition.LanguageServerDefinition;
import com.github.lsp4intellij.requests.Timeouts;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import org.eclipse.lsp4j.RegistrationParams;
import org.eclipse.lsp4j.ServerCapabilities;
import org.eclipse.lsp4j.UnregistrationParams;
import org.eclipse.lsp4j.jsonrpc.messages.Message;
import org.eclipse.lsp4j.services.LanguageServer;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.concurrent.CompletableFuture;

/**
 * A LanguageServerWrapper represents a connection to a LanguageServer and manages starting / stopping it as well as  connecting / disconnecting documents to it
 */
public abstract class LanguageServerWrapper {

    /**
     * Tells the wrapper if a request was timed out or not
     *
     * @param timeouts The type of timeout
     * @param success  if it didn't timeout
     */
    public abstract void notifyResult(Timeouts timeouts, boolean success);

    public void notifySuccess(Timeouts timeouts) {
        notifyResult(timeouts, true);
    }

    public void notifyFailure(Timeouts timeouts) {
        notifyResult(timeouts, false);
    }

    abstract Iterable<String> getConnectedFiles();

    /**
     * @return The current status of this server
     */
    public abstract ServerStatus getStatus();

    /**
     * @return the server definition corresponding to this wrapper
     */
    public abstract LanguageServerDefinition getServerDefinition();

    /**
     * @return the project corresponding to this wrapper
     */
    public abstract Project getProject();

    /**
     * Register a capability for the language server
     *
     * @param params The registration params
     * @return
     */
    public abstract CompletableFuture<Void> registerCapability(RegistrationParams params);

    /**
     * Unregister a capability for the language server
     *
     * @param params The unregistration params
     * @return
     */
    public abstract CompletableFuture<Void> unregisterCapability(UnregistrationParams params);

    /**
     * Returns the EditorEventManager for a given uri
     *
     * @param uri the URI as a string
     * @return the EditorEventManager (or null)
     */
    public abstract EditorEventManager getEditorManagerFor(String uri);

    /**
     * @return The request manager for this wrapper
     */
    public abstract RequestManager getRequestManager();

    /**
     * Starts the LanguageServer
     */
    public abstract void start();

    /**
     * @return whether the underlying connection to language languageServer is still active
     */
    public abstract boolean isActive();

    /**
     * Connects an editor to the languageServer
     *
     * @param editor the editor
     */
    public abstract void connect(Editor editor) throws IOException;

    /**
     * Disconnects an editor from the LanguageServer
     *
     * @param editor The editor
     */
    public abstract void disconnect(Editor editor);

    /**
     * Disconnects an editor from the LanguageServer
     *
     * @param path The uri of the editor
     */
    public abstract void disconnect(String path);

    /**
     * Checks if the wrapper is already connected to the document at the given path
     */
    public abstract boolean isConnectedTo(String location);

    /**
     * @return the LanguageServer
     */
    @Nullable
    public abstract LanguageServer getServer();

    /**
     * @return the languageServer capabilities, or null if initialization job didn't complete
     */
    @Nullable
    public abstract ServerCapabilities getServerCapabilities();

    /**
     * @return The language ID that this wrapper is dealing with if defined in the content type mapping for the language languageServer
     */
    @Nullable
    public abstract String getLanguageId(String[] contentTypes);

    public abstract void logMessage(Message message);

    /**
     * Stops the wrapper
     */
    public abstract void stop();

    /**
     * Notifies the wrapper that the server has crashed / stopped unexpectedly
     *
     * @param e The exception returned
     */
    public abstract void crashed(Exception e);

    public abstract void removeWidget();

}
