package com.lzt841.editor;

/** Immutable event payload describing a content mutation. */
public class CodeEditorContentChangeEvent {
    public final CodeEditorContentChangeType type;
    public final String text;
    public final int documentVersion;
    public final int cursorLine;
    public final int cursorColumn;

    public CodeEditorContentChangeEvent(
        CodeEditorContentChangeType type,
        String text,
        int documentVersion,
        int cursorLine,
        int cursorColumn
    ) {
        this.type = type == null ? CodeEditorContentChangeType.UNKNOWN : type;
        this.text = text == null ? "" : text;
        this.documentVersion = documentVersion;
        this.cursorLine = cursorLine;
        this.cursorColumn = cursorColumn;
    }
}
