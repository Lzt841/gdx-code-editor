package com.lzt841.editor.extensions;

import com.badlogic.gdx.Input;
import com.badlogic.gdx.utils.Array;
import com.lzt841.editor.CodeEditor;
import com.lzt841.editor.CodeEditorCommand;
import com.lzt841.editor.CodeEditorTextEdit;
import com.lzt841.editor.CodeEditorTextRange;
import com.lzt841.editor.CodeKeyStroke;
import com.lzt841.editor.formatting.CodeFormattingProvider;
import com.lzt841.editor.formatting.CodeFormattingRequest;
import com.lzt841.editor.formatting.CodeFormattingResponse;
import com.lzt841.editor.formatting.JsonCodeFormattingProvider;
import org.junit.Before;
import org.junit.Test;

import static com.lzt841.editor.EditorTestSupport.createEditor;
import static com.lzt841.editor.extensions.CodeFormattingExtension.*;
import static org.junit.Assert.*;

public class CodeFormattingExtensionTest {
    private CodeEditor editor;
    private HoldingProvider provider;
    private CodeFormattingExtension extension;

    @Before public void setup() {
        editor = createEditor("{\"a\":1}");
        provider = new HoldingProvider();
        extension = new CodeFormattingExtension(provider);
    }

    @Test public void commandsExposeMetadataAndRebindableDocumentShortcutWithoutStage() {
        editor.getExtensions().install(extension);
        assertEquals(2, editor.getCommands().size());
        assertEquals("Formatting", editor.getCommands().getCommand(FORMAT_DOCUMENT).category);
        assertTrue(editor.getCommands().getCommand(FORMAT_DOCUMENT).requiresEditable);
        assertEquals(Array.with(CodeKeyStroke.ctrlAlt(Input.Keys.L)), editor.getCommands().getShortcuts(FORMAT_DOCUMENT));
        assertTrue(editor.getCommands().executeShortcut(CodeKeyStroke.ctrlAlt(Input.Keys.L)));
        assertEquals(editor.getText(), provider.request.text);
        assertTrue(extension.getController().isPending());
        assertTrue(editor.getCommands().setShortcuts(FORMAT_DOCUMENT, CodeKeyStroke.ctrlShift(Input.Keys.F)));
        assertFalse(editor.getCommands().executeShortcut(CodeKeyStroke.ctrlAlt(Input.Keys.L)));
        CodeFormattingResponse old = provider.response;
        assertTrue(editor.getCommands().executeShortcut(CodeKeyStroke.ctrlShift(Input.Keys.F)));
        assertTrue(old.isCancelled());
    }

    @Test public void selectionAndAvailabilityTrackLiveEditorState() {
        editor.getExtensions().install(extension);
        assertFalse(editor.getCommands().isEnabled(FORMAT_SELECTION));
        editor.setSelection(0, 5, 0, 6);
        assertTrue(editor.getCommands().execute(FORMAT_SELECTION));
        assertEquals(new CodeEditorTextRange(0, 5, 0, 6), provider.request.range);
        assertTrue(provider.request.selectionOnly);
        editor.setReadOnly(true);
        assertFalse(editor.getCommands().execute(FORMAT_DOCUMENT));
        editor.setReadOnly(false);
        editor.setDisabled(true);
        assertFalse(editor.getCommands().execute(FORMAT_DOCUMENT));
        editor.setDisabled(false);
        editor.setPasswordMode(true);
        assertFalse(editor.getCommands().execute(FORMAT_DOCUMENT));
        editor.setPasswordMode(false);
        editor.beginCompoundEdit();
        try { assertFalse(editor.getCommands().execute(FORMAT_DOCUMENT)); }
        finally { editor.endCompoundEdit(); }
    }

