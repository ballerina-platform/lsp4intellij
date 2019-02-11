package com.github.lsp4intellij.client.languageserver.serverdefinition;

/**
 * Companion objects of the UserConfigurableServerDefinition
 */
public interface UserConfigurableServerDefinitionObject {
    /**
     * @return the type of the server definition
     */
    String getTyp();

    /**
     * @return the type of the server definition in a nicer way
     */
    String getPresentableTyp();

    /**
     * Transforms an array of string into the corresponding UserConfigurableServerDefinition
     *
     * @param arr The array
     * @return The server definition
     */
    UserConfigurableServerDefinition fromArray(String[] arr);
}
