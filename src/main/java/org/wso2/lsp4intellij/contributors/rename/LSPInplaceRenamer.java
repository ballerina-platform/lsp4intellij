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
package org.wso2.lsp4intellij.contributors.rename;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiNamedElement;
import com.intellij.psi.PsiReference;
import com.intellij.psi.search.SearchScope;
import com.intellij.refactoring.rename.inplace.MemberInplaceRenamer;
import org.jetbrains.annotations.NotNull;
import org.wso2.lsp4intellij.editor.EditorEventManager;
import org.wso2.lsp4intellij.editor.EditorEventManagerBase;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

/**
 * The LSP based in-place renaming implementation.
 */
public class LSPInplaceRenamer extends MemberInplaceRenamer {

    private Editor editor;

    LSPInplaceRenamer(@NotNull PsiNamedElement elementToRename, PsiElement substituted, Editor editor) {
        super(elementToRename, substituted, editor, elementToRename.getName(), elementToRename.getName());
        this.editor = editor;
    }

    @Override
    public Collection<PsiReference> collectRefs(SearchScope referencesSearchScope) {
        EditorEventManager eventManager = EditorEventManagerBase.forEditor(editor);
        if (eventManager != null) {
            Pair<List<PsiElement>, List<VirtualFile>> results = eventManager
                    .references(editor.getCaretModel().getCurrentCaret().getOffset(), true, false);
            List<PsiElement> references = results.getFirst();
            List<VirtualFile> toClose = results.getSecond();
            LSPRenameProcessor.addEditors(toClose);
            return references.stream().map(PsiElement::getReference).collect(Collectors.toList());
        } else {
            return new ArrayList<>();
        }
    }
}
