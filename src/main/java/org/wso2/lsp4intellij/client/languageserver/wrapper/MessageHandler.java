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
package org.wso2.lsp4intellij.client.languageserver.wrapper;

import org.eclipse.lsp4j.InitializeResult;
import org.eclipse.lsp4j.jsonrpc.MessageConsumer;
import org.eclipse.lsp4j.jsonrpc.messages.Message;
import org.eclipse.lsp4j.jsonrpc.messages.ResponseMessage;
import org.eclipse.lsp4j.services.LanguageServer;
import org.jetbrains.annotations.NotNull;
import org.wso2.lsp4intellij.client.languageserver.serverdefinition.ServerListener;

import java.util.function.BooleanSupplier;
import java.util.function.Function;

class MessageHandler implements Function<MessageConsumer, MessageConsumer> {

    private ServerListener listener;
    private BooleanSupplier isRunning;
    private LanguageServer languageServer;

    MessageHandler(@NotNull ServerListener listener, @NotNull BooleanSupplier isRunning) {
        this.listener = listener;
        this.isRunning = isRunning;
    }

    @Override
    public MessageConsumer apply(MessageConsumer messageConsumer) {
        return message -> {
            if(isRunning.getAsBoolean()) {
                handleMessage(message);
                messageConsumer.consume(message);
            }
        };

    }

    private void handleMessage(Message message) {
        if (message instanceof ResponseMessage) {
            ResponseMessage responseMessage = (ResponseMessage) message;
            if (responseMessage.getResult() instanceof InitializeResult) {
                listener.initialize(languageServer, (InitializeResult) responseMessage.getResult());
            }
        }
    }

    void setLanguageServer(@NotNull LanguageServer languageServer) {
        this.languageServer = languageServer;
    }
}
