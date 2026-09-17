package com.lzt841.editor.lwjgl3;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.utils.Array;
import com.lzt841.editor.CodeEditor.CodeEditorStyle;
import com.lzt841.editor.highlight.CodeHighlightSpan;
import com.lzt841.editor.highlight.CodeHighlighter;
import com.lzt841.editor.highlight.CodeTextStyle;

/**
 * A markdown-flavoured highlighter for the "Rich Text" demo profile: it reads inline markers and emits
 * the style each one stands for, which is the whole span API in one screen.
 *
 * <p>Markers: {@code # } headings get a gradient and fake bold; {@code **bold**} gets the bold overlay;
 * {@code ~~struck~~} a strike-through; {@code ==marked==} a background; {@code __under__} a plain
 * underline; {@code ;;double;;} a double underline; {@code --dashed--} a dashed underline;
 * {@code ..dotted..} a dotted one; {@code !!wavy!!} a wavy underline; {@code @@italics@@}
 * a fake italic. Between them the ten {@link CodeTextStyle} flags are all reachable from this one
 * screen — the heading carries BOLD and GRADIENT at once, which is why a heading is a marker and a
 * decoration at the same time. Each decoration also lands in its own colour, so the styles exercise
 * every colour slot the way a producer would set them: the plain and double underlines share
 * {@code underlineColor}, and the other four use the per-kind slots. The image column is not a marker —
 * the {@code RichTextImageProvider} owns that, because an image is measured, not coloured.
 *
 * <p>This is a demo, not a markdown parser: markers do not nest, an unterminated one colours the rest of
 * the line, and the spans are emitted unsorted. The editor sorts boundaries while flattening.
 */
public class RichTextHighlighter implements CodeHighlighter {
    private static final int MARKER_LENGTH = 2;

    private static final Color STRIKETHROUGH_COLOR = new Color(0.612f, 0.643f, 0.729f, 1f);
    private static final Color UNDERLINE_COLOR = new Color(0.549f, 0.729f, 0.929f, 1f);
    private static final Color DOUBLE_UNDERLINE_COLOR = new Color(0.6f, 0.522f, 0.929f, 1f);
    private static final Color WAVY_COLOR = new Color(0.949f, 0.388f, 0.412f, 1f);
    private static final Color DASHED_COLOR = new Color(0.949f, 0.769f, 0.345f, 1f);
    private static final Color DOTTED_COLOR = new Color(0.388f, 0.776f, 0.6f, 1f);

    private final CodeTextStyle headingStyle = new CodeTextStyle(
        CodeTextStyle.BOLD | CodeTextStyle.GRADIENT,
        null,
        null,
        new Color(0.369f, 0.709f, 0.992f, 1f)
    );
    private final CodeTextStyle boldStyle = new CodeTextStyle(CodeTextStyle.BOLD);
    // A dimmed grey-blue keeps the struck text itself readable; a saturated red would compete with the
    // words it is drawn through.
    private final CodeTextStyle strikeStyle = new CodeTextStyle(CodeTextStyle.STRIKETHROUGH)
        .withStrikethroughColor(STRIKETHROUGH_COLOR);
    private final CodeTextStyle markStyle = new CodeTextStyle(
        CodeTextStyle.BACKGROUND, null, new Color(0.988f, 0.796f, 0.318f, 0.20f), null);
    // The plain and double underlines take the shared slot, which is the one a producer sets when every
    // line it draws should be one colour.
    private final CodeTextStyle underlineStyle = new CodeTextStyle(CodeTextStyle.UNDERLINE, UNDERLINE_COLOR);
    // The demo has a plain underline already; this one exists so UNDERLINE_DOUBLE is reachable from a
    // producer, which is the only way its geometry (the gap and the second line) ever gets exercised.
    private final CodeTextStyle doubleUnderlineStyle = new CodeTextStyle(
        CodeTextStyle.UNDERLINE_DOUBLE, DOUBLE_UNDERLINE_COLOR);
    private final CodeTextStyle dashedStyle = new CodeTextStyle(CodeTextStyle.UNDERLINE_DASHED)
        .withDashedUnderlineColor(DASHED_COLOR);
    private final CodeTextStyle dottedStyle = new CodeTextStyle(CodeTextStyle.UNDERLINE_DOTTED)
        .withDottedUnderlineColor(DOTTED_COLOR);
    // A spell-check squiggle is what the shape is for, so it wears the colour one expects of one — the
    // wavy line is thin enough that drawing it in the text colour made it easy to miss.
    private final CodeTextStyle wavyStyle = new CodeTextStyle(CodeTextStyle.UNDERLINE_WAVY)
        .withWavyUnderlineColor(WAVY_COLOR);
    private final CodeTextStyle italicStyle = new CodeTextStyle(CodeTextStyle.ITALIC);

    @Override
    public Array<Array<CodeHighlightSpan>> highlight(Array<String> lines, CodeEditorStyle style) {
        Array<Array<CodeHighlightSpan>> result = new Array<>(lines.size);
        for (int i = 0; i < lines.size; i++) {
            result.add(highlightLine(lines.get(i), style));
        }
        return result;
    }

    private Array<CodeHighlightSpan> highlightLine(String text, CodeEditorStyle style) {
        Array<CodeHighlightSpan> spans = new Array<>();
        if (text.startsWith("# ")) {
            // A gradient needs a start colour as well as an end one: the span's colour is the first
            // endpoint and the style's gradient colour is the second.
            spans.add(new CodeHighlightSpan(0, text.length(), style.keywordColor, headingStyle));
            return spans;
        }
        int index = 0;
        while (index < text.length()) {
            CodeTextStyle markerStyle = styleForMarker(text, index);
            if (markerStyle == null) {
                index++;
                continue;
            }
            int close = findClose(text, index + MARKER_LENGTH, text.charAt(index));
            if (close < 0) {
                // An unterminated marker runs to the end of the line rather than leaving the text plain,
                // which shows a producer the editor draws whatever it is given.
                spans.add(new CodeHighlightSpan(index, text.length(), style.fontColor, markerStyle));
                return spans;
            }
            spans.add(new CodeHighlightSpan(
                index, close + MARKER_LENGTH, style.fontColor, markerStyle));
            index = close + MARKER_LENGTH;
        }
        return spans;
    }

    private CodeTextStyle styleForMarker(String text, int index) {
        if (index + MARKER_LENGTH > text.length()) {
            return null;
        }
        char first = text.charAt(index);
        char second = text.charAt(index + 1);
        if (first != second) {
            return null;
        }
        switch (first) {
            case '*':
                return boldStyle;
            case '~':
                return strikeStyle;
            case '=':
                return markStyle;
            case '_':
                return underlineStyle;
            case ';':
                return doubleUnderlineStyle;
            case '-':
                return dashedStyle;
            case '.':
                return dottedStyle;
            case '!':
                return wavyStyle;
            case '@':
                return italicStyle;
            default:
                return null;
        }
    }

    private int findClose(String text, int from, char marker) {
        for (int i = from; i + 1 < text.length(); i++) {
            if (text.charAt(i) == marker && text.charAt(i + 1) == marker) {
                return i;
            }
        }
        return -1;
    }
}
