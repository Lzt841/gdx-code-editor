package com.lzt841.editor.completion;

import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.IntArray;
import com.lzt841.editor.CodeEditor;
import com.lzt841.editor.CodeEditorTextRange;
import org.junit.Test;

import static com.lzt841.editor.EditorTestSupport.createEditor;
import static org.junit.Assert.*;

public class CodeSnippetSafetyTest {
    @Test public void rejectedCharactersPreserveTextSelectionVersionAndRedo() {
        CodeEditor editor = createEditor("keep");
        assertTrue(editor.insertTextAtCursor("!"));
        assertTrue(editor.undo());
        editor.setSelection(0, 4, 0, 0);
        int version = editor.getDocumentVersion();
        int[] events = {0};
        editor.addContentListener((source, event) -> events[0]++);
        editor.setInputFilter((source, character) -> character != '!');

        assertNull(CodeSnippetSession.start(editor, "${1:foo}!$0"));

        assertEquals("keep", editor.getText());
        assertEquals(new CodeEditorTextRange(0, 0, 0, 4), editor.getSelection());
        assertEquals(0, editor.getCursorColumn());
        assertEquals(version, editor.getDocumentVersion());
        assertEquals(0, editor.getUndoEntryCount());
        assertEquals(1, editor.getRedoEntryCount());
        assertEquals(0, events[0]);
    }

    @Test public void crlfAndBareCrAreNormalizedBeforeStopsAndFiltering() {
        CodeEditor editor = createEditor("head tail");
        editor.setCursorPosition(0, 5);
        StringBuilder filtered = new StringBuilder();
        editor.setInputFilter((source, character) -> { filtered.append(character); return true; });
        CodeSnippetSession session = CodeSnippetSession.start(editor, "x\r\n${1:alpha}\r${2:beta}$0");

        assertNotNull(session);
        assertEquals("head x\nalpha\nbetatail", editor.getText());
        assertEquals("x\nalpha\nbeta", filtered.toString());
        assertEquals("x\nalpha\nbeta", session.getTemplate().insertText);
        assertEquals(new CodeEditorTextRange(1, 0, 1, 5), editor.getSelection());
        assertTrue(session.next());
        assertEquals(new CodeEditorTextRange(2, 0, 2, 4), editor.getSelection());
        assertTrue(session.next());
        assertFalse(session.isActive());
        assertEquals(2, editor.getCursorLine());
        assertEquals(4, editor.getCursorColumn());
        assertEquals("Insert Snippet", editor.getUndoLabel());
        assertTrue(editor.undo());
        assertEquals("head tail", editor.getText());
    }

    @Test public void variablesAndParsedTemplatesGetIndependentNormalizedRanges() {
        CodeEditor editor = createEditor("");
        CodeSnippetContext context = new CodeSnippetContext();
        context.clipboard = "one\r\ntwo\rthree";
        CodeSnippetTemplate template = CodeSnippetTemplate.parse("$CLIPBOARD${1:end}$0", context);

        CodeSnippetSession session = CodeSnippetSession.startParsed(editor, template, null);

        assertNotNull(session);
        assertEquals("one\ntwo\nthreeend", editor.getText());
        assertEquals(new CodeEditorTextRange(2, 5, 2, 8), editor.getSelection());
        assertEquals("one\r\ntwo\rthreeend", template.insertText);
        assertNotSame(template, session.getTemplate());
        assertTrue(session.next());
        assertEquals(2, editor.getCursorLine());
        assertEquals(8, editor.getCursorColumn());
    }

    @Test public void aStopBetweenCrAndLfMapsAfterTheNormalizedNewline() {
        CodeEditor editor = createEditor("");
        CodeSnippetTemplate template = new CodeSnippetTemplate("", "a\r\nb", Array.with(
            new CodeSnippetPlaceholder(1, 2, 2), new CodeSnippetPlaceholder(0, 4, 4)), new IntArray(new int[] {1, 0}));

        CodeSnippetSession session = CodeSnippetSession.startParsed(editor, template, null);

        assertNotNull(session);
        assertEquals("a\nb", editor.getText());
        assertEquals(1, editor.getCursorLine());
        assertEquals(0, editor.getCursorColumn());
        assertTrue(session.next());
        assertEquals(1, editor.getCursorLine());
        assertEquals(1, editor.getCursorColumn());
    }

