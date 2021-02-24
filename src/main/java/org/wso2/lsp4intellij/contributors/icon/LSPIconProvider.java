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
package org.wso2.lsp4intellij.contributors.icon;

import org.eclipse.lsp4j.CompletionItemKind;
import org.eclipse.lsp4j.SymbolInformation;
import org.eclipse.lsp4j.SymbolKind;
import org.wso2.lsp4intellij.client.languageserver.ServerStatus;
import org.wso2.lsp4intellij.client.languageserver.serverdefinition.LanguageServerDefinition;

import java.util.Map;
import javax.swing.*;

public abstract class LSPIconProvider {

    public abstract Icon getCompletionIcon(CompletionItemKind kind);

    public abstract Map<ServerStatus, Icon> getStatusIcons();

    public abstract Icon getSymbolIcon(SymbolKind kind);

    public abstract boolean isSpecificFor(LanguageServerDefinition serverDefinition);

    /**
     * Return icon for symbol based on the symbol information. This method must only be used if you need more than the
     * symbol kind to decide the icon which needs to be used. Otherwise always prefer implementing
     * {@link #getSymbolIcon(SymbolKind)}.
     *
     * @return default implementation delegates to {@link #getSymbolIcon(SymbolKind)}.
     */
    public Icon getSymbolIcon(SymbolInformation information) {
        return getSymbolIcon(information.getKind());
    }
}
