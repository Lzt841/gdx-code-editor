package com.lzt841.editor.formatting;

import com.badlogic.gdx.utils.Array;
import com.lzt841.editor.CodeEditorTextEdit;
import com.lzt841.editor.CodeEditorTextRange;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class JsonCodeFormattingProviderTest {
    private final JsonCodeFormattingProvider provider = new JsonCodeFormattingProvider();

    @Test
    public void prettyPrintsNestedJsonWhilePreservingDuplicateKeysEscapesAndNumberLexemes() {
        String source = "{\"dup\":1e+03,\"dup\":\"a\\\"b\\n\",\"nest\":{\"n\":-0.00E-2}}";
        String expected = "{\n"
            + "    \"dup\": 1e+03,\n"
            + "    \"dup\": \"a\\\"b\\n\",\n"
            + "    \"nest\": {\n"
            + "        \"n\": -0.00E-2\n"
            + "    }\n"
            + "}";
        Result result = format(source);
        assertNull(result.failure);
        assertEquals(expected, apply(source, result.edits));
    }

    @Test
    public void formatsNonemptyArraysAndKeepsEmptyContainersCompact() {
        String source = "[{},[],true,null,1e+9]";
        String expected = "[\n    {},\n    [],\n    true,\n    null,\n    1e+9\n]";
        Result result = format(source);
        assertNull(result.failure);
        assertEquals(expected, apply(source, result.edits));
    }

    @Test
    public void acceptsPrimitiveRootValuesWithoutAddingContainers() {
        String[] values = {"true", "null", "-0", "\"raw\\tstring\""};
        for (String value : values) {
            Result result = format(value);
            assertNull(value, result.failure);
            assertEquals(value, apply(value, result.edits));
        }
    }

    @Test
    public void usesTabsForEachIndentLevel() {
        String source = "[{\"a\":1}]";
        Result result = format(source, false, 4, true);
        assertNull(result.failure);
        assertEquals("[\n\t{\n\t\t\"a\": 1\n\t}\n]", apply(source, result.edits));
    }

    @Test
    public void formatsOnlyTheSelectedValueAndUsesItsBaseIndentOnLaterLines() {
        String source = "prefix\n    {\"a\":[1,2]}\nsuffix";
        String selected = "{\"a\":[1,2]}";
        int start = source.indexOf(selected);
        int end = start + selected.length();
        CodeFormattingRequest request = request(source, start, end, true, 4, false);
        Result result = format(request);
        assertNull(result.failure);
        assertEquals("prefix\n    {\n        \"a\": [\n            1,\n            2\n        ]\n    }\nsuffix",
            apply(source, result.edits));
        assertTrue(result.edits.size > 0);
    }

    @Test
    public void rejectsMalformedJsonWithoutReturningPartialEdits() {
        String[] invalid = {
            "{", "{\"a\" 1}", "{\"a\":1,}", "[1,]", "01", "+1", "1.", "1e+", "true false",
            "[NaN]", "\"bad\nstring\"", "\"\\x\"", "{1:2}"
        };
        for (String source : invalid) {
            Result result = format(source);
            assertNotNull(source, result.failure);
            assertFalse(source, result.completed);
            assertEquals(source, 0, result.edits.size);
        }
    }

    @Test
    public void enforcesNestingLimitWithoutRejectingTheBoundary() {
        String allowed = nestedArrays(128);
        Result valid = format(allowed);
        assertNull(valid.failure);
        assertTrue(valid.completed);

        Result tooDeep = format(nestedArrays(129));
        assertNotNull(tooDeep.failure);
        assertTrue(tooDeep.failure.contains("nesting exceeds 128"));
        assertFalse(tooDeep.completed);
        assertEquals(0, tooDeep.edits.size);
    }

    @Test
    public void preservesOneTerminalLfOnlyWhenFullDocumentInputHasOne() {
        assertEquals("{\n    \"x\": 1\n}",
            apply("{\"x\":1}", format("{\"x\":1}").edits));
        assertEquals("{\n    \"x\": 1\n}\n",
            apply("{\"x\":1}\n", format("{\"x\":1}\n").edits));
        assertEquals("{\n    \"x\": 1\n}\n",
            apply("{\"x\":1}\n\n", format("{\"x\":1}\n\n").edits));
    }

    @Test
    public void emitsOnlyWhitespaceGapEditsAndIsIdempotent() {
        String source = " { \"x\" : 12345 , \"y\" : [ true , false ] }  ";
        Result first = format(source);
        assertNull(first.failure);
        for (CodeEditorTextEdit edit : first.edits) {
            String old = substringForRange(source, edit.range);
            assertTrue("edit must not include a lexical token: " + old, isJsonWhitespace(old));
            assertTrue("replacement must be whitespace", isJsonWhitespace(edit.replacement));
        }
        String formatted = apply(source, first.edits);
        Result second = format(formatted);
        assertNull(second.failure);
        assertEquals(0, second.edits.size);
    }

    @Test
    public void cancellationProducesNeitherCompletionNorFailure() {
        Capture capture = new Capture();
        capture.cancelled = true;
        provider.format(fullRequest("{\"x\":1}"), capture);
        assertFalse(capture.completed);
        assertNull(capture.failure);
    }

    @Test
    public void honorsNonDefaultSpaceIndentWidth() {
        String source = "{\"x\":[1,2]}";
        Result result = format(source, false, 2, false);
        assertNull(result.failure);
        assertEquals("{\n  \"x\": [\n    1,\n    2\n  ]\n}", apply(source, result.edits));
    }

    @Test
    public void preservesEscapedUnicodeAndRawSupplementaryCharacters() {
        String source = "{\"escaped\":\"\\uD83D\\uDE00\",\"😀\":\"x😀y\"}";
        String expected = "{\n    \"escaped\": \"\\uD83D\\uDE00\",\n    \"😀\": \"x😀y\"\n}";
        Result result = format(source);
        assertNull(result.failure);
        assertEquals(expected, apply(source, result.edits));
    }

    @Test
    public void cancellationDuringStringScanningReturnsNoAnswer() {
        StringBuilder source = new StringBuilder(10002);
        source.append('"');
        for (int i = 0; i < 10000; i++) source.append('a');
        source.append('"');

        final int[] checks = {0};
        final boolean[] completed = {false};
        final String[] failure = {null};
        CodeFormattingResponse response = new CodeFormattingResponse() {
            @Override public void complete(Array<CodeEditorTextEdit> edits) { completed[0] = true; }
            @Override public void fail(String message) { failure[0] = message; }
            @Override public boolean isCancelled() { return ++checks[0] >= 10; }
        };
        provider.format(fullRequest(source.toString()), response);
        assertTrue(checks[0] >= 10);
        assertFalse(completed[0]);
        assertNull(failure[0]);
    }

    @Test
    public void selectionMustContainACompleteJsonValue() {
        String source = "start {\"x\":1} end";
        int from = source.indexOf('{');
        int to = source.indexOf('}') ;
        Result result = format(request(source, from, to, true, 4, false));
        assertNotNull(result.failure);
        assertEquals(0, result.edits.size);
    }

    private Result format(String source) {
        return format(fullRequest(source));
    }

    private Result format(String source, boolean selectionOnly, int indentWidth, boolean useTabs) {
        CodeFormattingRequest request = new CodeFormattingRequest(source, 1,
            rangeAtOffsets(source, 0, source.length()), 0, source.length(), selectionOnly, indentWidth, useTabs);
        Capture capture = new Capture();
        provider.format(request, capture);
        return new Result(capture.edits, capture.failure, capture.completed);
    }

    private Result format(CodeFormattingRequest request) {
        Capture capture = new Capture();
        provider.format(request, capture);
        return new Result(capture.edits, capture.failure, capture.completed);
    }

    private static CodeFormattingRequest fullRequest(String source) {
        return request(source, 0, source.length(), false, 4, false);
    }

    private static CodeFormattingRequest request(String source, int start, int end,
                                                 boolean selectionOnly, int indentWidth, boolean useTabs) {
        return new CodeFormattingRequest(source, 1, rangeAtOffsets(source, start, end),
            start, end, selectionOnly, indentWidth, useTabs);
    }

    private static CodeEditorTextRange rangeAtOffsets(String text, int start, int end) {
        Position a = position(text, start);
        Position b = position(text, end);
        return new CodeEditorTextRange(a.line, a.column, b.line, b.column);
    }

    private static Position position(String text, int offset) {
        int line = 0;
        int column = 0;
        for (int i = 0; i < offset; i++) {
            if (text.charAt(i) == '\n') {
                line++;
                column = 0;
            } else {
                column++;
            }
        }
        return new Position(line, column);
    }

    private static int offset(String text, int targetLine, int targetColumn) {
        int line = 0;
        int column = 0;
        for (int i = 0; i <= text.length(); i++) {
            if (line == targetLine && column == targetColumn) return i;
            if (i == text.length()) break;
            if (text.charAt(i) == '\n') {
                line++;
                column = 0;
            } else {
                column++;
            }
        }
        throw new AssertionError("position outside source: " + targetLine + ":" + targetColumn);
    }

    private static String substringForRange(String text, CodeEditorTextRange range) {
        return text.substring(offset(text, range.startLine, range.startColumn),
            offset(text, range.endLine, range.endColumn));
    }

    private static String apply(String source, Array<CodeEditorTextEdit> edits) {
        List<CodeEditorTextEdit> ordered = new ArrayList<>();
        for (int i = 0; i < edits.size; i++) ordered.add(edits.get(i));
        ordered.sort(Comparator.comparingInt(
            (CodeEditorTextEdit edit) -> offset(source, edit.range.startLine, edit.range.startColumn)).reversed());
        StringBuilder result = new StringBuilder(source);
        for (CodeEditorTextEdit edit : ordered) {
            int start = offset(source, edit.range.startLine, edit.range.startColumn);
            int end = offset(source, edit.range.endLine, edit.range.endColumn);
            result.replace(start, end, edit.replacement);
        }
        return result.toString();
    }

    private static boolean isJsonWhitespace(String text) {
        for (int i = 0; i < text.length(); i++) {
            char value = text.charAt(i);
            if (value != ' ' && value != '\t' && value != '\r' && value != '\n') return false;
        }
        return true;
    }

    private static String nestedArrays(int depth) {
        StringBuilder result = new StringBuilder(depth * 2 + 1);
        for (int i = 0; i < depth; i++) result.append('[');
        result.append('0');
        for (int i = 0; i < depth; i++) result.append(']');
        return result.toString();
    }

    private static final class Capture implements CodeFormattingResponse {
        private Array<CodeEditorTextEdit> edits = new Array<>();
        private String failure;
        private boolean completed;
        private boolean cancelled;

        @Override public void complete(Array<CodeEditorTextEdit> edits) {
            this.edits = edits;
            completed = true;
        }

        @Override public void fail(String message) { failure = message; }
        @Override public boolean isCancelled() { return cancelled; }
    }

    private static final class Result {
        private final Array<CodeEditorTextEdit> edits;
        private final String failure;
        private final boolean completed;
        private Result(Array<CodeEditorTextEdit> edits, String failure, boolean completed) {
            this.edits = edits;
            this.failure = failure;
            this.completed = completed;
        }
    }

    private static final class Position {
        private final int line;
        private final int column;
        private Position(int line, int column) { this.line = line; this.column = column; }
    }
}
