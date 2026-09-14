package com.lzt841.editor;

/**
 * A diagnostic mark: an error, warning, information note or hint covering a document range.
 *
 * <p>The editor draws a squiggle under the range and an optional mark in the gutter. Hovering the
 * range can show {@link #message} via a {@code CodeHoverProvider} that consults
 * {@link CodeEditor#getDiagnosticsAt(int, int)}.
 */
public class CodeDiagnostic {
    public final int startLine;
    public final int startColumn;
    public final int endLine;
    public final int endColumn;
    public final CodeDiagnosticSeverity severity;
    public final String message;
    /** Optional machine-readable code, for example {@code unused-variable}. */
    public final String code;
    /** Free slot for the producer; the editor never reads it. */
    public final Object userData;

    public CodeDiagnostic(
        int startLine,
        int startColumn,
        int endLine,
        int endColumn,
        CodeDiagnosticSeverity severity,
        String message
    ) {
        this(startLine, startColumn, endLine, endColumn, severity, message, null, null);
    }

    public CodeDiagnostic(
        int startLine,
        int startColumn,
        int endLine,
        int endColumn,
        CodeDiagnosticSeverity severity,
        String message,
        String code,
        Object userData
    ) {
        this.startLine = startLine;
        this.startColumn = startColumn;
        this.endLine = endLine;
        this.endColumn = endColumn;
        this.severity = severity == null ? CodeDiagnosticSeverity.ERROR : severity;
        this.message = message == null ? "" : message;
        this.code = code;
        this.userData = userData;
    }

    public boolean covers(int line, int column) {
        if (line < startLine || line > endLine) {
            return false;
        }
        if (line == startLine && column < startColumn) {
            return false;
        }
        return line != endLine || column < endColumn;
    }

    public boolean overlapsLine(int line) {
        return line >= startLine && line <= endLine;
    }

    @Override
    public String toString() {
        return "CodeDiagnostic{" + severity + " " + startLine + ":" + startColumn + " " + message + "}";
    }
}
