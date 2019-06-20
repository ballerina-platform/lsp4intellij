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
package org.wso2.lsp4intellij.contributors.annotator;

import com.intellij.lang.annotation.Annotation;
import com.intellij.lang.annotation.AnnotationHolder;
import com.intellij.lang.annotation.ExternalAnnotator;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import org.eclipse.lsp4j.Diagnostic;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.wso2.lsp4intellij.IntellijLanguageClient;
import org.wso2.lsp4intellij.editor.EditorEventManager;
import org.wso2.lsp4intellij.editor.EditorEventManagerBase;
import org.wso2.lsp4intellij.utils.DocumentUtils;
import org.wso2.lsp4intellij.utils.FileUtils;

import java.util.List;

public class LSPAnnotator extends ExternalAnnotator {
    private static final Object RESULT = new Object();

    @Nullable
    @Override
    public Object collectInformation(@NotNull PsiFile file, @NotNull Editor editor, boolean hasErrors) {
        return RESULT;
    }

    @Nullable
    @Override
    public Object doAnnotate(Object collectedInfo) {
        return RESULT;
    }

    @Override
    public void apply(@NotNull PsiFile file, Object annotationResult, @NotNull AnnotationHolder holder) {
        VirtualFile virtualFile = file.getVirtualFile();
        if (FileUtils.isFileSupported(virtualFile) &&
                IntellijLanguageClient.isExtensionSupported(virtualFile.getExtension())) {
            String uri = FileUtils.VFSToURI(virtualFile);
            EditorEventManager eventManager = EditorEventManagerBase.forUri(uri);
            if (eventManager != null) {
                createAnnotations(holder, eventManager.getDiagnostics(), eventManager.editor);
            }
        }
    }

    private void createAnnotations(AnnotationHolder holder, List<Diagnostic> diagnostics, Editor editor) {
        diagnostics.forEach(d -> {
            final int start = DocumentUtils.LSPPosToOffset(editor, d.getRange().getStart());
            final int end = DocumentUtils.LSPPosToOffset(editor, d.getRange().getEnd());

            if (start >= end) {
                return;
            }

            switch (d.getSeverity()) {
                case Error:
                    holder.createErrorAnnotation(new TextRange(start, end), d.getMessage());
                    break;
                case Warning:
                    holder.createWarningAnnotation(new TextRange(start, end), d.getMessage());
                    break;
                case Information:
                    holder.createInfoAnnotation(new TextRange(start, end), d.getMessage());
                    break;
                case Hint:
                    holder.createWeakWarningAnnotation(new TextRange(start, end), d.getMessage());
                    break;
            }
        });
    }
}
