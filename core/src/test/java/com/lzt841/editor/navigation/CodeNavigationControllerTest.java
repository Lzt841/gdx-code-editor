package com.lzt841.editor.navigation;

import com.badlogic.gdx.Input;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.utils.Array;
import com.lzt841.editor.CodeEditor;
import com.lzt841.editor.CodeEditorAction;
import com.lzt841.editor.CodeEditorTextEdit;
import com.lzt841.editor.CodeEditorTextRange;
import com.lzt841.editor.CodeKeymap;
import com.lzt841.editor.input.CodeEditorInteractionMode;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

public class CodeNavigationControllerTest {
    private static final String TEXT = "alpha beta\nalpha gamma\nalpha delta";
    private CodeEditor editor;
    private RecordingProvider provider;
    private RecordingListener listener;
    private CodeNavigationController controller;

    @Before
    public void createController() {
        BitmapFont.BitmapFontData data = new BitmapFont.BitmapFontData();
        data.lineHeight = 16f;
        data.spaceXadvance = 8f;
        CodeEditor.CodeEditorStyle style = new CodeEditor.CodeEditorStyle();
        style.font = new BitmapFont(data, new TextureRegion(), false);
        editor = new CodeEditor(style);
        editor.setInteractionMode(CodeEditorInteractionMode.MOUSE);
        editor.setOnscreenKeyboard(visible -> { });
        editor.setSize(640, 480);
        editor.setText(TEXT);
        editor.setCursorPosition(0, 2);
        provider = new RecordingProvider();
        listener = new RecordingListener();
        controller = new CodeNavigationController(editor, provider);
        controller.setListener(listener);
    }

    @Test
    public void retainedResultsWrapInBothDirectionsWithoutRequestingAgain() {
        int version = editor.getDocumentVersion();
        controller.findReferences();
        provider.navigationResponses.first().complete(targets());
        assertEquals(0, controller.getLastTargetIndex());
        assertTrue(controller.navigateToNextTarget());
        assertEquals(1, editor.getCursorLine());
        assertTrue(controller.navigateToNextTarget());
        assertEquals(2, editor.getCursorLine());
        assertTrue(controller.navigateToNextTarget());
        assertEquals(0, editor.getCursorLine());
        assertTrue(controller.navigateToPreviousTarget());
        assertEquals(2, editor.getCursorLine());
        assertEquals(2, controller.getLastTargetIndex());
        assertEquals(3, controller.getTargetCount());
        assertEquals(1, provider.navigationResponses.size);
        assertEquals(version, editor.getDocumentVersion());
        assertEquals(0, editor.getUndoEntryCount());
    }

    @Test
    public void hostCanDeferInitialNavigationAndStartAtEitherEnd() {
        listener.handleMultiple = true;
        controller.findReferences();
        provider.navigationResponses.first().complete(targets());
        assertEquals(-1, controller.getLastTargetIndex());
        assertEquals(2, editor.getCursorColumn());
        assertTrue(controller.navigateToPreviousTarget());
        assertEquals(2, controller.getLastTargetIndex());
        assertTrue(controller.navigateToNextTarget());
        assertEquals(0, controller.getLastTargetIndex());
    }

    @Test
    public void snapshotsAndListenerArraysCannotCorruptRetainedResults() {
        controller.setListener(new RecordingListener() {
            @Override
            public boolean onMultipleTargets(CodeNavigationController source, CodeNavigationRequest request,
                                             Array<CodeNavigationTarget> targets) {
                targets.clear();
                return false;
            }
        });
        controller.findReferences();
        Array<CodeNavigationTarget> supplied = targets();
        provider.navigationResponses.first().complete(supplied);
        supplied.clear();
        controller.getLastTargets().clear();
        assertEquals(3, controller.getTargetCount());
        assertEquals(0, controller.getLastTargetIndex());
        assertTrue(controller.navigateToNextTarget());
        assertEquals(1, editor.getCursorLine());
    }

    @Test
    public void editsExpireResultsWithoutDisturbingCaretOrSelection() {
        controller.findReferences();
        provider.navigationResponses.first().complete(targets());
        editor.insertTextAtCursor("X");
        editor.setSelection(0, 0, 0, 2);
        CodeEditorTextRange selection = editor.getSelection();
        assertFalse(controller.navigateToNextTarget());
        assertFalse(controller.navigateToPreviousTarget());
        assertEquals(0, controller.getTargetCount());
        assertEquals(-1, controller.getLastTargetIndex());
        assertEquals(selection, editor.getSelection());
    }

