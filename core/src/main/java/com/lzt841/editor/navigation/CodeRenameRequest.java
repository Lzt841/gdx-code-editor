package com.lzt841.editor.navigation;

import com.lzt841.editor.CodeEditor;
import com.lzt841.editor.CodeEditorTextRange;

/**
 * Everything a {@link CodeRenameProvider} is told about one rename.
 *
 * <p>{@link #range} is the occurrence the caret was on — the one the user pointed at — and
 * {@link #oldName} is its current text. A provider is expected to find the other occurrences itself;
 * that search is the part only it can do correctly.
 */
public class CodeRenameRequest {
    public final CodeEditor editor;
    /** Caret line when the rename was asked for. */
    public final int line;
    /** Caret column when the rename was asked for. */
    public final int column;
    /** Document version when the rename was asked for; a later version means this result is stale. */
    public final int documentVersion;
    /** The occurrence under the caret. Never null. */
    public final CodeEditorTextRange range;
    /** Current text of {@link #range}. Never null or empty. */
    public final String oldName;
    /** What the user typed. Never null or empty. */
    public final String newName;

    public CodeRenameRequest(
        CodeEditor editor,
        int line,
        int column,
        int documentVersion,
        CodeEditorTextRange range,
        String oldName,
        String newName
    ) {
        if (editor == null) {
            throw new IllegalArgumentException("editor must not be null");
        }
        if (range == null) {
            throw new IllegalArgumentException("range must not be null");
        }
        if (oldName == null || oldName.isEmpty()) {
            throw new IllegalArgumentException("oldName must not be empty");
        }
        if (newName == null || newName.isEmpty()) {
            throw new IllegalArgumentException("newName must not be empty");
        }
        this.editor = editor;
        this.line = line;
        this.column = column;
        this.documentVersion = documentVersion;
        this.range = range;
        this.oldName = oldName;
        this.newName = newName;
    }

    @Override
    public String toString() {
        return "CodeRenameRequest{'" + oldName + "' -> '" + newName + "' at " + line + ":" + column + "}";
    }
}
