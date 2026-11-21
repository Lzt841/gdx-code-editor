package com.lzt841.editor.formatting;

import com.badlogic.gdx.utils.Array;
import com.lzt841.editor.CodeEditor;
import com.lzt841.editor.CodeEditorTextEdit;
import com.lzt841.editor.CodeEditorTextRange;
import com.lzt841.editor.CodeIndentStrategy;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static com.lzt841.editor.EditorTestSupport.createEditor;
import static com.lzt841.editor.EditorTestSupport.keyTyped;
import static org.junit.Assert.*;

/** Headless formatting lifecycle, exact-edit and callback reentrancy regressions. */
public class CodeFormattingControllerTest {
    private CodeEditor editor;
    private RecordingProvider provider;
    private CodeFormattingController controller;
    private RecordingListener listener;
    private int events;

    @Before public void setup() {
        editor = createEditor("ab\ncd");
        provider = new RecordingProvider();
        controller = new CodeFormattingController(editor, provider);
        listener = new RecordingListener();
        controller.setListener(listener);
        editor.addContentListener((source, event) -> events++);
    }

    @After public void cleanup() {
        controller.dispose();
    }

    @Test public void documentRequestCapturesImmutableTextRangeVersionAndIndentOptions() {
        editor.setIndentStrategy(CodeIndentStrategy.spaces(2));
        int version = editor.getDocumentVersion();
        assertTrue(controller.formatDocument());
        CodeFormattingRequest request = provider.requests.first();
        assertSame(editor, controller.getEditor());
        assertSame(provider, controller.getProvider());
        assertEquals("ab\ncd", request.text);
        assertEquals(version, request.documentVersion);
        assertEquals(new CodeEditorTextRange(0, 0, 1, 2), request.range);
        assertEquals(0, request.startOffset);
        assertEquals(5, request.endOffset);
        assertEquals(request.text, request.getRangeText());
        assertEquals("", request.getBaseIndent());
        assertFalse(request.selectionOnly);
        assertEquals(2, request.indentWidth);
        assertFalse(request.useTabs);
        editor.setIndentStrategy(CodeIndentStrategy.tabs(8));
        editor.setText("replacement");
        assertEquals("ab\ncd", request.text);
        assertEquals(2, request.indentWidth);
        assertFalse(request.useTabs);
        assertFalse(controller.isPending());
        assertTrue(provider.responses.first().isCancelled());
    }

    @Test public void selectionRequestCapturesAbsoluteRangeOffsetsAndBaseIndent() {
        editor.setText("  ab\n    cd\nend");
        editor.setIndentStrategy(CodeIndentStrategy.tabs(4));
        editor.setSelection(0, 2, 1, 6);
        assertTrue(controller.formatSelection());
        CodeFormattingRequest request = provider.requests.first();
        assertTrue(request.selectionOnly);
        assertEquals(new CodeEditorTextRange(0, 2, 1, 6), request.range);
        assertEquals(2, request.startOffset);
        assertEquals("  ab\n    cd".length(), request.endOffset);
        assertEquals("ab\n    cd", request.getRangeText());
        assertEquals("  ", request.getBaseIndent());
        assertEquals(4, request.indentWidth);
        assertTrue(request.useTabs);
    }

    @Test public void unorderedMultiEditBatchHasOneEventAndOneNamedUndoStep() {
        editor.setSelection(0, 1, 1, 2);
        assertTrue(controller.formatDocument());
        provider.responses.first().complete(Array.with(
            new CodeEditorTextEdit(1, 0, 1, 1, "CD"),
            new CodeEditorTextEdit(0, 0, 0, 1, "AB")));
        assertEquals("ABb\nCDd", editor.getText());
        assertNull(editor.getSelection());
        assertEquals(1, editor.getCursorLine());
        assertEquals(3, editor.getCursorColumn());
        assertEquals(1, editor.getUndoEntryCount());
        assertEquals("Format Document", editor.getUndoLabel());
        assertEquals(1, events);
        assertEquals(1, listener.changed);
        assertFalse(controller.isPending());
        assertTrue(editor.undo());
        assertEquals("ab\ncd", editor.getText());
        assertTrue(editor.redo());
        assertEquals("ABb\nCDd", editor.getText());
    }

