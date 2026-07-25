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

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.Service;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.wso2.lsp4intellij.client.languageserver.serverdefinition.LanguageServerDefinition;
import org.wso2.lsp4intellij.extensions.LSPExtensionManager;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Application-wide registry backing the parts of {@code IntellijLanguageClient}'s static API that
 * are not scoped to a project: server definitions registered without a project (applicable to any
 * project that opens a matching file) and the LSP extension managers (which are not project-scoped
 * either — a manager is registered per file extension only).
 *
 * <p>This is a light IntelliJ Platform service, obtained via {@link #getInstance()}; it requires no
 * plugin.xml registration. It is internal to the library — plugin developers should keep using
 * {@code IntellijLanguageClient}'s static methods.
 */
@Service(Service.Level.APP)
public final class LspApplicationServerRegistry {

    private final Map<String, LanguageServerDefinition> extToDefinition = new ConcurrentHashMap<>();
    private final Map<String, LSPExtensionManager> extToExtManager = new ConcurrentHashMap<>();

    public static LspApplicationServerRegistry getInstance() {
        return ApplicationManager.getApplication().getService(LspApplicationServerRegistry.class);
    }

    public void registerDefinition(String ext, LanguageServerDefinition definition) {
        extToDefinition.put(ext, definition);
    }

    @Nullable
    public LanguageServerDefinition definitionForExt(String ext) {
        return DefinitionMatcher.byExt(extToDefinition, ext);
    }

    @Nullable
    public Map.Entry<String, LanguageServerDefinition> matchByFileName(String fileName) {
        return DefinitionMatcher.byFileName(extToDefinition, fileName);
    }

    public boolean hasDefinitionMatching(String ext, String fileName) {
        return DefinitionMatcher.matches(extToDefinition, ext, fileName);
    }

    @Nullable
    public LSPExtensionManager extensionManagerFor(String ext) {
        return extToExtManager.get(ext);
    }

    public boolean hasExtensionManager(String ext) {
        return extToExtManager.containsKey(ext);
    }

    public void registerExtensionManager(@NotNull String ext, @NotNull LSPExtensionManager manager) {
        extToExtManager.put(ext, manager);
    }
}
