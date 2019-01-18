package com.github.lsp4intellij.contributors;

import com.github.lsp4intellij.editor.EditorEventManager;
import com.github.lsp4intellij.editor.EditorEventManagerBase;
import com.github.lsp4intellij.utils.DocumentUtils;
import com.intellij.codeInsight.completion.CompletionContributor;
import com.intellij.codeInsight.completion.CompletionParameters;
import com.intellij.codeInsight.completion.CompletionResultSet;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import org.eclipse.lsp4j.CompletionItem;
import org.eclipse.lsp4j.Position;

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
}
