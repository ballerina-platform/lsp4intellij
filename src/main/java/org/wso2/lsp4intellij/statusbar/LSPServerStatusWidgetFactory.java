package org.wso2.lsp4intellij.statusbar;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.StatusBar;
import com.intellij.openapi.wm.StatusBarWidget;
import com.intellij.openapi.wm.StatusBarWidgetFactory;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;

public class LSPServerStatusWidgetFactory implements StatusBarWidgetFactory {
    private final Map<Project, LSPServerStatusWidget> widgetForProject = new HashMap<>();

    @Override
    public @NonNls
    @NotNull
    String getId() {
        return "LSP";
    }

    @Override
    public @Nls
    @NotNull
    String getDisplayName() {
        return "Language Server";
    }

    @Override
    public boolean isAvailable(@NotNull Project project) {
        return true;
    }

    @Override
    public @NotNull
    StatusBarWidget createWidget(@NotNull Project project) {
        if (widgetForProject.containsKey(project)) {
            return widgetForProject.get(project);
        }
        LSPServerStatusWidget widget = new LSPServerStatusWidget(project);

        widgetForProject.put(project, widget);
        return widget;
    }

    @Override
    public void disposeWidget(@NotNull StatusBarWidget statusBarWidget) {
        if (statusBarWidget instanceof LSPServerStatusWidget) {
            widgetForProject.remove(((LSPServerStatusWidget) statusBarWidget).getProject());
        }
    }

    @Override
    public boolean canBeEnabledOn(@NotNull StatusBar statusBar) {
        return true;
    }

    public LSPServerStatusWidget getWidget(Project project) {
        return widgetForProject.get(project);
    }
}
