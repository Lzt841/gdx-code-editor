package com.lzt841.editor;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class CodeDocumentUndoTest {
    @Test
    public void singleMutationGroupRestoresBoundaryCaretsAcrossUndoCycles() {
        CodeDocument document = new CodeDocument();
        document.setText("abc");
        document.moveCursorTo(0, 2);
        document.beginCompoundEdit("insert");
        document.moveCursorTo(0, 0);
        document.insertText("X");
        document.moveCursorTo(0, 3);
        document.endCompoundEdit();

        for (int cycle = 0; cycle < 3; cycle++) {
            assertTrue(document.undo());
            assertEquals("abc", document.getText());
            assertEquals(2, document.getCursorColumn());
            assertEquals("insert", document.getRedoLabel());
            assertTrue(document.redo());
            assertEquals("Xabc", document.getText());
            assertEquals(3, document.getCursorColumn());
            assertEquals("insert", document.getUndoLabel());
        }
    }

    @Test
    public void compoundGroupRestoresBoundaryCaretsAcrossUndoCycles() {
        CodeDocument document = new CodeDocument();
        document.setText("abc");
        document.moveCursorTo(0, 2);
        document.beginCompoundEdit("two inserts");
        document.moveCursorTo(0, 0);
        document.insertText("X");
        document.insertText("Y");
        document.moveCursorTo(0, 4);
        document.endCompoundEdit();

        for (int cycle = 0; cycle < 3; cycle++) {
            assertTrue(document.undo());
            assertEquals("abc", document.getText());
            assertEquals(2, document.getCursorColumn());
            assertTrue(document.redo());
            assertEquals("XYabc", document.getText());
            assertEquals(4, document.getCursorColumn());
        }
    }

    @Test
    public void compoundRedoReplaysLineEditsInForwardOrder() {
        CodeDocument document = new CodeDocument();
        document.setText("a\nb\nc");

        document.beginCompoundEdit();
        document.moveCursorTo(0, 1);
        document.insertText("\n");
        document.moveCursorTo(2, 0);
        document.insertText("X");
        document.endCompoundEdit();

        String edited = "a\n\nXb\nc";
        assertEquals(edited, document.getText());
        assertTrue(document.undo());
        assertEquals("a\nb\nc", document.getText());
        assertTrue(document.redo());
        assertEquals(edited, document.getText());
    }
}
