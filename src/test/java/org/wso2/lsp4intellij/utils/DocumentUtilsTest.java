/*
 * Copyright (c) 2026, WSO2 LLC. (http://www.wso2.org) All Rights Reserved.
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

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorSettings;
import com.intellij.openapi.editor.LogicalPosition;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.TextRange;
import org.eclipse.lsp4j.InsertReplaceEdit;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.TextEdit;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.MockedStatic;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link DocumentUtils}: offset/LSP-position conversions, tab-aware column
 * arithmetic, tab-size and space-usage settings, Either-wrapping of text edits, and separator
 * constants.
 *
 * <p>{@code ApplicationUtils.computableReadAction} is stubbed in {@code @Before} to invoke the
 * {@code Computable} inline, avoiding the need for a live IntelliJ Application fixture.
 */
public class DocumentUtilsTest {

    /**
     * DocumentUtils wraps its body in ApplicationUtils.computableReadAction(...) which requires
     * a live IntelliJ Application. We stub it to invoke the Computable inline so we can unit-test
     * the position math without a platform fixture.
     */
    private MockedStatic<ApplicationUtils> appUtilsMock;

    @Before
    public void stubReadAction() {
        appUtilsMock = mockStatic(ApplicationUtils.class);
        appUtilsMock.when(() -> ApplicationUtils.computableReadAction(any()))
                .thenAnswer(inv -> ((Computable<?>) inv.getArgument(0)).compute());
    }

    @After
    public void releaseMock() {
        appUtilsMock.close();
    }

    private Editor mockEditor(Document doc, boolean disposed) {
        Editor editor = mock(Editor.class);
        when(editor.isDisposed()).thenReturn(disposed);
        when(editor.getDocument()).thenReturn(doc);
        return editor;
    }

    /**
     * Verifies that {@link DocumentUtils#offsetToLSPPos(Editor, int)} returns null when
     * the editor is disposed.
     */
    @Test
    public void offsetToLSPPosReturnsNullForDisposedEditor() {
        Editor editor = mockEditor(mock(Document.class), true);
        Assert.assertNull(DocumentUtils.offsetToLSPPos(editor, 0));
    }

    /**
     * Verifies that {@link DocumentUtils#offsetToLSPPos(Editor, int)} correctly computes
     * the LSP line and column from a document offset.
     */
    @Test
    public void offsetToLSPPosComputesLineAndColumn() {
        Document doc = mock(Document.class);
        when(doc.getLineNumber(15)).thenReturn(2);
        when(doc.getLineStartOffset(2)).thenReturn(10);
        when(doc.getText(TextRange.create(10, 15))).thenReturn("hello");

        Editor editor = mockEditor(doc, false);
        Position pos = DocumentUtils.offsetToLSPPos(editor, 15);

        Assert.assertNotNull(pos);
        Assert.assertEquals(2, pos.getLine());
        Assert.assertEquals(5, pos.getCharacter());
    }

    /**
     * Verifies that the LogicalPosition overload of {@link DocumentUtils#offsetToLSPPos(LogicalPosition, Editor)}
     * delegates via logicalPositionToOffset and correctly converts to LSP position.
     */
    @Test
    public void offsetToLSPPosLogicalOverloadDelegatesViaLogicalPositionToOffset() {
        Document doc = mock(Document.class);
        when(doc.getLineNumber(7)).thenReturn(1);
        when(doc.getLineStartOffset(1)).thenReturn(5);
        when(doc.getText(TextRange.create(5, 7))).thenReturn("hi");

        Editor editor = mockEditor(doc, false);
        LogicalPosition logical = new LogicalPosition(1, 2);
        when(editor.logicalPositionToOffset(logical)).thenReturn(7);

        Position pos = DocumentUtils.offsetToLSPPos(logical, editor);
        Assert.assertNotNull(pos);
        Assert.assertEquals(1, pos.getLine());
        Assert.assertEquals(2, pos.getCharacter());
    }

