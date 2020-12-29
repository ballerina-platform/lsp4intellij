/*
 * Copyright (c) 2020, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
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
package org.wso2.lsp4intellij.contributors.psi;

import com.intellij.icons.AllIcons;
import com.intellij.navigation.ItemPresentation;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import org.eclipse.lsp4j.SymbolKind;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public class LSPPsiSymbol extends LSPPsiElement{

  @NotNull
  private final SymbolKind kind;

  /**
   * @param name    The name (text) of the element
   * @param project The project it belongs to
   * @param start   The offset in the editor where the element starts
   * @param end     The offset where it ends
   * @param file
   */
  public LSPPsiSymbol(@NotNull SymbolKind kind, @NotNull String name, @NotNull Project project, int start, int end, @NotNull PsiFile file) {
    super(name, project, start, end, file);
    this.kind = kind;
  }

  @NotNull
  public SymbolKind getKind() {
    return kind;
  }

  @Override
  public ItemPresentation getPresentation() {
    return new ItemPresentation() {
      @Override
      public @Nullable String getPresentableText() {
        return getName();
      }

      @Override
      public @Nullable String getLocationString() {
        return null;
      }

      @Override
      public @Nullable Icon getIcon(boolean unused) {
          switch (kind){
            case Class:
              return AllIcons.Nodes.Class;
            case Interface:
              return AllIcons.Nodes.Interface;
            case Field:
              return AllIcons.Nodes.Field;
            case Constant:
              return AllIcons.Nodes.Constant;
            case Method:
            case Constructor:
              return AllIcons.Nodes.Method;
            case Function:
              return AllIcons.Nodes.Function;

          }
          return AllIcons.Nodes.AnonymousClass;
      }
    };
  }
}
