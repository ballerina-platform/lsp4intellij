package com.github.lsp4intellij.client.languageserver.serverdefinition;

/**
 * Java doesn't like Objects in Trait apparently
 * This represents the known types of UserConfigurableServerDefinition
 */
public enum ConfigurableTypes {
    ARTIFACT(ArtifactLanguageServerDefinition.getInstance().presentableTyp), EXE(ExeLanguageServerDefinition.getInstance().presentableTyp), RAWCOMMAND(RawCommandServerDefinition.getInstance().presentableTyp);
    private final String typ;

    ConfigurableTypes(final String typ) {
        this.typ = typ;
    }

    public String getTyp() {
        return typ;
    }
}