    @Test public void directStartRejectsCoordinatesInvalidatedByAFilter() {
        CodeEditor editor = createEditor("abc");
        editor.setCursorPosition(0, 1);
        editor.setInputFilter((source, character) -> { source.setText("host edit"); return true; });

        assertNull(CodeSnippetSession.start(editor, "${1:foo}$0"));

        assertEquals("host edit", editor.getText());
        assertNull(editor.getSelection());
        assertEquals(0, editor.getUndoEntryCount());
    }

    @Test public void filtersCanBlockDirectStartsWithoutAnUndoEntry() {
        for (int mode = 0; mode < 2; mode++) {
            CodeEditor editor = createEditor("keep");
            editor.setSelection(0, 4, 0, 0);
            final int blockedMode = mode;
            editor.setInputFilter((source, character) -> {
                if (blockedMode == 0) source.setReadOnly(true);
                else source.setDisabled(true);
                return true;
            });

            assertNull(CodeSnippetSession.start(editor, "${1:foo}$0"));

            assertEquals("keep", editor.getText());
            assertEquals(new CodeEditorTextRange(0, 0, 0, 4), editor.getSelection());
            assertEquals(0, editor.getUndoEntryCount());
        }
    }

    @Test public void callerMutationsDuringFilteringCannotChangeLiveStops() {
        CodeEditor editor = createEditor("");
        CodeSnippetTemplate template = CodeSnippetTemplate.parse("${1:alpha} ${2:beta}$0");
        editor.setInputFilter((source, character) -> {
            template.placeholders.clear();
            template.tabOrder.clear();
            return true;
        });

        CodeSnippetSession session = CodeSnippetSession.startParsed(editor, template, null);

        assertNotNull(session);
        assertEquals(new CodeEditorTextRange(0, 0, 0, 5), editor.getSelection());
        assertTrue(session.next());
        assertEquals(new CodeEditorTextRange(0, 6, 0, 10), editor.getSelection());
        assertTrue(session.next());
        assertFalse(session.isActive());
        assertEquals(10, editor.getCursorColumn());
    }

    @Test public void invalidParsedPlaceholderBoundsAreRejectedBeforeEditing() {
        for (CodeSnippetPlaceholder invalid : new CodeSnippetPlaceholder[] {
            new CodeSnippetPlaceholder(1, -1, 1), new CodeSnippetPlaceholder(1, 0, 4),
            new CodeSnippetPlaceholder(1, 2, 1), new CodeSnippetPlaceholder(-1, 0, 1), null
        }) {
            CodeEditor editor = createEditor("keep");
            CodeSnippetTemplate template = new CodeSnippetTemplate("", "abc", Array.with(
                invalid, new CodeSnippetPlaceholder(0, 3, 3)), new IntArray(new int[] {1, 0}));

            assertNull(CodeSnippetSession.startParsed(editor, template, null));

            assertEquals("keep", editor.getText());
            assertEquals(0, editor.getUndoEntryCount());
        }
    }

    @Test public void parsedStopsCannotSplitASupplementaryCharacter() {
        CodeEditor editor = createEditor("keep");
        CodeSnippetTemplate template = new CodeSnippetTemplate("", "\uD83D\uDE42", Array.with(
            new CodeSnippetPlaceholder(1, 1, 1), new CodeSnippetPlaceholder(0, 2, 2)), new IntArray(new int[] {1, 0}));

        assertNull(CodeSnippetSession.startParsed(editor, template, null));

        assertEquals("keep", editor.getText());
        assertEquals(0, editor.getUndoEntryCount());
    }

    @Test public void rejectedSupplementaryTextCannotInsertHalfASurrogatePair() {
        CodeEditor editor = createEditor("");
        editor.setInputFilter((source, character) -> !Character.isLowSurrogate(character));

        assertNull(CodeSnippetSession.start(editor, "${1:\uD83D\uDE42}$0"));

        assertEquals("", editor.getText());
        assertEquals(0, editor.getUndoEntryCount());
    }

