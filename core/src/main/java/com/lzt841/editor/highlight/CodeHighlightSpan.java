package com.lzt841.editor.highlight;

import com.badlogic.gdx.graphics.Color;

/**
 * A colored span produced by a syntax highlighter.
 *
 * <p>Optionally carries a {@link CodeTextStyle}, so a lexer can underline a pragma, strike a macro,
 * or tint a preprocessor region's background without a second pass over the line. Null means plain
 * text and is what every pre-existing constructor still produces.
 */
public class CodeHighlightSpan {
    public final int start;
    public final int end;
    public final Color color;
    /** How the span is drawn, or null for an undecorated run. */
    public final CodeTextStyle style;

    public CodeHighlightSpan(int start, int end, Color color) {
        this(start, end, color, null);
    }

    public CodeHighlightSpan(int start, int end, Color color, CodeTextStyle style) {
        this.start = start;
        this.end = end;
        this.color = color;
        this.style = style;
    }
}
