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

import java.util.Collections;
import java.util.Map;

/**
 * A class representing {@link java.lang.ProcessBuilder} based metadata to launch a language server.
 */
@SuppressWarnings("unused")
public class ProcessBuilderServerDefinition extends LanguageServerDefinition {

    protected ProcessBuilder processBuilder;

    /**
     * Creates new instance with the given language id which is different from the file extension.
     *
     * @param ext         The extension.
     * @param languageIds The language server ids mapping to extension(s).
     * @param process     The process builder instance to be started.
     */
    @SuppressWarnings("WeakerAccess")
    public ProcessBuilderServerDefinition(String ext, Map<String, String> languageIds, ProcessBuilder process) {
        this.ext = ext;
        this.languageIds = languageIds;
        this.processBuilder = process;
    }

    /**
     * Creates new instance.
     *
     * @param ext     The extension.
     * @param process The process builder instance to be started.
     */
    @SuppressWarnings("unused")
    public ProcessBuilderServerDefinition(String ext, ProcessBuilder process) {
        this(ext, Collections.emptyMap(), process);
    }

    public String toString() {
        return "ProcessBuilderServerDefinition : " + String.join(" ", processBuilder.command());
    }

    @Override
    public StreamConnectionProvider createConnectionProvider(String workingDir) {
        return new ProcessStreamConnectionProvider(processBuilder);
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof ProcessBuilderServerDefinition) {
            ProcessBuilderServerDefinition processBuilderDef = (ProcessBuilderServerDefinition) obj;
            return ext.equals(processBuilderDef.ext) && processBuilder.equals(processBuilderDef.processBuilder);
        }
        return false;
    }

    @Override
    public int hashCode() {
        return ext.hashCode() + 3 * processBuilder.hashCode();
    }
}
