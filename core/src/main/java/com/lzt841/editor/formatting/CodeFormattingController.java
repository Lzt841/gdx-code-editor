package com.lzt841.editor.formatting;

import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Disposable;
import com.lzt841.editor.CodeEditor;
import com.lzt841.editor.CodeEditorContentListener;
import com.lzt841.editor.CodeEditorTextEdit;
import com.lzt841.editor.CodeEditorTextRange;
import com.lzt841.editor.CodeIndentStrategy;

/**
 * Optional document/selection formatting service. Use all controller methods on the GDX thread.
 * Requests contain immutable snapshots; providers may compute asynchronously and post answers back.
 * Dispose the controller (or its owning extension) to cancel work and release its editor listener.
 */
public final class CodeFormattingController implements Disposable {
    private final CodeEditor editor;
    private final CodeEditorContentListener contentListener = (source, event) -> {
        if (this.pending != null && this.pending != this.applying
            && this.pending.request.documentVersion != source.getDocumentVersion()) cancel();
    };
    private CodeFormattingProvider provider;
    private CodeFormattingListener listener;
    private Sink pending;
    private long serial;
    private Sink applying;
    private boolean disposed;

    public CodeFormattingController(CodeEditor editor, CodeFormattingProvider provider) {
        if (editor == null || provider == null) {
            throw new IllegalArgumentException("editor and provider are required");
        }
        this.editor = editor;
        this.provider = provider;
        editor.addContentListener(contentListener);
    }

    public CodeEditor getEditor() { return editor; }
    public CodeFormattingProvider getProvider() { return provider; }
    public boolean isDisposed() { return disposed; }
    public void setListener(CodeFormattingListener listener) { this.listener = listener; }

    /** Replaces the host-owned provider and cancels any outstanding answer from the old provider. */
    public void setProvider(CodeFormattingProvider provider) {
        if (provider == null) throw new IllegalArgumentException("provider is required");
        cancel();
        this.provider = provider;
    }

    public boolean isPending() {
        if (pending != null && pending != applying && !isCurrent(pending)) cancel();
        return pending != null;
    }

    /** Returns whether a provider was dispatched, including a synchronous no-op or reported failure. */
    public boolean formatDocument() { return request(false); }

    /** Requires a nonempty selection; moving that selection invalidates the pending response. */
    public boolean formatSelection() { return request(true); }

    private boolean request(boolean selectionOnly) {
        if (disposed) return false;
        cancel();
        long generation = serial;
        editor.flushPendingContentChanges();
        // Flushing may invoke host code that disposes this service or starts a newer request.
        if (generation != serial || !isAvailable()) return false;
        CodeEditorTextRange range = editor.getSelection();
        if (!selectionOnly) {
            int lastLine = editor.getLineCount() - 1;
            range = new CodeEditorTextRange(0, 0, lastLine, editor.getLineLength(lastLine));
        } else if (range == null || range.isEmpty()) {
            return false;
        }
        String text = editor.getText();
        CodeIndentStrategy indent = editor.getIndentStrategy();
        CodeFormattingRequest snapshot = new CodeFormattingRequest(text, editor.getDocumentVersion(), range,
            editor.toOffset(range.startLine, range.startColumn), editor.toOffset(range.endLine, range.endColumn),
            selectionOnly, indent.indentWidth, indent.useTabs);
        Sink sink = new Sink(snapshot);
        pending = sink;
        try {
            provider.format(snapshot, sink);
        } catch (RuntimeException | Error failure) {
            // A callback may already have started a new request. Only retire this invocation.
            retire(sink);
            throw failure;
        }
        return true;
    }

    /** Cancellation is silent; providers can observe it through their response. */
    public void cancel() {
        serial++;
        if (pending != null) {
            pending.cancelled = true;
            pending = null;
        }
    }

    @Override public void dispose() {
        if (disposed) return;
        disposed = true;
        cancel();
        editor.removeContentListener(contentListener);
    }

    private boolean isAvailable() {
        return !disposed && !editor.isDisabled() && !editor.isReadOnly() && !editor.isPasswordMode()
            && !editor.isCompoundEditInProgress();
    }

    private boolean isCurrent(Sink sink) {
        return pending == sink && !sink.cancelled && isAvailable()
            && editor.getDocumentVersion() == sink.request.documentVersion
            && (!sink.request.selectionOnly || sink.request.range.equals(editor.getSelection()));
    }

    private void retire(Sink sink) {
        sink.cancelled = true;
        if (pending == sink) pending = null;
    }

    private void accept(Sink sink, Array<CodeEditorTextEdit> edits) {
        if (!isCurrent(sink)) { retire(sink); return; }
        if (edits == null) { fail(sink, "Formatter returned null edits."); return; }
        // Never retain a provider-owned mutable array across input-filter callbacks.
        Array<CodeEditorTextEdit> batch = new Array<>(edits);
        if (batch.size == 0) { formatted(sink, false); return; }
        if (!editor.canApplyEdits(batch)) { fail(sink, "Formatter returned invalid or overlapping edits."); return; }
        boolean changed = false;
        for (CodeEditorTextEdit edit : batch) {
            if (compare(edit.range.startLine, edit.range.startColumn,
                sink.request.range.startLine, sink.request.range.startColumn) < 0
                || compare(edit.range.endLine, edit.range.endColumn,
                    sink.request.range.endLine, sink.request.range.endColumn) > 0) {
                fail(sink, "Formatter returned edits outside the requested range.");
                return;
            }
            if (!edit.replacement.equals(editor.getTextRange(edit.range))) changed = true;
        }
        if (!changed) { formatted(sink, false); return; }
        boolean applied;
        Sink previousApplying = applying;
        applying = sink;
        try {
            applied = editor.applyEditsExact(batch,
                sink.request.selectionOnly ? "Format Selection" : "Format Document", () -> isCurrent(sink));
        } catch (RuntimeException | Error failure) {
            retire(sink);
            throw failure;
        } finally {
            applying = previousApplying;
        }
        if (applied) {
            formatted(sink, true);
        } else if (isCurrent(sink)) {
            fail(sink, "Formatting was rejected by the editor input filter.");
        } else {
            retire(sink);
        }
    }

    private void formatted(Sink sink, boolean changed) {
        if (pending != sink || sink.cancelled || disposed) { retire(sink); return; }
        pending = null;
        if (listener != null) listener.onFormatted(this, sink.request, changed);
    }

    private void fail(Sink sink, String message) {
        if (!isCurrent(sink)) { retire(sink); return; }
        pending = null;
        if (listener != null) listener.onFormattingFailed(this, sink.request,
            message == null || message.isEmpty() ? "Formatting failed." : message);
    }

    private static int compare(int line, int column, int otherLine, int otherColumn) {
        return line == otherLine ? Integer.compare(column, otherColumn) : Integer.compare(line, otherLine);
    }

    private final class Sink implements CodeFormattingResponse {
        private final CodeFormattingRequest request;
        private volatile boolean delivered;
        private volatile boolean cancelled;

        private Sink(CodeFormattingRequest request) { this.request = request; }

        @Override public void complete(Array<CodeEditorTextEdit> edits) {
            if (delivered || cancelled) return;
            delivered = true;
            // delivered is deliberately separate from the current-request commit predicate.
            accept(this, edits);
        }

        @Override public void fail(String message) {
            if (delivered || cancelled) return;
            delivered = true;
            CodeFormattingController.this.fail(this, message);
        }

        @Override public boolean isCancelled() { return cancelled || delivered; }
    }
}
