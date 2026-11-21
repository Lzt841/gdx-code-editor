package com.lzt841.editor.completion;

import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.utils.Array;
import com.lzt841.editor.CodeEditor;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public class CodeCompletionControllerUnicodeTest {
    @Test
    public void prioritiesSortAcrossTheFullIntegerRange() {
        CodeEditor editor = createEditor();
        final CodeCompletionItem highest = new CodeCompletionItem("highest", null, null, null,
            CodeCompletionItemKind.TEXT, Integer.MAX_VALUE, null, null);
        final CodeCompletionItem lowest = new CodeCompletionItem("lowest", null, null, null,
            CodeCompletionItemKind.TEXT, Integer.MIN_VALUE, null, null);
        CodeCompletionController controller = new CodeCompletionController(editor,
            new CodeCompletionProvider() {
                @Override
                public void provide(CodeCompletionRequest request, CodeCompletionResponse response) {
                    Array<CodeCompletionItem> items = new Array<CodeCompletionItem>();
                    items.add(lowest);
                    items.add(highest);
                    response.complete(items);
                }
            });

        controller.triggerManually();

        assertSame(highest, controller.getPopup().getItems().first());
        assertSame(lowest, controller.getPopup().getItems().peek());
    }

    @Test
    public void candidateFilteringFoldsSupplementaryLetterCase() {
        CodeEditor editor = createEditor();
        String upper = new String(Character.toChars(0x10400));
        String lower = new String(Character.toChars(0x10428));
        editor.setText(upper);
        editor.setCursorPosition(0, upper.length());
        CodeCompletionController controller = new CodeCompletionController(editor,
            new CodeCompletionProvider() {
                @Override
                public void provide(CodeCompletionRequest request, CodeCompletionResponse response) {
                    Array<CodeCompletionItem> items = new Array<CodeCompletionItem>();
                    items.add(CodeCompletionItem.of(lower + "foo"));
                    response.complete(items);
                }
            });

        controller.triggerManually();

        assertTrue(controller.isOpen());
        assertTrue(controller.getPopup().getItems().size == 1);
    }

    @Test
    public void delayedProviderResultIsDiscardedAfterDocumentChanges() {
        final CodeCompletionResponse[] pending = new CodeCompletionResponse[1];
        CodeEditor editor = createEditor();
        editor.setText("x");
        CodeCompletionController controller = new CodeCompletionController(editor,
            new CodeCompletionProvider() {
                @Override
                public void provide(CodeCompletionRequest request, CodeCompletionResponse response) {
                    pending[0] = response;
                }
            });

        controller.triggerManually();
        assertFalse(controller.isOpen());
        assertTrue(editor.insertTextAtCursor("x"));
        Array<CodeCompletionItem> staleItems = new Array<CodeCompletionItem>();
        staleItems.add(CodeCompletionItem.of("stale"));
        pending[0].complete(staleItems);

        assertFalse(controller.isOpen());
        assertTrue(controller.getPopup().getItems().size == 0);
    }

    @Test
    public void delayedProviderResultIsDiscardedAfterCaretMoves() {
        final CodeCompletionResponse[] pending = new CodeCompletionResponse[1];
        CodeEditor editor = createEditor();
        editor.setText("x");
        CodeCompletionController controller = new CodeCompletionController(editor,
            new CodeCompletionProvider() {
                @Override
                public void provide(CodeCompletionRequest request, CodeCompletionResponse response) {
                    pending[0] = response;
                }
            });

        controller.triggerManually();
        editor.setCursorPosition(0, 1);
        Array<CodeCompletionItem> staleItems = new Array<CodeCompletionItem>();
        staleItems.add(CodeCompletionItem.of("stale"));
        pending[0].complete(staleItems);

        assertFalse(controller.isOpen());
        assertTrue(controller.getPopup().getItems().size == 0);
    }

    @Test
    public void supplementaryLetterKeepsPopupOpenAndEmojiClosesIt() {
        CodeEditor editor = createEditor();
        CodeCompletionController controller = new CodeCompletionController(editor,
            new CodeCompletionProvider() {
                @Override
                public void provide(CodeCompletionRequest request, CodeCompletionResponse response) {
                    Array<CodeCompletionItem> items = new Array<CodeCompletionItem>();
                    items.add(CodeCompletionItem.of("candidate"));
                    response.complete(items);
                }
            });

        controller.triggerManually();
        assertTrue(controller.isOpen());

        String supplementaryLetter = new String(Character.toChars(0x10400));
        editor.setText(supplementaryLetter);
        editor.setCursorPosition(0, supplementaryLetter.length());
        controller.onCharacterTyped(supplementaryLetter.charAt(0));
        assertTrue(controller.isOpen());
        controller.onCharacterTyped(supplementaryLetter.charAt(1));
        assertTrue(controller.isOpen());

        String emoji = "\uD83D\uDE42";
        editor.setText(emoji);
        editor.setCursorPosition(0, emoji.length());
        controller.onCharacterTyped(emoji.charAt(0));
        assertTrue(controller.isOpen());
        controller.onCharacterTyped(emoji.charAt(1));
        assertFalse(controller.isOpen());
    }

    private CodeEditor createEditor() {
        BitmapFont.BitmapFontData fontData = new BitmapFont.BitmapFontData();
        fontData.lineHeight = 16f;
        fontData.spaceXadvance = 8f;
        CodeEditor.CodeEditorStyle style = new CodeEditor.CodeEditorStyle();
        style.font = new BitmapFont(fontData, new TextureRegion(), false);
        CodeEditor editor = new CodeEditor(style);
        editor.setSize(640, 480);
        return editor;
    }
}
