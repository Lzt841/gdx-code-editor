package com.lzt841.editor;

/** Describes the kind of content mutation that happened in the editor. */
public enum CodeEditorContentChangeType {
    UNKNOWN,
    SET_TEXT,
    INSERT,
    DELETE,
    CUT,
    PASTE,
    REPLACE_CURRENT,
    REPLACE_ALL,
    UNDO,
    REDO
}
