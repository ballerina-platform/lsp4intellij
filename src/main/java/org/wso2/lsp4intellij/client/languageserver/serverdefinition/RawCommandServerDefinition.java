/*
 * Copyright (c) 2019, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
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

import org.wso2.lsp4intellij.client.connection.ProcessStreamConnectionProvider;
import org.wso2.lsp4intellij.client.connection.StreamConnectionProvider;

import java.util.Arrays;
import java.util.Collections;
import java.util.Map;

/**
 * A class representing raw command based metadata to launch a language server.
 */
@SuppressWarnings("unused")
public class RawCommandServerDefinition extends LanguageServerDefinition {

    protected String[] command;

    /**
     * Creates new instance with the given languag id which is different from the file extension.
     *
     * @param ext         The extension
     * @param languageIds The language server ids mapping to extension(s).
     * @param command     The command to run
     */
    @SuppressWarnings("WeakerAccess")
    public RawCommandServerDefinition(String ext, Map<String, String> languageIds, String[] command) {
        this.ext = ext;
        this.languageIds = languageIds;
        this.command = command;
    }

    /**
     * Creates new instance.
     *
     * @param ext     The extension
     * @param command The command to run
     */
    @SuppressWarnings("unused")
    public RawCommandServerDefinition(String ext, String[] command) {
        this(ext, Collections.emptyMap(), command);
    }

    public String toString() {
        return "RawCommandServerDefinition : " + String.join(" ", command);
    }

    @Override
    public StreamConnectionProvider createConnectionProvider(String workingDir) {
        return new ProcessStreamConnectionProvider(Arrays.asList(command), workingDir);
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof RawCommandServerDefinition) {
            RawCommandServerDefinition commandsDef = (RawCommandServerDefinition) obj;
            return ext.equals(commandsDef.ext) && Arrays.equals(command, commandsDef.command);
        }
        return false;
    }

    @Override
    public int hashCode() {
        return ext.hashCode() + 3 * Arrays.hashCode(command);
    }
}
