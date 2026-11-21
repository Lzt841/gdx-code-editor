package com.lzt841.editor.codeactions;

import com.badlogic.gdx.utils.Array;
import com.lzt841.editor.CodeDiagnostic;
import com.lzt841.editor.CodeDiagnosticSeverity;
import com.lzt841.editor.CodeEditor;
import com.lzt841.editor.CodeEditorTextEdit;
import com.lzt841.editor.CodeEditorTextRange;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static com.lzt841.editor.EditorTestSupport.createEditor;
import static com.lzt841.editor.EditorTestSupport.keyTyped;
import static org.junit.Assert.*;

/** Discovery, preview and explicit transactional application without a Stage. */
public class CodeActionControllerTest {
    private CodeEditor editor;
    private Deferred provider;
    private CodeActionController controller;
    private Status status;
    private int contentEvents;

    @Before public void setup() {
        editor = createEditor("ab\ncd");
        provider = new Deferred();
        controller = new CodeActionController(editor, provider);
        status = new Status();
        controller.setListener(status);
        editor.addContentListener((source, event) -> contentEvents++);
    }

    @After public void cleanup() { controller.dispose(); }

    @Test public void caretRequestCapturesTextVersionsAndOnlyValidCoveringDiagnostics() {
        editor.setCursorPosition(0, 1);
        Object hostData = new Object();
        editor.setDiagnostics(Array.with(
            new CodeDiagnostic(0, 0, 0, 2, CodeDiagnosticSeverity.WARNING, "cover", "fix", hostData),
            mark(0, 1, 0, 1, "insertion"), mark(1, 0, 1, 2, "elsewhere"),
            mark(0, -1, 0, 2, "invalid")));
        assertTrue(controller.requestActions());
        CodeActionRequest request = provider.requests.first();
        assertSame(editor, controller.getEditor());
        assertSame(provider, controller.getProvider());
        assertEquals(editor.getDocumentVersion(), request.documentVersion);
        assertEquals(editor.getDiagnosticVersion(), request.diagnosticVersion);
        assertEquals(editor.getDiagnosticRevision(), request.diagnosticRevision);
        assertEquals("ab\ncd", request.text);
        assertEquals(new CodeEditorTextRange(0, 1, 0, 1), request.range);
        assertEquals(0, request.line);
        assertEquals(1, request.column);
        assertEquals(2, request.diagnostics.size());
        assertEquals("cover", request.diagnostics.get(0).message);
        assertEquals("insertion", request.diagnostics.get(1).code);
    }

    @Test public void snapshotOffsetsAndSelectionDiagnosticsUseHalfOpenIntersection() {
        editor.setSelection(0, 1, 1, 1);
        editor.setDiagnostics(Array.with(mark(0, 0, 0, 2, "crosses"), mark(1, 0, 1, 0, "point"),
            mark(1, 1, 1, 1, "atEnd"), mark(0, 0, 0, 1, "before"), mark(1, 1, 1, 2, "after")));
        assertTrue(controller.requestActions());
        CodeActionRequest request = provider.requests.first();
        assertEquals(1, request.startOffset);
        assertEquals(4, request.endOffset);
        assertEquals("b\nc", request.getRangeText());
        assertEquals(2, request.diagnostics.size());
        assertEquals("crosses", request.diagnostics.get(0).code);
        assertEquals("point", request.diagnostics.get(1).code);
    }

    @Test public void staleDiagnosticsAreOmittedButRefactorDiscoveryStillWorks() {
        editor.setDiagnostics(Array.with(mark(0, 0, 0, 2, "fix")));
        editor.replaceRange(1, 0, 1, 1, "X");
        assertTrue(editor.isDiagnosticsStale());
        assertTrue(controller.requestActions());
        assertTrue(provider.requests.first().diagnostics.isEmpty());
        assertEquals(editor.getDocumentVersion(), provider.requests.first().documentVersion);
    }

