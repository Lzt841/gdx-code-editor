package com.lzt841.editor.completion;

import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Disposable;
import com.lzt841.editor.CodeEditorTextRange;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

public class CodeCompletionRegistryTest {
    private CodeCompletionRegistry registry;
    private CodeCompletionRequest request;

    private static final class Result implements CodeCompletionResponse {
        int calls;
        boolean cancelled;
        Array<CodeCompletionItem> items;
        @Override public void complete(Array<CodeCompletionItem> items) { calls++; this.items = items; }
        @Override public boolean isCancelled() { return cancelled; }
    }

    @Before public void setup() {
        registry = new CodeCompletionRegistry();
        request = new CodeCompletionRequest(null, 0, 0, 0, 0, "", new CodeEditorTextRange(0, 0, 0, 0),
            CodeCompletionTrigger.MANUAL, '\0');
    }

    @Test public void emptyRegistryCompletesOnceAndMetadataIsASnapshot() {
        Result result = new Result();
        registry.provide(request, result);
        assertEquals(1, result.calls);
        assertEquals(0, result.items.size);
        registry.register("first", new KeywordCompletionProvider("a"));
        Array<String> ids = registry.getProviderIds();
        ids.clear();
        assertEquals(1, registry.size());
        assertEquals("first", registry.getProviderIds().first());
        assertNotNull(registry.getProvider("first"));
        assertNull(registry.getProvider(null));
    }

    @Test public void invalidRegistrationsPreserveExistingSourcesAndRevision() {
        CodeCompletionProvider source = new KeywordCompletionProvider("a");
        registry.register("first", source);
        long revision = registry.getRevision();
        assertThrows(IllegalArgumentException.class, () -> registry.register("first", source));
        assertThrows(IllegalArgumentException.class, () -> registry.register(" bad ", source));
        assertThrows(IllegalArgumentException.class, () -> registry.register("", source));
        assertThrows(IllegalArgumentException.class, () -> registry.register(null, source));
        assertThrows(IllegalArgumentException.class, () -> registry.register("null", null));
        assertThrows(IllegalArgumentException.class, () -> registry.register("self", registry));
        assertThrows(IllegalArgumentException.class, () -> registry.register("nested", new CodeCompletionRegistry()));
        assertEquals(revision, registry.getRevision());
        assertSame(source, registry.getProvider("first"));
    }

    @Test public void asynchronousResponsesMergeInRegistrationOrderAndCopyTheirArrays() {
        Array<CodeCompletionResponse> sinks = new Array<>();
        registry.register("first", (request, response) -> sinks.add(response));
        registry.register("second", (request, response) -> sinks.add(response));
        Result result = new Result();
        registry.provide(request, result);
        assertEquals(0, result.calls);
        Array<CodeCompletionItem> second = Array.with(CodeCompletionItem.of("b"), null);
        sinks.get(1).complete(second);
        second.clear();
        assertEquals(0, result.calls);
        sinks.get(0).complete(Array.with(CodeCompletionItem.of("a")));
        assertEquals(1, result.calls);
        assertEquals("a", result.items.get(0).label);
        assertEquals("b", result.items.get(1).label);
        sinks.get(1).complete(Array.with(CodeCompletionItem.of("late")));
        assertEquals(1, result.calls);
        assertTrue(sinks.first().isCancelled());
    }

    @Test public void equivalentInsertionsDeduplicateWithoutMergingSnippetsOrDistinctFilters() {
        CodeCompletionItem first = CodeCompletionItem.of("for");
        CodeCompletionItem snippet = CodeCompletionItem.snippet("for", "for");
        CodeCompletionItem otherFilter = new CodeCompletionItem("for", "for", "", "", null, 0, "loop", null);
        registry.register("first", (request, response) -> response.complete(Array.with(first)));
        registry.register("second", (request, response) -> response.complete(Array.with(
            CodeCompletionItem.of("for"), snippet, otherFilter)));
        Result result = new Result();
        registry.provide(request, result);
        assertEquals(3, result.items.size);
        assertSame(first, result.items.get(0));
        assertSame(snippet, result.items.get(1));
        assertSame(otherFilter, result.items.get(2));
    }

