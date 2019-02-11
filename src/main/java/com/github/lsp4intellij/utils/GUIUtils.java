package com.github.lsp4intellij.utils;

import com.github.lsp4intellij.client.languageserver.serverdefinition.LanguageServerDefinition;
import com.github.lsp4intellij.contributors.icon.LSPDefaultIconProvider;
import com.github.lsp4intellij.contributors.icon.LSPIconProvider;

public class GUIUtils {

    /**
     * Returns a suitable LSPIconProvider given a ServerDefinition
     *
     * @param serverDefinition The serverDefinition
     * @return The LSPIconProvider, or LSPDefaultIconProvider if none are found
     */
    public static LSPIconProvider getIconProviderFor(LanguageServerDefinition serverDefinition) {
        return new LSPDefaultIconProvider();
    }
}
