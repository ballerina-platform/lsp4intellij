/*
 * Copyright (c) 2026, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
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
package org.wso2.lsp4intellij.services;

import org.jetbrains.annotations.Nullable;
import org.wso2.lsp4intellij.client.languageserver.serverdefinition.LanguageServerDefinition;

import java.util.Map;

/**
 * Lookup logic shared by {@link LspServerManager} and {@link LspApplicationServerRegistry}: each
 * holds a map of file extension (or file-name regex pattern) to {@link LanguageServerDefinition},
 * and both need to resolve a definition either by an exact extension match or, failing that, by
 * matching a file name against the map's keys as regex patterns.
 */
final class DefinitionMatcher {

    private DefinitionMatcher() {
    }

    @Nullable
    static LanguageServerDefinition byExt(Map<String, LanguageServerDefinition> definitions, String ext) {
        return definitions.get(ext);
    }

    @Nullable
    static Map.Entry<String, LanguageServerDefinition> byFileName(
            Map<String, LanguageServerDefinition> definitions, String fileName) {
        for (Map.Entry<String, LanguageServerDefinition> entry : definitions.entrySet()) {
            if (fileName.matches(entry.getKey())) {
                return entry;
            }
        }
        return null;
    }

    static boolean matches(Map<String, LanguageServerDefinition> definitions, String ext, String fileName) {
        for (String key : definitions.keySet()) {
            if (key.equals(ext) || fileName.matches(key)) {
                return true;
            }
        }
        return false;
    }
}
