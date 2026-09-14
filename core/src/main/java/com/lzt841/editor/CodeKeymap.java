package com.lzt841.editor;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.utils.IntArray;
import com.badlogic.gdx.utils.IntMap;

/**
 * Maps key chords to {@link CodeEditorAction}s.
 *
 * <p>Install one on the editor with {@link CodeEditor#setKeymap(CodeKeymap)}. The built-in defaults
 * reproduce the chords the editor has always used, so installing nothing changes nothing:
 *
 * <pre>{@code
 * editor.getKeymap().unbindAction(CodeEditorAction.TOGGLE_FOLD)
 *     .bind(CodeKeyStroke.of(Input.Keys.F3), CodeEditorAction.TOGGLE_FOLD);
 * }</pre>
 *
 * <p>{@link #completionDefaults()} is the matching set for an open completion popup. Both maps are
 * independent: rebinding Up in the editor does not steal Up from the popup, and vice versa.
 *
 * <p>Lookup has two compatibility fallbacks, matching historical behaviour. Shift is a selection
 * modifier, so Shift+Left fires {@link CodeEditorAction#MOVE_LEFT} even though only Left is bound.
 * Extra Ctrl/Alt on a movement or edit key are ignored unless that exact chord is bound, so
 * Ctrl+Left still moves the caret. Chorded actions such as Copy do not get that treatment:
 * Ctrl+Alt+C does nothing unless you bind it.
 */
public class CodeKeymap {
    static final int KEYCODE_MASK = 0x1FF;
    static final int CTRL_BIT = 1 << 9;
    static final int SHIFT_BIT = 1 << 10;
    static final int ALT_BIT = 1 << 11;

    private final IntMap<CodeEditorAction> bindings = new IntMap<CodeEditorAction>();
    private boolean commandActsAsControl;

    public static CodeKeymap editorDefaults() {
        CodeKeymap map = new CodeKeymap();
        map.bind(Input.Keys.LEFT, CodeEditorAction.MOVE_LEFT);
        map.bind(Input.Keys.RIGHT, CodeEditorAction.MOVE_RIGHT);
        map.bind(Input.Keys.UP, CodeEditorAction.MOVE_UP);
        map.bind(Input.Keys.DOWN, CodeEditorAction.MOVE_DOWN);
        map.bind(Input.Keys.PAGE_UP, CodeEditorAction.MOVE_PAGE_UP);
        map.bind(Input.Keys.PAGE_DOWN, CodeEditorAction.MOVE_PAGE_DOWN);
        map.bind(Input.Keys.HOME, CodeEditorAction.MOVE_LINE_START);
        map.bind(Input.Keys.END, CodeEditorAction.MOVE_LINE_END);

        map.bind(Input.Keys.BACKSPACE, CodeEditorAction.BACKSPACE);
        map.bind(Input.Keys.FORWARD_DEL, CodeEditorAction.DELETE);
        map.bind(Input.Keys.ENTER, CodeEditorAction.NEW_LINE);
        map.bind(Input.Keys.NUMPAD_ENTER, CodeEditorAction.NEW_LINE);
        map.bind(Input.Keys.TAB, CodeEditorAction.INDENT);
        map.bind(CodeKeyStroke.shift(Input.Keys.TAB), CodeEditorAction.DEDENT);
        map.bind(Input.Keys.F2, CodeEditorAction.TOGGLE_FOLD);

        map.bind(CodeKeyStroke.ctrl(Input.Keys.Z), CodeEditorAction.UNDO);
        map.bind(CodeKeyStroke.ctrlShift(Input.Keys.Z), CodeEditorAction.REDO);
        map.bind(CodeKeyStroke.ctrl(Input.Keys.Y), CodeEditorAction.REDO);
        map.bind(CodeKeyStroke.ctrl(Input.Keys.A), CodeEditorAction.SELECT_ALL);
        map.bind(CodeKeyStroke.ctrl(Input.Keys.C), CodeEditorAction.COPY);
        map.bind(CodeKeyStroke.ctrl(Input.Keys.X), CodeEditorAction.CUT);
        map.bind(CodeKeyStroke.ctrl(Input.Keys.V), CodeEditorAction.PASTE);

        map.bind(CodeKeyStroke.ctrl(Input.Keys.SPACE), CodeEditorAction.COMPLETION_TRIGGER);
        return map;
    }

