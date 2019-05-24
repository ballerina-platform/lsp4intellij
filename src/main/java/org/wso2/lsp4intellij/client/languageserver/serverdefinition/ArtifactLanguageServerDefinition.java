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
import org.wso2.lsp4intellij.client.connection.ProcessStreamConnectionProvider;
import org.wso2.lsp4intellij.client.connection.StreamConnectionProvider;
import org.wso2.lsp4intellij.utils.Utils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Represents a ServerDefinition for a LanguageServer stored on a repository
 */
public class ArtifactLanguageServerDefinition extends UserConfigurableServerDefinition {
    private final static Logger LOG = Logger.getInstance(UserConfigurableServerDefinition.class);

    private String packge;
    private String mainClass;
    private String[] args;

    private static final ArtifactLanguageServerDefinition INSTANCE = new ArtifactLanguageServerDefinition();

    private ArtifactLanguageServerDefinition() {
    }

    public static ArtifactLanguageServerDefinition getInstance() {
        return INSTANCE;
    }

    /**
     * Creates a new ArtifactLanguageServerDefinition.
     *
     * @param ext       The extension that the server manages
     * @param packge    The artifact id of the server
     * @param mainClass The main class of the server
     * @param args      The arguments to give to the main class
     */
    public ArtifactLanguageServerDefinition(String ext, String packge, String mainClass, String[] args) {
        this.ext = ext;
        this.languageIds = Collections.emptyMap();
        this.packge = packge;
        this.mainClass = mainClass;
        this.args = args;
        this.typ = "artifact";
        this.presentableTyp = "Artifact";
    }

    public ArtifactLanguageServerDefinition fromArray(String[] arr) {
        if (arr[0].equals(typ)) {
            String[] arrTail = tail(arr);
            if (arrTail.length < 3) {
                LOG.warn("Not enough elements to translate into a ServerDefinition : " + String.join(" ; ", arr));
                return null;
            } else {
                String[] args = (arrTail.length > 3) ? Utils.parseArgs(tail(tail(tail(arrTail)))) : new String[] {};
                return new ArtifactLanguageServerDefinition(arrTail[0], head(tail(arrTail)), head(tail(tail(arrTail))),
                        args);
            }
        } else {
            return null;
        }
    }

    public StreamConnectionProvider createConnectionProvider(String workingDir) {
        // TODO: Re-Check CoursierImpl resolveClasspath
        // val cp = CoursierImpl.resolveClasspath(packge);
        String cp = packge;
        List<String> command = new ArrayList<>();
        command.add("java");
        command.add("-cp");
        command.add(cp);
        command.add(mainClass);
        Collections.addAll(command, this.args);
        return new ProcessStreamConnectionProvider(command, workingDir);
    }

    public String toString() {
        return super.toString() + " " + getTyp() + " : " + packge + " mainClass : " + mainClass + " args : " + String
                .join(" ", args);
    }

    public String[] toArray() {
        String[] strings = new String[] { getTyp(), ext, packge, mainClass };
        String[] merged = Arrays.copyOf(strings, strings.length + args.length);
        System.arraycopy(args, 0, merged, strings.length, args.length);
        return merged;
    }

    public boolean equals(Object obj) {
        if (obj instanceof ArtifactLanguageServerDefinition) {
            ArtifactLanguageServerDefinition definition = (ArtifactLanguageServerDefinition) obj;
            return ext.equals(definition.ext) && mainClass.equals(definition.mainClass) && Arrays
                    .equals(args, definition.args);
        }
        return false;
    }

    public int hashCode() {
        return ext.hashCode() + 3 * packge.hashCode() + 7 * mainClass.hashCode() + 11 * args.hashCode();
    }
}