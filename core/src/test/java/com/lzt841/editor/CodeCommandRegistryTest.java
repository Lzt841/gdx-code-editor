package com.lzt841.editor;

import com.badlogic.gdx.Input;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Disposable;
import org.junit.Before;
import org.junit.Test;

import static com.lzt841.editor.EditorTestSupport.*;
import static org.junit.Assert.*;

public class CodeCommandRegistryTest {
    private CodeEditor editor;
    private CodeCommandRegistry commands;
    private int executions;

    @Before
    public void createRegistry() {
        editor = createEditor("abc");
        commands = editor.getCommands();
        executions = 0;
    }

    private CodeEditorCommand command(String id) {
        return new CodeEditorCommand(id, id, source -> { executions++; return true; });
    }

    @Test
    public void editorSharesOneLazyRegistryWithItsExtensions() {
        assertSame(commands, editor.getCommands());
        assertSame(commands, editor.getExtensions().getCommands());
        assertNotSame(commands, createEditor("").getCommands());
    }

    @Test
    public void rejectsDuplicateIdsAndShortcutsWithoutPartialRegistration() {
        commands.register(command("first"), CodeKeyStroke.ctrl(Input.Keys.K));
        assertThrows(IllegalArgumentException.class,
            () -> commands.register(command("first"), CodeKeyStroke.of(Input.Keys.F6)));
        assertThrows(IllegalArgumentException.class, () -> commands.register(command("second"),
            CodeKeyStroke.of(Input.Keys.F6), CodeKeyStroke.ctrl(Input.Keys.K)));
        assertNull(commands.getCommand("second"));
        assertEquals(1, commands.size());
        commands.register(command("second"), CodeKeyStroke.of(Input.Keys.F6));
        assertTrue(commands.executeShortcut(CodeKeyStroke.of(Input.Keys.F6)));
        assertEquals(1, executions);
    }

    @Test
    public void validatesEveryShortcutBeforeRegistering() {
        CodeKeyStroke key = CodeKeyStroke.of(Input.Keys.F6);
        assertThrows(IllegalArgumentException.class, () -> commands.register(command("duplicate"), key, key));
        assertThrows(IllegalArgumentException.class, () -> commands.register(command("invalid"), key,
            CodeKeyStroke.of(512)));
        assertThrows(IllegalArgumentException.class, () -> commands.register(command("null"), key, null));
        assertEquals(0, commands.size());
        commands.register(command("valid"), key);
        assertTrue(commands.executeShortcut(key));
        assertFalse(commands.executeShortcut(CodeKeyStroke.of(Input.Keys.F6 + 512)));
        assertFalse(commands.executeShortcut(CodeKeyStroke.of(-1)));
        assertFalse(commands.handleKeyDown(512));
        assertFalse(commands.executeShortcut(null));
        assertEquals(1, executions);
    }

    @Test
    public void shortcutsRequireExactModifiersAndUseCurrentEditorKeymap() {
        commands.register(command("run"), CodeKeyStroke.ctrl(Input.Keys.K));
        assertFalse(commands.executeShortcut(CodeKeyStroke.of(Input.Keys.K)));
        assertFalse(commands.executeShortcut(CodeKeyStroke.ctrlShift(Input.Keys.K)));
        assertFalse(commands.executeShortcut(CodeKeyStroke.ctrlAlt(Input.Keys.K)));
        assertTrue(commands.executeShortcut(CodeKeyStroke.ctrl(Input.Keys.K)));
        CodeKeymap heldControl = new CodeKeymap() {
            @Override public boolean controlPressed() { return true; }
            @Override public boolean shiftPressed() { return false; }
            @Override public boolean altPressed() { return false; }
        };
        editor.setKeymap(heldControl);
        assertTrue(keyDown(editor, Input.Keys.K));
        assertEquals(2, executions);
        editor.setKeymap(new CodeKeymap());
        assertFalse(commands.handleKeyDown(Input.Keys.K));
    }

