package com.lzt841.editor.extensions;

import com.badlogic.gdx.Input;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Disposable;
import com.lzt841.editor.CodeEditor;
import com.lzt841.editor.CodeEditorCaretListener;
import com.lzt841.editor.CodeEditorCommand;
import com.lzt841.editor.CodeEditorContentListener;
import com.lzt841.editor.CodeEditorDiagnosticListener;
import com.lzt841.editor.CodeEditorTextEdit;
import com.lzt841.editor.CodeEditorTextRange;
import com.lzt841.editor.CodeKeyStroke;
import com.lzt841.editor.codeactions.CodeAction;
import com.lzt841.editor.codeactions.CodeActionController;
import com.lzt841.editor.codeactions.CodeActionKind;
import com.lzt841.editor.codeactions.CodeActionListener;
import com.lzt841.editor.codeactions.CodeActionProvider;
import com.lzt841.editor.codeactions.CodeActionRequest;
import com.lzt841.editor.codeactions.CodeActionResponse;
import org.junit.Before;
import org.junit.Test;

import static com.lzt841.editor.EditorTestSupport.createEditor;
import static com.lzt841.editor.extensions.CodeActionExtension.*;
import static org.junit.Assert.*;

public class CodeActionExtensionTest {
    private CodeEditor editor;
    private HoldingProvider provider;
    private CodeActionExtension extension;

    @Before public void setup() {
        editor = createEditor("one two");
        provider = new HoldingProvider();
        extension = new CodeActionExtension(provider);
    }

    @Test public void commandsExposeMetadataAndRebindableShortcutWithoutStage() {
        assertNull(extension.getController());
        assertEquals("editor.codeActions", extension.getId());
        editor.getExtensions().install(extension);
        assertNull(editor.getStage());
        assertEquals(2, editor.getCommands().size());
        CodeEditorCommand show = editor.getCommands().getCommand(REQUEST_ACTIONS);
        CodeEditorCommand apply = editor.getCommands().getCommand(APPLY_PREFERRED);
        assertEquals("Show Code Actions", show.label);
        assertEquals("Apply Preferred Code Action", apply.label);
        assertEquals("Code Actions", show.category);
        assertEquals("Code Actions", apply.category);
        assertTrue(show.requiresEditable);
        assertTrue(apply.requiresEditable);
        assertTrue(show.consumeShortcutOnNoOp);
        assertEquals(Array.with(CodeKeyStroke.alt(Input.Keys.ENTER)),
            editor.getCommands().getShortcuts(REQUEST_ACTIONS));
        assertEquals(0, editor.getCommands().getShortcuts(APPLY_PREFERRED).size);
        assertTrue(editor.getCommands().executeShortcut(CodeKeyStroke.alt(Input.Keys.ENTER)));
        assertEquals(editor.getText(), provider.request.text);
        assertTrue(extension.getController().isPending());
        CodeActionResponse old = provider.response;
        assertTrue(editor.getCommands().setShortcuts(REQUEST_ACTIONS, CodeKeyStroke.of(Input.Keys.F6)));
        assertFalse(editor.getCommands().executeShortcut(CodeKeyStroke.alt(Input.Keys.ENTER)));
        assertTrue(editor.getCommands().executeShortcut(CodeKeyStroke.of(Input.Keys.F6)));
        assertTrue(old.isCancelled());
        editor.setReadOnly(true);
        assertFalse(editor.getCommands().execute(REQUEST_ACTIONS));
        assertTrue(editor.getCommands().handleKeyDown(Input.Keys.F6));
        assertEquals(2, provider.calls);
    }

