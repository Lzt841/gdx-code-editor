package com.lzt841.editor.highlight;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.utils.Array;

/** Shared helpers for built-in syntax highlighters. */
final class HighlighterSupport {
    private HighlighterSupport() {
    }

    static void addSpan(Array<CodeHighlightSpan> spans, int start, int end, Color color) {
        if (spans == null || color == null || end <= start) {
            return;
        }
        spans.add(new CodeHighlightSpan(start, end, color));
    }

    static void addIgnoreSpan(Array<CodeBracketIgnoreSpan> spans, int start, int end) {
        if (spans == null || end <= start) {
            return;
        }
        spans.add(new CodeBracketIgnoreSpan(start, end));
    }

    /** {@code String.startsWith(prefix, offset)} for any CharSequence. */
    static boolean startsWith(CharSequence text, String prefix, int offset) {
        int length = prefix.length();
        if (offset < 0 || offset + length > text.length()) {
            return false;
        }
        for (int i = 0; i < length; i++) {
            if (text.charAt(offset + i) != prefix.charAt(i)) {
                return false;
            }
        }
        return true;
    }

    /** {@code String.indexOf(needle, from)} for any CharSequence; -1 when absent. */
    static int indexOf(CharSequence text, String needle, int from) {
        int limit = text.length() - needle.length();
        for (int i = Math.max(0, from); i <= limit; i++) {
            if (startsWith(text, needle, i)) {
                return i;
            }
        }
        return -1;
    }

    /** Whether {@code text[start,end)} equals {@code candidate}, without allocating a substring. */
    static boolean regionEquals(CharSequence text, int start, int end, String candidate) {
        if (end - start != candidate.length()) {
            return false;
        }
        for (int i = 0; i < candidate.length(); i++) {
            if (text.charAt(start + i) != candidate.charAt(i)) {
                return false;
            }
        }
        return true;
    }

    static Array<Array<CodeBracketIgnoreSpan>> emptyIgnoreSpans(int lineCount) {
        Array<Array<CodeBracketIgnoreSpan>> result = new Array<>(lineCount);
        for (int i = 0; i < lineCount; i++) {
            result.add(new Array<CodeBracketIgnoreSpan>(0));
        }
        return result;
    }

    static int readString(CharSequence line, int start, char quote) {
        boolean escaped = false;
        int index = start + 1;
        while (index < line.length()) {
            char c = line.charAt(index);
            if (escaped) {
                escaped = false;
            } else if (c == '\\' && quote != '`') {
                escaped = true;
            } else if (c == quote) {
                return index + 1;
            }
            index++;
        }
        return line.length();
    }

    static int readIdentifier(CharSequence line, int start) {
        return readIdentifier(line, start, "");
    }

    static int readIdentifier(CharSequence line, int start, String extraChars) {
        int index = start;
        while (index < line.length()) {
            char c = line.charAt(index);
            if (Character.isLetterOrDigit(c) || c == '_' || extraChars.indexOf(c) >= 0) {
                index++;
            } else {
                break;
            }
        }
        return index;
    }

    static int readNumber(CharSequence line, int start) {
        int index = start;
        if (index >= line.length()) {
            return index;
        }

        if (line.charAt(index) == '.') {
            index++;
        }

        if (index + 1 < line.length() && line.charAt(index) == '0') {
            char next = line.charAt(index + 1);
            if (next == 'x' || next == 'X') {
                index += 2;
                while (index < line.length()) {
                    char c = line.charAt(index);
                    if (Character.digit(c, 16) >= 0 || c == '_') {
                        index++;
                    } else {
                        break;
                    }
                }
                return consumeNumberSuffix(line, index);
            }
            if (next == 'b' || next == 'B') {
                index += 2;
                while (index < line.length()) {
                    char c = line.charAt(index);
                    if (c == '0' || c == '1' || c == '_') {
                        index++;
                    } else {
                        break;
                    }
                }
                return consumeNumberSuffix(line, index);
            }
        }

        while (index < line.length()) {
            char c = line.charAt(index);
            if (Character.isDigit(c) || c == '_') {
                index++;
            } else {
                break;
            }
        }

        if (index < line.length() && line.charAt(index) == '.') {
            index++;
            while (index < line.length()) {
                char c = line.charAt(index);
                if (Character.isDigit(c) || c == '_') {
                    index++;
                } else {
                    break;
                }
            }
        }

        if (index < line.length()) {
            char c = line.charAt(index);
            if (c == 'e' || c == 'E' || c == 'p' || c == 'P') {
                int exponent = index + 1;
                if (exponent < line.length() && (line.charAt(exponent) == '+' || line.charAt(exponent) == '-')) {
                    exponent++;
                }
                int digits = exponent;
                while (digits < line.length()) {
                    char digit = line.charAt(digits);
                    if (Character.isDigit(digit) || digit == '_') {
                        digits++;
                    } else {
                        break;
                    }
                }
                if (digits > exponent) {
                    index = digits;
                }
            }
        }

        return consumeNumberSuffix(line, index);
    }

    static boolean isNumberStart(CharSequence line, int index) {
        if (index < 0 || index >= line.length()) {
            return false;
        }
        char c = line.charAt(index);
        if (Character.isDigit(c)) {
            return true;
        }
        return c == '.' && index + 1 < line.length() && Character.isDigit(line.charAt(index + 1));
    }

    static int skipWhitespace(CharSequence line, int start) {
        int index = start;
        while (index < line.length() && Character.isWhitespace(line.charAt(index))) {
            index++;
        }
        return index;
    }

    static boolean isIdentifierBoundary(CharSequence line, int index) {
        if (index <= 0 || index > line.length()) {
            return true;
        }
        char c = line.charAt(index - 1);
        return !Character.isLetterOrDigit(c) && c != '_';
    }

    private static int consumeNumberSuffix(CharSequence line, int start) {
        int index = start;
        while (index < line.length()) {
            char c = line.charAt(index);
            if (Character.isLetter(c)) {
                index++;
            } else {
                break;
            }
        }
        return index;
    }
}
