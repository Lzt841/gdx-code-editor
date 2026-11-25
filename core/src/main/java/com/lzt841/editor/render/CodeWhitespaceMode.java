package com.lzt841.editor.render;

/** Which ASCII spaces and tabs get visible markers. Does not change the document or its layout. */
public enum CodeWhitespaceMode {
    NONE,
    ALL,
    /** Only spaces and tabs at the end of a logical line, including whitespace-only lines. */
    TRAILING,
    /** Only spaces and tabs inside the current selection. */
    SELECTION
}
