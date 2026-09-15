package com.lzt841.editor;

/**
 * Notified when the editor scrolls, and when a touch pan or the inertial scrolling that follows it
 * starts and ends.
 *
 * <p>{@link #onScrollChanged} covers every source — mouse wheel, scrollbar thumb, the editor scrolling
 * itself to keep the caret visible, {@link CodeEditor#setScroll(float, float)}, pinch zoom and a linked
 * diff pane — because it is driven by the offset rather than by the gestures that move it. The
 * remaining callbacks describe the drag and fling gestures alone, which is what a caller needs in order
 * to tell a user's pan apart from the editor moving on its own.
 *
 * <p>Changes are detected once per frame, in {@link CodeEditor#act(float)}. A burst of wheel events
 * inside one frame therefore arrives as a single {@link #onScrollChanged} carrying the summed delta,
 * and a widget that is not being acted on — off-stage, or inside a halted stage — reports nothing at
 * all. {@link CodeEditor#isTouchScrollDragging()} and {@link CodeEditor#isFlinging()} read live state
 * and have no such caveat.
 *
 * <p>Within one frame the callbacks are ordered so a gesture reads the way it happened: the pan and
 * fling transitions first, then the scroll offset itself. A release that flings therefore arrives as
 * {@link #onTouchScrollFinished}, {@link #onFlingStarted}, then the first {@link #onScrollChanged} the
 * fling produced.
 */
public interface CodeEditorScrollListener {

    /**
     * The scroll offset changed.
     *
     * @param scrollX offset after the change, in pixels
     * @param scrollY offset after the change, in pixels
     * @param deltaX change since the previous notification, in pixels
     * @param deltaY change since the previous notification, in pixels
     */
    void onScrollChanged(CodeEditor editor, float scrollX, float scrollY, float deltaX, float deltaY);

    /**
     * The offset started moving, whatever moved it. Fires immediately before the first
     * {@link #onScrollChanged} of a scroll, and not again until that scroll finishes.
     */
    default void onScrollStarted(CodeEditor editor, float scrollX, float scrollY) {
    }

    /**
     * The offset stopped moving, once the pan and any fling behind it have finished.
     *
     * <p>Not fired while {@link CodeEditor#isTouchScrollDragging()} or {@link CodeEditor#isFlinging()}
     * still hold: a finger held still mid-pan pauses the scroll rather than ending it.
     */
    default void onScrollFinished(CodeEditor editor, float scrollX, float scrollY) {
    }

    /**
     * A drag started panning the content, once it has passed the axis lock.
     *
     * <p>Touch drags are the case this exists for, but a mouse drag that started in the gutter pans
     * through the same state and reports here too; see {@link CodeEditor#isTouchScrollDragging()}.
     *
     * <p>Which axis the gesture is working on shows up in the velocity: on a locked axis the other one
     * is reported as zero.
     */
    default void onTouchScrollStarted(CodeEditor editor) {
    }

    /** The touch drag ended. {@code true} when a fling is taking over, in which case {@link #onFlingStarted} follows. */
    default void onTouchScrollFinished(CodeEditor editor, boolean continuesIntoFling) {
    }

    /**
     * Inertial scrolling started, on the first frame after a touch drag was released above the fling
     * threshold.
     *
     * <p>The velocity is read on that frame, which is already one damping step into the coast, so it
     * sits slightly below the speed the finger was moving at. Good enough to size an indicator with;
     * not a measurement of the release.
     *
     * @param velocityX pixels per second the coast is carrying, or zero on a locked-out axis
     * @param velocityY pixels per second the coast is carrying, or zero on a locked-out axis
     */
    default void onFlingStarted(CodeEditor editor, float velocityX, float velocityY) {
    }

    /** Inertial scrolling finished, either by damping below the threshold or by a new gesture. */
    default void onFlingFinished(CodeEditor editor) {
    }
}
