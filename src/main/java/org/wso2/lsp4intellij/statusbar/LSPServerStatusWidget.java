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
package org.wso2.lsp4intellij.statusbar;

import com.intellij.ide.DataManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.ui.popup.ListPopup;
import com.intellij.openapi.wm.StatusBar;
import com.intellij.openapi.wm.StatusBarWidget;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentManager;
import com.intellij.util.Consumer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.wso2.lsp4intellij.Lsp4IntellijBundle;
import org.wso2.lsp4intellij.client.languageserver.ServerStatus;
import org.wso2.lsp4intellij.client.languageserver.wrapper.LanguageServerWrapper;
import org.wso2.lsp4intellij.contributors.icon.LSPDefaultIconProvider;
import org.wso2.lsp4intellij.inspector.LspInspectorPanel;
import org.wso2.lsp4intellij.inspector.LspInspectorToolWindowFactory;
import org.wso2.lsp4intellij.services.LspServerManager;
import org.wso2.lsp4intellij.utils.GUIUtils;

import java.awt.Component;
import java.awt.Dimension;
import java.awt.Point;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.swing.Icon;

public class LSPServerStatusWidget implements StatusBarWidget {

    private final Project project;
    private final String projectName;

    LSPServerStatusWidget(Project project) {
        this.project = project;
        this.projectName = project.getName();
    }

    public IconPresentation getPresentation() {
        return new IconPresentation();
    }

    @Override
    public void install(@NotNull StatusBar statusBar) {
    }

    /**
     * Called whenever any server's status changes, to repaint the widget. The widget aggregates over
     * every server running for the project each time it repaints (see {@link IconPresentation}), so
     * which status transitioned is not needed here - only that something did.
     *
     * @param status unused; kept so {@code LanguageServerWrapper.setStatus}'s existing call site does
     *         not need to change
     */
    public void setStatus(ServerStatus status) {
        updateWidget();
    }

    private void updateWidget() {
        WindowManager manager = WindowManager.getInstance();
        if (manager != null && project != null && !project.isDisposed()) {
            StatusBar statusBar = manager.getStatusBar(project);
            if (statusBar != null) {
                statusBar.updateWidget(ID());
            }
        }
    }

    @Override
    public void dispose() {}

    @NotNull
    @Override
    public String ID() {
        return "LSP";
    }

    public Project getProject() {
        return project;
    }

    @NotNull
    private Set<LanguageServerWrapper> allWrappers() {
        LspServerManager manager = LspServerManager.getInstanceIfCreated(project);
        return manager == null ? Set.of() : manager.allWrappers();
    }

    private class IconPresentation implements StatusBarWidget.IconPresentation {
        @Nullable
        @Override
        public Icon getIcon() {
            Map<LanguageServerWrapper, ServerStatus> statuses = new LinkedHashMap<>();
            for (LanguageServerWrapper wrapper : allWrappers()) {
                statuses.put(wrapper, wrapper.getStatus());
            }
            if (statuses.isEmpty()) {
                return new LSPDefaultIconProvider().getStatusIcons().get(ServerStatus.STOPPED);
            }
            ServerStatus aggregate = ServerStatusAggregator.aggregate(statuses.values());
            LanguageServerWrapper representative = statuses.entrySet().stream()
                    .filter(entry -> entry.getValue() == aggregate)
                    .map(Map.Entry::getKey)
                    .findFirst()
                    .orElseThrow();
            return GUIUtils.getIconProviderFor(representative.getServerDefinition())
                    .getStatusIcons().get(aggregate);
        }

        @Nullable
        @Override
        public Consumer<MouseEvent> getClickConsumer() {
            return (MouseEvent t) -> {
                JBPopupFactory.ActionSelectionAid mnemonics = JBPopupFactory.ActionSelectionAid.MNEMONICS;
                Component component = t.getComponent();
                Set<LanguageServerWrapper> wrappers = allWrappers();
                DefaultActionGroup group = wrappers.size() <= 1
                        ? flatActionGroup(wrappers.stream().findFirst().orElse(null))
                        : perServerActionGroup(wrappers);

                String title = Lsp4IntellijBundle.message("lsp.server.actions.title");

                DataContext context = DataManager.getInstance().getDataContext(component);
                ListPopup popup = JBPopupFactory.getInstance()
                        .createActionGroupPopup(title, group, context, mnemonics, true);
                Dimension dimension = popup.getContent().getPreferredSize();
                Point at = new Point(0, -dimension.height);
                popup.show(new RelativePoint(t.getComponent(), at));
            };
        }

