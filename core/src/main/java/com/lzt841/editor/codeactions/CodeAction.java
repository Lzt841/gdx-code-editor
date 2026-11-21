package com.lzt841.editor.codeactions;

import com.badlogic.gdx.utils.Array;
import com.lzt841.editor.CodeEditorTextEdit;

/** An immutable action and its edits against the original request snapshot. */
public final class CodeAction {
    public final String id;
    public final String title;
    public final CodeActionKind kind;
    public final boolean preferred;
    /** Null or empty when enabled; otherwise an explanation for the host to display. */
    public final String disabledReason;
    private final Array<CodeEditorTextEdit> edits;

    public CodeAction(String id, String title, Array<CodeEditorTextEdit> edits) {
        this(id, title, CodeActionKind.QUICK_FIX, false, null, edits);
    }

    public CodeAction(String id, String title, CodeActionKind kind, boolean preferred,
                      String disabledReason, Array<CodeEditorTextEdit> edits) {
        if (id == null || id.trim().isEmpty()) {
            throw new IllegalArgumentException("id must not be blank");
        }
        if (title == null || title.trim().isEmpty()) {
            throw new IllegalArgumentException("title must not be blank");
        }
        if (kind == null) {
            throw new IllegalArgumentException("kind must not be null");
        }
        if (edits == null) {
            throw new IllegalArgumentException("edits must not be null");
        }
        this.edits = new Array<>(edits);
        for (int i = 0; i < this.edits.size; i++) {
            if (this.edits.get(i) == null) {
                throw new IllegalArgumentException("edits must not contain null");
            }
        }
        this.id = id;
        this.title = title;
        this.kind = kind;
        this.preferred = preferred;
        this.disabledReason = disabledReason;
    }

    /** Returns a separate array; immutable edit values may be shared safely. */
    public Array<CodeEditorTextEdit> getEdits() {
        return new Array<>(edits);
    }

    public boolean isEnabled() {
        return disabledReason == null || disabledReason.isEmpty();
    }
}