    @Test public void emptyAndIdenticalResultsReportUnchangedWithoutTouchingSelectionOrHistory() {
        editor.setSelection(0, 1, 1, 1);
        int version = editor.getDocumentVersion();
        assertTrue(controller.formatDocument());
        provider.responses.peek().complete(new Array<CodeEditorTextEdit>());
        assertTrue(controller.formatDocument());
        provider.responses.peek().complete(Array.with(new CodeEditorTextEdit(0, 0, 1, 2, "ab\ncd")));
        assertEquals(2, listener.unchanged);
        assertEquals(0, listener.failed);
        assertOriginalUntouched(version, new CodeEditorTextRange(0, 1, 1, 1));
    }

    @Test public void unavailableModesAndOpenCompoundEditDoNotDispatch() {
        editor.setReadOnly(true);
        assertFalse(controller.formatDocument());
        editor.setReadOnly(false);
        editor.setDisabled(true);
        assertFalse(controller.formatDocument());
        editor.setDisabled(false);
        editor.setPasswordMode(true);
        assertFalse(controller.formatDocument());
        editor.setPasswordMode(false);
        editor.beginCompoundEdit("Host edit");
        try {
            assertFalse(controller.formatDocument());
        } finally {
            editor.endCompoundEdit();
        }
        assertFalse(controller.formatSelection());
        assertEquals(0, provider.requests.size);
        assertFalse(controller.isPending());
    }

    @Test public void changedEditorModeSilentlyRejectsPendingAnswers() {
        for (int mode = 0; mode < 3; mode++) {
            assertTrue(controller.formatDocument());
            setMode(mode, true);
            provider.responses.peek().complete(replaceFirst("A"));
            assertFalse(controller.isPending());
            assertEquals("ab\ncd", editor.getText());
            setMode(mode, false);
        }
        assertEquals(0, listener.total());
        assertEquals(0, editor.getUndoEntryCount());
        assertEquals(0, events);
    }

    @Test public void documentFormattingAcceptsCaretMovementAndMapsTheLiveCaret() {
        assertTrue(controller.formatDocument());
        editor.setCursorPosition(1, 2);
        provider.responses.first().complete(replaceFirst("ABC"));
        assertEquals("ABCb\ncd", editor.getText());
        assertEquals(1, editor.getCursorLine());
        assertEquals(2, editor.getCursorColumn());
        assertEquals(1, listener.changed);
    }

    @Test public void selectionFormattingRequiresTheLiveRangeToMatch() {
        editor.setSelection(0, 0, 0, 1);
        assertTrue(controller.formatSelection());
        editor.setSelection(0, 1, 0, 2);
        provider.responses.first().complete(replaceFirst("A"));
        assertEquals("ab\ncd", editor.getText());
        assertEquals(new CodeEditorTextRange(0, 1, 0, 2), editor.getSelection());
        assertFalse(controller.isPending());
        assertEquals(0, listener.total());

        assertTrue(controller.formatSelection());
        editor.setSelection(0, 1, 0, 2);
        provider.responses.peek().complete(Array.with(new CodeEditorTextEdit(0, 1, 0, 2, "B")));
        assertEquals("aB\ncd", editor.getText());
        assertEquals("Format Selection", editor.getUndoLabel());
        assertEquals(1, listener.changed);
    }

    @Test public void newestRequestWinsAndRejectedSelectionRequestAlsoCancelsItsPredecessor() {
        assertTrue(controller.formatDocument());
        CodeFormattingResponse first = provider.responses.first();
        assertTrue(controller.formatDocument());
        first.complete(replaceFirst("OLD"));
        assertEquals("ab\ncd", editor.getText());
        assertTrue(controller.isPending());
        provider.responses.peek().complete(replaceFirst("A"));
        assertEquals("Ab\ncd", editor.getText());
        assertEquals(1, listener.changed);

        assertTrue(controller.formatDocument());
        CodeFormattingResponse cancelled = provider.responses.peek();
        assertFalse(controller.formatSelection());
        assertTrue(cancelled.isCancelled());
        cancelled.complete(replaceFirst("OLD"));
        assertEquals("Ab\ncd", editor.getText());
        assertFalse(controller.isPending());
    }

    @Test public void textChangesAndUndoBackToIdenticalTextStillInvalidateOldCoordinates() {
        assertTrue(controller.formatDocument());
        CodeFormattingResponse response = provider.responses.first();
        int version = editor.getDocumentVersion();
        editor.insertTextAtCursor("X");
        assertTrue(editor.undo());
        assertEquals("ab\ncd", editor.getText());
        assertNotEquals(version, editor.getDocumentVersion());
        response.complete(replaceFirst("A"));
        assertEquals("ab\ncd", editor.getText());
        assertFalse(controller.isPending());
        assertEquals(0, listener.total());
    }

