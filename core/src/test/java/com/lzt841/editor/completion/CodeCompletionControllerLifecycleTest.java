package com.lzt841.editor.completion;

import com.badlogic.gdx.Input;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.scenes.scene2d.Group;
import com.badlogic.gdx.utils.Array;
import com.lzt841.editor.CodeEditor;
import com.lzt841.editor.CodeEditorAction;
import com.lzt841.editor.CodeEditorTextRange;
import com.lzt841.editor.input.CodeEditorInteractionMode;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

public class CodeCompletionControllerLifecycleTest {
    private CodeEditor editor;
    private CodeCompletionController controller;
    private Array<CodeCompletionResponse> responses;

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
        editor.setText("abc");
        editor.setCursorPosition(0, 2);
        responses = new Array<CodeCompletionResponse>();
        controller = new CodeCompletionController(editor, (request, response) -> responses.add(response));
    }

    @Test
    public void rejectsSelectionChangesEvenWhenCaretAndVersionAreUnchanged() {
        int version = editor.getDocumentVersion();
        controller.triggerManually();
        editor.setSelection(0, 0, 0, 2);
        CodeEditorTextRange selection = editor.getSelection();

        responses.first().complete(candidates());

        assertClosed();
        assertEquals(version, editor.getDocumentVersion());
        assertEquals(2, editor.getCursorColumn());
        assertEquals(selection, editor.getSelection());
    }

    @Test
    public void rejectsPendingResultsAfterBecomingReadOnly() {
        controller.triggerManually();
        editor.setReadOnly(true);
        responses.first().complete(candidates());
        assertClosed();
    }

    @Test
    public void rejectsPendingResultsAfterBecomingDisabled() {
        controller.triggerManually();
        editor.setDisabled(true);
        responses.first().complete(candidates());
        assertClosed();
    }

    @Test
    public void rejectsPendingResultsAfterEnteringPasswordMode() {
        controller.triggerManually();
        editor.setPasswordMode(true);
        responses.first().complete(candidates());
        assertClosed();
    }

    @Test
    public void blockedModesNeverCallProviderOrConsumeOpeningShortcut() {
        editor.getKeymap().bind(Input.Keys.SPACE, CodeEditorAction.COMPLETION_TRIGGER);
        for (int mode = 0; mode < 3; mode++) {
            setBlockedMode(mode, true);
            controller.triggerManually();
            controller.onCharacterTyped('.');
            assertFalse(controller.handleKeyDown(Input.Keys.SPACE));
            assertEquals(0, responses.size);
            assertClosed();
            setBlockedMode(mode, false);
        }
        assertTrue(controller.handleKeyDown(Input.Keys.SPACE));
        assertEquals(1, responses.size);
    }

    @Test
    public void unchangedRequestCanOpenAndCommitCompletion() {
        controller.triggerManually();
        responses.first().complete(candidates());

        assertTrue(controller.isOpen());
        assertTrue(controller.getPopup().isVisible());
        assertTrue(controller.acceptSelected());
        assertEquals("absolutec", editor.getText());
        assertClosed();
        assertTrue(editor.undo());
        assertEquals("abc", editor.getText());
    }

    @Test
    public void unchangedSelectionAllowsDelayedResult() {
        editor.setSelection(0, 0, 0, 2);
        controller.triggerManually();
        responses.first().complete(candidates());

        assertTrue(controller.isOpen());
        assertEquals(new CodeEditorTextRange(0, 0, 0, 2), editor.getSelection());
        assertEquals("abc", editor.getText());
    }

    @Test
    public void uninstallCancelsRequestsTriggeredBeforeInstallation() {
        controller.triggerManually();
        controller.uninstall();
        responses.first().complete(candidates());
        assertClosed();
    }

    @Test
    public void uninstallRemovesPopupAndDocumentationEvenWithoutInstallation() {
        Group overlays = new Group();
        overlays.addActor(controller.getPopup());
        overlays.addActor(controller.getDocumentationPanel());
        assertEquals(2, overlays.getChildren().size);

        controller.uninstall();
        controller.uninstall();

        assertEquals(0, overlays.getChildren().size);
        assertNull(controller.getPopup().getParent());
        assertNull(controller.getDocumentationPanel().getParent());
    }

    @Test
    public void readOnlyPopupClosesWithoutConsumingEditorKey() {
        controller.triggerManually();
        responses.first().complete(candidates());
        assertTrue(controller.isOpen());
        editor.setReadOnly(true);

        assertFalse(controller.handleKeyDown(Input.Keys.ENTER));

        assertClosed();
        assertEquals("abc", editor.getText());
    }

    @Test
    public void blockedModesRejectDirectPopupActivation() {
        for (int mode = 0; mode < 3; mode++) {
            controller.triggerManually();
            responses.peek().complete(candidates());
            assertTrue(controller.isOpen());
            CodeCompletionItem selected = controller.getPopup().getSelectedItem();
            setBlockedMode(mode, true);

            assertFalse(controller.accept(selected));

            assertClosed();
            assertEquals("abc", editor.getText());
            assertEquals(0, editor.getUndoEntryCount());
            setBlockedMode(mode, false);
        }
    }

    @Test
    public void newerRequestWinsEvenWhenOldResultArrivesLast() {
        controller.triggerManually();
        controller.triggerManually();
        CodeCompletionItem current = CodeCompletionItem.of("absolute");
        responses.get(1).complete(Array.with(current));
        responses.first().complete(Array.with(CodeCompletionItem.of("abandoned")));

        assertTrue(controller.isOpen());
        assertEquals(1, controller.getPopup().getItems().size);
        assertSame(current, controller.getPopup().getItems().first());
    }

    @Test
    public void providersCanObserveCancellationBeforeReturningResults() {
        controller.triggerManually();
        assertFalse(responses.first().isCancelled());
        controller.triggerManually();
        assertTrue(responses.first().isCancelled());
        assertFalse(responses.peek().isCancelled());
        editor.insertTextAtCursor("x");
        assertTrue(responses.peek().isCancelled());
        controller.triggerManually();
        controller.uninstall();
        assertTrue(responses.peek().isCancelled());
    }

    @Test
    public void emptyResponseClearsPreviouslyDisplayedCandidates() {
        controller.triggerManually();
        responses.first().complete(candidates());
        assertTrue(controller.isOpen());
        controller.triggerManually();

        responses.peek().complete(new Array<CodeCompletionItem>());

        assertClosed();
        assertFalse(controller.acceptSelected());
        assertEquals("abc", editor.getText());
    }

    @Test
    public void dismissedAndAlreadyDeliveredResponsesCannotReopenPopup() {
        controller.triggerManually();
        responses.first().complete(candidates());
        controller.dismiss();
        responses.first().complete(candidates());
        assertClosed();

        controller.triggerManually();
        responses.peek().complete(new Array<CodeCompletionItem>());
        responses.peek().complete(candidates());
        assertClosed();
    }

    private Array<CodeCompletionItem> candidates() {
        return Array.with(CodeCompletionItem.of("absolute"));
    }

    private void setBlockedMode(int mode, boolean blocked) {
        if (mode == 0) {
            editor.setReadOnly(blocked);
        } else if (mode == 1) {
            editor.setDisabled(blocked);
        } else {
            editor.setPasswordMode(blocked);
        }
    }

    private void assertClosed() {
        assertFalse(controller.isOpen());
        assertFalse(controller.getPopup().isVisible());
        assertEquals(0, controller.getPopup().getItems().size);
    }
}
