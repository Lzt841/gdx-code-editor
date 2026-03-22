package com.lzt841.editor.highlight;

import java.util.Locale;

/** Shared access to built-in syntax highlighters. */
public final class BuiltinCodeHighlighters {
    private static final PlainTextHighlighter PLAIN_TEXT = new PlainTextHighlighter();
    private static final JavaCodeHighlighter JAVA = new JavaCodeHighlighter();
    private static final KotlinCodeHighlighter KOTLIN = new KotlinCodeHighlighter();
    private static final JavaScriptCodeHighlighter JAVASCRIPT = new JavaScriptCodeHighlighter();
    private static final PythonCodeHighlighter PYTHON = new PythonCodeHighlighter();
    private static final JsonCodeHighlighter JSON = new JsonCodeHighlighter();
    private static final XmlCodeHighlighter XML = new XmlCodeHighlighter();

    private BuiltinCodeHighlighters() {
    }

    public static CodeHighlighter plainText() {
        return PLAIN_TEXT;
    }

    public static CodeHighlighter java() {
        return JAVA;
    }

    public static CodeHighlighter kotlin() {
        return KOTLIN;
    }

    public static CodeHighlighter javascript() {
        return JAVASCRIPT;
    }

    public static CodeHighlighter python() {
        return PYTHON;
    }

    public static CodeHighlighter json() {
        return JSON;
    }

    public static CodeHighlighter xml() {
        return XML;
    }

    public static CodeHighlighter byName(String language) {
        if (language == null || language.trim().isEmpty()) {
            return JAVA;
        }
        String normalized = language.trim().toLowerCase(Locale.ROOT);
        switch (normalized) {
            case "text":
            case "txt":
            case "plain":
            case "plaintext":
                return PLAIN_TEXT;
            case "java":
                return JAVA;
            case "kt":
            case "kts":
            case "kotlin":
                return KOTLIN;
            case "js":
            case "jsx":
            case "ts":
            case "tsx":
            case "javascript":
            case "typescript":
                return JAVASCRIPT;
            case "py":
            case "python":
                return PYTHON;
            case "json":
                return JSON;
            case "xml":
            case "html":
            case "xhtml":
            case "svg":
                return XML;
            default:
                return JAVA;
        }
    }
}
