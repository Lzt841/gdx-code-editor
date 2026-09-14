package com.lzt841.editor;

import com.badlogic.gdx.Input;

/**
 * One key plus the modifiers that must be held with it. The unit of a {@link CodeKeymap} binding.
 *
 * <p>Left and right modifier keys are not distinguished: Ctrl is Ctrl. Command/Meta is folded into
 * Ctrl at lookup time when {@link CodeKeymap#setCommandActsAsControl(boolean)} is on, so a stroke
 * never stores a separate Command bit.
 */
public final class CodeKeyStroke {
    public final int keycode;
    public final boolean ctrl;
    public final boolean shift;
    public final boolean alt;

    public CodeKeyStroke(int keycode, boolean ctrl, boolean shift, boolean alt) {
        this.keycode = keycode;
        this.ctrl = ctrl;
        this.shift = shift;
        this.alt = alt;
    }

    public static CodeKeyStroke of(int keycode) {
        return new CodeKeyStroke(keycode, false, false, false);
    }

    public static CodeKeyStroke ctrl(int keycode) {
        return new CodeKeyStroke(keycode, true, false, false);
    }

    public static CodeKeyStroke shift(int keycode) {
        return new CodeKeyStroke(keycode, false, true, false);
    }

    public static CodeKeyStroke alt(int keycode) {
        return new CodeKeyStroke(keycode, false, false, true);
    }

    public static CodeKeyStroke ctrlShift(int keycode) {
        return new CodeKeyStroke(keycode, true, true, false);
    }

    public static CodeKeyStroke ctrlAlt(int keycode) {
        return new CodeKeyStroke(keycode, true, false, true);
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof CodeKeyStroke)) {
            return false;
        }
        CodeKeyStroke that = (CodeKeyStroke) other;
        return keycode == that.keycode && ctrl == that.ctrl && shift == that.shift && alt == that.alt;
    }

    @Override
    public int hashCode() {
        return CodeKeymap.pack(keycode, ctrl, shift, alt);
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        if (ctrl) {
            builder.append("Ctrl+");
        }
        if (shift) {
            builder.append("Shift+");
        }
        if (alt) {
            builder.append("Alt+");
        }
        String name = Input.Keys.toString(keycode);
        builder.append(name != null ? name : Integer.toString(keycode));
        return builder.toString();
    }
}
