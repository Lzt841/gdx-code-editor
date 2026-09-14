package com.lzt841.editor.highlight;

import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.ObjectSet;
import com.lzt841.editor.CodeEditor;

/** Default Java-like syntax highlighter. */
public class JavaCodeHighlighter extends AbstractIncrementalHighlighter {
    /** Continuation state: the line begins inside a block comment. */
    private static final int STATE_BLOCK_COMMENT = 1;

    private static final ObjectSet<String> KEYWORDS = ObjectSet.with(
        "abstract", "assert", "break", "case", "catch", "class", "const", "continue",
        "default", "do", "else", "enum", "extends", "final", "finally", "for", "goto",
        "if", "implements", "import", "instanceof", "interface", "native", "new", "package",
        "private", "protected", "public", "return", "static", "strictfp", "super", "switch",
        "synchronized", "this", "throw", "throws", "transient", "try", "volatile", "while",
        "module", "open", "opens", "exports", "requires", "uses", "provides", "to", "with",
        "record", "sealed", "permits", "yield"
    );

    private static final ObjectSet<String> TYPES = ObjectSet.with(
        "boolean", "byte", "char", "double", "float", "int", "long", "short", "void", "String", "var"
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

            char c = line.charAt(index);
            if (c == '@') {
                int end = HighlighterSupport.readIdentifier(line, index + 1);
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

        // Preserves the state for an empty or whitespace-only line inside a block comment, which
        // never enters the loop body.
        return inBlockComment ? STATE_BLOCK_COMMENT : 0;
    }

    private static com.badlogic.gdx.graphics.Color colorForWord(
        CharSequence line,
        int start,
        int end,
        CodeEditor.CodeEditorStyle style
    ) {
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
