/*
 * Copyright (c) 2026, WSO2 LLC. (http://www.wso2.org) All Rights Reserved.
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
package org.wso2.lsp4intellij.logging;

import org.eclipse.lsp4j.MessageType;
import org.eclipse.lsp4j.jsonrpc.MessageConsumer;
import org.eclipse.lsp4j.jsonrpc.json.StreamMessageConsumer;
import org.eclipse.lsp4j.jsonrpc.messages.Message;
import org.eclipse.lsp4j.jsonrpc.messages.NotificationMessage;
import org.eclipse.lsp4j.jsonrpc.messages.RequestMessage;
import org.eclipse.lsp4j.jsonrpc.messages.ResponseMessage;
import org.jetbrains.annotations.NotNull;
import org.wso2.lsp4intellij.client.languageserver.wrapper.LanguageServerWrapper;

import java.util.function.Supplier;

/**
 * Decorates one of the two {@link MessageConsumer}s lsp4j's {@code Launcher} builds while wiring up a
 * connection, turning every message that passes through into an {@link LspLogEntry} before forwarding
 * it on unchanged.
 *
 * <p>The direction is fixed at construction from the runtime type of the delegate: lsp4j wraps a
 * {@link StreamMessageConsumer} for the stream that writes to the server (client to server,
 * {@link LspLogSource#TRACE_OUT}) and a {@code RemoteEndpoint} for the one that dispatches messages
 * read from the server (server to client, {@link LspLogSource#TRACE_IN}); anything else is treated as
 * the latter.
 *
 * <p>{@code levelSupplier} is read once per message and nothing else happens while it reports
 * {@link LspTraceLevel#OFF}: no entry is built, no JSON is serialized, matching {@code MessageHandler}'s
 * own {@code isRunning} supplier rather than reaching into a service.
 */
public final class TracingMessageConsumer implements MessageConsumer {

    private final MessageConsumer delegate;
    private final LanguageServerWrapper wrapper;
    private final Supplier<LspTraceLevel> levelSupplier;
    private final LspLogSource direction;

    public TracingMessageConsumer(@NotNull MessageConsumer delegate, @NotNull LanguageServerWrapper wrapper,
            @NotNull Supplier<LspTraceLevel> levelSupplier) {
        this.delegate = delegate;
        this.wrapper = wrapper;
        this.levelSupplier = levelSupplier;
        this.direction = delegate instanceof StreamMessageConsumer ? LspLogSource.TRACE_OUT : LspLogSource.TRACE_IN;
    }

    @Override
    public void consume(Message message) {
        LspTraceLevel level = levelSupplier.get();
        if (level != LspTraceLevel.OFF) {
            record(message, level);
        }
        delegate.consume(message);
    }

    private void record(Message message, LspTraceLevel level) {
        LspLogStore store = LspLogStore.getInstanceIfCreated(wrapper.getProject());
        if (store == null) {
            return;
        }
        String detail = level == LspTraceLevel.VERBOSE ? serialize(message) : null;
        store.append(wrapper, direction, MessageType.Log, summarize(message), detail);
    }

    private static String summarize(Message message) {
        if (message instanceof RequestMessage request) {
            return request.getMethod() + " (" + request.getId() + ")";
        }
        if (message instanceof ResponseMessage response) {
            return "response (" + response.getId() + ")";
        }
        if (message instanceof NotificationMessage notification) {
            return notification.getMethod();
        }
        return message.toString();
    }

    /**
     * Returns the full payload for the detail column. {@code Message.jsonHandler()} would give the
     * exact wire JSON, but the lsp4j jsonrpc classes on this project's compile and runtime classpath
     * resolve to the older copy IntelliJ Platform bundles for its own generic LSP support, which
     * predates that accessor; {@link Message#toString()} is present on both and is close enough for a
     * debug detail column.
     */
    private static String serialize(Message message) {
        return message.toString();
    }
}