    @Test
    public void newRequestsClearPreviousResultsAndEmptyRepliesReportNoTarget() {
        controller.findReferences();
        provider.navigationResponses.first().complete(targets());
        controller.goToDefinition();
        assertEquals(0, controller.getTargetCount());
        assertEquals(-1, controller.getLastTargetIndex());
        provider.navigationResponses.peek().complete(null);
        assertEquals(1, listener.noTargetCount);
        assertEquals(0, controller.getLastTargets().size);
        assertFalse(controller.navigateToNextTarget());
    }

    @Test
    public void malformedAndNullTargetsAreSkippedIncludingSplitSurrogates() {
        editor.setText("A\uD83D\uDE42B\nalpha");
        controller.goToDefinition();
        CodeNavigationTarget valid = target(1);
        provider.navigationResponses.first().complete(Array.with(null,
            CodeNavigationTarget.inCurrentDocument(new CodeEditorTextRange(-1, 0, 0, 1)),
            CodeNavigationTarget.inCurrentDocument(new CodeEditorTextRange(0, 1, 0, 99)),
            CodeNavigationTarget.inCurrentDocument(new CodeEditorTextRange(0, 3, 0, 1)),
            CodeNavigationTarget.inCurrentDocument(new CodeEditorTextRange(0, 2, 0, 3)), valid));
        assertEquals(1, controller.getTargetCount());
        assertSame(valid, listener.lastNavigated);
        assertEquals(1, editor.getCursorLine());
        assertFalse(controller.navigateTo(CodeNavigationTarget.inCurrentDocument(
            new CodeEditorTextRange(0, 99, 0, 99))));
    }

    @Test
    public void unsupportedExternalTargetsAreSkippedWhileCycling() {
        listener.handleMultiple = true;
        controller.findReferences();
        provider.navigationResponses.first().complete(Array.with(externalTarget(), target(1)));
        assertTrue(controller.navigateToNextTarget());
        assertEquals(1, editor.getCursorLine());
        assertEquals(1, controller.getLastTargetIndex());
        assertEquals(1, listener.externalCount);
    }

    @Test
    public void automaticFallbackSkipsExternalTargetsTheHostCannotOpen() {
        controller.findReferences();
        provider.navigationResponses.first().complete(Array.with(externalTarget(), target(1)));
        assertEquals(1, editor.getCursorLine());
        assertEquals(1, controller.getLastTargetIndex());
        assertEquals(1, listener.externalCount);
    }

    @Test
    public void handledExternalTargetsParticipateInResultBrowsing() {
        controller.setListener(new RecordingListener() {
            @Override
            public boolean onMultipleTargets(CodeNavigationController source, CodeNavigationRequest request,
                                             Array<CodeNavigationTarget> targets) {
                return true;
            }

            @Override
            public boolean onNavigateToOtherDocument(CodeNavigationController source, CodeNavigationRequest request,
                                                     CodeNavigationTarget target) {
                return true;
            }
        });
        controller.findReferences();
        provider.navigationResponses.first().complete(Array.with(externalTarget(), target(1)));
        assertTrue(controller.navigateToNextTarget());
        assertEquals(0, controller.getLastTargetIndex());
        assertTrue(controller.navigateToNextTarget());
        assertEquals(1, controller.getLastTargetIndex());
        assertEquals(1, editor.getCursorLine());
    }

    @Test
    public void duplicateDestinationsDoNotTrapResultNavigation() {
        controller.findReferences();
        CodeNavigationTarget duplicate = target(0);
        provider.navigationResponses.first().complete(Array.with(duplicate, duplicate, target(2)));
        assertTrue(controller.navigateToNextTarget());
        assertEquals(1, controller.getLastTargetIndex());
        assertTrue(controller.navigateToNextTarget());
        assertEquals(2, controller.getLastTargetIndex());
        assertEquals(2, editor.getCursorLine());
    }

    @Test
    public void reentrantLocalNavigationKeepsTheFinalTargetIndex() {
        controller.setListener(new RecordingListener() {
            @Override
            public void onNavigated(CodeNavigationController source, CodeNavigationRequest request,
                                    CodeNavigationTarget target) {
                if (target.getLine() == 1) {
                    assertEquals(1, source.getLastTargetIndex());
                    source.navigateTo(target(2), request);
                }
            }
        });
        controller.findReferences();
        provider.navigationResponses.first().complete(targets());
        assertTrue(controller.navigateToNextTarget());
        assertEquals(2, editor.getCursorLine());
        assertEquals(2, controller.getLastTargetIndex());
    }

