package com.lzt841.editor;

import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.utils.Array;
import com.lzt841.editor.input.CodeEditorInteractionMode;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

public class CodeEditorSearchTest {
    private CodeEditor editor;

    @Before
    public void createEditor() {
        BitmapFont.BitmapFontData fontData = new BitmapFont.BitmapFontData();
        fontData.lineHeight = 16f;
        fontData.spaceXadvance = 8f;
        CodeEditor.CodeEditorStyle style = new CodeEditor.CodeEditorStyle();
        style.font = new BitmapFont(fontData, new TextureRegion(), false);
        editor = new CodeEditor(style);
        editor.setInteractionMode(CodeEditorInteractionMode.MOUSE);
        editor.setSize(640, 480);
    }

    @Test
    public void selectionSearchChecksShiftedLinesBeforeReplacing() {
        editor.setText("header\ninside hit\noutside hit");
        editor.setSelection(1, 0, 1, 10);
        assertTrue(editor.setSearchRangeToSelection());
        editor.setSearchText("hit");
        assertEquals(1, editor.getSearchMatchCount());

        editor.setCursorPosition(0, 0);
        assertTrue(editor.insertTextAtCursor("intro\n"));
        assertEquals(0, editor.getSearchMatchCount());
        assertEquals(0, editor.replaceAllSearchMatches("changed"));
        assertEquals("intro\nheader\ninside hit\noutside hit", editor.getText());

        assertTrue(editor.undo());
        assertEquals(1, editor.getSearchMatchCount());
        assertEquals(1, editor.replaceAllSearchMatches("changed"));
        assertEquals("header\ninside changed\noutside hit", editor.getText());
        assertTrue(editor.undo());
        assertEquals("header\ninside hit\noutside hit", editor.getText());
    }

    @Test
    public void navigationStartsAtMovedCaretAndWraps() {
        editor.setText("hit\nhit x hit\nhit");
        editor.setSearchText("hit");
        assertTrue(editor.findNextSearchMatch());
        assertEquals(1, editor.getCurrentSearchMatchOrdinal());

        editor.setCursorPosition(1, 4);
        assertEquals(0, editor.getCurrentSearchMatchOrdinal());
        assertTrue(editor.findNextSearchMatch());
        assertEquals(3, editor.getCurrentSearchMatchOrdinal());
        assertTrue(editor.findNextSearchMatch());
        assertEquals(4, editor.getCurrentSearchMatchOrdinal());
        assertTrue(editor.findNextSearchMatch());
        assertEquals(1, editor.getCurrentSearchMatchOrdinal());
        assertTrue(editor.findPreviousSearchMatch());
        assertEquals(4, editor.getCurrentSearchMatchOrdinal());

        editor.setCursorPosition(1, 8);
        assertTrue(editor.findPreviousSearchMatch());
        assertEquals(2, editor.getCurrentSearchMatchOrdinal());
    }

    @Test
    public void regexCacheRecoversAndReplaceAllAcrossFoldsUndoesAsOneEdit() {
        String original = "{\nvalue = 12\nvalue = 34\n}";
        editor.setText(original);
        assertEquals(1, editor.collapseAllFolds());
        editor.setSearchRegexEnabled(true);
        editor.setSearchText("(");
        assertNotNull(editor.getSearchRegexError());
        assertEquals(0, editor.getSearchMatchCount());

        editor.setSearchText("value = (\\d+)");
        assertNull(editor.getSearchRegexError());
        assertEquals(2, editor.getSearchMatchCount());
        assertEquals(2, editor.replaceAllSearchMatches("number = $1"));
        assertFalse(editor.isFoldCollapsed(0));
        assertEquals("{\nnumber = 12\nnumber = 34\n}", editor.getText());
        assertTrue(editor.undo());
        assertEquals(original, editor.getText());
        assertEquals(2, editor.getSearchMatchCount());
    }

