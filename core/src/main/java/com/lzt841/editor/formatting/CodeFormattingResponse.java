package com.lzt841.editor.formatting;

import com.badlogic.gdx.utils.Array;
import com.lzt841.editor.CodeEditorTextEdit;

/** One-shot formatter sink. Late, duplicate and cancelled answers are ignored. */
public interface CodeFormattingResponse {
    /** Deliver on the GDX thread. Empty edits mean already formatted; null is an invalid answer. */
    void complete(Array<CodeEditorTextEdit> edits);
    /** Deliver an explanatory failure on the GDX thread without modifying the document. */
    void fail(String message);
    /** Advisory cancellation flag, safe to poll from a worker. Final acceptance rechecks live state. */
    boolean isCancelled();
}
