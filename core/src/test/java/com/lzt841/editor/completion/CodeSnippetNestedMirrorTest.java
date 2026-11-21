package com.lzt841.editor.completion;

import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.IntArray;
import com.lzt841.editor.CodeEditor;
import com.lzt841.editor.CodeEditorTextRange;
import org.junit.Test;

import static com.lzt841.editor.EditorTestSupport.createEditor;
import static org.junit.Assert.*;

public class CodeSnippetNestedMirrorTest {
    @Test public void editingAChildUpdatesItsParentsEarlierMirrorAndKeepsTheCaretInTheChild() {
        CodeEditor editor = createEditor("");
        CodeSnippetSession session = CodeSnippetSession.start(editor,
            "$2 | ${2:pre ${1:x}} | $1 | ${3:tail}$0");
        assertNotNull(session);
        assertEquals("pre x | pre x | x | tail", editor.getText());

        assertTrue(editor.insertTextAtCursor("wide"));

        assertEquals("pre wide | pre wide | wide | tail", editor.getText());
        assertTrue(session.isActive());
        assertEquals(new CodeEditorTextRange(0, 15, 0, 19), session.getCurrentRange());
        assertEquals(0, editor.getCursorLine());
        assertEquals(19, editor.getCursorColumn());
        assertTrue(session.next());
        assertEquals(new CodeEditorTextRange(0, 0, 0, 8), editor.getSelection());
        assertEquals("pre wide", editor.getSelectionText());
        assertTrue(session.next());
        assertEquals(new CodeEditorTextRange(0, 29, 0, 33), editor.getSelection());
        assertTrue(session.next());
        assertFalse(session.isActive());
        assertEquals(33, editor.getCursorColumn());
    }

    @Test public void aMirrorInsideAnotherFieldUpdatesThatFieldsMirrors() {
        CodeEditor editor = createEditor("");
        CodeSnippetSession session = CodeSnippetSession.start(editor,
            "${1:x} | ${2:p $1} | $2 | ${3:tail}$0");
        assertNotNull(session);
        assertEquals("x | p x | p x | tail", editor.getText());

        assertTrue(editor.insertTextAtCursor("wide"));

        assertEquals("wide | p wide | p wide | tail", editor.getText());
        assertTrue(session.isActive());
        assertEquals(new CodeEditorTextRange(0, 0, 0, 4), session.getCurrentRange());
        assertEquals(4, editor.getCursorColumn());
        assertTrue(session.next());
        assertEquals(new CodeEditorTextRange(0, 7, 0, 13), editor.getSelection());
        assertEquals("p wide", editor.getSelectionText());
        assertTrue(session.next());
        assertEquals(new CodeEditorTextRange(0, 25, 0, 29), editor.getSelection());
        assertTrue(session.next());
        assertEquals(29, editor.getCursorColumn());
    }

    @Test public void changingTheInnermostFieldUpdatesEveryAncestorLevel() {
        CodeEditor editor = createEditor("");
        CodeSnippetSession session = CodeSnippetSession.start(editor,
            "${3:A ${2:B ${1:x}}} | $3 | $2 | $1 ${4:tail}$0");
        assertNotNull(session);
        assertEquals("A B x | A B x | B x | x tail", editor.getText());

        assertTrue(editor.insertTextAtCursor("wide"));

        assertEquals("A B wide | A B wide | B wide | wide tail", editor.getText());
        assertTrue(session.isActive());
        assertEquals(new CodeEditorTextRange(0, 4, 0, 8), session.getCurrentRange());
        assertEquals(8, editor.getCursorColumn());
        assertTrue(session.next());
        assertEquals(new CodeEditorTextRange(0, 2, 0, 8), editor.getSelection());
        assertEquals("B wide", editor.getSelectionText());
        assertTrue(session.next());
        assertEquals(new CodeEditorTextRange(0, 0, 0, 8), editor.getSelection());
        assertEquals("A B wide", editor.getSelectionText());
        assertTrue(session.next());
        assertEquals(new CodeEditorTextRange(0, 36, 0, 40), editor.getSelection());
        assertTrue(session.next());
        assertEquals(40, editor.getCursorColumn());
    }

