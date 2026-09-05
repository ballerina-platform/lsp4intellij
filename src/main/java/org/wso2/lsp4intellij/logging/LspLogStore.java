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
package org.wso2.lsp4intellij.logging;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.project.Project;
import org.eclipse.lsp4j.MessageType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.wso2.lsp4intellij.client.languageserver.serverdefinition.LanguageServerDefinition;
import org.wso2.lsp4intellij.client.languageserver.wrapper.LanguageServerWrapper;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Project-scoped, always-on capture point for {@link LspLogEntry} records: one bounded buffer per
 * {@link LanguageServerWrapper}, capacity {@value #CAPACITY}, oldest entry evicted first on overflow.
 * Every append is also published on {@link LspLogListener#TOPIC}.
 *
 * <p>This is a light IntelliJ Platform service; it requires no plugin.xml registration, exactly like
 * {@code LspServerManager}. Recording an entry for a server that has not logged anything yet in this
 * project is a registration-shaped operation and uses {@link #getInstance(Project)}; every other call
 * site only records for a server that is already running, so it uses {@link #getInstanceIfCreated(Project)}
 * and silently does nothing if the project has started disposing in the meantime.
 *
 * <p>A wrapper's buffer is removed by {@link #removeBuffer(LanguageServerWrapper)} once that wrapper
 * stops for good. Every remaining buffer is dropped by {@link #dispose()} when the project itself is
 * disposed, without publishing one {@link LspLogListener#serverCleared(String)} per server: by then the
 * project's message bus is on its way out as well, so state is cleared directly instead.
 */
@Service(Service.Level.PROJECT)
public final class LspLogStore implements Disposable {

    /** Maximum number of entries retained per server; the oldest is evicted once this is exceeded. */
    public static final int CAPACITY = 10_000;

    private final Project project;
    private final Map<LanguageServerWrapper, Buffer> buffers = new ConcurrentHashMap<>();
    private volatile boolean disposed = false;

    public LspLogStore(Project project) {
        this.project = project;
    }

    /**
     * Returns this project's log store, creating it if it does not exist yet.
     */
    public static LspLogStore getInstance(@NotNull Project project) {
        return project.getService(LspLogStore.class);
    }

    /**
     * Returns this project's log store, or null if the project is disposed (or disposing) or no store
     * has been created for it yet. Read and cleanup paths must use this rather than
     * {@link #getInstance(Project)}, for the same reason {@code LspServerManager.getInstanceIfCreated}
     * documents: a project can close between a caller's check and the lookup, and {@code Project.getService}
     * throws on a disposed project.
     */
    @Nullable
    public static LspLogStore getInstanceIfCreated(@NotNull Project project) {
        if (project.isDisposed()) {
            return null;
        }
        return project.getServiceIfCreated(LspLogStore.class);
    }

    /**
     * Appends an entry to {@code wrapper}'s buffer and publishes it on {@link LspLogListener#TOPIC}.
     * Does nothing once this store is disposed.
     */
    public void append(@NotNull LanguageServerWrapper wrapper, @NotNull LspLogSource source,
            @NotNull MessageType level, @NotNull String summary, @Nullable String detail) {
        if (disposed) {
            return;
        }
        LanguageServerDefinition definition = wrapper.getServerDefinition();
        LspLogEntry entry = new LspLogEntry(System.currentTimeMillis(), definition.ext, definition.toString(),
                source, level, summary, detail);
        buffers.computeIfAbsent(wrapper, w -> new Buffer()).add(entry);
        if (!project.isDisposed()) {
            project.getMessageBus().syncPublisher(LspLogListener.TOPIC).entryAdded(entry);
        }
    }

    /**
     * Returns a snapshot of the entries currently buffered for {@code wrapper}, oldest first. Empty if
     * nothing has been recorded for it (including if it is unknown to this store).
     */
    @NotNull
    public List<LspLogEntry> entries(@NotNull LanguageServerWrapper wrapper) {
        Buffer buffer = buffers.get(wrapper);
        return buffer == null ? List.of() : buffer.snapshot();
    }

    /**
     * Removes {@code wrapper}'s buffer and publishes {@link LspLogListener#serverCleared(String)}, if a
     * buffer existed for it.
     */
    public void removeBuffer(@NotNull LanguageServerWrapper wrapper) {
        Buffer removed = buffers.remove(wrapper);
        if (removed != null && !project.isDisposed()) {
            project.getMessageBus().syncPublisher(LspLogListener.TOPIC)
                    .serverCleared(wrapper.getServerDefinition().ext);
        }
    }

    @Override
    public void dispose() {
        disposed = true;
        buffers.clear();
    }

    /**
     * A bounded FIFO of entries for one server. Synchronized on itself: {@link #add} runs on whichever
     * JSON-RPC dispatch thread produced the entry, and {@link #snapshot} may run concurrently from
     * another one.
     */
    private static final class Buffer {

        private final Deque<LspLogEntry> entries = new ArrayDeque<>();

        synchronized void add(LspLogEntry entry) {
            entries.addLast(entry);
            if (entries.size() > CAPACITY) {
                entries.removeFirst();
            }
        }

        synchronized List<LspLogEntry> snapshot() {
            return List.copyOf(entries);
        }
    }
}
