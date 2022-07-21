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
import org.wso2.lsp4intellij.IntellijLanguageClient;
import org.wso2.lsp4intellij.client.languageserver.wrapper.LanguageServerWrapper;
import org.wso2.lsp4intellij.utils.FileUtils;

import java.util.Set;

public class LSPProjectManagerListener implements ProjectManagerListener {

    private static final Logger LOG = Logger.getInstance(LSPProjectManagerListener.class);

    @Override
    public void projectOpened(@Nullable final Project project) {
        // Todo
    }

    @Override
    public void projectClosing(@NotNull Project project) {
        // Removes all the attached LSP status widgets before closing a project. Otherwise the old status widget will
        // not be removed when opening a new project in the same project window.
            Set<LanguageServerWrapper> languageServerWrappers = IntellijLanguageClient.getProjectToLanguageWrappers().get(FileUtils.projectToUri(project));
            if (languageServerWrappers == null) {
                // nothing to do
                return;
            }
            languageServerWrappers.forEach(wrapper -> {
                wrapper.removeWidget();
                IntellijLanguageClient.removeWrapper(wrapper);
            });
    }
}
