/*
 * Copyright (c) 2026, WSO2 LLC. (http://www.wso2.org) All Rights Reserved.
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

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.wso2.lsp4intellij.client.languageserver.serverdefinition.LanguageServerDefinition;

import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A map of file extension (or file-name regex pattern) to {@link LanguageServerDefinition}, with
 * lookup either by an exact extension match or by matching a file name against the map's keys as
 * regex patterns.
 *
 * <p>Held by both {@link LspServerManager} and {@link LspApplicationServerRegistry}, which differ
 * only in scope (project vs. application) — not in how they store or look up definitions. Public
 * because both callers are outside this package; internal to the library like the two services
 * themselves.
 */
public final class DefinitionRegistry {

    private final Map<String, LanguageServerDefinition> extToDefinition = new ConcurrentHashMap<>();

    public void register(String ext, LanguageServerDefinition definition) {
        extToDefinition.put(ext, definition);
    }

    public void remove(String ext) {
        extToDefinition.remove(ext);
    }

    @Nullable
    public LanguageServerDefinition definitionForExt(String ext) {
        return extToDefinition.get(ext);
    }

    @Nullable
    public Map.Entry<String, LanguageServerDefinition> matchByFileName(String fileName) {
        for (Map.Entry<String, LanguageServerDefinition> entry : extToDefinition.entrySet()) {
            if (fileName.matches(entry.getKey())) {
                return entry;
            }
        }
        return null;
    }

    public boolean hasDefinitionMatching(String ext, String fileName) {
        for (String key : extToDefinition.keySet()) {
            if (key.equals(ext) || fileName.matches(key)) {
                return true;
            }
        }
        return false;
    }

    @NotNull
    public Map<String, LanguageServerDefinition> asMap() {
        return Collections.unmodifiableMap(extToDefinition);
    }
}
