package org.wso2.lsp4intellij.notifiers;

import com.intellij.util.messages.Topic;
import org.wso2.lsp4intellij.client.languageserver.ServerStatus;

public interface ServerStatusNotifier {
    Topic<ServerStatusNotifier> SERVER_STATUS_TOPIC = Topic.create("org.wso2.lsp4intellij.notifiers.ServerStatusNotifier", ServerStatusNotifier.class);

    void serverStatusChanged(ServerStatus oldStatus, ServerStatus newStatus);
}
