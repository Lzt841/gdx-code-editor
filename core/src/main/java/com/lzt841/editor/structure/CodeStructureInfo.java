package com.lzt841.editor.structure;

import com.badlogic.gdx.utils.Array;

/** Structural analysis output used for block guides and folding. */
public class CodeStructureInfo {
    public final int[] indentLevels;
    public final Array<CodeFoldRegion> foldRegions;

    public CodeStructureInfo(int[] indentLevels, Array<CodeFoldRegion> foldRegions) {
        this.indentLevels = indentLevels;
        this.foldRegions = foldRegions != null ? foldRegions : new Array<>();
    }
}
