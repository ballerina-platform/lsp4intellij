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
package org.wso2.lsp4intellij.contributors.symbol;

import com.intellij.icons.AllIcons;
import com.intellij.ide.projectView.PresentationData;
import com.intellij.ide.structureView.*;
import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.ide.util.treeView.smartTree.*;
import com.intellij.lang.PsiStructureViewFactory;
import com.intellij.navigation.ItemPresentation;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.event.DocumentListener;
import com.intellij.psi.NavigatablePsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiPlainTextFile;
import org.eclipse.lsp4j.*;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.wso2.lsp4intellij.IntellijLanguageClient;
import org.wso2.lsp4intellij.client.languageserver.wrapper.LanguageServerWrapper;
import org.wso2.lsp4intellij.contributors.psi.LSPPsiSymbol;
import org.wso2.lsp4intellij.requests.Timeout;
import org.wso2.lsp4intellij.requests.Timeouts;
import org.wso2.lsp4intellij.utils.DocumentUtils;
import org.wso2.lsp4intellij.utils.FileUtils;

import javax.annotation.Nullable;
import javax.swing.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static java.lang.Thread.sleep;

public final class LSPStructureView implements PsiStructureViewFactory {

  List<TreeElement> treeElements = new ArrayList<>();

  void loadSymbols(LSPStructureViewModel lspStructureViewModel, Editor editor, @NotNull PsiFile psiFile){
    // load data from server
    final Set<LanguageServerWrapper> wrappers = ServiceManager.getService(IntellijLanguageClient.class).getAllServerWrappersFor(FileUtils.projectToUri(psiFile.getProject()));
    final Optional<LanguageServerWrapper> wrapperOpt = wrappers.stream().findFirst();
    if(wrapperOpt.isPresent()) {
      final LanguageServerWrapper wrapper = wrapperOpt.get();
      final CompletableFuture<List<Either<SymbolInformation, DocumentSymbol>>> listCompletableFuture = wrapper.getRequestManager().documentSymbol(new DocumentSymbolParams(new TextDocumentIdentifier(FileUtils.uriFromVirtualFile(psiFile.getVirtualFile()))));

      List<Either<SymbolInformation, DocumentSymbol>> eithers;
      try {
        eithers = listCompletableFuture.get(Timeout.getTimeout(Timeouts.SYMBOLS), TimeUnit.MILLISECONDS);
        wrapper.notifySuccess(Timeouts.SYMBOLS);
        treeElements.clear();

        if(eithers != null) {
          for (Either<SymbolInformation, DocumentSymbol> either : eithers) {
            if (either.isLeft()) {
              final SymbolInformation symbolInfo = either.getLeft();
              treeElements.add(new LSPStructureView.LSPStructureViewElement(new LSPPsiSymbol(symbolInfo.getKind(), symbolInfo.getName(), psiFile.getProject(), DocumentUtils.LSPPosToOffset(editor, symbolInfo.getLocation().getRange().getStart()), DocumentUtils.LSPPosToOffset(editor, symbolInfo.getLocation().getRange().getEnd()), psiFile)));
            } else if (either.isRight()) {
              final DocumentSymbol docSymbol = either.getRight();

              final int start = DocumentUtils.LSPPosToOffset(editor, docSymbol.getRange().getStart());
              final int end = DocumentUtils.LSPPosToOffset(editor, docSymbol.getRange().getEnd());
              treeElements.add(new LSPStructureView.LSPStructureViewElement(
                      new LSPPsiSymbol(docSymbol.getKind(), docSymbol.getName(), psiFile.getProject(), start, end, psiFile)));
            }
          }
        }

        lspStructureViewModel.fireModelUpdate();

      }catch (InterruptedException | ExecutionException | TimeoutException e) {
        wrapper.notifyFailure(Timeouts.SYMBOLS);
        e.printStackTrace();
      }
    }
  }

  @Override
  public StructureViewBuilder getStructureViewBuilder(@NotNull final PsiFile psiFile) {
    return new TreeBasedStructureViewBuilder() {
      CompletableFuture<Void> debouncer;
      @NotNull
      @Override
      public StructureViewModel createStructureViewModel(@Nullable Editor editor) {

        final LSPStructureViewModel lspStructureViewModel = new LSPStructureViewModel(psiFile);
        loadSymbols(lspStructureViewModel, editor, psiFile);

        editor.getDocument().addDocumentListener(new DocumentListener() {
          @Override
          public void documentChanged(@NotNull DocumentEvent event){
            // debounce: request if sth changed after a timeout
            if(debouncer != null){
              debouncer.cancel(true);
            }
            debouncer = CompletableFuture.runAsync(() -> {
              try {
                sleep(500);
              } catch (InterruptedException e) {
                e.printStackTrace();
              }
              debouncer = null;
            }).thenRun( ()-> loadSymbols(lspStructureViewModel, editor, psiFile));
          }
        });

        return lspStructureViewModel;
      }
    };
  }

