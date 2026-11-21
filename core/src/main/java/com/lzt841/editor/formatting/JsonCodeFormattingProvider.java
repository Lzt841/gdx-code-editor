package com.lzt841.editor.formatting;

import com.badlogic.gdx.utils.Array;
import com.lzt841.editor.CodeEditorTextEdit;
import com.lzt841.editor.CodeEditorTextRange;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Strict, token-preserving JSON pretty printer. It validates the complete requested value before
 * producing edits, and changes only whitespace gaps so source tokens (including number spellings,
 * escapes, key order and duplicate keys) remain byte-for-byte intact. Nested arrays and objects are
 * limited to 128 levels to keep parsing and indentation bounded.
 */
public final class JsonCodeFormattingProvider implements CodeFormattingProvider {
    private static final int MAX_NESTING_DEPTH = 128;
    private static final int MAX_INDENT_CHARS = 1_000_000;

    @Override
    public void format(CodeFormattingRequest request, CodeFormattingResponse response) {
        if (response == null) {
            return;
        }
        if (request == null || request.text == null || request.range == null
            || request.startOffset < 0 || request.endOffset < request.startOffset
            || request.endOffset > request.text.length()) {
            if (!response.isCancelled()) response.fail("Invalid formatting request.");
            return;
        }
        if (response.isCancelled()) {
            return;
        }

        try {
            String baseIndent = request.selectionOnly && request.startOffset > 0
                ? request.getBaseIndent() : "";
            Parser parser = new Parser(request, response, baseIndent);
            List<Token> tokens = parser.parse();
            checkCancelled(response);

            OffsetMap offsets = new OffsetMap(request.text, response);
            Array<CodeEditorTextEdit> edits = new Array<>();
            if (tokens.isEmpty()) {
                throw new FormatFailure("Invalid JSON: expected a JSON value.");
            }

            Token first = tokens.get(0);
            addGapEdit(edits, offsets, request.startOffset, first.start, "", request.text);

            for (int i = 0; i + 1 < tokens.size(); i++) {
                checkCancelled(response);
                Token left = tokens.get(i);
                Token right = tokens.get(i + 1);
                String desired = parser.gapAfter.get(i);
                if (desired == null) desired = "";
                addGapEdit(edits, offsets, left.end, right.start, desired, request.text);
            }

            Token last = tokens.get(tokens.size() - 1);
            String sourceRange = request.getRangeText();
            String trailing = !request.selectionOnly && sourceRange.endsWith("\n") ? "\n" : "";
            addGapEdit(edits, offsets, last.end, request.endOffset, trailing, request.text);

            checkCancelled(response);
            response.complete(edits);
        } catch (Cancelled ignored) {
            // Cancellation is advisory and deliberately silent.
        } catch (FormatFailure failure) {
            if (!response.isCancelled()) response.fail(failure.getMessage());
        }
    }

    private static void addGapEdit(Array<CodeEditorTextEdit> edits, OffsetMap offsets,
                                   int start, int end, String replacement, String text) {
        if (start < 0 || end < start || end > text.length()) {
            throw new FormatFailure("Invalid formatting range.");
        }
        if (!text.substring(start, end).equals(replacement)) {
            edits.add(new CodeEditorTextEdit(offsets.range(start, end), replacement));
        }
    }

    private static void checkCancelled(CodeFormattingResponse response) {
        if (response.isCancelled()) throw new Cancelled();
    }

    private static final class Parser {
        private final String text;
        private final int startOffset;
        private final int endOffset;
        private final CodeFormattingResponse response;
        private final String baseIndent;
        private final int indentWidth;
        private final boolean useTabs;
        private final List<Token> tokens = new ArrayList<>();
        private final Map<Integer, String> gapAfter = new HashMap<>();
        private int position;

        private Parser(CodeFormattingRequest request, CodeFormattingResponse response, String baseIndent) {
            this.text = request.text;
            this.startOffset = request.startOffset;
            this.endOffset = request.endOffset;
            this.position = request.startOffset;
            this.response = response;
            this.baseIndent = baseIndent == null ? "" : baseIndent;
            this.indentWidth = Math.max(1, request.indentWidth);
            this.useTabs = request.useTabs;
        }

        private List<Token> parse() throws FormatFailure {
            skipWhitespace();
            if (position >= endOffset) fail("expected a JSON value");
            parseValue(0);
            skipWhitespace();
            if (position != endOffset) fail("trailing data");
            return tokens;
        }

