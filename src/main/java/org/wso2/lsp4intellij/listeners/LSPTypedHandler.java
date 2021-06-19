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
package org.wso2.lsp4intellij.listeners;

import com.intellij.codeInsight.AutoPopupController;
import com.intellij.codeInsight.editorActions.TypedHandlerDelegate;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;
import org.wso2.lsp4intellij.editor.EditorEventManager;
import org.wso2.lsp4intellij.editor.EditorEventManagerBase;
import org.wso2.lsp4intellij.utils.FileUtils;

/**
 * This class notifies an EditorEventManager that a character has been typed in the editor
 */
public class LSPTypedHandler extends TypedHandlerDelegate {

    @Override
    public Result charTyped(char c, Project project, @NotNull Editor editor, @NotNull PsiFile file) {
        if (!FileUtils.isFileSupported(file.getVirtualFile())) {
            return Result.CONTINUE;
        }

        EditorEventManager eventManager = EditorEventManagerBase.forEditor(editor);
        if (eventManager != null) {
            eventManager.characterTyped(c);
        }
        return Result.CONTINUE;
    }

    @Override
    public Result checkAutoPopup(char charTyped, @NotNull Project project, @NotNull Editor editor, @NotNull PsiFile file) {
        if (!FileUtils.isFileSupported(file.getVirtualFile())) {
            return Result.CONTINUE;
        }

        EditorEventManager manager = EditorEventManagerBase.forEditor(editor);
        if (manager == null) {
            return Result.CONTINUE;
        }
        for (String triggerChar : manager.completionTriggers) {
            if (triggerChar != null && triggerChar.length() == 1 && triggerChar.charAt(0) == charTyped) {
                return Result.STOP;
            }
        }
        return Result.CONTINUE;
    }
}
