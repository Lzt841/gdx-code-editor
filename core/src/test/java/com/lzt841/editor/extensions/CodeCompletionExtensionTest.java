package com.lzt841.editor.extensions;

import com.badlogic.gdx.Input;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Disposable;
import com.lzt841.editor.CodeEditor;
import com.lzt841.editor.completion.*;
import org.junit.Before;
import org.junit.Test;

import static com.lzt841.editor.EditorTestSupport.*;
import static org.junit.Assert.*;

public class CodeCompletionExtensionTest {
    private CodeEditor editor;

    @Before public void setup() { editor = createEditor("ab"); editor.setCursorPosition(0, 2); }

    private CodeEditorExtension contribution(String id, CodeCompletionProvider provider) {
        return new CodeEditorExtension() {
            @Override public String getId() { return id; }
            @Override public void install(CodeExtensionContext context) { context.registerCompletionProvider(id, provider); }
        };
    }

    @Test public void sharedControllerCombinesHostAndScopedProvidersAndRejectsUnloadedItems() {
        editor.getCompletionProviders().register("host", new KeywordCompletionProvider("absolute"));
        editor.getExtensions().install(contribution("extra", new KeywordCompletionProvider("abstract")));
        CodeCompletionController controller = new CodeCompletionController(editor);
        controller.install();
        controller.triggerManually();
        assertEquals(2, controller.getPopup().getItems().size);
        CodeCompletionItem stale = controller.getPopup().getItems().first();
        editor.getExtensions().uninstall("extra");
        assertFalse(controller.isOpen());
        assertFalse(controller.accept(stale));
        assertEquals("ab", editor.getText());
        controller.triggerManually();
        assertEquals(1, controller.getPopup().getItems().size);
        assertTrue(controller.acceptSelected());
        assertEquals("absolute", editor.getText());
        controller.uninstall();
    }

    @Test public void unloadingAPendingContributionCancelsItsResponseAndDoesNotReopenThePopup() {
        Array<CodeCompletionResponse> sinks = new Array<>();
        editor.getExtensions().install(contribution("slow", (request, response) -> sinks.add(response)));
        CodeCompletionController controller = new CodeCompletionController(editor);
        controller.install();
        controller.triggerManually();
        editor.getExtensions().uninstall("slow");
        assertTrue(sinks.first().isCancelled());
        sinks.first().complete(Array.with(CodeCompletionItem.of("abandoned")));
        assertFalse(controller.isOpen());
        assertEquals(0, controller.getPopup().getItems().size);
        controller.uninstall();
    }

    @Test public void failedInstallationRollsBackItsProvidersAndDisposedScopeRejectsNewOnes() {
        editor.getCompletionProviders().register("host", new KeywordCompletionProvider("absolute"));
        CodeExtensionContext[] scope = new CodeExtensionContext[1];
        assertThrows(IllegalStateException.class, () -> editor.getExtensions().install(new CodeEditorExtension() {
            @Override public String getId() { return "broken"; }
            @Override public void install(CodeExtensionContext context) {
                scope[0] = context;
                context.registerCompletionProvider("temp", new KeywordCompletionProvider("abstract"));
                throw new IllegalStateException("install");
            }
        }));
        assertEquals(Array.with("host"), editor.getCompletionProviders().getProviderIds());
        assertThrows(IllegalStateException.class,
            () -> scope[0].registerCompletionProvider("late", new KeywordCompletionProvider()));
    }

    @Test public void managerClearRetainsHostSourcesAndDisposeClosesControllersAndTheRegistry() {
        CodeCompletionRegistry registry = editor.getCompletionProviders();
        registry.register("host", new KeywordCompletionProvider("absolute"));
        editor.getExtensions().install(contribution("extra", new KeywordCompletionProvider("abstract")));
        CodeCompletionController controller = new CodeCompletionController(editor);
        controller.install();
        editor.getExtensions().clear();
        assertEquals(Array.with("host"), registry.getProviderIds());
        controller.triggerManually();
        assertTrue(controller.isOpen());
        editor.getExtensions().dispose();
        assertTrue(registry.isDisposed());
        assertFalse(controller.isOpen());
        controller.triggerManually();
        assertFalse(controller.isOpen());
        assertThrows(IllegalStateException.class, editor::getCompletionProviders);
        controller.uninstall();
    }

