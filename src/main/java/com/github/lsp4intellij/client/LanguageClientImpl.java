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
package com.github.lsp4intellij.client;

import com.github.lsp4intellij.editor.EditorEventManager;
import com.github.lsp4intellij.editor.EditorEventManagerBase;
import com.github.lsp4intellij.requests.WorkspaceEditHandler;
import com.github.lsp4intellij.utils.ApplicationUtils;
import com.github.lsp4intellij.utils.FileUtils;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.ui.Messages;
import com.intellij.util.ui.UIUtil;
import org.eclipse.lsp4j.ApplyWorkspaceEditParams;
import org.eclipse.lsp4j.ApplyWorkspaceEditResponse;
import org.eclipse.lsp4j.ConfigurationParams;
import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.MessageActionItem;
import org.eclipse.lsp4j.MessageParams;
import org.eclipse.lsp4j.MessageType;
import org.eclipse.lsp4j.PublishDiagnosticsParams;
import org.eclipse.lsp4j.RegistrationParams;
import org.eclipse.lsp4j.ShowMessageRequestParams;
import org.eclipse.lsp4j.Unregistration;
import org.eclipse.lsp4j.UnregistrationParams;
import org.eclipse.lsp4j.WorkspaceFolder;
import org.eclipse.lsp4j.services.LanguageClient;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import javax.swing.*;

public class LanguageClientImpl implements LanguageClient {
    private Logger LOG = Logger.getInstance(LanguageClientImpl.class);
    private Map<String, DynamicRegistrationMethods> registrations = new ConcurrentHashMap<>();

    @Override
    public CompletableFuture<ApplyWorkspaceEditResponse> applyEdit(ApplyWorkspaceEditParams params) {
        boolean response = WorkspaceEditHandler.applyEdit(params.getEdit(), "LSP edits");
        return CompletableFuture.supplyAsync(() -> new ApplyWorkspaceEditResponse(response));
    }

    @Override
    public CompletableFuture<List<Object>> configuration(ConfigurationParams configurationParams) {
        return LanguageClient.super.configuration(configurationParams);
    }

    @Override
    public CompletableFuture<List<WorkspaceFolder>> workspaceFolders() {
        return LanguageClient.super.workspaceFolders();
    }

    @Override
    public CompletableFuture<Void> registerCapability(RegistrationParams params) {
        return CompletableFuture.runAsync(() -> params.getRegistrations().forEach(r -> {
            String id = r.getId();
            DynamicRegistrationMethods method = DynamicRegistrationMethods.forName(r.getMethod());
            Object options = r.getRegisterOptions();
            registrations.put(id, method);
        }));
    }

    @Override
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

    @Override
    public void telemetryEvent(Object o) {
        //TODO
    }

    @Override
    public void publishDiagnostics(PublishDiagnosticsParams publishDiagnosticsParams) {
        String uri = FileUtils.sanitizeURI(publishDiagnosticsParams.getUri());
        List<Diagnostic> diagnostics = publishDiagnosticsParams.getDiagnostics();
        EditorEventManager manager = EditorEventManagerBase.forUri(uri);
        if (manager != null) {
            manager.diagnostics(diagnostics);
        }
    }

    @Override
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

    @Override
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

    @Override
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
