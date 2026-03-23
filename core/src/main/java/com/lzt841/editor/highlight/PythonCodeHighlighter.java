package com.lzt841.editor.highlight;

import com.badlogic.gdx.utils.ObjectSet;
import com.badlogic.gdx.utils.Array;
import com.lzt841.editor.CodeEditor;

/** Built-in highlighter for Python source files. */
public class PythonCodeHighlighter implements CodeHighlighter {
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
    public Array<Array<CodeHighlightSpan>> highlight(Array<String> lines, CodeEditor.CodeEditorStyle style) {
        Array<Array<CodeHighlightSpan>> result = new Array<>(lines.size);
        boolean inTripleString = false;
        String tripleDelimiter = null;

        for (String line : lines) {
            Array<CodeHighlightSpan> spans = new Array<>();
            int index = 0;

            while (index < line.length()) {
                if (inTripleString) {
                    int end = line.indexOf(tripleDelimiter, index);
                    if (end < 0) {
                        HighlighterSupport.addSpan(spans, index, line.length(), style.stringColor);
                        break;
                    }
                    HighlighterSupport.addSpan(spans, index, end + 3, style.stringColor);
                    index = end + 3;
                    inTripleString = false;
                    tripleDelimiter = null;
                    continue;
                }

                if (line.charAt(index) == '#') {
                    HighlighterSupport.addSpan(spans, index, line.length(), style.commentColor);
                    break;
                }

                int prefixLength = getStringPrefixLength(line, index);
                if (prefixLength >= 0) {
                    int quoteIndex = index + prefixLength;
                    char quote = line.charAt(quoteIndex);
                    if (quoteIndex + 2 < line.length()
                        && line.charAt(quoteIndex + 1) == quote
                        && line.charAt(quoteIndex + 2) == quote) {
                        String delimiter = repeatQuote(quote);
                        int end = line.indexOf(delimiter, quoteIndex + 3);
                        if (end < 0) {
                            HighlighterSupport.addSpan(spans, index, line.length(), style.stringColor);
                            inTripleString = true;
                            tripleDelimiter = delimiter;
                            break;
                        }
                        HighlighterSupport.addSpan(spans, index, end + 3, style.stringColor);
                        index = end + 3;
                        continue;
                    }
                    int end = HighlighterSupport.readString(line, quoteIndex, quote);
                    HighlighterSupport.addSpan(spans, index, end, style.stringColor);
                    index = end;
                    continue;
                }

                char c = line.charAt(index);
                if (c == '@') {
                    int end = HighlighterSupport.readIdentifier(line, index + 1, ".");
                    HighlighterSupport.addSpan(spans, index, Math.max(index + 1, end), style.annotationColor);
                    index = Math.max(index + 1, end);
                    continue;
                }
                if (HighlighterSupport.isNumberStart(line, index)) {
                    int end = HighlighterSupport.readNumber(line, index);
                    HighlighterSupport.addSpan(spans, index, end, style.numberColor);
                    index = end;
                    continue;
                }
                if (Character.isJavaIdentifierStart(c)) {
                    int end = HighlighterSupport.readIdentifier(line, index);
                    String token = line.substring(index, end);
                    if (KEYWORDS.contains(token)) {
                        HighlighterSupport.addSpan(spans, index, end, style.keywordColor);
                    } else if (TYPES.contains(token)) {
                        HighlighterSupport.addSpan(spans, index, end, style.typeColor);
                    } else if (LITERALS.contains(token)) {
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

    @Override
    public Array<Array<CodeBracketIgnoreSpan>> getBracketIgnoreSpans(Array<String> lines) {
        Array<Array<CodeBracketIgnoreSpan>> result = new Array<>(lines.size);
        boolean inTripleString = false;
        String tripleDelimiter = null;

        for (String line : lines) {
            Array<CodeBracketIgnoreSpan> spans = new Array<>();
            int index = 0;
            while (index < line.length()) {
                if (inTripleString) {
                    int end = line.indexOf(tripleDelimiter, index);
                    if (end < 0) {
                        HighlighterSupport.addIgnoreSpan(spans, index, line.length());
                        break;
                    }
                    HighlighterSupport.addIgnoreSpan(spans, index, end + 3);
                    index = end + 3;
                    inTripleString = false;
                    tripleDelimiter = null;
                    continue;
                }

                int prefixLength = getStringPrefixLength(line, index);
                if (prefixLength >= 0) {
                    int quoteIndex = index + prefixLength;
                    char quote = line.charAt(quoteIndex);
                    if (quoteIndex + 2 < line.length()
                        && line.charAt(quoteIndex + 1) == quote
                        && line.charAt(quoteIndex + 2) == quote) {
                        String delimiter = repeatQuote(quote);
                        int end = line.indexOf(delimiter, quoteIndex + 3);
                        if (end < 0) {
                            HighlighterSupport.addIgnoreSpan(spans, index, line.length());
                            inTripleString = true;
                            tripleDelimiter = delimiter;
                            break;
                        }
                        HighlighterSupport.addIgnoreSpan(spans, index, end + 3);
                        index = end + 3;
                        continue;
                    }
                }

                index++;
            }
            result.add(spans);
        }

        return result;
    }

    private int getStringPrefixLength(String line, int start) {
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

    private String repeatQuote(char quote) {
        return new String(new char[] {quote, quote, quote});
    }
}
