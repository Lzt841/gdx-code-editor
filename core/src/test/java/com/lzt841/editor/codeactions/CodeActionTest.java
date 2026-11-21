package com.lzt841.editor.codeactions;

import com.badlogic.gdx.utils.Array;
import com.lzt841.editor.CodeEditorTextEdit;
import org.junit.Test;

import static org.junit.Assert.*;

public class CodeActionTest {
    @Test
    public void simpleConstructorDefaultsToAnEnabledNonPreferredQuickFix() {
        CodeAction action = new CodeAction("fix", "Fix token", new Array<CodeEditorTextEdit>());
        assertEquals("fix", action.id);
        assertEquals("Fix token", action.title);
        assertEquals(CodeActionKind.QUICK_FIX, action.kind);
        assertFalse(action.preferred);
        assertNull(action.disabledReason);
        assertTrue(action.isEnabled());
        assertEquals(0, action.getEdits().size);
    }

    @Test
    public void sourceAndReturnedArrayChangesCannotMutateTheAction() {
        CodeEditorTextEdit original = CodeEditorTextEdit.insert(0, 0, "fixed");
        Array<CodeEditorTextEdit> source = new Array<>();
        source.add(original);
        CodeAction action = new CodeAction("fix", "Fix token", source);

        source.set(0, CodeEditorTextEdit.insert(0, 0, "different"));
        source.clear();
        Array<CodeEditorTextEdit> returned = action.getEdits();
        assertEquals(1, returned.size);
        assertSame(original, returned.first());
        returned.set(0, CodeEditorTextEdit.insert(1, 1, "changed"));
        returned.clear();

        assertEquals(1, action.getEdits().size);
        assertSame(original, action.getEdits().first());
        assertNotSame(action.getEdits(), action.getEdits());
    }

    @Test
    public void fullConstructorRetainsKindPreferenceAndDisabledExplanation() {
        CodeAction action = new CodeAction(" source ", " Organize source ", CodeActionKind.SOURCE,
            true, "Requires configuration", new Array<CodeEditorTextEdit>());
        assertEquals(" source ", action.id);
        assertEquals(" Organize source ", action.title);
        assertEquals(CodeActionKind.SOURCE, action.kind);
        assertTrue(action.preferred);
        assertEquals("Requires configuration", action.disabledReason);
        assertFalse(action.isEnabled());
    }

    @Test
    public void onlyNullAndEmptyDisabledReasonsMeanEnabled() {
        String[] reasons = {null, "", " ", "Unavailable"};
        for (int i = 0; i < reasons.length; i++) {
            CodeAction action = new CodeAction("refactor", "Refactor", CodeActionKind.REFACTOR,
                false, reasons[i], new Array<CodeEditorTextEdit>());
            assertEquals(i < 2, action.isEnabled());
        }
    }

    @Test
    public void emptyAndNoOpEditBatchesAreAllowed() {
        CodeAction disabled = new CodeAction("disabled", "Unavailable", CodeActionKind.QUICK_FIX,
            true, "Not applicable", new Array<CodeEditorTextEdit>());
        assertFalse(disabled.isEnabled());
        assertEquals(0, disabled.getEdits().size);

        Array<CodeEditorTextEdit> edits = new Array<>();
        edits.add(CodeEditorTextEdit.insert(0, 0, ""));
        CodeAction noOp = new CodeAction("noop", "Already fixed", edits);
        assertTrue(noOp.isEnabled());
        assertEquals(1, noOp.getEdits().size);
        assertTrue(noOp.getEdits().first().isNoOp());
    }

    @Test
    public void coordinateValidationIsLeftToTheController() {
        Array<CodeEditorTextEdit> edits = new Array<>();
        edits.add(new CodeEditorTextEdit(-1, -2, -3, -4, "fixed"));
        CodeAction action = new CodeAction("fix", "Fix token", edits);
        assertEquals(-1, action.getEdits().first().range.startLine);
        assertEquals(-4, action.getEdits().first().range.endColumn);
    }

    @Test
    public void rejectsNullAndBlankIdsAndTitles() {
        String[] invalid = {null, "", " \t\n"};
        for (String value : invalid) {
            expectIllegalArgument(() -> new CodeAction(value, "Fix", new Array<CodeEditorTextEdit>()));
            expectIllegalArgument(() -> new CodeAction("fix", value, new Array<CodeEditorTextEdit>()));
        }
    }

    @Test
    public void rejectsNullKindNullEditArrayAndNullEditEntries() {
        expectIllegalArgument(() -> new CodeAction("fix", "Fix", null, false, null,
            new Array<CodeEditorTextEdit>()));
        expectIllegalArgument(() -> new CodeAction("fix", "Fix", null));
        Array<CodeEditorTextEdit> edits = new Array<>();
        edits.add(CodeEditorTextEdit.insert(0, 0, "valid"));
        edits.add(null);
        expectIllegalArgument(() -> new CodeAction("fix", "Fix", edits));
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
