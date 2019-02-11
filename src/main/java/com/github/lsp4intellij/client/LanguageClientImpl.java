package com.github.lsp4intellij.client;

import com.github.lsp4intellij.client.languageserver.wrapper.LanguageServerWrapper;
import com.github.lsp4intellij.utils.ApplicationUtils;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.ui.Messages;
import com.intellij.util.ui.UIUtil;
import org.eclipse.lsp4j.ApplyWorkspaceEditParams;
import org.eclipse.lsp4j.ApplyWorkspaceEditResponse;
import org.eclipse.lsp4j.ConfigurationParams;
import org.eclipse.lsp4j.MessageActionItem;
import org.eclipse.lsp4j.MessageParams;
import org.eclipse.lsp4j.MessageType;
import org.eclipse.lsp4j.PublishDiagnosticsParams;
import org.eclipse.lsp4j.RegistrationParams;
import org.eclipse.lsp4j.ShowMessageRequestParams;
import org.eclipse.lsp4j.UnregistrationParams;
import org.eclipse.lsp4j.WorkspaceFolder;
import org.eclipse.lsp4j.services.LanguageClient;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import javax.swing.*;

public class LanguageClientImpl implements LanguageClient {
    private Logger LOG = Logger.getInstance(LanguageClientImpl.class);
    private LanguageServerWrapper wrapper;

    /**
     * Connects the LanguageClient to the server
     *
     * @param wrapper The Language Server Wrapper
     */
    public void connect(LanguageServerWrapper wrapper) {
        this.wrapper = wrapper;
    }

    public CompletableFuture<ApplyWorkspaceEditResponse> applyEdit(ApplyWorkspaceEditParams params) {
        //            return CompletableFuture.supplyAsync(new ApplyWorkspaceEditResponse(WorkspaceEditHandler.applyEdit(params
        //            .getEdit(), "LSP edits", new LinkedList<>())));
        return null;
    }

    public CompletableFuture<List<Object>> configuration(ConfigurationParams configurationParams) {
        return LanguageClient.super.configuration(configurationParams);
    }

    public CompletableFuture<List<WorkspaceFolder>> workspaceFolders() {
        return LanguageClient.super.workspaceFolders();
    }

    public CompletableFuture<Void> registerCapability(RegistrationParams params) {
        return wrapper.registerCapability(params);
    }

    public CompletableFuture<Void> unregisterCapability(UnregistrationParams params) {
        return wrapper.unregisterCapability(params);
    }

    public void telemetryEvent(Object o) {
        //TODO
    }

    public void publishDiagnostics(PublishDiagnosticsParams publishDiagnosticsParams) {
        //        String uri = FileUtils.sanitizeURI(publishDiagnosticsParams.getUri());
        //        List<Diagnostic> diagnostics = publishDiagnosticsParams.getDiagnostics();
        //        EditorEventManager.forUri(uri).foreach(e => e.diagnostics(diagnostics.asScala));
    }

    public void showMessage(MessageParams messageParams) {
        String title = "Language Server message";
        String message = messageParams.getMessage();
        ApplicationUtils.invokeLater(() -> {
            MessageType msgType = messageParams.getType();
            if (msgType == MessageType.Error) {
                Messages.showErrorDialog(message, title);
            } else if (msgType == MessageType.Warning) {
                Messages.showErrorDialog(message, title);
            } else if (msgType == MessageType.Info) {
                Messages.showErrorDialog(message, title);
            } else if (msgType == MessageType.Log) {
                Messages.showErrorDialog(message, title);
            } else {
                LOG.warn("No message type for " + message);
            }
        });
    }

    public CompletableFuture<MessageActionItem> showMessageRequest(ShowMessageRequestParams showMessageRequestParams) {
        List<MessageActionItem> actions = showMessageRequestParams.getActions();
        String title = "Language Server message";
        String message = showMessageRequestParams.getMessage();
        MessageType msgType = showMessageRequestParams.getType();
        Icon icon;
        if (msgType == MessageType.Error) {
            icon = UIUtil.getErrorIcon();
        } else if (msgType == MessageType.Warning) {
            icon = UIUtil.getWarningIcon();
        } else if (msgType == MessageType.Info) {
            icon = UIUtil.getInformationIcon();
        } else if (msgType == MessageType.Log) {
            icon = UIUtil.getInformationIcon();
        } else {
            icon = null;
            LOG.warn("No message type for " + message);
        }

        List<String> titles = new ArrayList<>();
        for (MessageActionItem item : actions) {
            titles.add(item.getTitle());
        }
        FutureTask<Integer> task = new FutureTask<>(
                () -> Messages.showDialog(message, title, (String[]) titles.toArray(), 0, icon));
        ApplicationManager.getApplication().invokeAndWait(task);

        int exitCode = 0;
        try {
            exitCode = task.get();
        } catch (InterruptedException | ExecutionException e) {
            LOG.warn(e.getMessage());
        }

        return CompletableFuture.completedFuture(new MessageActionItem(actions.get(exitCode).getTitle()));
    }

    public void logMessage(MessageParams messageParams) {
        String message = messageParams.getMessage();
        MessageType msgType = messageParams.getType();

        if (msgType == MessageType.Error) {
            LOG.error(message);
        } else if (msgType == MessageType.Warning) {
            LOG.warn(message);
        } else if (msgType == MessageType.Info) {
            LOG.info(message);
        }
        if (msgType == MessageType.Log) {
            LOG.debug(message);
        } else {
            LOG.warn("Unknown message type for " + message);
        }
    }

    //    public void semanticHighlighting(SemanticHighlightingParams params) {
    //        SemanticHighlightingHandler.handlePush(params);
    //    }
}
