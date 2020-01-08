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
package org.wso2.lsp4intellij.client;

import java.util.Arrays;
import java.util.Optional;

/**
 * Enum for methods which may support DynamicRegistration
 */
public enum DynamicRegistrationMethods {

    DID_CHANGE_CONFIGURATION("workspace/didChangeConfiguration"),
    DID_CHANGE_WATCHED_FILES("workspace/didChangeWatchedFiles"),
    SYMBOL("workspace/symbol"),
    EXECUTE_COMMAND("workspace/executeCommand"),
    SYNCHRONIZATION("textDocument/synchronization"),
    COMPLETION("textDocument/completion"),
    HOVER("textDocument/hover"),
    SIGNATURE_HELP("textDocument/signatureHelp"),
    REFERENCES("textDocument/references"),
    DOCUMENT_HIGHLIGHT("textDocument/documentHighlight"),
    DOCUMENT_SYMBOL("textDocument/documentSymbol"),
    FORMATTING("textDocument/formatting"),
    RANGE_FORMATTING("textDocument/rangeFormatting"),
    ONTYPE_FORMATTING("textDocument/onTypeFormatting"),
    DEFINITION("textDocument/definition"),
    CODE_ACTION("textDocument/codeAction"),
    CODE_LENS("textDocument/codeLens"),
    DOCUMENT_LINK("textDocument/documentLink"),
    RENAME("textDocument/rename");

    private final String name;

    DynamicRegistrationMethods(final String name) {
        this.name = name;
    }

    public static Optional<DynamicRegistrationMethods> forName(final String name) {
        return Arrays.stream(DynamicRegistrationMethods.values()).filter(n -> n.name.equals(name)).findAny();
    }

    public String getName() {
        return name;
    }

}