    @Test public void sameVersionDiagnosticReplacementAndClearInvalidatePendingAndRetainedResults() {
        assertTrue(controller.requestActions());
        int version = editor.getDocumentVersion();
        editor.setDiagnostics(new Array<>());
        assertTrue(provider.responses.first().isCancelled());
        provider.responses.first().complete(Array.with(action("late", "X", true)));
        assertEquals(0, status.ready);
        assertFalse(controller.isPending());
        CodeAction ready = deliver(action("ready", "Y", true));
        editor.clearDiagnostics();
        assertEquals(version, editor.getDocumentVersion());
        assertEquals(0, controller.getActionCount());
        assertNull(controller.preview(ready));
        assertFalse(controller.apply(ready));
        assertEquals("ab\ncd", editor.getText());
    }

    @Test public void rejectedOldDiagnosticPublicationDoesNotInvalidateCurrentActions() {
        CodeAction ready = deliver(action("ready", "X", true));
        assertFalse(editor.setDiagnostics(null, editor.getDocumentVersion() - 1));
        assertTrue(controller.canApply(ready));
        assertTrue(controller.apply(ready));
        assertEquals("Xb\ncd", editor.getText());
    }

    @Test public void caretAndSelectionAreCheckedWithoutWaitingForAnActNotification() {
        assertTrue(controller.requestActions());
        editor.setCursorPosition(0, 1);
        provider.responses.first().complete(Array.with(action("old", "X", true)));
        assertEquals(0, status.ready);
        CodeAction ready = deliver(action("ready", "Y", true));
        editor.setSelection(0, 0, 1, 1);
        assertFalse(controller.canApply(ready));
        assertNull(controller.getRequest());
        assertEquals("ab\ncd", editor.getText());
    }

    @Test public void blockedModesAndCompoundEditsDoNotDispatchAndRejectPendingAnswers() {
        for (int mode = 0; mode < 4; mode++) {
            setMode(mode, true);
            assertFalse(controller.requestActions());
            setMode(mode, false);
            assertTrue(controller.requestActions());
            setMode(mode, true);
            provider.responses.peek().complete(Array.with(action("old", "X", true)));
            assertEquals(0, controller.getActionCount());
            setMode(mode, false);
        }
        assertEquals(4, provider.requests.size);
        assertEquals(0, status.ready);
        assertEquals("ab\ncd", editor.getText());
    }

    @Test public void newRequestsCancelEarlierOnesAndFirstTerminalAnswerWins() {
        assertTrue(controller.requestActions());
        assertTrue(controller.requestActions());
        assertTrue(provider.responses.first().isCancelled());
        provider.responses.first().complete(Array.with(action("old", "X", true)));
        CodeAction current = action("new", "Y", true);
        provider.responses.peek().complete(Array.with(current));
        provider.responses.peek().complete(Array.with(action("duplicate", "Z", true)));
        provider.responses.peek().fail("late failure");
        assertEquals(1, status.ready);
        assertEquals(0, status.failed);
        assertSame(current, controller.getActions().first());
        assertTrue(controller.apply(current));
        assertEquals("Yb\ncd", editor.getText());
    }

    @Test public void providerReplacementExplicitCancelAndTerminalDisposeIgnoreLateAnswers() {
        assertTrue(controller.requestActions());
        Deferred next = new Deferred();
        controller.setProvider(next);
        assertTrue(provider.responses.first().isCancelled());
        assertSame(next, controller.getProvider());
        assertTrue(controller.requestActions());
        controller.cancel();
        assertTrue(next.responses.first().isCancelled());
        assertTrue(controller.requestActions());
        controller.dispose();
        controller.dispose();
        next.responses.peek().complete(Array.with(action("late", "X", true)));
        assertTrue(controller.isDisposed());
        assertFalse(controller.requestActions());
        assertEquals(0, status.ready);
        assertEquals("ab\ncd", editor.getText());
    }

    @Test public void invalidBatchesAreRejectedBeforeFilteringAndNeverPartiallyRetained() {
        int[] filters = {0};
        editor.setInputFilter((source, character) -> { filters[0]++; return true; });
        reject(null);
        reject(Array.with(action("same", "X", false), action("same", "Y", false)));
        Array<CodeAction> nullEntry = new Array<>();
        nullEntry.add(null);
        reject(nullEntry);
        reject(Array.with(new CodeAction("bad", "Bad", Array.with(new CodeEditorTextEdit(9, 0, 9, 0, "x")))));
        reject(Array.with(new CodeAction("overlap", "Overlap", Array.with(
            new CodeEditorTextEdit(0, 0, 0, 2, "X"), new CodeEditorTextEdit(0, 1, 0, 2, "Y")))));
        reject(Array.with(action("crlf", "X\r\nY", false)));
        editor.setText("a\uD83D\uDE00b");
        reject(Array.with(new CodeAction("surrogate", "Split", Array.with(new CodeEditorTextEdit(0, 1, 0, 2, "x")))));
        assertEquals(0, filters[0]);
        assertEquals(7, status.failed);
        assertEquals(0, status.ready);
        assertEquals(0, editor.getUndoEntryCount());
    }

