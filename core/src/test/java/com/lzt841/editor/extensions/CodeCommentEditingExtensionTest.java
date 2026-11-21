package com.lzt841.editor.extensions;

import com.badlogic.gdx.Input;
import com.lzt841.editor.CodeEditor;
import com.lzt841.editor.CodeEditorTextRange;
import com.lzt841.editor.CodeKeyStroke;
import com.lzt841.editor.CodeKeymap;
import org.junit.Before;
import org.junit.Test;

import static com.lzt841.editor.EditorTestSupport.*;
import static com.lzt841.editor.extensions.CodeCommentEditingExtension.*;
import static org.junit.Assert.*;

public class CodeCommentEditingExtensionTest {
    private CodeEditor editor;
    private CodeCommentEditingExtension extension;

    @Before public void setup() {
        editor = createEditor("  alpha\n\tbeta\ngamma");
        extension = new CodeCommentEditingExtension();
        editor.getExtensions().install(extension);
    }

    private boolean toggle() { return editor.getCommands().execute(TOGGLE_LINE_COMMENT); }

    @Test public void currentLineRoundTripsWithOneNamedUndoAndTheCaretFollowingItsText() {
        editor.setCursorPosition(0, 4);
        int[] events = {0};
        editor.addContentListener((source, event) -> {
            events[0]++;
            if (events[0] == 1) assertEquals(7, source.getCursorColumn());
        });
        assertTrue(toggle());
        assertEquals("  // alpha\n\tbeta\ngamma", editor.getText());
        assertEquals(7, editor.getCursorColumn());
        assertEquals(1, events[0]);
        assertEquals("Toggle Line Comment", editor.getUndoLabel());
        assertEquals(1, editor.getUndoEntryCount());
        assertTrue(editor.undo());
        assertEquals("  alpha\n\tbeta\ngamma", editor.getText());
        assertEquals(4, editor.getCursorColumn());
        assertTrue(editor.redo());
        assertEquals(7, editor.getCursorColumn());
        assertTrue(toggle());
        assertEquals("  alpha\n\tbeta\ngamma", editor.getText());
        assertEquals(4, editor.getCursorColumn());
    }

    @Test public void selectionEndingOnTheNextLineStartExcludesThatLine() {
        editor.setSelection(0, 0, 2, 0);
        assertTrue(toggle());
        assertEquals("  // alpha\n\t// beta\ngamma", editor.getText());
        assertEquals(new CodeEditorTextRange(0, 0, 2, 0), editor.getSelection());
        assertEquals(2, editor.getCursorLine());
        assertTrue(toggle());
        assertEquals("  alpha\n\tbeta\ngamma", editor.getText());
    }

    @Test public void reversedSelectionKeepsDirectionAndMappedEndpoints() {
        editor.setSelection(1, 3, 0, 3);
        assertTrue(toggle());
        assertEquals(new CodeEditorTextRange(0, 6, 1, 6), editor.getSelection());
        assertEquals(0, editor.getCursorLine());
        assertEquals(6, editor.getCursorColumn());
        assertTrue(toggle());
        assertEquals(new CodeEditorTextRange(0, 3, 1, 3), editor.getSelection());
        assertEquals(3, editor.getCursorColumn());
    }

    @Test public void mixedCommentsGainOnePrefixAndRoundTripWithoutLosingExistingComments() {
        editor.setText("// alpha\nbeta\n\n  \n\t// gamma");
        editor.selectAllText();
        String original = editor.getText();
        assertTrue(toggle());
        assertEquals("// // alpha\n// beta\n\n  \n\t// // gamma", editor.getText());
        assertTrue(toggle());
        assertEquals(original, editor.getText());
    }

    @Test public void uncommentRemovesOnlyThePrefixAndOneAsciiSpace() {
        editor.setText("  //alpha\n\t//  beta\n//\tgamma\n//");
        editor.selectAllText();
        assertTrue(toggle());
        assertEquals("  alpha\n\t beta\n\tgamma\n", editor.getText());
        assertEquals(1, editor.getUndoEntryCount());
    }

    @Test public void selectedBlankLinesAreANoOpWithHistoryAndSelectionIntact() {
        editor.setText(" \n\t\n");
        editor.selectAllText();
        CodeEditorTextRange selection = editor.getSelection();
        int version = editor.getDocumentVersion();
        assertFalse(toggle());
        assertEquals(" \n\t\n", editor.getText());
        assertEquals(selection, editor.getSelection());
        assertEquals(version, editor.getDocumentVersion());
        assertEquals(0, editor.getUndoEntryCount());
    }

    @Test public void caretOnAnEmptyOrWhitespaceLineCanStartAComment() {
        editor.setText("");
        assertTrue(toggle());
        assertEquals("// ", editor.getText());
        assertEquals(3, editor.getCursorColumn());
        assertTrue(toggle());
        assertEquals("", editor.getText());
        assertEquals(0, editor.getCursorColumn());
        editor.setText("  ");
        editor.setCursorPosition(0, 2);
        assertTrue(toggle());
        assertEquals("  // ", editor.getText());
        assertEquals(5, editor.getCursorColumn());
    }