        /** containerDepth is the number of containing objects/arrays, excluding this value. */
        private Value parseValue(int containerDepth) throws FormatFailure {
            checkCancelled(response);
            skipWhitespace();
            if (position >= endOffset) fail("expected a JSON value");
            char current = text.charAt(position);
            if (current == '{') return parseObject(containerDepth + 1);
            if (current == '[') return parseArray(containerDepth + 1);
            if (current == '"') {
                int token = parseString();
                return new Value(token, token);
            }
            if (current == 't') return parseLiteral("true");
            if (current == 'f') return parseLiteral("false");
            if (current == 'n') return parseLiteral("null");
            if (current == '-' || isDigit(current)) return parseNumber();
            fail("expected a JSON value");
            return null; // unreachable
        }

        private Value parseObject(int depth) throws FormatFailure {
            checkDepth(depth);
            int open = addToken(position, position + 1);
            position++;
            skipWhitespace();
            if (consume('}')) {
                int close = addToken(position, position + 1);
                position++;
                setGap(open, "");
                return new Value(open, close);
            }
            setGap(open, lineBreakIndent(depth));

            while (true) {
                checkCancelled(response);
                skipWhitespace();
                if (position >= endOffset || text.charAt(position) != '"') {
                    fail("object property name must be a string");
                }
                int key = parseString();
                setGap(key, "");
                skipWhitespace();
                if (!consume(':')) fail("expected ':' after object property name");
                int colon = addToken(position, position + 1);
                position++;
                setGap(colon, " ");

                Value value = parseValue(depth);
                skipWhitespace();
                if (consume(',')) {
                    int comma = addToken(position, position + 1);
                    position++;
                    setGap(comma, lineBreakIndent(depth));
                    skipWhitespace();
                    if (position < endOffset && text.charAt(position) == '}') {
                        fail("trailing comma in object");
                    }
                    continue;
                }
                if (consume('}')) {
                    setGap(value.lastToken, lineBreakIndent(depth - 1));
                    int close = addToken(position, position + 1);
                    position++;
                    return new Value(open, close);
                }
                fail("expected ',' or '}' after object property");
            }
        }

        private Value parseArray(int depth) throws FormatFailure {
            checkDepth(depth);
            int open = addToken(position, position + 1);
            position++;
            skipWhitespace();
            if (consume(']')) {
                int close = addToken(position, position + 1);
                position++;
                setGap(open, "");
                return new Value(open, close);
            }
            setGap(open, lineBreakIndent(depth));

            while (true) {
                checkCancelled(response);
                Value value = parseValue(depth);
                skipWhitespace();
                if (consume(',')) {
                    int comma = addToken(position, position + 1);
                    position++;
                    setGap(comma, lineBreakIndent(depth));
                    skipWhitespace();
                    if (position < endOffset && text.charAt(position) == ']') {
                        fail("trailing comma in array");
                    }
                    continue;
                }
                if (consume(']')) {
                    setGap(value.lastToken, lineBreakIndent(depth - 1));
                    int close = addToken(position, position + 1);
                    position++;
                    return new Value(open, close);
                }
                fail("expected ',' or ']' after array value");
            }
        }

        private int parseString() throws FormatFailure {
            int start = position;
            position++; // opening quote
            while (position < endOffset) {
                checkCancelled(response);
                char current = text.charAt(position++);
                if (current == '"') return addToken(start, position);
                if (current < 0x20) fail("unescaped control character in string");
                if (current == '\\') {
                    if (position >= endOffset) fail("incomplete escape in string");
                    char escape = text.charAt(position++);
                    if (escape == 'u') {
                        for (int i = 0; i < 4; i++) {
                            if (position >= endOffset || !isHex(text.charAt(position))) {
                                fail("invalid Unicode escape in string");
                            }
                            position++;
                        }
                    } else if (escape != '"' && escape != '\\' && escape != '/' && escape != 'b'
                        && escape != 'f' && escape != 'n' && escape != 'r' && escape != 't') {
                        fail("invalid escape in string");
                    }
                    continue;
                }
                if (Character.isHighSurrogate(current)) {
                    if (position >= endOffset || !Character.isLowSurrogate(text.charAt(position))) {
                        fail("unpaired surrogate in string");
                    }
                    position++;
                } else if (Character.isLowSurrogate(current)) {
                    fail("unpaired surrogate in string");
                }
            }
            fail("unterminated string");
            return -1; // unreachable
        }

        private Value parseNumber() throws FormatFailure {
            int start = position;
            if (consume('-')) position++;
            if (position >= endOffset) fail("incomplete number");
            char first = text.charAt(position);
            if (first == '0') {
                position++;
                if (position < endOffset && isDigit(text.charAt(position))) {
                    fail("leading zero in number");
                }
            } else if (first >= '1' && first <= '9') {
                position++;
                while (position < endOffset && isDigit(text.charAt(position))) {
                    checkCancelled(response);
                    position++;
                }
            } else {
                fail("invalid number");
            }
            if (consume('.')) {
                position++;
                if (position >= endOffset || !isDigit(text.charAt(position))) fail("fraction requires a digit");
                while (position < endOffset && isDigit(text.charAt(position))) {
                    checkCancelled(response);
                    position++;
                }
            }
            if (position < endOffset && (text.charAt(position) == 'e' || text.charAt(position) == 'E')) {
                position++;
                if (position < endOffset && (text.charAt(position) == '+' || text.charAt(position) == '-')) position++;
                if (position >= endOffset || !isDigit(text.charAt(position))) fail("exponent requires a digit");
                while (position < endOffset && isDigit(text.charAt(position))) {
                    checkCancelled(response);
                    position++;
                }
            }
            int token = addToken(start, position);
            return new Value(token, token);
        }

