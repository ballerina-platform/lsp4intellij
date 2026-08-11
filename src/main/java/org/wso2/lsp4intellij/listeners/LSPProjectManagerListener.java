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
package org.wso2.lsp4intellij.listeners;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManagerListener;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.wso2.lsp4intellij.services.LspServerManager;

public class LSPProjectManagerListener implements ProjectManagerListener {

    private static final Logger LOG = Logger.getInstance(LSPProjectManagerListener.class);

    @Override
    public void projectOpened(@Nullable final Project project) {
        // Todo
    }

    @Override
    public void projectClosing(@NotNull Project project) {
        // Disposes all the language server wrappers (and their attached LSP status widgets) before closing a
        // project. Otherwise the old status widget will not be removed when opening a new project in the same
        // project window.
        //
        // This runs before the project itself starts disposing, which is the one point in the wrapper lifecycle
        // where touching the project is known to be safe. LspServerManager.dispose() also runs automatically as
        // part of the project's own Disposer chain; calling it here explicitly first is redundant with that but
        // not harmful, since dispose() is idempotent.
        LspServerManager manager = LspServerManager.getInstanceIfCreated(project);
        if (manager != null) {
            manager.dispose();
        }
    }
}