    @Test public void eachSourceCanRespondOnlyOnceIncludingAnEmptyResponse() {
        registry.register("oneShot", (request, response) -> {
            response.complete(null);
            response.complete(Array.with(CodeCompletionItem.of("ignored")));
        });
        Result result = new Result();
        registry.provide(request, result);
        assertEquals(1, result.calls);
        assertEquals(0, result.items.size);
    }

    @Test public void conditionsAreEvaluatedPerRequestAndFailuresDoNotBlockHealthySources() {
        boolean[] enabled = {false};
        int[] calls = {0};
        Array<String> failures = new Array<>();
        registry.setFailureListener((id, exception) -> failures.add(id));
        registry.register("conditional", (request, response) -> { calls[0]++; response.complete(null); }, request -> enabled[0]);
        registry.register("brokenCondition", (request, response) -> fail(), request -> { throw new IllegalStateException(); });
        registry.register("brokenProvider", (request, response) -> { throw new IllegalArgumentException(); });
        registry.register("healthy", new KeywordCompletionProvider("ok"));
        Result result = new Result();
        registry.provide(request, result);
        assertEquals(0, calls[0]);
        assertEquals("ok", result.items.first().label);
        assertEquals(Array.with("brokenCondition", "brokenProvider"), failures);
        enabled[0] = true;
        registry.provide(request, new Result());
        assertEquals(1, calls[0]);
    }

    @Test public void triggerCharactersAreCombinedAndFailuresAreReported() {
        Array<String> failures = new Array<>();
        registry.setFailureListener((id, exception) -> failures.add(id));
        registry.register("broken", new CodeCompletionProvider() {
            @Override public void provide(CodeCompletionRequest request, CodeCompletionResponse response) { response.complete(null); }
            @Override public boolean isTriggerCharacter(char character) { throw new IllegalStateException(); }
        });
        registry.register("colon", new CodeCompletionProvider() {
            @Override public void provide(CodeCompletionRequest request, CodeCompletionResponse response) { response.complete(null); }
            @Override public boolean isTriggerCharacter(char character) { return character == ':'; }
        });
        registry.register("dot", new KeywordCompletionProvider("x"));
        assertTrue(registry.isTriggerCharacter(':'));
        assertTrue(registry.isTriggerCharacter('.'));
        assertFalse(registry.isTriggerCharacter(';'));
        assertEquals(3, failures.size);
    }

    @Test public void registrationChangesCancelPendingSnapshotsAndIgnoreLateResults() {
        Array<CodeCompletionResponse> sinks = new Array<>();
        Disposable handle = registry.register("slow", (request, response) -> sinks.add(response));
        Result first = new Result();
        registry.provide(request, first);
        handle.dispose();
        assertEquals(1, first.calls);
        assertEquals(0, first.items.size);
        assertTrue(sinks.first().isCancelled());
        sinks.first().complete(Array.with(CodeCompletionItem.of("late")));
        assertEquals(1, first.calls);
        registry.register("slow", new KeywordCompletionProvider("new"));
        handle.dispose();
        assertEquals(1, registry.size());
        Result next = new Result();
        registry.provide(request, next);
        assertEquals("new", next.items.first().label);
    }

    @Test public void addingASourceAlsoInvalidatesPendingResultsWithoutInvokingItRetroactively() {
        Array<CodeCompletionResponse> sinks = new Array<>();
        registry.register("slow", (request, response) -> sinks.add(response));
        Result result = new Result();
        registry.provide(request, result);
        int[] calls = {0};
        registry.register("new", (request, response) -> { calls[0]++; response.complete(null); });
        assertEquals(0, calls[0]);
        assertEquals(1, result.calls);
        sinks.first().complete(Array.with(CodeCompletionItem.of("late")));
        assertEquals(0, result.items.size);
    }