    @Test
    public void evaluatesEditableAndSelectionConditionsAtExecutionTime() {
        commands.register(new CodeEditorCommand("edit", "Edit", "Text", true,
            CodeEditor::hasSelection, source -> { executions++; return true; }));
        commands.register(command("view"));
        assertFalse(commands.isEnabled("edit"));
        assertFalse(commands.execute("edit"));
        editor.setSelection(0, 0, 0, 2);
        assertTrue(commands.isEnabled("edit"));
        assertTrue(commands.execute("edit"));
        editor.setReadOnly(true);
        assertFalse(commands.execute("edit"));
        assertTrue(commands.execute("view"));
        editor.setDisabled(true);
        assertFalse(commands.execute("view"));
        assertFalse(commands.isEnabled("view"));
        assertEquals(2, executions);
        assertFalse(commands.execute("missing"));
        assertFalse(commands.execute(null));
    }

    @Test
    public void oldRegistrationCannotRemoveReusedIdOrShortcut() {
        Disposable old = commands.register(command("run"), CodeKeyStroke.of(Input.Keys.F6));
        commands.clear();
        commands.register(command("run"), CodeKeyStroke.of(Input.Keys.F6));
        old.dispose();
        old.dispose();
        assertTrue(commands.executeShortcut(CodeKeyStroke.of(Input.Keys.F6)));
        assertEquals(1, commands.size());
    }

    @Test
    public void conditionThatUnregistersItselfCannotExecuteStaleHandler() {
        Disposable[] registration = new Disposable[1];
        registration[0] = commands.register(new CodeEditorCommand("run", "Run", "", false,
            source -> { registration[0].dispose(); return true; },
            source -> { executions++; return true; }));
        assertFalse(commands.execute("run"));
        assertEquals(0, executions);
        assertEquals(0, commands.size());
    }

    @Test
    public void exposesOrderedSearchableMetadataAndIndependentSnapshots() {
        commands.register(new CodeEditorCommand("text.upper", "Uppercase Selection", "Transform", true,
            CodeEditor::hasSelection, source -> true), CodeKeyStroke.ctrlShift(Input.Keys.U));
        commands.register(command("host.view"));
        Array<CodeEditorCommand> snapshot = commands.getCommands();
        assertEquals("text.upper", snapshot.first().id);
        assertEquals("host.view", snapshot.peek().id);
        assertEquals(1, commands.findCommands("TRANSform upper", false).size);
        assertEquals(0, commands.findCommands("selection missing", false).size);
        assertEquals(0, commands.findCommands("upper", true).size);
        editor.setSelection(0, 0, 0, 1);
        assertEquals(1, commands.findCommands("upper", true).size);
        commands.getShortcuts("text.upper").clear();
        assertEquals(1, commands.getShortcuts("text.upper").size);
        commands.clear();
        assertEquals(2, snapshot.size);
        assertEquals(0, commands.getCommands().size);
        assertEquals(0, commands.getShortcuts(null).size);
    }

    @Test
    public void shortcutOverrideRestoresBuiltInActionWhenUnregistered() {
        Disposable handle = commands.register(command("override"), CodeKeyStroke.of(Input.Keys.LEFT));
        editor.setCursorPosition(0, 2);
        assertTrue(keyDown(editor, Input.Keys.LEFT));
        assertEquals(2, editor.getCursorColumn());
        handle.dispose();
        assertTrue(keyDown(editor, Input.Keys.LEFT));
        assertEquals(1, editor.getCursorColumn());
        assertEquals(1, executions);
    }

    @Test
    public void falseHandlerAllowsFollowingInputInterceptors() {
        commands.register(new CodeEditorCommand("pass", "Pass", source -> false), CodeKeyStroke.of(Input.Keys.F6));
        editor.addInputInterceptor(new CodeEditorInputInterceptor() {
            @Override public boolean onKeyDown(CodeEditor source, int key) { executions++; return true; }
        });
        assertTrue(keyDown(editor, Input.Keys.F6));
        assertEquals(1, executions);
    }

    @Test
    public void disposeRemovesKeyboardRoutingAndRejectsFurtherRegistration() {
        commands.register(command("run"), CodeKeyStroke.of(Input.Keys.F6));
        commands.dispose();
        commands.dispose();
        assertFalse(commands.execute("run"));
        assertFalse(keyDown(editor, Input.Keys.F6));
        assertEquals(0, commands.size());
        assertThrows(IllegalStateException.class, () -> commands.register(command("new")));
    }

