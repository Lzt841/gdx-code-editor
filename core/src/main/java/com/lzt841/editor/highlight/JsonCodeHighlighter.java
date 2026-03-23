package com.lzt841.editor.highlight;

import com.badlogic.gdx.utils.Array;
import com.lzt841.editor.CodeEditor;

/** Built-in highlighter for JSON documents. */
public class JsonCodeHighlighter implements CodeHighlighter {
    @Override
    public Array<Array<CodeHighlightSpan>> highlight(Array<String> lines, CodeEditor.CodeEditorStyle style) {
        Array<Array<CodeHighlightSpan>> result = new Array<>(lines.size);
        for (String line : lines) {
            Array<CodeHighlightSpan> spans = new Array<>();
            int index = 0;

            while (index < line.length()) {
                char c = line.charAt(index);
                if (c == '"') {
                    int end = HighlighterSupport.readString(line, index, '"');
                    int afterString = HighlighterSupport.skipWhitespace(line, end);
                    boolean isKey = afterString < line.length() && line.charAt(afterString) == ':';
                    HighlighterSupport.addSpan(spans, index, end, isKey ? style.annotationColor : style.stringColor);
                    index = end;
                    continue;
                }
                if (HighlighterSupport.isNumberStart(line, index)) {
                    int end = HighlighterSupport.readNumber(line, index);
                    HighlighterSupport.addSpan(spans, index, end, style.numberColor);
                    index = end;
                    continue;
                }
                if (Character.isLetter(c)) {
                    int end = HighlighterSupport.readIdentifier(line, index);
                    String token = line.substring(index, end);
                    if ("true".equals(token) || "false".equals(token) || "null".equals(token)) {
                        HighlighterSupport.addSpan(spans, index, end, style.literalColor);
                    }
                    index = end;
                    continue;
                }
                index++;
            }

            result.add(spans);
        }
        return result;
    }
}
