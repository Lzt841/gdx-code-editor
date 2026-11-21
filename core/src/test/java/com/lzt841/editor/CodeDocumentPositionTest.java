package com.lzt841.editor;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class CodeDocumentPositionTest {
    @Test
    public void mapsLineBoundariesAndClampsOffsets() {
        CodeDocument document = new CodeDocument();
        document.setText("ab\n\n\uD83D\uDE42x\nz");

        assertEquals(9, document.getTextLength());
        assertEquals(0, document.toOffset(0, 0));
        assertEquals(2, document.toOffset(0, 99));
        assertEquals(3, document.toOffset(1, 0));
        assertEquals(4, document.toOffset(2, 0));
        assertEquals(7, document.toOffset(2, 99));
        assertEquals(9, document.toOffset(3, 1));
        assertEquals(0, document.toOffset(-1, -1));

        assertPosition(document, -1, 0, 0);
        assertPosition(document, 0, 0, 0);
        assertPosition(document, 2, 0, 2);
        assertPosition(document, 3, 1, 0);
        assertPosition(document, 4, 2, 0);
        assertPosition(document, 6, 2, 2);
        assertPosition(document, 8, 3, 0);
        assertPosition(document, 9, 3, 1);
    }

    @Test
    public void updatesSameLineIndexAfterEditsAndUndoRedo() {
        CodeDocument document = new CodeDocument();
        document.setText("first\nsecond\nthird");
        assertEquals(13, document.toOffset(2, 0));

        document.moveCursorTo(1, 3);
        document.insertText("XX");
        assertEquals("first\nsecXXond\nthird", document.getText());
        assertEquals(15, document.toOffset(2, 0));
        assertEquals(20, document.getTextLength());

        assertTrue(document.undo());
        assertEquals(13, document.toOffset(2, 0));
        assertEquals(18, document.getTextLength());
        assertTrue(document.redo());
        assertEquals(15, document.toOffset(2, 0));
        assertEquals(20, document.getTextLength());
    }

    @Test
    public void rebuildsAfterStructuralEditsAndCompoundUndoRedo() {
        CodeDocument document = new CodeDocument();
        document.setText("a\nb\nc");
        assertEquals(5, document.getTextLength());
        assertEquals(4, document.toOffset(2, 0));

        document.beginCompoundEdit("split and insert");
        document.moveCursorTo(0, 1);
        document.insertText("\n");
        document.moveCursorTo(2, 0);
        document.insertText("long");
        document.endCompoundEdit();

        String edited = "a\n\nlongb\nc";
        assertEquals(edited, document.getText());
        assertEquals(10, document.getTextLength());
        assertEquals(9, document.toOffset(3, 0));
        assertTrue(document.undo());
        assertEquals("a\nb\nc", document.getText());
        assertEquals(5, document.getTextLength());
        assertTrue(document.redo());
        assertEquals(edited, document.getText());
        assertEquals(9, document.toOffset(3, 0));
    }

    @Test
    public void keepsIndexAcrossEditingPathsAndReplay() {
        CodeDocument document = new CodeDocument();
        document.setText("    alpha\n    beta\n    gamma");
        assertMatchesLinearOracle(document);

        document.moveCursorTo(0, 4);
        document.insertChar('X');
        assertMatchesLinearOracle(document);
        document.backspace();
        assertMatchesLinearOracle(document);
        document.deleteForward();
        assertMatchesLinearOracle(document);

        document.moveCursorTo(0, 4);
        document.insertNewLine();
        assertMatchesLinearOracle(document);
        document.moveCursorTo(document.getCursorLine(), 0);
        document.backspace();
        assertMatchesLinearOracle(document);
        document.moveCursorTo(0, document.getLineLength(0));
        document.deleteForward();
        assertMatchesLinearOracle(document);

        assertTrue(document.shiftLinesIndent(0, document.getLineCount() - 1, true));
        assertMatchesLinearOracle(document);
        assertTrue(document.shiftLinesIndent(0, document.getLineCount() - 1, false));
        assertMatchesLinearOracle(document);

        document.setText("    }\ntext");
        document.moveCursorTo(0, 4);
        document.dedentBeforeClosingBrace();
        assertMatchesLinearOracle(document);

        while (document.canUndo()) {
            assertTrue(document.undo());
            assertMatchesLinearOracle(document);
        }
        while (document.canRedo()) {
            assertTrue(document.redo());
            assertMatchesLinearOracle(document);
        }
    }

    @Test
    public void largeDocumentMappingMatchesUtf16Offsets() {
        StringBuilder text = new StringBuilder();
        for (int line = 0; line < 4096; line++) {
            if (line > 0) {
                text.append('\n');
            }
            text.append("line").append(line);
            if (line % 7 == 0) {
                text.append('\uD83D').append('\uDE42');
            }
            text.append('x');
        }

        CodeDocument document = new CodeDocument();
        document.setText(text.toString());
        assertEquals(text.length(), document.getTextLength());

        int expectedOffset = 0;
        for (int line = 0; line < document.getLineCount(); line++) {
            String value = document.getLine(line);
            assertEquals(expectedOffset, document.toOffset(line, 0));
            assertPosition(document, expectedOffset + value.length(), line, value.length());
            expectedOffset += value.length();
            if (line + 1 < document.getLineCount()) {
                assertPosition(document, expectedOffset + 1, line + 1, 0);
                expectedOffset++;
            }
        }
    }

    @Test
    public void mappingMatchesLinearOracleThroughRandomEdits() {
        CodeDocument document = new CodeDocument();
        document.setText("alpha\n\uD83D\uDE42\nbeta");
        java.util.Random random = new java.util.Random(0x5EED);
        String[] inserts = {"x", "\n", "\uD83D\uDE42", "a\nb"};

        for (int step = 0; step < 160; step++) {
            switch (random.nextInt(10)) {
                case 0:
                case 1:
                    moveToRandomPosition(document, random);
                    document.insertText(inserts[random.nextInt(inserts.length)]);
                    break;
                case 2:
                    int startLine = random.nextInt(document.getLineCount());
                    int endLine = random.nextInt(document.getLineCount());
                    int startColumn = random.nextInt(document.getLineLength(startLine) + 1);
                    int endColumn = random.nextInt(document.getLineLength(endLine) + 1);
                    document.deleteRange(startLine, startColumn, endLine, endColumn);
                    break;
                case 3:
                    moveToRandomPosition(document, random);
                    document.insertChar((char) ('a' + random.nextInt(26)));
                    break;
                case 4:
                    if (random.nextBoolean()) {
                        document.undo();
                    } else {
                        document.redo();
                    }
                    break;
                case 5:
                    document.setText(inserts[random.nextInt(inserts.length)]);
                    break;
                case 6:
                    moveToRandomPosition(document, random);
                    document.beginCompoundEdit("position mapping test");
                    document.insertText("q");
                    document.insertText("\n");
                    document.endCompoundEdit();
                    break;
                case 7:
                    moveToRandomPosition(document, random);
                    document.backspace();
                    break;
                case 8:
                    moveToRandomPosition(document, random);
                    document.deleteForward();
                    break;
                default:
                    int indentFromLine = random.nextInt(document.getLineCount());
                    int indentToLine = random.nextInt(document.getLineCount());
                    document.shiftLinesIndent(indentFromLine, indentToLine, random.nextBoolean());
                    break;
            }
            assertMatchesLinearOracle(document);
        }
    }

    private static void moveToRandomPosition(CodeDocument document, java.util.Random random) {
        int line = random.nextInt(document.getLineCount());
        int column = random.nextInt(document.getLineLength(line) + 1);
        document.moveCursorTo(line, column);
    }

    private static void assertMatchesLinearOracle(CodeDocument document) {
        String text = document.getText();
        assertEquals(text.length(), document.getTextLength());
        for (int offset = -1; offset <= text.length() + 1; offset++) {
            int remaining = Math.max(0, offset);
            int expectedLine = document.getLineCount() - 1;
            int expectedColumn = document.getLineLength(expectedLine);
            for (int line = 0; line < document.getLineCount(); line++) {
                int length = document.getLineLength(line);
                if (remaining <= length) {
                    expectedLine = line;
                    expectedColumn = remaining;
                    break;
                }
                remaining -= length + 1;
            }
            assertPosition(document, offset, expectedLine, expectedColumn);
        }

        int prefix = 0;
        for (int line = 0; line < document.getLineCount(); line++) {
            int length = document.getLineLength(line);
            int[] columns = {-1, 0, length / 2, length, length + 1};
            for (int column : columns) {
                assertEquals(prefix + Math.max(0, Math.min(column, length)),
                    document.toOffset(line, column));
            }
            prefix += length + 1;
        }
    }

    private static void assertPosition(CodeDocument document, int offset, int line, int column) {
        assertEquals(line, document.lineAtOffset(offset));
        assertEquals(column, document.columnAtOffset(offset));
    }
}