    @Test public void ancestorPropagationRunsAfterItsChildEvenWhenTheParentComesFirstInTabOrder() {
        CodeEditor editor = createEditor("");
        CodeSnippetSession session = CodeSnippetSession.start(editor,
            "${1:p ${2:x}} | $1 | $2 | ${3:tail}$0");
        assertNotNull(session);
        assertTrue(session.next());
        assertEquals(2, session.getCurrentTabStop());

        assertTrue(editor.insertTextAtCursor("wide"));

        assertEquals("p wide | p wide | wide | tail", editor.getText());
        assertTrue(session.isActive());
        assertEquals(new CodeEditorTextRange(0, 2, 0, 6), session.getCurrentRange());
        assertEquals(6, editor.getCursorColumn());
        assertTrue(session.previous());
        assertEquals(new CodeEditorTextRange(0, 0, 0, 6), editor.getSelection());
        assertTrue(session.next());
        assertTrue(session.next());
        assertEquals(new CodeEditorTextRange(0, 25, 0, 29), editor.getSelection());
        assertTrue(session.next());
        assertEquals(29, editor.getCursorColumn());
    }

    @Test public void ancestorPropagationFollowsDependenciesAcrossSeparateOccurrences() {
        CodeEditor editor = createEditor("");
        CodeSnippetSession session = CodeSnippetSession.start(editor,
            "${1:x} | ${2:p $1} | ${3:q $2} | $3 | ${4:tail}$0");
        assertNotNull(session);

        assertTrue(editor.insertTextAtCursor("wide"));

        assertEquals("wide | p wide | q p wide | q p wide | tail", editor.getText());
        assertTrue(session.isActive());
        assertEquals(4, editor.getCursorColumn());
        assertTrue(session.next());
        assertEquals(new CodeEditorTextRange(0, 7, 0, 13), editor.getSelection());
        assertTrue(session.next());
        assertEquals(new CodeEditorTextRange(0, 16, 0, 24), editor.getSelection());
        assertEquals("q p wide", editor.getSelectionText());
        assertTrue(session.next());
        assertEquals(new CodeEditorTextRange(0, 38, 0, 42), editor.getSelection());
        assertTrue(session.next());
        assertEquals(42, editor.getCursorColumn());
    }

    @Test public void allChildrenOfOneParentAreUpdatedBeforeItsMirrorsAreCopied() {
        CodeEditor editor = createEditor("");
        CodeSnippetSession session = CodeSnippetSession.start(editor,
            "${1:x} | ${2:$1,$1} | $2 | ${3:tail}$0");
        assertNotNull(session);

        assertTrue(editor.insertTextAtCursor("wide"));

        assertEquals("wide | wide,wide | wide,wide | tail", editor.getText());
        assertTrue(session.isActive());
        assertTrue(session.next());
        assertEquals(new CodeEditorTextRange(0, 7, 0, 16), editor.getSelection());
        assertEquals("wide,wide", editor.getSelectionText());
        assertTrue(session.next());
        assertEquals(new CodeEditorTextRange(0, 31, 0, 35), editor.getSelection());
        assertTrue(session.next());
        assertEquals(35, editor.getCursorColumn());
    }

    @Test public void multilineChildAndAncestorMirrorsKeepTheirFollowingStopsOnTheRightLines() {
        CodeEditor editor = createEditor("");
        CodeSnippetSession session = CodeSnippetSession.start(editor,
            "${2:pre ${1:x}} | $2 | $1 | ${3:tail}$0");
        assertNotNull(session);

        assertTrue(editor.insertTextAtCursor("a\nb"));

        assertEquals("pre a\nb | pre a\nb | a\nb | tail", editor.getText());
        assertTrue(session.isActive());
        assertEquals(new CodeEditorTextRange(0, 4, 1, 1), session.getCurrentRange());
        assertEquals(1, editor.getCursorLine());
        assertEquals(1, editor.getCursorColumn());
        assertTrue(session.next());
        assertEquals(new CodeEditorTextRange(0, 0, 1, 1), editor.getSelection());
        assertEquals("pre a\nb", editor.getSelectionText());
        assertTrue(session.next());
        assertEquals(new CodeEditorTextRange(3, 4, 3, 8), editor.getSelection());
        assertEquals("tail", editor.getSelectionText());
        assertTrue(session.next());
        assertFalse(session.isActive());
        assertEquals(3, editor.getCursorLine());
        assertEquals(8, editor.getCursorColumn());
    }

