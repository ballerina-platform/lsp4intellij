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
package org.wso2.lsp4intellij.extensions;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.event.DocumentListener;
import com.intellij.openapi.editor.event.EditorMouseListener;
import com.intellij.psi.PsiFile;
import org.eclipse.lsp4j.ServerCapabilities;
import org.eclipse.lsp4j.services.LanguageClient;
import org.eclipse.lsp4j.services.LanguageServer;
import org.jetbrains.annotations.NotNull;
import org.wso2.lsp4intellij.client.ClientContext;
import org.wso2.lsp4intellij.client.languageserver.ServerOptions;
import org.wso2.lsp4intellij.client.languageserver.requestmanager.DefaultRequestManager;
import org.wso2.lsp4intellij.client.languageserver.requestmanager.RequestManager;
import org.wso2.lsp4intellij.client.languageserver.wrapper.LanguageServerWrapper;
import org.wso2.lsp4intellij.contributors.icon.LSPDefaultIconProvider;
import org.wso2.lsp4intellij.contributors.icon.LSPIconProvider;
import org.wso2.lsp4intellij.contributors.label.LSPDefaultLabelProvider;
import org.wso2.lsp4intellij.contributors.label.LSPLabelProvider;
import org.wso2.lsp4intellij.editor.EditorEventManager;
import org.wso2.lsp4intellij.listeners.EditorMouseMotionListenerImpl;

public interface LSPExtensionManager {

    <T extends DefaultRequestManager> T getExtendedRequestManagerFor(LanguageServerWrapper wrapper,
            LanguageServer server, LanguageClient client, ServerCapabilities serverCapabilities);

    <T extends EditorEventManager> T getExtendedEditorEventManagerFor(Editor editor, DocumentListener documentListener,
            EditorMouseListener mouseListener, EditorMouseMotionListenerImpl mouseMotionListener,
            RequestManager requestManager, ServerOptions serverOptions, LanguageServerWrapper wrapper);

    Class<? extends LanguageServer> getExtendedServerInterface();

    /**
     * Extension implementor must provide a {@link LanguageClient} implementation which this library
     * will use. LanuageClient is extended in situation where you have custom client notifications
     * which are not part of the LS protocol. As a starting point the implementor can extend the
     * {@link org.wso2.lsp4intellij.client.DefaultLanguageClient}.
     *
     * @param context The client context which can be used by the LanguageClient implementation.
     */
    LanguageClient getExtendedClientFor(ClientContext context);

    /**
     * The icon provider for the Language Server. Override and implement your own or extend the
     * {@link LSPDefaultIconProvider} to customize the default icons.
     *
     */
    @NotNull
    default LSPIconProvider getIconProvider() {
        return new LSPDefaultIconProvider();
    }

    /**
     * Some language servers might only need to start for files which has a specific content. This method can be used
     * in such situation to control whether the file must be connected to a language server which is registered for the
     * extension of this file.
     *
     * <b>Note:</b> By default this method returns <code>true</code>
     *
     * @param file PsiFile which is about to connect to a language server.
     * @return <code>true</code> if the file is supported.
     */
    default boolean isFileContentSupported(@NotNull PsiFile file) {
        return true;
    }

    /**
     * The label provider for the Language Server. Implement and override default behavior
     * if it needs to be customize.
     */
    @NotNull
    default LSPLabelProvider getLabelProvider() {
        return new LSPDefaultLabelProvider();
    }

}
