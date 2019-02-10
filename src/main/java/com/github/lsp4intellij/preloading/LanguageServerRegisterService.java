package com.github.lsp4intellij.preloading;

import com.github.lsp4intellij.PluginMain;
import com.github.lsp4intellij.client.languageserver.serverdefinition.LanguageServerDefinition;
import com.github.lsp4intellij.client.languageserver.serverdefinition.RawCommandServerDefinition;
import com.github.lsp4intellij.ballerinaextension.BallerinaLSPExtensionManager;

/**
 * Language Server Definition Register Service.
 */
public class LanguageServerRegisterService {

    static void register(String[] args) {
        LanguageServerDefinition.getInstance().register(new RawCommandServerDefinition("bal", args));
        PluginMain.addLSPExtension("bal", new BallerinaLSPExtensionManager());
    }
}
