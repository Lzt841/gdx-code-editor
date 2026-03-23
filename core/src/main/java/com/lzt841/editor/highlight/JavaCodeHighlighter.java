package com.lzt841.editor.highlight;

import com.badlogic.gdx.utils.ObjectSet;
import com.badlogic.gdx.utils.Array;
import com.lzt841.editor.CodeEditor;

/** Default Java-like syntax highlighter. */
public class JavaCodeHighlighter implements CodeHighlighter {
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
    public Array<Array<CodeHighlightSpan>> highlight(Array<String> lines, CodeEditor.CodeEditorStyle style) {
        Array<Array<CodeHighlightSpan>> result = new Array<>(lines.size);
        boolean inBlockComment = false;
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
                if (c == '@') {
                    int end = HighlighterSupport.readIdentifier(line, index + 1);
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
}