  class LSPStructureViewElement implements StructureViewTreeElement, SortableTreeElement {

    private final NavigatablePsiElement navigatablePsiElement;

    public LSPStructureViewElement(NavigatablePsiElement element) {
      this.navigatablePsiElement = element;
    }

    @Override
    public Object getValue() {
      return navigatablePsiElement;
    }

    @Override
    public void navigate(boolean requestFocus) {
      navigatablePsiElement.navigate(requestFocus);
    }

    @Override
    public boolean canNavigate() {
      return navigatablePsiElement.canNavigate();
    }

    @Override
    public boolean canNavigateToSource() {
      return navigatablePsiElement.canNavigateToSource();
    }

    @NotNull
    @Override
    public String getAlphaSortKey() {
      String name = navigatablePsiElement.getName();
      return name != null ? name : "";
    }

    @NotNull
    @Override
    public ItemPresentation getPresentation() {
      ItemPresentation presentation = navigatablePsiElement.getPresentation();
      return presentation != null ? presentation : new PresentationData();
    }

    @NotNull
    @Override
    public TreeElement[] getChildren() {
      if( navigatablePsiElement instanceof PsiPlainTextFile)
      {
        return treeElements.toArray(new TreeElement[0]);
      }
      return EMPTY_ARRAY;
    }

  }

  class LSPStructureViewModel extends StructureViewModelBase implements
          StructureViewModel.ElementInfoProvider {

    public LSPStructureViewModel(PsiFile psiFile) {
      super(psiFile, new LSPStructureView.LSPStructureViewElement(psiFile));
    }

    @Override
    public @NotNull Grouper[] getGroupers() {
      return new Grouper[]{new SymbolGrouper()};
    }

    @NotNull
    public Sorter[] getSorters() {
      return new Sorter[]{Sorter.ALPHA_SORTER};
    }

    @Override
    public boolean isAlwaysShowsPlus(StructureViewTreeElement element) {
      return false;
    }

    @Override
    public boolean isAlwaysLeaf(StructureViewTreeElement element) {
      return element.getValue() instanceof LSPPsiSymbol;
    }

  }



  public class SymbolGrouper implements Grouper{
    @NonNls
    public static final String ID = "GROUP_BY_SYMBOLKIND";

    @Override
    @NotNull
    // TODO: grouping by symbolkind does currently not work!
    public Collection<Group> group(@NotNull AbstractTreeNode<?> parent, @NotNull Collection<TreeElement> children) {
      Map<SymbolKind,List<TreeElement>> result = new HashMap<>();

      for (TreeElement o : children) {
        if (o instanceof LSPStructureViewElement) {
          LSPPsiSymbol element = (LSPPsiSymbol) ((LSPStructureViewElement) o).getValue();
          final List<TreeElement> list = result.computeIfAbsent(element.getKind(), k -> new ArrayList<>());
          list.add(o);
        }
      }

      List<Group> groupList = new ArrayList<>();
      for (Map.Entry<SymbolKind, List<TreeElement>> symbolKindListEntry : result.entrySet()) {
        groupList.add(new Group(){

          @Override
          public @NotNull ItemPresentation getPresentation() {
            return new ItemPresentation() {
              @Override
              public @Nullable String getPresentableText() {
                return "SymbolKind "+ symbolKindListEntry.getKey();
              }

              @Override
              public @Nullable String getLocationString() {
                return "";
              }

              @Override
              public @Nullable Icon getIcon(boolean unused) {
                return null;
              }
            };
          }

          @Override
          public @NotNull Collection<TreeElement> getChildren() {
            final List<TreeElement> value = symbolKindListEntry.getValue();
            return value == null ? Collections.emptyList() : value;
          }
        });
      }

      return groupList;
    }

    @Override
    @NotNull
    public ActionPresentation getPresentation() {
      return new ActionPresentationData("Groub by Symbolkind", null, AllIcons.Actions.GroupBy);
    }

    @Override
    @NotNull
    public String getName() {
      return ID;
    }
  }

}