    @Test public void cursorInsideRemovedPrefixClampsAtThePreservedIndent() {
        editor.setText("  // alpha");
        editor.setCursorPosition(0, 3);
        assertTrue(toggle());
        assertEquals("  alpha", editor.getText());
        assertEquals(2, editor.getCursorColumn());
    }

    @Test public void languagePrefixChangesAndUnsupportedLanguagesAreEvaluatedAtExecutionTime() {
        extension.setLineCommentPrefix("#");
        assertTrue(toggle());
        assertEquals("  # alpha\n\tbeta\ngamma", editor.getText());
        assertTrue(toggle());
        extension.setLineCommentPrefix("--");
        assertTrue(toggle());
        assertEquals("  -- alpha\n\tbeta\ngamma", editor.getText());
        extension.setLineCommentPrefix(null);
        int version = editor.getDocumentVersion();
        assertFalse(editor.getCommands().isEnabled(TOGGLE_LINE_COMMENT));
        assertFalse(toggle());
        assertEquals(version, editor.getDocumentVersion());
        assertNull(extension.getLineCommentPrefix());
        extension.setLineCommentPrefix("--");
        assertTrue(toggle());
        assertEquals("  alpha\n\tbeta\ngamma", editor.getText());
    }

    @Test public void rejectedPrefixRejectsTheEntireSelectionAndPreservesRedo() {
        assertTrue(editor.insertTextAtCursor("!"));
        assertTrue(editor.undo());
        editor.selectAllText();
        CodeEditorTextRange selection = editor.getSelection();
        int version = editor.getDocumentVersion();
        int[] calls = {0};
        editor.setInputFilter((source, character) -> ++calls[0] <= 4);
        assertFalse(toggle());
        assertEquals("  alpha\n\tbeta\ngamma", editor.getText());
        assertEquals(selection, editor.getSelection());
        assertEquals(version, editor.getDocumentVersion());
        assertEquals(1, editor.getRedoEntryCount());
    }

    @Test public void filtersOnlySeeInsertedPrefixesAndDoNotRewriteExistingUnicode() {
        editor.setText("\uD83D\uDE42中文\ne\u0301");
        editor.selectAllText();
        int[] calls = {0};
        editor.setInputFilter((source, character) -> { calls[0]++; return character == '/' || character == ' '; });
        assertTrue(toggle());
        assertEquals("// \uD83D\uDE42中文\n// e\u0301", editor.getText());
        assertEquals(6, calls[0]);
        assertTrue(toggle());
        assertEquals("\uD83D\uDE42中文\ne\u0301", editor.getText());
        assertEquals(6, calls[0]);
    }

    @Test public void readOnlyDisabledAndPasswordModesDisableTheCommand() {
        int version = editor.getDocumentVersion();
        editor.setReadOnly(true);
        assertFalse(toggle());
        editor.setReadOnly(false);
        editor.setDisabled(true);
        assertFalse(toggle());
        editor.setDisabled(false);
        editor.setPasswordMode(true);
        assertFalse(toggle());
        editor.setPasswordMode(false);
        assertEquals(version, editor.getDocumentVersion());
        assertEquals(0, editor.getUndoEntryCount());
    }

    @Test public void shortcutsAreExactAndUninstallReleasesTheCommandAndItsBinding() {
        assertEquals(1, editor.getCommands().getShortcuts(TOGGLE_LINE_COMMENT).size);
        assertFalse(editor.getCommands().executeShortcut(CodeKeyStroke.ctrlShift(Input.Keys.SLASH)));
        assertTrue(editor.getCommands().executeShortcut(CodeKeyStroke.ctrl(Input.Keys.SLASH)));
        editor.getExtensions().uninstall(ID);
        assertNull(editor.getCommands().getCommand(TOGGLE_LINE_COMMENT));
        assertFalse(editor.getCommands().executeShortcut(CodeKeyStroke.ctrl(Input.Keys.SLASH)));
        editor.getExtensions().install(new CodeCommentEditingExtension("#", false));
        assertEquals(0, editor.getCommands().getShortcuts(TOGGLE_LINE_COMMENT).size);
        assertTrue(toggle());
    }

    @Test public void rejectedShortcutDoesNotInsertASlashOrLoseTheSelection() {
        editor.setKeymap(new CodeKeymap() {
            @Override public boolean controlPressed() { return true; }
        });
        editor.selectAllText();
        editor.setInputFilter((source, character) -> false);
        CodeEditorTextRange selection = editor.getSelection();
        assertTrue(keyDown(editor, Input.Keys.SLASH));
        // Ctrl-chord character events are ignored before normal character dispatch.
        assertFalse(keyTyped(editor, '/'));
        keyUp(editor, Input.Keys.SLASH);
        assertEquals(selection, editor.getSelection());
        assertEquals("  alpha\n\tbeta\ngamma", editor.getText());
    }

    @Test public void prefixValidationDoesNotChangeThePreviousConfiguration() {
        for (String invalid : new String[] {"", "// ", " #", "a\nb", "\t", "\u0000", "\u00A0"}) {
            assertThrows(IllegalArgumentException.class, () -> extension.setLineCommentPrefix(invalid));
            assertEquals("//", extension.getLineCommentPrefix());
        }
    }
}