    @Test public void rejectingAnAncestorMirrorKeepsEveryCopyUnchangedAndPreservesTheUsersEdit() {
        CodeEditor editor = createEditor("");
        CodeSnippetSession session = CodeSnippetSession.start(editor,
            "${2:p ${1:x}} | $1 | $2 | ${3:tail}$0");
        assertNotNull(session);
        editor.setInputFilter((source, character) -> character != 'p');

        assertTrue(editor.insertTextAtCursor("y"));

        assertEquals("p y | x | p x | tail", editor.getText());
        assertFalse(session.isActive());
        assertEquals(2, editor.getUndoEntryCount());
        assertTrue(editor.undo());
        assertEquals("p x | x | p x | tail", editor.getText());
    }

    @Test public void mergedChildAndAncestorMirrorsAreFilteredOnceAndUseOneUndoStep() {
        CodeEditor editor = createEditor("");
        CodeSnippetSession session = CodeSnippetSession.start(editor,
            "${2:p ${1:x}} | ${2:q $1} | $1$0");
        assertNotNull(session);
        assertEquals("p x | q x | x", editor.getText());
        int[] calls = {0};
        editor.setInputFilter((source, character) -> { calls[0]++; return true; });

        assertTrue(editor.insertTextAtCursor("y"));

        assertEquals("p y | p y | y", editor.getText());
        assertTrue(session.isActive());
        // The user's y, one enclosing parent mirror p y, and one uncovered child mirror y.
        assertEquals(5, calls[0]);
        assertEquals(3, editor.getUndoEntryCount());
        assertEquals("Mirror Snippet", editor.getUndoLabel());
        assertTrue(editor.undo());
        assertEquals("p y | q x | x", editor.getText());
        assertFalse(session.isActive());
        assertTrue(editor.undo());
        assertEquals("p x | q x | x", editor.getText());
    }

    @Test public void aCrossOccurrenceIndexCycleIsRejectedBeforeEditing() {
        assertRejectedWithoutEditing(CodeSnippetTemplate.parse("${1:${2:x}} | ${2:${1:y}}$0"));
    }

    @Test public void aThreeIndexCycleIsRejectedEvenThoughEachOccurrenceTreeIsAcyclic() {
        assertRejectedWithoutEditing(CodeSnippetTemplate.parse(
            "${1:a${2:b}} | ${2:c${3:d}} | ${3:e${1:f}}$0"));
    }

    @Test public void overlappingSameIndexFieldsAreRejectedBeforeAMirrorCanOverwriteTheSource() {
        assertRejectedWithoutEditing(manualTemplate("abc",
            new CodeSnippetPlaceholder(1, 0, 2), new CodeSnippetPlaceholder(1, 1, 3)));
        assertRejectedWithoutEditing(manualTemplate("abc",
            new CodeSnippetPlaceholder(1, 1, 3), new CodeSnippetPlaceholder(1, 0, 2)));
    }

    @Test public void containingAndIdenticalSameIndexFieldsAreRejectedWithoutParentMetadata() {
        assertRejectedWithoutEditing(manualTemplate("abc",
            new CodeSnippetPlaceholder(1, 0, 3), new CodeSnippetPlaceholder(1, 1, 2)));
        assertRejectedWithoutEditing(manualTemplate("abc",
            new CodeSnippetPlaceholder(1, 0, 3), new CodeSnippetPlaceholder(1, 0, 3)));
    }

    @Test public void anEmptySameIndexFieldStrictlyInsideAnotherCopyIsRejected() {
        assertRejectedWithoutEditing(manualTemplate("abc",
            new CodeSnippetPlaceholder(1, 0, 3), new CodeSnippetPlaceholder(1, 1, 1)));
        assertRejectedWithoutEditing(manualTemplate("abc",
            new CodeSnippetPlaceholder(1, 1, 1), new CodeSnippetPlaceholder(1, 0, 3)));
    }

    @Test public void partiallyCrossingDifferentIndexFieldsAreRejectedInEitherArrayOrder() {
        assertRejectedWithoutEditing(manualTemplate("abcd",
            new CodeSnippetPlaceholder(1, 0, 3), new CodeSnippetPlaceholder(2, 1, 4)));
        assertRejectedWithoutEditing(manualTemplate("abcd",
            new CodeSnippetPlaceholder(2, 1, 4), new CodeSnippetPlaceholder(1, 0, 3)));
    }

