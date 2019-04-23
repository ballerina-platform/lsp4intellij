package org.wso2.lsp4intellij.client.languageserver.wrapper;

import java.util.function.Function;
import org.eclipse.lsp4j.InitializeResult;
import org.eclipse.lsp4j.jsonrpc.MessageConsumer;
import org.eclipse.lsp4j.jsonrpc.messages.Message;
import org.eclipse.lsp4j.jsonrpc.messages.ResponseMessage;
import org.eclipse.lsp4j.services.LanguageServer;
import org.jetbrains.annotations.NotNull;
import org.wso2.lsp4intellij.client.languageserver.serverdefinition.ServerListener;

class MessageHandler implements Function<MessageConsumer, MessageConsumer> {

    private ServerListener listener;
    private LanguageServer languageServer;

    public MessageHandler(ServerListener listener) {
        this.listener = listener;
    }


    @Override
    public MessageConsumer apply(MessageConsumer messageConsumer) {
        return message -> {
            handleMessage(message);
            messageConsumer.consume(message);
        };

    }

    private void handleMessage(Message message) {
        if (message instanceof ResponseMessage) {
            ResponseMessage responseMessage = (ResponseMessage) message;
            if (responseMessage.getResult() instanceof InitializeResult) {
                listener.initilize(languageServer, (InitializeResult) responseMessage.getResult());
            }
        }
    }

    public void setLanguageServer(@NotNull LanguageServer languageServer) {
        this.languageServer = languageServer;
    }
}
