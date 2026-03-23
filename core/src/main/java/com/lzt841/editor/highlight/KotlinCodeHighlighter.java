package com.lzt841.editor.highlight;

import com.badlogic.gdx.utils.ObjectSet;
import com.badlogic.gdx.utils.Array;
import com.lzt841.editor.CodeEditor;

/** Built-in highlighter for Kotlin source files. */
public class KotlinCodeHighlighter implements CodeHighlighter {
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
    public Array<Array<CodeHighlightSpan>> highlight(Array<String> lines, CodeEditor.CodeEditorStyle style) {
        Array<Array<CodeHighlightSpan>> result = new Array<>(lines.size);
        boolean inBlockComment = false;
        boolean inRawString = false;

        for (String line : lines) {
            Array<CodeHighlightSpan> spans = new Array<>();
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

                if (inRawString) {
                    int end = line.indexOf("\"\"\"", index);
                    if (end < 0) {
                        HighlighterSupport.addSpan(spans, index, line.length(), style.stringColor);
                        break;
                    }
                    HighlighterSupport.addSpan(spans, index, end + 3, style.stringColor);
                    index = end + 3;
                    inRawString = false;
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
                if (line.startsWith("\"\"\"", index)) {
                    int end = line.indexOf("\"\"\"", index + 3);
                    if (end < 0) {
                        HighlighterSupport.addSpan(spans, index, line.length(), style.stringColor);
                        inRawString = true;
                        break;
                    }
                    HighlighterSupport.addSpan(spans, index, end + 3, style.stringColor);
                    index = end + 3;
                    continue;
                }

                char c = line.charAt(index);
                if (c == '@') {
                    int end = HighlighterSupport.readIdentifier(line, index + 1, "[]");
                    HighlighterSupport.addSpan(spans, index, Math.max(index + 1, end), style.annotationColor);
                    index = Math.max(index + 1, end);
                    continue;
                }
                if (c == '"' || c == '\'') {
                    int end = HighlighterSupport.readString(line, index, c);
                    HighlighterSupport.addSpan(spans, index, end, style.stringColor);
                    index = end;
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
        boolean inRawString = false;

        for (String line : lines) {
            Array<CodeBracketIgnoreSpan> spans = new Array<>();
            int index = 0;
            while (index < line.length()) {
                if (inRawString) {
                    int end = line.indexOf("\"\"\"", index);
                    if (end < 0) {
                        HighlighterSupport.addIgnoreSpan(spans, index, line.length());
                        break;
                    }
                    HighlighterSupport.addIgnoreSpan(spans, index, end + 3);
                    index = end + 3;
                    inRawString = false;
                    continue;
                }

                if (line.startsWith("\"\"\"", index)) {
                    int end = line.indexOf("\"\"\"", index + 3);
                    if (end < 0) {
                        HighlighterSupport.addIgnoreSpan(spans, index, line.length());
                        inRawString = true;
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
}
