package com.lzt841.editor.formatting;

/** A host-owned document or selection formatter; the controller never disposes this provider. */
public interface CodeFormattingProvider {
    /**
     * Called on the GDX thread. May complete synchronously or retain the response. For asynchronous
     * work, use only the immutable request on a worker and post complete/fail back to the GDX thread.
     * Every returned edit must stay inside request.range and use the original snapshot's positions.
     * Report unsupported languages, incomplete selections or invalid syntax through response.fail.
     */
    void format(CodeFormattingRequest request, CodeFormattingResponse response);
}