        /**
         * The popup for zero or one server: flat, matching the widget's original layout as closely as
         * possible rather than wrapping the single server in its own submenu.
         */
        @NotNull
        private DefaultActionGroup flatActionGroup(@Nullable LanguageServerWrapper wrapper) {
            List<AnAction> actions = new ArrayList<>();
            actions.add(new Restart(wrapper));
            OpenInspectorAction openInspector = openInspectorActionFor(wrapper);
            if (openInspector != null) {
                actions.add(openInspector);
            }
            return new DefaultActionGroup(actions);
        }

        /**
         * The popup for more than one server: one submenu per server, each offering the same actions
         * the flat popup would for that server alone.
         */
        @NotNull
        private DefaultActionGroup perServerActionGroup(@NotNull Set<LanguageServerWrapper> wrappers) {
            DefaultActionGroup group = new DefaultActionGroup();
            wrappers.stream()
                    .sorted(Comparator.comparing(w -> w.getServerDefinition().toString()))
                    .forEach(wrapper -> {
                        DefaultActionGroup serverGroup =
                                new DefaultActionGroup(wrapper.getServerDefinition().toString(), true);
                        serverGroup.add(new Restart(wrapper));
                        OpenInspectorAction openInspector = openInspectorActionFor(wrapper);
                        if (openInspector != null) {
                            serverGroup.add(openInspector);
                        }
                        group.add(serverGroup);
                    });
            return group;
        }

        /**
         * Returns an action that opens the LSP Inspector tool window and selects {@code wrapper}, or
         * {@code null} if the consumer plugin never registered a tool window under
         * {@link LspInspectorToolWindowFactory#TOOL_WINDOW_ID} - the action must not be shown at all in
         * that case, never thrown for.
         */
        @Nullable
        private OpenInspectorAction openInspectorActionFor(@Nullable LanguageServerWrapper wrapper) {
            ToolWindow toolWindow = ToolWindowManager.getInstance(project)
                    .getToolWindow(LspInspectorToolWindowFactory.TOOL_WINDOW_ID);
            return toolWindow == null ? null : new OpenInspectorAction(wrapper);
        }

        class Restart extends AnAction implements DumbAware {

            private final LanguageServerWrapper wrapper;

            Restart(@Nullable LanguageServerWrapper wrapper) {
                super(Lsp4IntellijBundle.message("action.restart"),
                      Lsp4IntellijBundle.message("action.restart.description"), null);
                this.wrapper = wrapper;
            }

            @Override
            public void actionPerformed(@NotNull AnActionEvent anActionEvent) {
                if (wrapper != null) {
                    wrapper.restart();
                }
            }
        }

        /**
         * Opens the LSP Inspector tool window and selects the server this action was built for, via
         * {@link LspInspectorPanel#selectServer(LanguageServerWrapper)}. Only ever constructed by
         * {@link #openInspectorActionFor(LanguageServerWrapper)}, which already confirmed the tool
         * window exists.
         */
        class OpenInspectorAction extends AnAction implements DumbAware {

            private final LanguageServerWrapper wrapper;

            OpenInspectorAction(@Nullable LanguageServerWrapper wrapper) {
                super(Lsp4IntellijBundle.message("action.open.inspector"),
                      Lsp4IntellijBundle.message("action.open.inspector.description"), null);
                this.wrapper = wrapper;
            }

            @Override
            public void actionPerformed(@NotNull AnActionEvent e) {
                ToolWindow toolWindow = ToolWindowManager.getInstance(project)
                        .getToolWindow(LspInspectorToolWindowFactory.TOOL_WINDOW_ID);
                if (toolWindow == null) {
                    return;
                }
                toolWindow.activate(() -> {
                    if (wrapper == null) {
                        return;
                    }
                    ContentManager contentManager = toolWindow.getContentManager();
                    Content content = contentManager.getSelectedContent();
                    if (content == null && contentManager.getContentCount() > 0) {
                        content = contentManager.getContent(0);
                    }
                    if (content != null && content.getComponent() instanceof LspInspectorPanel panel) {
                        panel.selectServer(wrapper);
                    }
                });
            }
        }

        @Override
        public String getTooltipText() {
            Set<LanguageServerWrapper> wrappers = allWrappers();
            if (wrappers.isEmpty()) {
                return Lsp4IntellijBundle.message("tooltip.language.server.project", projectName);
            } else if (wrappers.size() == 1) {
                LanguageServerWrapper wrapper = wrappers.iterator().next();
                return Lsp4IntellijBundle.message("tooltip.language.server.extension.project",
                        wrapper.getServerDefinition().ext, projectName);
            } else {
                return Lsp4IntellijBundle.message("tooltip.language.server.multiple.project",
                        wrappers.size(), projectName);
            }
        }
    }
}
