package com.lzt841.editor.extensions;

import com.badlogic.gdx.Input;
import com.lzt841.editor.CodeEditor;
import com.lzt841.editor.CodeEditorTextRange;
import com.lzt841.editor.CodeKeyStroke;
import org.junit.Before;
import org.junit.Test;

import static com.lzt841.editor.EditorTestSupport.createEditor;
import static com.lzt841.editor.extensions.CodeWordEditingExtension.*;
import static org.junit.Assert.*;

public class CodeWordEditingExtensionTest {
    private CodeEditor editor;

    @Before public void setup() {
        editor = createEditor("alpha beta");
        editor.getExtensions().install(new CodeWordEditingExtension());
    }

    @Test public void navigationUsesIdentifierRunsAndSkipsWhitespaceAndPunctuation() {
        editor.setText("one_two$名  .! next\n第二");
        editor.setCursorPosition(0, 0);

        assertTrue(editor.getCommands().execute(MOVE_WORD_RIGHT));
        assertEquals(11, editor.getCursorColumn());
        assertTrue(editor.getCommands().execute(MOVE_WORD_RIGHT));
        assertEquals(14, editor.getCursorColumn());
        assertTrue(editor.getCommands().execute(MOVE_WORD_RIGHT));
        assertEquals(0, editor.getCursorColumn());
        assertEquals(1, editor.getCursorLine());
        assertTrue(editor.getCommands().execute(MOVE_WORD_RIGHT));
        assertEquals(editor.getLineLength(1), editor.getCursorColumn());
        assertFalse(editor.getCommands().execute(MOVE_WORD_RIGHT));

        assertTrue(editor.getCommands().execute(MOVE_WORD_LEFT));
        assertEquals(1, editor.getCursorLine());
        assertEquals(0, editor.getCursorColumn());
        assertTrue(editor.getCommands().execute(MOVE_WORD_LEFT));
        assertEquals(0, editor.getCursorLine());
        assertEquals(14, editor.getCursorColumn());
        assertTrue(editor.getCommands().execute(MOVE_WORD_LEFT));
        assertEquals(11, editor.getCursorColumn());
        assertTrue(editor.getCommands().execute(MOVE_WORD_LEFT));
        assertEquals(0, editor.getCursorColumn());
    }

    @Test public void shiftCommandsExtendAndPreserveSelectionDirection() {
        editor.setText("alpha beta gamma");
        editor.setCursorPosition(0, 0);
        assertTrue(editor.getCommands().execute(SELECT_WORD_RIGHT));
        assertEquals(new CodeEditorTextRange(0, 0, 0, 6), editor.getSelection());
        assertEquals("alpha ", editor.getSelectionText());

        // The active end is the caret, so extending left contracts from that end.
        assertTrue(editor.getCommands().execute(SELECT_WORD_LEFT));
        assertNull(editor.getSelection());
        assertEquals(0, editor.getCursorColumn());

        // A reverse selection retains its anchor when extending farther left.
        editor.setSelection(0, 11, 0, 6);
        assertTrue(editor.getCommands().execute(SELECT_WORD_LEFT));
        assertEquals(new CodeEditorTextRange(0, 0, 0, 11), editor.getSelection());
        assertEquals(0, editor.getCursorLine());
        assertEquals(0, editor.getCursorColumn());
    }

    @Test public void unicodeAndCrossLineDeletionIsOneUndoableEdit() {
        editor.setText("alpha\n\ud801\udc28 beta  ");
        editor.setCursorPosition(1, editor.getLineLength(1));
        int[] events = {0};
        editor.addContentListener((source, event) -> events[0]++);

        assertTrue(editor.getCommands().execute(DELETE_PREVIOUS_WORD));
        assertEquals("alpha\n\ud801\udc28 ", editor.getText());
        assertEquals(1, editor.getUndoEntryCount());
        assertEquals("Delete Previous Word", editor.getUndoLabel());
        assertEquals(1, events[0]);
        assertTrue(editor.undo());
        assertEquals("alpha\n\ud801\udc28 beta  ", editor.getText());
        assertTrue(editor.redo());
        assertEquals("alpha\n\ud801\udc28 ", editor.getText());
    }

