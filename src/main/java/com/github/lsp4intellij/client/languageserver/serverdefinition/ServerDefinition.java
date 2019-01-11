package com.github.lsp4intellij.client.languageserver.serverdefinition;

public interface ServerDefinition {
    /**
     * Transforms an array of string into the corresponding UserConfigurableServerDefinition
     *
     * @param arr The array
     * @return The server definition
     */
    UserConfigurableServerDefinition fromArray(String[] arr);
}
