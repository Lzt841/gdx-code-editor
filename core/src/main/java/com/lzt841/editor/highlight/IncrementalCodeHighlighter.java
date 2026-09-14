package com.lzt841.editor.highlight;

import com.badlogic.gdx.utils.Array;
import com.lzt841.editor.CodeEditor;

/**
 * A highlighter that can colour one line at a time given the lexical state at that line's start.
 *
 * <p>{@link CodeHighlighter} colours a whole document per call, which forces the editor to
 * re-highlight everything on every keystroke. An incremental highlighter instead threads a small
 * integer state from line to line, so the editor can:
 *
 * <ul>
 *   <li>highlight only the lines it is about to draw, and
 *   <li>after an edit, re-scan forward from the edited line only until the state matches what was
 *       already cached — usually one or two lines.
 * </ul>
 *
 * <p>The state must capture everything a line needs from the lines above it, for example "inside a
 * block comment". {@link #START_STATE} is the state at the first line of a document. States are
 * compared with {@code ==}, so encode them as small constants, not hashes.
 *
 * <p>Implementations must be pure: highlighting the same line with the same incoming state must
 * always produce the same spans and the same outgoing state, with no dependence on call order.
 */
public interface IncrementalCodeHighlighter extends CodeHighlighter {
    /** Lexical state at the start of line 0. */
    int START_STATE = 0;

    /**
     * Appends the spans for one line and returns the state the next line starts in.
     *
     * @param line the line text, without any line terminator
     * @param startState state at this line's first character, {@link #START_STATE} for line 0
     * @param style supplies the colours
     * @param spans receives this line's colour spans; already cleared, must not be reordered
     * @param bracketIgnoreSpans receives ranges where brackets must not count towards rainbow
     *     brackets or bracket matching, typically strings and comments; may be ignored
     * @return state at the start of the following line
     */
    int highlightLine(
        CharSequence line,
        int startState,
        CodeEditor.CodeEditorStyle style,
        Array<CodeHighlightSpan> spans,
        Array<CodeBracketIgnoreSpan> bracketIgnoreSpans
    );

    /**
     * Advances the state across a line without producing spans. Used to reach a distant line, so it
     * should skip span allocation where that is cheaper. The default delegates to
     * {@link #highlightLine}.
     */
    default int advanceState(CharSequence line, int startState, CodeEditor.CodeEditorStyle style) {
        return highlightLine(line, startState, style, null, null);
    }
}
