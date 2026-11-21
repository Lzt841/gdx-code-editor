package com.lzt841.editor.codeactions;

import com.badlogic.gdx.utils.Array;
import com.lzt841.editor.CodeEditorTextEdit;

/**
 * Generic, thread-safe diagnostic fixes with no language heuristics or producer user-data commands.
 * Each matching diagnostic/rule pair produces one quick fix, in diagnostic order then rule order.
 */
public final class DiagnosticCodeActionProvider implements CodeActionProvider {
    private final CodeDiagnosticFix[] fixes;

    public DiagnosticCodeActionProvider(CodeDiagnosticFix... fixes) {
        if (fixes == null) {
            throw new IllegalArgumentException("fixes must not be null");
        }
        this.fixes = fixes.clone();
        for (CodeDiagnosticFix fix : this.fixes) {
            if (fix == null) {
                throw new IllegalArgumentException("fixes must not contain null");
            }
        }
    }

    @Override
    public void provideActions(CodeActionRequest request, CodeActionResponse response) {
        if (response == null || response.isCancelled()) {
            return;
        }
        if (request == null) {
            response.fail("Invalid code-action request.");
            return;
        }

        Array<CodeAction> actions = new Array<>();
        for (int diagnosticIndex = 0; diagnosticIndex < request.diagnostics.size(); diagnosticIndex++) {
            if (response.isCancelled()) return;
            CodeActionDiagnostic diagnostic = request.diagnostics.get(diagnosticIndex);
            for (int ruleIndex = 0; ruleIndex < fixes.length; ruleIndex++) {
                if (response.isCancelled()) return;
                CodeDiagnosticFix fix = fixes[ruleIndex];
                if (!fix.diagnosticCode.equals(diagnostic.code)) continue;

                Array<CodeEditorTextEdit> edits = new Array<>(1);
                edits.add(new CodeEditorTextEdit(diagnostic.range, fix.replacement));
                actions.add(new CodeAction("diagnostic:" + diagnosticIndex + ":fix:" + ruleIndex,
                    fix.title, CodeActionKind.QUICK_FIX, fix.preferred, null, edits));
            }
        }
        if (!response.isCancelled()) response.complete(actions);
    }
}