    @Test public void adjacentSameIndexFieldsRemainLegalAndMirrorIndependently() {
        CodeEditor editor = createEditor("");
        CodeSnippetSession session = CodeSnippetSession.startParsed(editor, manualTemplate("xx",
            new CodeSnippetPlaceholder(1, 0, 1), new CodeSnippetPlaceholder(1, 1, 2)), null);
        assertNotNull(session);

        assertTrue(editor.insertTextAtCursor("wide"));

        assertEquals("widewide", editor.getText());
        assertTrue(session.isActive());
        assertEquals(new CodeEditorTextRange(0, 0, 0, 4), session.getCurrentRange());
        assertEquals(4, editor.getCursorColumn());
        assertTrue(session.next());
        assertEquals(8, editor.getCursorColumn());
    }

    @Test public void coincidentEmptySameIndexFieldsRemainLegal() {
        CodeEditor editor = createEditor("");
        CodeSnippetSession session = CodeSnippetSession.startParsed(editor, manualTemplate("",
            new CodeSnippetPlaceholder(1, 0, 0), new CodeSnippetPlaceholder(1, 0, 0),
            new CodeSnippetPlaceholder(1, 0, 0)), null);
        assertNotNull(session);

        assertTrue(editor.insertTextAtCursor("x"));

        assertEquals("xxx", editor.getText());
        assertTrue(session.isActive());
        assertEquals(new CodeEditorTextRange(0, 0, 0, 1), session.getCurrentRange());
        assertEquals(1, editor.getCursorColumn());
        assertTrue(session.next());
        assertEquals(3, editor.getCursorColumn());
    }

    @Test public void emptySameIndexFieldsAtBothBoundariesRemainLegal() {
        CodeEditor editor = createEditor("");
        CodeSnippetSession session = CodeSnippetSession.startParsed(editor, manualTemplate("x",
            new CodeSnippetPlaceholder(1, 0, 0), new CodeSnippetPlaceholder(1, 0, 1),
            new CodeSnippetPlaceholder(1, 1, 1)), null);

        assertNotNull(session);
        assertTrue(session.isActive());
        assertEquals("x", editor.getText());
        assertEquals(new CodeEditorTextRange(0, 0, 0, 0), session.getCurrentRange());
        session.cancel();
    }

    @Test public void manuallyContainedDifferentIndexFieldsRemainLegalWithoutParentMetadata() {
        CodeEditor editor = createEditor("");
        CodeSnippetSession session = CodeSnippetSession.startParsed(editor, manualTemplate("abc",
            new CodeSnippetPlaceholder(2, 0, 3), new CodeSnippetPlaceholder(1, 1, 2)), null);

        assertNotNull(session);
        assertTrue(session.isActive());
        assertEquals(new CodeEditorTextRange(0, 1, 0, 2), editor.getSelection());
        assertTrue(session.next());
        assertEquals(new CodeEditorTextRange(0, 0, 0, 3), editor.getSelection());
        assertEquals("abc", editor.getSelectionText());
        session.cancel();
    }

    private static CodeSnippetTemplate manualTemplate(String text, CodeSnippetPlaceholder... fields) {
        Array<CodeSnippetPlaceholder> placeholders = Array.with(fields);
        IntArray order = new IntArray();
        for (CodeSnippetPlaceholder field : fields) {
            if (field.index > 0 && !order.contains(field.index)) order.add(field.index);
        }
        order.sort();
        order.add(0);
        placeholders.add(new CodeSnippetPlaceholder(0, text.length(), text.length()));
        return new CodeSnippetTemplate("", text, placeholders, order);
    }

    private static void assertRejectedWithoutEditing(CodeSnippetTemplate template) {
        CodeEditor editor = createEditor("keep");
        assertTrue(editor.insertTextAtCursor("!"));
        assertTrue(editor.undo());
        editor.setSelection(0, 4, 0, 0);
        int version = editor.getDocumentVersion();
        CodeEditorTextRange selection = editor.getSelection();
        int[] events = {0};
        int[] filters = {0};
        editor.addContentListener((source, event) -> events[0]++);
        editor.setInputFilter((source, character) -> { filters[0]++; return true; });

        assertNull(CodeSnippetSession.startParsed(editor, template, null));

        assertEquals("keep", editor.getText());
        assertEquals(selection, editor.getSelection());
        assertEquals(0, editor.getCursorColumn());
        assertEquals(version, editor.getDocumentVersion());
        assertEquals(0, editor.getUndoEntryCount());
        assertEquals(1, editor.getRedoEntryCount());
        assertEquals(0, events[0]);
        assertEquals(0, filters[0]);
    }
}
