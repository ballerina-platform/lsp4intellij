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

import org.wso2.lsp4intellij.utils.Utils;

import java.util.Arrays;

/**
 * A class representing a raw command to launch a languageserver
 */
public class RawCommandServerDefinition extends CommandServerDefinition {
    private static final RawCommandServerDefinition INSTANCE = new RawCommandServerDefinition();

    private RawCommandServerDefinition() {
    }

    public static RawCommandServerDefinition getInstance() {
        return INSTANCE;
    }

    /**
     * Creates new instance.
     *
     * @param ext     The extension
     * @param command The command to run
     */
    public RawCommandServerDefinition(String ext, String[] command) {
        this.ext = ext;
        this.id = ext;
        this.command = command;
        this.typ = "rawCommand";
        this.presentableTyp = "Raw command";
    }

    /**
     * Transforms an array of string into the corresponding UserConfigurableServerDefinition
     *
     * @param arr The array
     * @return The server definition
     */
    public CommandServerDefinition fromArray(String[] arr) {
        if (arr[0].equals(typ)) {
            String[] arrTail = Arrays.copyOfRange(arr, 1, arr.length - 1);
            if (arrTail.length > 1) {
                new RawCommandServerDefinition(arrTail[0],
                        Utils.parseArgs(Arrays.copyOfRange(arrTail, 1, arrTail.length - 1)));
            }
        }
        return null;
    }

    //  import RawCommandServerDefinition.typ

    /**
     * @return The array corresponding to the server definition
     */
    public String[] toArray() {
        String[] strings = { typ, ext };
        String[] merged = Arrays.copyOf(strings, strings.length + command.length);
        System.arraycopy(command, 0, merged, strings.length, command.length);
        return merged;
    }

    public String toString() {
        return typ + " : " + String.join(" ", command);
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
        return ext.hashCode() + 3 * command.hashCode();
    }
}