    @Test public void cancelAndTerminalIdempotentDisposeIgnoreLateCallbacks() {
        assertTrue(controller.formatDocument());
        CodeFormattingResponse cancelled = provider.responses.peek();
        controller.cancel();
        assertTrue(cancelled.isCancelled());
        cancelled.complete(replaceFirst("A"));
        cancelled.fail("late");
        assertFalse(controller.isPending());
        assertTrue(controller.formatDocument());
        CodeFormattingResponse disposed = provider.responses.peek();
        controller.dispose();
        controller.dispose();
        assertTrue(controller.isDisposed());
        assertTrue(disposed.isCancelled());
        disposed.complete(replaceFirst("A"));
        assertFalse(controller.formatDocument());
        assertFalse(controller.formatSelection());
        assertEquals(2, provider.requests.size);
        assertEquals("ab\ncd", editor.getText());
        assertEquals(0, listener.total());
    }

    @Test public void replacingProviderCancelsOldAnswerAndNullProviderDoesNotReplaceIt() {
        assertTrue(controller.formatDocument());
        CodeFormattingResponse old = provider.responses.first();
        RecordingProvider replacement = new RecordingProvider();
        controller.setProvider(replacement);
        assertSame(replacement, controller.getProvider());
        assertTrue(old.isCancelled());
        old.complete(replaceFirst("OLD"));
        assertTrue(controller.formatDocument());
        try {
            controller.setProvider(null);
            fail("null provider must be rejected");
        } catch (IllegalArgumentException expected) {
            assertSame(replacement, controller.getProvider());
            assertTrue(controller.isPending());
        }
        replacement.responses.first().complete(replaceFirst("A"));
        assertEquals("Ab\ncd", editor.getText());
    }

    @Test public void firstTerminalCallbackWinsForSuccessAndFailure() {
        assertTrue(controller.formatDocument());
        CodeFormattingResponse complete = provider.responses.peek();
        complete.complete(replaceFirst("A"));
        complete.complete(replaceFirst("WRONG"));
        complete.fail("late failure");
        assertTrue(complete.isCancelled());
        assertEquals("Ab\ncd", editor.getText());
        assertEquals(1, listener.changed);
        assertEquals(0, listener.failed);

        assertTrue(controller.formatDocument());
        CodeFormattingResponse failure = provider.responses.peek();
        failure.fail("formatter unavailable");
        failure.complete(replaceFirst("WRONG"));
        failure.fail("duplicate");
        assertEquals(1, listener.failed);
        assertEquals("formatter unavailable", listener.lastMessage);
        assertEquals("Ab\ncd", editor.getText());
        assertFalse(controller.isPending());
    }

    @Test public void invalidBatchesFailAtomicallyBeforeFiltering() {
        int[] calls = {0};
        editor.setInputFilter((source, character) -> { calls[0]++; return true; });
        assertRejected(null);
        assertRejected(Array.with((CodeEditorTextEdit) null));
        assertRejected(Array.with(new CodeEditorTextEdit(-1, 0, 0, 0, "x")));
        assertRejected(Array.with(new CodeEditorTextEdit(0, 2, 0, 1, "x")));
        assertRejected(Array.with(new CodeEditorTextEdit(9, 0, 9, 1, "x")));
        assertRejected(Array.with(new CodeEditorTextEdit(0, 0, 0, 9, "x")));
        assertRejected(Array.with(new CodeEditorTextEdit(0, 0, 1, 1, "x"),
            new CodeEditorTextEdit(0, 1, 1, 2, "y")));
        assertEquals(0, calls[0]);
        assertEquals(7, listener.failed);
    }