    @Test public void snippetPackAcceptsDecoratedPrefixesAndStartsAnUndoableMirroredSession() {
        editor.setText("fo");
        editor.setCursorPosition(0, 2);
        CodeCompletionItem snippet = new CodeCompletionItem("For Loop", "${1:i} = $1; $0", "loop", "docs",
            CodeCompletionItemKind.SNIPPET, 2, "fori", null, CodeCompletionInsertFormat.SNIPPET);
        editor.getExtensions().install(new CodeSnippetExtension("snippets", snippet));
        CodeCompletionController controller = new CodeCompletionController(editor);
        controller.install();
        controller.triggerManually();
        assertSame(snippet, controller.getPopup().getSelectedItem());
        assertTrue(controller.acceptSelected());
        assertEquals("i = i; ", editor.getText());
        assertEquals("i", editor.getSelectionText());
        assertNotNull(controller.getSnippetSession());
        assertEquals(1, controller.getSnippetSession().getCurrentTabStop());
        assertTrue(editor.insertTextAtCursor("index"));
        assertEquals("index = index; ", editor.getText());
        controller.cancelSnippet();
        for (int remaining = editor.getUndoEntryCount(); remaining > 0; remaining--) assertTrue(editor.undo());
        assertEquals("fo", editor.getText());
        controller.uninstall();
    }

    @Test public void snippetProviderCopiesThePackAndReturnsIndependentSnapshots() {
        CodeCompletionItem snippet = CodeCompletionItem.snippet("ab", "${1:value}$0");
        CodeCompletionItem[] source = {snippet};
        CodeSnippetProvider provider = new CodeSnippetProvider(source);
        source[0] = CodeCompletionItem.snippet("other", "$0");
        provider.getSnippets().clear();
        assertSame(snippet, provider.getSnippets().first());
        assertFalse(provider.isTriggerCharacter('.'));
        assertThrows(IllegalArgumentException.class, () -> new CodeSnippetProvider(CodeCompletionItem.of("plain")));
        assertThrows(IllegalArgumentException.class, () -> new CodeSnippetProvider((CodeCompletionItem[]) null));
        assertThrows(IllegalArgumentException.class, () -> new CodeSnippetProvider((CodeCompletionItem) null));
    }

    @Test public void snippetPackConditionsAndConflictsRespectExtensionBoundaries() {
        boolean[] enabled = {false};
        CodeSnippetExtension pack = new CodeSnippetExtension("pack", request -> enabled[0],
            CodeCompletionItem.snippet("abstract", "${1:value}$0"));
        editor.getExtensions().install(pack);
        CodeCompletionController controller = new CodeCompletionController(editor);
        controller.triggerManually();
        assertFalse(controller.isOpen());
        enabled[0] = true;
        controller.triggerManually();
        assertTrue(controller.isOpen());
        editor.getExtensions().uninstall("pack");
        assertFalse(controller.acceptSelected()); // Also safe without install()/a change subscription.
        editor.getCompletionProviders().register("pack", new KeywordCompletionProvider("absolute"));
        assertThrows(IllegalArgumentException.class, () -> editor.getExtensions().install(pack));
        assertFalse(editor.getExtensions().isInstalled("pack"));
        assertEquals(1, editor.getCompletionProviders().size());
        controller.uninstall();
    }