    @Test
    public void validatesCommandMetadata() {
        assertThrows(IllegalArgumentException.class, () -> command(" "));
        assertThrows(IllegalArgumentException.class, () -> command(" bad"));
        assertThrows(IllegalArgumentException.class, () -> new CodeEditorCommand("id", "", source -> true));
        assertThrows(IllegalArgumentException.class, () -> new CodeEditorCommand("id", "Label", null));
    }

    @Test
    public void printableShortcutDoesNotAlsoInsertItsCharacterAndReleaseRestoresTyping() {
        editor.setText("");
        commands.register(command("run"), CodeKeyStroke.of(Input.Keys.K));
        assertTrue(keyDown(editor, Input.Keys.K));
        assertTrue(keyTyped(editor, 'k'));
        assertEquals("", editor.getText());
        keyUp(editor, Input.Keys.K);
        assertTrue(keyTyped(editor, 'x'));
        assertEquals("x", editor.getText());
        assertEquals(1, executions);
    }

    @Test
    public void nextKeyDownClearsCharacterSuppressionEvenBeforeOriginalKeyIsReleased() {
        editor.setText("");
        commands.register(command("run"), CodeKeyStroke.of(Input.Keys.K));
        assertTrue(keyDown(editor, Input.Keys.K));
        assertFalse(keyDown(editor, Input.Keys.J));
        assertTrue(keyTyped(editor, 'j'));
        assertEquals("j", editor.getText());
    }

    @Test
    public void unhandledCommandDoesNotSuppressCharacterInput() {
        editor.setText("");
        commands.register(new CodeEditorCommand("pass", "Pass", source -> false), CodeKeyStroke.of(Input.Keys.K));
        assertFalse(keyDown(editor, Input.Keys.K));
        assertTrue(keyTyped(editor, 'k'));
        assertEquals("k", editor.getText());
    }

    @Test
    public void reservedUnavailableShortcutSuppressesTypedTextButDirectExecutionReportsFalse() {
        editor.setText("");
        commands.register(new CodeEditorCommand("reserved", "Reserved", "", false,
            source -> false, source -> { fail("unavailable handler"); return true; }, true), CodeKeyStroke.of(Input.Keys.K));
        assertFalse(commands.execute("reserved"));
        assertFalse(commands.executeShortcut(CodeKeyStroke.of(Input.Keys.K)));
        assertTrue(keyDown(editor, Input.Keys.K));
        assertTrue(keyTyped(editor, 'k'));
        assertEquals("", editor.getText());
        keyUp(editor, Input.Keys.K);
        assertTrue(keyTyped(editor, 'x'));
        assertEquals("x", editor.getText());
    }

    @Test
    public void reservedNoOpShortcutDoesNotReachLaterInterceptorsAndUnregisterRestoresRouting() {
        Disposable handle = commands.register(new CodeEditorCommand("reserved", "Reserved", "", false,
            null, source -> false, true), CodeKeyStroke.of(Input.Keys.F6));
        editor.addInputInterceptor(new CodeEditorInputInterceptor() {
            @Override public boolean onKeyDown(CodeEditor source, int key) { executions++; return true; }
        });
        assertTrue(keyDown(editor, Input.Keys.F6));
        assertEquals(0, executions);
        handle.dispose();
        assertTrue(keyDown(editor, Input.Keys.F6));
        assertEquals(1, executions);
    }

    @Test
    public void releaseWhileDisabledClearsCharacterSuppression() {
        editor.setText("");
        commands.register(command("run"), CodeKeyStroke.of(Input.Keys.K));
        assertTrue(keyDown(editor, Input.Keys.K));
        editor.setDisabled(true);
        keyUp(editor, Input.Keys.K);
        editor.setDisabled(false);
        assertTrue(keyTyped(editor, 'x'));
        assertEquals("x", editor.getText());
    }