    @Test
    public void readOnlyNavigationExpandsFoldedTargets() {
        editor.setText("{\nalpha\n}");
        assertEquals(1, editor.collapseAllFolds());
        editor.setReadOnly(true);
        controller.goToDefinition();
        provider.navigationResponses.first().completeOne(target(1));
        assertEquals(1, editor.getCursorLine());
        assertFalse(editor.isFoldCollapsed(0));
        assertFalse(controller.requestRename());
    }

    @Test
    public void defaultResultShortcutsStayInControllerKeymap() {
        CodeKeymap map = controller.getKeymap();
        assertEquals(CodeEditorAction.NAVIGATE_NEXT_TARGET, map.actionForKeyDown(Input.Keys.F4, false, false, false));
        assertEquals(CodeEditorAction.NAVIGATE_PREVIOUS_TARGET, map.actionForKeyDown(Input.Keys.F4, false, true, false));
        assertNull(map.actionForKeyDown(Input.Keys.F4, true, false, false));
        assertNull(editor.getKeymap().actionForKeyDown(Input.Keys.F4, false, false, false));
        controller.findReferences();
        provider.navigationResponses.first().complete(targets());
        assertTrue(controller.handleKeyDown(Input.Keys.F4));
        assertEquals(1, controller.getLastTargetIndex());
        assertTrue(controller.performAction(CodeEditorAction.NAVIGATE_PREVIOUS_TARGET));
        assertEquals(0, controller.getLastTargetIndex());
    }

    @Test
    public void delayedNavigationIsIgnoredAfterDocumentChanges() {
        controller.goToDefinition();
        editor.insertTextAtCursor("X");
        provider.navigationResponses.first().completeOne(target(2));
        assertEquals(0, listener.navigatedCount);
        assertEquals(0, listener.noTargetCount);
        assertEquals(0, controller.getTargetCount());
        assertEquals(0, editor.getCursorLine());
        assertEquals(3, editor.getCursorColumn());
    }

    @Test
    public void delayedNavigationIsIgnoredAfterCaretMoves() {
        controller.goToDefinition();
        editor.setCursorPosition(1, 2);
        provider.navigationResponses.first().completeOne(target(2));
        assertEquals(1, editor.getCursorLine());
        assertEquals(2, editor.getCursorColumn());
        assertEquals(0, listener.navigatedCount);
    }

    @Test
    public void selectionChangeInvalidatesReplyEvenAtTheSameCaret() {
        controller.goToDefinition();
        editor.setSelection(0, 0, 0, 2);
        provider.navigationResponses.first().completeOne(target(2));
        assertEquals(new CodeEditorTextRange(0, 0, 0, 2), editor.getSelection());
        assertEquals(0, listener.navigatedCount);
    }

    @Test
    public void unchangedSelectionDoesNotBlockReply() {
        editor.setSelection(0, 0, 0, 2);
        controller.goToDefinition();
        provider.navigationResponses.first().completeOne(target(2));
        assertEquals(2, editor.getCursorLine());
        assertNull(editor.getSelection());
    }

    @Test
    public void onlyTheLatestRequestAndFirstDeliveryCanNavigate() {
        controller.goToDefinition();
        controller.goToDefinition();
        provider.navigationResponses.peek().completeOne(target(1));
        provider.navigationResponses.first().completeOne(target(2));
        provider.navigationResponses.peek().completeOne(target(0));
        assertEquals(1, editor.getCursorLine());
        assertEquals(1, listener.navigatedCount);
    }

    @Test
    public void directNavigationCancelsAnOlderPendingReply() {
        controller.goToDefinition();
        assertTrue(controller.navigateTo(target(1)));
        provider.navigationResponses.first().completeOne(target(2));
        assertEquals(1, editor.getCursorLine());
        assertEquals(1, listener.navigatedCount);
    }

    @Test
    public void uninstallCancelsDirectRequestsEvenBeforeInstallation() {
        controller.goToDefinition();
        controller.uninstall();
        provider.navigationResponses.first().completeOne(target(2));
        assertEquals(0, listener.navigatedCount);
        controller.renameTo("omega");
        controller.uninstall();
        provider.renameResponses.first().complete(renameEdits());
        assertEquals(TEXT, editor.getText());
        assertEquals(0, listener.appliedCount);
        assertEquals(0, controller.getTargetCount());
    }

