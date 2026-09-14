package com.lzt841.editor.structure;

import com.badlogic.gdx.utils.Array;

/**
 * Builds a document's symbol tree, for an outline view, a breadcrumb, or a go-to-symbol dialog.
 *
 * <p>Deliberately driven by the fold regions a {@link CodeStructureProvider} has already computed
 * rather than by a scan of its own. Structure analysis is the one O(document) pass the editor makes,
 * and on a large file it is debounced; running a second full scan for symbols would double that cost
 * and could disagree with the folding. A provider is therefore told which line ranges are blocks and
 * only has to classify their header lines.
 *
 * <p>Return the <em>roots</em> in document order, with nesting expressed through
 * {@link CodeSymbol#children}. Symbols that are not blocks at all — fields, imports, one-line
 * declarations — have no matching region, so a provider is free to scan the lines between regions for
 * them.
 */
public interface CodeSymbolProvider {
    /**
     * @param lines the whole document, owned by the caller. Do not retain it: the editor reuses this
     *     array between analyses.
     * @param foldRegions the block ranges just computed, in the order the structure provider emitted
     *     them. For brace-based providers that is innermost-first, since a region is closed when its
     *     closing brace is reached.
     * @return the root symbols in document order, or an empty array. Never null.
     */
    Array<CodeSymbol> extract(Array<String> lines, Array<CodeFoldRegion> foldRegions);
}
