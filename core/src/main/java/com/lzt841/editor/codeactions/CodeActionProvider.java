package com.lzt841.editor.codeactions;

/** A host-owned source of actions; the controller never disposes the provider. */
public interface CodeActionProvider {
    /**
     * Called on the GDX thread. May complete synchronously or retain the response. For asynchronous
     * computation, use only the immutable request on a worker and deliver complete/fail on the GDX
     * thread. Returned edits use the original snapshot's positions.
     */
    void provideActions(CodeActionRequest request, CodeActionResponse response);
}
