package com.lzt841.editor.highlight;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.ObjectSet;
import com.lzt841.editor.CodeEditor;

/** Built-in highlighter for JavaScript and TypeScript-like code. */
public class JavaScriptCodeHighlighter extends AbstractIncrementalHighlighter {
    /** Continuation state: the line begins inside a block comment. */
    private static final int STATE_BLOCK_COMMENT = 1;
    /** Continuation state: the line begins inside a back-tick template string. */
    private static final int STATE_TEMPLATE_STRING = 2;

    private static final ObjectSet<String> KEYWORDS = ObjectSet.with(
        "await", "break", "case", "catch", "class", "const", "continue", "debugger",
        "default", "delete", "do", "else", "export", "extends", "finally", "for",
        "function", "if", "import", "in", "instanceof", "let", "new", "of", "return",
        "super", "switch", "this", "throw", "try", "typeof", "var", "void", "while",
        "with", "yield", "as", "from", "async"
    );

    private static final ObjectSet<String> TYPES = ObjectSet.with(
        "Array", "Boolean", "Date", "Error", "Map", "Number", "Object", "Promise",
        "RegExp", "Set", "String", "Symbol"
    );

    private static final ObjectSet<String> LITERALS = ObjectSet.with(
        "false", "null", "true", "undefined", "NaN", "Infinity"
    );

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
        boolean inTemplateString = startState == STATE_TEMPLATE_STRING;
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

            if (inTemplateString) {
                int end = HighlighterSupport.readString(line, index, '`');
                if (wantSpans) {
                    HighlighterSupport.addSpan(spans, index, end, style.stringColor);
                }
                HighlighterSupport.addIgnoreSpan(bracketIgnoreSpans, index, end);
                inTemplateString = templateStringContinues(line, end);
                index = end;
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
            if (c == '"' || c == '\'') {
                int end = HighlighterSupport.readString(line, index, c);
                if (wantSpans) {
                    HighlighterSupport.addSpan(spans, index, end, style.stringColor);
                }
                HighlighterSupport.addIgnoreSpan(bracketIgnoreSpans, index, end);
                index = end;
                continue;
            }
            if (c == '`') {
                int end = HighlighterSupport.readString(line, index, '`');
                if (wantSpans) {
                    HighlighterSupport.addSpan(spans, index, end, style.stringColor);
                }
                HighlighterSupport.addIgnoreSpan(bracketIgnoreSpans, index, end);
                inTemplateString = templateStringContinues(line, end);
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
            if (Character.isJavaIdentifierStart(c) || c == '$') {
                int end = HighlighterSupport.readIdentifier(line, index, "$");
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
        return inTemplateString ? STATE_TEMPLATE_STRING : 0;
    }

    /**
     * Whether a template string that was scanned up to {@code end} runs on to the next line. Keeps
     * the original heuristic: the scan reached the end of the line without a closing back-tick.
     */
    private static boolean templateStringContinues(CharSequence line, int end) {
        int length = line.length();
        return end >= length && (length == 0 || line.charAt(length - 1) != '`');
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
