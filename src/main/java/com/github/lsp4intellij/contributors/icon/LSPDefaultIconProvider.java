package com.github.lsp4intellij.contributors.icon;

import com.github.lsp4intellij.client.languageserver.ServerStatus;
import com.github.lsp4intellij.client.languageserver.serverdefinition.LanguageServerDefinition;
import com.intellij.icons.AllIcons.Nodes;
import com.intellij.openapi.util.IconLoader;
import org.eclipse.lsp4j.CompletionItemKind;

import java.util.HashMap;
import java.util.Map;
import javax.swing.*;

public class LSPDefaultIconProvider extends LSPIconProvider {

    private static Icon STARTED = IconLoader.getIcon("/images/started.png");
    private static Icon STARTING = IconLoader.getIcon("/images/starting.png");
    private static Icon STOPPED = IconLoader.getIcon("/images/stopped.png");

    public static Icon getCompletionIcon(CompletionItemKind kind) {

        if (kind == CompletionItemKind.Class) {
            return Nodes.Class;
        } else if (kind == CompletionItemKind.Enum) {
            return Nodes.Class;
        } else if (kind == CompletionItemKind.Snippet) {
            return Nodes.Static;
        } else {
            return null;
        }
    }

    public static Map<ServerStatus, Icon> getStatusIcons() {
        Map<ServerStatus, Icon> statusIconMap = new HashMap<>();
        statusIconMap.put(ServerStatus.STOPPED, STOPPED);
        statusIconMap.put(ServerStatus.STARTING, STARTING);
        statusIconMap.put(ServerStatus.STARTED, STARTED);
        return statusIconMap;
    }

    @Override
    public boolean isSpecificFor(LanguageServerDefinition serverDefinition) {
        return false;
    }
}