    @Test public void requestingActionsRetainsResultsAndNotifiesWithoutApplying() {
        editor.getExtensions().install(extension);
        int[] ready = {0};
        extension.getController().setListener(new CodeActionListener() {
            @Override public void onActionsReady(CodeActionController controller, CodeActionRequest request,
                                                 Array<CodeAction> actions) {
                ready[0]++;
                assertSame(provider.request, request);
                assertEquals(1, actions.size);
                actions.clear();
            }
        });
        int version = editor.getDocumentVersion();
        assertTrue(editor.getCommands().execute(REQUEST_ACTIONS));
        CodeAction action = action("fixed", true, null);
        provider.response.complete(Array.with(action));
        assertEquals(1, ready[0]);
        assertEquals("one two", editor.getText());
        assertEquals(version, editor.getDocumentVersion());
        assertEquals(0, editor.getUndoEntryCount());
        assertFalse(extension.getController().isPending());
        assertSame(provider.request, extension.getController().getRequest());
        assertEquals(1, extension.getController().getActionCount());
        Array<CodeAction> snapshot = extension.getController().getActions();
        assertSame(action, snapshot.first());
        snapshot.clear();
        assertEquals(1, extension.getController().getActionCount());
        assertTrue(editor.getCommands().isEnabled(APPLY_PREFERRED));
    }

    @Test public void uniqueEnabledPreferredActionAppliesAsOneNamedUndoStep() {
        editor.getExtensions().install(extension);
        assertTrue(editor.getCommands().execute(REQUEST_ACTIONS));
        CodeAction preferred = new CodeAction("fix", "Fix names", CodeActionKind.QUICK_FIX, true, null,
            Array.with(new CodeEditorTextEdit(0, 0, 0, 3, "ONE"),
                new CodeEditorTextEdit(0, 4, 0, 7, "TWO")));
        provider.response.complete(Array.with(action("disabled", true, "Unavailable"),
            action("ordinary", false, null), preferred));
        assertTrue(editor.getCommands().isEnabled(APPLY_PREFERRED));
        assertTrue(editor.getCommands().execute(APPLY_PREFERRED));
        assertEquals("ONE TWO", editor.getText());
        assertEquals("Fix names", editor.getUndoLabel());
        assertEquals(1, editor.getUndoEntryCount());
        assertFalse(editor.getCommands().isEnabled(APPLY_PREFERRED));
        assertFalse(editor.getCommands().execute(APPLY_PREFERRED));
        assertEquals(0, extension.getController().getActionCount());
        assertTrue(editor.undo());
        assertEquals("one two", editor.getText());
    }

    @Test public void twoEnabledPreferredActionsDisableApplicationWithoutGuessing() {
        editor.getExtensions().install(extension);
        assertTrue(editor.getCommands().execute(REQUEST_ACTIONS));
        provider.response.complete(Array.with(action("first", true, null), action("second", true, null)));
        assertEquals(2, extension.getController().getActionCount());
        assertFalse(extension.getController().canApplyPreferred());
        assertFalse(editor.getCommands().isEnabled(APPLY_PREFERRED));
        assertFalse(editor.getCommands().execute(APPLY_PREFERRED));
        assertEquals("one two", editor.getText());
        assertEquals(0, editor.getUndoEntryCount());
    }

    @Test public void noEnabledPreferredActionDisablesApplication() {
        editor.getExtensions().install(extension);
        assertFalse(editor.getCommands().isEnabled(APPLY_PREFERRED));
        assertTrue(editor.getCommands().execute(REQUEST_ACTIONS));
        provider.response.complete(Array.with(new CodeAction("ordinary", "Ordinary action",
            Array.with(new CodeEditorTextEdit(0, 0, 0, 3, "other"))),
            action("disabled", true, "Needs host support")));
        assertFalse(editor.getCommands().isEnabled(APPLY_PREFERRED));
        assertFalse(editor.getCommands().execute(APPLY_PREFERRED));
        assertEquals("one two", editor.getText());
        assertEquals(0, editor.getUndoEntryCount());
    }