    @Test
    public void disabledAndPasswordModesBlockRequestsAndLateReplies() {
        for (int mode = 1; mode <= 2; mode++) {
            controller.goToDefinition();
            setMode(mode, true);
            provider.navigationResponses.peek().completeOne(target(2));
            assertEquals(0, listener.navigatedCount);
            int requests = provider.navigationResponses.size;
            controller.goToDefinition();
            assertEquals(requests, provider.navigationResponses.size);
            assertFalse(controller.navigateTo(target(1)));
            assertFalse(controller.handleKeyDown(Input.Keys.F12));
            assertFalse(controller.requestRename());
            controller.renameTo("omega");
            assertEquals(0, provider.renameResponses.size);
            setMode(mode, false);
        }
        assertEquals(0, provider.prepareCount);
    }

    @Test
    public void staleOrForeignRequestContextCannotNavigateFromAResultsList() {
        controller.goToDefinition();
        CodeNavigationRequest request = provider.navigationRequests.first();
        editor.insertTextAtCursor("X");
        assertFalse(controller.navigateTo(target(2), request));
        CodeEditor other = new CodeEditor(editor.getStyle());
        CodeNavigationRequest foreign = new CodeNavigationRequest(other, CodeNavigationKind.DEFINITION,
            0, 0, editor.getDocumentVersion(), null, "");
        assertFalse(controller.navigateTo(target(1), foreign));
        assertEquals(0, listener.navigatedCount);
    }

    @Test
    public void renameRechecksEditorModeWhenEditsArrive() {
        for (int mode = 0; mode < 3; mode++) {
            controller.renameTo("omega");
            setMode(mode, true);
            provider.renameResponses.peek().complete(renameEdits());
            assertEquals(TEXT, editor.getText());
            assertEquals(0, editor.getUndoEntryCount());
            assertNotNull(listener.renameFailure);
            setMode(mode, false);
        }
        assertEquals(0, listener.appliedCount);
    }

    @Test
    public void renameAllowsCaretMovementButRejectsDocumentChanges() {
        controller.renameTo("omega");
        editor.setCursorPosition(2, 8);
        provider.renameResponses.first().complete(renameEdits());
        assertEquals("omega beta\nomega gamma\nomega delta", editor.getText());
        assertEquals(3, listener.appliedCount);
        assertTrue(editor.undo());
        assertEquals(TEXT, editor.getText());

        editor.setCursorPosition(0, 2);
        controller.renameTo("omega");
        editor.insertTextAtCursor("X");
        String edited = editor.getText();
        provider.renameResponses.peek().complete(renameEdits());
        assertEquals(edited, editor.getText());
        assertTrue(listener.renameFailure.contains("document changed"));
    }

    @Test
    public void rejectedNewRenameStillSupersedesPreviousRequest() {
        controller.renameTo("omega");
        controller.renameTo("");
        assertEquals(1, provider.renameResponses.size);
        provider.renameResponses.first().complete(renameEdits());
        assertEquals(TEXT, editor.getText());
        assertEquals(0, listener.appliedCount);
    }

    @Test
    public void invalidPreparedRenameRangesFailBeforeAskingForEdits() {
        provider.invalidPreparedRange = new CodeEditorTextRange(0, 99, 0, 100);
        assertFalse(controller.requestRename());
        controller.renameTo("omega");
        assertEquals(0, provider.renameResponses.size);
        assertNotNull(listener.renameFailure);
        assertEquals(TEXT, editor.getText());
    }

    @Test
    public void threeArgumentConstructorUsesProvidersRenameCapability() {
        CodeNavigationController direct = new CodeNavigationController(editor, provider, null);
        assertTrue(direct.isRenameSupported());
        direct.renameTo("omega");
        assertEquals(1, provider.renameResponses.size);
    }

    @Test
    public void reentrantMultipleTargetCallbackCannotRestoreOldResults() {
        controller.setListener(new RecordingListener() {
            @Override
            public boolean onMultipleTargets(CodeNavigationController source, CodeNavigationRequest request,
                                             Array<CodeNavigationTarget> targets) {
                source.goToDefinition();
                return false;
            }
        });
        controller.findReferences();
        provider.navigationResponses.first().complete(targets());
        assertEquals(2, provider.navigationResponses.size);
        assertEquals(0, editor.getCursorLine());
        assertEquals(2, editor.getCursorColumn());
        assertEquals(0, controller.getTargetCount());
        provider.navigationResponses.peek().completeOne(target(2));
        assertEquals(2, editor.getCursorLine());
    }

