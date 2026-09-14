package com.lzt841.editor;

/**
 * Notified when the caret or the selection moves.
 *
 * <p>{@link CodeEditorContentListener} only fires when text changes, so it misses arrow keys, clicks
 * and selection changes. A completion popup needs those to decide whether to re-filter or dismiss.
 *
 * <p>Delivered from {@link CodeEditor#act(float)} by comparing against the previous frame, so a move
 * and any edit that caused it arrive as one notification, at most one frame later.
 */
public interface CodeEditorCaretListener {
    /**
     * @param editor the editor whose caret moved
     * @param position the new caret position
     * @param selection the new selection, or {@code null} when nothing is selected
     * @param causedByEdit whether the document also changed since the last notification
     */
    void onCaretMoved(
        CodeEditor editor,
        CodeEditorPosition position,
        CodeEditorTextRange selection,
        boolean causedByEdit
    );
}