    /**
     * Verifies that {@link DocumentUtils#logicalToLSPPos(LogicalPosition, Editor)} correctly
     * converts IntelliJ logical positions to LSP positions.
     */
    @Test
    public void logicalToLSPPosDelegatesViaLogicalPositionToOffset() {
        Document doc = mock(Document.class);
        when(doc.getLineNumber(3)).thenReturn(0);
        when(doc.getLineStartOffset(0)).thenReturn(0);
        when(doc.getText(TextRange.create(0, 3))).thenReturn("foo");

        Editor editor = mockEditor(doc, false);
        LogicalPosition logical = new LogicalPosition(0, 3);
        when(editor.logicalPositionToOffset(logical)).thenReturn(3);

        Position pos = DocumentUtils.logicalToLSPPos(logical, editor);
        Assert.assertNotNull(pos);
        Assert.assertEquals(0, pos.getLine());
        Assert.assertEquals(3, pos.getCharacter());
    }

    /**
     * Verifies that {@link DocumentUtils#lspPosToOffset(Editor, Position)} returns -1 when
     * the editor argument is null.
     */
    @Test
    public void lspPosToOffsetReturnsMinusOneForNullEditor() {
        Assert.assertEquals(-1, DocumentUtils.lspPosToOffset(null, new Position(0, 0)));
    }

    /**
     * Verifies that {@link DocumentUtils#lspPosToOffset(Editor, Position)} returns -2 when
     * the editor is disposed.
     */
    @Test
    public void lspPosToOffsetReturnsMinusTwoForDisposedEditor() {
        Editor editor = mockEditor(mock(Document.class), true);
        Assert.assertEquals(-2, DocumentUtils.lspPosToOffset(editor, new Position(0, 0)));
    }

    /**
     * Verifies that {@link DocumentUtils#lspPosToOffset(Editor, Position)} returns the document
     * text length when the LSP line number exceeds the document line count.
     */
    @Test
    public void lspPosToOffsetReturnsTextLengthWhenLineBeyondDocument() {
        Document doc = mock(Document.class);
        when(doc.getLineCount()).thenReturn(3);
        when(doc.getTextLength()).thenReturn(42);

        Editor editor = mockEditor(doc, false);
        // line >= lineCount triggers the early return
        Assert.assertEquals(42, DocumentUtils.lspPosToOffset(editor, new Position(99, 5)));
    }

    /**
     * Verifies that {@link DocumentUtils#lspPosToOffset(Editor, Position)} correctly computes
     * the character offset for a position without tab characters in the line.
     */
    @Test
    public void lspPosToOffsetComputesOffsetWithoutTabs() {
        Document doc = mock(Document.class);
        when(doc.getLineCount()).thenReturn(2);
        when(doc.getTextLength()).thenReturn(100);
        when(doc.getLineStartOffset(0)).thenReturn(0);
        when(doc.getLineEndOffset(0)).thenReturn(10);
        when(doc.getText(TextRange.create(0, 10))).thenReturn("hello world");

        Editor editor = mockEditor(doc, false);
        EditorSettings settings = mock(EditorSettings.class);
        when(settings.getTabSize(any())).thenReturn(4);
        when(editor.getSettings()).thenReturn(settings);
        when(editor.getProject()).thenReturn(mock(Project.class));
        when(editor.logicalPositionToOffset(new LogicalPosition(0, 5))).thenReturn(5);

        Assert.assertEquals(5, DocumentUtils.lspPosToOffset(editor, new Position(0, 5)));
    }

