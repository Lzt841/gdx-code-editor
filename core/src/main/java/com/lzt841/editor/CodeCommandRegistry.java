package com.lzt841.editor;

import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Disposable;
import com.badlogic.gdx.utils.IntMap;
import com.badlogic.gdx.utils.ObjectMap;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.utils.FocusListener;

import java.util.Locale;

/**
 * Per-editor commands, metadata and exact shortcuts for menus, toolbars and command pickers.
 * Constructing a registry registers its input interceptor; dispose it when its editor is destroyed.
 * {@link CodeEditor#getCommands()} supplies the editor's lazy shared registry. All calls use the GDX thread.
 */
public final class CodeCommandRegistry implements Disposable {
    private final CodeEditor editor;
    private final ObjectMap<String, Registration> commands = new ObjectMap<>();
    private final IntMap<Registration> shortcuts = new IntMap<>();
    private final Array<Registration> order = new Array<>();
    private final CodeEditorInputInterceptor interceptor = new CodeEditorInputInterceptor() {
        @Override
        public boolean onKeyDown(CodeEditor source, int keycode) {
            suppressedKeycode = -1;
            boolean handled = handleKeyDown(keycode);
            Stage stage = source.getStage();
            // A command may have opened a dialog. Its keyUp is routed to that new focus owner.
            if (handled && !disposed && (stage == null || stage.getKeyboardFocus() == source)) suppressedKeycode = keycode;
            return handled;
        }

        @Override
        public boolean onKeyTyped(CodeEditor source, char character) {
            // libGDX emits a separate character event even when keyDown was handled. This matters
            // for printable bindings, e.g. a modal editor mapping a plain letter to a command.
            return suppressedKeycode >= 0;
        }

        @Override
        public void onKeyUp(CodeEditor source, int keycode) {
            if (keycode == suppressedKeycode) suppressedKeycode = -1;
        }
    };
    private final FocusListener focusListener = new FocusListener() {
        @Override public void keyboardFocusChanged(FocusEvent event, Actor actor, boolean focused) {
            if (actor == editor && !focused) suppressedKeycode = -1;
        }
    };
    private int suppressedKeycode = -1;
    private boolean disposed;
    private long revision;

    public CodeCommandRegistry(CodeEditor editor) {
        if (editor == null) throw new IllegalArgumentException("editor is required");
        this.editor = editor;
        editor.addInputInterceptor(interceptor);
        editor.addListener(focusListener);
    }

    /**
     * Registers a command and optional exact shortcuts atomically. Duplicate IDs/chords are rejected;
     * existing commands are never silently replaced. Disposing the returned handle removes only this
     * registration, even if its ID has since been reused.
     */
    public Disposable register(CodeEditorCommand command, CodeKeyStroke... bindings) {
        if (disposed) throw new IllegalStateException("command registry is disposed");
        if (command == null) throw new IllegalArgumentException("command is required");
        if (commands.containsKey(command.id)) throw new IllegalArgumentException("duplicate command: " + command.id);
        Array<CodeKeyStroke> keys = validateBindings(null, bindings);
        Registration registration = new Registration(command, keys);
        commands.put(command.id, registration);
        order.add(registration);
        for (CodeKeyStroke stroke : keys) shortcuts.put(stroke.hashCode(), registration);
        revision++;
        return registration;
    }

    /**
     * Replaces a command's shortcuts atomically, without changing its registration handle or order.
     * Empty/null bindings unbind every chord. Conflicts leave the old bindings intact.
     * @return false if the command does not exist
     */
    public boolean setShortcuts(String id, CodeKeyStroke... bindings) {
        if (disposed) throw new IllegalStateException("command registry is disposed");
        Registration registration = id == null ? null : commands.get(id);
        if (registration == null) return false;
        Array<CodeKeyStroke> keys = validateBindings(registration, bindings);
        if (registration.bindings.equals(keys)) return true;
        for (CodeKeyStroke stroke : registration.bindings) shortcuts.remove(stroke.hashCode());
        registration.bindings.clear();
        registration.bindings.addAll(keys);
        for (CodeKeyStroke stroke : keys) shortcuts.put(stroke.hashCode(), registration);
        revision++;
        return true;
    }

    /** Changes only when registrations or shortcuts change; dynamic conditions need reevaluation. */
    public long getRevision() {
        return revision;
    }

    public boolean isDisposed() {
        return disposed;
    }

