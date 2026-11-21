package com.lzt841.editor.codeactions;

import com.badlogic.gdx.utils.Array;
import com.lzt841.editor.CodeDiagnostic;
import com.lzt841.editor.CodeDiagnosticSeverity;
import com.lzt841.editor.CodeEditorTextEdit;
import com.lzt841.editor.CodeEditorTextRange;
import org.junit.Test;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import static org.junit.Assert.*;

public class DiagnosticCodeActionProviderTest {
    @Test
    public void onlyExactMatchingDiagnosticCodesProduceQuickFixes() {
        String text = "bad bad bad bad";
        DiagnosticCodeActionProvider provider = new DiagnosticCodeActionProvider(
            new CodeDiagnosticFix("bad-token", "Replace token", "good"));
        CodeActionRequest request = request(text,
            diagnostic(0, 0, 0, 3, "bad-token"),
            diagnostic(0, 4, 0, 7, "BAD-TOKEN"),
            diagnostic(0, 8, 0, 11, "bad-token "),
            diagnostic(0, 12, 0, 15, null));
        Capture result = provide(provider, request);

        assertEquals(1, result.actions.size);
        CodeAction action = result.actions.first();
        assertEquals("Replace token", action.title);
        assertEquals(CodeActionKind.QUICK_FIX, action.kind);
        assertTrue(action.preferred);
        assertTrue(action.isEnabled());
        assertEquals(1, action.getEdits().size);
        assertEquals(new CodeEditorTextRange(0, 0, 0, 3), action.getEdits().first().range);
        assertEquals("good bad bad bad", apply(text, action));
    }

    @Test
    public void multipleSuggestionsKeepRuleOrderTitlesAndPreferredMetadata() {
        DiagnosticCodeActionProvider provider = new DiagnosticCodeActionProvider(
            new CodeDiagnosticFix("token", "Use first suggestion", "first"),
            new CodeDiagnosticFix("token", "Use another suggestion", "second", false));
        Capture result = provide(provider, request("bad", diagnostic(0, 0, 0, 3, "token")));

        assertEquals(2, result.actions.size);
        assertEquals("Use first suggestion", result.actions.get(0).title);
        assertEquals("first", result.actions.get(0).getEdits().first().replacement);
        assertTrue(result.actions.get(0).preferred);
        assertEquals("Use another suggestion", result.actions.get(1).title);
        assertEquals("second", result.actions.get(1).getEdits().first().replacement);
        assertFalse(result.actions.get(1).preferred);
    }

    @Test
    public void everyDiagnosticRulePairHasADeterministicUniqueId() {
        DiagnosticCodeActionProvider provider = new DiagnosticCodeActionProvider(
            new CodeDiagnosticFix("other", "Other", "other"),
            new CodeDiagnosticFix("token", "Same title", "same"),
            new CodeDiagnosticFix("token", "Same title", "same"));
        CodeActionRequest request = request("bad bad",
            diagnostic(0, 0, 0, 3, "unmatched"),
            diagnostic(0, 0, 0, 3, "token"),
            diagnostic(0, 4, 0, 7, "token"));
        Capture first = provide(provider, request);
        Capture second = provide(provider, request);
        Set<String> ids = new HashSet<>();

        assertEquals(4, first.actions.size);
        for (int i = 0; i < first.actions.size; i++) {
            CodeAction action = first.actions.get(i);
            assertTrue("Each pair needs its own id", ids.add(action.id));
            assertEquals(action.id, second.actions.get(i).id);
            assertEquals(i < 2 ? 0 : 4, action.getEdits().first().range.startColumn);
        }
    }

    @Test
    public void zeroWidthDiagnosticProducesAnInsertionAtItsExactPosition() {
        DiagnosticCodeActionProvider provider = new DiagnosticCodeActionProvider(
            new CodeDiagnosticFix("missing-semicolon", "Insert semicolon", ";"));
        String text = "fn()\n";
        Capture result = provide(provider, request(text,
            diagnostic(0, 4, 0, 4, "missing-semicolon")));
        assertEquals(1, result.actions.size);
        assertTrue(result.actions.first().getEdits().first().range.isEmpty());
        assertEquals("fn();\n", apply(text, result.actions.first()));
    }