    /**
     * Same as {@link #editorDefaults()} but treats the Command/Meta key as Ctrl, so Cmd+Z undoes on
     * macOS. On Windows the same flag makes the Windows key act as Ctrl, which is usually unwanted.
     */
    public static CodeKeymap macEditorDefaults() {
        return editorDefaults().setCommandActsAsControl(true);
    }

    /** Chords used while the completion popup is open. Independent of the editor map. */
    public static CodeKeymap completionDefaults() {
        CodeKeymap map = new CodeKeymap();
        map.bind(Input.Keys.UP, CodeEditorAction.COMPLETION_PREVIOUS);
        map.bind(Input.Keys.DOWN, CodeEditorAction.COMPLETION_NEXT);
        map.bind(Input.Keys.PAGE_UP, CodeEditorAction.COMPLETION_PAGE_UP);
        map.bind(Input.Keys.PAGE_DOWN, CodeEditorAction.COMPLETION_PAGE_DOWN);
        map.bind(Input.Keys.HOME, CodeEditorAction.COMPLETION_FIRST);
        map.bind(Input.Keys.END, CodeEditorAction.COMPLETION_LAST);
        map.bind(Input.Keys.ENTER, CodeEditorAction.COMPLETION_ACCEPT);
        map.bind(Input.Keys.NUMPAD_ENTER, CodeEditorAction.COMPLETION_ACCEPT);
        map.bind(Input.Keys.TAB, CodeEditorAction.COMPLETION_ACCEPT);
        map.bind(Input.Keys.ESCAPE, CodeEditorAction.COMPLETION_DISMISS);
        return map;
    }

    public CodeKeymap bind(CodeKeyStroke stroke, CodeEditorAction action) {
        if (stroke == null || action == null) {
            throw new IllegalArgumentException("stroke and action must not be null");
        }
        // Keycodes share a packed int with the modifier bits, so one out of range would silently
        // collide with them. libGDX keycodes are well inside this, so hitting it means a bad value.
        if (stroke.keycode < 0 || stroke.keycode > KEYCODE_MASK) {
            throw new IllegalArgumentException(
                "keycode out of range: " + stroke.keycode + " (0.." + KEYCODE_MASK + ")");
        }
        bindings.put(pack(stroke.keycode, stroke.ctrl, stroke.shift, stroke.alt), action);
        return this;
    }

    public CodeKeymap bind(int keycode, CodeEditorAction action) {
        return bind(CodeKeyStroke.of(keycode), action);
    }

    public CodeKeymap unbind(CodeKeyStroke stroke) {
        if (stroke != null) {
            bindings.remove(pack(stroke.keycode, stroke.ctrl, stroke.shift, stroke.alt));
        }
        return this;
    }

    public CodeKeymap unbind(int keycode) {
        return unbind(CodeKeyStroke.of(keycode));
    }

    /** Removes every chord currently bound to {@code action}. */
    public CodeKeymap unbindAction(CodeEditorAction action) {
        if (action == null) {
            return this;
        }
        IntArray remove = new IntArray();
        IntMap.Keys keys = bindings.keys();
        while (keys.hasNext) {
            int packed = keys.next();
            if (bindings.get(packed) == action) {
                remove.add(packed);
            }
        }
        for (int i = 0; i < remove.size; i++) {
            bindings.remove(remove.get(i));
        }
        return this;
    }

    public CodeKeymap clear() {
        bindings.clear();
        return this;
    }

    public int size() {
        return bindings.size;
    }

