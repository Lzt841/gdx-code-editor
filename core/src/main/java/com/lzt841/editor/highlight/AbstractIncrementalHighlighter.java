package com.lzt841.editor.highlight;

import com.badlogic.gdx.utils.Array;
import com.lzt841.editor.CodeEditor;

/**
 * Implements the whole-document {@link CodeHighlighter} contract on top of a single
 * {@link #highlightLine} method, so a highlighter written once works with both the fast incremental
 * path and any code still calling the batch API.
 */
public abstract class AbstractIncrementalHighlighter implements IncrementalCodeHighlighter {
    @Override
    public Array<Array<CodeHighlightSpan>> highlight(Array<String> lines, CodeEditor.CodeEditorStyle style) {
        Array<Array<CodeHighlightSpan>> result = new Array<>(lines.size);
        int state = START_STATE;
        for (int i = 0; i < lines.size; i++) {
            Array<CodeHighlightSpan> spans = new Array<>();
            state = highlightLine(lines.get(i), state, style, spans, null);
            result.add(spans);
        }
        return result;
    }

    @Override
    public Array<Array<CodeBracketIgnoreSpan>> getBracketIgnoreSpans(Array<String> lines) {
        Array<Array<CodeBracketIgnoreSpan>> result = new Array<>(lines.size);
        int state = START_STATE;
        for (int i = 0; i < lines.size; i++) {
            Array<CodeBracketIgnoreSpan> ignored = new Array<>();
            state = highlightLine(lines.get(i), state, null, null, ignored);
            result.add(ignored);
        }
        return result;
    }
}
