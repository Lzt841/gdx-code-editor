package com.lzt841.editor.codeactions;

import org.junit.Test;

import static org.junit.Assert.*;

public class CodeDiagnosticFixTest {
    @Test
    public void defaultsToPreferredAndRetainsExactRuleStrings() {
        CodeDiagnosticFix fix = new CodeDiagnosticFix(" token ", " Fix token ", "\u03B2\r\n\uD83D\uDE00");
        assertEquals(" token ", fix.diagnosticCode);
        assertEquals(" Fix token ", fix.title);
        assertEquals("\u03B2\r\n\uD83D\uDE00", fix.replacement);
        assertTrue(fix.preferred);
    }

    @Test
    public void allowsNonPreferredRulesAndEmptyReplacementForDeletion() {
        CodeDiagnosticFix fix = new CodeDiagnosticFix("unused", "Remove unused token", "", false);
        assertEquals("", fix.replacement);
        assertFalse(fix.preferred);
    }

    @Test
    public void rejectsNullAndBlankDiagnosticCodesAndTitles() {
        String[] invalid = {null, "", " \t\n"};
        for (String value : invalid) {
            expectIllegalArgument(() -> new CodeDiagnosticFix(value, "Fix", "replacement"));
            expectIllegalArgument(() -> new CodeDiagnosticFix("token", value, "replacement", false));
        }
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsNullReplacement() {
        new CodeDiagnosticFix("token", "Fix", null);
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
