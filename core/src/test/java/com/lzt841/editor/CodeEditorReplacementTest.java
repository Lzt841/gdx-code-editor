package com.lzt841.editor;

import com.badlogic.gdx.utils.Array;
import org.junit.Test;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.lzt841.editor.EditorTestSupport.createEditor;
import static org.junit.Assert.*;

/** Replacements must filter the inserted text and leave unchanged content/history alone. */
public class CodeEditorReplacementTest {
    @Test public void regexGroupsAreExpandedBeforeFilteringOneReplacement() {
        CodeEditor editor = createEditor("id=42 id=7");
        editor.setSearchRegexEnabled(true);
        editor.setSearchText("id=(\\d+)");
        StringBuilder filtered = new StringBuilder();
        editor.setInputFilter((source, character) -> {
            filtered.append(character);
            return Character.isDigit(character);
        });
        assertTrue(editor.replaceCurrentSearchMatch("$1"));
        assertEquals("42 id=7", editor.getText());
        assertEquals("42", filtered.toString());
        assertEquals(1, editor.getUndoEntryCount());
        assertTrue(editor.undo());
        assertEquals("id=42 id=7", editor.getText());
    }

    @Test public void regexGroupsAreFilteredOncePerActualInsertionInReplaceAll() {
        CodeEditor editor = createEditor("id=x42 id=y7");
        editor.setSearchRegexEnabled(true);
        editor.setSearchText("id=([a-z][0-9]+)");
        StringBuilder filtered = new StringBuilder();
        editor.setInputFilter((source, character) -> {
            filtered.append(character);
            return Character.isDigit(character);
        });
        int[] events = {0};
        editor.addContentListener((source, event) -> events[0]++);
        assertEquals(2, editor.replaceAllSearchMatches("$1"));
        assertEquals("42 7", editor.getText());
        assertEquals("x42y7", filtered.toString());
        assertEquals(1, events[0]);
        assertEquals(1, editor.getUndoEntryCount());
        assertTrue(editor.undo());
        assertEquals("id=x42 id=y7", editor.getText());
        assertTrue(editor.redo());
        assertEquals("42 7", editor.getText());
    }

    @Test public void namedGroupsAreExpandedBeforeInputFiltering() {
        CodeEditor editor = createEditor("id=42");
        editor.setSearchRegexEnabled(true);
        editor.setSearchText("id=(?<value>[0-9]+)");
        editor.setInputFilter((source, character) -> Character.isDigit(character));
        assertEquals(1, editor.replaceAllSearchMatches("${value}"));
        assertEquals("42", editor.getText());
    }

    @Test public void replacementExpansionKeepsNamedGroupsEscapesAndUnmatchedGroups() {
        CodeEditor editor = createEditor("foo foobar");
        editor.setSearchRegexEnabled(true);
        editor.setSearchText("foo(?<suffix>bar)?");
        assertEquals(2, editor.replaceAllSearchMatches("$0-${suffix}-\\$"));
        assertEquals("foo--$ foobar-bar-$", editor.getText());
    }

    @Test public void invalidReplacementGroupFallsBackToLiteralText() {
        CodeEditor editor = createEditor("id=42");
        editor.setSearchRegexEnabled(true);
        editor.setSearchText("id=([0-9]+)");
        assertEquals(1, editor.replaceAllSearchMatches("$2"));
        assertEquals("$2", editor.getText());
    }

    @Test public void replacementTemplatesAgreeWithJavaMatcherIncludingInvalidLiteralFallback() {
        String text = "acdefghijkl";
        String regex = "(?<value>a)(b)?(c)(d)(e)(f)(g)(h)(i)(j)(k)(l)";
        Pattern pattern = Pattern.compile(regex);
        String[] templates = {"$0", "$00", "$01", "$001", "$02", "$12", "$123", "$13", "$99",
            "$2", "${value}", "${missing}", "${}", "${9}", "${value", "${value}x", "\\$1", "\\\\$1",
            "$", "\\", "$-", "$1$", "ok\\n", "[${value}][$2][$12]"};
        for (String template : templates) {
            Matcher matcher = pattern.matcher(text);
            assertTrue(matcher.find());
            String expected;
            try {
                StringBuffer buffer = new StringBuffer();
                matcher.appendReplacement(buffer, template);
                expected = buffer.toString();
            } catch (IllegalArgumentException | IndexOutOfBoundsException invalid) {
                expected = template;
            }
            CodeEditor editor = createEditor(text);
            editor.setSearchRegexEnabled(true);
            editor.setSearchText(regex);
            int version = editor.getDocumentVersion();
            assertEquals(template, expected.equals(text) ? 0 : 1, editor.replaceAllSearchMatches(template));
            assertEquals(template, expected, editor.getText());
            if (expected.equals(text)) {
                assertEquals(version, editor.getDocumentVersion());
                assertEquals(0, editor.getUndoEntryCount());
            } else {
                assertTrue(template, editor.undo());
                assertEquals(template, text, editor.getText());
            }
        }
    }

