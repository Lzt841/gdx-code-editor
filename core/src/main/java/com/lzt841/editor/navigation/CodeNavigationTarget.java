package com.lzt841.editor.navigation;

import com.lzt841.editor.CodeEditorTextRange;

/**
 * One place a navigation request resolved to.
 *
 * <p>{@link #documentId} is how this supports more than one file without the editor knowing anything
 * about files. Null means "the document currently in this editor", which
 * {@link CodeNavigationController} navigates to directly. Anything else is handed to the host through
 * {@link CodeNavigationListener#onNavigateToOtherDocument}: only the host knows how to open a tab, so
 * the controller does not guess.
 *
 * <p>{@link #range} is the symbol's own extent — the name, not its whole body — and the caret goes to
 * its start. {@link #label} and {@link #detail} exist for the list a host shows when a request
 * resolves to several targets; both may be empty.
 */
public final class CodeNavigationTarget {
    /** Host-defined document identity, or null for the editor's current document. */
    public final String documentId;
    public final CodeEditorTextRange range;
    /** Short display text, e.g. the enclosing method name. Never null; empty when unknown. */
    public final String label;
    /** Secondary display text, e.g. the line's source. Never null; empty when unknown. */
    public final String detail;

    public CodeNavigationTarget(String documentId, CodeEditorTextRange range, String label, String detail) {
        if (range == null) {
            throw new IllegalArgumentException("range must not be null");
        }
        this.documentId = documentId;
        this.range = range;
        this.label = label == null ? "" : label;
        this.detail = detail == null ? "" : detail;
    }

    /** A target in the editor's current document. */
    public static CodeNavigationTarget inCurrentDocument(CodeEditorTextRange range) {
        return new CodeNavigationTarget(null, range, "", "");
    }

    /** A target in the editor's current document, with display text for a result list. */
    public static CodeNavigationTarget inCurrentDocument(CodeEditorTextRange range, String label, String detail) {
        return new CodeNavigationTarget(null, range, label, detail);
    }

    /** Whether this target lives in the document the editor is showing. */
    public boolean isCurrentDocument() {
        return documentId == null;
    }

    public int getLine() {
        return range.startLine;
    }

    public int getColumn() {
        return range.startColumn;
    }

    @Override
    public String toString() {
        return "CodeNavigationTarget{" + (documentId == null ? "<current>" : documentId) + " " + range + "}";
    }
}
