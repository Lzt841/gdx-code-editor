package com.lzt841.editor.extensions;

import com.badlogic.gdx.Input;
import com.lzt841.editor.CodeEditor;
import com.lzt841.editor.CodeEditorAction;
import com.lzt841.editor.CodeEditorCommand;
import com.lzt841.editor.CodeEditorTextRange;
import com.lzt841.editor.CodeKeyStroke;
import com.lzt841.editor.CodeKeymap;
import org.junit.Before;
import org.junit.Test;

import static com.lzt841.editor.EditorTestSupport.*;
import static com.lzt841.editor.extensions.CodeLineEditingExtension.*;
import static org.junit.Assert.*;

public class CodeLineEditingExtensionTest {
    private CodeEditor editor;

    @Before public void setup() {
        editor = createEditor("alpha\nbeta\ngamma\ndelta");
        editor.getExtensions().install(new CodeLineEditingExtension());
    }

    private boolean execute(String command) { return editor.getCommands().execute(command); }
    private void cursor(int line, int column) {
        assertEquals(line, editor.getCursorLine());
        assertEquals(column, editor.getCursorColumn());
    }

    @Test public void moveSingleLinePreservesColumnAndUsesOneNamedUndoStep() {
        editor.setCursorPosition(1, 3);
        int[] events = {0};
        editor.addContentListener((source, event) -> events[0]++);
        assertTrue(execute(MOVE_UP));
        assertEquals("beta\nalpha\ngamma\ndelta", editor.getText());
        cursor(0, 3);
        assertEquals(1, editor.getUndoEntryCount());
        assertEquals("Move Lines Up", editor.getUndoLabel());
        assertEquals(1, events[0]);
        assertTrue(editor.undo());
        assertEquals("alpha\nbeta\ngamma\ndelta", editor.getText());
        cursor(1, 3);
        assertTrue(editor.redo());
        assertEquals("beta\nalpha\ngamma\ndelta", editor.getText());
        cursor(0, 3);
        assertTrue(execute(MOVE_DOWN));
        assertEquals("alpha\nbeta\ngamma\ndelta", editor.getText());
        cursor(1, 3);
    }

    @Test public void movingAReversedSelectionPreservesItsDirectionAndUnicodeBoundaries() {
        editor.setText("zero\none\n\ud83d\ude00two\nthree\nfour");
        editor.setSelection(3, 2, 1, 1);
        String selected = editor.getSelectionText();
        assertTrue(execute(MOVE_UP));
        assertEquals("one\n\ud83d\ude00two\nthree\nzero\nfour", editor.getText());
        assertEquals(new CodeEditorTextRange(0, 1, 2, 2), editor.getSelection());
        assertEquals(selected, editor.getSelectionText());
        cursor(0, 1);
        assertTrue(execute(MOVE_DOWN));
        assertEquals("zero\none\n\ud83d\ude00two\nthree\nfour", editor.getText());
        cursor(1, 1);
    }

    @Test public void aSelectionEndingAtColumnZeroDoesNotIncludeTheNextLine() {
        editor.setText("a\nb\nc\nd");
        editor.setSelection(1, 0, 2, 0);
        assertTrue(execute(MOVE_UP));
        assertEquals("b\na\nc\nd", editor.getText());
        assertEquals(new CodeEditorTextRange(0, 0, 1, 0), editor.getSelection());
        assertEquals("b\n", editor.getSelectionText());
    }

    @Test public void movingATerminatedBlockToEofUsesTheFinalColumnInsteadOfColumnZero() {
        editor.setText("a\nb\nc");
        editor.setSelection(0, 0, 2, 0);
        assertTrue(execute(MOVE_DOWN));
        assertEquals("c\na\nb", editor.getText());
        assertEquals(new CodeEditorTextRange(1, 0, 2, 1), editor.getSelection());
        cursor(2, 1);
    }

    @Test public void boundaryNoOpsPreserveTextHistoryVersionAndSelection() {
        editor.setSelection(0, 1, 2, 0);
        CodeEditorTextRange selection = editor.getSelection();
        int version = editor.getDocumentVersion();
        assertFalse(editor.getCommands().isEnabled(MOVE_UP));
        assertFalse(execute(MOVE_UP));
        assertEquals(selection, editor.getSelection());
        assertEquals(version, editor.getDocumentVersion());
        assertEquals(0, editor.getUndoEntryCount());
        editor.setCursorPosition(3, 2);
        assertFalse(execute(MOVE_DOWN));
        cursor(3, 2);
    }

    @Test public void equalAdjacentLinesMoveTheCaretWithoutCreatingAContentEdit() {
        editor.setText("same\nsame\nother");
        editor.setCursorPosition(1, 3);
        int version = editor.getDocumentVersion();
        assertTrue(execute(MOVE_UP));
        cursor(0, 3);
        assertEquals(version, editor.getDocumentVersion());
        assertEquals(0, editor.getUndoEntryCount());
    }

