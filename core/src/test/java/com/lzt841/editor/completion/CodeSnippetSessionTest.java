package com.lzt841.editor.completion;

import com.lzt841.editor.CodeEditor;
import com.lzt841.editor.CodeEditorTextRange;
import org.junit.Test;

import static com.lzt841.editor.EditorTestSupport.createEditor;
import static com.lzt841.editor.EditorTestSupport.keyDown;
import static com.lzt841.editor.EditorTestSupport.keyTyped;
import static org.junit.Assert.*;

public class CodeSnippetSessionTest {
    @Test public void unchangedBodyStartsStopsWithoutFilteringOrChangingHistory() {
        CodeEditor editor = createEditor("alpha alpha");
        assertTrue(editor.insertTextAtCursor("!"));
        assertTrue(editor.undo());
        editor.setCursorPosition(0, 5);
        int version = editor.getDocumentVersion();
        int[] events = {0};
        int[] filters = {0};
        editor.addContentListener((source, event) -> events[0]++);
        editor.setInputFilter((source, character) -> { filters[0]++; return false; });

        CodeSnippetSession session = CodeSnippetSession.start(editor, "${1:alpha} $1$0",
            new CodeEditorTextRange(0, 0, 0, 11));

        assertNotNull(session);
        assertTrue(session.isActive());
        assertEquals(new CodeEditorTextRange(0, 0, 0, 5), editor.getSelection());
        assertEquals("alpha alpha", editor.getText());
        assertEquals(version, editor.getDocumentVersion());
        assertEquals(0, editor.getUndoEntryCount());
        assertEquals(1, editor.getRedoEntryCount());
        assertEquals(0, events[0]);
        assertEquals(0, filters[0]);
        assertTrue(session.next());
        assertFalse(session.isActive());
        assertNull(editor.getSelection());
        assertEquals(11, editor.getCursorColumn());
    }

    @Test public void unchangedSelectionStartsAStopWithoutReinsertingIt() {
        CodeEditor editor = createEditor("alpha");
        editor.setSelection(0, 5, 0, 0);
        int version = editor.getDocumentVersion();

        CodeSnippetSession session = CodeSnippetSession.start(editor, "${1:alpha}$0");

        assertNotNull(session);
        assertEquals(new CodeEditorTextRange(0, 0, 0, 5), editor.getSelection());
        assertEquals(version, editor.getDocumentVersion());
        assertEquals(0, editor.getUndoEntryCount());
        session.cancel();
    }

    @Test public void emptyPlaceholderAtCaretCanBeFilledAndFinished() {
        CodeEditor editor = createEditor("abc");
        editor.setCursorPosition(0, 1);
        int version = editor.getDocumentVersion();

        CodeSnippetSession session = CodeSnippetSession.start(editor, "${1}$0");

        assertNotNull(session);
        assertEquals(version, editor.getDocumentVersion());
        assertEquals(0, editor.getUndoEntryCount());
        assertTrue(editor.insertTextAtCursor("X"));
        assertEquals("aXbc", editor.getText());
        assertTrue(session.next());
        assertFalse(session.isActive());
        assertEquals(2, editor.getCursorColumn());
    }

    @Test public void tabImmediatelyAfterTypingFlushesAndMirrorsBeforeSelectingTheNextStop() {
        CodeEditor editor = createEditor("");
        CodeSnippetSession session = CodeSnippetSession.start(editor, "${1:x} | $1 | ${2:tail}$0");
        assertNotNull(session);

        assertTrue(keyTyped(editor, 'w'));
        assertTrue(keyTyped(editor, 'i'));
        assertTrue(keyTyped(editor, 'd'));
        assertTrue(keyTyped(editor, 'e'));
        assertEquals("wide | x | tail", editor.getText());
        assertTrue(keyDown(editor, com.badlogic.gdx.Input.Keys.TAB));

        assertTrue(session.isActive());
        assertEquals("wide | wide | tail", editor.getText());
        assertEquals(2, session.getCurrentTabStop());
        assertEquals(new CodeEditorTextRange(0, 14, 0, 18), editor.getSelection());
        editor.act(0f);
        assertTrue(session.isActive());
        assertEquals("tail", editor.getSelectionText());
        assertTrue(session.next());
        assertFalse(session.isActive());
        assertEquals(18, editor.getCursorColumn());
    }

    @Test public void previousImmediatelyAfterTypingFlushesTheCurrentStopFirst() {
        CodeEditor editor = createEditor("");
        CodeSnippetSession session = CodeSnippetSession.start(editor, "${1:first} | ${2:x} | $2$0");
        assertNotNull(session);
        assertTrue(session.next());
        assertTrue(keyTyped(editor, 'A'));
        assertTrue(keyTyped(editor, 'B'));

        assertTrue(session.previous());

        assertEquals("first | AB | AB", editor.getText());
        assertEquals(1, session.getCurrentTabStop());
        assertEquals("first", editor.getSelectionText());
        assertTrue(session.next());
        assertEquals("AB", editor.getSelectionText());
    }

    @Test public void unchangedBodyCanVisitOnlyItsFinalStop() {
        CodeEditor editor = createEditor("alpha");
        int version = editor.getDocumentVersion();

        assertNull(CodeSnippetSession.start(editor, "al$0pha", new CodeEditorTextRange(0, 0, 0, 5)));

        assertEquals("alpha", editor.getText());
        assertEquals(2, editor.getCursorColumn());
        assertNull(editor.getSelection());
        assertEquals(version, editor.getDocumentVersion());
        assertEquals(0, editor.getUndoEntryCount());
    }

    @Test public void blockedModesDoNotStartStopsOnExistingText() {
        CodeEditor editor = createEditor("alpha");
        editor.setCursorPosition(0, 2);
        int version = editor.getDocumentVersion();
        for (int mode = 0; mode < 3; mode++) {
            editor.setReadOnly(mode == 0);
            editor.setDisabled(mode == 1);
            editor.setPasswordMode(mode == 2);
            assertNull(CodeSnippetSession.start(editor, "${1:alpha}$0", new CodeEditorTextRange(0, 0, 0, 5)));
            assertEquals(2, editor.getCursorColumn());
            assertNull(editor.getSelection());
        }
        assertEquals(version, editor.getDocumentVersion());
        assertEquals(0, editor.getUndoEntryCount());
    }

    @Test public void invalidRangeCannotIntroduceAnEmptyStopInsideASurrogatePair() {
        CodeEditor editor = createEditor("\uD83D\uDE42alpha");
        int version = editor.getDocumentVersion();

        assertNull(CodeSnippetSession.start(editor, "${1}$0", new CodeEditorTextRange(0, 1, 0, 1)));

        assertEquals(0, editor.getCursorColumn());
        assertNull(editor.getSelection());
        assertEquals(version, editor.getDocumentVersion());
    }
}
