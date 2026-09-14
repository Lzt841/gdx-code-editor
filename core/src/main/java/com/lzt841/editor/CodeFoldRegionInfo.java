package com.lzt841.editor;

/**
 * A foldable region as reported to callers: where it starts and ends, how deeply nested it is, and
 * whether it is currently collapsed.
 *
 * <p>Immutable snapshot. Fold regions are recomputed when the document changes, so hold a line number
 * rather than this object if you need to refer to a region across edits.
 */
public final class CodeFoldRegionInfo {
    public final int startLine;
    public final int endLine;
    public final int depth;
    public final boolean collapsed;

    public CodeFoldRegionInfo(int startLine, int endLine, int depth, boolean collapsed) {
        this.startLine = startLine;
        this.endLine = endLine;
        this.depth = depth;
        this.collapsed = collapsed;
    }

    /** Lines hidden when this region is collapsed. */
    public int hiddenLineCount() {
        return Math.max(0, endLine - startLine);
    }

    public boolean containsLine(int line) {
        return line >= startLine && line <= endLine;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof CodeFoldRegionInfo)) {
            return false;
        }
        CodeFoldRegionInfo that = (CodeFoldRegionInfo) other;
        return startLine == that.startLine && endLine == that.endLine
            && depth == that.depth && collapsed == that.collapsed;
    }

    @Override
    public int hashCode() {
        return ((startLine * 31 + endLine) * 31 + depth) * 31 + (collapsed ? 1 : 0);
    }

    @Override
    public String toString() {
        return "CodeFoldRegionInfo{" + startLine + ".." + endLine + " depth=" + depth
            + (collapsed ? " collapsed" : "") + "}";
    }
}
