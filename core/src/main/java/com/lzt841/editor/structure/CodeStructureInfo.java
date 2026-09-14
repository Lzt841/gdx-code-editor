package com.lzt841.editor.structure;

import com.badlogic.gdx.utils.Array;

/** Structural analysis output used for block guides, folding and the symbol tree. */
public class CodeStructureInfo {
    public final int[] indentLevels;
    public final Array<CodeFoldRegion> foldRegions;
    /**
     * Root symbols in document order, or empty when the provider does not produce them. Carried here
     * rather than fetched separately so a provider that already walks the document can emit both in
     * one pass.
     */
    public final Array<CodeSymbol> symbols;

    public CodeStructureInfo(int[] indentLevels, Array<CodeFoldRegion> foldRegions) {
        this(indentLevels, foldRegions, null);
    }

    public CodeStructureInfo(int[] indentLevels, Array<CodeFoldRegion> foldRegions, Array<CodeSymbol> symbols) {
        this.indentLevels = indentLevels;
        this.foldRegions = foldRegions != null ? foldRegions : new Array<CodeFoldRegion>();
        this.symbols = symbols != null ? symbols : new Array<CodeSymbol>(0);
    }
}
