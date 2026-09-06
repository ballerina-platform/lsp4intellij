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
package org.wso2.lsp4intellij.inspector;

import com.intellij.execution.filters.TextConsoleBuilderFactory;
import com.intellij.execution.ui.ConsoleView;
import com.intellij.execution.ui.ConsoleViewContentType;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.ActionToolbar;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.actionSystem.ex.ComboBoxAction;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.openapi.wm.ex.ToolWindowManagerListener;
import com.intellij.ui.JBSplitter;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.treeStructure.Tree;
import com.intellij.util.Alarm;
import com.intellij.util.messages.MessageBusConnection;
import org.eclipse.lsp4j.MessageType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.wso2.lsp4intellij.client.languageserver.wrapper.LanguageServerWrapper;
import org.wso2.lsp4intellij.logging.LspLogEntry;
import org.wso2.lsp4intellij.logging.LspLogListener;
import org.wso2.lsp4intellij.logging.LspLogSource;
import org.wso2.lsp4intellij.logging.LspLogStore;
import org.wso2.lsp4intellij.logging.LspTraceLevel;
import org.wso2.lsp4intellij.services.LspServerManager;

import java.awt.BorderLayout;
import java.awt.Component;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;

import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import javax.swing.tree.TreeSelectionModel;

/**
 * Left: a tree of the servers running for this project, grouped by server definition. Right: one
 * {@link ConsoleView} per server, created lazily and cached for as long as this panel lives.
 *
 * <p>{@link LspLogListener#entryAdded(LspLogEntry)} fires synchronously on whichever thread produced
 * the entry, which for a traced message is an lsp4j dispatch thread, not the EDT. This panel never
 * touches Swing from that callback: it only offers the entry onto a {@link ConcurrentLinkedQueue}, and
 * a recurring {@link Alarm} request — always run on the EDT, since the alarm is created with
 * {@link Alarm.ThreadToUse#SWING_THREAD} — drains the queue in a batch every
 * {@value #FLUSH_INTERVAL_MILLIS}ms. The same tick also re-checks
 * {@link LspServerManager#allWrappers()} and rebuilds the tree if the set of known servers grew, so a
 * server that starts after this panel is already open still appears without a separate poller.
 *
 * <p>That timer only runs while the tool window is visible; hiding it cancels the alarm, so a console
 * nobody is looking at costs nothing. Entries published while it is hidden are discarded rather than
 * queued, because {@link #pendingEntries} would otherwise grow without limit during a verbose trace.
 * Nothing is lost: {@link LspLogStore} is the bounded source of truth, and every open console is
 * re-printed from it when the tool window becomes visible again.
 *
 * <p>Every {@link ConsoleView} created here, the {@link MessageBusConnection} and the {@link Alarm}
 * are registered as children of this panel's own {@link Disposable}, so
 * {@link LspInspectorToolWindowFactory} disposing this panel tears down all three.
 */
public final class LspInspectorPanel extends JBSplitter implements Disposable {

    private static final int FLUSH_INTERVAL_MILLIS = 100;
    private static final DateTimeFormatter TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern("HH:mm:ss");
    private static final String NO_SERVERS_TEXT = "No LSP servers are running for this project.";
    private static final String SELECT_A_SERVER_TEXT = "Select a server to view its log.";

    private final Project project;
    private final Tree tree;
    private final DefaultMutableTreeNode root = new DefaultMutableTreeNode();
    private final DefaultTreeModel treeModel = new DefaultTreeModel(root);
    private final JPanel rightPanel = new JPanel(new BorderLayout());
    private final Map<LanguageServerWrapper, ConsoleView> consoles = new HashMap<>();
    private final Map<String, LanguageServerWrapper> serverIdToWrapper = new HashMap<>();
    private final Set<LanguageServerWrapper> knownWrappers = new LinkedHashSet<>();
    private final ConcurrentLinkedQueue<LspLogEntry> pendingEntries = new ConcurrentLinkedQueue<>();
    private final ConcurrentLinkedQueue<String> pendingClears = new ConcurrentLinkedQueue<>();
    private final Alarm flushAlarm;
    private final ToolWindow toolWindow;

    private LspInspectorFilter filter = LspInspectorFilter.ALL;
    private String levelFilterLabel = "All Levels";
    private String sourceFilterLabel = "All Sources";
    private boolean flushScheduled = false;

