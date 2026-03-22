package com.lzt841.editor.highlight;

import com.lzt841.editor.CodeEditor;

import java.util.ArrayList;
import java.util.List;

/** No-op highlighter for plain text editing. */
public class PlainTextHighlighter implements CodeHighlighter {
    @Override
    public List<List<CodeHighlightSpan>> highlight(List<String> lines, CodeEditor.CodeEditorStyle style) {
        ArrayList<List<CodeHighlightSpan>> result = new ArrayList<>(lines.size());
        for (int i = 0; i < lines.size(); i++) {
            result.add(new ArrayList<CodeHighlightSpan>(0));
        }
        return result;
    }
}
