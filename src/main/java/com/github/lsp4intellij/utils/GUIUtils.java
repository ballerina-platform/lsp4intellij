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
package com.github.lsp4intellij.utils;

import com.github.lsp4intellij.client.languageserver.serverdefinition.LanguageServerDefinition;
import com.github.lsp4intellij.contributors.icon.LSPDefaultIconProvider;
import com.github.lsp4intellij.contributors.icon.LSPIconProvider;

public class GUIUtils {

    /**
     * Returns a suitable LSPIconProvider given a ServerDefinition
     *
     * @param serverDefinition The serverDefinition
     * @return The LSPIconProvider, or LSPDefaultIconProvider if none are found
     */
    public static LSPIconProvider getIconProviderFor(LanguageServerDefinition serverDefinition) {
        return new LSPDefaultIconProvider();
    }
}
