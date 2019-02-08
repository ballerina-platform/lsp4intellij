package com.github.lsp4intellij.extensions.server;

import org.eclipse.lsp4j.jsonrpc.services.JsonDelegate;
import org.eclipse.lsp4j.services.LanguageServer;

/**
 * Extended Language Server interface which includes ballerina document services.
 */
public interface ExtendedLanguageServer extends LanguageServer {

    @JsonDelegate
    BallerinaDocumentService getBallerinaDocumentService();
}