    @Test public void unloadCancelsRequestsRemovesCommandsAndAllowsReinstallation() {
        editor.getExtensions().install(extension);
        assertTrue(editor.getCommands().execute(FORMAT_DOCUMENT));
        CodeFormattingResponse old = provider.response;
        assertTrue(editor.getExtensions().uninstall(ID));
        assertTrue(old.isCancelled());
        assertTrue(extension.getController().isDisposed());
        assertNull(editor.getCommands().getCommand(FORMAT_DOCUMENT));
        assertNull(editor.getCommands().getCommand(FORMAT_SELECTION));
        editor.getExtensions().install(extension);
        assertTrue(editor.getCommands().execute(FORMAT_DOCUMENT));
        old.complete(Array.with(new CodeEditorTextEdit(0, 0, 0, 7, "stale")));
        assertEquals("{\"a\":1}", editor.getText());
        assertTrue(extension.getController().isPending());
    }

    @Test public void conflictingCommandRollsBackControllerAndPartialRegistrations() {
        editor.getCommands().register(new CodeEditorCommand(FORMAT_SELECTION, "Host command", source -> true));
        try {
            editor.getExtensions().install(extension);
            fail("expected command collision");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("duplicate command"));
        }
        assertFalse(editor.getExtensions().isInstalled(ID));
        assertTrue(extension.getController().isDisposed());
        assertNull(editor.getCommands().getCommand(FORMAT_DOCUMENT));
        assertEquals("Host command", editor.getCommands().getCommand(FORMAT_SELECTION).label);
        assertEquals(1, editor.getCommands().size());
    }

    @Test public void unloadInsideInputFilterPreventsMutationAndRetainsSelection() {
        editor.getExtensions().install(extension);
        editor.setSelection(0, 1, 0, 4);
        assertTrue(editor.getCommands().execute(FORMAT_DOCUMENT));
        int version = editor.getDocumentVersion();
        editor.setInputFilter((source, character) -> {
            source.getExtensions().uninstall(ID);
            return true;
        });
        provider.response.complete(Array.with(CodeEditorTextEdit.insert(0, 5, " ")));
        assertEquals("{\"a\":1}", editor.getText());
        assertEquals(version, editor.getDocumentVersion());
        assertEquals(new CodeEditorTextRange(0, 1, 0, 4), editor.getSelection());
        assertEquals(0, editor.getUndoEntryCount());
        assertTrue(extension.getController().isDisposed());
    }

    @Test public void activeExtensionCannotBeSharedAcrossEditors() {
        editor.getExtensions().install(extension);
        CodeEditor other = createEditor("{}");
        try {
            other.getExtensions().install(extension);
            fail("expected active instance rejection");
        } catch (IllegalStateException expected) {
            assertTrue(expected.getMessage().contains("already active"));
        }
        assertFalse(extension.getController().isDisposed());
        assertSame(editor, extension.getController().getEditor());
        assertTrue(editor.getCommands().execute(FORMAT_DOCUMENT));
        assertFalse(other.getExtensions().isInstalled(ID));
    }

    @Test public void builtinJsonFormattingProducesOneUndoableCommandAndKeepsCaretInToken() {
        editor.setText("{\"value\":12345,\"next\":[true,false]}");
        editor.setCursorPosition(0, 12); // Inside 12345, after its first three digits.
        editor.getExtensions().install(new CodeFormattingExtension(new JsonCodeFormattingProvider()));
        assertTrue(editor.getCommands().execute(FORMAT_DOCUMENT));
        assertEquals("{\n    \"value\": 12345,\n    \"next\": [\n        true,\n        false\n    ]\n}", editor.getText());
        assertEquals(1, editor.getCursorLine());
        assertEquals(16, editor.getCursorColumn());
        assertEquals("Format Document", editor.getUndoLabel());
        assertEquals(1, editor.getUndoEntryCount());
        assertTrue(editor.undo());
        assertEquals("{\"value\":12345,\"next\":[true,false]}", editor.getText());
    }

    private static final class HoldingProvider implements CodeFormattingProvider {
        CodeFormattingRequest request;
        CodeFormattingResponse response;
        @Override public void format(CodeFormattingRequest request, CodeFormattingResponse response) {
            this.request = request;
            this.response = response;
        }
    }
}
