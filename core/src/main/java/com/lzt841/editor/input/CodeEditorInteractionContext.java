package com.lzt841.editor.input;

/** Interaction callback payload for long press, right click, and double click. */
public class CodeEditorInteractionContext {
    public final float x;
    public final float y;
    public final int line;
    public final int column;
    public final String selectedText;
    public final boolean touch;

    public CodeEditorInteractionContext(float x, float y, int line, int column, String selectedText, boolean touch) {
        this.x = x;
        this.y = y;
        this.line = line;
        this.column = column;
        this.selectedText = selectedText;
        this.touch = touch;
    }
}
