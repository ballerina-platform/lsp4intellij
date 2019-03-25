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
package com.github.lsp4intellij.contributors;

import com.github.lsp4intellij.editor.EditorEventManager;
import com.github.lsp4intellij.editor.EditorEventManagerBase;
import com.github.lsp4intellij.utils.DocumentUtils;
import com.github.lsp4intellij.utils.FileUtils;
import com.intellij.codeInsight.completion.CompletionContributor;
import com.intellij.codeInsight.completion.CompletionParameters;
import com.intellij.codeInsight.completion.CompletionResultSet;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.PsiElement;
import org.eclipse.lsp4j.CompletionItem;
import org.eclipse.lsp4j.Position;
import org.jetbrains.annotations.NotNull;

/**
 * The completion contributor for the LSP
 */
class LSPCompletionContributor extends CompletionContributor {
    private Logger LOG = Logger.getInstance(LSPCompletionContributor.class);

    @Override
    public void fillCompletionVariants(CompletionParameters parameters, CompletionResultSet result) {
        Editor editor = parameters.getEditor();
        int offset = parameters.getOffset();
        Position serverPos = DocumentUtils.offsetToLSPPos(editor, offset);
        Iterable<CompletionItem> toAdd;

        EditorEventManager manager = EditorEventManagerBase.forEditor(editor);

        if (manager != null) {
            result.addAllElements(manager.completion(serverPos));
        }
        super.fillCompletionVariants(parameters, result);
    }

    @Override
    public boolean invokeAutoPopup(@NotNull PsiElement position, char typeChar) {
        String uri = FileUtils.VFSToURI(position.getContainingFile().getVirtualFile());
        EditorEventManager manager = EditorEventManagerBase.forUri(uri);
        if (manager == null) {
            return false;
        }
        for (String triggerChar : manager.completionTriggers) {
            if (triggerChar != null && triggerChar.length() == 1 && triggerChar.charAt(0) == typeChar) {
                return true;
            }
        }
        return false;
    }
}