    @Test
    public void reentrantExternalCallbackStopsCyclingSupersededResults() {
        controller.setListener(new RecordingListener() {
            @Override
            public boolean onMultipleTargets(CodeNavigationController source, CodeNavigationRequest request,
                                             Array<CodeNavigationTarget> targets) {
                return true;
            }

            @Override
            public boolean onNavigateToOtherDocument(CodeNavigationController source, CodeNavigationRequest request,
                                                     CodeNavigationTarget target) {
                source.goToDefinition();
                return false;
            }
        });
        controller.findReferences();
        provider.navigationResponses.first().complete(Array.with(externalTarget(), target(1)));
        assertFalse(controller.navigateToNextTarget());
        assertEquals(0, editor.getCursorLine());
        assertEquals(2, editor.getCursorColumn());
        assertEquals(0, controller.getTargetCount());
    }

    private Array<CodeNavigationTarget> targets() {
        return Array.with(target(0), target(1), target(2));
    }

    private CodeNavigationTarget target(int line) {
        return CodeNavigationTarget.inCurrentDocument(new CodeEditorTextRange(line, 0, line, 1));
    }

    private CodeNavigationTarget externalTarget() {
        return new CodeNavigationTarget("other.java", new CodeEditorTextRange(0, 0, 0, 1), "", "");
    }

    private Array<CodeEditorTextEdit> renameEdits() {
        return Array.with(new CodeEditorTextEdit(new CodeEditorTextRange(0, 0, 0, 5), "omega"),
            new CodeEditorTextEdit(new CodeEditorTextRange(1, 0, 1, 5), "omega"),
            new CodeEditorTextEdit(new CodeEditorTextRange(2, 0, 2, 5), "omega"));
    }

    private void setMode(int mode, boolean enabled) {
        if (mode == 0) editor.setReadOnly(enabled);
        else if (mode == 1) editor.setDisabled(enabled);
        else editor.setPasswordMode(enabled);
    }

    private static class RecordingProvider implements CodeNavigationProvider, CodeRenameProvider {
        final Array<CodeNavigationRequest> navigationRequests = new Array<CodeNavigationRequest>();
        final Array<CodeNavigationResponse> navigationResponses = new Array<CodeNavigationResponse>();
        final Array<CodeRenameResponse> renameResponses = new Array<CodeRenameResponse>();
        int prepareCount;
        CodeEditorTextRange invalidPreparedRange;

        @Override
        public void provideTargets(CodeNavigationRequest request, CodeNavigationResponse response) {
            navigationRequests.add(request);
            navigationResponses.add(response);
        }

        @Override
        public CodeEditorTextRange prepareRename(CodeEditor source, int line, int column) {
            prepareCount++;
            return invalidPreparedRange != null ? invalidPreparedRange : source.getWordRangeAt(line, column);
        }

        @Override
        public void provideRenameEdits(CodeRenameRequest request, CodeRenameResponse response) {
            renameResponses.add(response);
        }
    }

    private static class RecordingListener implements CodeNavigationListener {
        boolean handleMultiple;
        int noTargetCount;
        int navigatedCount;
        int externalCount;
        int appliedCount;
        String renameFailure;
        CodeNavigationTarget lastNavigated;

        @Override
        public boolean onMultipleTargets(CodeNavigationController source, CodeNavigationRequest request,
                                         Array<CodeNavigationTarget> targets) {
            return handleMultiple;
        }

        @Override
        public void onNoTarget(CodeNavigationController source, CodeNavigationRequest request) {
            noTargetCount++;
        }

        @Override
        public boolean onNavigateToOtherDocument(CodeNavigationController source, CodeNavigationRequest request,
                                                 CodeNavigationTarget target) {
            externalCount++;
            return false;
        }

        @Override
        public void onNavigated(CodeNavigationController source, CodeNavigationRequest request, CodeNavigationTarget target) {
            navigatedCount++;
            lastNavigated = target;
        }

        @Override
        public void onRenameApplied(CodeNavigationController source, CodeRenameRequest request, int editCount) {
            appliedCount = editCount;
        }

        @Override
        public void onRenameFailed(CodeNavigationController source, String message) {
            renameFailure = message;
        }
    }
}
