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
package org.wso2.lsp4intellij.utils;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.LogicalPosition;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.DocumentUtil;
import org.eclipse.lsp4j.InsertReplaceEdit;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.TextEdit;
import org.eclipse.lsp4j.jsonrpc.messages.Either;

import javax.annotation.Nullable;

import java.util.List;
import java.util.stream.Collectors;

import static java.lang.Math.max;
import static java.lang.Math.min;
import static org.wso2.lsp4intellij.utils.ApplicationUtils.computableReadAction;

/**
 * Various methods to convert offsets / logical position / server position
 */
public class DocumentUtils {

    private static Logger LOG = Logger.getInstance(DocumentUtils.class);
    public static final String WIN_SEPARATOR = "\r\n";
    public static final String LINUX_SEPARATOR = "\n";

    /**
     * Gets the line at the given offset given an editor and bolds the text between the given offsets
     *
     * @param editor      The editor
     * @param startOffset The starting offset
     * @param endOffset   The ending offset
     * @return The document line
     */
    public static String getLineText(Editor editor, int startOffset, int endOffset) {
        return computableReadAction(() -> {
            Document doc = editor.getDocument();
            int lineIdx = doc.getLineNumber(startOffset);
            int lineStartOff = doc.getLineStartOffset(lineIdx);
            int lineEndOff = doc.getLineEndOffset(lineIdx);
            String line = doc.getText(new TextRange(lineStartOff, lineEndOff));
            int startOffsetInLine = startOffset - lineStartOff;
            int endOffsetInLine = endOffset - lineStartOff;
            StringBuilder sb = new StringBuilder( line.length()+7 );
            sb.append(line, 0, startOffsetInLine);
            sb.append("<b>");
            sb.append(line, startOffsetInLine, endOffsetInLine);
            sb.append("</b>");
            sb.append(line, endOffsetInLine, line.length());
            return sb.toString();
        });
    }

    /**
     * Transforms a LogicalPosition (IntelliJ) to an LSP Position
     *
     * @param position the LogicalPosition
     * @param editor   The editor
     * @return the Position
     */
    @Nullable
    public static Position logicalToLSPPos(LogicalPosition position, Editor editor) {
        return offsetToLSPPos(editor, editor.logicalPositionToOffset(position));
    }

    /**
     * Transforms a LogicalPosition (IntelliJ) to an LSP Position
     *
     * @param position the LogicalPosition
     * @param editor   The editor
     * @return the Position
     */
    @Nullable
    public static Position offsetToLSPPos(LogicalPosition position, Editor editor) {
        return offsetToLSPPos(editor, editor.logicalPositionToOffset(position));
    }

    /**
     * Calculates a Position given an editor and an offset
     *
     * @param editor The editor
     * @param offset The offset
     * @return an LSP position
     */
    @Nullable
    public static Position offsetToLSPPos(Editor editor, int offset) {
        return computableReadAction(() -> {
            if (editor.isDisposed()) {
                return null;
            }
            Document doc = editor.getDocument();
            int line = doc.getLineNumber(offset);
            int lineStart = doc.getLineStartOffset(line);
            String lineTextBeforeOffset = doc.getText(TextRange.create(lineStart, offset));

            int tabs = StringUtil.countChars(lineTextBeforeOffset, '\t');
            int tabSize = getTabSize(editor);
            int column = lineTextBeforeOffset.length() - tabs * (tabSize - 1);
            return new Position(line, column);
        });
    }

    /**
     * Transforms an LSP position to an editor offset
     *
     * @param editor The editor
     * @param pos    The LSPPos
     * @return The offset
     */
    public static int LSPPosToOffset(Editor editor, Position pos) {
        return computableReadAction(() -> {
            if (editor == null) {
                return -1;
            }
            if (editor.isDisposed()) {
                return -2;
            }
            // lsp and intellij start lines/columns zero-based
            Document doc = editor.getDocument();
            int line = max(0, Math.min(pos.getLine(), doc.getLineCount()));
            if (line >= doc.getLineCount()) {
                return doc.getTextLength();
            }
            String lineText = doc.getText(DocumentUtil.getLineTextRange(doc, line));

            final int positionInLine = max(0, min(lineText.length(), pos.getCharacter()));
            int tabs = StringUtil.countChars(lineText, '\t', 0, positionInLine, false);
            int tabSize = getTabSize(editor);
            int column = positionInLine + tabs * (tabSize - 1);
            int offset = editor.logicalPositionToOffset(new LogicalPosition(line, column));
            if (pos.getCharacter() >= lineText.length()) {
                LOG.debug(String.format("LSPPOS outofbounds: %s, line : %s, column : %d, offset : %d", pos,
                        lineText, column, offset));
            }
            int docLength = doc.getTextLength();
            if (offset > docLength) {
                LOG.debug(String.format("Offset greater than text length : %d > %d", offset, docLength));
            }
            return Math.min(max(offset, 0), docLength);

        });


    }

    @Nullable
    public static LogicalPosition getTabsAwarePosition(Editor editor, Position pos) {
        return computableReadAction(() -> {
            if (editor.isDisposed()) {
                return null;
            }
            Document doc = editor.getDocument();
            int line = max(0, Math.min(pos.getLine(), doc.getLineCount() - 1));
            String lineText = doc.getText(DocumentUtil.getLineTextRange(doc, line));
            final int positionInLine = max(0, min(lineText.length(), pos.getCharacter()));
            int tabs = StringUtil.countChars(lineText, '\t', 0, positionInLine, false);
            int tabSize = getTabSize(editor);
            int column = positionInLine + tabs * (tabSize - 1);
            return new LogicalPosition(line, column);
        });
    }

    /**
     * Retrieves the amount of whitespaces a tab represents.
     */
    public static int getTabSize(Editor editor) {
        return computableReadAction(() -> editor.getSettings().getTabSize(editor.getProject()));
    }

    public static boolean shouldUseSpaces(Editor editor){
        return computableReadAction(() -> !editor.getSettings().isUseTabCharacter(editor.getProject()));
    }

    public static List<Either<TextEdit, InsertReplaceEdit>> toEither(List<TextEdit> edits) {
        return edits.stream().map(Either::<TextEdit, InsertReplaceEdit>forLeft).collect(Collectors.toList());
    }


}