    @Test public void replacementExpansionRecoversLookaroundGroupsAtEachOriginalOffset() {
        CodeEditor editor = createEditor("pre42 pre7\npre9");
        editor.setSearchRegexEnabled(true);
        editor.setSearchText("(?<=(pre))([0-9]+)");
        assertEquals(3, editor.replaceAllSearchMatches("$1=$2"));
        assertEquals("prepre=42 prepre=7\nprepre=9", editor.getText());
        assertTrue(editor.undo());
        assertEquals("pre42 pre7\npre9", editor.getText());
    }

    @Test public void literalReplaceAllFiltersEveryInsertionOnce() {
        CodeEditor editor = createEditor("a a");
        editor.setSearchText("a");
        int[] calls = {0};
        editor.setInputFilter((source, character) -> ++calls[0] <= 1);
        assertEquals(2, editor.replaceAllSearchMatches("X"));
        assertEquals("X ", editor.getText());
        assertEquals(2, calls[0]);
    }

    @Test public void identicalRangeReplacementsPreserveSelectionVersionAndRedo() {
        CodeEditor editor = createEditor("alpha\nbeta");
        assertTrue(editor.insertTextAtCursor("!"));
        assertTrue(editor.undo());
        editor.setSelection(1, 2, 0, 1);
        CodeEditorTextRange selection = editor.getSelection();
        int version = editor.getDocumentVersion();
        int[] events = {0};
        editor.addContentListener((source, event) -> events[0]++);
        assertFalse(editor.replaceRange(0, 0, 1, 4, "alpha\r\nbeta"));
        assertEquals(version, editor.getDocumentVersion());
        assertEquals(selection, editor.getSelection());
        assertEquals(0, editor.getCursorLine());
        assertEquals(1, editor.getCursorColumn());
        assertEquals(1, editor.getRedoEntryCount());
        assertEquals(0, events[0]);
    }

    @Test public void unchangedRegularBatchDoesNotMoveCaretInsideItsRange() {
        CodeEditor editor = createEditor("alpha");
        editor.setSelection(0, 4, 0, 2);
        int version = editor.getDocumentVersion();
        assertFalse(editor.applyEdits(Array.with(new CodeEditorTextEdit(0, 0, 0, 5, "alpha"))));
        assertEquals(version, editor.getDocumentVersion());
        assertEquals(new CodeEditorTextRange(0, 2, 0, 4), editor.getSelection());
        assertEquals(2, editor.getCursorColumn());
        assertEquals(0, editor.getUndoEntryCount());
    }

    @Test public void mixedBatchDoesNotTrackCaretAcrossAnUnchangedReplacement() {
        CodeEditor editor = createEditor("alpha beta");
        editor.setCursorPosition(0, 2);
        assertTrue(editor.applyEdits(Array.with(
            new CodeEditorTextEdit(0, 0, 0, 5, "alpha"), CodeEditorTextEdit.insert(0, 10, "!"))));
        assertEquals("alpha beta!", editor.getText());
        assertEquals(2, editor.getCursorColumn());
    }

    @Test public void unchangedSearchReplacementPreservesTheActiveMatchAndSelection() {
        CodeEditor editor = createEditor("alpha alpha");
        editor.setSearchText("alpha");
        assertTrue(editor.findNextSearchMatch());
        int version = editor.getDocumentVersion();
        assertFalse(editor.replaceCurrentSearchMatch("alpha"));
        assertEquals(version, editor.getDocumentVersion());
        assertEquals(1, editor.getCurrentSearchMatchOrdinal());
        assertEquals(0, editor.getUndoEntryCount());
    }

