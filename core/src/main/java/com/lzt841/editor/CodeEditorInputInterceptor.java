package com.lzt841.editor;

/**
 * Lets an overlay see keyboard input before the editor acts on it.
 *
 * <p>An open completion popup needs Up, Down, Enter, Tab and Escape for itself; without this hook the
 * editor would move the caret instead. Returning true from {@link #onKeyDown} or
 * {@link #onKeyTyped} consumes the event.
 *
 * <p>Interceptors are consulted in registration order and the first to consume wins. This exists so
 * the editor does not have to depend on the completion, hover or parameter-hint code.
 */
public interface CodeEditorInputInterceptor {
    /**
     * @param keycode a {@code com.badlogic.gdx.Input.Keys} constant
     * @return true to consume the key
     */
    default boolean onKeyDown(CodeEditor editor, int keycode) {
        return false;
    }

    /**
     * Called before the character is inserted.
     *
     * @return true to consume it, so nothing is typed
     */
    default boolean onKeyTyped(CodeEditor editor, char character) {
        return false;
    }

    /** Called after a character has been inserted, for triggering on what was typed. */
    default void afterKeyTyped(CodeEditor editor, char character) {
    }
}
