package com.lzt841.editor.highlight;

import com.badlogic.gdx.graphics.Color;

import java.util.ArrayList;
import java.util.List;

/** Shared helpers for built-in syntax highlighters. */
final class HighlighterSupport {
    private HighlighterSupport() {
    }

    static void addSpan(List<CodeHighlightSpan> spans, int start, int end, Color color) {
        if (spans == null || color == null || end <= start) {
            return;
        }
        spans.add(new CodeHighlightSpan(start, end, color));
    }

    static void addIgnoreSpan(List<CodeBracketIgnoreSpan> spans, int start, int end) {
        if (spans == null || end <= start) {
            return;
        }
        spans.add(new CodeBracketIgnoreSpan(start, end));
    }

    static List<List<CodeBracketIgnoreSpan>> emptyIgnoreSpans(int lineCount) {
        ArrayList<List<CodeBracketIgnoreSpan>> result = new ArrayList<>(lineCount);
        for (int i = 0; i < lineCount; i++) {
            result.add(new ArrayList<CodeBracketIgnoreSpan>(0));
        }
        return result;
    }

    static int readString(String line, int start, char quote) {
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

    static int readIdentifier(String line, int start) {
        return readIdentifier(line, start, "");
    }

    static int readIdentifier(String line, int start, String extraChars) {
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

    static int readNumber(String line, int start) {
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

    static boolean isNumberStart(String line, int index) {
        if (index < 0 || index >= line.length()) {
            return false;
        }
        char c = line.charAt(index);
        if (Character.isDigit(c)) {
            return true;
        }
        return c == '.' && index + 1 < line.length() && Character.isDigit(line.charAt(index + 1));
    }

    static int skipWhitespace(String line, int start) {
        int index = start;
        while (index < line.length() && Character.isWhitespace(line.charAt(index))) {
            index++;
        }
        return index;
    }

    static boolean isIdentifierBoundary(String line, int index) {
        if (index <= 0 || index > line.length()) {
            return true;
        }
        char c = line.charAt(index - 1);
        return !Character.isLetterOrDigit(c) && c != '_';
    }

    private static int consumeNumberSuffix(String line, int start) {
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