    @Test public void duplicateAboveAndBelowKeepTheCaretOnTheNewCopy() {
        editor.setCursorPosition(1, 2);
        assertTrue(execute(DUPLICATE_DOWN));
        assertEquals("alpha\nbeta\nbeta\ngamma\ndelta", editor.getText());
        cursor(2, 2);
        assertEquals("Duplicate Lines Down", editor.getUndoLabel());
        assertTrue(editor.undo());
        cursor(1, 2);
        assertTrue(execute(DUPLICATE_UP));
        assertEquals("alpha\nbeta\nbeta\ngamma\ndelta", editor.getText());
        cursor(1, 2);
        assertEquals(1, editor.getUndoEntryCount());
    }

    @Test public void duplicateReversedMultiLineSelectionKeepsTheNewSelectionDirection() {
        editor.setText("a\nbeta\ngamma\nz");
        editor.setSelection(2, 3, 1, 1);
        String selected = editor.getSelectionText();
        assertTrue(execute(DUPLICATE_DOWN));
        assertEquals("a\nbeta\ngamma\nbeta\ngamma\nz", editor.getText());
        assertEquals(selected, editor.getSelectionText());
        assertEquals(new CodeEditorTextRange(3, 1, 4, 3), editor.getSelection());
        cursor(3, 1);
    }

    @Test public void duplicatingAColumnZeroTerminatedSelectionDoesNotCopyTheNextLine() {
        editor.setText("a\nb\nc");
        editor.setSelection(1, 0, 2, 0);
        assertTrue(execute(DUPLICATE_DOWN));
        assertEquals("a\nb\nb\nc", editor.getText());
        assertEquals(new CodeEditorTextRange(2, 0, 3, 0), editor.getSelection());
        assertEquals("b\n", editor.getSelectionText());
    }

    @Test public void duplicatingTheFinalLineAndAnEmptyDocumentCreatesExactlyOneSeparator() {
        editor.setText("last");
        editor.setCursorPosition(0, 4);
        assertTrue(execute(DUPLICATE_DOWN));
        assertEquals("last\nlast", editor.getText());
        cursor(1, 4);
        editor.setText("");
        assertTrue(execute(DUPLICATE_DOWN));
        assertEquals("\n", editor.getText());
        cursor(1, 0);
        assertTrue(editor.undo());
        assertEquals("", editor.getText());
    }

    @Test public void duplicateAboveKeepsATrailingNewlineAndTheSameSelection() {
        editor.setText("a\nb\n");
        editor.setSelection(0, 0, 2, 0);
        assertTrue(execute(DUPLICATE_UP));
        assertEquals("a\nb\na\nb\n", editor.getText());
        assertEquals(new CodeEditorTextRange(0, 0, 2, 0), editor.getSelection());
    }

    @Test public void deleteMiddleAndFinalLinesLeaveNoExtraOrMissingSeparators() {
        editor.setText("a\nbbb\ncc");
        editor.setCursorPosition(1, 2);
        assertTrue(execute(DELETE));
        assertEquals("a\ncc", editor.getText());
        cursor(1, 2);
        assertEquals("Delete Lines", editor.getUndoLabel());
        assertTrue(execute(DELETE));
        assertEquals("a", editor.getText());
        cursor(0, 1);
    }

    @Test public void deleteSelectionHonoursExclusiveEndpointAndCollapsesAtItsFirstLine() {
        editor.setText("a\nb\nc\nd");
        editor.setSelection(3, 0, 1, 0);
        assertTrue(execute(DELETE));
        assertEquals("a\nd", editor.getText());
        assertNull(editor.getSelection());
        cursor(1, 0);
        assertEquals(1, editor.getUndoEntryCount());
        assertTrue(editor.undo());
        assertEquals("a\nb\nc\nd", editor.getText());
    }

    @Test public void deletingAllLinesLeavesOneEmptyLineAndEmptyDeletionIsANoOp() {
        editor.selectAllText();
        assertTrue(execute(DELETE));
        assertEquals("", editor.getText());
        assertEquals(1, editor.getLineCount());
        int version = editor.getDocumentVersion();
        assertFalse(editor.getCommands().isEnabled(DELETE));
        assertFalse(execute(DELETE));
        assertEquals(version, editor.getDocumentVersion());
        assertEquals(1, editor.getUndoEntryCount());
    }

    @Test public void deletingTheFinalEmptyLineRemovesOnlyTheTrailingNewline() {
        editor.setText("abc\n");
        editor.setCursorPosition(1, 0);
        assertTrue(execute(DELETE));
        assertEquals("abc", editor.getText());
        cursor(0, 0);
    }

