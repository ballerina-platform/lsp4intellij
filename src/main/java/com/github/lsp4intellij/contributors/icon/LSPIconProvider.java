package com.github.lsp4intellij.contributors.icon;

import com.github.lsp4intellij.client.languageserver.ServerStatus;
import com.github.lsp4intellij.client.languageserver.serverdefinition.LanguageServerDefinition;
import org.eclipse.lsp4j.CompletionItemKind;
import org.eclipse.lsp4j.SymbolKind;

import java.util.Map;
import javax.swing.*;

public abstract class LSPIconProvider {
    public static Icon getCompletionIcon(CompletionItemKind kind){
        return LSPDefaultIconProvider.getCompletionIcon(kind);
    }

    public static Map<ServerStatus, Icon> getStatusIcons() {
        return LSPDefaultIconProvider.getStatusIcons();
    }

    public static Icon getSymbolIcon(SymbolKind kind){
        return LSPDefaultIconProvider.getSymbolIcon(kind);
    }

    public abstract boolean isSpecificFor(LanguageServerDefinition serverDefinition);
}
