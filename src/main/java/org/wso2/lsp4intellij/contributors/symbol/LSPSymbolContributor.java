/*
 * Copyright (c) 2018-2019, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
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
package org.wso2.lsp4intellij.contributors.symbol;

import com.intellij.navigation.ChooseByNameContributor;
import com.intellij.navigation.ChooseByNameContributorEx;
import com.intellij.navigation.NavigationItem;
import com.intellij.openapi.project.Project;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.util.Processor;
import com.intellij.util.indexing.FindSymbolParameters;
import com.intellij.util.indexing.IdFilter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * The symbol provider implementation for LSP client.
 *
 * @author gayanper
 */
public class LSPSymbolContributor implements ChooseByNameContributorEx {

    private WorkspaceSymbolProvider workspaceSymbolProvider = new WorkspaceSymbolProvider();

    @Override
    public void processNames(@NotNull Processor<String> processor, @NotNull GlobalSearchScope globalSearchScope, @Nullable IdFilter idFilter) {
        workspaceSymbolProvider.workspaceSymbols("", globalSearchScope.getProject()).stream().map(NavigationItem::getName)
                .forEach(processor::process);
    }

    @Override
    public void processElementsWithName(@NotNull String s, @NotNull Processor<NavigationItem> processor, @NotNull FindSymbolParameters findSymbolParameters) {
        workspaceSymbolProvider.workspaceSymbols(s, findSymbolParameters.getProject()).forEach(processor::process);
    }

    @NotNull
    @Override
    public String[] getNames(Project project, boolean includeNonProjectItems) {
        return null;
    }

    @NotNull
    @Override
    public NavigationItem[] getItemsByName(String name, String pattern, Project project,
            boolean includeNonProjectItems) {
        return null;
    }
}
