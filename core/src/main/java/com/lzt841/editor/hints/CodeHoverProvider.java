package com.lzt841.editor.hints;

import com.lzt841.editor.CodeEditor;
import com.lzt841.editor.CodeEditorPosition;

/**
 * Supplies the text shown when the pointer rests over a document position.
 *
 * <p>Like completion, the answer may arrive later: call
 * {@link CodeHoverResponse#complete(String)} from the GDX thread when it is ready, or with
 * {@code null} when there is nothing to show.
 */
public interface CodeHoverProvider {
    /**
     * @param editor the editor being hovered
     * @param position document position under the pointer
     * @param response one-shot sink for the tooltip text
     */
    void provideHover(CodeEditor editor, CodeEditorPosition position, CodeHoverResponse response);
}
