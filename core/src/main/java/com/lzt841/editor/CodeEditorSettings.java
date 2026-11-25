package com.lzt841.editor;

import com.badlogic.gdx.utils.Array;
import com.lzt841.editor.input.CodeEditorInteractionMode;
import com.lzt841.editor.render.CodeWhitespaceMode;
import java.util.Arrays;

/** Shared editor behavior settings that can be reused across multiple editors. */
public class CodeEditorSettings {
    private final Array<Listener> listeners = new Array<>();
    private boolean wrapEnabled;
    private boolean wrapContinuationIndentEnabled;
    private int wrapContinuationIndentColumns;
    private boolean lineNumbersVisible = true;
    private boolean lineNumbersFixed = true;
    private boolean magnifierEnabled = true;
    private boolean transientCaretHandleEnabled = true;
    private boolean passwordMode;
    private boolean rainbowBracketsEnabled;
    private boolean rainbowGuidesEnabled;
    private CodeWhitespaceMode whitespaceMode = CodeWhitespaceMode.NONE;
    private boolean lineEndingsVisible;
    private boolean trailingWhitespaceHighlighted;
    private int[] columnRulers = new int[0];
    private boolean scrollbarsVisible = true;
    private boolean overscrollEnabled = true;
    private boolean zoomEnabled = true;
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

    /** Whether wrapped rows align under the start of their line's text. Off by default. */
    public boolean isWrapContinuationIndentEnabled() {
        return wrapContinuationIndentEnabled;
    }

    /**
     * Indents wrapped continuation rows under the start of their line's own text.
     *
     * <p>Changes how many rows an indented line occupies, so it defaults to off. See
     * {@link CodeEditor#setWrapContinuationIndentEnabled(boolean)}.
     */
    public void setWrapContinuationIndentEnabled(boolean wrapContinuationIndentEnabled) {
        if (this.wrapContinuationIndentEnabled == wrapContinuationIndentEnabled) {
            return;
        }
        this.wrapContinuationIndentEnabled = wrapContinuationIndentEnabled;
        notifyChanged();
    }

    /** Extra continuation-indent columns past the line's own leading whitespace. */
    public int getWrapContinuationIndentColumns() {
        return wrapContinuationIndentColumns;
    }

    /** Negative values are treated as zero. */
    public void setWrapContinuationIndentColumns(int columns) {
        int safe = Math.max(0, columns);
        if (this.wrapContinuationIndentColumns == safe) {
            return;
        }
        this.wrapContinuationIndentColumns = safe;
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

    public boolean isLineNumbersVisible() {
        return lineNumbersVisible;
    }

    public void setLineNumbersVisible(boolean lineNumbersVisible) {
        if (this.lineNumbersVisible == lineNumbersVisible) {
            return;
        }
        this.lineNumbersVisible = lineNumbersVisible;
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

    public boolean isTransientCaretHandleEnabled() {
        return transientCaretHandleEnabled;
    }

    public void setTransientCaretHandleEnabled(boolean transientCaretHandleEnabled) {
        if (this.transientCaretHandleEnabled == transientCaretHandleEnabled) {
            return;
        }
        this.transientCaretHandleEnabled = transientCaretHandleEnabled;
        notifyChanged();
    }

    public boolean isPasswordMode() {
        return passwordMode;
    }

    public void setPasswordMode(boolean passwordMode) {
        if (this.passwordMode == passwordMode) {
            return;
        }
        this.passwordMode = passwordMode;
        notifyChanged();
    }

    public boolean isZoomEnabled() {
        return zoomEnabled;
    }

    public void setZoomEnabled(boolean zoomEnabled) {
        if (this.zoomEnabled == zoomEnabled) {
            return;
        }
        this.zoomEnabled = zoomEnabled;
        notifyChanged();
    }

    public boolean isOverscrollEnabled() {
        return overscrollEnabled;
    }

    public void setOverscrollEnabled(boolean overscrollEnabled) {
        if (this.overscrollEnabled == overscrollEnabled) {
            return;
        }
        this.overscrollEnabled = overscrollEnabled;
        notifyChanged();
    }

    public boolean isScrollbarsVisible() {
        return scrollbarsVisible;
    }

    public void setScrollbarsVisible(boolean scrollbarsVisible) {
        if (this.scrollbarsVisible == scrollbarsVisible) {
            return;
        }
        this.scrollbarsVisible = scrollbarsVisible;
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

    public CodeWhitespaceMode getWhitespaceMode() {
        return whitespaceMode;
    }

    /** Null means {@link CodeWhitespaceMode#NONE}. Markers are suppressed in password mode. */
    public void setWhitespaceMode(CodeWhitespaceMode mode) {
        CodeWhitespaceMode normalized = mode == null ? CodeWhitespaceMode.NONE : mode;
        if (whitespaceMode == normalized) return;
        whitespaceMode = normalized;
        notifyChanged();
    }

    public boolean isLineEndingsVisible() {
        return lineEndingsVisible;
    }

    /** Shows a return arrow at actual line breaks, not soft wraps or the end of the document. */
    public void setLineEndingsVisible(boolean visible) {
        if (lineEndingsVisible == visible) return;
        lineEndingsVisible = visible;
        notifyChanged();
    }

    public boolean isTrailingWhitespaceHighlighted() {
        return trailingWhitespaceHighlighted;
    }

    /** Tints trailing ASCII spaces/tabs, independently of the marker mode. */
    public void setTrailingWhitespaceHighlighted(boolean highlighted) {
        if (trailingWhitespaceHighlighted == highlighted) return;
        trailingWhitespaceHighlighted = highlighted;
        notifyChanged();
    }

    /** A defensive copy of the sorted, distinct, positive ruler columns. Empty means disabled. */
    public int[] getColumnRulers() {
        return columnRulers.clone();
    }

    /**
     * Draws rulers after the given number of space advances from the unindented text origin.
     * Typical values are 80 and 120. Nonpositive values and duplicates are ignored; null clears.
     * Continuation indentation does not shift them. This is a visual margin, not a UTF-16 column
     * in proportionally spaced text.
     */
    public void setColumnRulers(int... columns) {
        int[] normalized = columns == null ? new int[0] : columns.clone();
        Arrays.sort(normalized);
        int size = 0;
        for (int column : normalized) {
            if (column > 0 && (size == 0 || normalized[size - 1] != column)) {
                normalized[size++] = column;
            }
        }
        normalized = Arrays.copyOf(normalized, size);
        if (Arrays.equals(columnRulers, normalized)) return;
        columnRulers = normalized;
        notifyChanged();
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
