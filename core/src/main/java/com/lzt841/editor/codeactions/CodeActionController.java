package com.lzt841.editor.codeactions;

import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Disposable;
import com.lzt841.editor.CodeDiagnostic;
import com.lzt841.editor.CodeEditor;
import com.lzt841.editor.CodeEditorCaretListener;
import com.lzt841.editor.CodeEditorContentListener;
import com.lzt841.editor.CodeEditorDiagnosticListener;
import com.lzt841.editor.CodeEditorTextEdit;
import com.lzt841.editor.CodeEditorTextRange;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Optional current-document code actions. Discovery and preview never change the document; the host
 * explicitly applies a returned action. Use this controller and deliver responses on the GDX thread.
 * Providers may compute from immutable requests on workers and poll response.isCancelled().
 */
public final class CodeActionController implements Disposable {
    private static final Comparator<CodeEditorTextEdit> EDIT_ORDER = (left, right) -> {
        int start = compare(left.range.startLine, left.range.startColumn,
            right.range.startLine, right.range.startColumn);
        return start != 0 ? start : compare(left.range.endLine, left.range.endColumn,
            right.range.endLine, right.range.endColumn);
    };

    private final CodeEditor editor;
    private final CodeEditorContentListener contentListener = (source, event) -> invalidateStale();
    private final CodeEditorCaretListener caretListener = (source, position, selection, byEdit) -> invalidateStale();
    private final CodeEditorDiagnosticListener diagnosticListener = (source, revision) -> invalidateStale();
    private CodeActionProvider provider;
    private CodeActionListener listener;
    private Session current;
    private Session applying;
    private long serial;
    private boolean disposed;

    public CodeActionController(CodeEditor editor, CodeActionProvider provider) {
        if (editor == null || provider == null) throw new IllegalArgumentException("editor and provider are required");
        this.editor = editor;
        this.provider = provider;
        editor.addContentListener(contentListener);
        editor.addCaretListener(caretListener);
        editor.addDiagnosticListener(diagnosticListener);
    }

    public CodeEditor getEditor() { return editor; }
    public CodeActionProvider getProvider() { return provider; }
    public boolean isDisposed() { return disposed; }
    public void setListener(CodeActionListener listener) { this.listener = listener; }

    /** The host owns the provider. Replacing it invalidates both pending and retained actions. */
    public void setProvider(CodeActionProvider provider) {
        if (provider == null) throw new IllegalArgumentException("provider is required");
        cancel();
        this.provider = provider;
    }

    public boolean isPending() {
        invalidateStale();
        return current != null && !current.delivered;
    }

    /** Current pending or retained snapshot, or null after cancellation/invalidation. */
    public CodeActionRequest getRequest() {
        invalidateStale();
        return current == null ? null : current.request;
    }

    /** Provider-order snapshot. The actions and their edits are immutable. */
    public Array<CodeAction> getActions() {
        invalidateStale();
        return current == null ? new Array<>() : new Array<>(current.actions);
    }

    public int getActionCount() {
        invalidateStale();
        return current == null ? 0 : current.actions.size;
    }

    /** Returns whether the provider was dispatched, including synchronous empty results or failure. */
    public boolean requestActions() {
        if (disposed) return false;
        cancel();
        long generation = serial;
        editor.flushPendingContentChanges();
        // A flush listener may dispose us, change modes, replace the provider or start a newer request.
        if (generation != serial || !isAvailable()) return false;
        CodeEditorTextRange range = liveRange();
        CodeActionRequest request = new CodeActionRequest(editor.getText(), editor.getDocumentVersion(),
            editor.getDiagnosticVersion(), editor.getDiagnosticRevision(), editor.getCursorLine(),
            editor.getCursorColumn(), range, editor.toOffset(range.startLine, range.startColumn),
            editor.toOffset(range.endLine, range.endColumn), relevantDiagnostics(range));
        Session session = new Session(request);
        current = session;
        try {
            provider.provideActions(request, session);
        } catch (RuntimeException | Error failure) {
            retire(session);
            throw failure;
        }
        return true;
    }

