package com.lzt841.editor.structure;

import java.util.ArrayList;
import java.util.List;

/** Structural analysis output used for block guides and folding. */
public class CodeStructureInfo {
    public final int[] indentLevels;
    public final List<CodeFoldRegion> foldRegions;

    public CodeStructureInfo(int[] indentLevels, List<CodeFoldRegion> foldRegions) {
        this.indentLevels = indentLevels;
        this.foldRegions = foldRegions != null ? foldRegions : new ArrayList<CodeFoldRegion>();
    }
}
