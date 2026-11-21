package com.lzt841.editor.extensions;

import com.badlogic.gdx.Input;
import com.badlogic.gdx.utils.Array;
import com.lzt841.editor.CodeCommandRegistry;
import com.lzt841.editor.CodeDiagnostic;
import com.lzt841.editor.CodeDiagnosticSeverity;
import com.lzt841.editor.CodeEditor;
import com.lzt841.editor.CodeEditorAction;
import com.lzt841.editor.CodeEditorCommand;
import com.lzt841.editor.CodeEditorTextRange;
import com.lzt841.editor.CodeKeyStroke;
import org.junit.Before;
import org.junit.Test;

import static com.lzt841.editor.EditorTestSupport.createEditor;
import static com.lzt841.editor.extensions.CodeStandardCommandsExtension.*;
import static org.junit.Assert.*;

public class CodeStandardCommandsExtensionTest {
    private CodeEditor editor;
    private CodeCommandRegistry commands;

    @Before public void setup() {
        editor = createEditor("alpha\nbeta");
        editor.getExtensions().install(new CodeStandardCommandsExtension());
        commands = editor.getCommands();
    }

    @Test public void exposesTenActionsWithoutReplacingNativeBindings() {
        assertEquals(10, commands.size());
        assertEquals(CodeEditorAction.UNDO, editor.getKeymap().actionFor(CodeKeyStroke.ctrl(Input.Keys.Z)));
        assertEquals(0, commands.getShortcuts(UNDO).size);
        assertEquals(2, commands.findCommands("view toggle", false).size);
    }

    @Test public void historyAvailabilityTracksActualUndoAndRedo() {
        assertFalse(commands.execute(UNDO));
        assertFalse(commands.execute(REDO));
        editor.insertTextAtCursor("X");
        assertTrue(commands.isEnabled(UNDO));
        assertTrue(commands.execute(UNDO));
        assertEquals("alpha\nbeta", editor.getText());
        assertFalse(commands.isEnabled(UNDO));
        assertTrue(commands.isEnabled(REDO));
        assertTrue(commands.execute(REDO));
        assertEquals("Xalpha\nbeta", editor.getText());
    }

    @Test public void selectionAndReadOnlyRestrictionsAreCommandSpecific() {
        assertFalse(commands.isEnabled(COPY));
        assertFalse(commands.isEnabled(CUT));
        editor.setReadOnly(true);
        assertTrue(commands.execute(SELECT_ALL));
        assertEquals(new CodeEditorTextRange(0, 0, 1, 4), editor.getSelection());
        assertTrue(commands.isEnabled(COPY));
        assertFalse(commands.isEnabled(CUT));
        assertFalse(commands.isEnabled(PASTE));
        assertFalse(commands.isEnabled(UNDO));
        boolean wrapped = editor.isWrapEnabled();
        assertTrue(commands.execute(TOGGLE_WRAP));
        assertEquals(!wrapped, editor.isWrapEnabled());
        editor.setDisabled(true);
        for (CodeEditorCommand command : commands.getCommands()) assertFalse(commands.isEnabled(command.id));
    }

    @Test public void passwordModeBlocksContentActionsButAllowsViewConfiguration() {
        editor.selectAllText();
        editor.insertTextAtCursor("X");
        editor.selectAllText();
        editor.setPasswordMode(true);
        for (String id : new String[] {UNDO, REDO, SELECT_ALL, COPY, CUT, PASTE, NEXT_DIAGNOSTIC, PREVIOUS_DIAGNOSTIC}) {
            assertFalse(id, commands.isEnabled(id));
        }
        boolean visible = editor.isLineNumbersVisible();
        assertTrue(commands.execute(TOGGLE_LINE_NUMBERS));
        assertEquals(!visible, editor.isLineNumbersVisible());
        assertEquals("X", editor.getText());
    }

    @Test public void diagnosticCommandsRejectStaleAnalysisAndDoNotCreateUndoEntries() {
        assertFalse(commands.isEnabled(NEXT_DIAGNOSTIC));
        editor.setDiagnostics(Array.with(new CodeDiagnostic(1, 0, 1, 2, CodeDiagnosticSeverity.WARNING, "demo")));
        assertTrue(commands.execute(NEXT_DIAGNOSTIC));
        assertEquals(1, editor.getCursorLine());
        assertEquals(0, editor.getUndoEntryCount());
        assertTrue(commands.execute(PREVIOUS_DIAGNOSTIC));
        editor.insertTextAtCursor("X");
        assertFalse(commands.isEnabled(NEXT_DIAGNOSTIC));
        assertFalse(commands.execute(PREVIOUS_DIAGNOSTIC));
    }

    @Test public void unloadingRemovesReboundShortcutsAndRetainsHostCommands() {
        commands.register(new CodeEditorCommand("host.run", "Host", source -> true));
        commands.setShortcuts(TOGGLE_WRAP, CodeKeyStroke.of(Input.Keys.F6));
        editor.getExtensions().uninstall(ID);
        assertEquals(1, commands.size());
        assertFalse(commands.executeShortcut(CodeKeyStroke.of(Input.Keys.F6)));
        assertTrue(commands.execute("host.run"));
    }

    @Test public void installationConflictRollsBackOnlyNewCommands() {
        editor.getExtensions().uninstall(ID);
        commands.register(new CodeEditorCommand(COPY, "Host Copy", source -> true));
        assertThrows(IllegalArgumentException.class,
            () -> editor.getExtensions().install(new CodeStandardCommandsExtension()));
        assertEquals(1, commands.size());
        assertEquals("Host Copy", commands.getCommand(COPY).label);
        assertFalse(editor.getExtensions().isInstalled(ID));
    }
}
