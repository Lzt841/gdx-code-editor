package com.lzt841.editor.completion;

import com.badlogic.gdx.utils.Array;

/**
 * One-shot sink for a {@link CodeCompletionProvider}. Calling {@link #complete} more than once, or
 * after the request has been cancelled, is a no-op.
 */
public interface CodeCompletionResponse {
    /**
     * Delivers the candidate list. Must be called from the GDX thread. {@code items} may be empty
     * (the popup then closes) and may be retained by the editor, so do not mutate it afterwards.
     */
    void complete(Array<CodeCompletionItem> items);

    /**
     * Whether the caller no longer needs this response. Check on the GDX thread before starting or
     * delivering expensive work. Existing sinks default to false; complete remains one-shot.
     */
    default boolean isCancelled() { return false; }
}