    @Test public void commandsRespectReadonlyDisabledPasswordAndCompoundStates() {
        editor.getExtensions().install(extension);
        for (int state = 0; state < 4; state++) {
            assertTrue(editor.getCommands().execute(REQUEST_ACTIONS));
            provider.response.complete(Array.with(action("fixed", true, null)));
            assertTrue(editor.getCommands().isEnabled(APPLY_PREFERRED));
            if (state == 0) editor.setReadOnly(true);
            else if (state == 1) editor.setDisabled(true);
            else if (state == 2) editor.setPasswordMode(true);
            else editor.beginCompoundEdit();
            try {
                assertFalse(editor.getCommands().isEnabled(REQUEST_ACTIONS));
                assertFalse(editor.getCommands().execute(REQUEST_ACTIONS));
                assertFalse(editor.getCommands().isEnabled(APPLY_PREFERRED));
                assertFalse(editor.getCommands().execute(APPLY_PREFERRED));
            } finally {
                if (state == 0) editor.setReadOnly(false);
                else if (state == 1) editor.setDisabled(false);
                else if (state == 2) editor.setPasswordMode(false);
                else editor.endCompoundEdit();
            }
        }
        assertEquals(4, provider.calls);
        assertEquals("one two", editor.getText());
        assertEquals(0, editor.getUndoEntryCount());
    }

    @Test public void unloadCancelsLateResponsesAndAllowsReinstallWithHostOwnedProvider() {
        editor.getExtensions().install(extension);
        assertTrue(editor.getCommands().execute(REQUEST_ACTIONS));
        CodeActionResponse oldResponse = provider.response;
        CodeActionController oldController = extension.getController();
        assertTrue(editor.getExtensions().uninstall(ID));
        assertTrue(oldResponse.isCancelled());
        assertTrue(oldController.isDisposed());
        assertNull(editor.getCommands().getCommand(REQUEST_ACTIONS));
        assertNull(editor.getCommands().getCommand(APPLY_PREFERRED));
        assertFalse(provider.disposed);
        editor.getExtensions().install(extension);
        assertNotSame(oldController, extension.getController());
        assertTrue(editor.getCommands().execute(REQUEST_ACTIONS));
        oldResponse.complete(Array.with(action("stale", true, null)));
        assertEquals("one two", editor.getText());
        assertEquals(0, extension.getController().getActionCount());
        assertTrue(extension.getController().isPending());
        provider.response.complete(Array.with(action("fresh", true, null)));
        assertTrue(editor.getCommands().execute(APPLY_PREFERRED));
        assertEquals("fresh two", editor.getText());
        assertFalse(provider.disposed);
    }

    @Test public void conflictingEitherCommandRollsBackControllerListenersAndPartialRegistration() {
        for (String conflict : new String[] {REQUEST_ACTIONS, APPLY_PREFERRED}) {
            TrackingEditor target = new TrackingEditor(editor.getStyle());
            target.setText("one two");
            target.getCommands().register(new CodeEditorCommand(conflict, "Host command", source -> true));
            CodeActionExtension candidate = new CodeActionExtension(provider);
            try {
                target.getExtensions().install(candidate);
                fail("expected command collision");
            } catch (IllegalArgumentException expected) {
                assertTrue(expected.getMessage().contains("duplicate command"));
            }
            assertFalse(target.getExtensions().isInstalled(ID));
            assertTrue(candidate.getController().isDisposed());
            assertFalse(candidate.getController().requestActions());
            assertNull(candidate.getController().getRequest());
            assertEquals(1, target.contentAdditions);
            assertEquals(0, target.contentResources.size);
            assertEquals(0, target.caretResources.size);
            assertEquals(0, target.diagnosticResources.size);
            assertEquals(1, target.getCommands().size());
            assertEquals("Host command", target.getCommands().getCommand(conflict).label);
            assertNull(target.getCommands().getCommand(
                REQUEST_ACTIONS.equals(conflict) ? APPLY_PREFERRED : REQUEST_ACTIONS));
            assertEquals(0, target.getCommands().getShortcuts(REQUEST_ACTIONS).size);
            assertFalse(provider.disposed);
        }
        assertEquals(0, provider.calls);
    }