    @Test public void mirrorsTrackLaterStopsAndKeepThePrimaryCaret() {
        CodeEditor editor = createEditor("");
        CodeSnippetSession session = CodeSnippetSession.start(editor, "${1:x} $1 ${2:tail} $1$0");
        assertNotNull(session);

        assertTrue(editor.insertTextAtCursor("wide"));

        assertEquals("wide wide tail wide", editor.getText());
        assertTrue(session.isActive());
        assertEquals(4, editor.getCursorColumn());
        assertTrue(session.next());
        assertEquals("tail", editor.getSelectionText());
        assertEquals(new CodeEditorTextRange(0, 10, 0, 14), editor.getSelection());
        assertTrue(session.next());
        assertEquals(19, editor.getCursorColumn());
    }

    @Test public void aRejectedMirrorRejectsAllCopiesAndEndsTheSession() {
        CodeEditor editor = createEditor("");
        CodeSnippetSession session = CodeSnippetSession.start(editor, "${1:x} $1 $1$0");
        assertNotNull(session);
        int[] calls = {0};
        editor.setInputFilter((source, character) -> ++calls[0] <= 2);

        assertTrue(editor.insertTextAtCursor("y"));

        assertEquals("y x x", editor.getText());
        assertFalse(session.isActive());
        assertEquals(3, calls[0]);
        assertEquals(2, editor.getUndoEntryCount());
        assertTrue(editor.undo());
        assertEquals("x x x", editor.getText());
    }

    @Test public void mirrorFilterDocumentChangesCannotUseOldRanges() {
        CodeEditor editor = createEditor("");
        CodeSnippetSession session = CodeSnippetSession.start(editor, "${1:x} $1$0");
        assertNotNull(session);
        int[] calls = {0};
        editor.setInputFilter((source, character) -> {
            if (++calls[0] > 1) source.setText("host edit");
            return true;
        });

        assertTrue(editor.insertTextAtCursor("y"));

        assertEquals("host edit", editor.getText());
        assertFalse(session.isActive());
        assertEquals(0, editor.getUndoEntryCount());
    }

    @Test public void multilineMirrorsKeepTheFollowingStopOnItsActualLine() {
        CodeEditor editor = createEditor("");
        CodeSnippetSession session = CodeSnippetSession.start(editor, "${1:x} $1 ${2:tail}$0");
        assertNotNull(session);

        assertTrue(editor.insertTextAtCursor("a\nb"));

        assertEquals("a\nb a\nb tail", editor.getText());
        assertTrue(session.isActive());
        assertEquals(1, editor.getCursorLine());
        assertEquals(1, editor.getCursorColumn());
        assertTrue(session.next());
        assertEquals(new CodeEditorTextRange(2, 2, 2, 6), editor.getSelection());
        assertEquals("tail", editor.getSelectionText());
        session.cancel();
    }

    @Test public void contentNotificationsObserveTheFirstStop() {
        CodeEditor editor = createEditor("");
        int[] events = {0};
        editor.addContentListener((source, event) -> {
            events[0]++;
            assertEquals(new CodeEditorTextRange(0, 0, 0, 5), source.getSelection());
        });

        CodeSnippetSession session = CodeSnippetSession.start(editor, "${1:alpha} ${2:beta}$0");

        assertNotNull(session);
        assertTrue(session.isActive());
        assertEquals(1, events[0]);
        assertEquals("Insert Snippet", editor.getUndoLabel());
        session.cancel();
    }

    @Test public void aContentListenerRewriteCannotLeaveAStaleSession() {
        CodeEditor editor = createEditor("");
        int[] events = {0};
        editor.addContentListener((source, event) -> {
            if (++events[0] == 1) source.setText("host edit");
        });

        assertNull(CodeSnippetSession.start(editor, "${1:alpha}$0"));

        assertEquals("host edit", editor.getText());
        assertNull(editor.getSelection());
    }

