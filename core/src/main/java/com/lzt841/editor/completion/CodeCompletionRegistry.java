package com.lzt841.editor.completion;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Disposable;
import com.badlogic.gdx.utils.ObjectMap;
import com.badlogic.gdx.utils.ObjectSet;

/**
 * Named, independently removable completion sources. All calls and responses use the GDX thread.
 * Each request snapshots the sources and waits for their one-shot responses, then merges in
 * registration order. Changes invalidate pending requests and notify installed controllers.
 * Providers are borrowed; removing a registration does not dispose the provider itself.
 */
public final class CodeCompletionRegistry implements CodeCompletionProvider, Disposable {
    public interface Condition {
        boolean isEnabled(CodeCompletionRequest request);
    }

    public interface FailureListener {
        void failed(String providerId, RuntimeException exception);
    }

    private final ObjectMap<String, Registration> providers = new ObjectMap<>();
    private final Array<Registration> order = new Array<>();
    private final Array<Pending> pending = new Array<>();
    private final Array<ChangeSubscription> listeners = new Array<>();
    private long revision;
    private boolean disposed;
    private FailureListener failureListener;

    public Disposable register(String id, CodeCompletionProvider provider) {
        return register(id, provider, null);
    }

    /** Conditions are evaluated per request; trigger characters are the union of all sources. */
    public Disposable register(String id, CodeCompletionProvider provider, Condition condition) {
        if (disposed) throw new IllegalStateException("completion registry is disposed");
        if (id == null || id.trim().isEmpty() || !id.equals(id.trim())) {
            throw new IllegalArgumentException("provider id must be non-blank without surrounding whitespace");
        }
        if (provider == null || provider instanceof CodeCompletionRegistry) {
            throw new IllegalArgumentException("register individual providers, not another completion registry");
        }
        if (providers.containsKey(id)) throw new IllegalArgumentException("duplicate completion provider: " + id);
        Registration registration = new Registration(id, provider, condition);
        providers.put(id, registration);
        order.add(registration);
        changed();
        return registration;
    }

    public int size() { return providers.size; }
    public long getRevision() { return revision; }
    public boolean isDisposed() { return disposed; }

    /** Invalidates cached/pending results after host state used by provider conditions changes. */
    public void invalidate() {
        if (disposed) throw new IllegalStateException("completion registry is disposed");
        changed();
    }

    public CodeCompletionProvider getProvider(String id) {
        Registration registration = id == null ? null : providers.get(id);
        return registration == null ? null : registration.provider;
    }

    public Array<String> getProviderIds() {
        Array<String> ids = new Array<>(order.size);
        for (Registration registration : order) ids.add(registration.id);
        return ids;
    }

    /** Null restores logging through Gdx.app.error when an application is available. */
    public void setFailureListener(FailureListener listener) { failureListener = listener; }

    /** Called after a registration change, clear or disposal; dispose the subscription to detach. */
    public Disposable addChangeListener(Runnable listener) {
        if (disposed) throw new IllegalStateException("completion registry is disposed");
        if (listener == null) throw new IllegalArgumentException("listener is required");
        ChangeSubscription subscription = new ChangeSubscription(listener);
        listeners.add(subscription);
        return subscription;
    }

    @Override public void provide(CodeCompletionRequest request, CodeCompletionResponse response) {
        if (request == null || response == null) throw new IllegalArgumentException("request and response are required");
        // A provider that never returns must not retain every superseded controller request.
        for (Pending previous : new Array<>(pending)) {
            if (previous.response.isCancelled()) previous.detach();
        }
        if (disposed || response.isCancelled()) {
            if (!response.isCancelled()) response.complete(new Array<CodeCompletionItem>());
            return;
        }
        Pending operation = new Pending(response, new Array<>(order));
        pending.add(operation);
        try {
            for (int index = 0; index < operation.sources.size && !operation.isCancelled(); index++) {
                Registration source = operation.sources.get(index);
                final int slot = index;
                CodeCompletionResponse sink = new CodeCompletionResponse() {
                    @Override public void complete(Array<CodeCompletionItem> items) { operation.complete(slot, items); }
                    @Override public boolean isCancelled() { return operation.isCancelled() || operation.received[slot]; }
                };
                try {
                    if (source.condition == null || source.condition.isEnabled(request)) {
                        if (!sink.isCancelled()) source.provider.provide(request, sink);
                    } else {
                        sink.complete(null);
                    }
                } catch (RuntimeException exception) {
                    sink.complete(null);
                    reportFailure(source.id, exception);
                }
            }
        } finally {
            operation.dispatching = false;
            operation.finishIfReady();
        }
    }

    @Override public boolean isTriggerCharacter(char character) {
        for (Registration source : new Array<>(order)) {
            if (providers.get(source.id) != source) continue;
            try {
                if (source.provider.isTriggerCharacter(character)) return true;
            } catch (RuntimeException exception) {
                reportFailure(source.id, exception);
            }
        }
        return false;
    }

