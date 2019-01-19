package com.github.lsp4intellij.preloading;

import com.github.lsp4intellij.client.languageserver.serverdefinition.LanguageServerDefinition;
import com.github.lsp4intellij.client.languageserver.serverdefinition.RawCommandServerDefinition;

/**
 * Language Server Definition Register Service.
 */
public class LanguageServerRegisterService {

    static void register(String[] args) {
        LanguageServerDefinition.getInstance().register(new RawCommandServerDefinition("bal", args));
    }
}
