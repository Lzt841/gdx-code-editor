package com.lzt841.editor.extensions;

import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Disposable;
import com.lzt841.editor.CodeEditor;
import com.lzt841.editor.CodeEditorCaretListener;
import com.lzt841.editor.CodeEditorCommand;
import com.lzt841.editor.CodeEditorContentListener;
import com.lzt841.editor.CodeEditorInputInterceptor;
import com.lzt841.editor.CodeKeyStroke;
import com.lzt841.editor.completion.CodeCompletionProvider;
import com.lzt841.editor.completion.CodeCompletionRegistry;

/** Resources registered through this scope are released in reverse order on uninstall or rollback. */
public final class CodeExtensionContext {
    private final CodeEditor editor;
    private final CodeExtensionManager manager;
    private final Array<Disposable> resources = new Array<>();
    private boolean disposed;

    CodeExtensionContext(CodeEditor editor, CodeExtensionManager manager) {
        this.editor = editor;
        this.manager = manager;
    }

    public CodeEditor getEditor() {
        return editor;
    }

    public boolean isDisposed() {
        return disposed;
    }

    /** Owns a resource once by identity. This scope calls dispose even when another cleanup fails. */
    public <T extends Disposable> T own(T resource) {
        requireActive();
        if (resource == null) throw new IllegalArgumentException("resource is required");
        if (!resources.contains(resource, true)) resources.add(resource);
        return resource;
    }

    /** Useful for controllers with uninstall(), asynchronous request cancellation or overlay actors. */
    public void onDispose(Runnable cleanup) {
        if (cleanup == null) throw new IllegalArgumentException("cleanup is required");
        own((Disposable) cleanup::run);
    }

    public Disposable registerCommand(CodeEditorCommand command, CodeKeyStroke... shortcuts) {
        requireActive();
        return own(manager.getCommands().register(command, shortcuts));
    }

    public Disposable registerCompletionProvider(String id, CodeCompletionProvider provider) {
        return registerCompletionProvider(id, provider, null);
    }

    /** Removes this contribution on unload/rollback; the provider itself remains host-owned. */
    public Disposable registerCompletionProvider(String id, CodeCompletionProvider provider,
                                                 CodeCompletionRegistry.Condition condition) {
        requireActive();
        return own(manager.getCompletionProviders().register(id, provider, condition));
    }

    public void addContentListener(CodeEditorContentListener listener) {
        requireListener(listener);
        CodeEditorContentListener wrapper = (source, event) -> {
            if (!disposed) listener.onContentChanged(source, event);
        };
        editor.addContentListener(wrapper);
        own((Disposable) () -> editor.removeContentListener(wrapper));
    }

    public void addCaretListener(CodeEditorCaretListener listener) {
        requireListener(listener);
        CodeEditorCaretListener wrapper = (source, position, selection, causedByEdit) -> {
            if (!disposed) listener.onCaretMoved(source, position, selection, causedByEdit);
        };
        editor.addCaretListener(wrapper);
        own((Disposable) () -> editor.removeCaretListener(wrapper));
    }

    public void addInputInterceptor(CodeEditorInputInterceptor listener) {
        requireListener(listener);
        CodeEditorInputInterceptor wrapper = new CodeEditorInputInterceptor() {
            @Override
            public boolean onKeyDown(CodeEditor source, int keycode) {
                return !disposed && listener.onKeyDown(source, keycode);
            }

            @Override
            public void onKeyUp(CodeEditor source, int keycode) {
                if (!disposed) listener.onKeyUp(source, keycode);
            }

            @Override
            public boolean onKeyTyped(CodeEditor source, char character) {
                return !disposed && listener.onKeyTyped(source, character);
            }

            @Override
            public void afterKeyTyped(CodeEditor source, char character) {
                if (!disposed) listener.afterKeyTyped(source, character);
            }
        };
        editor.addInputInterceptor(wrapper);
        own((Disposable) () -> editor.removeInputInterceptor(wrapper));
    }

    void close() {
        if (disposed) return;
        disposed = true;
        Throwable failure = null;
        while (resources.size > 0) {
            try {
                resources.pop().dispose();
            } catch (RuntimeException | Error exception) {
                failure = CodeExtensionManager.combine(failure, exception);
            }
        }
        CodeExtensionManager.rethrow(failure);
    }

    private void requireListener(Object listener) {
        requireActive();
        if (listener == null) throw new IllegalArgumentException("listener is required");
    }

    private void requireActive() {
        if (disposed) throw new IllegalStateException("extension context is disposed");
    }
}