    @Test public void previewMatchesUnorderedEditsOutsideRequestAndRunsNoFilters() {
        editor.setCursorPosition(1, 1);
        CodeAction action = new CodeAction("imports", "Add import and fix", CodeActionKind.REFACTOR, true, null,
            Array.with(new CodeEditorTextEdit(1, 0, 1, 1, "CD"), CodeEditorTextEdit.insert(0, 0, "header\n"),
                CodeEditorTextEdit.insert(0, 0, "prefix")));
        int[] filters = {0};
        editor.setInputFilter((source, character) -> { filters[0]++; return true; });
        deliver(action);
        int version = editor.getDocumentVersion();
        assertEquals("header\nprefixab\nCDd", controller.preview(action));
        assertEquals(0, filters[0]);
        assertEquals(version, editor.getDocumentVersion());
        assertEquals(0, editor.getUndoEntryCount());
        assertTrue(controller.apply(action));
        assertEquals("header\nprefixab\nCDd", editor.getText());
        assertTrue(filters[0] > 0);
    }

    @Test public void exactBatchProducesOneContentEventAndNamedUndoAndTracksTheLiveCaret() {
        editor.setSelection(0, 1, 1, 2);
        CodeAction action = new CodeAction("multi", "Fix both", CodeActionKind.QUICK_FIX, true, null,
            Array.with(new CodeEditorTextEdit(1, 0, 1, 1, "CD"), new CodeEditorTextEdit(0, 0, 0, 1, "AB")));
        deliver(action);
        assertEquals("ab\ncd", editor.getText());
        assertTrue(controller.apply(action));
        assertEquals("ABb\nCDd", editor.getText());
        assertEquals(1, contentEvents);
        assertEquals(1, editor.getUndoEntryCount());
        assertEquals("Fix both", editor.getUndoLabel());
        assertEquals(1, editor.getCursorLine());
        assertEquals(3, editor.getCursorColumn());
        assertNull(editor.getSelection());
        assertEquals(1, status.changed);
        assertEquals(0, controller.getActionCount());
        assertFalse(controller.apply(action));
        assertTrue(editor.undo());
        assertEquals("ab\ncd", editor.getText());
        assertTrue(editor.redo());
        assertEquals("ABb\nCDd", editor.getText());
    }

    @Test public void noopActionIsSuccessfulAndConsumesResultsWithoutTouchingSelectionOrHistory() {
        editor.setSelection(0, 0, 1, 1);
        CodeAction action = new CodeAction("same", "Already fixed", Array.with(new CodeEditorTextEdit(0, 0, 0, 1, "a")));
        deliver(action);
        int version = editor.getDocumentVersion();
        CodeEditorTextRange selection = editor.getSelection();
        assertEquals(editor.getText(), controller.preview(action));
        assertTrue(controller.apply(action));
        assertEquals(version, editor.getDocumentVersion());
        assertEquals(selection, editor.getSelection());
        assertEquals(0, editor.getUndoEntryCount());
        assertEquals(0, contentEvents);
        assertEquals(1, status.unchanged);
        assertEquals(0, controller.getActionCount());
    }

    @Test public void preferredApplicationRequiresExactlyOneEnabledPreferredAction() {
        CodeAction first = action("first", "X", true);
        CodeAction second = action("second", "Y", true);
        deliver(first, second);
        assertFalse(controller.canApplyPreferred());
        assertFalse(controller.applyPreferred());
        CodeAction disabled = new CodeAction("disabled", "Unavailable", CodeActionKind.QUICK_FIX, true,
            "Backend unavailable", new Array<>());
        deliver(first, disabled, action("notPreferred", "Z", false));
        assertTrue(controller.canApplyPreferred());
        assertFalse(controller.canApply(disabled));
        assertNull(controller.preview(disabled));
        assertTrue(controller.applyPreferred());
        assertEquals("Xb\ncd", editor.getText());
        deliver(action("only", "A", false));
        assertFalse(controller.canApplyPreferred());
    }

