package com.github.lsp4intellij.ballerinaextension.server;

import org.eclipse.lsp4j.jsonrpc.services.JsonRequest;
import org.eclipse.lsp4j.jsonrpc.services.JsonSegment;

import java.util.concurrent.CompletableFuture;

/**
 * An extension interface for Language server to add features related to ballerina files.
 *
 * @since 0.981.2
 */
@JsonSegment("ballerinaDocument")
public interface BallerinaDocumentService {

    @JsonRequest
    CompletableFuture<BallerinaServiceListResponse> serviceList(BallerinaServiceListRequest request);

}