    public void clear() {
        if (providers.size == 0) return;
        for (Registration registration : order) registration.removed = true;
        providers.clear();
        order.clear();
        changed();
    }

    @Override public void dispose() {
        if (disposed) return;
        disposed = true;
        for (Registration registration : order) registration.removed = true;
        providers.clear();
        order.clear();
        try {
            changed();
        } finally {
            for (ChangeSubscription subscription : listeners) subscription.removed = true;
            listeners.clear();
            failureListener = null;
        }
    }

    private void changed() {
        revision++;
        // Detach before callbacks: callbacks may trigger requests using the new registry state.
        Array<Pending> cancelled = new Array<>(pending);
        for (Pending operation : cancelled) operation.detach();
        try {
            for (ChangeSubscription subscription : new Array<>(listeners)) {
                if (!subscription.removed) {
                    try { subscription.listener.run(); }
                    catch (RuntimeException exception) { reportFailure("changeListener", exception); }
                }
            }
        } finally {
            for (Pending operation : cancelled) {
                if (!operation.response.isCancelled()) {
                    try { operation.response.complete(new Array<CodeCompletionItem>()); }
                    catch (RuntimeException exception) { reportFailure("response", exception); }
                }
            }
        }
    }

    private void reportFailure(String id, RuntimeException exception) {
        if (failureListener != null) {
            try { failureListener.failed(id, exception); return; }
            catch (RuntimeException listenerFailure) {
                if (listenerFailure != exception) exception.addSuppressed(listenerFailure);
            }
        }
        if (Gdx.app != null) Gdx.app.error("CodeCompletionRegistry", "Completion callback failed: " + id, exception);
    }

    private final class Registration implements Disposable {
        final String id;
        final CodeCompletionProvider provider;
        final Condition condition;
        boolean removed;

        Registration(String id, CodeCompletionProvider provider, Condition condition) {
            this.id = id;
            this.provider = provider;
            this.condition = condition;
        }

        @Override public void dispose() {
            if (removed) return;
            removed = true;
            if (providers.get(id) == this) providers.remove(id);
            order.removeValue(this, true);
            changed();
        }
    }

    private final class ChangeSubscription implements Disposable {
        final Runnable listener;
        boolean removed;
        ChangeSubscription(Runnable listener) { this.listener = listener; }
        @Override public void dispose() {
            if (removed) return;
            removed = true;
            listeners.removeValue(this, true);
        }
    }

    private final class Pending {
        final CodeCompletionResponse response;
        final Array<Registration> sources;
        final Array<Array<CodeCompletionItem>> results;
        final boolean[] received;
        int remaining;
        boolean dispatching = true;
        boolean done;

        Pending(CodeCompletionResponse response, Array<Registration> sources) {
            this.response = response;
            this.sources = sources;
            remaining = sources.size;
            received = new boolean[remaining];
            results = new Array<>(remaining);
            for (int index = 0; index < remaining; index++) results.add(null);
        }

        boolean isCancelled() {
            if (!done && response.isCancelled()) detach();
            return done;
        }

        void complete(int slot, Array<CodeCompletionItem> items) {
            if (isCancelled() || received[slot]) return;
            received[slot] = true;
            Array<CodeCompletionItem> copy = new Array<>();
            if (items != null) for (CodeCompletionItem item : items) if (item != null) copy.add(item);
            results.set(slot, copy);
            remaining--;
            finishIfReady();
        }

        void finishIfReady() {
            if (isCancelled() || dispatching || remaining != 0) return;
            Array<CodeCompletionItem> merged = new Array<>();
            ObjectSet<ItemKey> seen = new ObjectSet<>();
            for (Array<CodeCompletionItem> batch : results) {
                for (CodeCompletionItem item : batch) if (seen.add(new ItemKey(item))) merged.add(item);
            }
            detach();
            response.complete(merged);
        }

        void detach() {
            done = true;
            pending.removeValue(this, true);
            results.clear();
            sources.clear();
        }

    }

    /** Equivalent visible label, match text and insertion; the first registered source wins. */
    private static final class ItemKey {
        final CodeCompletionItem item;
        ItemKey(CodeCompletionItem item) { this.item = item; }
        @Override public int hashCode() {
            int hash = item.label.hashCode();
            hash = 31 * hash + item.insertText.hashCode();
            hash = 31 * hash + item.matchText().hashCode();
            return 31 * hash + item.insertFormat.hashCode();
        }
        @Override public boolean equals(Object other) {
            if (!(other instanceof ItemKey)) return false;
            CodeCompletionItem candidate = ((ItemKey) other).item;
            return item.label.equals(candidate.label) && item.insertText.equals(candidate.insertText)
                && item.matchText().equals(candidate.matchText()) && item.insertFormat == candidate.insertFormat;
        }
    }
}