    @Test
    public void emptyReplacementProducesAnExactDeletion() {
        DiagnosticCodeActionProvider provider = new DiagnosticCodeActionProvider(
            new CodeDiagnosticFix("unused", "Remove token", ""));
        String text = "unused keep\n";
        Capture result = provide(provider, request(text, diagnostic(0, 0, 0, 7, "unused")));
        assertEquals(1, result.actions.size);
        assertEquals("", result.actions.first().getEdits().first().replacement);
        assertEquals("keep\n", apply(text, result.actions.first()));
    }

    @Test
    public void unicodeTokenReplacementKeepsOtherTextAndTerminalNewlinesExact() {
        DiagnosticCodeActionProvider provider = new DiagnosticCodeActionProvider(
            new CodeDiagnosticFix("token", "Replace Unicode token", "fixed\uD801\uDC00"));
        for (String suffix : new String[] {"", "\n"}) {
            String text = "head\uD83D\uDE00\nwrong\uD83D\uDE00\n\u5C3E" + suffix;
            Capture result = provide(provider, request(text, diagnostic(1, 0, 1, 7, "token")));
            assertEquals(1, result.actions.size);
            assertEquals(new CodeEditorTextRange(1, 0, 1, 7),
                result.actions.first().getEdits().first().range);
            assertEquals("head\uD83D\uDE00\nfixed\uD801\uDC00\n\u5C3E" + suffix,
                apply(text, result.actions.first()));
        }
    }

    @Test
    public void replacementTextIsLiteralWithoutUnicodeOrNewlineRewriting() {
        String replacement = "\u03B2\r\n\uD83D\uDE00\n";
        DiagnosticCodeActionProvider provider = new DiagnosticCodeActionProvider(
            new CodeDiagnosticFix("token", "Replace literally", replacement));
        String text = "bad\nkeep";
        Capture result = provide(provider, request(text, diagnostic(0, 0, 0, 3, "token")));
        assertEquals(replacement, result.actions.first().getEdits().first().replacement);
        assertEquals(replacement + "\nkeep", apply(text, result.actions.first()));
    }

    @Test
    public void sourceRuleArrayAndEarlierResultsCannotMutateLaterComputations() {
        CodeDiagnosticFix[] rules = {new CodeDiagnosticFix("token", "Fix", "good")};
        DiagnosticCodeActionProvider provider = new DiagnosticCodeActionProvider(rules);
        rules[0] = null;
        CodeActionRequest request = request("bad", diagnostic(0, 0, 0, 3, "token"));
        Capture first = provide(provider, request);
        assertEquals(1, first.actions.size);
        first.actions.first().getEdits().clear();
        first.actions.clear();

        Capture second = provide(provider, request);
        assertEquals(1, second.actions.size);
        assertEquals("Fix", second.actions.first().title);
        assertEquals("good", apply("bad", second.actions.first()));
    }

    @Test
    public void emptyRuleSetCompletesWithNoActions() {
        Capture result = provide(new DiagnosticCodeActionProvider(),
            request("bad", diagnostic(0, 0, 0, 3, "token")));
        assertEquals(0, result.actions.size);
    }

    @Test
    public void emptyDiagnosticsAndUnmatchedCodesCompleteWithNoActions() {
        DiagnosticCodeActionProvider provider = new DiagnosticCodeActionProvider(
            new CodeDiagnosticFix("token", "Fix", "good"));
        CodeActionRequest[] requests = {
            request("bad"),
            request("bad", diagnostic(0, 0, 0, 3, "other")),
            request("bad", diagnostic(0, 0, 0, 3, null))
        };
        for (CodeActionRequest request : requests) {
            assertEquals(0, provide(provider, request).actions.size);
        }
    }

    @Test
    public void cancellationBeforeComputationProducesNoCompletionOrFailure() {
        DiagnosticCodeActionProvider provider = new DiagnosticCodeActionProvider(
            new CodeDiagnosticFix("token", "Fix", "good"));
        Capture capture = new Capture();
        capture.cancelled = true;
        provider.provideActions(request("bad", diagnostic(0, 0, 0, 3, "token")), capture);
        assertEquals(0, capture.completions);
        assertNull(capture.failure);
    }

