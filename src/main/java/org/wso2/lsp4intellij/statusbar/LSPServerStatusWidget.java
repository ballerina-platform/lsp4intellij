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
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.ui.popup.ListPopup;
import com.intellij.openapi.wm.StatusBar;
import com.intellij.openapi.wm.StatusBarWidget;
import com.intellij.openapi.wm.StatusBarWidgetFactory;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.util.Consumer;
import org.apache.commons.lang3.tuple.MutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.wso2.lsp4intellij.client.languageserver.ServerStatus;
import org.wso2.lsp4intellij.client.languageserver.wrapper.LanguageServerWrapper;
import org.wso2.lsp4intellij.contributors.icon.LSPDefaultIconProvider;
import org.wso2.lsp4intellij.requests.Timeouts;
import org.wso2.lsp4intellij.utils.ApplicationUtils;
import org.wso2.lsp4intellij.utils.GUIUtils;

import java.awt.*;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.swing.*;

public class LSPServerStatusWidget implements StatusBarWidget {

    private final Map<Timeouts, Pair<Integer, Integer>> timeouts = new HashMap<>();
    private final Project project;
    private final String projectName;
    private ServerStatus status = ServerStatus.STOPPED;

    LSPServerStatusWidget(Project project) {
        this.project = project;
        this.projectName = project.getName();

        for (Timeouts t : Timeouts.values()) {
            timeouts.put(t, new MutablePair<>(0, 0));
        }
    }

    public void notifyResult(Timeouts timeout, Boolean success) {
        Pair<Integer, Integer> oldValue = timeouts.get(timeout);
        if (success) {
            timeouts.replace(timeout, new MutablePair<>(oldValue.getKey() + 1, oldValue.getValue()));
        } else {
            timeouts.replace(timeout, new MutablePair<>(oldValue.getKey(), oldValue.getValue() + 1));
        }
    }

    public IconPresentation getPresentation() {
        return new IconPresentation();
    }

    @Override
    public void install(@NotNull StatusBar statusBar) {
    }

    /**
     * Sets the status of the server
     *
     * @param status The new status
     */
    public void setStatus(ServerStatus status) {
        this.status = status;
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

    public void dispose() {
        WindowManager manager = WindowManager.getInstance();
        if (manager != null && project != null && !project.isDisposed()) {
            StatusBar statusBar = manager.getStatusBar(project);
            if (statusBar != null)
                ApplicationUtils.invokeLater(() -> {
                    StatusBarWidgetFactory factory = ServiceManager.getService(StatusBarWidgetFactory.class);
                    factory.disposeWidget(this);
                });
        }
    }

    @NotNull
    @Override
    public String ID() {
        return "LSP";
    }

    public Project getProject() {
        return project;
    }

    private class IconPresentation implements StatusBarWidget.IconPresentation {
        @Nullable
        @Override
        public Icon getIcon() {
            LanguageServerWrapper wrapper = LanguageServerWrapper.forProject(project);
            Map<ServerStatus, Icon> icons = new LSPDefaultIconProvider().getStatusIcons();
            if (wrapper != null) {
                icons = GUIUtils.getIconProviderFor(wrapper.getServerDefinition())
                        .getStatusIcons();
            }
            return icons.get(status);
        }

        @Nullable
        @Override
        public Consumer<MouseEvent> getClickConsumer() {
            return (MouseEvent t) -> {
                JBPopupFactory.ActionSelectionAid mnemonics = JBPopupFactory.ActionSelectionAid.MNEMONICS;
                Component component = t.getComponent();
                List<AnAction> actions = new ArrayList<>();
                if (LanguageServerWrapper.forProject(project).getStatus() == ServerStatus.INITIALIZED) {
                    actions.add(new ShowConnectedFiles());
                }
                actions.add(new ShowTimeouts());

                actions.add(new Restart());

                String title = "Server Actions";
                DataContext context = DataManager.getInstance().getDataContext(component);
                DefaultActionGroup group = new DefaultActionGroup(actions);
                ListPopup popup = JBPopupFactory.getInstance()
                        .createActionGroupPopup(title, group, context, mnemonics, true);
                Dimension dimension = popup.getContent().getPreferredSize();
                Point at = new Point(0, -dimension.height);
                popup.show(new RelativePoint(t.getComponent(), at));
            };
        }

        class ShowConnectedFiles extends AnAction implements DumbAware {
            ShowConnectedFiles() {
                super("&Show Connected Files", "Show the files connected to the server", null);
            }

            @Override
            public void actionPerformed(@NotNull AnActionEvent e) {
                StringBuilder connectedFiles = new StringBuilder("Connected files :");
                LanguageServerWrapper.forProject(project).getConnectedFiles().forEach(f -> connectedFiles.append(System.lineSeparator()).append(f));
                Messages.showInfoMessage(connectedFiles.toString(), "Connected Files");
            }
        }

        class ShowTimeouts extends AnAction implements DumbAware {
            ShowTimeouts() {
                super("&Show Timeouts", "Show the timeouts proportions of the server", null);
            }

            @Override
            public void actionPerformed(@NotNull AnActionEvent e) {
                StringBuilder message = new StringBuilder();
                message.append("<html>");
                message.append("Timeouts (failed requests) :<br>");
                timeouts.forEach((t, v) -> {
                    int timeouts = v.getRight();
                    message.append(t.name(), 0, 1).append(t.name().substring(1).toLowerCase()).append(" => ");
                    int total = v.getLeft() + timeouts;
                    if (total != 0) {
                        if (timeouts > 0) {
                            message.append("<font color=\"red\">");
                        }
                        message.append(timeouts).append("/").append(total).append(" (")
                                .append(100 * (double) timeouts / total).append("%)<br>");
                        if (timeouts > 0) {
                            message.append("</font>");
                        }
                    } else {
                        message.append("0/0 (0%)<br>");
                    }
                });
                message.append("</html>");
                Messages.showInfoMessage(message.toString(), "Timeouts");
            }
        }

        class Restart extends AnAction implements DumbAware {

            Restart() {
                super("&Restart", "Restarts the language server.", null);
            }

            @Override
            public void actionPerformed(@NotNull AnActionEvent anActionEvent) {
                LanguageServerWrapper.forProject(project).restart();
            }

        }

        @Override
        public String getTooltipText() {
            LanguageServerWrapper wrapper = LanguageServerWrapper.forProject(project);
            if (wrapper == null) {
                return "Language server, project " + projectName;
            } else {
                return "Language server for extension " + wrapper.getServerDefinition().ext + ", project " + projectName;
            }
        }
    }
}
