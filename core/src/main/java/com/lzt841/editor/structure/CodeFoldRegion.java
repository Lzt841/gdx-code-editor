package com.lzt841.editor.structure;

/** Structural fold region for a code document. */
public class CodeFoldRegion {
    public final int startLine;
    public final int endLine;
    public final int depth;

    public CodeFoldRegion(int startLine, int endLine, int depth) {
        this.startLine = startLine;
        this.endLine = endLine;
        this.depth = depth;
    }
}
