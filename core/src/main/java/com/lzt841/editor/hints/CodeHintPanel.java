package com.lzt841.editor.hints;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.utils.Drawable;
import com.badlogic.gdx.utils.Array;
import com.lzt841.editor.CodeEditor;

/**
 * A small floating text panel, used for both hover tooltips and parameter hints.
 *
 * <p>Wraps text to a maximum width, sizes itself to the result, and can highlight one run of
 * characters — parameter hints use that to mark the active argument.
 *
 * <p>Not touchable: it must never steal clicks from the editor underneath.
 */
public class CodeHintPanel extends Actor {
    /** Visual configuration; a null drawable simply skips that layer. */
    public static class CodeHintPanelStyle {
        public BitmapFont font;
        public Drawable background;
        public Color textColor = new Color(0.85f, 0.89f, 0.94f, 1f);
        /** Colour of the highlighted run, for example the active parameter. */
        public Color highlightColor = new Color(0.37f, 0.71f, 0.99f, 1f);
        public float horizontalPadding = 8f;
        public float verticalPadding = 5f;
        public float maxWidth = 480f;
        public float lineSpacing = 2f;
        /**
         * Baseline nudge, matching {@code CodeEditorStyle.textBaselineOffset}. Fonts differ in where
         * their line height sits relative to the glyphs, so this is the one value to tweak if text
         * looks vertically off inside the panel.
         */
        public float textBaselineOffset = -6f;
    }

    private final Array<String> wrappedLines = new Array<>();
    private final GlyphLayout glyphLayout = new GlyphLayout();
    private CodeHintPanelStyle style;
    private String text = "";
    private int highlightStart = -1;
    private int highlightEnd = -1;

    /**
     * A style derived from the editor's own, so a panel matches the code it floats over without any
     * extra setup. Callers that want a different accent are expected to overwrite
     * {@link CodeHintPanelStyle#textColor} and {@link CodeHintPanelStyle#highlightColor} afterwards.
     */
    public static CodeHintPanelStyle styleFrom(CodeEditor editor) {
        if (editor == null) {
            throw new IllegalArgumentException("editor must not be null");
        }
        CodeEditor.CodeEditorStyle editorStyle = editor.getStyle();
        CodeHintPanelStyle style = new CodeHintPanelStyle();
        style.font = editorStyle.font;
        style.background = editorStyle.focusedBackground != null
            ? editorStyle.focusedBackground
            : editorStyle.background;
        style.textColor.set(editorStyle.fontColor);
        style.highlightColor.set(editorStyle.keywordColor);
        style.textBaselineOffset = editorStyle.textBaselineOffset;
        return style;
    }

    public CodeHintPanel(CodeHintPanelStyle style) {
        setStyle(style);
        setVisible(false);
        setTouchable(com.badlogic.gdx.scenes.scene2d.Touchable.disabled);
    }

    public void setStyle(CodeHintPanelStyle style) {
        if (style == null || style.font == null) {
            throw new IllegalArgumentException("CodeHintPanelStyle and its font must not be null");
        }
        this.style = style;
        rewrap();
    }

    public CodeHintPanelStyle getStyle() {
        return style;
    }

    public String getText() {
        return text;
    }

    /** Sets the text and clears any highlight. */
    public void setText(String text) {
        setText(text, -1, -1);
    }

    /**
     * Sets the text and highlights {@code [highlightStart, highlightEnd)} within it. The highlight is
     * only applied on the wrapped line that contains it.
     */
    public void setText(String text, int highlightStart, int highlightEnd) {
        this.text = text == null ? "" : text;
        this.highlightStart = highlightStart;
        this.highlightEnd = highlightEnd;
        rewrap();
    }

    public boolean isEmpty() {
        return text.isEmpty();
    }

    /**
     * Positions the panel above {@code anchorX}/{@code anchorY}, flipping below when there is no room
     * above, and keeping it inside the stage horizontally.
     *
     * @param anchorY the y the panel should sit above, typically a text row's top
     * @param flipHeight height to skip when flipping below, typically one line height
     */
    public void positionAbove(Stage stage, float anchorX, float anchorY, float flipHeight) {
        if (stage == null) {
            return;
        }
        float worldWidth = stage.getViewport().getWorldWidth();
        float worldHeight = stage.getViewport().getWorldHeight();
        float x = Math.max(0f, Math.min(anchorX, worldWidth - getWidth()));
        float above = anchorY;
        float y;
        if (above + getHeight() <= worldHeight) {
            y = above;
        } else {
            float below = anchorY - flipHeight - getHeight();
            y = below >= 0f ? below : Math.max(0f, worldHeight - getHeight());
        }
        setPosition(x, y);
    }

