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
package org.wso2.lsp4intellij.contributors.icon;

import com.intellij.icons.AllIcons;
import com.intellij.icons.AllIcons.Nodes;
import com.intellij.openapi.util.IconLoader;
import java.util.HashMap;
import java.util.Map;
import javax.swing.Icon;
import org.eclipse.lsp4j.CompletionItemKind;
import org.eclipse.lsp4j.SymbolKind;
import org.wso2.lsp4intellij.client.languageserver.ServerStatus;
import org.wso2.lsp4intellij.client.languageserver.serverdefinition.LanguageServerDefinition;

public class LSPDefaultIconProvider extends LSPIconProvider {

    private static Icon STARTED = IconLoader.getIcon("/images/started.png");
    private static Icon STARTING = IconLoader.getIcon("/images/starting.png");
    private static Icon STOPPED = IconLoader.getIcon("/images/stopped.png");

    public static Icon getCompletionIcon(CompletionItemKind kind) {

        if (kind == CompletionItemKind.Class) {
            return Nodes.Class;
        } else if (kind == CompletionItemKind.Color) {
            return null;
        } else if (kind == CompletionItemKind.Constructor) {
            return null;
        } else if (kind == CompletionItemKind.Enum) {
            return Nodes.Class;
        } else if (kind == CompletionItemKind.Field) {
            return Nodes.Field;
        } else if (kind == CompletionItemKind.File) {
            return AllIcons.FileTypes.Any_type;
        } else if (kind == CompletionItemKind.Function) {
            return Nodes.Function;
        } else if (kind == CompletionItemKind.Interface) {
            return Nodes.Interface;
        } else if (kind == CompletionItemKind.Keyword) {
            return Nodes.UpLevel;
        } else if (kind == CompletionItemKind.Method) {
            return Nodes.Method;
        } else if (kind == CompletionItemKind.Module) {
            return Nodes.Module;
        } else if (kind == CompletionItemKind.Property) {
            return Nodes.Property;
        } else if (kind == CompletionItemKind.Reference) {
            return Nodes.MethodReference;
        } else if (kind == CompletionItemKind.Snippet) {
            return Nodes.Static;
        } else if (kind == CompletionItemKind.Text) {
            return AllIcons.FileTypes.Text;
        } else if (kind == CompletionItemKind.Unit) {
            return Nodes.Artifact;
        } else if (kind == CompletionItemKind.Value) {
            return Nodes.DataSource;
        } else if (kind == CompletionItemKind.Variable) {
            return Nodes.Variable;
        } else {
            return null;
        }
    }

    public static Icon getSymbolIcon(SymbolKind kind) {
        switch (kind) {
            case Field:
            case EnumMember:
                return Nodes.Field;
            case Method:
                return Nodes.Method;
            case Variable:
                return Nodes.Variable;
            case Class:
                return Nodes.Class;
            case Constructor:
                return Nodes.ClassInitializer;
            case Enum:
                return Nodes.Enum;
            default:
                return Nodes.Tag;
        }
    }
    public static Map<ServerStatus, Icon> getStatusIcons() {
        Map<ServerStatus, Icon> statusIconMap = new HashMap<>();
        statusIconMap.put(ServerStatus.STOPPED, STOPPED);
        statusIconMap.put(ServerStatus.STARTING, STARTING);
        statusIconMap.put(ServerStatus.STARTED, STARTED);
        return statusIconMap;
    }

    @Override
    public boolean isSpecificFor(LanguageServerDefinition serverDefinition) {
        return false;
    }
}
