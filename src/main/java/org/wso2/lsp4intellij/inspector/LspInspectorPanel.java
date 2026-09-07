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
import com.intellij.ui.ColoredListCellRenderer;
import com.intellij.ui.ColoredTreeCellRenderer;
import com.intellij.ui.JBColor;
import com.intellij.ui.JBSplitter;
import com.intellij.ui.ListSpeedSearch;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBList;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.table.JBTable;
import com.intellij.ui.treeStructure.Tree;
import com.intellij.util.Alarm;
import com.intellij.util.messages.MessageBusConnection;
import org.eclipse.lsp4j.MessageType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.wso2.lsp4intellij.client.languageserver.serverdefinition.LanguageServerDefinition;
import org.wso2.lsp4intellij.client.languageserver.wrapper.LanguageServerWrapper;
import org.wso2.lsp4intellij.logging.LspLogEntry;
import org.wso2.lsp4intellij.logging.LspLogListener;
import org.wso2.lsp4intellij.logging.LspLogSource;
import org.wso2.lsp4intellij.logging.LspLogStore;
import org.wso2.lsp4intellij.logging.LspTraceLevel;
import org.wso2.lsp4intellij.requests.TimeoutStats;
import org.wso2.lsp4intellij.requests.Timeouts;
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

import javax.swing.DefaultListModel;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.JTree;
import javax.swing.SwingConstants;
import javax.swing.ToolTipManager;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableCellRenderer;
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
    private final JPanel logPanel = new JPanel(new BorderLayout());
    private final ConnectedFilesPanel connectedFilesPanel = new ConnectedFilesPanel();
    private final TimeoutsPanel timeoutsPanel = new TimeoutsPanel();
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
        // The node labels are deliberately short; the full command line and project path live here.
        tree.setCellRenderer(new ServerTreeRenderer());
        ToolTipManager.sharedInstance().registerComponent(tree);
        tree.addTreeSelectionListener(e -> {
            LanguageServerWrapper selected = getSelectedWrapper();
            showConsoleFor(selected);
            connectedFilesPanel.refresh(selected);
            timeoutsPanel.refresh(selected);
        });

        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab("Log", logPanel);
        tabs.addTab("Timeouts", timeoutsPanel);
        tabs.addTab("Connected Files", connectedFilesPanel);

        rightPanel.add(buildToolbar().getComponent(), BorderLayout.NORTH);
        rightPanel.add(tabs, BorderLayout.CENTER);
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
            connectedFilesPanel.refresh(getSelectedWrapper());
            timeoutsPanel.refresh(getSelectedWrapper());
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
        connectedFilesPanel.refresh(getSelectedWrapper());
        timeoutsPanel.refresh(getSelectedWrapper());
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
                group = new DefaultMutableTreeNode(new DefinitionGroup(wrapper.getServerDefinition()));
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
        } else if (tree.getSelectionPath() == null) {
            // Nothing has ever been selected: select the first server so the Log, Timeouts and
            // Connected Files tabs have a subject as soon as the tool window opens. Without this
            // every tab renders its empty state even though a server is running.
            selectFirstServer();
        }
    }

    /**
     * Selects the first server leaf in the tree, if there is one.
     */
    private void selectFirstServer() {
        for (int row = 0; row < tree.getRowCount(); row++) {
            TreePath path = tree.getPathForRow(row);
            Object userObject = ((DefaultMutableTreeNode) path.getLastPathComponent()).getUserObject();
            if (userObject instanceof ServerLeaf) {
                tree.setSelectionPath(path);
                return;
            }
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

    /**
     * Selects {@code wrapper}'s node in the tree, which in turn shows its console, connected files and
     * timeouts via the tree selection listener installed in the constructor. Adds {@code wrapper} to
     * {@link #knownWrappers} first in case this panel has not ticked since the server started, so the
     * node exists to select.
     *
     * <p>The one entry point {@link org.wso2.lsp4intellij.statusbar.LSPServerStatusWidget} uses to
     * jump here for a specific server, so it never has to reach into this panel's tree or model.
     * Must be called on the EDT, like every other method on this Swing component.
     */
    public void selectServer(@NotNull LanguageServerWrapper wrapper) {
        knownWrappers.add(wrapper);
        rebuildTree();
        selectWrapper(wrapper);
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
        BorderLayout layout = (BorderLayout) logPanel.getLayout();
        Component current = layout.getLayoutComponent(BorderLayout.CENTER);
        if (current != null) {
            logPanel.remove(current);
        }
        logPanel.add(component, BorderLayout.CENTER);
        logPanel.revalidate();
        logPanel.repaint();
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
    /**
     * Renders tree nodes with their short label and carries the long form as the node's tooltip, so
     * the full command line and project path stay reachable without widening the tree.
     */
    private static final class ServerTreeRenderer extends ColoredTreeCellRenderer {

        @Override
        public void customizeCellRenderer(@NotNull JTree tree, Object value, boolean selected,
                boolean expanded, boolean leaf, int row, boolean hasFocus) {
            Object userObject = value instanceof DefaultMutableTreeNode node ? node.getUserObject() : null;
            if (userObject instanceof DefinitionGroup group) {
                append(group.toString());
                setToolTipText(group.tooltip());
            } else if (userObject instanceof ServerLeaf serverLeaf) {
                append(serverLeaf.toString());
                setToolTipText(serverLeaf.tooltip());
            } else if (userObject != null) {
                append(userObject.toString());
                setToolTipText(null);
            }
        }
    }

    /**
     * A tree group node for one server definition. Renders as the definition's short display name;
     * its {@link #toString()} on a {@code RawCommandServerDefinition} would otherwise be the whole
     * command line, which is what the definition's own {@code toString()} produces.
     *
     * @param definition the server definition this group node stands for
     */
    private record DefinitionGroup(@NotNull LanguageServerDefinition definition) {

        @Override
        public String toString() {
            return definition.getDisplayName();
        }

        /**
         * The definition's full description, shown on hover.
         */
        @NotNull
        String tooltip() {
            return definition.toString();
        }
    }

    private record ServerLeaf(@NotNull LanguageServerWrapper wrapper) {

        /**
         * The project's name rather than its full root path: the path is too long for a tree that
         * occupies a quarter of the tool window, and it is available as the node's tooltip.
         */
        @Override
        public String toString() {
            String projectName = wrapper.getProject() == null ? null : wrapper.getProject().getName();
            if (projectName != null && !projectName.isEmpty()) {
                return projectName;
            }
            String rootPath = wrapper.getProjectRootPath();
            return rootPath != null ? rootPath : wrapper.getServerDefinition().getDisplayName();
        }

        /**
         * The full project root path, shown on hover.
         */
        @Nullable
        String tooltip() {
            return wrapper.getProjectRootPath();
        }
    }

    /**
     * "Connected Files" tab content: the files connected to whichever server is selected in the tree,
     * replacing what used to be the {@code ShowConnectedFiles} modal dialog on the status bar widget.
     * Refreshed via {@link #refresh(LanguageServerWrapper)}, called from the tree selection listener
     * and from every {@link #flush()} tick so the list does not go stale while the selection has not
     * changed.
     *
     * <p>Each entry is rendered as its file name (the part someone actually scans for) with the
     * containing directory as dimmed secondary text, rather than the full URI
     * {@link LanguageServerWrapper#getConnectedFiles()} returns - the same primary/secondary split the
     * IDE's own file lists use. Entries are sorted so the list does not reorder itself on every
     * refresh, and a {@link ListSpeedSearch} lets someone jump to a file by typing part of its name.
     */
    private static final class ConnectedFilesPanel extends JPanel {

        private static final String NO_SERVER_TEXT = "Select a server to see its connected files.";
        private static final String NO_FILES_TEXT = "No files connected.";

        private final DefaultListModel<String> model = new DefaultListModel<>();
        private final JBList<String> list;
        private List<String> shown = List.of();

        ConnectedFilesPanel() {
            super(new BorderLayout());
            list = new JBList<>(model);
            list.setCellRenderer(new ConnectedFileRenderer());
            list.getEmptyText().setText(NO_SERVER_TEXT);
            ListSpeedSearch.installOn(list);
            add(new JBScrollPane(list), BorderLayout.CENTER);
        }

        /**
         * Re-reads the selected server's connected files. Called on every flush tick, so the model is
         * only rebuilt when the file set actually changed: rebuilding it unconditionally would discard
         * the user's list selection and any in-progress speed search ten times a second.
         */
        void refresh(@Nullable LanguageServerWrapper wrapper) {
            list.getEmptyText().setText(wrapper == null ? NO_SERVER_TEXT : NO_FILES_TEXT);
            List<String> files = wrapper == null
                    ? List.of()
                    : wrapper.getConnectedFiles().stream().sorted().toList();
            if (files.equals(shown)) {
                return;
            }
            shown = files;
            model.clear();
            files.forEach(model::addElement);
        }

        private static final class ConnectedFileRenderer extends ColoredListCellRenderer<String> {

            @Override
            protected void customizeCellRenderer(@NotNull JList<? extends String> list, String value, int index,
                    boolean selected, boolean hasFocus) {
                int slash = value.lastIndexOf('/');
                String name = slash < 0 ? value : value.substring(slash + 1);
                String directory = slash < 0 ? null : value.substring(0, slash);
                append(name);
                if (directory != null && !directory.isEmpty()) {
                    append("  " + directory, SimpleTextAttributes.GRAYED_ATTRIBUTES);
                }
            }
        }
    }

    /**
     * "Timeouts" tab content: the {@link TimeoutStats} counters for whichever server is selected in
     * the tree, replacing what used to be the {@code ShowTimeouts} modal dialog on the status bar
     * widget. A table rather than formatted text so the failure rate can be colored per row - gray
     * for a request kind nothing has exercised yet, green once every call has succeeded, red once any
     * has failed - the same at-a-glance signal the old dialog gave with inline HTML, without building
     * HTML by hand.
     *
     * <p>Refreshed via {@link #refresh(LanguageServerWrapper)}, called from the tree selection
     * listener and from every {@link #flush()} tick so the counters do not go stale while the
     * selection has not changed.
     */
    private static final class TimeoutsPanel extends JPanel {

        private static final String[] COLUMNS = {"Request", "Success", "Failures", "Total", "Failure Rate"};

        private final List<Timeouts> rows = List.of(Timeouts.values());
        private final Object[][] data = new Object[rows.size()][COLUMNS.length];
        private final AbstractTableModel model = new AbstractTableModel() {
            @Override
            public int getRowCount() {
                return rows.size();
            }

            @Override
            public int getColumnCount() {
                return COLUMNS.length;
            }

            @Override
            public String getColumnName(int column) {
                return COLUMNS[column];
            }

            @Override
            public Object getValueAt(int row, int column) {
                return data[row][column];
            }

            @Override
            public Class<?> getColumnClass(int column) {
                return column == 0 ? String.class : Integer.class;
            }
        };

        TimeoutsPanel() {
            super(new BorderLayout());
            for (int row = 0; row < rows.size(); row++) {
                data[row][0] = displayName(rows.get(row));
                data[row][1] = 0;
                data[row][2] = 0;
                data[row][3] = 0;
            }
            JBTable table = new JBTable(model);
            table.setStriped(true);
            table.setShowGrid(false);
            table.getColumnModel().getColumn(0).setPreferredWidth(140);
            DefaultTableCellRenderer centered = new DefaultTableCellRenderer();
            centered.setHorizontalAlignment(SwingConstants.CENTER);
            table.getColumnModel().getColumn(1).setCellRenderer(centered);
            table.getColumnModel().getColumn(2).setCellRenderer(centered);
            table.getColumnModel().getColumn(3).setCellRenderer(centered);
            table.getColumnModel().getColumn(4).setCellRenderer(new FailureRateRenderer());
            centerHeadersFromColumn(table, 1);
            add(new JBScrollPane(table), BorderLayout.CENTER);
        }

        /**
         * Centres the header text of every column from {@code firstColumn} on, matching the centred
         * cell renderers below them. Wraps whatever renderer the look and feel already installed
         * rather than replacing it, so the header keeps its native painting and only the alignment
         * changes.
         */
        private static void centerHeadersFromColumn(@NotNull JBTable table, int firstColumn) {
            TableCellRenderer original = table.getTableHeader().getDefaultRenderer();
            table.getTableHeader().setDefaultRenderer((tbl, value, selected, focused, row, column) -> {
                Component component =
                        original.getTableCellRendererComponent(tbl, value, selected, focused, row, column);
                if (component instanceof JLabel label) {
                    label.setHorizontalAlignment(column >= firstColumn ? SwingConstants.CENTER : SwingConstants.LEFT);
                }
                return component;
            });
        }

        void refresh(@Nullable LanguageServerWrapper wrapper) {
            TimeoutStats stats = wrapper == null ? null : wrapper.getTimeoutStats();
            for (int row = 0; row < rows.size(); row++) {
                Timeouts timeout = rows.get(row);
                int success = stats == null ? 0 : stats.getSuccessCount(timeout);
                int failures = stats == null ? 0 : stats.getFailureCount(timeout);
                data[row][1] = success;
                data[row][2] = failures;
                data[row][3] = success + failures;
            }
            model.fireTableDataChanged();
        }

        @NotNull
        private static String displayName(@NotNull Timeouts timeout) {
            String name = timeout.name();
            return name.charAt(0) + name.substring(1).toLowerCase();
        }

        /**
         * The "Failure Rate" column has no backing data of its own - it is computed from the
         * "Success"/"Failures" columns of the same row at paint time - so it is rendered rather than
         * modeled, coloring the text to draw the eye to whichever request kind is actually failing.
         */
        private static final class FailureRateRenderer extends DefaultTableCellRenderer {

            FailureRateRenderer() {
                setHorizontalAlignment(SwingConstants.CENTER);
            }

            @Override
            public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected,
                    boolean hasFocus, int row, int column) {
                int success = (Integer) table.getModel().getValueAt(row, 1);
                int failures = (Integer) table.getModel().getValueAt(row, 2);
                int total = success + failures;
                String text = total == 0 ? "—" : String.format("%.0f%%", 100.0 * failures / total);
                Component component = super.getTableCellRendererComponent(table, text, isSelected, hasFocus, row,
                        column);
                if (!isSelected) {
                    component.setForeground(total == 0 ? JBColor.GRAY : failures == 0 ? JBColor.GREEN : JBColor.RED);
                }
                return component;
            }
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
