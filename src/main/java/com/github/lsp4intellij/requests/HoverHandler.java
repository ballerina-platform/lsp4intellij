package com.github.lsp4intellij.requests;

import com.intellij.openapi.diagnostic.Logger;
import com.vladsch.flexmark.html.HtmlRenderer;
import com.vladsch.flexmark.parser.Parser;
import com.vladsch.flexmark.util.options.MutableDataSet;
import org.eclipse.lsp4j.Hover;
import org.eclipse.lsp4j.MarkedString;
import org.eclipse.lsp4j.MarkupContent;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.eclipse.lsp4j.jsonrpc.validation.NonNull;

import java.util.ArrayList;
import java.util.List;

/**
 * Object used to process Hover responses
 */
public class HoverHandler {

    private Logger LOG = Logger.getInstance(HoverHandler.class);

    /**
     * Returns the hover string corresponding to an Hover response
     *
     * @param hover The Hover
     * @return The string response
     */
    public static String getHoverString(@NonNull Hover hover) {
        if (hover == null || hover.getContents() == null) {
            return "";
        }
        Either<List<Either<String, MarkedString>>, MarkupContent> hoverContents = hover.getContents();
        if (hoverContents.isLeft()) {
            boolean useHtml = false;
            List<Either<String, MarkedString>> contents = hoverContents.getLeft();
            if (contents != null && !contents.isEmpty()) {
                List<String> result = new ArrayList<>();
                for (Either<String, MarkedString> c : contents) {
                    if (c.isLeft() && !c.getLeft().isEmpty()) {
                        result.add(c.getLeft());
                    } else if (c.isRight()) {
                        useHtml = true;
                        MutableDataSet options = new MutableDataSet();
                        Parser parser = Parser.builder(options).build();
                        HtmlRenderer renderer = HtmlRenderer.builder(options).build();
                        MarkedString markedString = c.getRight();
                        String string = (markedString.getLanguage() != null && !markedString.getLanguage().isEmpty()) ?
                                "```" + markedString.getLanguage() + " " + markedString.getValue() + "```" :
                                "";
                        if (!string.isEmpty()) {
                            result.add(renderer.render(parser.parse(string)));
                        }
                    }
                }
                return useHtml ? "<html>" + String.join("\n\n", result) + "</html>" : String.join("\n\n", result);
            } else {
                return "";
            }
        } else if (hoverContents.isRight()) {
            MutableDataSet options = new MutableDataSet();
            Parser parser = Parser.builder(options).build();
            HtmlRenderer renderer = HtmlRenderer.builder(options).build();
            String markedContent = hoverContents.getRight().getValue();
            return "<html>" + renderer.render(parser.parse(markedContent)) + "</html>";
        } else {
            return "";
        }
    }
}