    @Test public void providerCanUnregisterItselfWithoutDispatchingTheRemainingSnapshot() {
        Disposable[] self = new Disposable[1];
        self[0] = registry.register("self", (request, response) -> {
            self[0].dispose();
            assertTrue(response.isCancelled());
            response.complete(Array.with(CodeCompletionItem.of("stale")));
        });
        registry.register("next", (request, response) -> fail("snapshot was invalidated"));
        Result result = new Result();
        registry.provide(request, result);
        assertEquals(1, result.calls);
        assertEquals(0, result.items.size);
    }

    @Test public void callerCancellationIsVisibleToProvidersAndPreventsDelivery() {
        Array<CodeCompletionResponse> sinks = new Array<>();
        registry.register("slow", (request, response) -> sinks.add(response));
        Result result = new Result();
        registry.provide(request, result);
        result.cancelled = true;
        assertTrue(sinks.first().isCancelled());
        sinks.first().complete(Array.with(CodeCompletionItem.of("late")));
        assertEquals(0, result.calls);
        registry.provide(request, result);
        assertEquals(1, sinks.size);
    }

    @Test public void changeListenersCanDetachOthersAndFailWithoutBreakingRegistration() {
        Array<String> failures = new Array<>();
        registry.setFailureListener((id, exception) -> failures.add(id));
        Disposable[] second = new Disposable[1];
        registry.addChangeListener(() -> second[0].dispose());
        second[0] = registry.addChangeListener(() -> fail("detached listener must be skipped"));
        registry.addChangeListener(() -> { throw new IllegalStateException(); });
        int[] calls = {0};
        registry.addChangeListener(() -> calls[0]++);
        Disposable registration = registry.register("ok", new KeywordCompletionProvider("ok"));
        assertEquals(1, calls[0]);
        assertEquals(Array.with("changeListener"), failures);
        registration.dispose();
        assertEquals(2, calls[0]);
    }

    @Test public void clearIsReusableAndDisposalInvalidatesPendingWorkAndIsTerminal() {
        Array<CodeCompletionResponse> sinks = new Array<>();
        registry.register("slow", (request, response) -> sinks.add(response));
        Result first = new Result();
        registry.provide(request, first);
        registry.clear();
        assertEquals(1, first.calls);
        long revision = registry.getRevision();
        registry.clear();
        assertEquals(revision, registry.getRevision());
        registry.register("again", (request, response) -> sinks.add(response));
        Result second = new Result();
        registry.provide(request, second);
        registry.dispose();
        registry.dispose();
        assertEquals(1, second.calls);
        assertTrue(sinks.peek().isCancelled());
        assertTrue(registry.isDisposed());
        assertEquals(0, registry.size());
        assertFalse(registry.isTriggerCharacter('.'));
        assertThrows(IllegalStateException.class, () -> registry.register("late", new KeywordCompletionProvider()));
        assertThrows(IllegalStateException.class, () -> registry.addChangeListener(() -> { }));
        assertThrows(IllegalStateException.class, registry::invalidate);
        Result disposed = new Result();
        registry.provide(request, disposed);
        assertEquals(1, disposed.calls);
        assertEquals(0, disposed.items.size);
    }

    @Test public void synchronousConsumerFailureIsNotMisreportedAsAProviderFailure() {
        registry.register("sync", new KeywordCompletionProvider("ok"));
        registry.setFailureListener((id, exception) -> fail("this was a consumer failure"));
        RuntimeException failure = new IllegalStateException("consumer");
        assertSame(failure, assertThrows(RuntimeException.class,
            () -> registry.provide(request, items -> { throw failure; })));
        Result next = new Result();
        registry.provide(request, next);
        assertEquals(1, next.calls);
    }

    @Test public void completedConsumerCanMutateRegistryWithoutReceivingAnExtraResponse() {
        registry.register("sync", new KeywordCompletionProvider("ok"));
        int[] calls = {0};
        registry.provide(request, items -> {
            calls[0]++;
            registry.clear();
        });
        assertEquals(1, calls[0]);
        assertEquals(0, registry.size());
    }
}
