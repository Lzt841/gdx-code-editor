package com.lzt841.editor.highlight;

import com.badlogic.gdx.utils.Array;
import com.lzt841.editor.CodeEditor;

/** No-op highlighter for plain text editing. */
public class PlainTextHighlighter implements CodeHighlighter {
    @Override
    public Array<Array<CodeHighlightSpan>> highlight(Array<String> lines, CodeEditor.CodeEditorStyle style) {
        Array<Array<CodeHighlightSpan>> result = new Array<>(lines.size);
        for (int i = 0; i < lines.size; i++) {
            result.add(new Array<CodeHighlightSpan>(0));
        }
        return result;
    }
}
