package com.lzt841.editor;

import com.badlogic.gdx.utils.Array;
import org.junit.Test;

import static com.lzt841.editor.EditorTestSupport.createEditor;
import static com.lzt841.editor.EditorTestSupport.keyTyped;
import static org.junit.Assert.*;

/** Content events describe actual splices and maintain their version chain through deferred/reentrant edits. */
public class CodeEditorContentChangeTrackingTest {
    @Test public void rangeReplacementRetainsSharedPrefixAndSuffixInItsExactSpans() {
        CodeEditor editor = createEditor("left keep old tail right");
        Array<CodeEditorContentChangeEvent> events = recordChanges(editor);
        int before = editor.getDocumentVersion();

        assertTrue(editor.replaceRange(0, 5, 0, 18, "keep newer tail"));

        assertEquals("left keep newer tail right", editor.getText());
        assertEquals(1, events.size);
        assertEquals(CodeEditorContentChangeType.REPLACE_CURRENT, events.first().type);
        assertExactTransition(events.first(), before, editor.getDocumentVersion(),
            new CodeEditorTextRange(0, 5, 0, 18), new CodeEditorTextRange(0, 5, 0, 20));
    }

    @Test public void selectionReplacementRetainsSharedPrefixAndSuffixInItsExactSpans() {
        CodeEditor editor = createEditor("left keep old tail right");
        editor.setSelection(0, 18, 0, 5);
        Array<CodeEditorContentChangeEvent> events = recordChanges(editor);
        int before = editor.getDocumentVersion();

        assertTrue(editor.insertTextAtCursor("keep newer tail"));

        assertEquals("left keep newer tail right", editor.getText());
        assertEquals(1, events.size);
        assertEquals(CodeEditorContentChangeType.INSERT, events.first().type);
        assertExactTransition(events.first(), before, editor.getDocumentVersion(),
            new CodeEditorTextRange(0, 5, 0, 18), new CodeEditorTextRange(0, 5, 0, 20));
        assertNull(editor.getSelection());
    }

    @Test public void multilineReplacementUsesPreEditAndPostEditCoordinates() {
        CodeEditor editor = createEditor("top\nleft keep old tail right\nbottom");
        Array<CodeEditorContentChangeEvent> events = recordChanges(editor);
        int before = editor.getDocumentVersion();

        assertTrue(editor.replaceRange(1, 5, 1, 18, "keep\nnewer\ntail"));

        assertEquals("top\nleft keep\nnewer\ntail right\nbottom", editor.getText());
        assertEquals(1, events.size);
        assertExactTransition(events.first(), before, editor.getDocumentVersion(),
            new CodeEditorTextRange(1, 5, 1, 18), new CodeEditorTextRange(1, 5, 3, 4));
        assertEquals(3, events.first().cursorLine);
        assertEquals(4, events.first().cursorColumn);
    }

    @Test public void sameFrameTypedCharactersPublishOneExactInsertion() {
        CodeEditor editor = createEditor("left right");
        editor.setCursorPosition(0, 5);
        Array<CodeEditorContentChangeEvent> events = recordChanges(editor);
        int before = editor.getDocumentVersion();

        assertTrue(keyTyped(editor, 'x'));
        assertTrue(keyTyped(editor, 'y'));
        assertTrue(keyTyped(editor, 'z'));
        assertEquals("left xyzright", editor.getText());
        assertEquals(0, events.size);
        int after = editor.getDocumentVersion();

        editor.flushPendingContentChanges();

        assertEquals(1, events.size);
        assertEquals(CodeEditorContentChangeType.INSERT, events.first().type);
        assertExactTransition(events.first(), before, after,
            new CodeEditorTextRange(0, 5, 0, 5), new CodeEditorTextRange(0, 5, 0, 8));
        editor.flushPendingContentChanges();
        editor.act(0f);
        assertEquals(1, events.size);
    }

    @Test public void sameFrameTypingOverASelectionKeepsItsOriginalReplacedSpan() {
        CodeEditor editor = createEditor("left old right");
        editor.setSelection(0, 5, 0, 8);
        Array<CodeEditorContentChangeEvent> events = recordChanges(editor);
        int before = editor.getDocumentVersion();

        assertTrue(keyTyped(editor, 'o'));
        assertTrue(keyTyped(editor, 'k'));
        assertEquals(0, events.size);
        editor.flushPendingContentChanges();

        assertEquals("left ok right", editor.getText());
        assertEquals(1, events.size);
        assertExactTransition(events.first(), before, editor.getDocumentVersion(),
            new CodeEditorTextRange(0, 5, 0, 8), new CodeEditorTextRange(0, 5, 0, 7));
    }