    @Test public void adjacentMirrorsDoNotMisidentifyRepeatedCharacters() {
        CodeEditor editor = createEditor("");
        CodeSnippetSession session = CodeSnippetSession.start(editor, "${1:x}$1${2:tail}$0");
        assertNotNull(session);
        assertEquals("xxtail", editor.getText());
        assertEquals("x", editor.getSelectionText());

        assertTrue(editor.insertTextAtCursor("xx"));

        assertEquals("xxxxtail", editor.getText());
        assertTrue(session.isActive());
        assertEquals(new CodeEditorTextRange(0, 0, 0, 2), session.getCurrentRange());
        assertEquals(2, editor.getCursorColumn());
        assertTrue(session.next());
        assertEquals(new CodeEditorTextRange(0, 4, 0, 8), editor.getSelection());
        assertTrue(session.next());
        assertEquals(8, editor.getCursorColumn());
        assertNull(editor.getSelection());
    }

    @Test public void anEmptyStopDoesNotAbsorbTheFollowingDefault() {
        CodeEditor editor = createEditor("");
        CodeSnippetSession session = CodeSnippetSession.start(editor, "${1}${2:tail}$0");
        assertNotNull(session);

        assertTrue(editor.insertTextAtCursor("x"));

        assertEquals("xtail", editor.getText());
        assertTrue(session.next());
        assertEquals(new CodeEditorTextRange(0, 1, 0, 5), editor.getSelection());
        assertEquals("tail", editor.getSelectionText());
    }

    @Test public void coincidentEmptyMirrorsRemainSeparateStops() {
        CodeEditor editor = createEditor("");
        CodeSnippetSession session = CodeSnippetSession.start(editor, "${1}$1$1${2:tail}$0");
        assertNotNull(session);

        assertTrue(editor.insertTextAtCursor("x"));

        assertEquals("xxxtail", editor.getText());
        assertTrue(session.isActive());
        assertEquals(1, editor.getCursorColumn());
        assertEquals(new CodeEditorTextRange(0, 0, 0, 1), session.getCurrentRange());
        assertTrue(session.next());
        assertEquals(new CodeEditorTextRange(0, 3, 0, 7), editor.getSelection());
        assertTrue(session.previous());
        assertTrue(editor.insertTextAtCursor("yy"));
        assertEquals("yyyyyytail", editor.getText());
        assertEquals(2, editor.getCursorColumn());
        assertTrue(session.next());
        assertEquals(new CodeEditorTextRange(0, 6, 0, 10), editor.getSelection());
    }

    @Test public void mirrorOrderUsesCoordinatesAndTracksAnEarlierCopy() {
        CodeEditor editor = createEditor("");
        CodeSnippetTemplate template = new CodeSnippetTemplate("", "x x tail x", Array.with(
            new CodeSnippetPlaceholder(1, 2, 3), new CodeSnippetPlaceholder(1, 9, 10),
            new CodeSnippetPlaceholder(2, 4, 8), new CodeSnippetPlaceholder(1, 0, 1),
            new CodeSnippetPlaceholder(0, 10, 10)), new IntArray(new int[] {1, 2, 0}));
        CodeSnippetSession session = CodeSnippetSession.startParsed(editor, template, null);
        assertNotNull(session);

        assertTrue(editor.insertTextAtCursor("wide"));

        assertEquals("wide wide tail wide", editor.getText());
        assertTrue(session.isActive());
        assertEquals(9, editor.getCursorColumn());
        assertEquals(new CodeEditorTextRange(0, 5, 0, 9), session.getCurrentRange());
        assertTrue(session.next());
        assertEquals(new CodeEditorTextRange(0, 10, 0, 14), editor.getSelection());
        assertTrue(session.next());
        assertEquals(19, editor.getCursorColumn());
    }

    @Test public void templateAccessCannotMutateTheLiveSession() {
        CodeEditor editor = createEditor("");
        CodeSnippetSession session = CodeSnippetSession.start(editor, "${1:x} $1 ${2:tail}$0");
        assertNotNull(session);
        CodeSnippetTemplate exposed = session.getTemplate();
        exposed.placeholders.clear();
        exposed.tabOrder.clear();

        assertTrue(editor.insertTextAtCursor("wide"));

        assertEquals("wide wide tail", editor.getText());
        assertTrue(session.isActive());
        assertTrue(session.next());
        assertEquals("tail", editor.getSelectionText());
    }

