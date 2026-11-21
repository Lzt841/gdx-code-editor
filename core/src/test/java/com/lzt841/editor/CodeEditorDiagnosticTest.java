package com.lzt841.editor;

import com.badlogic.gdx.Input;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.utils.Array;
import com.lzt841.editor.input.CodeEditorInteractionMode;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

public class CodeEditorDiagnosticTest {
    private CodeEditor editor;

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
        editor.setText("first\nsecond\nthird");
    }

    @Test
    public void rejectsOutdatedBatchesWithoutDiscardingCurrentMarks() {
        int oldVersion = editor.getDocumentVersion();
        assertTrue(editor.insertTextAtCursor("X"));
        CodeDiagnostic current = diagnostic(1, 0, CodeDiagnosticSeverity.WARNING);
        assertTrue(editor.setDiagnostics(Array.with(current), editor.getDocumentVersion()));
        assertFalse(editor.setDiagnostics(Array.with(diagnostic(0, 0, CodeDiagnosticSeverity.ERROR)), oldVersion));
        assertSame(current, editor.getDiagnostics().first());
        assertEquals(editor.getDocumentVersion(), editor.getDiagnosticVersion());
        assertFalse(editor.isDiagnosticsStale());
    }

    @Test
    public void tracksStalenessEvenForEmptyBatchesAndResetsWhenCleared() {
        assertEquals(-1, editor.getDiagnosticVersion());
        assertFalse(editor.isDiagnosticsStale());
        editor.setDiagnostics(new Array<CodeDiagnostic>());
        int version = editor.getDiagnosticVersion();
        assertTrue(editor.insertTextAtCursor("X"));
        assertTrue(editor.isDiagnosticsStale());
        assertEquals(version, editor.getDiagnosticVersion());
        editor.clearDiagnostics();
        assertFalse(editor.isDiagnosticsStale());
        assertEquals(-1, editor.getDiagnosticVersion());
    }

    @Test
    public void navigatesUnsortedMarksInDocumentOrderAndWrapsBothWays() {
        CodeDiagnostic first = diagnostic(0, 1, CodeDiagnosticSeverity.WARNING);
        CodeDiagnostic second = diagnostic(1, 2, CodeDiagnosticSeverity.ERROR);
        CodeDiagnostic third = diagnostic(2, 1, CodeDiagnosticSeverity.HINT);
        editor.setDiagnostics(Array.with(third, first, second));
        int version = editor.getDocumentVersion();
        assertSame(first, editor.navigateToNextDiagnostic());
        assertSame(second, editor.navigateToNextDiagnostic());
        assertSame(third, editor.navigateToNextDiagnostic());
        assertSame(first, editor.navigateToNextDiagnostic());
        assertSame(third, editor.navigateToPreviousDiagnostic());
        assertSame(second, editor.navigateToPreviousDiagnostic());
        assertSame(first, editor.navigateToPreviousDiagnostic());
        assertEquals(0, editor.getCursorLine());
        assertEquals(1, editor.getCursorColumn());
        assertEquals(version, editor.getDocumentVersion());
        assertEquals(0, editor.getUndoEntryCount());
    }

    @Test
    public void filtersSeverityAndChoosesMostSevereMessageAtOnePosition() {
        CodeDiagnostic warning = diagnostic(0, 1, CodeDiagnosticSeverity.WARNING);
        CodeDiagnostic error = diagnostic(0, 1, CodeDiagnosticSeverity.ERROR);
        CodeDiagnostic hint = diagnostic(1, 0, CodeDiagnosticSeverity.HINT);
        CodeDiagnostic laterError = diagnostic(2, 0, CodeDiagnosticSeverity.ERROR);
        editor.setDiagnostics(Array.with(warning, hint, laterError, error));
        assertSame(error, editor.navigateToNextDiagnostic());
        assertSame(laterError, editor.navigateToNextDiagnostic(CodeDiagnosticSeverity.ERROR));
        assertSame(error, editor.navigateToNextDiagnostic(CodeDiagnosticSeverity.WARNING));
        assertSame(laterError, editor.navigateToPreviousDiagnostic(CodeDiagnosticSeverity.ERROR));

        editor.setDiagnostics(Array.with(hint));
        CodeEditorPosition caret = editor.getCursorPosition();
        assertNull(editor.navigateToNextDiagnostic(CodeDiagnosticSeverity.WARNING));
        assertEquals(caret.line, editor.getCursorLine());
        assertEquals(caret.column, editor.getCursorColumn());
    }

    @Test
    public void navigationExpandsFoldsAndWorksInReadOnlyMode() {
        editor.setText("{\nproblem\n}");
        assertEquals(1, editor.collapseAllFolds());
        CodeDiagnostic target = diagnostic(1, 1, CodeDiagnosticSeverity.ERROR);
        editor.setDiagnostics(Array.with(target));
        editor.setReadOnly(true);
        assertSame(target, editor.navigateToNextDiagnostic());
        assertFalse(editor.isFoldCollapsed(0));
        assertEquals(1, editor.getCursorLine());
        assertEquals(1, editor.getCursorColumn());
    }

    @Test
    public void staleOrDisabledNavigationPreservesSelectionAndCaret() {
        editor.setDiagnostics(Array.with(diagnostic(1, 0, CodeDiagnosticSeverity.ERROR)));
        editor.insertTextAtCursor("X");
        editor.setSelection(0, 0, 0, 2);
        CodeEditorTextRange selection = editor.getSelection();
        assertNull(editor.navigateToNextDiagnostic());
        assertEquals(selection, editor.getSelection());
        editor.setDiagnostics(Array.with(diagnostic(1, 0, CodeDiagnosticSeverity.ERROR)));
        editor.setDisabled(true);
        assertNull(editor.navigateToPreviousDiagnostic());
        assertEquals(selection, editor.getSelection());
    }

    @Test
    public void skipsInvalidRangesAndKeepsSupplementaryBoundariesSafe() {
        editor.setText("A\uD83D\uDE42B");
        CodeDiagnostic onPair = diagnostic(0, 2, CodeDiagnosticSeverity.ERROR);
        CodeDiagnostic earlier = diagnostic(0, 0, CodeDiagnosticSeverity.WARNING);
        editor.setDiagnostics(Array.with(null, onPair,
            diagnostic(-1, 0, CodeDiagnosticSeverity.ERROR),
            diagnostic(1, 0, CodeDiagnosticSeverity.ERROR),
            new CodeDiagnostic(0, 3, 0, 1, CodeDiagnosticSeverity.ERROR, "inverted"),
            diagnostic(0, 99, CodeDiagnosticSeverity.ERROR), earlier));
        assertSame(onPair, editor.navigateToNextDiagnostic());
        assertEquals(3, editor.getCursorColumn());
        assertSame(earlier, editor.navigateToPreviousDiagnostic());
        assertEquals(0, editor.getCursorColumn());
    }

    @Test
    public void acceptsLiveArrayWithoutClearingItAndSkipsNullMarks() {
        CodeDiagnostic mark = diagnostic(0, 1, CodeDiagnosticSeverity.ERROR);
        editor.setDiagnostics(Array.with(null, mark));
        assertEquals(1, editor.getDiagnostics().size);
        editor.setDiagnostics(editor.getDiagnostics());
        assertEquals(1, editor.getDiagnostics().size);
        assertSame(mark, editor.getDiagnostics().first());
    }

    @Test
    public void defaultShortcutsDispatchDiagnosticNavigation() {
        CodeKeymap map = editor.getKeymap();
        assertEquals(CodeEditorAction.DIAGNOSTIC_NEXT, map.actionForKeyDown(Input.Keys.F8, false, false, false));
        assertEquals(CodeEditorAction.DIAGNOSTIC_PREVIOUS, map.actionForKeyDown(Input.Keys.F8, false, true, false));
        assertNull(map.actionForKeyDown(Input.Keys.F8, true, false, false));
        CodeDiagnostic first = diagnostic(0, 1, CodeDiagnosticSeverity.ERROR);
        CodeDiagnostic second = diagnostic(1, 1, CodeDiagnosticSeverity.WARNING);
        editor.setDiagnostics(Array.with(second, first));
        assertTrue(editor.performAction(CodeEditorAction.DIAGNOSTIC_NEXT));
        assertEquals(0, editor.getCursorLine());
        assertEquals(1, editor.getCursorColumn());
        assertTrue(editor.performAction(CodeEditorAction.DIAGNOSTIC_PREVIOUS));
        assertEquals(1, editor.getCursorLine());
        assertEquals(1, editor.getCursorColumn());
    }

    private CodeDiagnostic diagnostic(int line, int column, CodeDiagnosticSeverity severity) {
        return new CodeDiagnostic(line, column, line, column + 1, severity, "diagnostic");
    }
}
