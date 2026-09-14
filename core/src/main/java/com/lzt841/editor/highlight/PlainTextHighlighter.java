package com.lzt841.editor.highlight;

import com.badlogic.gdx.utils.Array;
import com.lzt841.editor.CodeEditor;

/** No-op highlighter for plain text editing. */
public class PlainTextHighlighter extends AbstractIncrementalHighlighter {
    @Override
    public int highlightLine(
        CharSequence line,
        int startState,
        CodeEditor.CodeEditorStyle style,
        Array<CodeHighlightSpan> spans,
        Array<CodeBracketIgnoreSpan> bracketIgnoreSpans
    ) {
        return START_STATE;
    }

    @Override
    public int advanceState(CharSequence line, int startState, CodeEditor.CodeEditorStyle style) {
        return START_STATE;
    }
}
