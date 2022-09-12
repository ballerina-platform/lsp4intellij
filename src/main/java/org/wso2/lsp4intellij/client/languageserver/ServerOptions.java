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
package org.wso2.lsp4intellij.client.languageserver;

import org.eclipse.lsp4j.CodeLensOptions;
import org.eclipse.lsp4j.CompletionOptions;
import org.eclipse.lsp4j.DocumentLinkOptions;
import org.eclipse.lsp4j.DocumentOnTypeFormattingOptions;
import org.eclipse.lsp4j.ExecuteCommandOptions;
import org.eclipse.lsp4j.ServerCapabilities;
import org.eclipse.lsp4j.SignatureHelpOptions;
import org.eclipse.lsp4j.TextDocumentSyncKind;

/**
 * Class containing the options of the language server.
 */

public class ServerOptions {

    //Todo - Revisit and implement with accessors
    public TextDocumentSyncKind syncKind;
    public ServerCapabilities capabilities;
    public CompletionOptions completionOptions;
    public SignatureHelpOptions signatureHelpOptions;
    public CodeLensOptions codeLensOptions;
    public DocumentOnTypeFormattingOptions documentOnTypeFormattingOptions;
    public DocumentLinkOptions documentLinkOptions;
    public ExecuteCommandOptions executeCommandOptions;

    public ServerOptions(ServerCapabilities serverCapabilities) {

        this.capabilities = serverCapabilities;

        if (capabilities.getTextDocumentSync().isRight()) {
            this.syncKind = capabilities.getTextDocumentSync().getRight().getChange();
        } else if (capabilities.getTextDocumentSync().isLeft()) {
            this.syncKind = capabilities.getTextDocumentSync().getLeft();
        }

        this.completionOptions = capabilities.getCompletionProvider();
        this.signatureHelpOptions = capabilities.getSignatureHelpProvider();
        this.codeLensOptions = capabilities.getCodeLensProvider();
        this.documentOnTypeFormattingOptions = capabilities.getDocumentOnTypeFormattingProvider();
        this.documentLinkOptions = capabilities.getDocumentLinkProvider();
        this.executeCommandOptions = capabilities.getExecuteCommandProvider();
    }
}
