package com.lzt841.editor.formatting;

/** Optional status callbacks, delivered on the GDX thread. Cancelled requests produce no callback. */
public interface CodeFormattingListener {
    /** changed is false for an already formatted document; no undo entry is made in that case. */
    default void onFormatted(CodeFormattingController controller, CodeFormattingRequest request, boolean changed) { }
    default void onFormattingFailed(CodeFormattingController controller, CodeFormattingRequest request, String message) { }
}
