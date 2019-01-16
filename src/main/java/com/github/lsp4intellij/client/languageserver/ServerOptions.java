package com.github.lsp4intellij.client.languageserver;

import org.eclipse.lsp4j.CodeLensOptions;
import org.eclipse.lsp4j.CompletionOptions;
import org.eclipse.lsp4j.DocumentLinkOptions;
import org.eclipse.lsp4j.DocumentOnTypeFormattingOptions;
import org.eclipse.lsp4j.ExecuteCommandOptions;
import org.eclipse.lsp4j.SemanticHighlightingServerCapabilities;
import org.eclipse.lsp4j.SignatureHelpOptions;
import org.eclipse.lsp4j.TextDocumentSyncKind;

/**
 * Class containing the options of the language server
 *
 * syncKind                        The type of synchronization
 * completionOptions               The completion options
 * signatureHelpOptions            The signatureHelp options
 * codeLensOptions                 The codeLens options
 * documentOnTypeFormattingOptions The onTypeFormatting options
 * documentLinkOptions             The link options
 * executeCommandOptions           The execute options
 */
public class ServerOptions {

    //Todo - Revisit and implement with accessors
    public TextDocumentSyncKind syncKind;
    public CompletionOptions completionOptions;
    public SignatureHelpOptions signatureHelpOptions;
    public CodeLensOptions codeLensOptions;
    public DocumentOnTypeFormattingOptions documentOnTypeFormattingOptions;
    public DocumentLinkOptions documentLinkOptions;
    public ExecuteCommandOptions executeCommandOptions;
    public SemanticHighlightingServerCapabilities semanticHighlightingOptions;

    public ServerOptions(TextDocumentSyncKind syncKind, CompletionOptions completionOptions,
            SignatureHelpOptions signatureHelpOptions, CodeLensOptions codeLensOptions,
            DocumentOnTypeFormattingOptions documentOnTypeFormattingOptions, DocumentLinkOptions documentLinkOptions,
            ExecuteCommandOptions executeCommandOptions,
            SemanticHighlightingServerCapabilities semanticHighlightingOptions) {

        this.syncKind = syncKind;
        this.completionOptions = completionOptions;
        this.signatureHelpOptions = signatureHelpOptions;
        this.codeLensOptions = codeLensOptions;
        this.documentOnTypeFormattingOptions = documentOnTypeFormattingOptions;
        this.documentLinkOptions = documentLinkOptions;
        this.executeCommandOptions = executeCommandOptions;
        this.semanticHighlightingOptions = semanticHighlightingOptions;
    }
}
