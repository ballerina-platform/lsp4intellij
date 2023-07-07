package org.wso2.lsp4intellij.contributors;

import com.intellij.lang.ASTNode;
import com.intellij.lang.folding.CustomFoldingBuilder;
import com.intellij.lang.folding.FoldingDescriptor;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import org.eclipse.lsp4j.FoldingRange;
import org.eclipse.lsp4j.FoldingRangeRequestParams;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.eclipse.lsp4j.jsonrpc.JsonRpcException;
import org.jetbrains.annotations.NotNull;
import org.wso2.lsp4intellij.client.languageserver.wrapper.LanguageServerWrapper;
import org.wso2.lsp4intellij.requests.Timeouts;
import org.wso2.lsp4intellij.utils.DocumentUtils;
import org.wso2.lsp4intellij.utils.FileUtils;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.wso2.lsp4intellij.requests.Timeout.getTimeout;

public class LSPFoldingRangeProvider extends CustomFoldingBuilder {

    protected Logger LOG = Logger.getInstance(LSPFoldingRangeProvider.class);
    private Editor editor;
    private LanguageServerWrapper wrapper;

    @Override
    protected void buildLanguageFoldRegions(@NotNull List<FoldingDescriptor> descriptors, @NotNull PsiElement root, @NotNull Document document, boolean quick) {
        // if quick flag is set, we do nothing here
        if (quick) {
            return;
        }

        if (editor == null || wrapper == null) {
            PsiFile psiFile = root.getContainingFile();
            editor = FileUtils.editorFromPsiFile(psiFile);
            wrapper = LanguageServerWrapper.forVirtualFile(psiFile.getVirtualFile(), root.getProject());
        }

        String url = root.getContainingFile().getVirtualFile().getUrl();
        TextDocumentIdentifier textDocumentIdentifier = new TextDocumentIdentifier(url);
        FoldingRangeRequestParams params = new FoldingRangeRequestParams(textDocumentIdentifier);
        CompletableFuture<List<FoldingRange>> future = wrapper.getRequestManager().foldingRange(params);

        if (future != null) {
            try {
                List<FoldingRange> foldingRanges = future.get(getTimeout(Timeouts.FOLDING), TimeUnit.MILLISECONDS);
                wrapper.notifySuccess(Timeouts.FOLDING);

                for (FoldingRange foldingRange : foldingRanges) {
                    int start = getStartOffset(foldingRange, document);
                    int end = getEndOffset(foldingRange, document);

                    if (foldingRange.getCollapsedText() != null) {
                        descriptors.add(new FoldingDescriptor(root.getNode(), new TextRange(start, end), null, foldingRange.getCollapsedText()));
                    } else {
                        descriptors.add(new FoldingDescriptor(root.getNode(), new TextRange(start, end)));
                    }
                }
            } catch (TimeoutException | InterruptedException e) {
                LOG.warn(e);
                wrapper.notifyFailure(Timeouts.FOLDING);
            } catch (JsonRpcException | ExecutionException e) {
                LOG.warn(e);
                wrapper.crashed(e);
            }
        }
    }

    private int getEndOffset(@NotNull FoldingRange foldingRange, @NotNull Document document) {
        // EndCharacter is optional. When missing, it should be set to the length of the end line.
        if (foldingRange.getEndCharacter() == null) {
            return document.getLineEndOffset(foldingRange.getEndLine());
        }

        return DocumentUtils.LSPPosToOffset(editor, new Position(foldingRange.getEndLine(), foldingRange.getEndCharacter()));
    }

    private int getStartOffset(@NotNull FoldingRange foldingRange, @NotNull Document document) {
        // StartCharacter is optional. When missing, it should be set to the length of the start line.
        if (foldingRange.getStartCharacter() == null) {
            return document.getLineEndOffset(foldingRange.getStartLine());
        } else {
            return DocumentUtils.LSPPosToOffset(editor, new Position(foldingRange.getStartLine(), foldingRange.getStartCharacter()));
        }
    }

    @Override
    protected String getLanguagePlaceholderText(@NotNull ASTNode node, @NotNull TextRange range) {
        return null;
    }

    @Override
    protected boolean isRegionCollapsedByDefault(@NotNull ASTNode node) {
        return false;
    }
}