    /**
     * When true, the Command/Meta key ({@link Input.Keys#SYM}) counts as Ctrl during lookup. Off by
     * default; {@link #macEditorDefaults()} turns it on.
     */
    public CodeKeymap setCommandActsAsControl(boolean commandActsAsControl) {
        this.commandActsAsControl = commandActsAsControl;
        return this;
    }

    public boolean isCommandActsAsControl() {
        return commandActsAsControl;
    }

    public CodeEditorAction actionFor(CodeKeyStroke stroke) {
        if (stroke == null) {
            return null;
        }
        return actionFor(stroke.keycode, stroke.ctrl, stroke.shift, stroke.alt);
    }

    public CodeEditorAction actionFor(int keycode, boolean ctrl, boolean shift, boolean alt) {
        return bindings.get(pack(keycode, ctrl, shift, alt));
    }

    /**
     * Resolves a physical key press, applying the two compatibility fallbacks described on the class.
     * This is what {@link CodeEditor} and {@link com.lzt841.editor.completion.CodeCompletionController}
     * call from {@code keyDown}.
     */
    public CodeEditorAction actionForKeyDown(int keycode, boolean ctrl, boolean shift, boolean alt) {
        CodeEditorAction action = actionFor(keycode, ctrl, shift, alt);
        if (action != null) {
            return action;
        }
        if (shift) {
            action = actionFor(keycode, ctrl, false, alt);
            if (action != null) {
                return action;
            }
        }
        if (ctrl || alt) {
            CodeEditorAction bare = actionFor(keycode, false, false, false);
            if (bare != null && bare.ignoresExtraModifiers()) {
                return bare;
            }
        }
        return null;
    }

    /** {@link #actionForKeyDown(int, boolean, boolean, boolean)} using the keys currently held. */
    public CodeEditorAction actionForCurrentKeyDown(int keycode) {
        return actionForKeyDown(keycode, controlPressed(), shiftPressed(), altPressed());
    }

    /** First chord bound to {@code action}, or null. Useful for a menu label. */
    public CodeKeyStroke firstBindingOf(CodeEditorAction action) {
        if (action == null) {
            return null;
        }
        IntMap.Keys keys = bindings.keys();
        while (keys.hasNext) {
            int packed = keys.next();
            if (bindings.get(packed) == action) {
                return unpack(packed);
            }
        }
        return null;
    }

    public boolean isBound(CodeEditorAction action) {
        return firstBindingOf(action) != null;
    }

    /** Ctrl currently held, honouring {@link #setCommandActsAsControl(boolean)}. */
    public boolean controlPressed() {
        if (Gdx.input == null) {
            return false;
        }
        if (Gdx.input.isKeyPressed(Input.Keys.CONTROL_LEFT)
            || Gdx.input.isKeyPressed(Input.Keys.CONTROL_RIGHT)) {
            return true;
        }
        return commandActsAsControl && Gdx.input.isKeyPressed(Input.Keys.SYM);
    }

    public boolean shiftPressed() {
        return Gdx.input != null
            && (Gdx.input.isKeyPressed(Input.Keys.SHIFT_LEFT)
            || Gdx.input.isKeyPressed(Input.Keys.SHIFT_RIGHT));
    }

    public boolean altPressed() {
        return Gdx.input != null
            && (Gdx.input.isKeyPressed(Input.Keys.ALT_LEFT)
            || Gdx.input.isKeyPressed(Input.Keys.ALT_RIGHT));
    }

    static int pack(int keycode, boolean ctrl, boolean shift, boolean alt) {
        int packed = keycode & KEYCODE_MASK;
        if (ctrl) {
            packed |= CTRL_BIT;
        }
        if (shift) {
            packed |= SHIFT_BIT;
        }
        if (alt) {
            packed |= ALT_BIT;
        }
        return packed;
    }

    static CodeKeyStroke unpack(int packed) {
        return new CodeKeyStroke(
            packed & KEYCODE_MASK,
            (packed & CTRL_BIT) != 0,
            (packed & SHIFT_BIT) != 0,
            (packed & ALT_BIT) != 0
        );
    }
}
