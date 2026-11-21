package com.lzt841.editor.codeactions;

import com.badlogic.gdx.utils.Array;

/** Optional status callbacks, delivered on the GDX thread. Cancelled requests produce no callback. */
public interface CodeActionListener {
    default void onActionsReady(CodeActionController controller, CodeActionRequest request,
                                Array<CodeAction> actions) { }

    /** changed is false for an enabled action whose edits do not change the document. */
    default void onActionApplied(CodeActionController controller, CodeActionRequest request,
                                 CodeAction action, boolean changed) { }

    default void onCodeActionFailed(CodeActionController controller, CodeActionRequest request,
                                    String message) { }
}
