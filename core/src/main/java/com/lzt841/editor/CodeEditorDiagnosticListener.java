package com.lzt841.editor;

/** Notified on the GDX thread when an accepted diagnostic batch is replaced or cleared. */
public interface CodeEditorDiagnosticListener {
    /**
     * The revision advances independently of the document version, including same-version updates.
     * A listener may publish another batch or unregister itself; query the editor for live state.
     */
    void onDiagnosticsChanged(CodeEditor editor, long revision);
}
