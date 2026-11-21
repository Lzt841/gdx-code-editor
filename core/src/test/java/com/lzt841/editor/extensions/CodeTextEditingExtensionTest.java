package com.lzt841.editor.extensions;

import com.badlogic.gdx.Input;
import com.lzt841.editor.CodeEditor;
import com.lzt841.editor.CodeEditorCommand;
import com.lzt841.editor.CodeEditorTextRange;
import com.lzt841.editor.CodeKeyStroke;
import org.junit.Before;
import org.junit.Test;

import java.util.Locale;

import static com.lzt841.editor.EditorTestSupport.createEditor;
import static com.lzt841.editor.extensions.CodeTextEditingExtension.*;
import static org.junit.Assert.*;

public class CodeTextEditingExtensionTest {
    private CodeEditor editor;

    @Before public void setup() {
        editor = createEditor("abc");
        editor.getExtensions().install(new CodeTextEditingExtension());
    }

    @Test
    public void unicodeExpansionPreservesSelectionAndUsesOneNamedUndoStep() {
        editor.setText("xstra\u00dfe!");
        editor.setSelection(0, 1, 0, 7);
        int[] events = {0};
        editor.addContentListener((source, event) -> events[0]++);
        assertTrue(editor.getCommands().execute(UPPERCASE_SELECTION));
        assertEquals("xSTRASSE!", editor.getText());
        assertEquals(new CodeEditorTextRange(0, 1, 0, 8), editor.getSelection());
        assertEquals("STRASSE", editor.getSelectionText());
        assertEquals(1, editor.getUndoEntryCount());
        assertEquals("Uppercase Selection", editor.getUndoLabel());
        assertEquals(1, events[0]);
        assertTrue(editor.undo());
        assertEquals("xstra\u00dfe!", editor.getText());
        assertFalse(editor.canUndo());
        assertTrue(editor.redo());
        assertEquals("xSTRASSE!", editor.getText());
    }

    @Test
    public void multilineConversionHandlesSupplementaryLetters() {
        editor.setText("pre stra\u00dfe\n\ud801\udc28x end");
        editor.setSelection(0, 4, 1, 3);
        assertTrue(editor.getCommands().execute(UPPERCASE_SELECTION));
        assertEquals("pre STRASSE\n\ud801\udc00X end", editor.getText());
        assertEquals(new CodeEditorTextRange(0, 4, 1, 3), editor.getSelection());
        assertTrue(editor.getCommands().execute(LOWERCASE_SELECTION));
        assertEquals("pre strasse\n\ud801\udc28x end", editor.getText());
        assertEquals("Lowercase Selection", editor.getUndoLabel());
        assertEquals(2, editor.getUndoEntryCount());
    }

    @Test
    public void caseMappingDoesNotDependOnSystemLocale() {
        Locale previous = Locale.getDefault();
        try {
            Locale.setDefault(new Locale("tr", "TR"));
            editor.setText("iI");
            editor.setSelection(0, 0, 0, 2);
            assertTrue(editor.getCommands().execute(UPPERCASE_SELECTION));
            assertEquals("II", editor.getSelectionText());
            assertTrue(editor.getCommands().execute(LOWERCASE_SELECTION));
            assertEquals("ii", editor.getSelectionText());
        } finally {
            Locale.setDefault(previous);
        }
    }

    @Test
    public void filteredReplacementUsesActualSelectionEndpoint() {
        editor.setText("xab!");
        editor.setSelection(0, 1, 0, 3);
        editor.setInputFilter((source, character) -> character != 'B');
        assertTrue(editor.getCommands().execute(UPPERCASE_SELECTION));
        assertEquals("xA!", editor.getText());
        assertEquals(new CodeEditorTextRange(0, 1, 0, 2), editor.getSelection());
        assertEquals("A", editor.getSelectionText());
        assertTrue(editor.undo());
        assertEquals("xab!", editor.getText());
    }

