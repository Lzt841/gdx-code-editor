package com.lzt841.editor.structure;

import com.badlogic.gdx.utils.Array;

/**
 * A structure provider that can analyse one line at a time, so an edit does not force a whole-document
 * re-scan.
 *
 * <p>{@link CodeStructureProvider#analyze} walks the entire document per call, which on a large file is
 * why the editor debounces it and why fold regions lag behind your typing. An incremental provider
 * instead threads a small integer state from line to line and reports block boundaries as it goes,
 * letting the editor re-scan only from the edited line until the state converges with what it had
 * cached.
 *
 * <p><strong>The provider does not own the block stack.</strong> It opens and closes blocks through
 * {@link CodeStructureLineContext}, and the caller keeps the frames. That is deliberate: everything
 * carrying a line number lives on the caller's side, so when an edit shifts line numbers the caller can
 * fix them up without the provider knowing. What is left for the provider is per-line lexing, which is
 * the part that actually differs between languages.
 *
 * <p>So the state only has to capture what a line needs from the lines above it that is <em>not</em>
 * already in the stack — "inside a block comment", "inside a triple-quoted string". Depth, start lines
 * and indent widths belong in the stack instead. States are compared with {@code ==}, so encode them as
 * small constants, not hashes.
 *
 * <p>Implementations must be pure: the same line with the same incoming state and the same stack must
 * always produce the same events and the same outgoing state. The editor relies on that to conclude
 * that a converged state means everything below is unchanged.
 *
 * <p>Extend {@link AbstractIncrementalStructureProvider} rather than implementing this directly, and the
 * whole-document {@link CodeStructureProvider#analyze} contract is filled in from the per-line method,
 * so the two paths cannot disagree.
 */
public interface IncrementalCodeStructureProvider extends CodeStructureProvider {
    /** State at the start of line 0. */
    int START_STATE = 0;

    /**
     * Analyses one line, reporting what it does to the block structure, and returns the state the next
     * line starts in.
     *
     * @param line the line text, without any line terminator
     * @param startState state at this line's first character, {@link #START_STATE} for line 0
     * @param context where to read the block stack and report indent level and block boundaries; only
     *     valid for the duration of this call
     * @return state at the start of the following line
     */
    int analyzeLine(CharSequence line, int startState, CodeStructureLineContext context);

    /**
     * Called once after the last line, to deal with blocks still open at end of file.
     *
     * <p>The two languages want opposite things here, which is why this is a separate hook rather than a
     * rule. An unclosed brace is a syntax error and folding it would fold to the wrong place, so the
     * brace provider leaves it open. An indentation-based block simply ends when the file does, so the
     * Python provider closes everything at {@link CodeStructureLineContext#getLastContentLine()}. The
     * default does nothing, which is the brace behaviour.
     *
     * <p>{@link CodeStructureLineContext#getLineIndex()} is the document's line count here, one past the
     * last line, so use {@code getLastContentLine()} for the end line rather than the current index.
     */
    default void finish(int endState, CodeStructureLineContext context) {
    }

    /**
     * Symbols for a document whose fold regions have just been computed, or null for none.
     *
     * <p>Exists so a caller driving {@link #analyzeLine} directly can still get symbols without going
     * through {@link CodeStructureProvider#analyze}, which would re-walk the whole document — the exact
     * cost the per-line path is there to avoid. The default returns null;
     * {@link AbstractIncrementalStructureProvider} delegates to its {@link CodeSymbolProvider}.
     *
     * @param foldRegions the regions just computed, in the order they were closed
     */
    default Array<CodeSymbol> extractSymbols(Array<String> lines, Array<CodeFoldRegion> foldRegions) {
        return null;
    }
}
