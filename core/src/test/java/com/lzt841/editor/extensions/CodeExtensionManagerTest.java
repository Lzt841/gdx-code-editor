package com.lzt841.editor.extensions;

import com.badlogic.gdx.Input;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Disposable;
import com.lzt841.editor.CodeCommandRegistry;
import com.lzt841.editor.CodeEditor;
import com.lzt841.editor.CodeEditorCommand;
import com.lzt841.editor.CodeEditorContentListener;
import com.lzt841.editor.CodeEditorInputInterceptor;
import com.lzt841.editor.CodeKeyStroke;
import org.junit.Before;
import org.junit.Test;

import java.util.function.Consumer;

import static com.lzt841.editor.EditorTestSupport.*;
import static org.junit.Assert.*;

public class CodeExtensionManagerTest {
    private CodeEditor editor;
    private CodeExtensionManager manager;

    @Before public void setup() {
        editor = createEditor("abc");
        manager = editor.getExtensions();
    }

    private static CodeEditorExtension extension(String id, Consumer<CodeExtensionContext> install) {
        return new CodeEditorExtension() {
            @Override public String getId() { return id; }
            @Override public void install(CodeExtensionContext context) { install.accept(context); }
        };
    }

    private static CodeEditorCommand command(String id) {
        return new CodeEditorCommand(id, id, source -> true);
    }

    @Test
    public void uninstallReleasesCommandsListenersAndInputCallbacks() {
        int[] calls = new int[3];
        CodeExtensionContext scope = manager.install(extension("sample", context -> {
            assertSame(editor, context.getEditor());
            context.registerCommand(command("sample.run"), CodeKeyStroke.of(Input.Keys.F6));
            context.addContentListener((source, event) -> calls[0]++);
            context.addCaretListener((source, position, selection, edited) -> calls[1]++);
            context.addInputInterceptor(new CodeEditorInputInterceptor() {
                @Override public boolean onKeyDown(CodeEditor source, int key) { calls[2]++; return true; }
            });
        }));
        assertTrue(manager.isInstalled("sample"));
        assertTrue(editor.insertTextAtCursor("x"));
        editor.act(0f);
        assertTrue(keyDown(editor, Input.Keys.F7));
        assertEquals(1, calls[0]);
        assertTrue(calls[1] > 0);
        assertEquals(1, calls[2]);
        int caretCalls = calls[1];

        assertTrue(manager.uninstall("sample"));
        assertTrue(scope.isDisposed());
        assertFalse(manager.uninstall("sample"));
        assertFalse(editor.getCommands().execute("sample.run"));
        assertFalse(keyDown(editor, Input.Keys.F6));
        assertTrue(editor.insertTextAtCursor("y"));
        editor.act(0f);
        assertArrayEquals(new int[] {1, caretCalls, 1}, calls);
        assertThrows(IllegalStateException.class, () -> scope.registerCommand(command("late")));
        assertThrows(IllegalStateException.class, () -> scope.onDispose(() -> { }));
    }

    @Test
    public void failedInstallRollsBackAndKeepsOriginalException() {
        RuntimeException installFailure = new IllegalStateException("install failed");
        RuntimeException cleanupFailure = new IllegalStateException("cleanup failed");
        int[] callbacks = {0};
        CodeExtensionContext[] scope = new CodeExtensionContext[1];
        RuntimeException thrown = assertThrows(RuntimeException.class, () -> manager.install(new CodeEditorExtension() {
            @Override public String getId() { return "failed"; }
            @Override public void install(CodeExtensionContext context) {
                scope[0] = context;
                context.registerCommand(command("failed.run"), CodeKeyStroke.of(Input.Keys.F6));
                context.addContentListener((source, event) -> callbacks[0]++);
                context.onDispose(() -> { throw cleanupFailure; });
                throw installFailure;
            }
            @Override public void uninstall() { fail("failed installations must not run the uninstall hook"); }
        }));
        assertSame(installFailure, thrown);
        assertArrayEquals(new Throwable[] {cleanupFailure}, thrown.getSuppressed());
        assertTrue(scope[0].isDisposed());
        assertFalse(manager.isInstalled("failed"));
        assertEquals(0, editor.getCommands().size());
        assertFalse(keyDown(editor, Input.Keys.F6));
        editor.insertTextAtCursor("x");
        assertEquals(0, callbacks[0]);
        manager.install(extension("failed", context -> context.registerCommand(command("failed.run"))));
        assertTrue(editor.getCommands().execute("failed.run"));
    }

    @Test
    public void cleanupIsReverseOrderedDeduplicatedAndContinuesAfterFailure() {
        StringBuilder order = new StringBuilder();
        RuntimeException lastFailure = new IllegalStateException("last");
        RuntimeException firstFailure = new IllegalStateException("first");
        RuntimeException hookFailure = new IllegalStateException("hook");
        Disposable once = () -> { order.append('A'); throw firstFailure; };
        manager.install(new CodeEditorExtension() {
            @Override public String getId() { return "cleanup"; }
            @Override public void install(CodeExtensionContext context) {
                context.own(once);
                context.own(once);
                context.registerCommand(command("cleanup.run"));
                context.onDispose(() -> { order.append('B'); throw lastFailure; });
            }
            @Override public void uninstall() {
                assertFalse(manager.isInstalled("cleanup"));
                assertNull(editor.getCommands().getCommand("cleanup.run"));
                order.append('C');
                throw hookFailure;
            }
        });
        RuntimeException thrown = assertThrows(RuntimeException.class, () -> manager.uninstall("cleanup"));
        assertSame(lastFailure, thrown);
        assertArrayEquals(new Throwable[] {firstFailure, hookFailure}, thrown.getSuppressed());
        assertEquals("BAC", order.toString());
        assertFalse(manager.isInstalled("cleanup"));
        assertEquals(0, manager.getInstalledIds().size);
    }

