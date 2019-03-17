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
package com.github.lsp4intellij.extensions;

import com.github.lsp4intellij.client.languageserver.ServerOptions;
import com.github.lsp4intellij.client.languageserver.requestmanager.DefaultRequestManager;
import com.github.lsp4intellij.client.languageserver.requestmanager.RequestManager;
import com.github.lsp4intellij.client.languageserver.wrapper.LanguageServerWrapper;
import com.github.lsp4intellij.editor.EditorEventManager;
import com.github.lsp4intellij.editor.listeners.EditorMouseMotionListenerImpl;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.event.DocumentListener;
import com.intellij.openapi.editor.event.EditorMouseListener;
import org.eclipse.lsp4j.ServerCapabilities;
import org.eclipse.lsp4j.services.LanguageClient;
import org.eclipse.lsp4j.services.LanguageServer;

public interface LSPExtensionManager {

    <T extends DefaultRequestManager> T getExtendedRequestManagerFor(LanguageServerWrapper wrapper,
            LanguageServer server, LanguageClient client, ServerCapabilities serverCapabilities);

    <T extends EditorEventManager> T getExtendedEditorEventManagerFor(Editor editor, DocumentListener documentListener,
            EditorMouseListener mouseListener, EditorMouseMotionListenerImpl mouseMotionListener,
            RequestManager requestManager, ServerOptions serverOptions, LanguageServerWrapper wrapper);

    Class<? extends LanguageServer> getExtendedServerInterface();

}
