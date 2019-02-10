package com.github.lsp4intellij.ballerinaextension.editoreventmanager;/*
 *  Copyright (c) 2019, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

import com.github.lsp4intellij.ballerinaextension.client.ExtendedRequestManager;
import com.github.lsp4intellij.ballerinaextension.server.BallerinaServiceListRequest;
import com.github.lsp4intellij.ballerinaextension.server.BallerinaServiceListResponse;
import com.github.lsp4intellij.client.languageserver.ServerOptions;
import com.github.lsp4intellij.client.languageserver.requestmanager.RequestManager;
import com.github.lsp4intellij.client.languageserver.wrapper.LanguageServerWrapperImpl;
import com.github.lsp4intellij.editor.EditorEventManager;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.event.DocumentListener;
import org.eclipse.lsp4j.Position;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

public class ExtendedEditorEventManager extends EditorEventManager {

    public ExtendedEditorEventManager(Editor editor, DocumentListener documentListener, RequestManager requestManager,
            ServerOptions serverOptions, LanguageServerWrapperImpl wrapper) {
        super(editor, documentListener, requestManager, serverOptions, wrapper);
    }

    @Override
    public Iterable<? extends LookupElement> completion(Position pos) {
        BallerinaServiceListRequest serviceRequest = new BallerinaServiceListRequest();
        serviceRequest.setDocumentIdentifier(getIdentifier());
        ExtendedRequestManager requestManager = (ExtendedRequestManager) getRequestManager();
        CompletableFuture<BallerinaServiceListResponse> responseFuture = requestManager.serviceList(serviceRequest);
        BallerinaServiceListResponse response = null;
        try {
            response = responseFuture.get();
        } catch (InterruptedException | ExecutionException e) {
            e.printStackTrace();
        }

        String[] services = response.getServices();
        return super.completion(pos);
    }
}
