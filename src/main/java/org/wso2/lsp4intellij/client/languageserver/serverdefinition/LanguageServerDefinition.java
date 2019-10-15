/*
 * Copyright (c) 2018-2019, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.wso2.lsp4intellij.client.languageserver.serverdefinition;

import com.intellij.openapi.diagnostic.Logger;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.jetbrains.annotations.NotNull;
import org.wso2.lsp4intellij.client.connection.StreamConnectionProvider;
import org.wso2.lsp4intellij.contributors.icon.LSPDefaultIconProvider;
import org.wso2.lsp4intellij.contributors.icon.LSPIconProvider;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A trait representing a ServerDefinition
 */
public class LanguageServerDefinition {
    public static final String SPLIT_CHAR = ",";
    private static final Logger LOG = Logger.getInstance(LanguageServerDefinition.class);
    private static final LanguageServerDefinition INSTANCE = new LanguageServerDefinition();
    /**
     * The extension that the language server manages.
     */
    public String ext;

    protected Map<String, String> languageIds = Collections.emptyMap();

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
     * @throws IOException if the stream connection provider is crashed
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

    public ServerListener getServerListener() {
        return ServerListener.DEFAULT;
    }

    /**
     * Return language id for the given extension. if there is no langauge ids registered then the
     * return value will be the value of <code>extension</code>.
     */
    public String languageIdFor(String extension) {
        return languageIds.getOrDefault(extension, extension);
    }
}
