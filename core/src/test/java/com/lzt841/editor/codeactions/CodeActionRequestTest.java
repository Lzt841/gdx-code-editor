package com.lzt841.editor.codeactions;

import com.lzt841.editor.CodeDiagnostic;
import com.lzt841.editor.CodeDiagnosticSeverity;
import com.lzt841.editor.CodeEditorTextRange;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.*;

public class CodeActionRequestTest {
    @Test
    public void retainsVersionsCaretAndUtf16SelectionFromTheLfSnapshot() {
        String text = "a\uD83D\uDE00\n\u03B2eta\nend";
        CodeEditorTextRange range = new CodeEditorTextRange(0, 1, 1, 4);
        long revision = (long) Integer.MAX_VALUE + 42;
        CodeActionRequest request = new CodeActionRequest(text, 11, 10, revision,
            1, 4, range, 1, 8, Collections.<CodeActionDiagnostic>emptyList());

        assertEquals(text, request.text);
        assertEquals(11, request.documentVersion);
        assertEquals(10, request.diagnosticVersion);
        assertEquals(revision, request.diagnosticRevision);
        assertEquals(1, request.line);
        assertEquals(4, request.column);
        assertEquals(range, request.range);
        assertEquals(1, request.startOffset);
        assertEquals(8, request.endOffset);
        assertEquals("\uD83D\uDE00\n\u03B2eta", request.getRangeText());
    }

    @Test
    public void changesToTheSourceDiagnosticListCannotMutateTheSnapshot() {
        CodeActionDiagnostic first = diagnostic("first");
        CodeActionDiagnostic second = diagnostic("second");
        List<CodeActionDiagnostic> source = new ArrayList<>(Arrays.asList(first, second));
        CodeActionRequest request = request(source);
        source.set(0, diagnostic("replacement"));
        source.clear();

        assertEquals(2, request.diagnostics.size());
        assertSame(first, request.diagnostics.get(0));
        assertSame(second, request.diagnostics.get(1));
    }

    @Test
    public void diagnosticListCannotBeMutatedByConsumers() {
        CodeActionDiagnostic diagnostic = diagnostic("token");
        CodeActionRequest request = request(Collections.singletonList(diagnostic));
        expectUnsupported(() -> request.diagnostics.add(diagnostic));
        expectUnsupported(() -> request.diagnostics.set(0, diagnostic("other")));
        expectUnsupported(() -> request.diagnostics.remove(0));
        expectUnsupported(() -> request.diagnostics.clear());
        assertEquals(1, request.diagnostics.size());
        assertSame(diagnostic, request.diagnostics.get(0));
    }

    @Test
    public void emptyRangeAtCaretReturnsEmptyText() {
        String text = "\uD83D\uDE00\n\u03B2x";
        CodeEditorTextRange caret = new CodeEditorTextRange(1, 2, 1, 2);
        CodeActionRequest request = new CodeActionRequest(text, 1, 1, 1L,
            1, 2, caret, text.length(), text.length(), Collections.<CodeActionDiagnostic>emptyList());
        assertTrue(request.range.isEmpty());
        assertEquals("", request.getRangeText());
        assertEquals(1, request.line);
        assertEquals(2, request.column);
    }

    @Test
    public void emptyDiagnosticsAreAValidImmutableSnapshot() {
        List<CodeActionDiagnostic> source = new ArrayList<>();
        CodeActionRequest request = request(source);
        source.add(diagnostic("later"));
        assertTrue(request.diagnostics.isEmpty());
        expectUnsupported(() -> request.diagnostics.add(diagnostic("other")));
    }

    @Test
    public void rejectsNullSnapshotPartsAndNullDiagnosticEntries() {
        CodeEditorTextRange range = new CodeEditorTextRange(0, 0, 0, 0);
        List<CodeActionDiagnostic> empty = Collections.emptyList();
        expectIllegalArgument(() -> new CodeActionRequest(null, 1, 1, 1L,
            0, 0, range, 0, 0, empty));
        expectIllegalArgument(() -> new CodeActionRequest("", 1, 1, 1L,
            0, 0, null, 0, 0, empty));
        expectIllegalArgument(() -> new CodeActionRequest("", 1, 1, 1L,
            0, 0, range, 0, 0, null));
        expectIllegalArgument(() -> request(Arrays.asList(diagnostic("valid"), null)));
    }

    private static CodeActionRequest request(List<CodeActionDiagnostic> diagnostics) {
        return new CodeActionRequest("token", 1, 1, 1L, 0, 2,
            new CodeEditorTextRange(0, 2, 0, 2), 2, 2, diagnostics);
    }

    private static CodeActionDiagnostic diagnostic(String code) {
        return new CodeActionDiagnostic(new CodeDiagnostic(0, 0, 0, 5,
            CodeDiagnosticSeverity.ERROR, "Token", code, new Object()));
    }

    private static void expectUnsupported(Runnable operation) {
        try {
            operation.run();
            fail("Expected UnsupportedOperationException");
        } catch (UnsupportedOperationException expected) {
            // The request exposes a read-only diagnostic list.
        }
    }

    private static void expectIllegalArgument(Runnable operation) {
        try {
            operation.run();
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            // Expected validation failure.
        }
    }
}