    @Test public void filteredMovesAndCopiesRejectTheWholeOperationWithoutLosingOldText() {
        editor.setText("a1\nb2\nc3");
        editor.setSelection(1, 0, 2, 0);
        editor.setInputFilter((source, character) -> !Character.isDigit(character));
        int version = editor.getDocumentVersion();
        assertFalse(execute(MOVE_UP));
        assertFalse(execute(MOVE_DOWN));
        assertFalse(execute(DUPLICATE_UP));
        assertFalse(execute(DUPLICATE_DOWN));
        assertEquals("a1\nb2\nc3", editor.getText());
        assertEquals(new CodeEditorTextRange(1, 0, 2, 0), editor.getSelection());
        assertEquals(version, editor.getDocumentVersion());
        assertEquals(0, editor.getUndoEntryCount());
        assertTrue(execute(DELETE)); // Pure deletion inserts no characters to filter.
        assertEquals("a1\nc3", editor.getText());
    }

    @Test public void rejectedNewlineDoesNotTurnADuplicateIntoTextOnTheSameLine() {
        editor.setCursorPosition(1, 2);
        editor.setInputFilter((source, character) -> character != '\n');
        assertFalse(execute(DUPLICATE_DOWN));
        assertEquals("alpha\nbeta\ngamma\ndelta", editor.getText());
        cursor(1, 2);
        assertEquals(0, editor.getUndoEntryCount());
    }

    @Test public void reservedBoundaryShortcutDoesNotFallBackToMovementOrLoseTheSelection() {
        editor.setKeymap(new CodeKeymap() {
            @Override public boolean altPressed() { return true; }
        }.bind(Input.Keys.UP, CodeEditorAction.MOVE_UP));
        editor.setSelection(0, 1, 2, 0);
        CodeEditorTextRange selection = editor.getSelection();
        assertFalse(execute(MOVE_UP));
        assertTrue(keyDown(editor, Input.Keys.UP));
        assertEquals(selection, editor.getSelection());
        cursor(2, 0);
        keyUp(editor, Input.Keys.UP);
    }

    @Test public void rejectedMoveShortcutAlsoPreservesTheCaretAndSelection() {
        editor.setKeymap(new CodeKeymap() {
            @Override public boolean altPressed() { return true; }
        }.bind(Input.Keys.DOWN, CodeEditorAction.MOVE_DOWN));
        editor.setSelection(1, 0, 2, 0);
        editor.setInputFilter((source, character) -> character != 'b');
        assertTrue(keyDown(editor, Input.Keys.DOWN));
        assertEquals(new CodeEditorTextRange(1, 0, 2, 0), editor.getSelection());
        cursor(2, 0);
        assertEquals("alpha\nbeta\ngamma\ndelta", editor.getText());
        keyUp(editor, Input.Keys.DOWN);
    }

    @Test public void commandsRespectBlockedModesAndDefaultBindingsCanBeOmitted() {
        editor.setCursorPosition(1, 0);
        editor.setReadOnly(true);
        assertFalse(execute(MOVE_UP));
        assertFalse(execute(DUPLICATE_DOWN));
        editor.setReadOnly(false);
        editor.setPasswordMode(true);
        assertFalse(execute(DELETE));
        editor.setPasswordMode(false);
        editor.setDisabled(true);
        assertFalse(execute(MOVE_DOWN));
        editor.setDisabled(false);
        assertEquals(1, editor.getCommands().getShortcuts(MOVE_UP).size);
        editor.getExtensions().uninstall(ID);
        editor.getExtensions().install(new CodeLineEditingExtension(false));
        assertEquals(0, editor.getCommands().getShortcuts(MOVE_UP).size);
        assertTrue(execute(MOVE_UP));
    }

    @Test public void uninstallReleasesReboundShortcutsAndConflictsRollBackEarlierCommands() {
        editor.getCommands().setShortcuts(MOVE_UP, CodeKeyStroke.of(Input.Keys.F7));
        editor.getExtensions().uninstall(ID);
        assertEquals(0, editor.getCommands().size());
        assertFalse(editor.getCommands().executeShortcut(CodeKeyStroke.of(Input.Keys.F7)));
        editor.getCommands().register(new CodeEditorCommand(DUPLICATE_DOWN, "Host", source -> true));
        assertThrows(IllegalArgumentException.class, () -> editor.getExtensions().install(new CodeLineEditingExtension()));
        assertEquals(1, editor.getCommands().size());
        assertNull(editor.getCommands().getCommand(MOVE_UP));
        assertEquals("Host", editor.getCommands().getCommand(DUPLICATE_DOWN).label);
    }
}
