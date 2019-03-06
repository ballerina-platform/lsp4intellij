package com.github.lsp4intellij.editor;

import com.github.lsp4intellij.utils.DocumentUtils;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.markup.RangeHighlighter;
import org.eclipse.lsp4j.Location;

import java.awt.*;

public class CtrlRangeMarker {

    Location location;
    private Editor editor;
    private RangeHighlighter range;

    CtrlRangeMarker(Location location, Editor editor, RangeHighlighter range) {
        this.location = location;
        this.editor = editor;
        this.range = range;

    }

    boolean highlightContainsOffset(int offset) {
        if (!isDefinition()) {
            return range.getStartOffset() <= offset && range.getEndOffset() >= offset;
        } else {
            return definitionContainsOffset(offset);
        }
    }

    boolean definitionContainsOffset(int offset) {
        return DocumentUtils.LSPPosToOffset(editor, location.getRange().getStart()) <= offset && offset <= DocumentUtils
                .LSPPosToOffset(editor, location.getRange().getEnd());
    }

    /**
     * Removes the highlighter and restores the default cursor
     */
    void dispose() {
        if (!isDefinition()) {
            editor.getMarkupModel().removeHighlighter(range);
            editor.getContentComponent().setCursor(Cursor.getDefaultCursor());
        }
    }

    /**
     * If the marker points to the definition itself
     */
    boolean isDefinition() {
        return range == null;
    }
}
