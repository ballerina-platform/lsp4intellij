package com.github.lsp4intellij.client.languageserver.serverdefinition;

import com.github.lsp4intellij.client.connection.ProcessStreamConnectionProvider;
import com.github.lsp4intellij.client.connection.StreamConnectionProvider;

/**
 * A base trait for every command-line server definition
 */
public abstract class CommandServerDefinition extends UserConfigurableServerDefinition {
    protected String[] command;

    public CommandServerDefinition() {
        this.getPresentableTyp = "Command";
        this.typ = "command";
    }

    public UserConfigurableServerDefinition fromArray(String[] arr) {
        CommandServerDefinition raw = RawCommandServerDefinition.fromArray(arr);
        if (raw == null) {
            return ExeLanguageServerDefinition.fromArray(arr);
        } else {
            return raw;
        }
    }

    @Override
    public StreamConnectionProvider createConnectionProvider(String workingDir) {
        return new ProcessStreamConnectionProvider(command, workingDir);
    }
}
