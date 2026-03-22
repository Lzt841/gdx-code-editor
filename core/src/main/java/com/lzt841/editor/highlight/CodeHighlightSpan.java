package com.lzt841.editor.highlight;

import com.badlogic.gdx.graphics.Color;

/** A colored span produced by a syntax highlighter. */
public class CodeHighlightSpan {
    public final int start;
    public final int end;
    public final Color color;

    public CodeHighlightSpan(int start, int end, Color color) {
        this.start = start;
        this.end = end;
        this.color = color;
    }
}