    private Array<CodeKeyStroke> validateBindings(Registration replacing, CodeKeyStroke[] bindings) {
        Array<CodeKeyStroke> keys = new Array<>();
        if (bindings == null) return keys;
        for (CodeKeyStroke stroke : bindings) {
            if (stroke == null || stroke.keycode < 0 || stroke.keycode > CodeKeymap.KEYCODE_MASK) {
                throw new IllegalArgumentException("shortcut must have a valid key code");
            }
            Registration owner = shortcuts.get(stroke.hashCode());
            if ((owner != null && owner != replacing) || keys.contains(stroke, false)) {
                throw new IllegalArgumentException("duplicate shortcut: " + stroke);
            }
            keys.add(stroke);
        }
        return keys;
    }

    public CodeEditorCommand getCommand(String id) {
        Registration registration = id == null ? null : commands.get(id);
        return registration == null ? null : registration.command;
    }

    public int size() {
        return commands.size;
    }

    public boolean isEnabled(String id) {
        Registration registration = id == null ? null : commands.get(id);
        return isEnabled(registration);
    }

    public boolean execute(String id) {
        return execute(id == null ? null : commands.get(id));
    }

    /** Uses the editor keymap's Ctrl/Command policy, with no Shift or modifier fallbacks. */
    public boolean handleKeyDown(int keycode) {
        if (disposed || keycode < 0 || keycode > CodeKeymap.KEYCODE_MASK) return false;
        CodeKeymap map = editor.getKeymap();
        Registration registration = shortcuts.get(CodeKeymap.pack(keycode,
            map.controlPressed(), map.shiftPressed(), map.altPressed()));
        boolean handled = execute(registration);
        return handled || (registration != null && !registration.removed
            && registration.command.consumeShortcutOnNoOp);
    }

    /** Explicit chord entry point for host key routers. */
    public boolean executeShortcut(CodeKeyStroke stroke) {
        if (stroke == null || stroke.keycode < 0 || stroke.keycode > CodeKeymap.KEYCODE_MASK) return false;
        return execute(shortcuts.get(stroke.hashCode()));
    }

    /** Metadata snapshot in registration order. */
    public Array<CodeEditorCommand> getCommands() {
        return findCommands(null, false);
    }

    /** Case-insensitive ID/label/category search; every whitespace-separated term must match. */
    public Array<CodeEditorCommand> findCommands(String query, boolean enabledOnly) {
        String normalized = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        String[] terms = normalized.isEmpty() ? new String[0] : normalized.split("\\s+");
        Array<CodeEditorCommand> result = new Array<>();
        Array<Registration> snapshot = new Array<>(order);
        for (Registration registration : snapshot) {
            if (commands.get(registration.command.id) != registration
                || (enabledOnly && !isEnabled(registration))) continue;
            CodeEditorCommand command = registration.command;
            String searchable = (command.id + " " + command.label + " " + command.category).toLowerCase(Locale.ROOT);
            boolean matches = true;
            for (String term : terms) {
                if (!searchable.contains(term)) {
                    matches = false;
                    break;
                }
            }
            if (matches) result.add(command);
        }
        return result;
    }

    public Array<CodeKeyStroke> getShortcuts(String id) {
        Registration registration = id == null ? null : commands.get(id);
        return registration == null ? new Array<CodeKeyStroke>() : new Array<>(registration.bindings);
    }

    public void clear() {
        suppressedKeycode = -1;
        if (commands.size > 0) revision++;
        for (Registration registration : order) registration.removed = true;
        commands.clear();
        shortcuts.clear();
        order.clear();
    }

    @Override
    public void dispose() {
        if (disposed) return;
        disposed = true;
        editor.removeInputInterceptor(interceptor);
        editor.removeListener(focusListener);
        clear();
        revision++;
    }

    private boolean isEnabled(Registration registration) {
        return !disposed && registration != null && !registration.removed
            && registration.command.isEnabled(editor)
            && commands.get(registration.command.id) == registration;
    }

    private boolean execute(Registration registration) {
        return isEnabled(registration) && registration.command.execute(editor);
    }

    private final class Registration implements Disposable {
        final CodeEditorCommand command;
        final Array<CodeKeyStroke> bindings;
        boolean removed;

        Registration(CodeEditorCommand command, Array<CodeKeyStroke> bindings) {
            this.command = command;
            this.bindings = bindings;
        }

        @Override
        public void dispose() {
            if (removed) return;
            removed = true;
            if (commands.get(command.id) == this) commands.remove(command.id);
            for (CodeKeyStroke stroke : bindings) {
                if (shortcuts.get(stroke.hashCode()) == this) shortcuts.remove(stroke.hashCode());
            }
            order.removeValue(this, true);
            revision++;
        }
    }
}
