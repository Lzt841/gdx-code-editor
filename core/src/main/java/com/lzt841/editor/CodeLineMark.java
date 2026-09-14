package com.lzt841.editor;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.scenes.scene2d.utils.Drawable;

/**
 * A mark attached to a line: a breakpoint, a bookmark, a version-control change bar, a build error.
 *
 * <p>Marks are drawn in the gutter and, optionally, as a tint across the line. They are the general
 * mechanism the diagnostic gutter ticks were a special case of.
 *
 * <p>A mark carries either a {@link #icon}, drawn in the gutter's icon column, or just a
 * {@link #color}, drawn as a narrow bar at the gutter's edge — which is what a Git change bar looks
 * like. Give both and the icon wins in the gutter while the colour still tints the line if
 * {@link #highlightLine} is set.
 *
 * <p>Marks are positioned by line number. They do not move themselves when the document changes; hold
 * them somewhere that can re-place them, or re-push the set from a
 * {@link CodeEditorContentListener}.
 */
public class CodeLineMark {
    public final int line;
    /** Gutter icon, or null to draw a colour bar instead. */
    public final Drawable icon;
    /** Colour of the bar, and of the line tint when {@link #highlightLine} is set. */
    public final Color color;
    /** Whether to tint the whole line, not just the gutter. */
    public final boolean highlightLine;
    /** Higher wins when several marks land on the same line. */
    public final int priority;
    /** Text a hover provider can surface; the editor itself does not display it. */
    public final String tooltip;
    /** Free slot for the producer. */
    public final Object userData;

    public CodeLineMark(int line, Drawable icon, Color color) {
        this(line, icon, color, false, 0, null, null);
    }

    public CodeLineMark(
        int line,
        Drawable icon,
        Color color,
        boolean highlightLine,
        int priority,
        String tooltip,
        Object userData
    ) {
        this.line = line;
        this.icon = icon;
        this.color = color;
        this.highlightLine = highlightLine;
        this.priority = priority;
        this.tooltip = tooltip == null ? "" : tooltip;
        this.userData = userData;
    }

    /** A gutter icon, e.g. a breakpoint dot. */
    public static CodeLineMark icon(int line, Drawable icon) {
        return new CodeLineMark(line, icon, null, false, 0, null, null);
    }

    /** A colour bar at the gutter edge, e.g. a version-control change indicator. */
    public static CodeLineMark bar(int line, Color color) {
        return new CodeLineMark(line, null, color, false, 0, null, null);
    }

    /** A colour bar plus a tint across the line, e.g. the current execution line. */
    public static CodeLineMark highlight(int line, Color color) {
        return new CodeLineMark(line, null, color, true, 0, null, null);
    }

    /** This mark at a different line, for callers that shift marks after an edit. */
    public CodeLineMark movedTo(int newLine) {
        return new CodeLineMark(newLine, icon, color, highlightLine, priority, tooltip, userData);
    }

    @Override
    public String toString() {
        return "CodeLineMark{line=" + line + (icon != null ? " icon" : "")
            + (highlightLine ? " highlight" : "") + (tooltip.isEmpty() ? "" : " '" + tooltip + "'") + "}";
    }
}