    @Test public void malformedTabOrdersAreRejectedBeforeEditing() {
        for (int[] order : new int[][] {{1, 1, 0}, {-1, 0}, {2, 0}, {0, 1}}) {
            CodeEditor editor = createEditor("keep");
            CodeSnippetTemplate template = new CodeSnippetTemplate("", "x", Array.with(
                new CodeSnippetPlaceholder(1, 0, 1), new CodeSnippetPlaceholder(0, 1, 1)), new IntArray(order));

            assertNull(CodeSnippetSession.startParsed(editor, template, null));

            assertEquals("keep", editor.getText());
            assertEquals(0, editor.getUndoEntryCount());
        }
    }

    @Test public void aMirrorNotificationRewriteEndsTheSession() {
        CodeEditor editor = createEditor("");
        CodeSnippetSession session = CodeSnippetSession.start(editor, "${1:x} $1$0");
        assertNotNull(session);
        editor.addContentListener((source, event) -> {
            if ("wide wide".equals(source.getText())) source.setText("host edit");
        });

        assertTrue(editor.insertTextAtCursor("wide"));

        assertEquals("host edit", editor.getText());
        assertFalse(session.isActive());
    }

    @Test public void shrinkingMultilineMirrorsKeepsTheFollowingLineIntact() {
        CodeEditor editor = createEditor("before\nafter");
        editor.setCursorPosition(1, 0);
        CodeSnippetSession session = CodeSnippetSession.start(editor, "${1:a\nb} $1 ${2:tail}$0\n");
        assertNotNull(session);

        assertTrue(editor.insertTextAtCursor("x"));

        assertEquals("before\nx x tail\nafter", editor.getText());
        assertTrue(session.isActive());
        assertEquals(1, editor.getCursorLine());
        assertEquals(1, editor.getCursorColumn());
        assertTrue(session.next());
        assertEquals(new CodeEditorTextRange(1, 4, 1, 8), editor.getSelection());
        assertTrue(session.next());
        assertEquals(1, editor.getCursorLine());
        assertEquals(8, editor.getCursorColumn());
    }

    @Test public void defaultMirrorFillingDoesNotExtendAPrecedingSibling() {
        CodeEditor editor = createEditor("");
        CodeSnippetSession session = CodeSnippetSession.start(editor, "${1:a}$2${2:b}$0");

        assertNotNull(session);
        assertEquals("abb", editor.getText());
        assertEquals("a", editor.getSelectionText());
        assertTrue(editor.insertTextAtCursor("A"));
        assertEquals("Abb", editor.getText());
        assertTrue(session.next());
        assertEquals(new CodeEditorTextRange(0, 1, 0, 2), editor.getSelection());
        assertTrue(editor.insertTextAtCursor("B"));
        assertEquals("ABB", editor.getText());
        assertTrue(session.next());
        assertEquals(3, editor.getCursorColumn());
    }

    @Test public void anEmptyNestedParentGrowsWithItsChildWithoutAbsorbingTheMirror() {
        CodeEditor editor = createEditor("");
        CodeSnippetSession session = CodeSnippetSession.start(editor, "${2:${1}}$1$0");
        assertNotNull(session);

        assertTrue(editor.insertTextAtCursor("x"));

        assertEquals("xx", editor.getText());
        assertTrue(session.isActive());
        assertEquals(new CodeEditorTextRange(0, 0, 0, 1), session.getCurrentRange());
        assertTrue(session.next());
        assertEquals(new CodeEditorTextRange(0, 0, 0, 1), editor.getSelection());
        assertTrue(session.next());
        assertEquals(2, editor.getCursorColumn());
    }

