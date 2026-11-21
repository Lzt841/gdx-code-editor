package com.lzt841.editor.codeactions;

import com.badlogic.gdx.utils.Array;

/** One-shot action sink. Late, duplicate, and cancelled answers are ignored by the controller. */
public interface CodeActionResponse {
    /** Deliver on the GDX thread. Empty means no actions; null is an invalid answer. */
    void complete(Array<CodeAction> actions);

    /** Deliver an explanatory failure on the GDX thread without changing the document. */
    void fail(String message);

    /** The only response operation safe to call on a worker; final acceptance rechecks live state. */
    boolean isCancelled();
}