    @Test public void returnedArraysAreCopiesAndLookalikeActionsCannotBeApplied() {
        CodeAction ready = deliver(action("fix", "X", false));
        controller.getActions().clear();
        assertEquals(1, controller.getActionCount());
        assertFalse(controller.apply(action("fix", "X", false)));
        assertNull(controller.preview(action("fix", "X", false)));
        assertTrue(controller.apply(ready));
    }

    @Test public void oneRejectedCharacterRejectsTheEntireActionAndFiltersOnlyOnce() {
        int[] calls = {0};
        editor.setInputFilter((source, character) -> { calls[0]++; return character != 'Z'; });
        CodeAction action = new CodeAction("filter", "Fix", Array.with(
            new CodeEditorTextEdit(0, 0, 0, 1, "XY"), new CodeEditorTextEdit(1, 0, 1, 1, "Z")));
        deliver(action);
        int version = editor.getDocumentVersion();
        assertFalse(controller.apply(action));
        assertEquals(3, calls[0]);
        assertEquals("ab\ncd", editor.getText());
        assertEquals(version, editor.getDocumentVersion());
        assertEquals(0, editor.getUndoEntryCount());
        assertEquals(0, contentEvents);
        assertEquals(1, status.failed);
        assertFalse(editor.isCompoundEditInProgress());
    }

    @Test public void sameVersionDiagnosticRefreshInsideFilterPreventsThePreparedCommit() {
        CodeAction action = deliver(action("oldDiagnostic", "X", true));
        int version = editor.getDocumentVersion();
        editor.setInputFilter((source, character) -> { source.setDiagnostics(new Array<>()); return true; });
        assertFalse(controller.apply(action));
        assertEquals("ab\ncd", editor.getText());
        assertEquals(version, editor.getDocumentVersion());
        assertEquals(0, status.changed);
        assertEquals(0, status.failed);
        assertEquals(0, editor.getUndoEntryCount());
        assertEquals(0, controller.getActionCount());
    }

    @Test public void filterCancellationSelectionMovementAndModeChangesRejectTheCommit() {
        for (int operation = 0; operation < 4; operation++) {
            CodeEditor target = createEditor("ab");
            Deferred deferred = new Deferred();
            CodeActionController service = new CodeActionController(target, deferred);
            final int mode = operation;
            try {
                service.requestActions();
                CodeAction ready = action("fix", "X", true);
                deferred.responses.first().complete(Array.with(ready));
                target.setInputFilter((source, character) -> {
                    if (mode == 0) service.cancel();
                    else if (mode == 1) service.dispose();
                    else if (mode == 2) source.setCursorPosition(0, 1);
                    else source.setPasswordMode(true);
                    return true;
                });
                assertFalse(service.apply(ready));
                assertEquals("ab", target.getText());
                assertEquals(0, target.getUndoEntryCount());
                assertEquals(0, service.getActionCount());
            } finally { service.dispose(); }
        }
    }

    @Test public void newerRequestStartedInsideFilterSurvivesRejectedOldApplication() {
        CodeAction old = deliver(action("old", "X", true));
        boolean[] once = {false};
        editor.setInputFilter((source, character) -> {
            if (!once[0]) { once[0] = true; assertTrue(controller.requestActions()); }
            return true;
        });
        assertFalse(controller.apply(old));
        assertTrue(controller.isPending());
        assertEquals(2, provider.requests.size);
        assertFalse(provider.responses.peek().isCancelled());
        CodeAction next = action("new", "Y", true);
        provider.responses.peek().complete(Array.with(next));
        assertTrue(controller.apply(next));
        assertEquals("Yb\ncd", editor.getText());
    }

