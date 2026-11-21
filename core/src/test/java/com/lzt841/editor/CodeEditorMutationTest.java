package com.lzt841.editor;

import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.utils.Array;
import com.lzt841.editor.input.CodeEditorInteractionMode;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** Regression coverage for mutation APIs that must not report empty edits as content changes. */
public class CodeEditorMutationTest {
    private CodeEditor editor;
    private int contentChangeCount;
    private CodeEditorContentChangeEvent lastEvent;

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
        editor.setText("abc");
        editor.addContentListener(new CodeEditorContentListener() {
            @Override
            public void onContentChanged(CodeEditor changedEditor, CodeEditorContentChangeEvent event) {
                contentChangeCount++;
                lastEvent = event;
            }
        });
        contentChangeCount = 0;
        lastEvent = null;
    }

    @Test
    public void emptyDeleteDoesNotChangeDocumentOrNotify() {
        editor.setSelection(0, 0, 0, 2);
        CodeEditorTextRange selection = editor.getSelection();
        int version = editor.getDocumentVersion();
        int undoEntries = editor.getUndoEntryCount();

        assertFalse(editor.deleteRange(0, 1, 0, 1));

        assertEquals("abc", editor.getText());
        assertEquals(version, editor.getDocumentVersion());
        assertEquals(undoEntries, editor.getUndoEntryCount());
        assertEquals(0, contentChangeCount);
        assertFalse(editor.canUndo());
        assertEquals(selection, editor.getSelection());
    }

    @Test
    public void emptyReplacementDoesNotChangeDocumentOrNotify() {
        editor.setSelection(0, 0, 0, 2);
        CodeEditorTextRange selection = editor.getSelection();
        int version = editor.getDocumentVersion();
        int undoEntries = editor.getUndoEntryCount();

        assertFalse(editor.replaceRange(0, 1, 0, 1, ""));

        assertEquals("abc", editor.getText());
        assertEquals(version, editor.getDocumentVersion());
        assertEquals(undoEntries, editor.getUndoEntryCount());
        assertEquals(0, contentChangeCount);
        assertFalse(editor.canUndo());
        assertEquals(selection, editor.getSelection());
    }

    @Test
    public void nonEmptyReplacementAndDeletionStillNotifyExactlyOnce() {
        assertTrue(editor.replaceRange(0, 1, 0, 1, "X"));
        assertEquals("aXbc", editor.getText());
        assertEquals(1, contentChangeCount);
        assertEquals(CodeEditorContentChangeType.REPLACE_CURRENT, lastEvent.type);

        assertTrue(editor.deleteRange(0, 1, 0, 2));
        assertEquals("abc", editor.getText());
        assertEquals(2, contentChangeCount);
        assertEquals(CodeEditorContentChangeType.DELETE, lastEvent.type);
        assertEquals(2, editor.getUndoEntryCount());
    }

    @Test
    public void emptyCompoundEditAndNoOpBatchDoNotCreateUndoOrNotify() {
        int version = editor.getDocumentVersion();
        editor.beginCompoundEdit("empty");
        editor.endCompoundEdit();
        assertEquals(version, editor.getDocumentVersion());
        assertEquals(0, editor.getUndoEntryCount());
        assertEquals(0, contentChangeCount);

        Array<CodeEditorTextEdit> noOps = new Array<CodeEditorTextEdit>();
        noOps.add(CodeEditorTextEdit.insert(0, 1, ""));
        assertFalse(editor.applyEdits(noOps, "empty batch"));
        assertEquals(version, editor.getDocumentVersion());
        assertEquals(0, editor.getUndoEntryCount());
        assertEquals(0, contentChangeCount);
    }

    @Test
    public void filteredBatchThatProducesNoTextIsNotReportedAsSuccessful() {
        editor.setSelection(0, 0, 0, 2);
        CodeEditorTextRange selection = editor.getSelection();
        editor.setInputFilter(new CodeEditor.InputFilter() {
            @Override
            public boolean acceptChar(CodeEditor source, char character) {
                return false;
            }
        });
        Array<CodeEditorTextEdit> edits = new Array<CodeEditorTextEdit>();
        edits.add(CodeEditorTextEdit.insert(0, 1, "X"));
        int version = editor.getDocumentVersion();

        assertFalse(editor.applyEdits(edits));

        assertEquals("abc", editor.getText());
        assertEquals(version, editor.getDocumentVersion());
        assertEquals(0, editor.getUndoEntryCount());
        assertEquals(0, contentChangeCount);
        assertEquals(selection, editor.getSelection());
    }

    @Test
    public void batchTracksCaretUsingFilteredTextAndFiltersOnlyOnce() {
        editor.setCursorPosition(0, 2);
        final int[] filterCalls = {0};
        editor.setInputFilter(new CodeEditor.InputFilter() {
            @Override
            public boolean acceptChar(CodeEditor source, char character) {
                filterCalls[0]++;
                return character != 'x';
            }
        });
        Array<CodeEditorTextEdit> edits = new Array<CodeEditorTextEdit>();
        edits.add(CodeEditorTextEdit.insert(0, 0, "xQ"));

        assertTrue(editor.applyEdits(edits, "filtered insert"));

        assertEquals("Qabc", editor.getText());
        assertEquals(0, editor.getCursorLine());
        assertEquals(3, editor.getCursorColumn());
        assertEquals(2, filterCalls[0]);
        assertEquals(1, contentChangeCount);
        assertEquals(CodeEditorContentChangeType.REPLACE_ALL, lastEvent.type);
        assertEquals(1, editor.getUndoEntryCount());
        assertTrue(editor.undo());
        assertEquals("abc", editor.getText());
        assertEquals(2, editor.getCursorColumn());
        assertTrue(editor.redo());
        assertEquals("Qabc", editor.getText());
        assertEquals(3, editor.getCursorColumn());
    }

    @Test
    public void batchTracksCaretAcrossNormalizedCarriageReturns() {
        editor.setCursorPosition(0, 2);
        Array<CodeEditorTextEdit> edits = new Array<CodeEditorTextEdit>();
        edits.add(CodeEditorTextEdit.insert(0, 0, "x\ry"));

        assertTrue(editor.applyEdits(edits));

        assertEquals("x\nyabc", editor.getText());
        assertEquals(1, editor.getCursorLine());
        assertEquals(3, editor.getCursorColumn());
    }

    @Test
    public void emptyOperationsPreserveRedoAndCompoundEventType() {
        assertTrue(editor.insertTextAtCursor("X"));
        assertTrue(editor.undo());
        contentChangeCount = 0;
        int version = editor.getDocumentVersion();
        assertFalse(editor.deleteRange(0, 1, 0, 1));
        assertFalse(editor.replaceRange(0, 1, 0, 1, ""));
        assertEquals(version, editor.getDocumentVersion());
        assertEquals(1, editor.getRedoEntryCount());
        assertEquals(0, contentChangeCount);

        editor.beginCompoundEdit("insert and empty operations");
        try {
            assertTrue(editor.insertTextAtCursor("Y"));
            assertFalse(editor.deleteRange(0, 1, 0, 1));
            assertFalse(editor.replaceRange(0, 1, 0, 1, ""));
            assertFalse(editor.shiftLinesIndent(0, 0, false));
        } finally {
            editor.endCompoundEdit();
        }
        assertEquals(1, contentChangeCount);
        assertEquals(CodeEditorContentChangeType.INSERT, lastEvent.type);
        assertEquals(1, editor.getUndoEntryCount());
    }
}