    @Test
    public void trimRemovesOnlyTrailingAsciiSpacesAndTabsAsOneUndoStep() {
        String original = "  alpha \t\n\tbeta\t \n \t\n\u00a0 \t\n";
        editor.setText(original);
        editor.setCursorPosition(1, 3);
        assertTrue(editor.getCommands().execute(TRIM_TRAILING_WHITESPACE));
        assertEquals("  alpha\n\tbeta\n\n\u00a0\n", editor.getText());
        assertEquals(5, editor.getLineCount());
        assertEquals(1, editor.getCursorLine());
        assertEquals(3, editor.getCursorColumn());
        assertEquals(1, editor.getUndoEntryCount());
        assertEquals("Trim Trailing Whitespace", editor.getUndoLabel());
        assertTrue(editor.undo());
        assertEquals(original, editor.getText());
    }

    @Test
    public void unchangedCommandsPreserveSelectionHistoryAndVersion() {
        editor.setText("ABC");
        editor.setSelection(0, 0, 0, 3);
        int version = editor.getDocumentVersion();
        int[] events = {0};
        editor.addContentListener((source, event) -> events[0]++);
        assertFalse(editor.getCommands().execute(UPPERCASE_SELECTION));
        assertFalse(editor.getCommands().execute(TRIM_TRAILING_WHITESPACE));
        assertEquals(new CodeEditorTextRange(0, 0, 0, 3), editor.getSelection());
        assertEquals(version, editor.getDocumentVersion());
        assertEquals(0, editor.getUndoEntryCount());
        assertEquals(0, events[0]);
    }

    @Test
    public void commandsRespectEditorModesAndUninstallRemovesShortcuts() {
        editor.setText("abc \t");
        assertFalse(editor.getCommands().execute(UPPERCASE_SELECTION));
        editor.setSelection(0, 0, 0, 3);
        editor.setReadOnly(true);
        assertFalse(editor.getCommands().execute(UPPERCASE_SELECTION));
        assertFalse(editor.getCommands().execute(TRIM_TRAILING_WHITESPACE));
        editor.setReadOnly(false);
        editor.setPasswordMode(true);
        assertFalse(editor.getCommands().execute(LOWERCASE_SELECTION));
        assertFalse(editor.getCommands().execute(TRIM_TRAILING_WHITESPACE));
        editor.setPasswordMode(false);
        editor.setDisabled(true);
        assertFalse(editor.getCommands().execute(UPPERCASE_SELECTION));
        editor.setDisabled(false);
        assertTrue(editor.getCommands().isEnabled(UPPERCASE_SELECTION));
        editor.getExtensions().uninstall(ID);
        assertEquals(0, editor.getCommands().size());
        assertFalse(editor.getCommands().executeShortcut(CodeKeyStroke.ctrlShift(Input.Keys.U)));
        assertFalse(editor.getCommands().execute(TRIM_TRAILING_WHITESPACE));
        assertEquals("abc \t", editor.getText());
        editor.getExtensions().install(new CodeTextEditingExtension());
        assertEquals(3, editor.getCommands().size());
    }

    @Test
    public void conflictingCommandRollsBackEarlierTextCommandRegistrations() {
        editor.getExtensions().uninstall(ID);
        editor.getCommands().register(new CodeEditorCommand(LOWERCASE_SELECTION, "Host command", source -> true));
        assertThrows(IllegalArgumentException.class, () -> editor.getExtensions().install(new CodeTextEditingExtension()));
        assertFalse(editor.getExtensions().isInstalled(ID));
        assertNull(editor.getCommands().getCommand(UPPERCASE_SELECTION));
        assertNull(editor.getCommands().getCommand(TRIM_TRAILING_WHITESPACE));
        assertEquals(1, editor.getCommands().size());
        assertEquals("Host command", editor.getCommands().getCommand(LOWERCASE_SELECTION).label);
    }
}