    @Test public void selectionScopeAndSurrogateBoundariesAreValidatedBeforeAnyEdit() {
        editor.setSelection(0, 1, 1, 1);
        assertTrue(controller.formatSelection());
        provider.responses.peek().complete(Array.with(
            new CodeEditorTextEdit(0, 1, 0, 2, "B"), new CodeEditorTextEdit(1, 1, 1, 2, "D")));
        assertEquals("ab\ncd", editor.getText());
        assertEquals(new CodeEditorTextRange(0, 1, 1, 1), editor.getSelection());
        assertEquals(1, listener.failed);
        assertEquals(0, editor.getUndoEntryCount());
        editor.setText("a\uD83D\uDE00b\ncd");
        events = 0;
        assertTrue(controller.formatDocument());
        provider.responses.peek().complete(Array.with(new CodeEditorTextEdit(0, 1, 0, 2, "x")));
        assertEquals("a\uD83D\uDE00b\ncd", editor.getText());
        assertEquals(2, listener.failed);
        assertEquals(0, events);
        assertEquals(0, editor.getUndoEntryCount());
    }

    @Test public void exactFilteringIsSinglePassAndOneRejectedCharacterRejectsTheWholeBatch() {
        int[] calls = {0};
        editor.setInputFilter((source, character) -> { calls[0]++; return character != 'Z'; });
        assertTrue(controller.formatDocument());
        provider.responses.peek().complete(Array.with(
            new CodeEditorTextEdit(0, 0, 0, 1, "XY"), new CodeEditorTextEdit(1, 0, 1, 1, "Z")));
        assertEquals(3, calls[0]);
        assertEquals("ab\ncd", editor.getText());
        assertEquals(1, listener.failed);
        assertEquals(0, editor.getUndoEntryCount());
        assertEquals(0, events);

        editor.setInputFilter((source, character) -> { calls[0]++; return true; });
        calls[0] = 0;
        assertTrue(controller.formatDocument());
        provider.responses.peek().complete(Array.with(
            new CodeEditorTextEdit(0, 0, 0, 1, "XY"), new CodeEditorTextEdit(1, 0, 1, 1, "Z")));
        assertEquals(3, calls[0]);
        assertEquals("XYb\nZd", editor.getText());
        assertEquals(1, listener.changed);
    }

    @Test public void nonCanonicalNewlinesFailRatherThanSilentlyNormalizing() {
        assertTrue(controller.formatDocument());
        provider.responses.first().complete(replaceFirst("x\r\ny"));
        assertEquals("ab\ncd", editor.getText());
        assertEquals(1, listener.failed);
        assertFalse(controller.isPending());
        assertEquals(0, editor.getUndoEntryCount());
        assertEquals(0, events);
    }

    @Test public void filterCancellationDisposalAndModeChangesPreventThePreparedCommit() {
        for (int action = 0; action < 4; action++) {
            CodeEditor target = createEditor("ab\ncd");
            RecordingProvider deferred = new RecordingProvider();
            CodeFormattingController service = new CodeFormattingController(target, deferred);
            RecordingListener status = new RecordingListener();
            service.setListener(status);
            final int operation = action;
            target.setSelection(0, 1, 1, 1);
            int version = target.getDocumentVersion();
            target.setInputFilter((source, character) -> {
                if (operation == 0) service.cancel();
                else if (operation == 1) service.dispose();
                else if (operation == 2) source.setPasswordMode(true);
                else source.setReadOnly(true);
                return true;
            });
            try {
                assertTrue(service.formatDocument());
                deferred.responses.first().complete(replaceFirst("A"));
                assertEquals("ab\ncd", target.getText());
                assertEquals(version, target.getDocumentVersion());
                assertEquals(new CodeEditorTextRange(0, 1, 1, 1), target.getSelection());
                assertEquals(0, target.getUndoEntryCount());
                assertFalse(service.isPending());
                assertEquals(0, status.total());
            } finally {
                service.dispose();
            }
        }
    }

    @Test public void filterStartingNewRequestRejectsOldCommitWithoutClearingNewPendingState() {
        boolean[] once = {false};
        editor.setInputFilter((source, character) -> {
            if (!once[0]) {
                once[0] = true;
                assertTrue(controller.formatDocument());
            }
            return true;
        });
        assertTrue(controller.formatDocument());
        provider.responses.first().complete(replaceFirst("OLD"));
        assertEquals("ab\ncd", editor.getText());
        assertEquals(2, provider.requests.size);
        assertTrue(controller.isPending());
        assertEquals(0, listener.total());
        provider.responses.peek().complete(replaceFirst("A"));
        assertEquals("Ab\ncd", editor.getText());
        assertFalse(controller.isPending());
        assertEquals(1, listener.changed);
    }

