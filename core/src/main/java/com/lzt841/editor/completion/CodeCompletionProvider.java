package com.lzt841.editor.completion;

import com.badlogic.gdx.utils.Array;

/**
 * Supplies completion candidates for a {@link com.lzt841.editor.CodeEditor}.
 *
 * <p>The editor never assumes the answer is ready immediately. Call
 * {@link CodeCompletionResponse#complete(Array)} from the GDX thread when the list is ready — from
 * {@link #provide} itself for a cheap lookup, or later via {@code Gdx.app.postRunnable} after a
 * background parse. A later call with a newer {@link CodeCompletionRequest#documentVersion} is
 * ignored automatically.
 *
 * <p>A provider that has nothing to offer should still call {@code complete} with an empty array so
 * the popup can close.
 */
public interface CodeCompletionProvider {
    /**
     * Called on the GDX thread. Implementations that do non-trivial work should return immediately
     * and complete the response later, rather than stalling the frame.
     *
     * @param request snapshot of the caret and the word being completed
     * @param response one-shot sink for the resulting items
     */
    void provide(CodeCompletionRequest request, CodeCompletionResponse response);

    /**
     * Whether typing {@code character} at the caret should open or refresh the popup. The default
     * treats {@code '.'} as a trigger, which is the usual "member access" convention.
     */
    default boolean isTriggerCharacter(char character) {
        return character == '.';
    }
}