    @Test public void completionExtensionOwnsTheControllerAndCanBeReinstalled() {
        editor.getCompletionProviders().register("host", new KeywordCompletionProvider("absolute"));
        CodeCompletionExtension extension = new CodeCompletionExtension();
        editor.getExtensions().install(extension);
        CodeCompletionController first = extension.getController();
        first.triggerManually();
        assertTrue(first.isOpen());
        assertNotNull(editor.getCommands().getCommand(CodeCompletionExtension.TRIGGER));
        assertFalse(editor.getCommands().isEnabled(CodeCompletionExtension.TRIGGER)); // No Stage.
        editor.getExtensions().uninstall(CodeCompletionExtension.ID);
        assertFalse(first.isOpen());
        assertNull(editor.getCommands().getCommand(CodeCompletionExtension.TRIGGER));
        assertFalse(keyDown(editor, Input.Keys.SPACE));
        editor.getExtensions().install(extension);
        assertNotSame(first, extension.getController());
        extension.getController().triggerManually();
        assertTrue(extension.getController().isOpen());
        editor.getExtensions().dispose();
    }

    @Test public void completionExtensionRollsBackControllerIfItsCommandConflicts() {
        editor.getCommands().register(new com.lzt841.editor.CodeEditorCommand(
            CodeCompletionExtension.TRIGGER, "host", source -> true));
        CodeCompletionExtension extension = new CodeCompletionExtension();
        assertThrows(IllegalArgumentException.class, () -> editor.getExtensions().install(extension));
        assertFalse(editor.getExtensions().isInstalled(CodeCompletionExtension.ID));
        assertFalse(extension.getController().isOpen());
        assertEquals(1, editor.getCommands().size());
        assertFalse(keyDown(editor, Input.Keys.SPACE));
    }

    @Test public void explicitProviderConstructorStillIgnoresTheSharedRegistry() {
        editor.getCompletionProviders().register("host", new KeywordCompletionProvider("absolute"));
        CodeCompletionController controller = new CodeCompletionController(editor, new KeywordCompletionProvider("abstract"));
        controller.install();
        controller.triggerManually();
        editor.getCompletionProviders().clear();
        assertTrue(controller.isOpen());
        assertEquals("abstract", controller.getPopup().getSelectedItem().label);
        controller.uninstall();
    }

    @Test public void registryDisposalDoesNotDisposeBorrowedProviderResources() {
        boolean[] disposed = {false};
        class BorrowedProvider implements CodeCompletionProvider, Disposable {
            @Override public void provide(CodeCompletionRequest request, CodeCompletionResponse response) { response.complete(null); }
            @Override public void dispose() { disposed[0] = true; }
        }
        editor.getExtensions().install(contribution("borrowed", new BorrowedProvider()));
        editor.getExtensions().dispose();
        assertFalse(disposed[0]);
    }

    @Test public void externalStateInvalidationDismissesOldConditionsAndRequestsFreshCandidates() {
        boolean[] java = {true};
        editor.getExtensions().install(new CodeSnippetExtension("java", request -> java[0],
            CodeCompletionItem.snippet("abstract", "${1:value}$0")));
        CodeCompletionController controller = new CodeCompletionController(editor);
        controller.install();
        controller.triggerManually();
        CodeCompletionItem stale = controller.getPopup().getSelectedItem();
        assertTrue(controller.isOpen());
        java[0] = false;
        editor.getCompletionProviders().invalidate();
        assertFalse(controller.isOpen());
        assertFalse(controller.accept(stale));
        controller.triggerManually();
        assertFalse(controller.isOpen());
        assertEquals("ab", editor.getText());
        controller.uninstall();
    }

    @Test public void cleanupFailureStillDisposesTheSharedRegistryAndCommandRegistry() {
        CodeCompletionRegistry registry = editor.getCompletionProviders();
        com.lzt841.editor.CodeCommandRegistry commands = editor.getCommands();
        RuntimeException failure = new IllegalStateException("cleanup");
        editor.getExtensions().install(new CodeEditorExtension() {
            @Override public String getId() { return "cleanup"; }
            @Override public void install(CodeExtensionContext context) {
                context.registerCompletionProvider("owned", new KeywordCompletionProvider("abstract"));
                context.onDispose(() -> { throw failure; });
            }
        });
        assertSame(failure, assertThrows(RuntimeException.class, () -> editor.getExtensions().dispose()));
        assertTrue(registry.isDisposed());
        assertTrue(commands.isDisposed());
        assertEquals(0, registry.size());
    }
}
