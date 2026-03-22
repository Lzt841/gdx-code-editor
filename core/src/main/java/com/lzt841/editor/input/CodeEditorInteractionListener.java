package com.lzt841.editor.input;

import com.lzt841.editor.CodeEditor;

/** Optional callbacks for platform-specific editor interactions. */
public interface CodeEditorInteractionListener {
    boolean onLongPress(CodeEditor editor, CodeEditorInteractionContext context);

    boolean onSecondaryClick(CodeEditor editor, CodeEditorInteractionContext context);

    boolean onDoubleClick(CodeEditor editor, CodeEditorInteractionContext context);
}