    @Test public void nestedDefaultMirrorsRetainTheirParents() {
        CodeEditor editor = createEditor("");
        CodeSnippetSession session = CodeSnippetSession.start(editor, "${2:${1}} $1 ${1:x}$0");
        assertNotNull(session);
        assertEquals("x x x", editor.getText());
        assertEquals(new CodeEditorTextRange(0, 0, 0, 1), editor.getSelection());

        assertTrue(editor.insertTextAtCursor("wide"));

        assertEquals("wide wide wide", editor.getText());
        assertTrue(session.next());
        assertEquals(new CodeEditorTextRange(0, 0, 0, 4), editor.getSelection());
        assertTrue(session.next());
        assertEquals(14, editor.getCursorColumn());
    }

    @Test public void replacingAParentDropsItsChildrenEvenWhenTheirTextMatchesTheReplacement() {
        CodeEditor editor = createEditor("");
        CodeSnippetSession session = CodeSnippetSession.start(editor, "${1:${2:keep} old} | $1 | ${3:tail}$0");
        assertNotNull(session);

        assertTrue(editor.insertTextAtCursor("keep new"));

        assertEquals("keep new | keep new | tail", editor.getText());
        assertTrue(session.isActive());
        assertEquals(3, session.getTabStopCount());
        assertTrue(session.next());
        assertEquals(3, session.getCurrentTabStop());
        assertEquals("tail", editor.getSelectionText());
        assertTrue(session.previous());
        assertEquals("keep new", editor.getSelectionText());
        assertTrue(session.next());
        assertTrue(session.next());
        assertFalse(session.isActive());
        assertEquals(editor.getLineLength(0), editor.getCursorColumn());
    }

    @Test public void overwrittenChildrenLeaveOnlyTheirSurvivingCopiesNavigable() {
        CodeEditor editor = createEditor("");
        CodeSnippetSession session = CodeSnippetSession.start(editor, "${1:(${2:value}$3)} | $2 | $2 | ${4:tail}$0");
        assertNotNull(session);

        assertTrue(editor.insertTextAtCursor("other\nvalue"));

        assertTrue(session.isActive());
        assertTrue(session.next());
        assertEquals(2, session.getCurrentTabStop());
        assertEquals("value", editor.getSelectionText());
        assertTrue(editor.insertTextAtCursor("V"));
        assertEquals("other\nvalue | V | V | tail", editor.getText());
        assertTrue(session.next());
        assertEquals(4, session.getCurrentTabStop());
        assertEquals("tail", editor.getSelectionText());
    }

    @Test public void replacingAParentCarriesItsNestedExitToTheEndOfTheInsertion() {
        CodeEditor editor = createEditor("");
        CodeSnippetSession session = CodeSnippetSession.start(editor, "${1:a$0b} suffix");
        assertNotNull(session);

        assertTrue(editor.insertTextAtCursor("replacement"));

        assertTrue(session.next());
        assertFalse(session.isActive());
        assertEquals("replacement suffix", editor.getText());
        assertEquals(11, editor.getCursorColumn());
    }

    @Test public void editingAMirrorNeverOverwritesTheUsersEditWithTheFirstCopy() {
        CodeEditor editor = createEditor("");
        CodeSnippetSession session = CodeSnippetSession.start(editor, "${1:x} | $1 | ${2:tail}$0");
        assertNotNull(session);
        editor.setSelection(0, 4, 0, 5);

        assertTrue(editor.insertTextAtCursor("wide"));

        assertEquals("wide | wide | tail", editor.getText());
        assertEquals(11, editor.getCursorColumn());
        assertTrue(session.isActive());
        assertTrue(session.next());
        assertEquals("tail", editor.getSelectionText());
    }

    @Test public void editsAboveAndBelowTheRegionAdvanceItsVersionAndKeepStopsAligned() {
        CodeEditor editor = createEditor("top\n\nbottom");
        editor.setCursorPosition(1, 0);
        CodeSnippetSession session = CodeSnippetSession.start(editor, "${1:x} $1 ${2:tail}$0");
        assertNotNull(session);

        assertTrue(editor.replaceRange(0, 0, 0, 3, "header\nextra"));
        assertTrue(session.isActive());
        assertTrue(editor.replaceRange(3, 0, 3, 6, "footer"));
        assertTrue(session.isActive());
        assertTrue(session.next());

        assertEquals(new CodeEditorTextRange(2, 4, 2, 8), editor.getSelection());
        assertTrue(session.previous());
        assertTrue(editor.insertTextAtCursor("wide"));
        assertEquals("header\nextra\nwide wide tail\nfooter", editor.getText());
        assertTrue(session.next());
        assertEquals("tail", editor.getSelectionText());
    }

