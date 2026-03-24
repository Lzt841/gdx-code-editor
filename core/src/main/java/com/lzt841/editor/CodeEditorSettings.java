package com.lzt841.editor;

import com.badlogic.gdx.utils.Array;
import com.lzt841.editor.input.CodeEditorInteractionMode;

/** Shared editor behavior settings that can be reused across multiple editors. */
public class CodeEditorSettings {
    private final Array<Listener> listeners = new Array<>();
    private boolean wrapEnabled;
    private boolean lineNumbersFixed = true;
    private boolean magnifierEnabled = true;
    private boolean rainbowBracketsEnabled;
    private boolean rainbowGuidesEnabled;
    private CodeEditorInteractionMode interactionMode = CodeEditorInteractionMode.AUTO;

    public boolean isWrapEnabled() {
        return wrapEnabled;
    }

    public void setWrapEnabled(boolean wrapEnabled) {
        if (this.wrapEnabled == wrapEnabled) {
            return;
        }
        this.wrapEnabled = wrapEnabled;
        notifyChanged();
    }

    public boolean isLineNumbersFixed() {
        return lineNumbersFixed;
    }

    public void setLineNumbersFixed(boolean lineNumbersFixed) {
        if (this.lineNumbersFixed == lineNumbersFixed) {
            return;
        }
        this.lineNumbersFixed = lineNumbersFixed;
        notifyChanged();
    }

    public boolean isMagnifierEnabled() {
        return magnifierEnabled;
    }

    public void setMagnifierEnabled(boolean magnifierEnabled) {
        if (this.magnifierEnabled == magnifierEnabled) {
            return;
        }
        this.magnifierEnabled = magnifierEnabled;
        notifyChanged();
    }

    public boolean isRainbowBracketsEnabled() {
        return rainbowBracketsEnabled;
    }

    public void setRainbowBracketsEnabled(boolean rainbowBracketsEnabled) {
        if (this.rainbowBracketsEnabled == rainbowBracketsEnabled) {
            return;
        }
        this.rainbowBracketsEnabled = rainbowBracketsEnabled;
        notifyChanged();
    }

    public boolean isRainbowGuidesEnabled() {
        return rainbowGuidesEnabled;
    }

    public void setRainbowGuidesEnabled(boolean rainbowGuidesEnabled) {
        if (this.rainbowGuidesEnabled == rainbowGuidesEnabled) {
            return;
        }
        this.rainbowGuidesEnabled = rainbowGuidesEnabled;
        notifyChanged();
    }

    public CodeEditorInteractionMode getInteractionMode() {
        return interactionMode;
    }

    public void setInteractionMode(CodeEditorInteractionMode interactionMode) {
        CodeEditorInteractionMode normalized = interactionMode == null
            ? CodeEditorInteractionMode.AUTO
            : interactionMode;
        if (this.interactionMode == normalized) {
            return;
        }
        this.interactionMode = normalized;
        notifyChanged();
    }

    public void addListener(Listener listener) {
        if (listener != null && !listeners.contains(listener, true)) {
            listeners.add(listener);
        }
    }

    public void removeListener(Listener listener) {
        listeners.removeValue(listener, true);
    }

    private void notifyChanged() {
        for (Listener listener : listeners) {
            listener.onSettingsChanged(this);
        }
    }

    public interface Listener {
        void onSettingsChanged(CodeEditorSettings settings);
    }
}