    /** Re-wraps the text and resizes the actor to fit. */
    private void rewrap() {
        wrappedLines.clear();
        if (text.isEmpty()) {
            setSize(0f, 0f);
            return;
        }

        float contentLimit = Math.max(40f, style.maxWidth - style.horizontalPadding * 2f);
        int lineStart = 0;
        while (lineStart <= text.length()) {
            int hardBreak = text.indexOf('\n', lineStart);
            int lineEnd = hardBreak < 0 ? text.length() : hardBreak;
            wrapSegment(text.substring(lineStart, lineEnd), contentLimit);
            if (hardBreak < 0) {
                break;
            }
            lineStart = hardBreak + 1;
        }

        float widest = 0f;
        for (int i = 0; i < wrappedLines.size; i++) {
            widest = Math.max(widest, measure(wrappedLines.get(i)));
        }
        float lineHeight = style.font.getLineHeight() + style.lineSpacing;
        setSize(
            widest + style.horizontalPadding * 2f,
            wrappedLines.size * lineHeight + style.verticalPadding * 2f
        );
    }

    /** Greedy word wrap, falling back to a hard character break for a word that cannot fit. */
    private void wrapSegment(String segment, float limit) {
        if (segment.isEmpty()) {
            wrappedLines.add("");
            return;
        }
        int start = 0;
        while (start < segment.length()) {
            int lastBreak = -1;
            int index = start;
            while (index < segment.length()) {
                if (measure(segment.substring(start, index + 1)) > limit) {
                    break;
                }
                if (segment.charAt(index) == ' ') {
                    lastBreak = index + 1;
                }
                index++;
            }
            if (index >= segment.length()) {
                wrappedLines.add(segment.substring(start));
                return;
            }
            int breakAt = lastBreak > start ? lastBreak : Math.max(start + 1, index);
            wrappedLines.add(segment.substring(start, breakAt));
            start = breakAt;
        }
    }

    private float measure(String value) {
        if (value == null || value.isEmpty()) {
            return 0f;
        }
        glyphLayout.setText(style.font, value);
        return glyphLayout.width;
    }

    @Override
    public void draw(Batch batch, float parentAlpha) {
        if (wrappedLines.size == 0) {
            return;
        }
        if (style.background != null) {
            style.background.draw(batch, getX(), getY(), getWidth(), getHeight());
        }

        float lineHeight = style.font.getLineHeight() + style.lineSpacing;
        // Same baseline rule the editor uses for its rows: row bottom + line height + offset.
        float firstRowBottom = getY() + getHeight() - style.verticalPadding - lineHeight;
        float baseline = firstRowBottom + lineHeight + style.textBaselineOffset;
        int consumed = 0;
        for (int i = 0; i < wrappedLines.size; i++) {
            String line = wrappedLines.get(i);
            float x = getX() + style.horizontalPadding;
            drawLine(batch, line, consumed, x, baseline - i * lineHeight);
            consumed += line.length();
        }
    }

    /** Draws one wrapped line, splitting it where the highlight range crosses it. */
    private void drawLine(Batch batch, String line, int lineOffset, float x, float baseline) {
        int localStart = highlightStart - lineOffset;
        int localEnd = highlightEnd - lineOffset;
        boolean hasHighlight = highlightStart >= 0
            && highlightEnd > highlightStart
            && localEnd > 0
            && localStart < line.length();

        if (!hasHighlight) {
            style.font.setColor(style.textColor);
            style.font.draw(batch, line, x, baseline);
            return;
        }

        int from = Math.max(0, localStart);
        int to = Math.min(line.length(), localEnd);
        String head = line.substring(0, from);
        String middle = line.substring(from, to);
        String tail = line.substring(to);

        float cursorX = x;
        if (!head.isEmpty()) {
            style.font.setColor(style.textColor);
            style.font.draw(batch, head, cursorX, baseline);
            cursorX += measure(head);
        }
        if (!middle.isEmpty()) {
            style.font.setColor(style.highlightColor);
            style.font.draw(batch, middle, cursorX, baseline);
            cursorX += measure(middle);
        }
        if (!tail.isEmpty()) {
            style.font.setColor(style.textColor);
            style.font.draw(batch, tail, cursorX, baseline);
        }
    }
}
