package com.lzt841.editor.highlight;

import com.badlogic.gdx.utils.Array;
import com.lzt841.editor.CodeEditor;

/** Generates syntax highlight spans for a document snapshot. */
public interface CodeHighlighter {
    Array<Array<CodeHighlightSpan>> highlight(Array<String> lines, CodeEditor.CodeEditorStyle style);

    default Array<Array<CodeBracketIgnoreSpan>> getBracketIgnoreSpans(Array<String> lines) {
        return HighlighterSupport.emptyIgnoreSpans(lines.size);
    }
}
