package com.lzt841.editor.formatting;

import com.lzt841.editor.CodeEditorTextRange;

/** Immutable snapshot for a formatter. Only this data, never the editor, may be used by a worker. */
public final class CodeFormattingRequest {
    /** Entire document with canonical LF newlines; positions use zero-based UTF-16 columns. */
    public final String text;
    public final int documentVersion;
    /** Requested range, inclusive start and exclusive end, in the original snapshot. */
    public final CodeEditorTextRange range;
    public final int startOffset;
    public final int endOffset;
    public final boolean selectionOnly;
    public final int indentWidth;
    public final boolean useTabs;

    CodeFormattingRequest(String text, int documentVersion, CodeEditorTextRange range,
                          int startOffset, int endOffset, boolean selectionOnly,
                          int indentWidth, boolean useTabs) {
        this.text = text;
        this.documentVersion = documentVersion;
        this.range = range;
        this.startOffset = startOffset;
        this.endOffset = endOffset;
        this.selectionOnly = selectionOnly;
        this.indentWidth = indentWidth;
        this.useTabs = useTabs;
    }

    public String getRangeText() { return text.substring(startOffset, endOffset); }

    /** Leading indentation of the selected value's source line. The first output line omits it. */
    public String getBaseIndent() {
        if (!selectionOnly) return "";
        int start = startOffset == 0 ? 0 : text.lastIndexOf('\n', startOffset - 1) + 1;
        int end = start;
        while (end < startOffset && (text.charAt(end) == ' ' || text.charAt(end) == '\t')) end++;
        return text.substring(start, end);
    }
}
