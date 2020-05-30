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
package org.wso2.lsp4intellij.contributors.symbol;

import com.intellij.ide.util.gotoByName.ChooseByNamePopup;
import com.intellij.navigation.ChooseByNameContributorEx;
import com.intellij.navigation.NavigationItem;
import com.intellij.openapi.project.Project;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.util.Processor;
import com.intellij.util.indexing.FindSymbolParameters;
import com.intellij.util.indexing.IdFilter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;

/**
 * The symbol provider implementation for LSP client.
 *
 * @author gayanper
 */
public class LSPSymbolContributor implements ChooseByNameContributorEx {

    private final WorkspaceSymbolProvider workspaceSymbolProvider = new WorkspaceSymbolProvider();

    @Override
    public void processNames(@NotNull Processor<? super String> processor, @NotNull GlobalSearchScope globalSearchScope, @Nullable IdFilter idFilter) {
        String queryString = Optional.ofNullable(globalSearchScope.getProject())
            .map(p -> p.getUserData(ChooseByNamePopup.CURRENT_SEARCH_PATTERN)).orElse("");

        workspaceSymbolProvider.workspaceSymbols(queryString, globalSearchScope.getProject()).stream()
            .filter(ni -> globalSearchScope.accept(ni.getFile()))
            .map(NavigationItem::getName)
            .forEach(processor::process);
    }

    @Override
    public void processElementsWithName(@NotNull String s, @NotNull Processor<? super NavigationItem> processor, @NotNull FindSymbolParameters findSymbolParameters) {
        workspaceSymbolProvider.workspaceSymbols(s, findSymbolParameters.getProject()).stream()
            .filter(ni -> findSymbolParameters.getSearchScope().accept(ni.getFile()))
            .forEach(processor::process);
    }
}
