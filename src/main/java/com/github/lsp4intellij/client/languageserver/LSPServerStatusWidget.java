package com.github.lsp4intellij.client.languageserver;

import com.github.lsp4intellij.client.languageserver.wrapper.LanguageServerWrapper;
import com.github.lsp4intellij.requests.Timeouts;
import com.github.lsp4intellij.utils.ApplicationUtils;
import com.github.lsp4intellij.utils.GUIUtils;
import com.intellij.ide.DataManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.ui.popup.ListPopup;
import com.intellij.openapi.wm.StatusBar;
import com.intellij.openapi.wm.StatusBarWidget;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.util.Consumer;
import org.apache.commons.lang3.tuple.Pair;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.swing.*;

public class LSPServerStatusWidget implements StatusBarWidget {
    private static Map<Project, List<String>> widgetIDs = new HashMap<>();
    private Map<Timeouts, Pair<Integer, Integer>> timeouts = new HashMap<>();
    LanguageServerWrapper wrapper;
    private String ext;
    private Project project;
    private String projectName;
    private Map<ServerStatus, Icon> icons;
    private ServerStatus status = ServerStatus.STOPPED;

    LSPServerStatusWidget(LanguageServerWrapper wrapper) {
        this.wrapper = wrapper;
        this.ext = wrapper.getServerDefinition().ext;
        this.project = wrapper.getProject();
        this.projectName = project.getName();
        this.icons = GUIUtils.getIconProviderFor(wrapper.getServerDefinition()).getStatusIcons();

        for (Timeouts t : Timeouts.values()) {
            timeouts.put(t, new Pair<>(0, 0));
        }
    }

    /**
     * Creates a widget given a LanguageServerWrapper and adds it to the status bar
     *
     * @param wrapper The wrapper
     * @return The widget
     */
    public static LSPServerStatusWidget createWidgetFor(LanguageServerWrapper wrapper) {
        LSPServerStatusWidget widget = new LSPServerStatusWidget(wrapper);
        Project project = wrapper.getProject();
        StatusBar statusBar = WindowManager.getInstance().getStatusBar(project);

        List<String> position = widgetIDs.computeIfAbsent(project, k -> new ArrayList<String>("Position");

        statusBar.addWidget(widget, "before " + widgetIDs.get(project).get(0));
        widgetIDs.get(project).add(0, widget.ID());
        return widget;
    }

    public static void removeWidgetID(LSPServerStatusWidget widget) {
        Project project = widget.wrapper.getProject();
        widgetIDs.get(project).remove(widget.ID());
    }

    public void notifyResult(Timeouts timeout, Boolean success) {
        Pair<Integer, Integer> oldValue = timeouts.get(timeout);
        if (success) {
            timeouts.replace(timeout, new Pair<>(oldValue.getKey() + 1, oldValue.getValue()));
        } else {
            timeouts.replace(timeout, new Pair<>(oldValue.getKey(), oldValue.getValue() + 1));
        }
    }

    public IconPresentation getPresentation(@NotNull PlatformType type) {

        return new IconPresentation() {

            @NotNull
            @Override
            public Icon getIcon() {
                return icons.get(status);
            }

            @NotNull
            @Override
            public Consumer<MouseEvent> getClickConsumer() {
                return (MouseEvent t) -> {
                    JBPopupFactory.ActionSelectionAid mnemonics = JBPopupFactory.ActionSelectionAid.MNEMONICS;
                    Component component = t.getComponent();
                    List<AnAction> actions = new ArrayList<>();
                    if (wrapper.getStatus() == ServerStatus.STARTED) {
                        actions.add(new ShowConnectedFiles());
                    }
                    actions.add(new ShowTimeouts());
                    String title = "Server actions";
                    DataContext context = DataManager.getInstance().getDataContext(component);
                    DefaultActionGroup group = new DefaultActionGroup(actions);
                    ListPopup popup = JBPopupFactory.getInstance()
                            .createActionGroupPopup(title, group, context, mnemonics, true);
                    Dimension dimension = popup.getContent().getPreferredSize();
                    Point at = new Point(0, -dimension.height);
                    popup.show(new RelativePoint(t.getComponent(), at));
                };
            }

            class ShowConnectedFiles extends AnAction {
                @Override
                public void actionPerformed(AnActionEvent e) {
                    //Todo - revisit
                    Messages.showInfoMessage("Connected files :\n" + wrapper.getConnectedFiles().toString("\n"),
                            "Connected Files");
                }
            }

            class ShowTimeouts extends AnAction {
                @Override
                public void actionPerformed(AnActionEvent e) {
                    //Todo - Implement
                }
            }

            @Override
            public String getTooltipText() {
                return "Language server for extension " + ext + ", project " + projectName;
            }

        };

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
            LSPServerStatusWidget.removeWidgetID(this);
            if (statusBar != null)
                ApplicationUtils.invokeLater(() -> statusBar.removeWidget(ID()));

        }
    }

    @NotNull
    @Override
    public String ID() {
        return projectName + "_" + ext;
    }
}
