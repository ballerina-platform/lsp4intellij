package com.github.lsp4intellij.ballerinaextension.client;

import com.github.lsp4intellij.ballerinaextension.server.BallerinaDocumentService;
import com.github.lsp4intellij.ballerinaextension.server.BallerinaServiceListRequest;
import com.github.lsp4intellij.ballerinaextension.server.BallerinaServiceListResponse;
import com.github.lsp4intellij.ballerinaextension.server.ExtendedLanguageServer;
import com.github.lsp4intellij.client.languageserver.requestmanager.DefaultRequestManager;
import com.github.lsp4intellij.client.languageserver.wrapper.LanguageServerWrapper;
import org.eclipse.lsp4j.ServerCapabilities;
import org.eclipse.lsp4j.services.LanguageClient;
import org.eclipse.lsp4j.services.LanguageServer;

import java.util.concurrent.CompletableFuture;

public class ExtendedRequestManager extends DefaultRequestManager {

    BallerinaDocumentService ballerinaDocumentService;

    public ExtendedRequestManager(LanguageServerWrapper wrapper, LanguageServer server, LanguageClient client,
            ServerCapabilities serverCapabilities) {
        super(wrapper, server, client, serverCapabilities);
        ExtendedLanguageServer extendedServer = (ExtendedLanguageServer) server;
        ballerinaDocumentService = extendedServer.getBallerinaDocumentService();
    }

    public CompletableFuture<BallerinaServiceListResponse> serviceList(BallerinaServiceListRequest request) {
        return ballerinaDocumentService.serviceList(request);
    }
}