        private Value parseLiteral(String literal) throws FormatFailure {
            int start = position;
            for (int i = 0; i < literal.length(); i++) {
                checkCancelled(response);
                if (position >= endOffset || text.charAt(position) != literal.charAt(i)) {
                    fail("invalid literal");
                }
                position++;
            }
            int token = addToken(start, position);
            return new Value(token, token);
        }

        private void skipWhitespace() {
            while (position < endOffset) {
                checkCancelled(response);
                char current = text.charAt(position);
                if (current != ' ' && current != '\t' && current != '\n' && current != '\r') break;
                position++;
            }
        }

        private boolean consume(char expected) {
            return position < endOffset && text.charAt(position) == expected;
        }

        private int addToken(int start, int end) {
            tokens.add(new Token(start, end));
            return tokens.size() - 1;
        }

        private void setGap(int tokenIndex, String whitespace) {
            gapAfter.put(tokenIndex, whitespace);
        }

        private String lineBreakIndent(int depth) throws FormatFailure {
            return "\n" + indentation(depth);
        }

        private String indentation(int depth) throws FormatFailure {
            int levels = Math.max(0, depth);
            int unitLength = useTabs ? 1 : indentWidth;
            if (levels > 0 && unitLength > (MAX_INDENT_CHARS - baseIndent.length()) / levels) {
                throw new FormatFailure("Indentation exceeds the supported size.");
            }
            int count = baseIndent.length() + unitLength * levels;
            if (count > MAX_INDENT_CHARS) throw new FormatFailure("Indentation exceeds the supported size.");
            StringBuilder result = new StringBuilder(count);
            result.append(baseIndent);
            char unit = useTabs ? '\t' : ' ';
            for (int i = 0; i < unitLength * levels; i++) result.append(unit);
            return result.toString();
        }

        private void checkDepth(int depth) throws FormatFailure {
            if (depth > MAX_NESTING_DEPTH) {
                throw new FormatFailure("Invalid JSON: nesting exceeds " + MAX_NESTING_DEPTH + " levels.");
            }
        }

        private void fail(String reason) throws FormatFailure {
            throw new FormatFailure("Invalid JSON at offset " + (position - startOffset) + ": " + reason + ".");
        }
    }

    private static final class OffsetMap {
        private final int[] lineStarts;

        private OffsetMap(String text, CodeFormattingResponse response) {
            int count = 1;
            for (int i = 0; i < text.length(); i++) {
                if ((i & 8191) == 0) checkCancelled(response);
                if (text.charAt(i) == '\n') count++;
            }
            lineStarts = new int[count];
            int line = 1;
            for (int i = 0; i < text.length(); i++) {
                if ((i & 8191) == 0) checkCancelled(response);
                if (text.charAt(i) == '\n') lineStarts[line++] = i + 1;
            }
        }

        private CodeEditorTextRange range(int startOffset, int endOffset) {
            Position start = position(startOffset);
            Position end = position(endOffset);
            return new CodeEditorTextRange(start.line, start.column, end.line, end.column);
        }

        private Position position(int offset) {
            int found = Arrays.binarySearch(lineStarts, offset);
            int line = found >= 0 ? found : -found - 2;
            if (line < 0) line = 0;
            return new Position(line, offset - lineStarts[line]);
        }
    }

    private static final class Position {
        private final int line;
        private final int column;
        private Position(int line, int column) { this.line = line; this.column = column; }
    }

    private static final class Token {
        private final int start;
        private final int end;
        private Token(int start, int end) { this.start = start; this.end = end; }
    }

    private static final class Value {
        private final int firstToken;
        private final int lastToken;
        private Value(int firstToken, int lastToken) {
            this.firstToken = firstToken;
            this.lastToken = lastToken;
        }
    }

    private static boolean isDigit(char value) { return value >= '0' && value <= '9'; }

    private static boolean isHex(char value) {
        return (value >= '0' && value <= '9') || (value >= 'a' && value <= 'f')
            || (value >= 'A' && value <= 'F');
    }

    private static final class FormatFailure extends RuntimeException {
        private FormatFailure(String message) { super(message); }
    }

    private static final class Cancelled extends RuntimeException {
        private Cancelled() { super(null, null, false, false); }
    }
}
