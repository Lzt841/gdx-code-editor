package com.lzt841.editor.highlight;

import com.lzt841.editor.CodeEditor;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Built-in highlighter for JavaScript and TypeScript-like code. */
public class JavaScriptCodeHighlighter implements CodeHighlighter {
    private static final Set<String> KEYWORDS = new HashSet<>(Arrays.asList(
        "await", "break", "case", "catch", "class", "const", "continue", "debugger",
        "default", "delete", "do", "else", "export", "extends", "finally", "for",
        "function", "if", "import", "in", "instanceof", "let", "new", "of", "return",
        "super", "switch", "this", "throw", "try", "typeof", "var", "void", "while",
        "with", "yield", "as", "from", "async"
    ));

    private static final Set<String> TYPES = new HashSet<>(Arrays.asList(
        "Array", "Boolean", "Date", "Error", "Map", "Number", "Object", "Promise",
        "RegExp", "Set", "String", "Symbol"
    ));

    private static final Set<String> LITERALS = new HashSet<>(Arrays.asList(
        "false", "null", "true", "undefined", "NaN", "Infinity"
    ));

    @Override
    public List<List<CodeHighlightSpan>> highlight(List<String> lines, CodeEditor.CodeEditorStyle style) {
        ArrayList<List<CodeHighlightSpan>> result = new ArrayList<>(lines.size());
        boolean inBlockComment = false;
        boolean inTemplateString = false;

        for (String line : lines) {
            ArrayList<CodeHighlightSpan> spans = new ArrayList<>();
            int index = 0;

            while (index < line.length()) {
                if (inBlockComment) {
                    int end = line.indexOf("*/", index);
                    if (end < 0) {
                        HighlighterSupport.addSpan(spans, index, line.length(), style.commentColor);
                        break;
                    }
                    HighlighterSupport.addSpan(spans, index, end + 2, style.commentColor);
                    index = end + 2;
                    inBlockComment = false;
                    continue;
                }

                if (inTemplateString) {
                    int end = HighlighterSupport.readString(line, index, '`');
                    HighlighterSupport.addSpan(spans, index, end, style.stringColor);
                    inTemplateString = end >= line.length() && (line.isEmpty() || line.charAt(line.length() - 1) != '`');
                    index = end;
                    continue;
                }

                if (line.startsWith("//", index)) {
                    HighlighterSupport.addSpan(spans, index, line.length(), style.commentColor);
                    break;
                }
                if (line.startsWith("/*", index)) {
                    int end = line.indexOf("*/", index + 2);
                    if (end < 0) {
                        HighlighterSupport.addSpan(spans, index, line.length(), style.commentColor);
                        inBlockComment = true;
                        break;
                    }
                    HighlighterSupport.addSpan(spans, index, end + 2, style.commentColor);
                    index = end + 2;
                    continue;
                }

                char c = line.charAt(index);
                if (c == '"' || c == '\'') {
                    int end = HighlighterSupport.readString(line, index, c);
                    HighlighterSupport.addSpan(spans, index, end, style.stringColor);
                    index = end;
                    continue;
                }
                if (c == '`') {
                    int end = HighlighterSupport.readString(line, index, '`');
                    HighlighterSupport.addSpan(spans, index, end, style.stringColor);
                    inTemplateString = end >= line.length() && (line.isEmpty() || line.charAt(line.length() - 1) != '`');
                    index = end;
                    continue;
                }
                if (HighlighterSupport.isNumberStart(line, index)) {
                    int end = HighlighterSupport.readNumber(line, index);
                    HighlighterSupport.addSpan(spans, index, end, style.numberColor);
                    index = end;
                    continue;
                }
                if (Character.isJavaIdentifierStart(c) || c == '$') {
                    int end = HighlighterSupport.readIdentifier(line, index, "$");
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
    public List<List<CodeBracketIgnoreSpan>> getBracketIgnoreSpans(List<String> lines) {
        ArrayList<List<CodeBracketIgnoreSpan>> result = new ArrayList<>(lines.size());
        boolean inTemplateString = false;

        for (String line : lines) {
            ArrayList<CodeBracketIgnoreSpan> spans = new ArrayList<>();
            int index = 0;
            while (index < line.length()) {
                if (inTemplateString) {
                    int end = HighlighterSupport.readString(line, index, '`');
                    HighlighterSupport.addIgnoreSpan(spans, index, end);
                    inTemplateString = end >= line.length() && (line.isEmpty() || line.charAt(line.length() - 1) != '`');
                    index = end;
                    continue;
                }

                if (line.charAt(index) == '`') {
                    int end = HighlighterSupport.readString(line, index, '`');
                    HighlighterSupport.addIgnoreSpan(spans, index, end);
                    inTemplateString = end >= line.length() && (line.isEmpty() || line.charAt(line.length() - 1) != '`');
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
