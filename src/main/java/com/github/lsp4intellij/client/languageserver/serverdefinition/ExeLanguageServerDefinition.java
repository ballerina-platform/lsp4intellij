package com.github.lsp4intellij.client.languageserver.serverdefinition;

import java.util.Arrays;

/**
 * Class representing server definitions corresponding to an executable file This class is basically a more convenient
 * way to write a RawCommand
 */
public class ExeLanguageServerDefinition extends CommandServerDefinition {
    private static final ExeLanguageServerDefinition INSTANCE = new ExeLanguageServerDefinition();

    private ExeLanguageServerDefinition() {
    }

    public static ExeLanguageServerDefinition getInstance() {
        return INSTANCE;
    }

    private String path;
    private String[] args;

    /**
     * Creates new JExeLanguageServerDefinition.
     *
     * @param ext  The extension
     * @param path The path to the exe file
     * @param args The arguments for the exe file
     */
    public ExeLanguageServerDefinition(String ext, String path, String[] args) {
        this.ext = ext;
        this.id = ext;
        this.path = path;
        this.args = args;
    }

    public String[] toArray() {
        String[] strings = { typ, ext, path };
        String[] merged = Arrays.copyOf(strings, strings.length + args.length);
        System.arraycopy(args, 0, merged, strings.length, args.length);
        return merged;
    }

    public String toString() {
        return typ + " : path " + path + " args : " + String.join(" ", args);
    }

    public String[] command() {
        String[] strings = { path };
        String[] merged = Arrays.copyOf(strings, strings.length + args.length);
        System.arraycopy(args, 0, merged, strings.length, args.length);
        return merged;
    }

    public boolean equals(Object obj) {
        if (obj instanceof ExeLanguageServerDefinition) {
            ExeLanguageServerDefinition commandsDef = (ExeLanguageServerDefinition) obj;
            return ext.equals(commandsDef.ext) && path.equals(commandsDef.path) && Arrays
                    .equals(args, commandsDef.args);
        }
        return false;
    }

    public int hashCode() {
        return ext.hashCode() + 3 * path.hashCode() + 7 * args.hashCode();
    }
}