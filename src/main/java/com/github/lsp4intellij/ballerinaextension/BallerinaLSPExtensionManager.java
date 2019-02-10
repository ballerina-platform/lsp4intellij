package com.github.lsp4intellij.ballerinaextension;

import com.github.lsp4intellij.ballerinaextension.client.ExtendedRequestManager;
import com.github.lsp4intellij.ballerinaextension.editoreventmanager.ExtendedEditorEventManager;
import com.github.lsp4intellij.ballerinaextension.server.ExtendedLanguageServer;
import com.github.lsp4intellij.client.languageserver.ServerOptions;
import com.github.lsp4intellij.client.languageserver.requestmanager.DefaultRequestManager;
import com.github.lsp4intellij.client.languageserver.requestmanager.RequestManager;
import com.github.lsp4intellij.client.languageserver.wrapper.LanguageServerWrapper;
import com.github.lsp4intellij.client.languageserver.wrapper.LanguageServerWrapperImpl;
import com.github.lsp4intellij.editor.EditorEventManager;
import com.github.lsp4intellij.extensions.LSPExtensionManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.event.DocumentListener;
import org.eclipse.lsp4j.ServerCapabilities;
import org.eclipse.lsp4j.services.LanguageClient;
import org.eclipse.lsp4j.services.LanguageServer;

public class BallerinaLSPExtensionManager implements LSPExtensionManager {

    @Override
    public <T extends DefaultRequestManager> T getExtendedRequestManagerFor(LanguageServerWrapper wrapper,
            LanguageServer server, LanguageClient client, ServerCapabilities serverCapabilities) {
        return (T) new ExtendedRequestManager(wrapper, server, client, serverCapabilities);
    }

    @Override
    public <T extends EditorEventManager> T getExtendedEditorEventManagerFor(Editor editor,
            DocumentListener documentListener, RequestManager requestManager, ServerOptions serverOptions,
            LanguageServerWrapperImpl wrapper) {
        return (T) new ExtendedEditorEventManager(editor, documentListener, requestManager, serverOptions, wrapper);
    }

    @Override
    public Class<? extends LanguageServer> getExtendedServerInterface() {
        return ExtendedLanguageServer.class;
    }
}