    @Test public void selectionMovedByFilterCannotCommitIntoTheOldSelectedRange() {
        editor.setSelection(0, 0, 0, 1);
        assertTrue(controller.formatSelection());
        editor.setInputFilter((source, character) -> {
            source.setSelection(1, 0, 1, 1);
            return true;
        });
        provider.responses.first().complete(replaceFirst("A"));
        assertEquals("ab\ncd", editor.getText());
        assertEquals(new CodeEditorTextRange(1, 0, 1, 1), editor.getSelection());
        assertEquals(0, editor.getUndoEntryCount());
        assertFalse(controller.isPending());
        assertEquals(0, listener.total());
    }

    @Test public void providerOwnedArrayMutationDuringFilteringCannotChangeTheCopiedBatch() {
        Array<CodeEditorTextEdit> edits = replaceFirst("A");
        editor.setInputFilter((source, character) -> {
            edits.clear();
            edits.add(new CodeEditorTextEdit(1, 0, 1, 2, "WRONG"));
            return true;
        });
        assertTrue(controller.formatDocument());
        provider.responses.first().complete(edits);
        assertEquals("Ab\ncd", editor.getText());
        assertEquals(1, listener.changed);
        assertEquals(1, editor.getUndoEntryCount());
    }

    @Test public void providerExceptionPropagatesAndRetiresItsPendingRequest() {
        RuntimeException failure = new IllegalStateException("provider failed");
        controller.setProvider((request, response) -> { throw failure; });
        try {
            controller.formatDocument();
            fail("provider exception must propagate");
        } catch (RuntimeException caught) {
            assertSame(failure, caught);
        }
        assertFalse(controller.isPending());
        assertEquals(0, listener.total());
        assertEquals("ab\ncd", editor.getText());
        controller.setProvider(provider);
        assertTrue(controller.formatDocument());
        provider.responses.peek().complete(replaceFirst("A"));
        assertEquals("Ab\ncd", editor.getText());
    }

    @Test public void completeThenThrowPreservesTheNewRequestStartedByTheCompletionListener() {
        RuntimeException failure = new IllegalStateException("after completion");
        RecordingProvider newer = new RecordingProvider();
        controller.setListener(new RecordingListener() {
            @Override public void onFormatted(CodeFormattingController source, CodeFormattingRequest request,
                                              boolean changed) {
                super.onFormatted(source, request, changed);
                source.setProvider(newer);
                assertTrue(source.formatDocument());
            }
        });
        controller.setProvider((request, response) -> {
            response.complete(new Array<CodeEditorTextEdit>());
            throw failure;
        });
        try {
            controller.formatDocument();
            fail("exception after delivery must still propagate");
        } catch (RuntimeException caught) {
            assertSame(failure, caught);
        }
        assertEquals(1, newer.requests.size);
        assertTrue(controller.isPending());
        controller.setListener(listener);
        newer.responses.first().complete(replaceFirst("A"));
        assertEquals("Ab\ncd", editor.getText());
        assertEquals(1, listener.changed);
    }

    @Test public void contentListenerCanStartNewRequestWhileThePreviousCommitCloses() {
        boolean[] once = {false};
        editor.addContentListener((source, event) -> {
            if (!once[0]) {
                once[0] = true;
                assertTrue(controller.formatDocument());
            }
        });
        assertTrue(controller.formatDocument());
        provider.responses.first().complete(replaceFirst("A"));
        assertEquals("Ab\ncd", editor.getText());
        assertEquals(2, provider.requests.size);
        assertEquals("Ab\ncd", provider.requests.peek().text);
        assertTrue(controller.isPending());
        assertEquals(0, listener.total());
        provider.responses.peek().complete(new Array<CodeEditorTextEdit>());
        assertFalse(controller.isPending());
        assertEquals(1, listener.unchanged);
    }

    @Test public void deferredTypedContentIsFlushedBeforeDispatchingAnUpdatedSnapshot() {
        assertTrue(keyTyped(editor, 'x'));
        assertEquals("xab\ncd", editor.getText());
        assertEquals(0, events);
        assertTrue(controller.formatDocument());
        assertEquals(1, events);
        assertEquals(1, provider.requests.size);
        assertEquals("xab\ncd", provider.requests.first().text);
        assertEquals(editor.getDocumentVersion(), provider.requests.first().documentVersion);
        assertTrue(controller.isPending());
        provider.responses.first().complete(replaceFirst("X"));
        assertEquals("Xab\ncd", editor.getText());
        assertEquals(1, listener.changed);
    }

