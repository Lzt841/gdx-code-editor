package com.lzt841.editor;

import com.badlogic.gdx.utils.Array;
import org.junit.Test;

import static com.lzt841.editor.EditorTestSupport.createEditor;
import static org.junit.Assert.*;

public class CodeEditorDiagnosticRevisionTest {
    @Test public void acceptedBatchesAndClearAdvanceIndependentRevisionEvenAtTheSameDocumentVersion() {
        CodeEditor editor = createEditor("text");
        int version = editor.getDocumentVersion();
        long initial = editor.getDiagnosticRevision();
        int[] events = {0};
        editor.addDiagnosticListener((source, revision) -> {
            assertSame(editor, source);
            assertEquals(initial + ++events[0], revision);
            assertEquals(revision, source.getDiagnosticRevision());
        });
        Array<CodeDiagnostic> marks = Array.with(mark());
        assertTrue(editor.setDiagnostics(marks, version));
        assertTrue(editor.setDiagnostics(marks, version));
        assertTrue(editor.setDiagnostics(editor.getDiagnostics(), version));
        editor.clearDiagnostics();
        editor.clearDiagnostics();
        assertEquals(initial + 5, editor.getDiagnosticRevision());
        assertEquals(5, events[0]);
        assertEquals(version, editor.getDocumentVersion());
        assertEquals(-1, editor.getDiagnosticVersion());
        assertEquals(0, editor.getUndoEntryCount());
    }

    @Test public void rejectedOutdatedBatchDoesNotAdvanceOrPublishOrReplaceTheExistingBatch() {
        CodeEditor editor = createEditor("text");
        editor.setDiagnostics(Array.with(mark()));
        long revision = editor.getDiagnosticRevision();
        int[] events = {0};
        editor.addDiagnosticListener((source, batch) -> events[0]++);
        assertFalse(editor.setDiagnostics(null, editor.getDocumentVersion() - 1));
        assertEquals(revision, editor.getDiagnosticRevision());
        assertEquals(1, editor.getDiagnostics().size);
        assertEquals(0, events[0]);
    }

    @Test public void duplicateRegistrationAndSelfRemovalAreSafe() {
        CodeEditor editor = createEditor("text");
        int[] events = {0};
        CodeEditorDiagnosticListener[] listener = new CodeEditorDiagnosticListener[1];
        listener[0] = (source, revision) -> {
            events[0]++;
            source.removeDiagnosticListener(listener[0]);
        };
        editor.addDiagnosticListener(null);
        editor.addDiagnosticListener(listener[0]);
        editor.addDiagnosticListener(listener[0]);
        editor.clearDiagnostics();
        editor.clearDiagnostics();
        assertEquals(1, events[0]);
    }

    @Test public void nestedPublicationHasIndependentListenerSnapshotsAndLiveRevision() {
        CodeEditor editor = createEditor("text");
        long initial = editor.getDiagnosticRevision();
        Array<Long> observed = new Array<>();
        boolean[] once = {false};
        editor.addDiagnosticListener((source, revision) -> {
            if (!once[0]) {
                once[0] = true;
                source.clearDiagnostics();
            }
        });
        editor.addDiagnosticListener((source, revision) -> {
            observed.add(revision);
            assertEquals(initial + 2, source.getDiagnosticRevision());
        });
        editor.setDiagnostics(Array.with(mark()));
        assertEquals(Array.with(initial + 2, initial + 1), observed);
        assertEquals(0, editor.getDiagnostics().size);
        assertEquals(-1, editor.getDiagnosticVersion());
    }

    @Test public void nullBatchIsAnAcceptedEmptyAnalysisAtTheCurrentVersion() {
        CodeEditor editor = createEditor("text");
        long before = editor.getDiagnosticRevision();
        assertTrue(editor.setDiagnostics(null, editor.getDocumentVersion()));
        assertEquals(before + 1, editor.getDiagnosticRevision());
        assertEquals(editor.getDocumentVersion(), editor.getDiagnosticVersion());
        assertFalse(editor.isDiagnosticsStale());
    }

    private static CodeDiagnostic mark() {
        return new CodeDiagnostic(0, 0, 0, 1, CodeDiagnosticSeverity.WARNING, "test", "fix", null);
    }
}
