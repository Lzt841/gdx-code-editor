package com.lzt841.editor.codeactions;

import com.lzt841.editor.CodeDiagnostic;
import com.lzt841.editor.CodeDiagnosticSeverity;
import com.lzt841.editor.CodeEditorTextRange;

/** Immutable diagnostic data, without producer user data, subclass state, or an editor reference. */
public final class CodeActionDiagnostic {
    public final CodeEditorTextRange range;
    public final CodeDiagnosticSeverity severity;
    public final String message;
    public final String code;

    public CodeActionDiagnostic(CodeDiagnostic diagnostic) {
        if (diagnostic == null) {
            throw new IllegalArgumentException("diagnostic must not be null");
        }
        this.range = new CodeEditorTextRange(diagnostic.startLine, diagnostic.startColumn,
            diagnostic.endLine, diagnostic.endColumn);
        this.severity = diagnostic.severity;
        this.message = diagnostic.message;
        this.code = diagnostic.code;
    }
}