    @Test public void actFlushesTypingOnceAndStartsTheNextTransitionAtThatVersion() {
        CodeEditor editor = createEditor("");
        Array<CodeEditorContentChangeEvent> events = recordChanges(editor);
        int before = editor.getDocumentVersion();

        assertTrue(keyTyped(editor, 'a'));
        assertTrue(keyTyped(editor, 'b'));
        int firstVersion = editor.getDocumentVersion();
        assertEquals(0, events.size);
        editor.act(0f);
        assertEquals(1, events.size);
        assertExactTransition(events.first(), before, firstVersion,
            new CodeEditorTextRange(0, 0, 0, 0), new CodeEditorTextRange(0, 0, 0, 2));

        assertTrue(keyTyped(editor, 'c'));
        assertEquals(1, events.size);
        editor.act(0f);
        assertEquals(2, events.size);
        assertExactTransition(events.get(1), firstVersion, editor.getDocumentVersion(),
            new CodeEditorTextRange(0, 2, 0, 2), new CodeEditorTextRange(0, 2, 0, 3));
        editor.act(0f);
        editor.flushPendingContentChanges();
        assertEquals(2, events.size);
    }

    @Test public void aSynchronousEditAbsorbsPendingTypingWithoutADuplicateFlush() {
        CodeEditor editor = createEditor("tail");
        Array<CodeEditorContentChangeEvent> events = recordChanges(editor);
        int before = editor.getDocumentVersion();

        assertTrue(keyTyped(editor, 'a'));
        assertEquals(0, events.size);
        assertTrue(editor.replaceRange(0, 1, 0, 1, "b"));

        assertEquals("abtail", editor.getText());
        assertEquals(1, events.size);
        assertExactTransition(events.first(), before, editor.getDocumentVersion(),
            new CodeEditorTextRange(0, 0, 0, 0), new CodeEditorTextRange(0, 0, 0, 2));
        editor.flushPendingContentChanges();
        editor.act(0f);
        assertEquals(1, events.size);
    }

    @Test public void flushWaitsForTheOutermostCompoundEditToClose() {
        CodeEditor editor = createEditor("tail");
        Array<CodeEditorContentChangeEvent> events = recordChanges(editor);
        int before = editor.getDocumentVersion();

        editor.beginCompoundEdit("typing");
        try {
            editor.beginCompoundEdit();
            try {
                assertTrue(keyTyped(editor, 'a'));
                assertTrue(keyTyped(editor, 'b'));
                editor.flushPendingContentChanges();
                editor.act(0f);
                assertEquals(0, events.size);
            } finally {
                editor.endCompoundEdit();
            }
            editor.flushPendingContentChanges();
            assertEquals(0, events.size);
        } finally {
            editor.endCompoundEdit();
        }

        assertEquals("abtail", editor.getText());
        assertEquals(1, events.size);
        assertExactTransition(events.first(), before, editor.getDocumentVersion(),
            new CodeEditorTextRange(0, 0, 0, 0), new CodeEditorTextRange(0, 0, 0, 2));
        editor.flushPendingContentChanges();
        editor.act(0f);
        assertEquals(1, events.size);
    }

    @Test public void separatedCompoundEditsNeverRegainAnExactRange() {
        CodeEditor editor = createEditor("abcdef");
        Array<CodeEditorContentChangeEvent> events = recordChanges(editor);
        int before = editor.getDocumentVersion();

        editor.beginCompoundEdit("separate replacements");
        try {
            assertTrue(editor.replaceRange(0, 1, 0, 2, "B"));
            assertTrue(editor.replaceRange(0, 4, 0, 5, "E"));
            // This touches the first insertion's end, but the intervening disjoint edit invalidated it.
            assertTrue(editor.replaceRange(0, 2, 0, 2, "!"));
            editor.flushPendingContentChanges();
            assertEquals(0, events.size);
        } finally {
            editor.endCompoundEdit();
        }

        assertEquals("aB!cdEf", editor.getText());
        assertEquals(1, events.size);
        assertUnknownTransition(events.first(), before, editor.getDocumentVersion());
    }

    @Test public void separatedBatchEditsDoNotClaimOneExactSplice() {
        CodeEditor editor = createEditor("ab\ncd");
        Array<CodeEditorContentChangeEvent> events = recordChanges(editor);
        int before = editor.getDocumentVersion();

        assertTrue(editor.applyEditsExact(Array.with(
            new CodeEditorTextEdit(0, 0, 0, 1, "A"), new CodeEditorTextEdit(1, 1, 1, 2, "D"))));

        assertEquals("Ab\ncD", editor.getText());
        assertEquals(1, events.size);
        assertEquals(CodeEditorContentChangeType.REPLACE_ALL, events.first().type);
        assertUnknownTransition(events.first(), before, editor.getDocumentVersion());
    }