    /**
     * Verifies that {@link DocumentUtils#lspPosToOffset(Editor, Position)} accounts for tab
     * expansion when converting LSP character index to IntelliJ column (adds extra columns for tabs).
     */
    @Test
    public void lspPosToOffsetAddsExtraColumnsForTabs() {
        // line: "\thello" — one tab then 5 chars. With tabSize=4 the IntelliJ logical column for
        // the LSP character index 3 (the 'l' after '\the') should account for the tab expansion.
        Document doc = mock(Document.class);
        when(doc.getLineCount()).thenReturn(1);
        when(doc.getTextLength()).thenReturn(100);
        when(doc.getLineStartOffset(0)).thenReturn(0);
        when(doc.getLineEndOffset(0)).thenReturn(6);
        when(doc.getText(TextRange.create(0, 6))).thenReturn("\thello");

        Editor editor = mockEditor(doc, false);
        EditorSettings settings = mock(EditorSettings.class);
        when(settings.getTabSize(any())).thenReturn(4);
        when(editor.getSettings()).thenReturn(settings);
        when(editor.getProject()).thenReturn(mock(Project.class));

        // positionInLine=3, tabs=1, tabSize=4 → column = 3 + 1*(4-1) = 6
        when(editor.logicalPositionToOffset(new LogicalPosition(0, 6))).thenReturn(3);

        Assert.assertEquals(3, DocumentUtils.lspPosToOffset(editor, new Position(0, 3)));
    }

    /**
     * Verifies that {@link DocumentUtils#lspPosToOffset(Editor, Position)} clamps the character
     * index to the line's text length if it exceeds the line's bounds.
     */
    @Test
    public void lspPosToOffsetClampsCharacterBeyondLineLength() {
        Document doc = mock(Document.class);
        when(doc.getLineCount()).thenReturn(1);
        when(doc.getTextLength()).thenReturn(5);
        when(doc.getLineStartOffset(0)).thenReturn(0);
        when(doc.getLineEndOffset(0)).thenReturn(5);
        when(doc.getText(TextRange.create(0, 5))).thenReturn("hello");

        Editor editor = mockEditor(doc, false);
        EditorSettings settings = mock(EditorSettings.class);
        when(settings.getTabSize(any())).thenReturn(4);
        when(editor.getSettings()).thenReturn(settings);
        when(editor.getProject()).thenReturn(mock(Project.class));
        when(editor.logicalPositionToOffset(new LogicalPosition(0, 5))).thenReturn(5);

        // pos.character=99 gets clamped down to lineText.length()=5 internally.
        Assert.assertEquals(5, DocumentUtils.lspPosToOffset(editor, new Position(0, 99)));
    }

    /**
     * Verifies that {@link DocumentUtils#lspPosToOffset(Editor, Position)} clamps the final
     * result to the document's text length, preventing out-of-bounds positions.
     */
    @Test
    public void lspPosToOffsetClampsResultIntoDocumentBounds() {
        Document doc = mock(Document.class);
        when(doc.getLineCount()).thenReturn(1);
        when(doc.getTextLength()).thenReturn(3);
        when(doc.getLineStartOffset(0)).thenReturn(0);
        when(doc.getLineEndOffset(0)).thenReturn(3);
        when(doc.getText(TextRange.create(0, 3))).thenReturn("foo");

        Editor editor = mockEditor(doc, false);
        EditorSettings settings = mock(EditorSettings.class);
        when(settings.getTabSize(any())).thenReturn(4);
        when(editor.getSettings()).thenReturn(settings);
        when(editor.getProject()).thenReturn(mock(Project.class));
        // Intentionally return a value beyond doc length — DocumentUtils should clamp to textLength.
        when(editor.logicalPositionToOffset(any(LogicalPosition.class))).thenReturn(999);

        Assert.assertEquals(3, DocumentUtils.lspPosToOffset(editor, new Position(0, 1)));
    }

    /**
     * Verifies that {@link DocumentUtils#getTabsAwarePosition(Editor, Position)} returns null
     * when the editor is disposed.
     */
    @Test
    public void getTabsAwarePositionReturnsNullForDisposedEditor() {
        Editor editor = mockEditor(mock(Document.class), true);
        Assert.assertNull(DocumentUtils.getTabsAwarePosition(editor, new Position(0, 0)));
    }

