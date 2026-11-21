package com.lzt841.editor.formatting;

import com.badlogic.gdx.utils.Array;
import com.lzt841.editor.CodeEditor;
import com.lzt841.editor.CodeEditorTextEdit;
import com.lzt841.editor.CodeEditorTextRange;
import org.junit.Before;
import org.junit.Test;

import static com.lzt841.editor.EditorTestSupport.createEditor;
import static org.junit.Assert.*;

/** Transaction boundaries used by async tools even when application callbacks reenter the editor. */
public class CodeFormattingCommitGuardTest {
    private CodeEditor editor;
    private Array<CodeEditorTextEdit> edits;
    private int events;

    @Before public void setup() {
        editor = createEditor("ab\ncd");
        editor.setSelection(0, 1, 1, 1);
        editor.addContentListener((source, event) -> events++);
        edits = Array.with(new CodeEditorTextEdit(0, 0, 0, 1, "XY"),
            new CodeEditorTextEdit(1, 0, 1, 1, "Z"));
    }

    @Test public void rejectingGuardRunsAfterOneFilterPassAndPreservesAllEditorState() {
        int[] filters = {0};
        int[] guards = {0};
        int version = editor.getDocumentVersion();
        editor.setInputFilter((source, character) -> { filters[0]++; return true; });
        assertFalse(editor.applyEditsExact(edits, "Guarded", () -> {
            assertEquals(3, filters[0]);
            assertEquals("ab\ncd", editor.getText());
            guards[0]++;
            return false;
        }));
        assertEquals(1, guards[0]);
        assertEquals(version, editor.getDocumentVersion());
        assertEquals("ab\ncd", editor.getText());
        assertEquals(new CodeEditorTextRange(0, 1, 1, 1), editor.getSelection());
        assertEquals(0, editor.getUndoEntryCount());
        assertEquals(0, events);
    }

    @Test public void acceptingGuardCommitsOnceWithOneUndoAndOneEvent() {
        int[] filters = {0};
        int[] guards = {0};
        editor.setInputFilter((source, character) -> { filters[0]++; return true; });
        assertTrue(editor.applyEditsExact(edits, "Guarded", () -> { guards[0]++; return true; }));
        assertEquals(3, filters[0]);
        assertEquals(1, guards[0]);
        assertEquals("XYb\nZd", editor.getText());
        assertEquals(1, events);
        assertEquals(1, editor.getUndoEntryCount());
        assertEquals("Guarded", editor.getUndoLabel());
        assertTrue(editor.undo());
        assertEquals("ab\ncd", editor.getText());
    }

    @Test public void guardCannotAuthorizeStalePositionsAfterChangingTheDocument() {
        assertFalse(editor.applyEditsExact(edits, "Stale", () -> {
            editor.setText("host update");
            return true;
        }));
        assertEquals("host update", editor.getText());
        assertEquals(0, editor.getUndoEntryCount());
        assertFalse(editor.isCompoundEditInProgress());
    }

    @Test public void editableStateIsRecheckedAfterTheGuardCallback() {
        int version = editor.getDocumentVersion();
        assertFalse(editor.applyEditsExact(edits, "Blocked", () -> {
            editor.setReadOnly(true);
            return true;
        }));
        assertEquals(version, editor.getDocumentVersion());
        assertEquals("ab\ncd", editor.getText());
        assertEquals(new CodeEditorTextRange(0, 1, 1, 1), editor.getSelection());
        assertEquals(0, events);
    }

    @Test public void guardExceptionPropagatesWithoutOpeningAnUndoGroup() {
        RuntimeException expected = new IllegalStateException("host guard");
        try {
            editor.applyEditsExact(edits, "Failed", () -> { throw expected; });
            fail("expected guard exception");
        } catch (RuntimeException actual) { assertSame(expected, actual); }
        assertEquals("ab\ncd", editor.getText());
        assertEquals(new CodeEditorTextRange(0, 1, 1, 1), editor.getSelection());
        assertEquals(0, events);
        assertEquals(0, editor.getUndoEntryCount());
        assertFalse(editor.isCompoundEditInProgress());
    }
}