    @Test
    public void clearUnloadsInReverseOrderAndRetainsHostCommands() {
        StringBuilder order = new StringBuilder();
        CodeCommandRegistry commands = editor.getCommands();
        commands.register(command("host.run"));
        manager.install(extension("first", context -> {
            context.registerCommand(command("first.run"));
            context.onDispose(() -> order.append('1'));
        }));
        manager.install(extension("second", context -> {
            context.registerCommand(command("second.run"));
            context.onDispose(() -> { order.append('2'); throw new IllegalStateException("cleanup"); });
        }));
        Array<String> snapshot = manager.getInstalledIds();
        assertEquals("first", snapshot.first());
        assertEquals("second", snapshot.peek());
        assertThrows(IllegalStateException.class, manager::clear);
        assertEquals("21", order.toString());
        assertEquals(2, snapshot.size);
        assertEquals(0, manager.getInstalledIds().size);
        assertEquals(1, commands.size());
        assertTrue(commands.execute("host.run"));
        manager.install(extension("first", context -> context.registerCommand(command("first.run"))));
        manager.clear();
        assertEquals(1, commands.size());
    }

    @Test
    public void lifecycleCallbacksCannotReenterLifecycleChanges() {
        manager.install(new CodeEditorExtension() {
            @Override public String getId() { return "guarded"; }
            @Override public void install(CodeExtensionContext context) {
                assertThrows(IllegalStateException.class, () -> manager.install(extension("nested", ignored -> { })));
                assertThrows(IllegalStateException.class, manager::clear);
                assertThrows(IllegalStateException.class, manager::dispose);
                context.registerCommand(command("guarded.run"));
                context.onDispose(() -> assertThrows(IllegalStateException.class, manager::clear));
            }
            @Override public void uninstall() {
                assertThrows(IllegalStateException.class, () -> manager.uninstall("guarded"));
            }
        });
        assertTrue(manager.uninstall("guarded"));
        assertEquals(0, manager.getInstalledIds().size);
    }

    @Test
    public void duplicateAndInvalidIdsLeaveInstalledExtensionIntact() {
        manager.install(extension("one", context -> context.registerCommand(command("one.run"))));
        assertThrows(IllegalArgumentException.class, () -> manager.install(extension("one", ignored -> fail())));
        assertThrows(IllegalArgumentException.class, () -> manager.install(extension(" bad", ignored -> fail())));
        assertThrows(IllegalArgumentException.class, () -> manager.install(extension(null, ignored -> fail())));
        assertEquals(1, manager.getInstalledIds().size);
        assertTrue(editor.getCommands().execute("one.run"));
    }

    @Test
    public void sharedListenersRemainRegisteredByOtherScopesAndHost() {
        int[] calls = {0};
        CodeEditorContentListener listener = (source, event) -> calls[0]++;
        editor.addContentListener(listener);
        manager.install(extension("one", context -> context.addContentListener(listener)));
        manager.install(extension("two", context -> context.addContentListener(listener)));
        editor.insertTextAtCursor("x");
        assertEquals(3, calls[0]);
        manager.uninstall("one");
        editor.insertTextAtCursor("y");
        assertEquals(5, calls[0]);
        manager.clear();
        editor.insertTextAtCursor("z");
        assertEquals(6, calls[0]);
    }

    @Test
    public void selfUninstallSuppressesRemainingScopedCallbacksWithoutSkippingHost() {
        int[] calls = {0};
        manager.install(extension("one", context -> {
            context.addInputInterceptor(new CodeEditorInputInterceptor() {
                @Override public boolean onKeyDown(CodeEditor source, int key) {
                    manager.uninstall("one");
                    return false;
                }
            });
            context.addInputInterceptor(new CodeEditorInputInterceptor() {
                @Override public boolean onKeyDown(CodeEditor source, int key) {
                    fail("an unloaded scope must not receive the rest of the dispatch");
                    return false;
                }
            });
        }));
        editor.addInputInterceptor(new CodeEditorInputInterceptor() {
            @Override public boolean onKeyDown(CodeEditor source, int key) { calls[0]++; return true; }
        });
        assertTrue(keyDown(editor, Input.Keys.F6));
        assertEquals(1, calls[0]);
        assertFalse(manager.isInstalled("one"));
    }

    @Test
    public void disposalClearsHostCommandsEvenWhenExtensionCleanupFails() {
        CodeCommandRegistry commands = manager.getCommands();
        commands.register(command("host.run"), CodeKeyStroke.of(Input.Keys.F6));
        manager.install(extension("one", context -> context.onDispose(() -> {
            throw new IllegalStateException("cleanup failed");
        })));
        assertThrows(IllegalStateException.class, manager::dispose);
        assertTrue(manager.isDisposed());
        manager.dispose();
        assertEquals(0, commands.size());
        assertFalse(keyDown(editor, Input.Keys.F6));
        assertThrows(IllegalStateException.class, () -> manager.install(extension("late", ignored -> { })));
        assertThrows(IllegalStateException.class, manager::getCommands);
        assertThrows(IllegalStateException.class, editor::getCommands);
    }

    @Test
    public void scopesAreIsolatedAcrossEditors() {
        CodeEditor other = createEditor("abc");
        manager.install(extension("same", context -> context.registerCommand(command("same.run"))));
        other.getExtensions().install(extension("same", context -> context.registerCommand(command("same.run"))));
        manager.dispose();
        assertTrue(other.getExtensions().isInstalled("same"));
        assertTrue(other.getCommands().execute("same.run"));
    }
}