    public LspInspectorPanel(@NotNull Project project, @NotNull ToolWindow toolWindow) {
        super(false, 0.25f);
        this.project = project;
        this.toolWindow = toolWindow;

        tree = new Tree(treeModel);
        tree.setRootVisible(false);
        tree.setShowsRootHandles(true);
        tree.getSelectionModel().setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);
        tree.getEmptyText().setText(NO_SERVERS_TEXT);
        tree.addTreeSelectionListener(e -> showConsoleFor(getSelectedWrapper()));

        rightPanel.add(buildToolbar().getComponent(), BorderLayout.NORTH);
        showEmptyState(SELECT_A_SERVER_TEXT);

        setFirstComponent(new JBScrollPane(tree));
        setSecondComponent(rightPanel);

        flushAlarm = new Alarm(Alarm.ThreadToUse.SWING_THREAD, this);
        MessageBusConnection connection = project.getMessageBus().connect(this);
        connection.subscribe(LspLogListener.TOPIC, new LspLogListener() {
            @Override
            public void entryAdded(@NotNull LspLogEntry entry) {
                pendingEntries.offer(entry);
            }

            @Override
            public void serverCleared(@NotNull String serverId) {
                pendingClears.offer(serverId);
            }
        });

        connection.subscribe(ToolWindowManagerListener.TOPIC, new ToolWindowManagerListener() {
            @Override
            public void stateChanged(@NotNull ToolWindowManager toolWindowManager) {
                updateFlushState();
            }
        });

