package com.lzt841.editor.navigation;

import com.lzt841.editor.CodeEditor;
import com.lzt841.editor.CodeEditorTextRange;

/**
 * Turns "rename this to that" into a batch of edits.
 *
 * <p>Two phases, as in LSP. {@link #prepareRename} runs before the host asks the user for a name and
 * says whether the position can be renamed at all, so a UI can refuse up front instead of popping a
 * dialog and rejecting the answer afterwards. {@link #provideRenameEdits} then produces the edits.
 *
 * <p>A provider must not edit the document itself. It returns edits and the controller applies them
 * through {@link CodeEditor#applyEdits}, which is what makes the whole rename one undo step and what
 * makes an invalid batch fail cleanly rather than half-way.
 */
public interface CodeRenameProvider {
    /**
     * The range that would be renamed at this position, or null when nothing there can be.
     *
     * <p>Synchronous on purpose: it runs on a key press, before any dialog, and a host needs the answer
     * to decide whether to show one. Keep it cheap — no document-wide scan.
     *
     * <p>The default is the identifier under the position, which is right for most languages.
     */
    default CodeEditorTextRange prepareRename(CodeEditor editor, int line, int column) {
        return editor.getWordRangeAt(line, column);
    }

    /**
     * Produces the edits for {@code request} and delivers them to {@code response}, or calls
     * {@link CodeRenameResponse#fail} with a reason. May answer later, from the GDX thread; a reply
     * that arrives after the document has moved on is dropped by the controller.
     */
    void provideRenameEdits(CodeRenameRequest request, CodeRenameResponse response);
}