    @Test public void unchangedReplaceAllPreservesFoldsHistorySelectionAndRedo() {
        CodeEditor editor = createEditor("{\nalpha\nalpha\n}");
        assertTrue(editor.insertTextAtCursor("!"));
        assertTrue(editor.undo());
        assertEquals(1, editor.collapseAllFolds());
        editor.setSelection(3, 1, 0, 0);
        editor.setSearchText("alpha");
        int version = editor.getDocumentVersion();
        int[] events = {0};
        editor.addContentListener((source, event) -> events[0]++);
        assertEquals(0, editor.replaceAllSearchMatches("alpha"));
        assertEquals(version, editor.getDocumentVersion());
        assertEquals(new CodeEditorTextRange(0, 0, 3, 1), editor.getSelection());
        assertTrue(editor.isFoldCollapsed(0));
        assertEquals(1, editor.getRedoEntryCount());
        assertEquals(0, events[0]);
    }

    @Test public void replaceAllCountsOnlyChangedMatchesAfterGroupExpansion() {
        CodeEditor editor = createEditor("a ab");
        editor.setSearchRegexEnabled(true);
        editor.setSearchText("a(b?)");
        assertEquals(1, editor.replaceAllSearchMatches("a"));
        assertEquals("a a", editor.getText());
        assertEquals(1, editor.getUndoEntryCount());
    }

    @Test public void aFilterCannotChangeTheDocumentAndApplyOutdatedBatchCoordinates() {
        CodeEditor editor = createEditor("abc");
        editor.setInputFilter((source, character) -> { source.setText("new"); return true; });
        assertFalse(editor.applyEdits(Array.with(new CodeEditorTextEdit(0, 0, 0, 1, "X"))));
        assertEquals("new", editor.getText());
        assertEquals(0, editor.getUndoEntryCount());
    }

    @Test public void filtersCannotMakeTheEditorReadOnlyAndStillReplaceText() {
        CodeEditor editor = createEditor("abc abc");
        editor.setSearchText("abc");
        editor.setInputFilter((source, character) -> { source.setReadOnly(true); return true; });
        assertFalse(editor.replaceCurrentSearchMatch("X"));
        assertEquals("abc abc", editor.getText());
        editor.setReadOnly(false);
        assertEquals(0, editor.replaceAllSearchMatches("X"));
        assertEquals("abc abc", editor.getText());
        editor.setReadOnly(false);
        assertFalse(editor.replaceRange(0, 0, 0, 3, "X"));
        assertEquals("abc abc", editor.getText());
        assertEquals(0, editor.getUndoEntryCount());
    }

    @Test public void replaceAllExpandsOnlyFoldsWhoseMatchedTextChanges() {
        CodeEditor editor = createEditor("{\nalpha\n}\n{\nab\n}");
        assertEquals(2, editor.collapseAllFolds());
        editor.setSearchRegexEnabled(true);
        editor.setSearchText("a(lpha|b)");
        assertEquals(1, editor.replaceAllSearchMatches("alpha"));
        assertEquals("{\nalpha\n}\n{\nalpha\n}", editor.getText());
        assertTrue(editor.isFoldCollapsed(0));
        assertFalse(editor.isFoldCollapsed(3));
    }

    @Test public void batchUsesItsValidatedSnapshotEvenWhenAFilterChangesTheCallerArray() {
        CodeEditor editor = createEditor("abc");
        Array<CodeEditorTextEdit> edits = Array.with(
            new CodeEditorTextEdit(0, 0, 0, 1, "X"), new CodeEditorTextEdit(0, 2, 0, 3, "Y"));
        editor.setInputFilter((source, character) -> { edits.clear(); return true; });
        assertTrue(editor.applyEdits(edits));
        assertEquals("XbY", editor.getText());
        assertEquals(1, editor.getUndoEntryCount());
    }

    @Test public void searchFiltersCannotRewriteTheDocumentThenApplyOldMatches() {
        CodeEditor editor = createEditor("abc abc");
        editor.setSearchText("abc");
        editor.setInputFilter((source, character) -> { source.setText("new"); return true; });
        assertFalse(editor.replaceCurrentSearchMatch("X"));
        assertEquals("new", editor.getText());
        editor.setText("abc abc");
        assertEquals(0, editor.replaceAllSearchMatches("X"));
        assertEquals("new", editor.getText());
        assertEquals(0, editor.getUndoEntryCount());
    }
}
