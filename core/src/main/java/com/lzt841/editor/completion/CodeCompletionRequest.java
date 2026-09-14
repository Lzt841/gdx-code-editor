package com.lzt841.editor.completion;

import com.lzt841.editor.CodeEditor;
import com.lzt841.editor.CodeEditorTextRange;

/**
 * Everything a {@link CodeCompletionProvider} is told about one completion request.
 *
 * <p>{@link #replaceRange} is the range the accepted item will replace, normally the word being
 * typed, and {@link #prefix} is the text already typed inside it. A provider should filter on
 * {@code prefix} but does not have to: the popup filters again on whatever it is given.
 */
public class CodeCompletionRequest {
    public final CodeEditor editor;
    /** Caret line when the request was made. */
    public final int line;
    /** Caret column when the request was made. */
    public final int column;
    /** Document character offset of the caret. */
    public final int offset;
    /** Document version when the request was made; a later version means this result is stale. */
    public final int documentVersion;
    /** Word characters immediately before the caret, possibly empty. */
    public final String prefix;
    /** Range an accepted item replaces. */
    public final CodeEditorTextRange replaceRange;
    /** What caused the request. */
    public final CodeCompletionTrigger trigger;
    /** The character that triggered it, or {@code '\0'} for a manual or prefix trigger. */
    public final char triggerCharacter;

    public CodeCompletionRequest(
        CodeEditor editor,
        int line,
        int column,
        int offset,
        int documentVersion,
        String prefix,
        CodeEditorTextRange replaceRange,
        CodeCompletionTrigger trigger,
        char triggerCharacter
    ) {
        this.editor = editor;
        this.line = line;
        this.column = column;
        this.offset = offset;
        this.documentVersion = documentVersion;
        this.prefix = prefix == null ? "" : prefix;
        this.replaceRange = replaceRange;
        this.trigger = trigger == null ? CodeCompletionTrigger.MANUAL : trigger;
        this.triggerCharacter = triggerCharacter;
    }

    /** Text of the caret's line, a convenience for providers that need surrounding context. */
    public String lineText() {
        return editor.getLineText(line);
    }

    /** Text on the caret's line before the caret. */
    public String linePrefix() {
        String text = editor.getLineText(line);
        int safe = Math.max(0, Math.min(column, text.length()));
        return text.substring(0, safe);
    }

    @Override
    public String toString() {
        return "CodeCompletionRequest{" + line + ":" + column + ", prefix='" + prefix
            + "', trigger=" + trigger + "}";
    }
}
