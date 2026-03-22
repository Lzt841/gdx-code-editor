package com.lzt841.editor.highlight;

import com.lzt841.editor.CodeEditor;

import java.util.ArrayList;
import java.util.List;

/** Built-in highlighter for XML and HTML-like markup. */
public class XmlCodeHighlighter implements CodeHighlighter {
    @Override
    public List<List<CodeHighlightSpan>> highlight(List<String> lines, CodeEditor.CodeEditorStyle style) {
        ArrayList<List<CodeHighlightSpan>> result = new ArrayList<>(lines.size());
        boolean inComment = false;

        for (String line : lines) {
            ArrayList<CodeHighlightSpan> spans = new ArrayList<>();
            int index = 0;

            while (index < line.length()) {
                if (inComment) {
                    int end = line.indexOf("-->", index);
                    if (end < 0) {
                        HighlighterSupport.addSpan(spans, index, line.length(), style.commentColor);
                        break;
                    }
                    HighlighterSupport.addSpan(spans, index, end + 3, style.commentColor);
                    index = end + 3;
                    inComment = false;
                    continue;
                }

                if (line.startsWith("<!--", index)) {
                    int end = line.indexOf("-->", index + 4);
                    if (end < 0) {
                        HighlighterSupport.addSpan(spans, index, line.length(), style.commentColor);
                        inComment = true;
                        break;
                    }
                    HighlighterSupport.addSpan(spans, index, end + 3, style.commentColor);
                    index = end + 3;
                    continue;
                }

                if (line.charAt(index) == '<') {
                    int tagEnd = findTagEnd(line, index + 1);
                    highlightTag(line, spans, style, index, tagEnd);
                    index = Math.max(index + 1, tagEnd);
                    continue;
                }

                index++;
            }

            result.add(spans);
        }
        return result;
    }

    @Override
    public List<List<CodeBracketIgnoreSpan>> getBracketIgnoreSpans(List<String> lines) {
        ArrayList<List<CodeBracketIgnoreSpan>> result = new ArrayList<>(lines.size());
        boolean inCdata = false;

        for (String line : lines) {
            ArrayList<CodeBracketIgnoreSpan> spans = new ArrayList<>();
            int index = 0;
            while (index < line.length()) {
                if (inCdata) {
                    int end = line.indexOf("]]>", index);
                    if (end < 0) {
                        HighlighterSupport.addIgnoreSpan(spans, index, line.length());
                        break;
                    }
                    HighlighterSupport.addIgnoreSpan(spans, index, end + 3);
                    index = end + 3;
                    inCdata = false;
                    continue;
                }

                if (line.startsWith("<![CDATA[", index)) {
                    int end = line.indexOf("]]>", index + 9);
                    if (end < 0) {
                        HighlighterSupport.addIgnoreSpan(spans, index, line.length());
                        inCdata = true;
                        break;
                    }
                    HighlighterSupport.addIgnoreSpan(spans, index, end + 3);
                    index = end + 3;
                    continue;
                }

                index++;
            }
            result.add(spans);
        }

        return result;
    }

    private void highlightTag(
        String line,
        List<CodeHighlightSpan> spans,
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

    private int findTagEnd(String line, int start) {
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
