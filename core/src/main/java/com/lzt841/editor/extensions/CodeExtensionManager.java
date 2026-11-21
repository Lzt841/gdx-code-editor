package com.lzt841.editor.extensions;

import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Disposable;
import com.badlogic.gdx.utils.ObjectMap;
import com.lzt841.editor.CodeCommandRegistry;
import com.lzt841.editor.CodeEditor;
import com.lzt841.editor.completion.CodeCompletionRegistry;

/**
 * Per-editor extension lifecycle. Use {@link CodeEditor#getExtensions()} for the shared instance.
 * All operations run on the GDX thread. Lifecycle callbacks may register scoped resources but must
 * not install/uninstall extensions reentrantly. clear() is reusable; dispose() ends this manager.
 */
public final class CodeExtensionManager implements Disposable {
    private final CodeEditor editor;
    private final ObjectMap<String, Entry> installed = new ObjectMap<>();
    private final Array<Entry> order = new Array<>();
    private CodeCommandRegistry commands;
    private CodeCompletionRegistry completionProviders;
    private boolean changing;
    private boolean disposed;

    public CodeExtensionManager(CodeEditor editor) {
        if (editor == null) throw new IllegalArgumentException("editor is required");
        this.editor = editor;
    }

    public CodeCommandRegistry getCommands() {
        if (disposed) throw new IllegalStateException("extension manager is disposed");
        if (commands == null) commands = new CodeCommandRegistry(editor);
        return commands;
    }

    public CodeCompletionRegistry getCompletionProviders() {
        if (disposed) throw new IllegalStateException("extension manager is disposed");
        if (completionProviders == null) completionProviders = new CodeCompletionRegistry();
        return completionProviders;
    }

    public CodeExtensionContext install(CodeEditorExtension extension) {
        requireMutable();
        if (extension == null) throw new IllegalArgumentException("extension is required");
        String id = extension.getId();
        if (id == null || id.trim().isEmpty() || !id.equals(id.trim())) {
            throw new IllegalArgumentException("extension id must be non-blank without surrounding whitespace");
        }
        if (installed.containsKey(id)) throw new IllegalArgumentException("duplicate extension: " + id);
        changing = true;
        CodeExtensionContext context = new CodeExtensionContext(editor, this);
        try {
            extension.install(context);
            Entry entry = new Entry(id, extension, context);
            installed.put(id, entry);
            order.add(entry);
            return context;
        } catch (RuntimeException | Error exception) {
            try {
                context.close();
            } catch (RuntimeException | Error cleanupFailure) {
                combine(exception, cleanupFailure);
            }
            throw exception;
        } finally {
            changing = false;
        }
    }

    public boolean isInstalled(String id) {
        return id != null && installed.containsKey(id);
    }

    public Array<String> getInstalledIds() {
        Array<String> ids = new Array<>(order.size);
        for (Entry entry : order) ids.add(entry.id);
        return ids;
    }

    public boolean uninstall(String id) {
        requireMutable();
        Entry entry = id == null ? null : installed.get(id);
        if (entry == null) return false;
        changing = true;
        try {
            remove(entry);
            return true;
        } finally {
            changing = false;
        }
    }

    /** Removes extensions in reverse installation order, retaining directly registered host commands. */
    public void clear() {
        requireMutable();
        changing = true;
        try {
            clearEntries();
        } finally {
            changing = false;
        }
    }

    public boolean isDisposed() {
        return disposed;
    }

    @Override
    public void dispose() {
        if (disposed) return;
        requireMutable();
        changing = true;
        disposed = true;
        Throwable failure = null;
        try { clearEntries(); } catch (RuntimeException | Error exception) { failure = exception; }
        try {
            if (commands != null) commands.dispose();
        } catch (RuntimeException | Error exception) { failure = combine(failure, exception); }
        try {
            if (completionProviders != null) completionProviders.dispose();
        } catch (RuntimeException | Error exception) { failure = combine(failure, exception); }
        finally { changing = false; }
        rethrow(failure);
    }

    private void clearEntries() {
        Throwable failure = null;
        while (order.size > 0) {
            try {
                remove(order.peek());
            } catch (RuntimeException | Error exception) {
                failure = combine(failure, exception);
            }
        }
        rethrow(failure);
    }

    private void remove(Entry entry) {
        installed.remove(entry.id);
        order.removeValue(entry, true);
        Throwable failure = null;
        try {
            entry.context.close();
        } catch (RuntimeException | Error exception) {
            failure = exception;
        }
        try {
            entry.extension.uninstall();
        } catch (RuntimeException | Error exception) {
            failure = combine(failure, exception);
        }
        rethrow(failure);
    }

    private void requireMutable() {
        if (disposed) throw new IllegalStateException("extension manager is disposed");
        if (changing) throw new IllegalStateException("extension lifecycle changes cannot be reentrant");
    }

    static Throwable combine(Throwable first, Throwable next) {
        if (first == null) return next;
        if (first != next) first.addSuppressed(next);
        return first;
    }

    static void rethrow(Throwable failure) {
        if (failure instanceof RuntimeException) throw (RuntimeException) failure;
        if (failure instanceof Error) throw (Error) failure;
    }

    private static final class Entry {
        final String id;
        final CodeEditorExtension extension;
        final CodeExtensionContext context;

        Entry(String id, CodeEditorExtension extension, CodeExtensionContext context) {
            this.id = id;
            this.extension = extension;
            this.context = context;
        }
    }
}
