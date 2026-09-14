package com.lzt841.editor.hints;

/** One-shot sink for a {@link CodeSignatureHelpProvider}. */
public interface CodeSignatureHelpResponse {
    /**
     * Shows {@code help}, or hides the hint when it is null. Must be called from the GDX thread.
     */
    void complete(CodeSignatureHelp help);
}
