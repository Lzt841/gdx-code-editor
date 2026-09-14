package com.lzt841.editor.navigation;

import com.badlogic.gdx.utils.Array;
import com.lzt841.editor.CodeEditorTextEdit;

/**
 * One-shot sink for a {@link CodeRenameProvider}. The first call wins; later ones, and any call after
 * a newer request has superseded this one, do nothing.
 *
 * <p>{@link #fail} exists separately from completing with nothing because a rename that cannot be done
 * has a reason worth showing the user — "that is a keyword", "the symbol is declared in a library" —
 * and an empty edit list cannot carry one.
 */
public interface CodeRenameResponse {
    /**
     * Delivers the edits to apply. Must be called from the GDX thread. An empty or null list is
     * treated as a failure with no message. The array may be retained, so do not mutate it afterwards.
     */
    void complete(Array<CodeEditorTextEdit> edits);

    /** Reports that the rename cannot be done. {@code message} may be null. */
    void fail(String message);
}
