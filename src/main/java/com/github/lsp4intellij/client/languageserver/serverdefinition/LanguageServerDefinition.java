package com.github.lsp4intellij.client.languageserver.serverdefinition;

import com.github.lsp4intellij.client.LanguageClientImpl;
import com.github.lsp4intellij.client.connection.StreamConnectionProvider;
import com.intellij.openapi.diagnostic.Logger;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A trait representing a ServerDefinition
 */
public class LanguageServerDefinition {
    public static final String SPLIT_CHAR = ";";
    private static final Logger LOG = Logger.getInstance(LanguageServerDefinition.class);
    private static final LanguageServerDefinition INSTANCE = new LanguageServerDefinition();
    /**
     * @return The extension that the language server manages
     */
    public String ext;
    /**
     * @return The id of the language server (same as extension)
     */
    public String id;

    private Map<String, StreamConnectionProvider> streamConnectionProviders = new ConcurrentHashMap<>();

    LanguageServerDefinition() {
    }

    public static LanguageServerDefinition getInstance() {
        return INSTANCE;
    }

    public LanguageServerDefinition fromArray(String[] arr) {
        return new UserConfigurableServerDefinition().fromArray(arr);
    }

    /**
     * Starts a Language server for the given directory and returns a tuple (InputStream, OutputStream)
     *
     * @param workingDir The root directory
     * @return The input and output streams of the server
     */
    public Pair<InputStream, OutputStream> start(String workingDir) throws IOException {
        StreamConnectionProvider streamConnectionProvider = streamConnectionProviders.get(workingDir);
        if (streamConnectionProvider != null) {
            return new ImmutablePair<>(streamConnectionProvider.getInputStream(),
                    streamConnectionProvider.getOutputStream());
        } else {
            streamConnectionProvider = createConnectionProvider(workingDir);
            streamConnectionProvider.start();
            streamConnectionProviders.put(workingDir, streamConnectionProvider);
            return new ImmutablePair<>(streamConnectionProvider.getInputStream(),
                    streamConnectionProvider.getOutputStream());
        }
    }

    /**
     * Stops the Language server corresponding to the given working directory
     *
     * @param workingDir The root directory
     */
    public void stop(String workingDir) {
        StreamConnectionProvider streamConnectionProvider = streamConnectionProviders.get(workingDir);
        if (streamConnectionProvider != null) {
            streamConnectionProvider.stop();
            streamConnectionProviders.remove(workingDir);
        } else {
            LOG.warn("No connection for workingDir " + workingDir + " and ext " + ext);
        }
    }

    /**
     * @return the LanguageClient for this LanguageServer
     */
    public LanguageClientImpl createLanguageClient() {
        return new LanguageClientImpl();
    }

    public Object getInitializationOptions(URI uri) {
        return null;
    }

    @Override
    public String toString() {
        return "ServerDefinition for " + ext;
    }

    /**
     * @return The array corresponding to the server definition
     */
    public String[] toArray() {
        throw new UnsupportedOperationException();
    }

    /**
     * Creates a StreamConnectionProvider given the working directory
     *
     * @param workingDir The root directory
     * @return The stream connection provider
     */
    public StreamConnectionProvider createConnectionProvider(String workingDir) {
        throw new UnsupportedOperationException();
    }
}
