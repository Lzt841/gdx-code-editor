package com.lzt841.editor;

/**
 * Intercepts single-key edits so an implementation can do something smarter than inserting the
 * character: auto-closing brackets, wrapping a selection in quotes, deleting a pair with one
 * Backspace, splitting a brace block on Enter.
 *
 * <p>Install one with {@link CodeEditor#setAutoEditStrategy(CodeAutoEditStrategy)}. There is no
 * strategy by default, so typing behaves exactly as it did before; {@link CodeBracketAutoEditStrategy}
 * is the ready-made one.
 *
 * <p>Every method returns whether it handled the key. Returning true means the editor does nothing
 * further for that key, so the strategy must have performed the edit itself through the editor's
 * public mutators ({@link CodeEditor#insertTextAtCursor}, {@link CodeEditor#replaceRange},
 * {@link CodeEditor#deleteRange}, {@link CodeEditor#setCursorPosition}) — each of which is one undo
 * step. Returning false leaves the editor's own handling in place.
 *
 * <p>These hooks run after the editor has decided the key is an edit it would perform: the editor is
 * neither disabled nor read-only, the character passed the input filter, and any collapsed region at
 * the caret has been expanded. Implementations are called on the render thread and should not block.
 *
 * <p>All methods have default implementations that decline, so an implementation only overrides what
 * it cares about. Java 8 default methods are used deliberately: this interface is expected to grow.
 */
public interface CodeAutoEditStrategy {
    /**
     * Called before the editor inserts a typed character.
     *
     * @param character the character about to be inserted; never a control character
     * @return true when the strategy performed the edit itself
     */
    default boolean onCharacterTyped(CodeEditor editor, char character) {
        return false;
    }

    /**
     * Called before the editor's Backspace, and only when there is no selection — a selected range is
     * always just deleted.
     *
     * @return true when the strategy performed the deletion itself
     */
    default boolean onBackspace(CodeEditor editor) {
        return false;
    }

    /**
     * Called before the editor inserts a line break, including when a selection is about to be
     * replaced by it. A strategy that wants the simple case to stay simple should check
     * {@link CodeEditor#hasSelection()} and decline.
     *
     * @return true when the strategy performed the edit itself
     */
    default boolean onEnter(CodeEditor editor) {
        return false;
    }
}