    @Test public void filterHostMutationPersistsButStaleOriginalCoordinatesAreNeverApplied() {
        CodeAction old = deliver(action("old", "X", true));
        editor.setInputFilter((source, character) -> { source.setText("host"); return true; });
        assertFalse(controller.apply(old));
        assertEquals("host", editor.getText());
        assertEquals(0, editor.getUndoEntryCount());
        assertEquals(0, status.changed);
    }

    @Test public void providerExceptionAfterReadyCallbackStartsNewRequestDoesNotRetireTheNewOne() {
        Deferred next = new Deferred();
        RuntimeException expected = new IllegalStateException("provider error");
        controller.setListener(new CodeActionListener() {
            @Override public void onActionsReady(CodeActionController source, CodeActionRequest request, Array<CodeAction> actions) {
                source.setProvider(next);
                assertTrue(source.requestActions());
            }
        });
        controller.setProvider((request, response) -> {
            response.complete(Array.with(action("old", "X", true)));
            throw expected;
        });
        try { controller.requestActions(); fail("expected exception"); }
        catch (RuntimeException actual) { assertSame(expected, actual); }
        assertTrue(controller.isPending());
        assertFalse(next.responses.first().isCancelled());
        controller.setListener(status);
        CodeAction ready = action("new", "Y", true);
        next.responses.first().complete(Array.with(ready));
        assertTrue(controller.apply(ready));
        assertEquals("Yb\ncd", editor.getText());
    }

    @Test public void readyListenerCanPreviewAndApplySynchronously() {
        controller.setListener(new CodeActionListener() {
            @Override public void onActionsReady(CodeActionController source, CodeActionRequest request, Array<CodeAction> actions) {
                assertEquals("Xb\ncd", source.preview(actions.first()));
                assertTrue(source.apply(actions.first()));
            }
            @Override public void onActionApplied(CodeActionController source, CodeActionRequest request, CodeAction action, boolean changed) {
                assertTrue(changed);
                status.changed++;
            }
        });
        deliver(action("sync", "X", true));
        assertEquals("Xb\ncd", editor.getText());
        assertEquals(1, status.changed);
        assertEquals(0, controller.getActionCount());
    }

    @Test public void contentCallbackCanStartNewRequestWithoutOldCleanupClearingIt() {
        boolean[] once = {false};
        editor.addContentListener((source, event) -> {
            if (!once[0]) { once[0] = true; assertTrue(controller.requestActions()); }
        });
        CodeAction old = deliver(action("old", "X", true));
        assertTrue(controller.apply(old));
        assertTrue(controller.isPending());
        assertEquals("Xb\ncd", provider.requests.peek().text);
        assertFalse(provider.responses.peek().isCancelled());
        CodeAction next = action("new", "Y", true);
        provider.responses.peek().complete(Array.with(next));
        assertTrue(controller.apply(next));
        assertEquals("Yb\ncd", editor.getText());
    }

    @Test public void ownContentNotificationCanQueryActionsWithoutCancellingSuccessfulStatus() {
        editor.addContentListener((source, event) -> {
            assertEquals(1, controller.getActionCount());
            assertFalse(controller.isPending());
            assertFalse(controller.canApplyPreferred());
        });
        CodeAction ready = deliver(action("fix", "X", true));
        assertTrue(controller.apply(ready));
        assertEquals(1, status.changed);
        assertEquals(0, status.failed);
    }

    @Test public void immediateTypedInputIsFlushedBeforeDiscoverySnapshot() {
        assertTrue(keyTyped(editor, 'x'));
        assertTrue(controller.requestActions());
        assertEquals("xab\ncd", provider.requests.first().text);
        assertTrue(controller.isPending());
        assertEquals(1, contentEvents);
    }

    @Test public void earlierFlushListenerCanStartNewerRequestWithoutLaterListenerInvalidatingIt() {
        CodeEditor target = createEditor("ab");
        CodeActionController[] service = new CodeActionController[1];
        boolean[] once = {false};
        target.addContentListener((source, event) -> {
            if (!once[0]) { once[0] = true; assertTrue(service[0].requestActions()); }
        });
        Deferred deferred = new Deferred();
        service[0] = new CodeActionController(target, deferred);
        try {
            assertTrue(keyTyped(target, 'x'));
            assertFalse(service[0].requestActions());
            assertEquals(1, deferred.requests.size);
            assertEquals("xab", deferred.requests.first().text);
            assertTrue(service[0].isPending());
            assertFalse(deferred.responses.first().isCancelled());
        } finally { service[0].dispose(); }
    }

