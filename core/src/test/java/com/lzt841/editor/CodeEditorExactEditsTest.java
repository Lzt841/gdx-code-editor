package com.lzt841.editor;

import com.badlogic.gdx.utils.Array;
import org.junit.Before;
import org.junit.Test;

import static com.lzt841.editor.EditorTestSupport.createEditor;
import static org.junit.Assert.*;

public class CodeEditorExactEditsTest {
    private CodeEditor editor;
    private int events;

    @Before public void setup() {
        editor = createEditor("ab\ncd");
        editor.setSelection(0, 1, 1, 1);
        editor.addContentListener((source, event) -> events++);
    }

    @Test public void aRejectedCharacterRejectsTheWholeBatchBeforeDeletingAnyText() {
        editor.setInputFilter((source, character) -> character != 'Z');
        int version = editor.getDocumentVersion();
        assertFalse(editor.applyEditsExact(Array.with(
            new CodeEditorTextEdit(0, 0, 0, 1, "X"), new CodeEditorTextEdit(1, 0, 1, 1, "YZ")), "Exact"));
        assertUnchanged(version);
    }

    @Test public void statefulFiltersRunOnlyOncePerInsertedCharacter() {
        int[] calls = {0};
        editor.setInputFilter((source, character) -> ++calls[0] <= 3);
        assertTrue(editor.applyEditsExact(Array.with(
            new CodeEditorTextEdit(0, 0, 0, 1, "XY"), new CodeEditorTextEdit(1, 0, 1, 1, "Z")), "Exact"));
        assertEquals(3, calls[0]);
        assertEquals("XYb\nZd", editor.getText());
        assertEquals(1, events);
        assertEquals(1, editor.getUndoEntryCount());
        assertEquals("Exact", editor.getUndoLabel());
        assertTrue(editor.undo());
        assertEquals("ab\ncd", editor.getText());
        assertTrue(editor.redo());
        assertEquals("XYb\nZd", editor.getText());
    }

    @Test public void newlineNormalizationIsRejectedInsteadOfSilentlyChangingTheBatch() {
        int version = editor.getDocumentVersion();
        assertFalse(editor.applyEditsExact(Array.with(CodeEditorTextEdit.insert(0, 0, "x\r\ny"))));
        assertUnchanged(version);
        assertTrue(editor.applyEditsExact(Array.with(CodeEditorTextEdit.insert(0, 0, "x\ny"))));
        assertEquals("x\nyab\ncd", editor.getText());
    }

    @Test public void identicalReplacementsPreserveHistoryVersionAndSelection() {
        int version = editor.getDocumentVersion();
        assertFalse(editor.applyEditsExact(Array.with(new CodeEditorTextEdit(0, 0, 1, 2, "ab\ncd")), "Same"));
        assertUnchanged(version);
    }

    @Test public void mixedIdenticalAndChangedEditsStillApplyAsOneStep() {
        assertTrue(editor.applyEditsExact(Array.with(
            new CodeEditorTextEdit(0, 0, 0, 2, "ab"), new CodeEditorTextEdit(1, 0, 1, 2, "CD"))));
        assertEquals("ab\nCD", editor.getText());
        assertEquals(1, editor.getUndoEntryCount());
        assertEquals(1, events);
        assertTrue(editor.undo());
        assertEquals("ab\ncd", editor.getText());
    }

    @Test public void invalidAndOverlappingRangesRejectWithoutCallingTheFilter() {
        int[] calls = {0};
        editor.setInputFilter((source, character) -> { calls[0]++; return true; });
        int version = editor.getDocumentVersion();
        assertFalse(editor.applyEditsExact(Array.with(new CodeEditorTextEdit(9, 0, 9, 1, "x"))));
        assertFalse(editor.applyEditsExact(Array.with(
            new CodeEditorTextEdit(0, 0, 1, 1, "x"), new CodeEditorTextEdit(0, 1, 1, 2, "y"))));
        assertEquals(0, calls[0]);
        assertUnchanged(version);
    }

    @Test public void emptyAndBlockedBatchesLeaveSelectionAlone() {
        int version = editor.getDocumentVersion();
        Array<CodeEditorTextEdit> edits = Array.with(new CodeEditorTextEdit(0, 0, 0, 1, "X"));
        assertFalse(editor.applyEditsExact(null));
        assertFalse(editor.applyEditsExact(new Array<CodeEditorTextEdit>()));
        editor.setReadOnly(true);
        assertFalse(editor.applyEditsExact(edits));
        editor.setReadOnly(false);
        editor.setDisabled(true);
        assertFalse(editor.applyEditsExact(edits));
        editor.setDisabled(false);
        assertUnchanged(version);
    }

    @Test public void filterChangingEditorModeCannotSneakAnEditIntoAReadOnlyEditor() {
        editor.setInputFilter((source, character) -> { source.setReadOnly(true); return true; });
        int version = editor.getDocumentVersion();
        assertFalse(editor.applyEditsExact(Array.with(new CodeEditorTextEdit(0, 0, 0, 1, "X"))));
        assertUnchanged(version);
    }

    private void assertUnchanged(int version) {
        assertEquals("ab\ncd", editor.getText());
        assertEquals(version, editor.getDocumentVersion());
        assertEquals(new CodeEditorTextRange(0, 1, 1, 1), editor.getSelection());
        assertEquals(1, editor.getCursorLine());
        assertEquals(1, editor.getCursorColumn());
        assertEquals(0, editor.getUndoEntryCount());
        assertEquals(0, events);
    }
}