    @Test
    public void searchSkipsHalfOfSupplementaryCharacter() {
        String face = "\uD83D\uDE42";
        editor.setText(face + " " + face);
        editor.setSearchText(face.substring(1));
        assertEquals(0, editor.getSearchMatchCount());
        assertEquals(0, editor.replaceAllSearchMatches("x"));

        editor.setSearchRegexEnabled(true);
        editor.setSearchText("(?=" + face + ")|(" + face + ")");
        assertEquals(0, editor.getSearchMatchCount());

        editor.setSearchText("(" + face + ")");
        assertEquals(2, editor.replaceAllSearchMatches("[$1]"));
        assertEquals("[" + face + "] [" + face + "]", editor.getText());
    }

    @Test
    public void wholeWordSearchTreatsSupplementaryLettersAsWordCharacters() {
        String supplementaryLetter = new String(Character.toChars(0x10400));
        editor.setText(supplementaryLetter + "foo foo" + supplementaryLetter + " foo");
        editor.setSearchWholeWord(true);
        editor.setSearchText("foo");
        assertEquals(1, editor.getSearchMatchCount());

        editor.setSearchRegexEnabled(true);
        assertEquals(1, editor.getSearchMatchCount());

        String identifier = supplementaryLetter + "foo";
        editor.setText(identifier);
        assertEquals(identifier, editor.getWordPrefixAt(0, identifier.length()));
        CodeEditorTextRange word = editor.getWordRangeAt(0, 1);
        assertNotNull(word);
        assertEquals(identifier, editor.getTextRange(word));
        editor.setCursorPosition(0, 1);
        editor.selectWordAtCursor();
        assertEquals(identifier, editor.getSelectionText());
    }

    @Test
    public void selectionAndBackspaceDoNotSplitSupplementaryCharactersWithoutImages() {
        String face = "\uD83D\uDE42";
        editor.setText("a" + face + "b");
        editor.setSelection(0, 1, 0, 2);
        assertEquals(face, editor.getSelectionText());
        assertEquals(3, editor.getSelection().endColumn);
        assertTrue(editor.insertTextAtCursor("x"));
        assertEquals("axb", editor.getText());
        assertTrue(editor.undo());

        editor.setCursorPosition(0, 2);
        assertEquals(3, editor.getCursorColumn());
        editor.performAction(CodeEditorAction.BACKSPACE);
        assertEquals("ab", editor.getText());
    }

    @Test
    public void insertionAtReplacementStartIsStableInEitherInputOrder() {
        Array<CodeEditorTextEdit> edits = new Array<>();
        edits.add(new CodeEditorTextEdit(0, 0, 0, 2, "X"));
        edits.add(CodeEditorTextEdit.insert(0, 0, "^"));

        editor.setText("abc");
        assertTrue(editor.canApplyEdits(edits));
        assertTrue(editor.applyEdits(edits, "format"));
        assertEquals("^Xc", editor.getText());
        assertTrue(editor.undo());
        assertEquals("abc", editor.getText());
        assertTrue(editor.redo());
        assertEquals("^Xc", editor.getText());

        Array<CodeEditorTextEdit> reversed = new Array<>();
        reversed.add(CodeEditorTextEdit.insert(0, 0, "^"));
        reversed.add(new CodeEditorTextEdit(0, 0, 0, 2, "X"));
        editor.setText("abc");
        assertTrue(editor.canApplyEdits(reversed));
        assertTrue(editor.applyEdits(reversed));
        assertEquals("^Xc", editor.getText());
    }

    @Test
    public void batchValidationRejectsOutOfRangeLines() {
        editor.setText("a\nb");

        Array<CodeEditorTextEdit> edits = new Array<>();
        edits.add(CodeEditorTextEdit.insert(2, 0, "x"));
        assertFalse(editor.canApplyEdits(edits));

        edits.clear();
        edits.add(CodeEditorTextEdit.insert(-1, 0, "x"));
        assertFalse(editor.canApplyEdits(edits));

        edits.clear();
        edits.add(new CodeEditorTextEdit(0, 0, -1, 0, "x"));
        assertFalse(editor.canApplyEdits(edits));
    }
}
