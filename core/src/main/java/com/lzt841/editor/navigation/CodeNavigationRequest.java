package com.lzt841.editor.navigation;

import com.lzt841.editor.CodeEditor;
import com.lzt841.editor.CodeEditorTextRange;

/**
 * Everything a {@link CodeNavigationProvider} is told about one request.
 *
 * <p>{@link #wordRange} is the identifier under the caret and {@link #word} is its text; both are null
 * and empty when the caret is not on a word, which a provider is free to answer with no targets.
 *
 * <p>There is deliberately no document offset here. Converting a line/column to an offset walks every
 * line above it, so a field nobody reads would cost O(document) on every request in a large file. A
 * provider that wants one can call {@code editor.toOffset(line, column)}.
 */
public class CodeNavigationRequest {
    public final CodeEditor editor;
    public final CodeNavigationKind kind;
    /** Caret line when the request was made. */
    public final int line;
    /** Caret column when the request was made. */
    public final int column;
    /** Document version when the request was made; a later version means this result is stale. */
    public final int documentVersion;
    /** Identifier under the caret, or null when there is none. */
    public final CodeEditorTextRange wordRange;
    /** Text of {@link #wordRange}, or empty. */
    public final String word;

    public CodeNavigationRequest(
        CodeEditor editor,
        CodeNavigationKind kind,
        int line,
        int column,
        int documentVersion,
        CodeEditorTextRange wordRange,
        String word
    ) {
        if (editor == null) {
            throw new IllegalArgumentException("editor must not be null");
        }
        this.editor = editor;
        this.kind = kind == null ? CodeNavigationKind.DEFINITION : kind;
        this.line = line;
        this.column = column;
        this.documentVersion = documentVersion;
        this.wordRange = wordRange;
        this.word = word == null ? "" : word;
    }

    /** Text of the caret's line, a convenience for providers that need surrounding context. */
    public String lineText() {
        return editor.getLineText(line);
    }

    /** Whether the caret was on an identifier. */
    public boolean hasWord() {
        return wordRange != null && !word.isEmpty();
    }

    @Override
    public String toString() {
        return "CodeNavigationRequest{" + kind + " at " + line + ":" + column + ", word='" + word + "'}";
    }
}
