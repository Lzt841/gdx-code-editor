package com.lzt841.editor.hints;

/**
 * One-shot sink for a {@link CodeHoverProvider}. Calling {@link #complete} twice, or after the
 * pointer has moved on, is a no-op.
 */
public interface CodeHoverResponse {
    /**
     * Shows {@code text} as the tooltip, or hides it when {@code text} is null or empty. Must be
     * called from the GDX thread. Newlines are honoured.
     */
    void complete(String text);
}
