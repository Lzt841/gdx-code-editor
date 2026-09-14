package com.lzt841.editor;

/**
 * Describes a content mutation.
 *
 * <p><b>The full document text is not carried by this event.</b> An earlier version exposed a
 * {@code public final String text}, which meant every keystroke rebuilt the whole document into a
 * string — about 4 MB per key in a 100k line file, for a value most listeners never read. Use
 * {@link #getText()} if you really need it, and prefer the change range below.
 *
 * <p>{@link #startLine} to {@link #endLine} bound the lines that changed, in post-edit coordinates.
 * For a plain keystroke both are the caret's line. For an undo, or a paste spanning lines, the range
 * covers everything affected. When the editor cannot narrow it down — {@link #setText(String)}, or an
 * edit history too long to replay — the range covers the whole document and {@link #isWholeDocument()}
 * returns true.
 */
public class CodeEditorContentChangeEvent {
    public final CodeEditorContentChangeType type;
    public final int documentVersion;
    public final int cursorLine;
    public final int cursorColumn;
    /** First line affected, in post-edit coordinates. */
    public final int startLine;
    /** Last line affected, in post-edit coordinates, inclusive. */
    public final int endLine;
    /** Lines present in the affected range before the edit. */
    public final int removedLineCount;
    /** Lines present in the affected range after the edit. */
    public final int insertedLineCount;

    private final CodeEditor editor;

    public CodeEditorContentChangeEvent(
        CodeEditor editor,
        CodeEditorContentChangeType type,
        int documentVersion,
        int cursorLine,
        int cursorColumn,
        int startLine,
        int endLine,
        int removedLineCount,
        int insertedLineCount
    ) {
        this.editor = editor;
        this.type = type == null ? CodeEditorContentChangeType.UNKNOWN : type;
        this.documentVersion = documentVersion;
        this.cursorLine = cursorLine;
        this.cursorColumn = cursorColumn;
        this.startLine = startLine;
        this.endLine = endLine;
        this.removedLineCount = removedLineCount;
        this.insertedLineCount = insertedLineCount;
    }

    /**
     * The whole document text.
     *
     * <p>Allocates a string the size of the document on every call, so do not call it from a listener
     * that runs on every keystroke. Read {@link #getChangedText()} or the editor's line accessors
     * instead.
     */
    public String getText() {
        return editor == null ? "" : editor.getText();
    }

    /** Text of the changed lines only, joined with newlines. */
    public String getChangedText() {
        if (editor == null) {
            return "";
        }
        int lastLine = Math.min(endLine, editor.getLineCount() - 1);
        if (startLine > lastLine) {
            return "";
        }
        return editor.getTextRange(startLine, 0, lastLine, editor.getLineLength(lastLine));
    }

    /** Whether the change range could not be narrowed down and covers the entire document. */
    public boolean isWholeDocument() {
        return editor != null && startLine <= 0 && endLine >= editor.getLineCount() - 1;
    }

    /** Whether the edit changed how many lines the document has. */
    public boolean isLineCountChanged() {
        return removedLineCount != insertedLineCount;
    }

    /** Document length in characters, without building the text. */
    public int getTextLength() {
        return editor == null ? 0 : editor.getTextLength();
    }

    @Override
    public String toString() {
        return "CodeEditorContentChangeEvent{" + type + " v" + documentVersion
            + " lines " + startLine + ".." + endLine
            + " (-" + removedLineCount + "/+" + insertedLineCount + ")"
            + " caret " + cursorLine + ":" + cursorColumn + "}";
    }
}
