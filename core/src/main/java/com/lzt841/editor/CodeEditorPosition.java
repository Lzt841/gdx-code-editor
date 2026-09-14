package com.lzt841.editor;

/**
 * An immutable caret position: a line, a column, and the document character offset for the same
 * spot. Returned by the {@link CodeEditor} query API so callers can work in whichever form suits
 * them without recomputing the conversion.
 */
public final class CodeEditorPosition {
    public final int line;
    public final int column;
    public final int offset;

    public CodeEditorPosition(int line, int column, int offset) {
        this.line = line;
        this.column = column;
        this.offset = offset;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof CodeEditorPosition)) {
            return false;
        }
        CodeEditorPosition that = (CodeEditorPosition) other;
        return line == that.line && column == that.column && offset == that.offset;
    }

    @Override
    public int hashCode() {
        return (line * 31 + column) * 31 + offset;
    }

    @Override
    public String toString() {
        return "CodeEditorPosition{line=" + line + ", column=" + column + ", offset=" + offset + "}";
    }
}
