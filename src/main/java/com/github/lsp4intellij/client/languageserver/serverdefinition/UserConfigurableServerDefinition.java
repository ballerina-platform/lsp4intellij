package com.github.lsp4intellij.client.languageserver.serverdefinition;

import com.intellij.openapi.diagnostic.Logger;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;

/**
 * A UserConfigurableServerDefinition is a server definition which can be manually entered by the user in the IntellliJ settings
 */
public class UserConfigurableServerDefinition extends LanguageServerDefinition
        implements UserConfigurableServerDefinitionObject {

    private static final Logger LOG = Logger.getInstance(UserConfigurableServerDefinition.class);

    /**
     * @return the type of the server definition
     */
    protected String typ = "userConfigurable";
    /**
     * @return the type of the server definition in a nicer way
     */
    protected String presentableTyp = "Configurable";

    protected UserConfigurableServerDefinition() {
    }

    static String head(String[] arr) {
        return arr[0];
    }

    static String[] tail(String[] arr) {
        return Arrays.copyOfRange(arr, 1, arr.length - 1);
    }

    @Override
    public String getTyp() {
        return typ;
    }

    @Override
    public String getPresentableTyp() {
        return presentableTyp;
    }

    /**
     * Transforms a Map<String, ServerDefinitionExtensionPointArtifact> to a Map<String, String[]>
     *
     * @param map A map
     * @return the transformed map
     */
    public Map<String, String[]> toArrayMap(Map<String, UserConfigurableServerDefinition> map) {
        Map<String, String[]> result = new HashMap<>();
        map.forEach((key, value) -> result.put(key, value.toArray()));
        return result;
    }

    /**
     * Transforms a (java) Map<String, String[]> to a Map<String, ServerDefinitionExtensionPointArtifact>
     *
     * @param map A java map
     * @return the transformed java map
     */
    public Map<String, UserConfigurableServerDefinition> fromArrayMap(Map<String, String[]> map) {
        Map<String, UserConfigurableServerDefinition> result = new HashMap<>();
        map.forEach((key, value) -> result.put(key, fromArray(value)));
        return result;
    }

    public UserConfigurableServerDefinition fromArray(String[] arr) {
        String[] filteredArr = Stream.of(arr).filter(s -> s != null && !"".equals(s.trim())).toArray(String[]::new);
        ArtifactLanguageServerDefinition artifact = ArtifactLanguageServerDefinition.getInstance()
                .fromArray(filteredArr);
        if (artifact == null) {
            return CommandServerDefinition.getInstance().fromArray(filteredArr);
        } else {
            return artifact;
        }
    }
}
