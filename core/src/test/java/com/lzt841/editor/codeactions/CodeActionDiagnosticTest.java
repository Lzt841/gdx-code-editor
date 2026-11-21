package com.lzt841.editor.codeactions;

import com.lzt841.editor.CodeDiagnostic;
import com.lzt841.editor.CodeDiagnosticSeverity;
import com.lzt841.editor.CodeEditorTextRange;
import org.junit.Test;

import static org.junit.Assert.*;

public class CodeActionDiagnosticTest {
    @Test
    public void snapshotsRangeAndMetadataWithoutProducerSubclassState() {
        final StringBuilder producerState = new StringBuilder("producer state");
        CodeDiagnostic diagnostic = new CodeDiagnostic(2, 3, 4, 5, CodeDiagnosticSeverity.WARNING,
            "Unknown token", "unknown-token", producerState) {
            @Override
            public boolean covers(int line, int column) {
                throw new AssertionError("Snapshot consumers must not call diagnostic subclasses");
            }
        };
        CodeActionDiagnostic snapshot = new CodeActionDiagnostic(diagnostic);
        producerState.setLength(0);

        assertEquals(new CodeEditorTextRange(2, 3, 4, 5), snapshot.range);
        assertEquals(CodeDiagnosticSeverity.WARNING, snapshot.severity);
        assertEquals("Unknown token", snapshot.message);
        assertEquals("unknown-token", snapshot.code);
        assertTrue(snapshot.range.contains(2, 3));
    }

    @Test
    public void retainsDiagnosticDefaultsAndOptionalCode() {
        CodeActionDiagnostic snapshot = new CodeActionDiagnostic(new CodeDiagnostic(0, 1, 0, 1,
            null, null));
        assertTrue(snapshot.range.isEmpty());
        assertEquals(CodeDiagnosticSeverity.ERROR, snapshot.severity);
        assertEquals("", snapshot.message);
        assertNull(snapshot.code);
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsNullDiagnostic() {
        new CodeActionDiagnostic(null);
    }
}