    @Test public void unloadInsideInputFilterPreventsApplicationAndRetainsSelection() {
        editor.getExtensions().install(extension);
        editor.setSelection(0, 1, 0, 5);
        assertTrue(editor.getCommands().execute(REQUEST_ACTIONS));
        provider.response.complete(Array.with(action("fixed", true, null)));
        int version = editor.getDocumentVersion();
        int[] filters = {0};
        editor.setInputFilter((source, character) -> {
            filters[0]++;
            source.getExtensions().uninstall(ID);
            return true;
        });
        assertFalse(editor.getCommands().execute(APPLY_PREFERRED));
        assertTrue(filters[0] > 0);
        assertEquals("one two", editor.getText());
        assertEquals(version, editor.getDocumentVersion());
        assertEquals(new CodeEditorTextRange(0, 1, 0, 5), editor.getSelection());
        assertEquals(0, editor.getUndoEntryCount());
        assertTrue(extension.getController().isDisposed());
        assertFalse(provider.disposed);
    }

    @Test public void activeExtensionCannotBeSharedAcrossEditors() {
        editor.getExtensions().install(extension);
        CodeActionController original = extension.getController();
        assertTrue(editor.getCommands().execute(REQUEST_ACTIONS));
        CodeActionResponse pending = provider.response;
        CodeEditor other = createEditor("other");
        try {
            other.getExtensions().install(extension);
            fail("expected active instance rejection");
        } catch (IllegalStateException expected) {
            assertTrue(expected.getMessage().contains("already active"));
        }
        assertSame(original, extension.getController());
        assertSame(editor, original.getEditor());
        assertFalse(original.isDisposed());
        assertFalse(pending.isCancelled());
        assertFalse(other.getExtensions().isInstalled(ID));
        assertEquals(0, other.getCommands().size());
        pending.complete(Array.with(action("fixed", true, null)));
        assertTrue(editor.getCommands().execute(APPLY_PREFERRED));
        assertEquals("fixed two", editor.getText());
        assertEquals("other", other.getText());
    }

    private static CodeAction action(String id, boolean preferred, String disabledReason) {
        return new CodeAction(id, "Fix " + id, CodeActionKind.QUICK_FIX, preferred, disabledReason,
            Array.with(new CodeEditorTextEdit(0, 0, 0, 3, id)));
    }

    private static final class HoldingProvider implements CodeActionProvider, Disposable {
        CodeActionRequest request;
        CodeActionResponse response;
        int calls;
        boolean disposed;

        @Override public void provideActions(CodeActionRequest request, CodeActionResponse response) {
            this.request = request;
            this.response = response;
            calls++;
        }

        @Override public void dispose() { disposed = true; }
    }

    private static final class TrackingEditor extends CodeEditor {
        final Array<CodeEditorContentListener> contentResources = new Array<>();
        final Array<CodeEditorCaretListener> caretResources = new Array<>();
        final Array<CodeEditorDiagnosticListener> diagnosticResources = new Array<>();
        int contentAdditions;

        TrackingEditor(CodeEditorStyle style) { super(style); }

        @Override public void addContentListener(CodeEditorContentListener listener) {
            super.addContentListener(listener);
            if (!contentResources.contains(listener, true)) {
                contentResources.add(listener);
                contentAdditions++;
            }
        }

        @Override public void removeContentListener(CodeEditorContentListener listener) {
            super.removeContentListener(listener);
            contentResources.removeValue(listener, true);
        }

        @Override public void addCaretListener(CodeEditorCaretListener listener) {
            super.addCaretListener(listener);
            if (!caretResources.contains(listener, true)) caretResources.add(listener);
        }

        @Override public void removeCaretListener(CodeEditorCaretListener listener) {
            super.removeCaretListener(listener);
            caretResources.removeValue(listener, true);
        }

        @Override public void addDiagnosticListener(CodeEditorDiagnosticListener listener) {
            super.addDiagnosticListener(listener);
            if (!diagnosticResources.contains(listener, true)) diagnosticResources.add(listener);
        }

        @Override public void removeDiagnosticListener(CodeEditorDiagnosticListener listener) {
            super.removeDiagnosticListener(listener);
            diagnosticResources.removeValue(listener, true);
        }
    }
}
