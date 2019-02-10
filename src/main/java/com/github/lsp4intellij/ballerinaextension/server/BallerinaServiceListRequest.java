package com.github.lsp4intellij.ballerinaextension.server;

import org.eclipse.lsp4j.TextDocumentIdentifier;

/**
 * Represents a Ballerina service list reuqest.
 *
 * @since 0.981.2
 */
public class BallerinaServiceListRequest {

    private TextDocumentIdentifier documentIdentifier;

    public TextDocumentIdentifier getDocumentIdentifier() {
        return documentIdentifier;
    }

    public void setDocumentIdentifier(TextDocumentIdentifier documentIdentifier) {
        this.documentIdentifier = documentIdentifier;
    }
}
