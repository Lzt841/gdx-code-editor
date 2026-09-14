package com.lzt841.editor;

/**
 * Notified when the mouse rests over a document position, or leaves it.
 *
 * <p>The editor debounces the movement, so {@link #onHoverStart} fires once the pointer has been
 * still over the same position for {@link CodeEditor#setHoverDelay(float)} seconds. Moving to a
 * different position or off the widget fires {@link #onHoverEnd} first.
 *
 * <p>Only meaningful with a mouse; touch interaction uses long press instead, which is delivered
 * through {@link com.lzt841.editor.input.CodeEditorInteractionListener}.
 */
public interface CodeEditorHoverListener {
    /**
     * @param position document position under the pointer
     * @param localX pointer x in the editor's local coordinates
     * @param localY pointer y in the editor's local coordinates
     */
    void onHoverStart(CodeEditor editor, CodeEditorPosition position, float localX, float localY);

    /** The pointer moved off the hovered position, or off the editor. */
    void onHoverEnd(CodeEditor editor);
}
