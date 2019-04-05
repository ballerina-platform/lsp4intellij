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
import com.intellij.navigation.NavigationItem;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

/**
 * The symbol provider implementation for LSP client.
 *
 * @author gayanper
 */
class LSPSymbolContributor implements ChooseByNameContributor {

  private WorkspaceSymbolProvider workspaceSymbolProvider = new WorkspaceSymbolProvider();

  @NotNull
  @Override
  public String[] getNames(Project project, boolean includeNonProjectItems) {
    return workspaceSymbolProvider.workspaceSymbols("", project).stream()
        .map(NavigationItem::getName)
        .toArray(i -> new String[i]);
  }

  @NotNull
  @Override
  public NavigationItem[] getItemsByName(String name, String pattern, Project project,
      boolean includeNonProjectItems) {
    return workspaceSymbolProvider.workspaceSymbols(name, project).toArray(new NavigationItem[0]);
  }
}
