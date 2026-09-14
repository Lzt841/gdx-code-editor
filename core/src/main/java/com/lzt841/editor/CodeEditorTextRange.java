package com.lzt841.editor;

/**
 * An immutable half-open line/column range, {@code start} inclusive and {@code end} exclusive.
 *
 * <p>Used for selections, the word or token under a position, and the range a completion should
 * replace.
 */
public final class CodeEditorTextRange {
    public final int startLine;
    public final int startColumn;
    public final int endLine;
    public final int endColumn;

    public CodeEditorTextRange(int startLine, int startColumn, int endLine, int endColumn) {
        this.startLine = startLine;
        this.startColumn = startColumn;
        this.endLine = endLine;
        this.endColumn = endColumn;
    }

    public boolean isEmpty() {
        return startLine == endLine && startColumn == endColumn;
    }

    public boolean isSingleLine() {
        return startLine == endLine;
    }

    /** Whether {@code line}/{@code column} lies in the range, end exclusive. */
    public boolean contains(int line, int column) {
        if (line < startLine || line > endLine) {
            return false;
        }
        if (line == startLine && column < startColumn) {
            return false;
        }
        return line != endLine || column < endColumn;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof CodeEditorTextRange)) {
            return false;
        }
        CodeEditorTextRange that = (CodeEditorTextRange) other;
        return startLine == that.startLine && startColumn == that.startColumn
            && endLine == that.endLine && endColumn == that.endColumn;
    }

    @Override
    public int hashCode() {
        return ((startLine * 31 + startColumn) * 31 + endLine) * 31 + endColumn;
    }

    @Override
    public String toString() {
        return "CodeEditorTextRange{" + startLine + ":" + startColumn + " -> " + endLine + ":" + endColumn + "}";
    }
}
