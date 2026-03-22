package com.lzt841.editor.highlight;

import com.lzt841.editor.CodeEditor;

import java.util.List;

/** Generates syntax highlight spans for a document snapshot. */
public interface CodeHighlighter {
    List<List<CodeHighlightSpan>> highlight(List<String> lines, CodeEditor.CodeEditorStyle style);

    default List<List<CodeBracketIgnoreSpan>> getBracketIgnoreSpans(List<String> lines) {
        return HighlighterSupport.emptyIgnoreSpans(lines.size());
    }
}
