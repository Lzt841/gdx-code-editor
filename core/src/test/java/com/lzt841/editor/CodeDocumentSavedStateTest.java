package com.lzt841.editor;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class CodeDocumentSavedStateTest {
    @Test
    public void setTextNormalizesNewlinesAndCreatesACleanBaseline() {
        CodeDocument document = new CodeDocument();
        assertFalse(document.isDirty());

        document.setText("one\r\n\r");

        assertEquals("one\n\n", document.getText());
        assertFalse(document.isDirty());
        document.deleteRange(1, 0, 2, 0);
        assertEquals("one\n", document.getText());
        assertTrue(document.isDirty());
        assertTrue(document.undo());
        assertEquals("one\n\n", document.getText());
        assertFalse(document.isDirty());
    }

    @Test
    public void markSavedPreservesUndoRedoAndDoesNotChangeDocumentState() {
        CodeDocument document = new CodeDocument();
        document.setText("base");
        document.moveCursorTo(0, 4);
        document.insertText("X");

        int version = document.getVersion();
        int cursorLine = document.getCursorLine();
        int cursorColumn = document.getCursorColumn();
        int undoEntries = document.getUndoEntryCount();
        int redoEntries = document.getRedoEntryCount();
        String text = document.getText();
        assertTrue(document.isDirty());

        document.markSaved();

        assertFalse(document.isDirty());
        assertEquals(text, document.getText());
        assertEquals(version, document.getVersion());
        assertEquals(cursorLine, document.getCursorLine());
        assertEquals(cursorColumn, document.getCursorColumn());
        assertEquals(undoEntries, document.getUndoEntryCount());
        assertEquals(redoEntries, document.getRedoEntryCount());

        assertTrue(document.undo());
        assertEquals("base", document.getText());
        assertTrue(document.isDirty());
        document.markSaved();
        assertEquals(1, document.getRedoEntryCount());
        assertTrue(document.redo());
        assertEquals("baseX", document.getText());
        assertTrue(document.isDirty());
    }

    @Test
    public void markSavedEndsTheCurrentTypingMergeRun() {
        CodeDocument document = new CodeDocument();
        document.insertChar('a');
        document.markSaved();
        document.insertChar('b');

        assertEquals(2, document.getUndoEntryCount());
        assertTrue(document.undo());
        assertEquals("a", document.getText());
        assertFalse(document.isDirty());
    }

    @Test
    public void equalityIsExactForHashCollisionsAndUnicodeCodeUnits() {
        CodeDocument document = new CodeDocument();
        document.setText("Aa");
        document.deleteRange(0, 0, 0, 2);
        document.insertText("BB");

        assertEquals("Aa".hashCode(), "BB".hashCode());
        assertTrue(document.isDirty());

        document.setText("\u00e9");
        document.deleteRange(0, 0, 0, 1);
        document.insertText("e\u0301");
        assertTrue(document.isDirty());
    }

    @Test
    public void savedSnapshotCanBeOlderThanCurrentText() {
        CodeDocument document = new CodeDocument();
        document.setText("saved");
        String persistedText = document.getText();
        document.moveCursorTo(0, document.getLine(0).length());
        document.insertText(" newer");

        document.markSaved(persistedText);

        assertEquals("saved newer", document.getText());
        assertTrue(document.isDirty());
        assertTrue(document.undo());
        assertFalse(document.isDirty());
    }

    @Test
    public void persistedSnapshotsNormalizeNewlinesAndPreserveTrailingSeparators() {
        CodeDocument document = new CodeDocument();
        document.setText("one\n\n");

        document.markSaved("one\r\n\r");
        assertFalse(document.isDirty());
        document.markSaved("one\r\n");
        assertTrue(document.isDirty());
        document.markSaved("one\r\n\r");
        assertFalse(document.isDirty());

        document.setText(null);
        assertEquals("", document.getText());
        assertFalse(document.isDirty());
        document.markSaved("\r");
        assertTrue(document.isDirty());
        document.markSaved(null);
        assertFalse(document.isDirty());
        document.insertChar('x');
        document.markSaved(null);
        assertTrue(document.isDirty());
        assertTrue(document.undo());
        assertFalse(document.isDirty());
    }

    @Test
    public void manualRestorationAndRedoCanReturnToTheSavedSnapshot() {
        CodeDocument document = new CodeDocument();
        document.insertChar('a');
        document.markSaved("a");
        document.insertChar('b');
        assertEquals(2, document.getUndoEntryCount());
        assertTrue(document.isDirty());
        document.backspace();
        assertEquals("a", document.getText());
        assertFalse(document.isDirty());

        assertTrue(document.undo());
        assertEquals("ab", document.getText());
        assertTrue(document.isDirty());
        assertTrue(document.redo());
        assertEquals("a", document.getText());
        assertFalse(document.isDirty());
    }

    @Test
    public void savePointInsideCompoundEditUsesExactContentWithoutSplittingTheGroup() {
        CodeDocument document = new CodeDocument();
        document.setText("a");
        document.moveCursorTo(0, 1);
        document.beginCompoundEdit();
        document.insertText("b");
        document.markSaved();
        assertFalse(document.isDirty());
        document.insertText("c");
        document.endCompoundEdit();

        assertEquals("abc", document.getText());
        assertTrue(document.isDirty());
        assertTrue(document.undo());
        assertEquals("a", document.getText());
        assertTrue(document.isDirty());
    }
}
