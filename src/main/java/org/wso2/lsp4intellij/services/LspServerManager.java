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

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Project-scoped registry backing the parts of {@code IntellijLanguageClient}'s and
 * {@code LanguageServerWrapper}'s static APIs that are scoped to one project: project-level server
 * definitions, and the wrappers running for this project (indexed by extension and by connected
 * file URI).
 *
 * <p>This is a light IntelliJ Platform service; it requires no plugin.xml registration. Registration
 * paths obtain it via {@link #getInstance(Project)}; read and cleanup paths must use
 * {@link #getInstanceIfCreated(Project)}, which returns null instead of throwing on a project that
 * is closing. It is internal to the library — plugin developers should keep using the existing
 * static methods on {@code IntellijLanguageClient} and {@code LanguageServerWrapper}.
 */
@Service(Service.Level.PROJECT)
public final class LspServerManager implements Disposable {

    private static final Logger LOG = Logger.getInstance(LspServerManager.class);

    private final Project project;
    private final DefinitionRegistry definitions = new DefinitionRegistry();
    private final Map<String, LanguageServerWrapper> extToWrapper = new ConcurrentHashMap<>();
    private final Map<String, LanguageServerWrapper> uriToWrapper = new ConcurrentHashMap<>();
    private final Set<LanguageServerWrapper> wrappers = ConcurrentHashMap.newKeySet();
    private final AtomicReference<LanguageServerWrapper> lastWrapper = new AtomicReference<>();
    private boolean disposed = false;

    public LspServerManager(Project project) {
        this.project = project;
    }

    /**
     * Returns this project's manager, creating it if it does not exist yet. Only for call sites that
     * are registering state (definitions, wrappers) and are known to run while the project is alive.
     * Throws if the project is already disposed; read-only lookups must use
     * {@link #getInstanceIfCreated(Project)} instead.
     */
    public static LspServerManager getInstance(@NotNull Project project) {
        return project.getService(LspServerManager.class);
    }

    /**
     * Returns this project's manager, or null if the project is disposed (or disposing) or no
     * manager has been created for it yet.
     *
     * <p>Read paths must use this rather than {@link #getInstance(Project)}. The static facades on
     * {@code IntellijLanguageClient} and {@code LanguageServerWrapper} that these back were plain
     * map lookups before the registries became services: they returned null or an empty set for an
     * unknown project and never threw. {@code Project.getService} throws
     * {@code AlreadyDisposedException} on a disposed project, and several of those facades run on
     * EDT contributor paths (annotator, rename, reformat, status-bar widget) or asynchronously from
     * a pool thread, where a project can close between the caller's check and the lookup. Returning
     * null there preserves the original never-throws contract.
     *
     * <p>Not creating the service when absent is also correct for read paths: nothing can be
     * registered for a project without going through {@link #getInstance(Project)} first, so a
     * project with no manager has no definitions and no wrappers by construction.
     */
    @Nullable
    public static LspServerManager getInstanceIfCreated(@NotNull Project project) {
        if (project.isDisposed()) {
            return null;
        }
        return project.getServiceIfCreated(LspServerManager.class);
    }

    @NotNull
    public DefinitionRegistry definitions() {
        return definitions;
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
        lastWrapper.set(wrapper);
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
        return lastWrapper.get();
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
     *
     * <p>Deliberately not {@code synchronized}. This runs inside
     * {@link LanguageServerWrapper#dispose()}, which holds the wrapper's own monitor, while
     * {@link #dispose()} takes this service's monitor and then each wrapper's — so locking here
     * would establish the reverse acquisition order and make the two paths deadlockable. Every
     * field it touches is already thread-safe on its own (both maps are concurrent, {@code wrappers}
     * is a concurrent set, and {@code lastWrapper} is cleared with a compare-and-set), so the
     * monitor bought no atomicity worth that risk.
     */
    public void unregister(LanguageServerWrapper wrapper, String[] extensions) {
        for (String ext : extensions) {
            extToWrapper.remove(ext);
            definitions.remove(ext);
        }
        wrappers.remove(wrapper);
        lastWrapper.compareAndSet(wrapper, null);
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
     *
     * <p>Each wrapper is disposed inside its own try/catch so that one failure cannot abort disposal
     * of the rest — this method is the platform's last-resort cleanup for this project, so a partial
     * run leaks a running language server process. The registry is then cleared directly rather than
     * relying on each wrapper's {@link #unregister} callback, which is skipped on the platform's
     * disposal path (by then {@link #getInstanceIfCreated} returns null, since the project is
     * already disposing).
     */
    @Override
    public synchronized void dispose() {
        disposed = true;
        for (LanguageServerWrapper wrapper : new HashSet<>(wrappers)) {
            try {
                wrapper.dispose();
            } catch (Exception e) {
                LOG.warn("Failed to dispose the language server wrapper for "
                        + wrapper.getServerDefinition().ext, e);
            }
        }
        wrappers.clear();
        extToWrapper.clear();
        uriToWrapper.clear();
        lastWrapper.set(null);
    }
}
