package com.lzt841.editor.codeactions;

/** Immutable instruction to replace a matching diagnostic's range with literal text. */
public final class CodeDiagnosticFix {
    public final String diagnosticCode;
    public final String title;
    public final String replacement;
    public final boolean preferred;

    public CodeDiagnosticFix(String diagnosticCode, String title, String replacement) {
        this(diagnosticCode, title, replacement, true);
    }

    public CodeDiagnosticFix(String diagnosticCode, String title, String replacement, boolean preferred) {
        if (diagnosticCode == null || diagnosticCode.trim().isEmpty()) {
            throw new IllegalArgumentException("diagnosticCode must not be blank");
        }
        if (title == null || title.trim().isEmpty()) {
            throw new IllegalArgumentException("title must not be blank");
        }
        if (replacement == null) {
            throw new IllegalArgumentException("replacement must not be null");
        }
        this.diagnosticCode = diagnosticCode;
        this.title = title;
        this.replacement = replacement;
        this.preferred = preferred;
    }
}
