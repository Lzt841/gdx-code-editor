package com.lzt841.editor.codeactions;

import com.lzt841.editor.CodeEditorTextRange;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Immutable snapshot for code-action computation, safe to use without consulting the editor. */
public final class CodeActionRequest {
    /** Entire document with canonical LF newlines; offsets and zero-based columns use UTF-16. */
    public final String text;
    public final int documentVersion;
    public final int diagnosticVersion;
    public final long diagnosticRevision;
    /** Caret position when the request was made. */
    public final int line;
    public final int column;
    /** Selection, or an empty range at the caret when there is no selection. */
    public final CodeEditorTextRange range;
    public final int startOffset;
    public final int endOffset;
    /** Relevant diagnostics selected and validated by the controller. */
    public final List<CodeActionDiagnostic> diagnostics;

    CodeActionRequest(String text, int documentVersion, int diagnosticVersion, long diagnosticRevision,
                      int line, int column, CodeEditorTextRange range, int startOffset, int endOffset,
                      List<CodeActionDiagnostic> diagnostics) {
        if (text == null || range == null || diagnostics == null) {
            throw new IllegalArgumentException("text, range and diagnostics must not be null");
        }
        List<CodeActionDiagnostic> copy = new ArrayList<>(diagnostics);
        for (CodeActionDiagnostic diagnostic : copy) {
            if (diagnostic == null) {
                throw new IllegalArgumentException("diagnostics must not contain null");
            }
        }
        this.text = text;
        this.documentVersion = documentVersion;
        this.diagnosticVersion = diagnosticVersion;
        this.diagnosticRevision = diagnosticRevision;
        this.line = line;
        this.column = column;
        this.range = new CodeEditorTextRange(range.startLine, range.startColumn,
            range.endLine, range.endColumn);
        this.startOffset = startOffset;
        this.endOffset = endOffset;
        this.diagnostics = Collections.unmodifiableList(copy);
    }

    public String getRangeText() {
        return text.substring(startOffset, endOffset);
    }
}