    @Test public void undoBackToIdenticalTextStillInvalidatesEarlierActions() {
        CodeAction ready = deliver(action("fix", "X", true));
        editor.replaceRange(0, 0, 0, 1, "A");
        assertTrue(editor.undo());
        assertEquals("ab\ncd", editor.getText());
        assertFalse(controller.apply(ready));
        assertEquals(0, controller.getActionCount());
    }

    @Test public void filterExceptionRetiresOnlyThatActionSetAndLeavesNoOpenCompoundEdit() {
        CodeAction ready = deliver(action("fix", "X", true));
        RuntimeException expected = new IllegalStateException("host filter");
        editor.setInputFilter((source, character) -> { throw expected; });
        try { controller.apply(ready); fail("expected exception"); }
        catch (RuntimeException actual) { assertSame(expected, actual); }
        assertEquals(0, controller.getActionCount());
        assertEquals("ab\ncd", editor.getText());
        assertFalse(editor.isCompoundEditInProgress());
    }

    @Test public void builtinDiagnosticProviderRepairsOnlyAnExplicitlyChosenCurrentDiagnostic() {
        editor.setText("teh word");
        editor.setCursorPosition(0, 1);
        editor.setDiagnostics(Array.with(mark(0, 0, 0, 3, "spelling")));
        controller.setProvider(new DiagnosticCodeActionProvider(new CodeDiagnosticFix("spelling", "Use the", "the")));
        assertTrue(controller.requestActions());
        assertEquals("teh word", editor.getText());
        assertEquals("the word", controller.preview(controller.getActions().first()));
        assertTrue(controller.applyPreferred());
        assertEquals("the word", editor.getText());
        assertTrue(editor.isDiagnosticsStale());
        assertEquals("Use the", editor.getUndoLabel());
        assertTrue(editor.undo());
        assertEquals("teh word", editor.getText());
    }

    private CodeAction deliver(CodeAction... actions) {
        assertTrue(controller.requestActions());
        provider.responses.peek().complete(Array.with(actions));
        return actions.length == 0 ? null : actions[0];
    }

    private void reject(Array<CodeAction> actions) {
        assertTrue(controller.requestActions());
        provider.responses.peek().complete(actions);
        assertFalse(controller.isPending());
        assertEquals(0, controller.getActionCount());
        assertNotNull(status.message);
    }

    private void setMode(int mode, boolean enabled) {
        if (mode == 0) editor.setReadOnly(enabled);
        else if (mode == 1) editor.setDisabled(enabled);
        else if (mode == 2) editor.setPasswordMode(enabled);
        else if (enabled) editor.beginCompoundEdit();
        else editor.endCompoundEdit();
    }

    private static CodeAction action(String id, String replacement, boolean preferred) {
        return new CodeAction(id, "Fix " + id, CodeActionKind.QUICK_FIX, preferred, null,
            Array.with(new CodeEditorTextEdit(0, 0, 0, 1, replacement)));
    }

    private static CodeDiagnostic mark(int sl, int sc, int el, int ec, String code) {
        return new CodeDiagnostic(sl, sc, el, ec, CodeDiagnosticSeverity.WARNING, code, code, null);
    }

    private static class Deferred implements CodeActionProvider {
        final Array<CodeActionRequest> requests = new Array<>();
        final Array<CodeActionResponse> responses = new Array<>();
        @Override public void provideActions(CodeActionRequest request, CodeActionResponse response) {
            requests.add(request);
            responses.add(response);
        }
    }

    private static class Status implements CodeActionListener {
        int ready, changed, unchanged, failed;
        String message;
        @Override public void onActionsReady(CodeActionController source, CodeActionRequest request, Array<CodeAction> actions) { ready++; }
        @Override public void onActionApplied(CodeActionController source, CodeActionRequest request, CodeAction action, boolean changed) {
            if (changed) this.changed++; else unchanged++;
        }
        @Override public void onCodeActionFailed(CodeActionController source, CodeActionRequest request, String message) {
            failed++;
            this.message = message;
        }
    }
}