    @Test public void forwardDeleteSpansLogicalLinesAndSelectionDeletesExactlyOnce() {
        editor.setText("中文 foo\nbar");
        editor.setCursorPosition(0, 0);
        assertTrue(editor.getCommands().execute(DELETE_NEXT_WORD));
        assertEquals("foo\nbar", editor.getText());
        assertEquals("Delete Next Word", editor.getUndoLabel());

        editor.setText("first selected last");
        editor.setSelection(0, 6, 0, 14);
        assertTrue(editor.getCommands().execute(DELETE_PREVIOUS_WORD));
        assertEquals("first  last", editor.getText());
        assertNull(editor.getSelection());
        assertEquals(1, editor.getUndoEntryCount());
    }

    @Test public void selectionCollapsesByDirectionAndBoundaryNoOpsPreserveHistory() {
        editor.setText("alpha beta");
        editor.setSelection(0, 1, 0, 4);
        assertTrue(editor.getCommands().execute(MOVE_WORD_LEFT));
        assertEquals(1, editor.getCursorColumn());
        assertNull(editor.getSelection());

        editor.setCursorPosition(0, 0);
        int version = editor.getDocumentVersion();
        assertFalse(editor.getCommands().execute(MOVE_WORD_LEFT));
        assertFalse(editor.getCommands().execute(DELETE_PREVIOUS_WORD));
        assertEquals(version, editor.getDocumentVersion());
        assertEquals(0, editor.getUndoEntryCount());

        editor.setSelection(0, 1, 0, 4);
        assertTrue(editor.getCommands().execute(MOVE_WORD_RIGHT));
        assertEquals(4, editor.getCursorColumn());
        assertNull(editor.getSelection());
    }

    @Test public void profilesAreOptionalAndUninstallRemovesEveryShortcut() {
        editor.setCursorPosition(0, 10);
        assertTrue(editor.getCommands().executeShortcut(CodeKeyStroke.ctrl(Input.Keys.LEFT)));
        assertTrue(editor.getCommands().executeShortcut(CodeKeyStroke.ctrlShift(Input.Keys.RIGHT)));
        assertTrue(editor.getCommands().executeShortcut(CodeKeyStroke.ctrl(Input.Keys.BACKSPACE)));

        editor.getExtensions().uninstall(ID);
        assertEquals(0, editor.getCommands().size());
        assertFalse(editor.getCommands().executeShortcut(CodeKeyStroke.ctrl(Input.Keys.LEFT)));

        editor.getExtensions().install(CodeWordEditingExtension.macOSDefaults());
        editor.setCursorPosition(0, editor.getLineLength(0));
        assertTrue(editor.getCommands().executeShortcut(CodeKeyStroke.alt(Input.Keys.LEFT)));
        assertTrue(editor.getCommands().executeShortcut(new CodeKeyStroke(Input.Keys.RIGHT, false, true, true)));
        assertFalse(editor.getCommands().executeShortcut(CodeKeyStroke.ctrl(Input.Keys.LEFT)));
        editor.getExtensions().uninstall(ID);

        editor.getExtensions().install(new CodeWordEditingExtension(ShortcutProfile.NONE));
        assertEquals(6, editor.getCommands().size());
        assertFalse(editor.getCommands().executeShortcut(CodeKeyStroke.ctrl(Input.Keys.LEFT)));
        assertTrue(editor.getCommands().execute(MOVE_WORD_LEFT));
    }

    @Test public void editingCommandsRespectReadOnlyDisabledAndPasswordModes() {
        editor.setText("alpha beta");
        editor.setCursorPosition(0, 10);
        editor.setReadOnly(true);
        assertTrue(editor.getCommands().execute(MOVE_WORD_LEFT));
        assertEquals(6, editor.getCursorColumn());
        assertFalse(editor.getCommands().execute(DELETE_PREVIOUS_WORD));
        assertEquals("alpha beta", editor.getText());

        editor.setReadOnly(false);
        editor.setPasswordMode(true);
        assertFalse(editor.getCommands().isEnabled(MOVE_WORD_LEFT));
        assertFalse(editor.getCommands().execute(DELETE_PREVIOUS_WORD));
        assertEquals("alpha beta", editor.getText());

        editor.setPasswordMode(false);
        editor.setDisabled(true);
        assertFalse(editor.getCommands().execute(MOVE_WORD_LEFT));
        assertFalse(editor.getCommands().execute(DELETE_PREVIOUS_WORD));
    }
}