    @Test
    public void rebindReplacesAllChordsAndKeepsRegistrationOrderAndHandle() {
        Disposable handle = commands.register(command("first"), CodeKeyStroke.of(Input.Keys.F6));
        commands.register(command("second"));
        long revision = commands.getRevision();
        assertTrue(commands.setShortcuts("first", CodeKeyStroke.ctrl(Input.Keys.K), CodeKeyStroke.of(Input.Keys.F7)));
        assertTrue(commands.getRevision() > revision);
        assertFalse(commands.executeShortcut(CodeKeyStroke.of(Input.Keys.F6)));
        assertTrue(commands.executeShortcut(CodeKeyStroke.of(Input.Keys.F7)));
        assertTrue(commands.executeShortcut(CodeKeyStroke.ctrl(Input.Keys.K)));
        assertEquals("first", commands.getCommands().first().id);
        handle.dispose();
        assertFalse(commands.executeShortcut(CodeKeyStroke.of(Input.Keys.F7)));
        assertFalse(commands.executeShortcut(CodeKeyStroke.ctrl(Input.Keys.K)));
        assertEquals(1, commands.size());
    }

    @Test
    public void conflictingRebindLeavesEveryOldAndNewBindingUntouched() {
        commands.register(command("one"), CodeKeyStroke.of(Input.Keys.F6));
        commands.register(command("two"), CodeKeyStroke.of(Input.Keys.F7));
        long revision = commands.getRevision();
        assertThrows(IllegalArgumentException.class, () -> commands.setShortcuts("one",
            CodeKeyStroke.of(Input.Keys.F9), CodeKeyStroke.of(Input.Keys.F7)));
        assertEquals(revision, commands.getRevision());
        assertTrue(commands.executeShortcut(CodeKeyStroke.of(Input.Keys.F6)));
        assertTrue(commands.executeShortcut(CodeKeyStroke.of(Input.Keys.F7)));
        assertFalse(commands.executeShortcut(CodeKeyStroke.of(Input.Keys.F9)));
        assertEquals(1, commands.getShortcuts("one").size);
    }

    @Test
    public void invalidAndDuplicateRebindingsAreAtomic() {
        CodeKeyStroke key = CodeKeyStroke.of(Input.Keys.F6);
        commands.register(command("run"), key);
        long revision = commands.getRevision();
        assertThrows(IllegalArgumentException.class, () -> commands.setShortcuts("run", key, key));
        assertThrows(IllegalArgumentException.class, () -> commands.setShortcuts("run", CodeKeyStroke.of(512)));
        assertThrows(IllegalArgumentException.class, () -> commands.setShortcuts("run", key, null));
        assertEquals(revision, commands.getRevision());
        assertTrue(commands.executeShortcut(key));
    }

    @Test
    public void unbindingLeavesCommandExecutableByIdAndDisposalIsTerminal() {
        commands.register(command("run"), CodeKeyStroke.of(Input.Keys.F6));
        assertTrue(commands.setShortcuts("run"));
        assertFalse(commands.executeShortcut(CodeKeyStroke.of(Input.Keys.F6)));
        assertTrue(commands.execute("run"));
        assertTrue(commands.setShortcuts("run", (CodeKeyStroke[]) null));
        assertFalse(commands.setShortcuts(null));
        assertFalse(commands.setShortcuts("missing"));
        commands.dispose();
        assertTrue(commands.isDisposed());
        assertThrows(IllegalStateException.class, () -> commands.setShortcuts("run", CodeKeyStroke.of(Input.Keys.F6)));
    }

    @Test
    public void unchangedRebindsQueriesAndObsoleteHandlesDoNotAdvanceRevision() {
        Disposable handle = commands.register(command("run"), CodeKeyStroke.of(Input.Keys.F6));
        long revision = commands.getRevision();
        commands.findCommands("run", true);
        commands.getCommands();
        commands.getShortcuts("run");
        commands.setShortcuts("run", CodeKeyStroke.of(Input.Keys.F6));
        assertEquals(revision, commands.getRevision());
        commands.clear();
        assertTrue(commands.getRevision() > revision);
        revision = commands.getRevision();
        handle.dispose();
        commands.clear();
        assertEquals(revision, commands.getRevision());
    }
}