    @Test public void requestStartedDuringDeferredFlushSupersedesTheOuterRequest() {
        boolean[] once = {false};
        editor.addContentListener((source, event) -> {
            if (!once[0]) {
                once[0] = true;
                assertTrue(controller.formatDocument());
            }
        });
        assertTrue(keyTyped(editor, 'x'));
        assertFalse(controller.formatDocument());
        assertEquals(1, provider.requests.size);
        assertEquals("xab\ncd", provider.requests.first().text);
        assertTrue(controller.isPending());
        provider.responses.first().complete(replaceFirst("X"));
        assertEquals("Xab\ncd", editor.getText());
        assertEquals(1, listener.changed);
    }

    @Test public void earlierHostListenerCanRequestFormattingDuringFlushWithoutLaterListenerCancellingIt() {
        CodeEditor target = createEditor("ab\ncd");
        CodeFormattingController[] service = new CodeFormattingController[1];
        boolean[] once = {false};
        target.addContentListener((source, event) -> {
            if (!once[0]) {
                once[0] = true;
                assertTrue(service[0].formatDocument());
            }
        });
        RecordingProvider deferred = new RecordingProvider();
        service[0] = new CodeFormattingController(target, deferred);
        try {
            assertTrue(keyTyped(target, 'x'));
            assertFalse(service[0].formatDocument());
            assertEquals(1, deferred.requests.size);
            assertEquals("xab\ncd", deferred.requests.first().text);
            assertTrue(service[0].isPending());
            assertFalse(deferred.responses.first().isCancelled());
            deferred.responses.first().complete(replaceFirst("X"));
            assertEquals("Xab\ncd", target.getText());
            assertFalse(service[0].isPending());
        } finally {
            service[0].dispose();
        }
    }

    @Test public void queryingProgressInsideOwnContentNotificationDoesNotCancelSuccessfulCompletion() {
        int[] queries = {0};
        editor.addContentListener((source, event) -> {
            queries[0]++;
            assertTrue(controller.isPending());
        });
        assertTrue(controller.formatDocument());
        provider.responses.first().complete(replaceFirst("A"));
        assertEquals("Ab\ncd", editor.getText());
        assertEquals(1, queries[0]);
        assertEquals(1, listener.changed);
        assertEquals(0, listener.failed);
        assertFalse(controller.isPending());
    }

    private void assertRejected(Array<CodeEditorTextEdit> edits) {
        int version = editor.getDocumentVersion();
        CodeEditorTextRange selection = editor.getSelection();
        int failures = listener.failed;
        assertTrue(controller.formatDocument());
        provider.responses.peek().complete(edits);
        assertEquals(failures + 1, listener.failed);
        assertNotNull(listener.lastMessage);
        assertFalse(listener.lastMessage.isEmpty());
        assertFalse(controller.isPending());
        assertOriginalUntouched(version, selection);
    }

    private void assertOriginalUntouched(int version, CodeEditorTextRange selection) {
        assertEquals("ab\ncd", editor.getText());
        assertEquals(version, editor.getDocumentVersion());
        assertEquals(selection, editor.getSelection());
        assertEquals(0, editor.getUndoEntryCount());
        assertEquals(0, events);
    }

    private void setMode(int mode, boolean enabled) {
        if (mode == 0) editor.setReadOnly(enabled);
        else if (mode == 1) editor.setDisabled(enabled);
        else editor.setPasswordMode(enabled);
    }

    private static Array<CodeEditorTextEdit> replaceFirst(String replacement) {
        return Array.with(new CodeEditorTextEdit(0, 0, 0, 1, replacement));
    }

    private static class RecordingProvider implements CodeFormattingProvider {
        final Array<CodeFormattingRequest> requests = new Array<>();
        final Array<CodeFormattingResponse> responses = new Array<>();
        @Override public void format(CodeFormattingRequest request, CodeFormattingResponse response) {
            requests.add(request);
            responses.add(response);
        }
    }

    private static class RecordingListener implements CodeFormattingListener {
        int changed;
        int unchanged;
        int failed;
        String lastMessage;
        @Override public void onFormatted(CodeFormattingController source, CodeFormattingRequest request,
                                          boolean changed) {
            if (changed) this.changed++;
            else unchanged++;
        }
        @Override public void onFormattingFailed(CodeFormattingController source, CodeFormattingRequest request,
                                                 String message) {
            failed++;
            lastMessage = message;
        }
        int total() { return changed + unchanged + failed; }
    }
}