    @Test
    public void cancellationDuringRuleScanningDoesNotDeliverPartialActions() {
        DiagnosticCodeActionProvider provider = new DiagnosticCodeActionProvider(
            new CodeDiagnosticFix("token", "First", "first"),
            new CodeDiagnosticFix("token", "Second", "second"),
            new CodeDiagnosticFix("token", "Third", "third"));
        Capture capture = new Capture();
        capture.cancelAtCheck = 4;
        provider.provideActions(request("bad", diagnostic(0, 0, 0, 3, "token")), capture);
        assertEquals(4, capture.cancellationChecks);
        assertEquals(0, capture.completions);
        assertNull(capture.failure);
    }

    @Test
    public void cancellationBeforeDeliveryAlsoSuppressesEmptyResults() {
        Capture capture = new Capture();
        capture.cancelAtCheck = 2;
        new DiagnosticCodeActionProvider().provideActions(request(""), capture);
        assertEquals(2, capture.cancellationChecks);
        assertEquals(0, capture.completions);
        assertNull(capture.failure);
    }

    @Test
    public void rejectsNullRuleArraysAndNullRules() {
        expectIllegalArgument(() -> new DiagnosticCodeActionProvider((CodeDiagnosticFix[]) null));
        expectIllegalArgument(() -> new DiagnosticCodeActionProvider(
            new CodeDiagnosticFix("token", "Fix", "good"), null));
    }

    private static Capture provide(DiagnosticCodeActionProvider provider, CodeActionRequest request) {
        Capture capture = new Capture();
        provider.provideActions(request, capture);
        assertNull(capture.failure);
        assertEquals(1, capture.completions);
        assertNotNull(capture.actions);
        return capture;
    }

    private static CodeActionRequest request(String text, CodeActionDiagnostic... diagnostics) {
        return new CodeActionRequest(text, 7, 7, 9L, 0, 0,
            new CodeEditorTextRange(0, 0, 0, 0), 0, 0, Arrays.asList(diagnostics));
    }

    private static CodeActionDiagnostic diagnostic(int startLine, int startColumn,
                                                    int endLine, int endColumn, String code) {
        return new CodeActionDiagnostic(new CodeDiagnostic(startLine, startColumn, endLine, endColumn,
            CodeDiagnosticSeverity.ERROR, "Host diagnostic", code, new Object()));
    }

    private static String apply(String text, CodeAction action) {
        Array<CodeEditorTextEdit> edits = action.getEdits();
        assertEquals("Each generic diagnostic action replaces only its diagnostic range", 1, edits.size);
        CodeEditorTextEdit edit = edits.first();
        int start = offset(text, edit.range.startLine, edit.range.startColumn);
        int end = offset(text, edit.range.endLine, edit.range.endColumn);
        return text.substring(0, start) + edit.replacement + text.substring(end);
    }

    private static int offset(String text, int line, int column) {
        int start = 0;
        for (int currentLine = 0; currentLine < line; currentLine++) {
            int newline = text.indexOf('\n', start);
            if (newline < 0) throw new AssertionError("Missing source line " + line);
            start = newline + 1;
        }
        int end = text.indexOf('\n', start);
        if (end < 0) end = text.length();
        if (column < 0 || column > end - start) {
            throw new AssertionError("Column outside source line: " + line + ":" + column);
        }
        return start + column;
    }

    private static void expectIllegalArgument(Runnable operation) {
        try {
            operation.run();
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            // Expected validation failure.
        }
    }

    private static final class Capture implements CodeActionResponse {
        private Array<CodeAction> actions;
        private String failure;
        private int completions;
        private boolean cancelled;
        private int cancellationChecks;
        private int cancelAtCheck;

        @Override
        public void complete(Array<CodeAction> actions) {
            this.actions = actions;
            completions++;
        }

        @Override
        public void fail(String message) {
            failure = message;
        }

        @Override
        public boolean isCancelled() {
            cancellationChecks++;
            return cancelled || cancelAtCheck > 0 && cancellationChecks >= cancelAtCheck;
        }
    }
}