    @Test public void aReplacementBeginningAboveTheRegionCannotKeepStaleStops() {
        CodeEditor editor = createEditor("top\n\nbottom");
        editor.setCursorPosition(1, 0);
        CodeSnippetSession session = CodeSnippetSession.start(editor, "${1:x} $1 ${2:tail}$0");
        assertNotNull(session);

        assertTrue(editor.replaceRange(0, 0, 1, 1, "other\nx"));

        assertEquals("other\nx x tail\nbottom", editor.getText());
        assertFalse(session.isActive());
        assertFalse(session.next());
    }

    @Test public void anEarlierListenerRewritingTheDocumentEndsTheSessionBeforeItCanMirror() {
        CodeEditor editor = createEditor("");
        boolean[] rewrite = {false};
        editor.addContentListener((source, event) -> {
            if (!rewrite[0]) return;
            rewrite[0] = false;
            assertTrue(source.replaceRange(0, 11, 0, 15, "host"));
        });
        CodeSnippetSession session = CodeSnippetSession.start(editor, "${1:x} | $1 | ${2:tail}$0");
        assertNotNull(session);
        rewrite[0] = true;

        assertTrue(editor.insertTextAtCursor("wide"));

        assertEquals("wide | x | host", editor.getText());
        assertFalse(session.isActive());
    }

    @Test public void allMirrorCopiesFormOneUndoStepAfterTheUsersEdit() {
        CodeEditor editor = createEditor("");
        CodeSnippetSession session = CodeSnippetSession.start(editor, "${1:x} $1 $1$0");
        assertNotNull(session);
        assertTrue(editor.insertTextAtCursor("wide"));
        assertEquals("wide wide wide", editor.getText());
        assertEquals("Mirror Snippet", editor.getUndoLabel());
        assertEquals(3, editor.getUndoEntryCount());

        assertTrue(editor.undo());

        assertEquals("wide x x", editor.getText());
        assertFalse(session.isActive());
        assertTrue(editor.undo());
        assertEquals("x x x", editor.getText());
    }

    @Test public void supplementaryCharactersRemainWholeWhenEditingAnotherCopy() {
        CodeEditor editor = createEditor("");
        CodeSnippetSession session = CodeSnippetSession.start(editor, "${1:\uD83D\uDE42} | $1 | ${2:tail}$0");
        assertNotNull(session);
        editor.setSelection(0, 5, 0, 7);

        assertTrue(editor.insertTextAtCursor("\uD83D\uDE43"));

        assertEquals("\uD83D\uDE43 | \uD83D\uDE43 | tail", editor.getText());
        assertTrue(session.next());
        assertEquals("tail", editor.getSelectionText());
    }

    @Test public void recursivelyLinkedStopsAreRejectedBeforeChangingTheDocument() {
        CodeEditor editor = createEditor("keep");
        editor.setSelection(0, 0, 0, 4);

        assertNull(CodeSnippetSession.start(editor, "${1:${1}} ${1:x}$0"));

        assertEquals("keep", editor.getText());
        assertEquals("keep", editor.getSelectionText());
        assertEquals(0, editor.getUndoEntryCount());
    }

    @Test public void variableFallbackNestingRebasesParentIndices() {
        CodeEditor editor = createEditor("");
        CodeSnippetSession session = CodeSnippetSession.start(editor, "${2:pre ${UNKNOWN:${3:${1}}}} $1$0");
        assertNotNull(session);
        assertEquals(1, session.getCurrentTabStop());

        assertTrue(editor.insertTextAtCursor("x"));

        assertEquals("pre x x", editor.getText());
        assertTrue(session.isActive());
        assertTrue(session.next());
        assertEquals("pre x", editor.getSelectionText());
        assertTrue(session.next());
        assertEquals("x", editor.getSelectionText());
        assertTrue(session.next());
        assertEquals(7, editor.getCursorColumn());
    }
}
