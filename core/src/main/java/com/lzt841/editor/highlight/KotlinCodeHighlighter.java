package com.lzt841.editor.highlight;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.ObjectSet;
import com.lzt841.editor.CodeEditor;

/** Built-in highlighter for Kotlin source files. */
public class KotlinCodeHighlighter extends AbstractIncrementalHighlighter {
    /** Continuation state: the line begins inside a block comment. */
    private static final int STATE_BLOCK_COMMENT = 1;
    /** Continuation state: the line begins inside a triple-quoted raw string. */
    private static final int STATE_RAW_STRING = 2;

    private static final ObjectSet<String> KEYWORDS = ObjectSet.with(
        "as", "break", "class", "continue", "do", "else", "false", "for", "fun", "if",
        "in", "interface", "is", "null", "object", "package", "return", "super", "this",
        "throw", "true", "try", "typealias", "typeof", "val", "var", "when", "while",
        "by", "catch", "constructor", "delegate", "dynamic", "field", "file", "finally",
        "get", "import", "init", "param", "property", "receiver", "set", "setparam",
        "where", "actual", "abstract", "annotation", "companion", "const", "crossinline",
        "data", "enum", "expect", "external", "final", "infix", "inline", "inner",
        "internal", "lateinit", "noinline", "open", "operator", "out", "override",
        "private", "protected", "public", "reified", "sealed", "suspend", "tailrec",
        "value", "vararg"
    );

    private static final ObjectSet<String> TYPES = ObjectSet.with(
        "Any", "Array", "Boolean", "Byte", "Char", "Double", "Float", "Int", "Long",
        "Nothing", "Short", "String", "Unit", "List", "MutableList", "Map", "Set"
    );

    private static final ObjectSet<String> LITERALS = ObjectSet.with("true", "false", "null");

    @Override
    public int highlightLine(
        CharSequence line,
        int startState,
        CodeEditor.CodeEditorStyle style,
        Array<CodeHighlightSpan> spans,
        Array<CodeBracketIgnoreSpan> bracketIgnoreSpans
    ) {
        boolean wantSpans = spans != null && style != null;
        boolean inBlockComment = startState == STATE_BLOCK_COMMENT;
        boolean inRawString = startState == STATE_RAW_STRING;
        int index = 0;
        int length = line.length();

        while (index < length) {
            if (inBlockComment) {
                int end = HighlighterSupport.indexOf(line, "*/", index);
                if (end < 0) {
                    if (wantSpans) {
                        HighlighterSupport.addSpan(spans, index, length, style.commentColor);
                    }
                    HighlighterSupport.addIgnoreSpan(bracketIgnoreSpans, index, length);
                    return STATE_BLOCK_COMMENT;
                }
                if (wantSpans) {
                    HighlighterSupport.addSpan(spans, index, end + 2, style.commentColor);
                }
                HighlighterSupport.addIgnoreSpan(bracketIgnoreSpans, index, end + 2);
                index = end + 2;
                inBlockComment = false;
                continue;
            }

            if (inRawString) {
                int end = HighlighterSupport.indexOf(line, "\"\"\"", index);
                if (end < 0) {
                    if (wantSpans) {
                        HighlighterSupport.addSpan(spans, index, length, style.stringColor);
                    }
                    HighlighterSupport.addIgnoreSpan(bracketIgnoreSpans, index, length);
                    return STATE_RAW_STRING;
                }
                if (wantSpans) {
                    HighlighterSupport.addSpan(spans, index, end + 3, style.stringColor);
                }
                HighlighterSupport.addIgnoreSpan(bracketIgnoreSpans, index, end + 3);
                index = end + 3;
                inRawString = false;
                continue;
            }

            if (HighlighterSupport.startsWith(line, "//", index)) {
                if (wantSpans) {
                    HighlighterSupport.addSpan(spans, index, length, style.commentColor);
                }
                HighlighterSupport.addIgnoreSpan(bracketIgnoreSpans, index, length);
                return 0;
            }
            if (HighlighterSupport.startsWith(line, "/*", index)) {
                int end = HighlighterSupport.indexOf(line, "*/", index + 2);
                if (end < 0) {
                    if (wantSpans) {
                        HighlighterSupport.addSpan(spans, index, length, style.commentColor);
                    }
                    HighlighterSupport.addIgnoreSpan(bracketIgnoreSpans, index, length);
                    return STATE_BLOCK_COMMENT;
                }
                if (wantSpans) {
                    HighlighterSupport.addSpan(spans, index, end + 2, style.commentColor);
                }
                HighlighterSupport.addIgnoreSpan(bracketIgnoreSpans, index, end + 2);
                index = end + 2;
                continue;
            }
            if (HighlighterSupport.startsWith(line, "\"\"\"", index)) {
                int end = HighlighterSupport.indexOf(line, "\"\"\"", index + 3);
                if (end < 0) {
                    if (wantSpans) {
                        HighlighterSupport.addSpan(spans, index, length, style.stringColor);
                    }
                    HighlighterSupport.addIgnoreSpan(bracketIgnoreSpans, index, length);
                    return STATE_RAW_STRING;
                }
                if (wantSpans) {
                    HighlighterSupport.addSpan(spans, index, end + 3, style.stringColor);
                }
                HighlighterSupport.addIgnoreSpan(bracketIgnoreSpans, index, end + 3);
                index = end + 3;
                continue;
            }

            char c = line.charAt(index);
            if (c == '@') {
                int end = HighlighterSupport.readIdentifier(line, index + 1, "[]");
                int stop = Math.max(index + 1, end);
                if (wantSpans) {
                    HighlighterSupport.addSpan(spans, index, stop, style.annotationColor);
                }
                index = stop;
                continue;
            }
            if (c == '"' || c == '\'') {
                int end = HighlighterSupport.readString(line, index, c);
                if (wantSpans) {
                    HighlighterSupport.addSpan(spans, index, end, style.stringColor);
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
            if (Character.isJavaIdentifierStart(c)) {
                int end = HighlighterSupport.readIdentifier(line, index);
                if (wantSpans) {
                    HighlighterSupport.addSpan(spans, index, end, colorForWord(line, index, end, style));
                }
                index = end;
                continue;
            }
            index++;
        }

        if (inBlockComment) {
            return STATE_BLOCK_COMMENT;
        }
        return inRawString ? STATE_RAW_STRING : 0;
    }

    private static Color colorForWord(CharSequence line, int start, int end, CodeEditor.CodeEditorStyle style) {
        String token = line.subSequence(start, end).toString();
        if (KEYWORDS.contains(token)) {
            return style.keywordColor;
        }
        if (TYPES.contains(token)) {
            return style.typeColor;
        }
        if (LITERALS.contains(token)) {
            return style.literalColor;
        }
        return null;
    }
}
