package com.lzt841.editor.highlight;

import com.badlogic.gdx.utils.Array;
import com.lzt841.editor.CodeEditor;

/** Built-in highlighter for JSON documents. */
public class JsonCodeHighlighter extends AbstractIncrementalHighlighter {
    @Override
    public int highlightLine(
        CharSequence line,
        int startState,
        CodeEditor.CodeEditorStyle style,
        Array<CodeHighlightSpan> spans,
        Array<CodeBracketIgnoreSpan> bracketIgnoreSpans
    ) {
        boolean wantSpans = spans != null && style != null;
        int index = 0;
        int length = line.length();

        while (index < length) {
            char c = line.charAt(index);
            if (c == '"') {
                int end = HighlighterSupport.readString(line, index, '"');
                if (wantSpans) {
                    int afterString = HighlighterSupport.skipWhitespace(line, end);
                    boolean isKey = afterString < length && line.charAt(afterString) == ':';
                    HighlighterSupport.addSpan(spans, index, end, isKey ? style.annotationColor : style.stringColor);
                }
                HighlighterSupport.addIgnoreSpan(bracketIgnoreSpans, index, end);
                index = end;
                continue;
            }
            if (HighlighterSupport.isNumberStart(line, index)) {
                int end = HighlighterSupport.readNumber(line, index);
                if (wantSpans) {
                    HighlighterSupport.addSpan(spans, index, end, style.numberColor);
                }
                index = end;
                continue;
            }
            if (Character.isLetter(c)) {
                int end = HighlighterSupport.readIdentifier(line, index);
                if (wantSpans && isJsonLiteral(line, index, end)) {
                    HighlighterSupport.addSpan(spans, index, end, style.literalColor);
                }
                index = end;
                continue;
            }
            index++;
        }

        return START_STATE;
    }

    private static boolean isJsonLiteral(CharSequence line, int start, int end) {
        return HighlighterSupport.regionEquals(line, start, end, "true")
            || HighlighterSupport.regionEquals(line, start, end, "false")
            || HighlighterSupport.regionEquals(line, start, end, "null");
    }
}
