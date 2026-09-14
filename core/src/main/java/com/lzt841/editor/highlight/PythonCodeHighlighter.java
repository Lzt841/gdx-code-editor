package com.lzt841.editor.highlight;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.ObjectSet;
import com.lzt841.editor.CodeEditor;

/** Built-in highlighter for Python source files. */
public class PythonCodeHighlighter extends AbstractIncrementalHighlighter {
    /** Continuation state: the line begins inside a {@code '''} string. */
    private static final int STATE_TRIPLE_SINGLE = 1;
    /** Continuation state: the line begins inside a {@code """} string. */
    private static final int STATE_TRIPLE_DOUBLE = 2;

    private static final String TRIPLE_SINGLE = "'''";
    private static final String TRIPLE_DOUBLE = "\"\"\"";

    private static final ObjectSet<String> KEYWORDS = ObjectSet.with(
        "and", "as", "assert", "async", "await", "break", "class", "continue", "def",
        "del", "elif", "else", "except", "finally", "for", "from", "global", "if",
        "import", "in", "is", "lambda", "nonlocal", "not", "or", "pass", "raise",
        "return", "try", "while", "with", "yield", "match", "case"
    );

    private static final ObjectSet<String> TYPES = ObjectSet.with(
        "bool", "bytes", "dict", "float", "frozenset", "int", "list", "object", "set",
        "str", "tuple"
    );

    private static final ObjectSet<String> LITERALS = ObjectSet.with("True", "False", "None");

    @Override
    public int highlightLine(
        CharSequence line,
        int startState,
        CodeEditor.CodeEditorStyle style,
        Array<CodeHighlightSpan> spans,
        Array<CodeBracketIgnoreSpan> bracketIgnoreSpans
    ) {
        boolean wantSpans = spans != null && style != null;
        String tripleDelimiter = delimiterForState(startState);
        int index = 0;
        int length = line.length();

        while (index < length) {
            if (tripleDelimiter != null) {
                int end = HighlighterSupport.indexOf(line, tripleDelimiter, index);
                if (end < 0) {
                    if (wantSpans) {
                        HighlighterSupport.addSpan(spans, index, length, style.stringColor);
                    }
                    HighlighterSupport.addIgnoreSpan(bracketIgnoreSpans, index, length);
                    return stateForDelimiter(tripleDelimiter);
                }
                if (wantSpans) {
                    HighlighterSupport.addSpan(spans, index, end + 3, style.stringColor);
                }
                HighlighterSupport.addIgnoreSpan(bracketIgnoreSpans, index, end + 3);
                index = end + 3;
                tripleDelimiter = null;
                continue;
            }

            if (line.charAt(index) == '#') {
                if (wantSpans) {
                    HighlighterSupport.addSpan(spans, index, length, style.commentColor);
                }
                HighlighterSupport.addIgnoreSpan(bracketIgnoreSpans, index, length);
                return 0;
            }

            int prefixLength = getStringPrefixLength(line, index);
            if (prefixLength >= 0) {
                int quoteIndex = index + prefixLength;
                char quote = line.charAt(quoteIndex);
                if (quoteIndex + 2 < length
                    && line.charAt(quoteIndex + 1) == quote
                    && line.charAt(quoteIndex + 2) == quote) {
                    String delimiter = quote == '\'' ? TRIPLE_SINGLE : TRIPLE_DOUBLE;
                    int end = HighlighterSupport.indexOf(line, delimiter, quoteIndex + 3);
                    if (end < 0) {
                        if (wantSpans) {
                            HighlighterSupport.addSpan(spans, index, length, style.stringColor);
                        }
                        HighlighterSupport.addIgnoreSpan(bracketIgnoreSpans, index, length);
                        return stateForDelimiter(delimiter);
                    }
                    if (wantSpans) {
                        HighlighterSupport.addSpan(spans, index, end + 3, style.stringColor);
                    }
                    HighlighterSupport.addIgnoreSpan(bracketIgnoreSpans, index, end + 3);
                    index = end + 3;
                    continue;
                }
                int end = HighlighterSupport.readString(line, quoteIndex, quote);
                if (wantSpans) {
                    HighlighterSupport.addSpan(spans, index, end, style.stringColor);
                }
                HighlighterSupport.addIgnoreSpan(bracketIgnoreSpans, index, end);
                index = end;
                continue;
            }

            char c = line.charAt(index);
            if (c == '@') {
                int end = HighlighterSupport.readIdentifier(line, index + 1, ".");
                int stop = Math.max(index + 1, end);
                if (wantSpans) {
                    HighlighterSupport.addSpan(spans, index, stop, style.annotationColor);
                }
                index = stop;
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

        return tripleDelimiter == null ? 0 : stateForDelimiter(tripleDelimiter);
    }

    private static String delimiterForState(int state) {
        if (state == STATE_TRIPLE_SINGLE) {
            return TRIPLE_SINGLE;
        }
        return state == STATE_TRIPLE_DOUBLE ? TRIPLE_DOUBLE : null;
    }

    private static int stateForDelimiter(String delimiter) {
        return TRIPLE_SINGLE.equals(delimiter) ? STATE_TRIPLE_SINGLE : STATE_TRIPLE_DOUBLE;
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

    private static int getStringPrefixLength(CharSequence line, int start) {
        if (!HighlighterSupport.isIdentifierBoundary(line, start)) {
            return -1;
        }
        int index = start;
        while (index < line.length()) {
            char c = line.charAt(index);
            if (c == '\'' || c == '"') {
                return index - start;
            }
            if ("rRuUbBfF".indexOf(c) < 0 || index - start >= 2) {
                return -1;
            }
            index++;
        }
        return -1;
    }
}