    /** Cancels pending work and discards retained actions. No status callback is emitted. */
    public void cancel() {
        serial++;
        if (current != null) retire(current);
    }

    @Override public void dispose() {
        if (disposed) return;
        disposed = true;
        cancel();
        editor.removeContentListener(contentListener);
        editor.removeCaretListener(caretListener);
        editor.removeDiagnosticListener(diagnosticListener);
    }

    /** Only exact action objects from the current result may be applied, once and while still current. */
    public boolean canApply(CodeAction action) {
        invalidateStale();
        return current != null && current != applying && current.delivered && action != null
            && action.isEnabled() && current.actions.contains(action, true) && isCurrent(current);
    }

    /** Exactly one enabled preferred action is required; ambiguity is never resolved by array order. */
    public boolean canApplyPreferred() { return preferredAction() != null; }

    public boolean applyPreferred() {
        CodeAction action = preferredAction();
        return action != null && apply(action);
    }

    /**
     * Returns the prospective complete text, or null for an unavailable action. No filter runs and
     * nothing is mutated; an input filter can still reject the real application. Edits may touch any
     * part of this document (for example an import), not just the original request range.
     */
    public String preview(CodeAction action) {
        if (!canApply(action)) return null;
        Array<CodeEditorTextEdit> ordered = action.getEdits();
        ordered.sort(EDIT_ORDER);
        StringBuilder result = new StringBuilder(current.request.text);
        for (int i = ordered.size - 1; i >= 0; i--) {
            CodeEditorTextEdit edit = ordered.get(i);
            int from = editor.toOffset(edit.range.startLine, edit.range.startColumn);
            int to = editor.toOffset(edit.range.endLine, edit.range.endColumn);
            result.replace(from, to, edit.replacement);
        }
        return result.toString();
    }

    /**
     * Applies an explicitly chosen action as one exact, named undo step. Returns true for a successful
     * no-op as well as a changed document. Filters run once; the final guard checks ownership, text,
     * diagnostics, caret, selection and availability after filters. Success consumes the result set.
     */
    public boolean apply(CodeAction action) {
        if (!canApply(action)) return false;
        Session session = current;
        Array<CodeEditorTextEdit> edits = action.getEdits();
        boolean changed = false;
        for (CodeEditorTextEdit edit : edits) {
            if (!edit.replacement.equals(editor.getTextRange(edit.range))) changed = true;
        }
        if (!changed) {
            applied(session, action, false);
            return true;
        }
        boolean committed;
        Session previousApplying = applying;
        applying = session;
        try {
            committed = editor.applyEditsExact(edits, action.title, () -> isCurrent(session));
        } catch (RuntimeException | Error failure) {
            retire(session);
            throw failure;
        } finally {
            applying = previousApplying;
        }
        if (committed) {
            applied(session, action, true);
        } else if (isCurrent(session)) {
            fail(session, "Code action was rejected by the editor input filter.");
        } else {
            retire(session);
        }
        return committed;
    }

    private CodeAction preferredAction() {
        invalidateStale();
        if (current == null || current == applying || !current.delivered) return null;
        CodeAction preferred = null;
        for (CodeAction action : current.actions) {
            if (!action.isEnabled() || !action.preferred) continue;
            if (preferred != null) return null;
            preferred = action;
        }
        return preferred;
    }

    private boolean isAvailable() {
        return !disposed && !editor.isDisabled() && !editor.isReadOnly() && !editor.isPasswordMode()
            && !editor.isCompoundEditInProgress();
    }

    private CodeEditorTextRange liveRange() {
        CodeEditorTextRange selection = editor.getSelection();
        return selection != null ? selection : new CodeEditorTextRange(editor.getCursorLine(),
            editor.getCursorColumn(), editor.getCursorLine(), editor.getCursorColumn());
    }

    private boolean isCurrent(Session session) {
        return current == session && !session.cancelled && isAvailable()
            && editor.getDocumentVersion() == session.request.documentVersion
            && editor.getDiagnosticRevision() == session.request.diagnosticRevision
            && editor.getCursorLine() == session.request.line && editor.getCursorColumn() == session.request.column
            && session.request.range.equals(liveRange());
    }

