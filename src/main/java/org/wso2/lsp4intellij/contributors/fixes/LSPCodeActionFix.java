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
package org.wso2.lsp4intellij.contributors.fixes;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import org.eclipse.lsp4j.CodeAction;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.wso2.lsp4intellij.editor.EditorEventManager;
import org.wso2.lsp4intellij.editor.EditorEventManagerBase;
import org.wso2.lsp4intellij.requests.WorkspaceEditHandler;

import java.util.Collections;

public class LSPCodeActionFix implements IntentionAction {

    private final String uri;
    private final CodeAction codeAction;

    public LSPCodeActionFix(String uri, @NotNull CodeAction codeAction) {
        this.uri = uri;
        this.codeAction = codeAction;
    }

    @Nls(capitalization = Nls.Capitalization.Sentence)
    @NotNull
    @Override
    public String getText() {
        return codeAction.getTitle();
    }

    @Nls
    @NotNull
    @Override
    public String getFamilyName() {
        return "LSP Fixes";
    }

    @Override
    public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile psiFile) {
        return true;
    }

    @Override
    public void invoke(@NotNull Project project, Editor editor, PsiFile psiFile) {
        if (codeAction.getEdit() != null) {
            WorkspaceEditHandler.applyEdit(codeAction.getEdit(), codeAction.getTitle());
        }
        EditorEventManager manager = EditorEventManagerBase.forEditor(editor);
        if (manager != null) {
            manager.executeCommands(Collections.singletonList(codeAction.getCommand()));
        }
    }

    @Override
    public boolean startInWriteAction() {
        return true;
    }
}
