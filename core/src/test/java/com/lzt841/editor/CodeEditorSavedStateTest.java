package com.lzt841.editor;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class CodeEditorSavedStateTest {
    @Test
    public void facadeSaveMethodsDoNotAlterDocumentStateOrNotifyContentListeners() {
        CodeEditor editor = EditorTestSupport.createEditor("abc");
        int[] eventCount = {0};
        editor.addContentListener((source, event) -> eventCount[0]++);
        assertTrue(editor.replaceRange(0, 3, 0, 3, "X"));
        assertEquals(1, eventCount[0]);
        assertTrue(editor.isDirty());

        int version = editor.getDocumentVersion();
        int cursorLine = editor.getCursorLine();
        int cursorColumn = editor.getCursorColumn();
        int undoEntries = editor.getUndoEntryCount();
        int redoEntries = editor.getRedoEntryCount();
        String persistedText = editor.getText();

        editor.markSaved();

        assertFalse(editor.isDirty());
        assertEquals(persistedText, editor.getText());
        assertEquals(version, editor.getDocumentVersion());
        assertEquals(cursorLine, editor.getCursorLine());
        assertEquals(cursorColumn, editor.getCursorColumn());
        assertEquals(undoEntries, editor.getUndoEntryCount());
        assertEquals(redoEntries, editor.getRedoEntryCount());
        assertEquals(1, eventCount[0]);

        assertTrue(editor.replaceRange(0, 4, 0, 4, "Y"));
        String currentText = editor.getText();
        int currentVersion = editor.getDocumentVersion();
        int currentCursorLine = editor.getCursorLine();
        int currentCursorColumn = editor.getCursorColumn();
        int currentUndoEntries = editor.getUndoEntryCount();
        int currentRedoEntries = editor.getRedoEntryCount();
        editor.markSaved(persistedText);
        assertEquals("abcXY", editor.getText());
        assertEquals(currentText, editor.getText());
        assertEquals(currentVersion, editor.getDocumentVersion());
        assertEquals(currentCursorLine, editor.getCursorLine());
        assertEquals(currentCursorColumn, editor.getCursorColumn());
        assertEquals(currentUndoEntries, editor.getUndoEntryCount());
        assertEquals(currentRedoEntries, editor.getRedoEntryCount());
        assertTrue(editor.isDirty());
        assertEquals(2, eventCount[0]);
    }

    @Test
    public void reentrantListenerCanMarkSavedThenEditAndLeaveDocumentDirty() {
        CodeEditor editor = EditorTestSupport.createEditor("abc");
        int[] eventCount = {0};
        boolean[] nestedEditStarted = {false};
        editor.addContentListener((source, event) -> {
            eventCount[0]++;
            if (!nestedEditStarted[0]) {
                nestedEditStarted[0] = true;
                source.markSaved();
                assertFalse(source.isDirty());
                assertTrue(source.replaceRange(0, 0, 0, 1, "Y"));
            }
        });

        assertTrue(editor.replaceRange(0, 0, 0, 1, "X"));

        assertEquals("Ybc", editor.getText());
        assertEquals(2, eventCount[0]);
        assertTrue(editor.isDirty());
    }
}
