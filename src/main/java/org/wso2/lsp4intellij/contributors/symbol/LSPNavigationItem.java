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

import com.intellij.navigation.ItemPresentation;
import com.intellij.navigation.NavigationItem;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Objects;

/**
 * LSP implementation of NavigationItem for intellij
 *
 * @author gayanper
 */
public class LSPNavigationItem extends OpenFileDescriptor implements NavigationItem {

    private ItemPresentation presentation;

    LSPNavigationItem(String name, String location, Icon icon, @NotNull Project project, @NotNull VirtualFile file,
            int logicalLine, int logicalColumn) {
        super(project, file, logicalLine, logicalColumn);
        presentation = new LSPItemPresentation(location, name, icon);
    }

    @Nullable
    @Override
    public String getName() {
        return presentation.getPresentableText();
    }

    @Nullable
    @Override
    public ItemPresentation getPresentation() {
        return presentation;
    }

    @Override
    public boolean equals(Object obj) {
        if(obj instanceof LSPNavigationItem) {
            LSPNavigationItem other = (LSPNavigationItem) obj;
            return this.getLine() == other.getLine() && this.getColumn() == other.getColumn() &&
                    Objects.equals(this.getName(), other.getName());
        }
        return false;
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.getLine(), this.getColumn(), this.getName());
    }

    private class LSPItemPresentation implements ItemPresentation {

        private String location;
        private String presentableText;
        private Icon icon;

        LSPItemPresentation(String location, String presentableText, Icon icon) {
            this.location = location;
            this.presentableText = presentableText;
            this.icon = icon;
        }

        @Nullable
        @Override
        public String getPresentableText() {
            return presentableText;
        }

        @Nullable
        @Override
        public String getLocationString() {
            return location;
        }

        @Nullable
        @Override
        public Icon getIcon(boolean unused) {
            return icon;
        }
    }
}
