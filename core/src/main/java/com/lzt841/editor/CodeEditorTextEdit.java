package com.lzt841.editor;

/**
 * One immutable "replace this range with this text" instruction.
 *
 * <p>A batch of these is what a rename, a quick fix or a formatter produces, and
 * {@link CodeEditor#applyEdits} applies a batch as a single undo step. Positions are line/column and
 * refer to the document <em>before</em> any edit in the batch is applied, so a producer can walk the
 * document once and emit edits in whatever order it finds them.
 *
 * <p>An empty range is a pure insertion; an empty replacement is a pure deletion.
 */
public final class CodeEditorTextEdit {
    public final CodeEditorTextRange range;
    /** Text that replaces {@link #range}. Never null; empty means delete. */
    public final String replacement;

    public CodeEditorTextEdit(CodeEditorTextRange range, String replacement) {
        if (range == null) {
            throw new IllegalArgumentException("range must not be null");
        }
        this.range = range;
        this.replacement = replacement == null ? "" : replacement;
    }

    public CodeEditorTextEdit(int startLine, int startColumn, int endLine, int endColumn, String replacement) {
        this(new CodeEditorTextRange(startLine, startColumn, endLine, endColumn), replacement);
    }

    public static CodeEditorTextEdit replace(CodeEditorTextRange range, String replacement) {
        return new CodeEditorTextEdit(range, replacement);
    }

    public static CodeEditorTextEdit insert(int line, int column, String text) {
        return new CodeEditorTextEdit(line, column, line, column, text);
    }

    public static CodeEditorTextEdit delete(CodeEditorTextRange range) {
        return new CodeEditorTextEdit(range, "");
    }

    /** Whether this edit changes nothing: an empty range with an empty replacement. */
    public boolean isNoOp() {
        return range.isEmpty() && replacement.isEmpty();
    }

    @Override
    public String toString() {
        return "CodeEditorTextEdit{" + range + " -> '" + replacement + "'}";
    }
}
