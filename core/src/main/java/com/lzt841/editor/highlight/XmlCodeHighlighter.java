package com.lzt841.editor.highlight;

import com.badlogic.gdx.utils.Array;
import com.lzt841.editor.CodeEditor;

/** Built-in highlighter for XML and HTML-like markup. */
public class XmlCodeHighlighter extends AbstractIncrementalHighlighter {
    /** Continuation state: the line begins inside a {@code <!-- -->} comment. */
    private static final int STATE_COMMENT = 1;
    /** Continuation state: the line begins inside a {@code <![CDATA[ ]]>} section. */
    private static final int STATE_CDATA = 2;

    @Override
    public int highlightLine(
        CharSequence line,
        int startState,
        CodeEditor.CodeEditorStyle style,
        Array<CodeHighlightSpan> spans,
        Array<CodeBracketIgnoreSpan> bracketIgnoreSpans
    ) {
        boolean wantSpans = spans != null && style != null;
        boolean inComment = startState == STATE_COMMENT;
        boolean inCdata = startState == STATE_CDATA;
        int index = 0;
        int length = line.length();

        while (index < length) {
            if (inComment) {
                int end = HighlighterSupport.indexOf(line, "-->", index);
                if (end < 0) {
                    if (wantSpans) {
                        HighlighterSupport.addSpan(spans, index, length, style.commentColor);
                    }
                    HighlighterSupport.addIgnoreSpan(bracketIgnoreSpans, index, length);
                    return STATE_COMMENT;
                }
                if (wantSpans) {
                    HighlighterSupport.addSpan(spans, index, end + 3, style.commentColor);
                }
                HighlighterSupport.addIgnoreSpan(bracketIgnoreSpans, index, end + 3);
                index = end + 3;
                inComment = false;
                continue;
            }

            if (inCdata) {
                int end = HighlighterSupport.indexOf(line, "]]>", index);
                if (end < 0) {
                    if (wantSpans) {
                        HighlighterSupport.addSpan(spans, index, length, style.stringColor);
                    }
                    HighlighterSupport.addIgnoreSpan(bracketIgnoreSpans, index, length);
                    return STATE_CDATA;
                }
                if (wantSpans) {
                    HighlighterSupport.addSpan(spans, index, end + 3, style.stringColor);
                }
                HighlighterSupport.addIgnoreSpan(bracketIgnoreSpans, index, end + 3);
                index = end + 3;
                inCdata = false;
                continue;
            }

            if (HighlighterSupport.startsWith(line, "<!--", index)) {
                int end = HighlighterSupport.indexOf(line, "-->", index + 4);
                if (end < 0) {
                    if (wantSpans) {
                        HighlighterSupport.addSpan(spans, index, length, style.commentColor);
                    }
                    HighlighterSupport.addIgnoreSpan(bracketIgnoreSpans, index, length);
                    return STATE_COMMENT;
                }
                if (wantSpans) {
                    HighlighterSupport.addSpan(spans, index, end + 3, style.commentColor);
                }
                HighlighterSupport.addIgnoreSpan(bracketIgnoreSpans, index, end + 3);
                index = end + 3;
                continue;
            }

            if (HighlighterSupport.startsWith(line, "<![CDATA[", index)) {
                int end = HighlighterSupport.indexOf(line, "]]>", index + 9);
                if (end < 0) {
                    if (wantSpans) {
                        HighlighterSupport.addSpan(spans, index, length, style.stringColor);
                    }
                    HighlighterSupport.addIgnoreSpan(bracketIgnoreSpans, index, length);
                    return STATE_CDATA;
                }
                if (wantSpans) {
                    HighlighterSupport.addSpan(spans, index, end + 3, style.stringColor);
                }
                HighlighterSupport.addIgnoreSpan(bracketIgnoreSpans, index, end + 3);
                index = end + 3;
                continue;
            }

            if (line.charAt(index) == '<') {
                int tagEnd = findTagEnd(line, index + 1);
                if (wantSpans) {
                    highlightTag(line, spans, style, index, tagEnd);
                }
                collectTagIgnoreSpans(line, bracketIgnoreSpans, index, tagEnd);
                index = Math.max(index + 1, tagEnd);
                continue;
            }

            index++;
        }

        if (inComment) {
            return STATE_COMMENT;
        }
        return inCdata ? STATE_CDATA : 0;
    }

    /** Attribute values are quoted text, so brackets inside them must not count. */
    private void collectTagIgnoreSpans(
        CharSequence line,
        Array<CodeBracketIgnoreSpan> bracketIgnoreSpans,
        int start,
        int end
    ) {
        if (bracketIgnoreSpans == null) {
            return;
        }
        int index = start + 1;
        while (index < end) {
            char c = line.charAt(index);
            if (c == '"' || c == '\'') {
                int valueEnd = HighlighterSupport.readString(line, index, c);
                HighlighterSupport.addIgnoreSpan(bracketIgnoreSpans, index, Math.min(valueEnd, end));
                index = valueEnd;
                continue;
            }
            index++;
        }
    }

    private void highlightTag(
        CharSequence line,
        Array<CodeHighlightSpan> spans,
        CodeEditor.CodeEditorStyle style,
        int start,
        int end
    ) {
        HighlighterSupport.addSpan(spans, start, Math.min(start + 1, end), style.annotationColor);
        int index = start + 1;

        if (index < end && (line.charAt(index) == '/' || line.charAt(index) == '?' || line.charAt(index) == '!')) {
            HighlighterSupport.addSpan(spans, index, index + 1, style.annotationColor);
            index++;
        }

        int tagNameStart = index;
        int tagNameEnd = HighlighterSupport.readIdentifier(line, tagNameStart, "-:.");
        HighlighterSupport.addSpan(spans, tagNameStart, tagNameEnd, style.keywordColor);
        index = tagNameEnd;

        while (index < end) {
            index = HighlighterSupport.skipWhitespace(line, index);
            if (index >= end) {
                break;
            }
            char c = line.charAt(index);
            if (c == '"' || c == '\'') {
                int valueEnd = HighlighterSupport.readString(line, index, c);
                HighlighterSupport.addSpan(spans, index, Math.min(valueEnd, end), style.stringColor);
                index = valueEnd;
                continue;
            }
            if (c == '>' || c == '/') {
                HighlighterSupport.addSpan(spans, index, Math.min(index + 1, end), style.annotationColor);
                index++;
                continue;
            }
            int attributeEnd = HighlighterSupport.readIdentifier(line, index, "-:.");
            if (attributeEnd > index) {
                HighlighterSupport.addSpan(spans, index, attributeEnd, style.typeColor);
                index = attributeEnd;
                continue;
            }
            index++;
        }
    }

    private int findTagEnd(CharSequence line, int start) {
        int index = start;
        char quote = 0;
        while (index < line.length()) {
            char c = line.charAt(index);
            if (quote != 0) {
                if (c == quote) {
                    quote = 0;
                } else if (c == '\\') {
                    index++;
                }
            } else if (c == '"' || c == '\'') {
                quote = c;
            } else if (c == '>') {
                return index + 1;
            }
            index++;
        }
        return line.length();
    }
}
