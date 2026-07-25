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

import com.intellij.openapi.Disposable;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.wso2.lsp4intellij.client.languageserver.serverdefinition.LanguageServerDefinition;
import org.wso2.lsp4intellij.client.languageserver.wrapper.LanguageServerWrapper;
import org.wso2.lsp4intellij.extensions.LSPExtensionManager;
import org.wso2.lsp4intellij.utils.FileUtils;

import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Project-scoped registry backing the parts of {@code IntellijLanguageClient}'s and
 * {@code LanguageServerWrapper}'s static APIs that are scoped to one project: project-level server
 * definitions, and the wrappers running for this project (indexed by extension and by connected
 * file URI).
 *
 * <p>This is a light IntelliJ Platform service, obtained via {@link #getInstance(Project)}; it
 * requires no plugin.xml registration. It is internal to the library — plugin developers should
 * keep using the existing static methods on {@code IntellijLanguageClient} and
 * {@code LanguageServerWrapper}.
 */
@Service(Service.Level.PROJECT)
public final class LspServerManager implements Disposable {

    private static final Logger LOG = Logger.getInstance(LspServerManager.class);

    private final Project project;
    private final Map<String, LanguageServerDefinition> extToDefinition = new ConcurrentHashMap<>();
    private final Map<String, LanguageServerWrapper> extToWrapper = new ConcurrentHashMap<>();
    private final Map<String, LanguageServerWrapper> uriToWrapper = new ConcurrentHashMap<>();
    private final Set<LanguageServerWrapper> wrappers = ConcurrentHashMap.newKeySet();
    private volatile LanguageServerWrapper lastWrapper;
    private boolean disposed = false;

    public LspServerManager(Project project) {
        this.project = project;
    }

    public static LspServerManager getInstance(@NotNull Project project) {
        return project.getService(LspServerManager.class);
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

    @NotNull
    public Map<String, LanguageServerDefinition> allDefinitions() {
        return Collections.unmodifiableMap(extToDefinition);
    }

    /**
     * Returns the wrapper serving the given extension for this project, creating and registering
     * one first if none exists yet. Synchronized per project instance (not globally, unlike the
     * static method this replaces), so concurrent wrapper creation in unrelated projects is never
     * blocked by this lock. Returns null once this project has started disposing (see
     * {@link #dispose()}), so that a wrapper can never be created after the point past which
     * nothing will dispose it.
     */
    @Nullable
    public synchronized LanguageServerWrapper getOrCreateWrapper(
            String ext, LanguageServerDefinition serverDefinition, @Nullable LSPExtensionManager extManager) {
        if (disposed) {
            LOG.debug("Ignoring wrapper creation for " + ext + " after project "
                    + FileUtils.projectToUri(project) + " started disposing");
            return null;
        }
        LanguageServerWrapper wrapper = extToWrapper.get(ext);
        if (wrapper != null) {
            LOG.info("Wrapper already existing for " + ext + " , " + FileUtils.projectToUri(project));
            return wrapper;
        }
        LOG.info("Instantiating wrapper for " + ext + " : " + FileUtils.projectToUri(project));
        wrapper = extManager != null
                ? new LanguageServerWrapper(serverDefinition, project, extManager)
                : new LanguageServerWrapper(serverDefinition, project);
        for (String ex : serverDefinition.ext.split(LanguageServerDefinition.SPLIT_CHAR)) {
            extToWrapper.put(ex, wrapper);
        }
        wrappers.add(wrapper);
        lastWrapper = wrapper;
        return wrapper;
    }

    @Nullable
    public LanguageServerWrapper wrapperForUri(String uri) {
        return uriToWrapper.get(uri);
    }

    public void mapUri(String uri, LanguageServerWrapper wrapper) {
        uriToWrapper.put(uri, wrapper);
    }

    public void unmapUri(String uri) {
        uriToWrapper.remove(uri);
    }

    @Nullable
    public LanguageServerWrapper lastWrapper() {
        return lastWrapper;
    }

    @NotNull
    public Set<LanguageServerWrapper> allWrappers() {
        return new HashSet<>(wrappers);
    }

    /**
     * Removes the given wrapper from this project's registry. Mirrors what the static
     * {@code IntellijLanguageClient.removeWrapper} did: the server definition for each of the
     * wrapper's extensions is forgotten along with the wrapper itself, not just the ext-to-wrapper
     * mapping.
     */
    public synchronized void unregister(LanguageServerWrapper wrapper, String[] extensions) {
        for (String ext : extensions) {
            extToWrapper.remove(ext);
            extToDefinition.remove(ext);
        }
        wrappers.remove(wrapper);
        if (lastWrapper == wrapper) {
            lastWrapper = null;
        }
    }

    /**
     * Disposes every wrapper registered for this project.
     *
     * <p>Called explicitly (and synchronously) from {@code LSPProjectManagerListener.projectClosing},
     * before the project itself starts disposing — the one point in the wrapper lifecycle where
     * touching the project is known to be safe, since that listener runs to completion before the
     * project's own dispose sequence begins. This method is also this service's own
     * {@link Disposable#dispose()}, so the platform's project-close sequence disposes any wrapper
     * the explicit trigger missed. Safe to call twice: {@link LanguageServerWrapper#dispose()} is
     * idempotent, and a second call here iterates an already-empty set.
     *
     * <p>Synchronized with {@link #getOrCreateWrapper}, and sets the terminal {@code disposed} flag
     * before taking the snapshot below, so a wrapper cannot be created concurrently with (or after)
     * disposal and then be left running with nothing left to dispose it.
     */
    @Override
    public synchronized void dispose() {
        disposed = true;
        for (LanguageServerWrapper wrapper : new HashSet<>(wrappers)) {
            wrapper.dispose();
        }
    }
}