    /**
     * Verifies that {@link DocumentUtils#getTabsAwarePosition(Editor, Position)} accounts for
     * tab expansion when converting an LSP position to an IntelliJ LogicalPosition.
     */
    @Test
    public void getTabsAwarePositionAccountsForTabs() {
        Document doc = mock(Document.class);
        when(doc.getLineCount()).thenReturn(1);
        when(doc.getLineStartOffset(0)).thenReturn(0);
        when(doc.getLineEndOffset(0)).thenReturn(6);
        when(doc.getText(TextRange.create(0, 6))).thenReturn("\thello");

        Editor editor = mockEditor(doc, false);
        EditorSettings settings = mock(EditorSettings.class);
        when(settings.getTabSize(any())).thenReturn(4);
        when(editor.getSettings()).thenReturn(settings);
        when(editor.getProject()).thenReturn(mock(Project.class));

        LogicalPosition result = DocumentUtils.getTabsAwarePosition(editor, new Position(0, 3));
        Assert.assertNotNull(result);
        Assert.assertEquals(0, result.line);
        Assert.assertEquals(6, result.column);
    }

    /**
     * Verifies that {@link DocumentUtils#getTabSize(Editor)} retrieves the tab size from
     * the editor's settings.
     */
    @Test
    public void getTabSizeReadsFromEditorSettings() {
        Editor editor = mock(Editor.class);
        EditorSettings settings = mock(EditorSettings.class);
        when(settings.getTabSize(any())).thenReturn(8);
        when(editor.getSettings()).thenReturn(settings);
        when(editor.getProject()).thenReturn(mock(Project.class));

        Assert.assertEquals(8, DocumentUtils.getTabSize(editor));
    }

    /**
     * Verifies that {@link DocumentUtils#shouldUseSpaces(Editor)} returns true when the editor
     * is configured to not use tab characters (i.e., to use spaces for indentation).
     */
    @Test
    public void shouldUseSpacesReflectsEditorSetting() {
        Editor editor = mock(Editor.class);
        EditorSettings settings = mock(EditorSettings.class);
        when(editor.getSettings()).thenReturn(settings);
        when(editor.getProject()).thenReturn(mock(Project.class));

        when(settings.isUseTabCharacter(any())).thenReturn(false);
        Assert.assertTrue(DocumentUtils.shouldUseSpaces(editor));

        when(settings.isUseTabCharacter(any())).thenReturn(true);
        Assert.assertFalse(DocumentUtils.shouldUseSpaces(editor));
    }

    /**
     * Verifies that {@link DocumentUtils#toEither(java.util.List)} wraps each TextEdit in an
     * Either as the left value.
     */
    @Test
    public void toEitherWrapsEachEditAsLeft() {
        TextEdit a = new TextEdit(new Range(new Position(0, 0), new Position(0, 1)), "a");
        TextEdit b = new TextEdit(new Range(new Position(1, 0), new Position(1, 1)), "b");

        List<Either<TextEdit, InsertReplaceEdit>> out = DocumentUtils.toEither(Arrays.asList(a, b));
        Assert.assertEquals(2, out.size());
        Assert.assertTrue(out.get(0).isLeft());
        Assert.assertSame(a, out.get(0).getLeft());
        Assert.assertSame(b, out.get(1).getLeft());
    }

    /**
     * Verifies that {@link DocumentUtils#toEither(java.util.List)} returns an empty list
     * when passed an empty input list.
     */
    @Test
    public void toEitherOnEmptyListReturnsEmptyList() {
        Assert.assertTrue(DocumentUtils.toEither(Collections.emptyList()).isEmpty());
    }

    /**
     * Verifies that the separator constants in {@link DocumentUtils} match the expected platform
     * line separator values (Windows: CRLF, Unix/Linux: LF).
     */
    @Test
    public void separatorConstantsMatchPlatformValues() {
        Assert.assertEquals("\r\n", DocumentUtils.WIN_SEPARATOR);
        Assert.assertEquals("\n", DocumentUtils.LINUX_SEPARATOR);
    }
}