    private void invalidateStale() {
        // Our own content/caret notifications occur after mutation, before applied() can report success.
        // Only this identity is protected: a newer request from a host callback still has live checks.
        if (current != null && current != applying && !isCurrent(current)) {
            serial++;
            retire(current);
        }
    }

    private void retire(Session session) {
        session.cancelled = true;
        if (current == session) current = null;
    }

    private List<CodeActionDiagnostic> relevantDiagnostics(CodeEditorTextRange range) {
        List<CodeActionDiagnostic> found = new ArrayList<>();
        if (editor.isDiagnosticsStale()) return found;
        for (CodeDiagnostic diagnostic : editor.getDiagnostics()) {
            if (diagnostic == null) continue;
            CodeEditorTextRange mark = new CodeEditorTextRange(diagnostic.startLine, diagnostic.startColumn,
                diagnostic.endLine, diagnostic.endColumn);
            // Reuse the editor's strict bounds and UTF-16 boundary validation, without filtering.
            if (editor.canApplyEdits(Array.with(CodeEditorTextEdit.delete(mark))) && intersects(range, mark)) {
                found.add(new CodeActionDiagnostic(diagnostic));
            }
        }
        return found;
    }

    private void accept(Session session, Array<CodeAction> actions) {
        if (!isCurrent(session)) { retire(session); return; }
        if (actions == null) { fail(session, "Code action provider returned null actions."); return; }
        Array<CodeAction> snapshot = new Array<>(actions);
        Set<String> ids = new HashSet<>();
        for (CodeAction action : snapshot) {
            if (action == null || !ids.add(action.id)) {
                fail(session, "Code action provider returned null actions or duplicate IDs.");
                return;
            }
            Array<CodeEditorTextEdit> edits = action.getEdits();
            if (edits.size > 0 && !editor.canApplyEdits(edits)) {
                fail(session, "Code action provider returned invalid or overlapping edits.");
                return;
            }
            for (CodeEditorTextEdit edit : edits) {
                if (edit.replacement.indexOf('\r') >= 0) {
                    fail(session, "Code action replacements must use LF newlines.");
                    return;
                }
            }
        }
        session.actions = snapshot;
        if (listener != null) listener.onActionsReady(this, session.request, new Array<>(snapshot));
    }

    private void applied(Session session, CodeAction action, boolean changed) {
        if (current != session || session.cancelled || disposed) { retire(session); return; }
        retire(session);
        if (listener != null) listener.onActionApplied(this, session.request, action, changed);
    }

    private void fail(Session session, String message) {
        if (!isCurrent(session)) { retire(session); return; }
        retire(session);
        if (listener != null) listener.onCodeActionFailed(this, session.request,
            message == null || message.isEmpty() ? "Code action failed." : message);
    }

    private static boolean intersects(CodeEditorTextRange query, CodeEditorTextRange diagnostic) {
        if (query.isEmpty()) {
            return diagnostic.isEmpty() ? query.equals(diagnostic)
                : diagnostic.contains(query.startLine, query.startColumn);
        }
        if (diagnostic.isEmpty()) return query.contains(diagnostic.startLine, diagnostic.startColumn);
        return compare(query.startLine, query.startColumn, diagnostic.endLine, diagnostic.endColumn) < 0
            && compare(diagnostic.startLine, diagnostic.startColumn, query.endLine, query.endColumn) < 0;
    }

    private static int compare(int line, int column, int otherLine, int otherColumn) {
        return line == otherLine ? Integer.compare(column, otherColumn) : Integer.compare(line, otherLine);
    }

    private final class Session implements CodeActionResponse {
        final CodeActionRequest request;
        Array<CodeAction> actions = new Array<>();
        volatile boolean delivered;
        volatile boolean cancelled;

        Session(CodeActionRequest request) { this.request = request; }

        @Override public void complete(Array<CodeAction> actions) {
            if (delivered || cancelled) return;
            delivered = true;
            accept(this, actions);
        }

        @Override public void fail(String message) {
            if (delivered || cancelled) return;
            delivered = true;
            CodeActionController.this.fail(this, message);
        }

        @Override public boolean isCancelled() { return cancelled || delivered; }
    }
}