    @Test public void typingAtSeparatePositionsInOneFrameDoesNotClaimAnExactRange() {
        CodeEditor editor = createEditor("abcd");
        Array<CodeEditorContentChangeEvent> events = recordChanges(editor);
        int before = editor.getDocumentVersion();

        assertTrue(keyTyped(editor, 'X'));
        editor.setCursorPosition(0, 4);
        assertTrue(keyTyped(editor, 'Y'));
        assertEquals(0, events.size);
        editor.flushPendingContentChanges();

        assertEquals("XabcYd", editor.getText());
        assertEquals(1, events.size);
        assertUnknownTransition(events.first(), before, editor.getDocumentVersion());
    }

    @Test public void reentrantEditsChainVersionsAndPreserveEveryListenerSnapshot() {
        CodeEditor editor = createEditor("abc");
        Array<CodeEditorContentChangeEvent> firstObserver = recordChanges(editor);
        editor.addContentListener(new CodeEditorContentListener() {
            @Override public void onContentChanged(CodeEditor source, CodeEditorContentChangeEvent event) {
                source.removeContentListener(this);
                assertTrue(source.replaceRange(0, 2, 0, 3, "Y"));
            }
        });
        Array<CodeEditorContentChangeEvent> lastObserver = recordChanges(editor);
        int before = editor.getDocumentVersion();

        assertTrue(editor.replaceRange(0, 0, 0, 1, "XX"));

        assertEquals("XXYc", editor.getText());
        assertEquals(2, firstObserver.size);
        assertEquals(2, lastObserver.size);
        CodeEditorContentChangeEvent outer = firstObserver.first();
        CodeEditorContentChangeEvent nested = firstObserver.get(1);
        assertExactTransition(outer, before, outer.documentVersion,
            new CodeEditorTextRange(0, 0, 0, 1), new CodeEditorTextRange(0, 0, 0, 2));
        assertExactTransition(nested, outer.documentVersion, editor.getDocumentVersion(),
            new CodeEditorTextRange(0, 2, 0, 3), new CodeEditorTextRange(0, 2, 0, 3));
        assertTrue(nested.documentVersion > outer.documentVersion);
        assertEquals(2, outer.cursorColumn);
        assertEquals(3, nested.cursorColumn);
        // Recursive notification reaches a later listener before the outer snapshot resumes.
        assertSame(nested, lastObserver.first());
        assertSame(outer, lastObserver.get(1));

        assertTrue(editor.replaceRange(0, 3, 0, 4, "Z"));
        assertEquals("XXYZ", editor.getText());
        assertEquals(3, firstObserver.size);
        assertEquals(3, lastObserver.size);
        assertExactTransition(firstObserver.get(2), nested.documentVersion, editor.getDocumentVersion(),
            new CodeEditorTextRange(0, 3, 0, 4), new CodeEditorTextRange(0, 3, 0, 4));
        assertSame(firstObserver.get(2), lastObserver.get(2));
        editor.flushPendingContentChanges();
        assertEquals(3, firstObserver.size);
        assertEquals(3, lastObserver.size);
    }

    @Test public void typingFromAListenerRemainsPendingForTheNextFlush() {
        CodeEditor editor = createEditor("abc");
        Array<CodeEditorContentChangeEvent> events = new Array<>();
        editor.addContentListener((source, event) -> {
            events.add(event);
            if (events.size == 1) assertTrue(keyTyped(source, 'z'));
        });
        int before = editor.getDocumentVersion();

        assertTrue(editor.replaceRange(0, 0, 0, 1, "X"));

        assertEquals("Xzbc", editor.getText());
        assertEquals(1, events.size);
        CodeEditorContentChangeEvent outer = events.first();
        assertExactTransition(outer, before, outer.documentVersion,
            new CodeEditorTextRange(0, 0, 0, 1), new CodeEditorTextRange(0, 0, 0, 1));
        assertTrue(editor.getDocumentVersion() > outer.documentVersion);

        editor.flushPendingContentChanges();

        assertEquals(2, events.size);
        assertExactTransition(events.get(1), outer.documentVersion, editor.getDocumentVersion(),
            new CodeEditorTextRange(0, 1, 0, 1), new CodeEditorTextRange(0, 1, 0, 2));
        editor.flushPendingContentChanges();
        editor.act(0f);
        assertEquals(2, events.size);
    }

    private static Array<CodeEditorContentChangeEvent> recordChanges(CodeEditor editor) {
        Array<CodeEditorContentChangeEvent> events = new Array<>();
        editor.addContentListener((source, event) -> events.add(event));
        return events;
    }

    private static void assertExactTransition(CodeEditorContentChangeEvent event, int before, int after,
        CodeEditorTextRange replaced, CodeEditorTextRange inserted) {
        assertEquals(before, event.previousDocumentVersion);
        assertEquals(after, event.documentVersion);
        assertEquals(replaced, event.replacedRange);
        assertEquals(inserted, event.insertedRange);
    }

    private static void assertUnknownTransition(CodeEditorContentChangeEvent event, int before, int after) {
        assertEquals(before, event.previousDocumentVersion);
        assertEquals(after, event.documentVersion);
        assertNull(event.replacedRange);
        assertNull(event.insertedRange);
    }
}
