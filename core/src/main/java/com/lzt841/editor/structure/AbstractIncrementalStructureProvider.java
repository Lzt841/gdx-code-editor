package com.lzt841.editor.structure;

import com.badlogic.gdx.utils.Array;

/**
 * Implements the whole-document {@link CodeStructureProvider} contract on top of a single
 * {@link #analyzeLine} method, so a structure provider written once works with both the incremental path
 * and any code still calling {@link #analyze}.
 *
 * <p>Also holds the optional {@link CodeSymbolProvider}, since both built-in providers carry one and the
 * symbol pass is driven from the fold regions this class produces.
 */
public abstract class AbstractIncrementalStructureProvider implements IncrementalCodeStructureProvider {

    /**
     * Symbol extraction, or null for none. Off by default: symbols are opt-in so a setup that only wants
     * folding pays nothing.
     */
    protected CodeSymbolProvider symbolProvider;

    public CodeSymbolProvider getSymbolProvider() {
        return symbolProvider;
    }

    @Override
    public CodeStructureInfo analyze(Array<String> lines) {
        int lineCount = lines == null ? 0 : lines.size;
        int[] indentLevels = new int[lineCount];
        CodeStructureScanner scanner = new CodeStructureScanner(this);
        for (int i = 0; i < lineCount; i++) {
            indentLevels[i] = scanner.scanLine(lines.get(i));
        }
        scanner.finish();
        Array<CodeFoldRegion> regions = scanner.getRegions();
        return new CodeStructureInfo(indentLevels, regions,
            lines == null ? null : extractSymbols(lines, regions));
    }

    @Override
    public Array<CodeSymbol> extractSymbols(Array<String> lines, Array<CodeFoldRegion> foldRegions) {
        return symbolProvider == null || lines == null ? null : symbolProvider.extract(lines, foldRegions);
    }
}
