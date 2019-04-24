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
package org.wso2.lsp4intellij.contributors.rename;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiReference;
import com.intellij.refactoring.listeners.RefactoringElementListener;
import com.intellij.refactoring.rename.RenameDialog;
import com.intellij.refactoring.rename.RenamePsiElementProcessor;
import com.intellij.usageView.UsageInfo;
import org.jetbrains.annotations.NotNull;
import org.wso2.lsp4intellij.contributors.psi.LSPPsiElement;
import org.wso2.lsp4intellij.editor.EditorEventManager;
import org.wso2.lsp4intellij.editor.EditorEventManagerBase;
import org.wso2.lsp4intellij.requests.WorkspaceEditHandler;
import org.wso2.lsp4intellij.utils.FileUtils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * LSPRenameProcessor implementation.
 */
public class LSPRenameProcessor extends RenamePsiElementProcessor {

    private static Set<VirtualFile> openedEditors = new HashSet<>();
    private PsiElement curElem;
    private Set<PsiElement> elements = new HashSet<>();

    @Override
    public boolean canProcessElement(@NotNull PsiElement element) {
        if (element instanceof LSPPsiElement) {
            return true;
        } else if (element instanceof PsiFile) {
            FileEditor fEditor = FileEditorManager.getInstance(element.getProject())
                    .getSelectedEditor(((PsiFile) element).getVirtualFile());
            if (!(fEditor instanceof Editor)) {
                return false;
            }
            Editor editor = (Editor) fEditor;
            EditorEventManager manager = EditorEventManagerBase.forEditor(editor);
            if (manager != null) {
                if (editor.getContentComponent().hasFocus()) {
                    int offset = editor.getCaretModel().getCurrentCaret().getOffset();
                    Pair<List<PsiElement>, List<VirtualFile>> refResponse = manager.references(offset, true, false);
                    this.elements.addAll(refResponse.getFirst());
                    LSPRenameProcessor.openedEditors.addAll(refResponse.getSecond());
                    this.curElem = elements.stream()
                            .filter(e -> e.getTextRange().getStartOffset() <= offset && offset <= e.getTextRange()
                                    .getEndOffset()).findAny().orElse(null);
                    if (curElem != null) {
                        this.elements = this.elements.stream().filter(elem -> elem.getText().equals(curElem.getText()))
                                .collect(Collectors.toSet());
                        return true;
                    }
                }
            }
            return false;
        }
        return false;
    }

    @Override
    public RenameDialog createRenameDialog(Project project, PsiElement element, PsiElement nameSuggestionContext,
            Editor editor) {
        return super.createRenameDialog(project, curElem, nameSuggestionContext, editor);
    }

    @NotNull
    @Override
    public Collection<PsiReference> findReferences(PsiElement element) {
        return findReferences(element, false);
    }

    @NotNull
    @Override
    public Collection<PsiReference> findReferences(PsiElement element, boolean searchInCommentsAndStrings) {
        if (element instanceof LSPPsiElement) {
            if (elements.contains(element)) {
                return elements.stream().map(PsiElement::getReference).collect(Collectors.toList());
            } else {
                EditorEventManager manager = EditorEventManagerBase
                        .forEditor(FileUtils.editorFromPsiFile(element.getContainingFile()));
                if (manager != null) {
                    Pair<List<PsiElement>, List<VirtualFile>> refs = manager
                            .references(element.getTextOffset(), true, false);
                    addEditors(refs.getSecond());
                    return refs.getFirst().stream().map(PsiElement::getReference).collect(Collectors.toList());
                }
            }
        }
        return new ArrayList<>();
    }

    @Override
    public boolean isInplaceRenameSupported() {
        return true;
    }

    //TODO may rename invalid elements
    @Override
    public void renameElement(PsiElement element, String newName, UsageInfo[] usages,
            RefactoringElementListener listener) {
        WorkspaceEditHandler.applyEdit(element, newName, usages, listener, new ArrayList<>(openedEditors));
        openedEditors.clear();
        elements.clear();
        curElem = null;
    }

    public static void clearEditors() {
        openedEditors.clear();
    }

    public static Set<VirtualFile> getEditors() {
        return openedEditors;
    }

    public static void addEditors(List<VirtualFile> toAdd) {
        openedEditors.addAll(toAdd);
    }
}


