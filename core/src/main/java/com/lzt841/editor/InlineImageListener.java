package com.lzt841.editor;

/**
 * Reacts to the pointer landing on an inline image: clicks, hover-enter and hover-exit.
 *
 * <p><b>Why this exists.</b> An image column is measured exactly like a glyph, so the editor's own
 * click handling resolves it to a cursor position and moves the caret — the right default for text, and
 * useless for an icon that is a button. A listener gets the call <em>before</em> the caret moves, so it
 * can consume the click and the editor does not treat the image as a character the user typed past.
 *
 * <p><b>Consuming a click.</b> Return true from {@link #onImageClicked} and the editor neither moves the
 * caret nor clears a selection: the image owned that press. Return false and the press falls through to
 * the ordinary text behaviour, which is the right answer for an image that is decorative. A consumed
 * click still does not consume the drag or the double-click that follow a single click — those are
 * separate gestures, and a double-click on an image still selects the word around it.
 *
 * <p><b>Hover.</b> Hover is reported the moment the pointer enters an image — there is no rest delay, as
 * there is for text hover, because this is a hit test rather than an inference that the user paused. It
 * fires again when the pointer leaves that image. Both the entered and the exited report the position
 * that is being left, so a listener can tear down exactly what it built. There is no hover while the
 * pointer is down, and none in password mode, where there are no images at all.
 *
 * <p><b>When hover ends.</b> Besides the pointer leaving the image, an end is reported when it leaves
 * the editor, when the content scrolls under it, when password mode is entered, and when the listener
 * or the provider is replaced — in every case so a listener does not keep a tooltip or a highlight for
 * an image that can no longer be drawn. A document edit that moves an image out from under a still
 * pointer is not among them; that self-heals at the next pointer move, which is the same staleness the
 * text hover has.
 *
 * <p><b>Threading.</b> Every method is called on the render thread, from the input event that caused it.
 *
 * @see InlineImageHit
 * @see CodeEditor#setInlineImageListener
 */
public interface InlineImageListener {
    /**
     * A press-and-release landed on an image.
     *
     * @param editor the editor the event came from
     * @param hit where the image is
     * @param localX pointer x, in the editor's local coordinates
     * @param localY pointer y, in the editor's local coordinates
     * @return true to consume the click — the caret stays put and any selection is kept
     */
    boolean onImageClicked(CodeEditor editor, InlineImageHit hit, float localX, float localY);

    /**
     * The pointer is resting on an image. Fires only on entry, not continuously while it stays.
     *
     * @param editor the editor the event came from
     * @param hit where the image is
     * @param localX pointer x, in the editor's local coordinates
     * @param localY pointer y, in the editor's local coordinates
     */
    void onImageHover(CodeEditor editor, InlineImageHit hit, float localX, float localY);

    /**
     * The pointer left the image that {@link #onImageHover} reported, or the hover was cancelled: the
     * pointer left the editor, the content scrolled under it, password mode was entered, or the listener
     * or the provider was replaced.
     *
     * @param editor the editor the event came from
     * @param hit the position that is no longer hovered; never null, but always the position that was
     *            previously reported, so a listener can compare against what it stored
     */
    void onImageHoverEnd(CodeEditor editor, InlineImageHit hit);
}
