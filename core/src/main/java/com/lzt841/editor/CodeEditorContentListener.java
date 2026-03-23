package com.lzt841.editor;

/** Listener for editor content mutations such as typing, delete, replace, undo and redo. */
public interface CodeEditorContentListener {
    void onContentChanged(CodeEditor editor, CodeEditorContentChangeEvent event);
}
