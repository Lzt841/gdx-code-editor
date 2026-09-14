package com.lzt841.editor.hints;

import com.lzt841.editor.CodeEditor;
import com.lzt841.editor.CodeEditorPosition;

/**
 * Supplies the signature shown while the caret is inside a call's argument list.
 *
 * <p>{@link CodeSignatureHelpController} works out the enclosing call and the argument index by
 * counting brackets, and passes both in, so a provider usually only has to look up
 * {@code functionName}.
 */
public interface CodeSignatureHelpProvider {
    /**
     * @param editor the editor asking
     * @param position caret position
     * @param functionName identifier immediately before the call's opening parenthesis, possibly empty
     * @param activeParameter zero-based index of the argument the caret is in
     * @param response one-shot sink; complete with null to show nothing
     */
    void provideSignatureHelp(
        CodeEditor editor,
        CodeEditorPosition position,
        String functionName,
        int activeParameter,
        CodeSignatureHelpResponse response
    );
}
