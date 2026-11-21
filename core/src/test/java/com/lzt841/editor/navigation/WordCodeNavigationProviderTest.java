package com.lzt841.editor.navigation;

import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.utils.Array;
import com.lzt841.editor.CodeEditor;
import com.lzt841.editor.CodeEditorTextRange;
import com.lzt841.editor.input.CodeEditorInteractionMode;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.Timeout;

import static org.junit.Assert.*;

public class WordCodeNavigationProviderTest {
    @Rule
    public final Timeout timeout = Timeout.seconds(30);

    private static final String LETTER = new String(Character.toChars(0x10400));
    private CodeEditor editor;
    private CodeNavigationController controller;
    private String failure;

    @Before
    public void createEditor() {
        BitmapFont.BitmapFontData data = new BitmapFont.BitmapFontData();
        data.lineHeight = 16f;
        data.spaceXadvance = 8f;
        CodeEditor.CodeEditorStyle style = new CodeEditor.CodeEditorStyle();
        style.font = new BitmapFont(data, new TextureRegion(), false);
        editor = new CodeEditor(style);
        editor.setInteractionMode(CodeEditorInteractionMode.MOUSE);
        editor.setOnscreenKeyboard(visible -> { });
        editor.setSize(640, 480);
        controller = new CodeNavigationController(editor, new WordCodeNavigationProvider().setSkipDeclarationLookup(true));
        controller.setListener(new CodeNavigationListener() {
            @Override
            public void onRenameFailed(CodeNavigationController source, String message) {
                failure = message;
            }
        });
    }

    @Test
    public void supplementaryLettersAndDollarSignsAreWholeWordBoundaries() {
        editor.setText("x " + LETTER + "x x" + LETTER + " $x x$ x");
        Array<CodeEditorTextRange> found = WordCodeNavigationProvider.findWholeWordOccurrences(editor, "x");
        assertEquals(2, found.size);
        assertEquals(0, found.first().startColumn);
        assertEquals(editor.getLineLength(0) - 1, found.peek().startColumn);
    }

    @Test
    public void findsCompleteSupplementaryIdentifiersWithoutPartialMatches() {
        String identifier = LETTER + "value";
        editor.setText(identifier + " " + identifier + "Suffix\n" + identifier);
        Array<CodeEditorTextRange> found = WordCodeNavigationProvider.findWholeWordOccurrences(editor, identifier);
        assertEquals(2, found.size);
        assertEquals(new CodeEditorTextRange(0, 0, 0, identifier.length()), found.first());
        assertEquals(new CodeEditorTextRange(1, 0, 1, identifier.length()), found.peek());
    }

    @Test
    public void neverReturnsHalfOfASurrogatePair() {
        editor.setText(LETTER + " " + LETTER);
        assertEquals(0, WordCodeNavigationProvider.findWholeWordOccurrences(editor, LETTER.substring(0, 1)).size);
        assertEquals(0, WordCodeNavigationProvider.findWholeWordOccurrences(editor, LETTER.substring(1)).size);
        assertEquals(2, WordCodeNavigationProvider.findWholeWordOccurrences(editor, LETTER).size);
    }

    @Test
    public void supplementaryRenameIsOneUndoStepAndKeepsBoundaries() {
        editor.setText("alpha alpha alphabet");
        editor.setCursorPosition(0, 2);
        String name = LETTER + "_$2";
        controller.renameTo(name);
        assertNull(failure);
        assertEquals(name + " " + name + " alphabet", editor.getText());
        assertEquals(1, editor.getUndoEntryCount());
        assertTrue(editor.undo());
        assertEquals("alpha alpha alphabet", editor.getText());
    }

    @Test
    public void dollarIdentifiersCanBeRenamedWithoutChangingLongerNames() {
        editor.setText("$value value $valueLong $value");
        editor.setCursorPosition(0, 2);
        controller.renameTo("$renamed");
        assertNull(failure);
        assertEquals("$renamed value $valueLong $renamed", editor.getText());
    }

    @Test
    public void identifierLookupIncludesDollarWithoutChangingWholeWordSearch() {
        editor.setText("$value value");
        assertEquals(new CodeEditorTextRange(0, 0, 0, 6), editor.getWordRangeAt(0, 2));
        assertEquals("$va", editor.getWordPrefixAt(0, 3));
        editor.setSearchText("value");
        editor.setSearchWholeWord(true);
        assertEquals(2, editor.getSearchMatchCount());
    }

    @Test
    public void invalidNamesIncludingSupplementaryLeadingDigitsAreRejected() {
        editor.setText("alpha alpha");
        editor.setCursorPosition(0, 2);
        String[] invalid = {new String(Character.toChars(0x1D7CE)) + "name", "\uD83D\uDE42name", "\uD801name", "a b"};
        for (String name : invalid) {
            failure = null;
            controller.renameTo(name);
            assertNotNull("expected invalid name to be rejected", failure);
            assertEquals("alpha alpha", editor.getText());
            assertEquals(0, editor.getUndoEntryCount());
        }
    }

    @Test
    public void defaultReferenceProviderRetainsSupplementaryIdentifiersForCycling() {
        String name = LETTER + "value";
        editor.setText(name + "\n" + name + "\n" + name + "Suffix");
        editor.setCursorPosition(0, 2);
        controller.findReferences();
        assertEquals(2, controller.getTargetCount());
        assertTrue(controller.navigateToNextTarget());
        assertEquals(1, editor.getCursorLine());
        assertTrue(controller.navigateToNextTarget());
        assertEquals(0, editor.getCursorLine());
    }
}