        refreshKnownWrappers();
        rebuildTree();
        updateFlushState();
    }

    @Override
    public void dispose() {
        // Every ConsoleView, the message bus connection and the flush alarm are registered as
        // children of this Disposable and are torn down by the platform's Disposer.
    }

    /**
     * Starts or stops the flush timer to match the tool window's visibility, so a hidden console costs
     * nothing. Runs on EDT, both from the constructor and from {@link ToolWindowManagerListener}.
     *
     * <p>While stopped, entries published by {@link LspLogStore} are discarded rather than queued: the
     * store is the source of truth and is bounded, so on becoming visible again every open console is
     * re-printed from it. Queueing instead would let {@link #pendingEntries} grow without limit behind
     * a hidden tool window during a verbose trace.
     */
    private void updateFlushState() {
        boolean visible = toolWindow.isVisible();
        if (visible == flushScheduled) {
            return;
        }
        if (visible) {
            flushScheduled = true;
            pendingEntries.clear();
            pendingClears.clear();
            refreshKnownWrappers();
            rebuildTree();
            reprintAllConsoles();
            scheduleFlush();
        } else {
            flushScheduled = false;
            flushAlarm.cancelAllRequests();
            pendingEntries.clear();
            pendingClears.clear();
        }
    }

    private void scheduleFlush() {
        flushAlarm.addRequest(this::flush, FLUSH_INTERVAL_MILLIS);
    }

    private void flush() {
        if (!flushScheduled) {
            return;
        }
        drainPendingClears();
        drainPendingEntries();
        if (refreshKnownWrappers()) {
            rebuildTree();
        }
        scheduleFlush();
    }

    private void drainPendingClears() {
        String serverId;
        while ((serverId = pendingClears.poll()) != null) {
            LanguageServerWrapper wrapper = serverIdToWrapper.get(serverId);
            ConsoleView console = wrapper == null ? null : consoles.get(wrapper);
            if (console != null) {
                console.clear();
            }
        }
    }

    private void drainPendingEntries() {
        LspLogEntry entry;
        while ((entry = pendingEntries.poll()) != null) {
            LanguageServerWrapper wrapper = serverIdToWrapper.get(entry.serverId());
            if (wrapper == null) {
                // Not in the tree yet; LspLogStore already has it and the tree will catch up on the
                // next tick, at which point the console is primed from the full buffer.
                continue;
            }
            ConsoleView console = consoles.get(wrapper);
            if (console != null && filter.matches(entry)) {
                printEntry(console, entry);
            }
        }
    }

    /**
     * Adds every wrapper {@link LspServerManager} currently knows about to {@link #knownWrappers}.
     * Never removes one, so a server's node stays in the tree after it stops: a server that has just
     * died is the one whose log someone is most likely to want to read, matching how
     * {@link LspLogStore} keeps a stopped server's buffer around. Returns whether the set grew.
     */
    private boolean refreshKnownWrappers() {
        LspServerManager manager = LspServerManager.getInstanceIfCreated(project);
        Set<LanguageServerWrapper> current = manager == null ? Set.of() : manager.allWrappers();
        return knownWrappers.addAll(current);
    }

    private void rebuildTree() {
        LanguageServerWrapper previouslySelected = getSelectedWrapper();

        root.removeAllChildren();
        serverIdToWrapper.clear();
        Map<String, DefaultMutableTreeNode> groups = new LinkedHashMap<>();
        for (LanguageServerWrapper wrapper : knownWrappers) {
            String serverId = wrapper.getServerDefinition().ext;
            serverIdToWrapper.put(serverId, wrapper);
            DefaultMutableTreeNode group = groups.get(serverId);
            if (group == null) {
                group = new DefaultMutableTreeNode(wrapper.getServerDefinition().toString());
                groups.put(serverId, group);
                root.add(group);
            }
            group.add(new DefaultMutableTreeNode(new ServerLeaf(wrapper)));
        }
        treeModel.reload();
        for (int row = 0; row < tree.getRowCount(); row++) {
            tree.expandRow(row);
        }
        if (previouslySelected != null) {
            selectWrapper(previouslySelected);
        }
    }

    private void selectWrapper(@NotNull LanguageServerWrapper wrapper) {
        for (int i = 0; i < root.getChildCount(); i++) {
            DefaultMutableTreeNode group = (DefaultMutableTreeNode) root.getChildAt(i);
            for (int j = 0; j < group.getChildCount(); j++) {
                DefaultMutableTreeNode leaf = (DefaultMutableTreeNode) group.getChildAt(j);
                if (leaf.getUserObject() instanceof ServerLeaf serverLeaf && serverLeaf.wrapper() == wrapper) {
                    tree.setSelectionPath(new TreePath(leaf.getPath()));
                    return;
                }
            }
        }
    }

    @Nullable
    private LanguageServerWrapper getSelectedWrapper() {
        TreePath path = tree.getSelectionPath();
        if (path == null) {
            return null;
        }
        Object userObject = ((DefaultMutableTreeNode) path.getLastPathComponent()).getUserObject();
        return userObject instanceof ServerLeaf serverLeaf ? serverLeaf.wrapper() : null;
    }

    private void showConsoleFor(@Nullable LanguageServerWrapper wrapper) {
        if (wrapper == null) {
            showEmptyState(SELECT_A_SERVER_TEXT);
            return;
        }
        ConsoleView console = consoles.computeIfAbsent(wrapper, this::createConsole);
        showComponent(console.getComponent());
    }

    @NotNull
    private ConsoleView createConsole(@NotNull LanguageServerWrapper wrapper) {
        ConsoleView console = TextConsoleBuilderFactory.getInstance().createBuilder(project).getConsole();
        Disposer.register(this, console);
        LspLogStore store = LspLogStore.getInstanceIfCreated(project);
        if (store != null) {
            for (LspLogEntry entry : store.entries(wrapper)) {
                if (filter.matches(entry)) {
                    printEntry(console, entry);
                }
            }
        }
        return console;
    }

    /**
     * Re-prints the currently selected server's console from {@link LspLogStore#entries}, applying
     * the current filter. Called whenever the level or source filter changes; affordable because the
     * buffer is bounded at {@link LspLogStore#CAPACITY}.
     */
    private void reprintSelected() {
        LanguageServerWrapper wrapper = getSelectedWrapper();
        ConsoleView console = wrapper == null ? null : consoles.get(wrapper);
        if (wrapper != null && console != null) {
            reprintConsole(wrapper, console);
        }
    }

    /**
     * Re-prints every console this panel has already created. Used when the tool window becomes
     * visible again, since entries published while it was hidden were discarded rather than queued.
     */
    private void reprintAllConsoles() {
        consoles.forEach(this::reprintConsole);
    }

    private void reprintConsole(@NotNull LanguageServerWrapper wrapper, @NotNull ConsoleView console) {
        console.clear();
        LspLogStore store = LspLogStore.getInstanceIfCreated(project);
        if (store == null) {
            return;
        }
        for (LspLogEntry entry : store.entries(wrapper)) {
            if (filter.matches(entry)) {
                printEntry(console, entry);
            }
        }
    }

    private void printEntry(@NotNull ConsoleView console, @NotNull LspLogEntry entry) {
        Instant timestamp = Instant.ofEpochMilli(entry.timestampMillis());
        String time = TIMESTAMP_FORMAT.format(timestamp.atZone(ZoneId.systemDefault()));
        String marker = switch (entry.source()) {
            case TRACE_OUT -> "→";
            case TRACE_IN -> "←";
            default -> entry.level().name();
        };
        console.print(time + " " + marker + " " + entry.summary() + "\n", contentTypeFor(entry.level()));
        if (entry.detail() != null) {
            console.print("    " + entry.detail() + "\n", ConsoleViewContentType.SYSTEM_OUTPUT);
        }
    }

    @NotNull
    private static ConsoleViewContentType contentTypeFor(@NotNull MessageType level) {
        return switch (level) {
            case Error -> ConsoleViewContentType.ERROR_OUTPUT;
            case Warning -> ConsoleViewContentType.LOG_WARNING_OUTPUT;
            case Info -> ConsoleViewContentType.LOG_INFO_OUTPUT;
            case Log -> ConsoleViewContentType.NORMAL_OUTPUT;
        };
    }

    private void showEmptyState(@NotNull String message) {
        JBLabel label = new JBLabel(message, SwingConstants.CENTER);
        showComponent(label);
    }

    private void showComponent(@NotNull JComponent component) {
        BorderLayout layout = (BorderLayout) rightPanel.getLayout();
        Component current = layout.getLayoutComponent(BorderLayout.CENTER);
        if (current != null) {
            rightPanel.remove(current);
        }
        rightPanel.add(component, BorderLayout.CENTER);
        rightPanel.revalidate();
        rightPanel.repaint();
    }

    @NotNull
    private ActionToolbar buildToolbar() {
        DefaultActionGroup group = new DefaultActionGroup();
        group.add(new LevelFilterAction());
        group.add(new SourceFilterAction());
        group.add(new TraceLevelAction());
        group.add(new ClearAction());
        group.add(new ScrollToEndAction());
        ActionToolbar toolbar = ActionManager.getInstance().createActionToolbar("LspInspector", group, true);
        toolbar.setTargetComponent(this);
        return toolbar;
    }

    /**
     * Tree leaf user object for one running (or previously running) server. Overrides
     * {@code toString()} because {@link LanguageServerWrapper} does not, and the default tree cell
     * renderer displays a node's user object via {@code toString()}.
     *
     * @param wrapper the server this leaf represents
     */
    private record ServerLeaf(@NotNull LanguageServerWrapper wrapper) {

        @Override
        public String toString() {
            String rootPath = wrapper.getProjectRootPath();
            return rootPath != null ? rootPath : wrapper.getServerDefinition().toString();
        }
    }

    private final class LevelFilterAction extends ComboBoxAction implements DumbAware {

        private final List<Option> options = List.of(
                new Option("All Levels", null),
                new Option("Error", MessageType.Error),
                new Option("Warning+", MessageType.Warning),
                new Option("Info+", MessageType.Info));

        private record Option(@NotNull String label, @Nullable MessageType minLevel) {
        }

        LevelFilterAction() {
            getTemplatePresentation().setIcon(AllIcons.General.Filter);
        }

        @Override
        public void update(@NotNull AnActionEvent e) {
            e.getPresentation().setText(levelFilterLabel);
        }

        @NotNull
        @Override
        protected DefaultActionGroup createPopupActionGroup(@NotNull JComponent button, @NotNull DataContext context) {
            DefaultActionGroup popup = new DefaultActionGroup();
            for (Option option : options) {
                popup.add(new AnAction(option.label()) {
                    @Override
                    public void actionPerformed(@NotNull AnActionEvent e) {
                        levelFilterLabel = option.label();
                        filter = new LspInspectorFilter(option.minLevel(), filter.source());
                        reprintSelected();
                    }
                });
            }
            return popup;
        }
    }

    private final class SourceFilterAction extends ComboBoxAction implements DumbAware {

        private final List<Option> options = List.of(
                new Option("All Sources", null),
                new Option("Server Log", LspLogSource.SERVER_LOG),
                new Option("Server Message", LspLogSource.SERVER_MESSAGE),
                new Option("Lifecycle", LspLogSource.LIFECYCLE),
                new Option("Trace Out", LspLogSource.TRACE_OUT),
                new Option("Trace In", LspLogSource.TRACE_IN));

        private record Option(@NotNull String label, @Nullable LspLogSource source) {
        }

        SourceFilterAction() {
            getTemplatePresentation().setIcon(AllIcons.General.Filter);
        }

        @Override
        public void update(@NotNull AnActionEvent e) {
            e.getPresentation().setText(sourceFilterLabel);
        }

        @NotNull
        @Override
        protected DefaultActionGroup createPopupActionGroup(@NotNull JComponent button, @NotNull DataContext context) {
            DefaultActionGroup popup = new DefaultActionGroup();
            for (Option option : options) {
                popup.add(new AnAction(option.label()) {
                    @Override
                    public void actionPerformed(@NotNull AnActionEvent e) {
                        sourceFilterLabel = option.label();
                        filter = new LspInspectorFilter(filter.minLevel(), option.source());
                        reprintSelected();
                    }
                });
            }
            return popup;
        }
    }

    /**
     * Sets the JSON-RPC trace level for the selected server via
     * {@link LanguageServerWrapper#setTraceLevel(LspTraceLevel)}, and reflects that server's current
     * {@link LanguageServerWrapper#getTraceLevel()} in its own label. Tracks the tree selection since
     * the trace level is per server.
     */
    private final class TraceLevelAction extends ComboBoxAction implements DumbAware {

        TraceLevelAction() {
            getTemplatePresentation().setIcon(AllIcons.Debugger.Console);
        }

        @Override
        public void update(@NotNull AnActionEvent e) {
            LanguageServerWrapper wrapper = getSelectedWrapper();
            e.getPresentation().setEnabled(wrapper != null);
            e.getPresentation().setText(wrapper == null ? "Trace" : "Trace: " + wrapper.getTraceLevel().name());
        }

        @NotNull
        @Override
        protected DefaultActionGroup createPopupActionGroup(@NotNull JComponent button, @NotNull DataContext context) {
            DefaultActionGroup popup = new DefaultActionGroup();
            for (LspTraceLevel level : LspTraceLevel.values()) {
                popup.add(new AnAction(level.name()) {
                    @Override
                    public void actionPerformed(@NotNull AnActionEvent e) {
                        LanguageServerWrapper wrapper = getSelectedWrapper();
                        if (wrapper != null) {
                            wrapper.setTraceLevel(level);
                        }
                    }
                });
            }
            return popup;
        }
    }

    private final class ClearAction extends AnAction implements DumbAware {

        ClearAction() {
            super("Clear", "Clear the buffered log for the selected server", AllIcons.Actions.GC);
        }

        @Override
        public void update(@NotNull AnActionEvent e) {
            e.getPresentation().setEnabled(getSelectedWrapper() != null);
        }

        @Override
        public void actionPerformed(@NotNull AnActionEvent e) {
            LanguageServerWrapper wrapper = getSelectedWrapper();
            if (wrapper == null) {
                return;
            }
            LspLogStore store = LspLogStore.getInstanceIfCreated(project);
            if (store != null) {
                store.removeBuffer(wrapper);
            }
            ConsoleView console = consoles.get(wrapper);
            if (console != null) {
                console.clear();
            }
        }
    }

    private final class ScrollToEndAction extends AnAction implements DumbAware {

        ScrollToEndAction() {
            super("Scroll to End", "Scroll the selected server's console to the last entry",
                    AllIcons.RunConfigurations.Scroll_down);
        }

        @Override
        public void update(@NotNull AnActionEvent e) {
            e.getPresentation().setEnabled(getSelectedWrapper() != null);
        }

        @Override
        public void actionPerformed(@NotNull AnActionEvent e) {
            LanguageServerWrapper wrapper = getSelectedWrapper();
            ConsoleView console = wrapper == null ? null : consoles.get(wrapper);
            if (console != null) {
                console.requestScrollingToEnd();
            }
        }
    }
}
