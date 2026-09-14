package com.lzt841.editor;

import com.lzt841.editor.completion.CodeCompletionController;

/**
 * Named editor commands that a {@link CodeKeymap} can bind to a key chord.
 *
 * <p>The editor owns the {@code MOVE_*} / edit / clipboard actions. The
 * {@code COMPLETION_*} actions are owned by {@link CodeCompletionController} and the
 * {@code NAVIGATE_*} ones by
 * {@link com.lzt841.editor.navigation.CodeNavigationController}: they live here so a single keymap
 * type can describe them all, and so a custom overlay can reuse the same chords.
 */
public enum CodeEditorAction {
    MOVE_LEFT,
    MOVE_RIGHT,
    MOVE_UP,
    MOVE_DOWN,
    MOVE_PAGE_UP,
    MOVE_PAGE_DOWN,
    MOVE_LINE_START,
    MOVE_LINE_END,

    BACKSPACE,
    DELETE,
    NEW_LINE,
    /** Insert one indent, or indent every line of a multi-line selection. */
    INDENT,
    /** Remove one indent from the current line or selection. */
    DEDENT,
    TOGGLE_FOLD,

    UNDO,
    REDO,
    SELECT_ALL,
    COPY,
    CUT,
    PASTE,

    /** Ctrl-Space by default; opens the completion popup. */
    COMPLETION_TRIGGER,
    COMPLETION_PREVIOUS,
    COMPLETION_NEXT,
    COMPLETION_PAGE_UP,
    COMPLETION_PAGE_DOWN,
    COMPLETION_FIRST,
    COMPLETION_LAST,
    COMPLETION_ACCEPT,
    COMPLETION_DISMISS,

    /** F12 by default; asks the navigation provider where the symbol under the caret is defined. */
    NAVIGATE_TO_DEFINITION,
    /** Shift+F12 by default; asks for every use of the symbol under the caret. */
    NAVIGATE_FIND_REFERENCES,
    /**
     * Shift+F6 by default; starts a rename of the symbol under the caret. Not F2, which this editor
     * has always used for {@link #TOGGLE_FOLD}.
     */
    NAVIGATE_RENAME;

    /**
     * Whether extra Ctrl/Alt on top of this action's default chord should still fire it. Historical
     * behaviour: arrow keys, Tab, Enter, Backspace and F2 ran even with leftover modifiers. Chorded
     * actions such as {@link #COPY} do not, so Ctrl+Alt+C does not copy unless bound.
     */
    public boolean ignoresExtraModifiers() {
        switch (this) {
            case MOVE_LEFT:
            case MOVE_RIGHT:
            case MOVE_UP:
            case MOVE_DOWN:
            case MOVE_PAGE_UP:
            case MOVE_PAGE_DOWN:
            case MOVE_LINE_START:
            case MOVE_LINE_END:
            case BACKSPACE:
            case DELETE:
            case NEW_LINE:
            case INDENT:
            case DEDENT:
            case TOGGLE_FOLD:
            case COMPLETION_PREVIOUS:
            case COMPLETION_NEXT:
            case COMPLETION_PAGE_UP:
            case COMPLETION_PAGE_DOWN:
            case COMPLETION_FIRST:
            case COMPLETION_LAST:
            case COMPLETION_ACCEPT:
            case COMPLETION_DISMISS:
                return true;
            default:
                return false;
        }
    }

    /** Caret-moving actions, which Shift extends a selection over. */
    public boolean isMovement() {
        switch (this) {
            case MOVE_LEFT:
            case MOVE_RIGHT:
            case MOVE_UP:
            case MOVE_DOWN:
            case MOVE_PAGE_UP:
            case MOVE_PAGE_DOWN:
            case MOVE_LINE_START:
            case MOVE_LINE_END:
                return true;
            default:
                return false;
        }
    }
}
