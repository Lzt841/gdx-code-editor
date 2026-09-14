package com.lzt841.editor.structure;

import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.IntArray;

/**
 * Drives an {@link IncrementalCodeStructureProvider} across lines, holding the block stack the provider
 * is not allowed to hold itself.
 *
 * <p>One scanner is the whole mechanism: {@link AbstractIncrementalStructureProvider} uses it to walk a
 * document from the top, and the editor uses it to resume from a {@link Checkpoint} and re-scan a few
 * lines after an edit. Both go through the same {@link #scanLine} call, so a provider cannot behave
 * differently on the two paths.
 *
 * <p>Not thread safe, and not reentrant: it is the {@link CodeStructureLineContext} passed to the
 * provider, so a provider that stored that reference and used it later would be mutating the scanner's
 * live stack.
 */
public class CodeStructureScanner implements CodeStructureLineContext {

    private final IncrementalCodeStructureProvider provider;
    /** Key each open block was opened with, innermost last. */
    private final IntArray blockKeys = new IntArray();
    /** Line each open block was opened on, parallel to {@link #blockKeys}. */
    private final IntArray blockStartLines = new IntArray();
    /** Regions closed so far, in the order the provider closed them. */
    private final Array<CodeFoldRegion> regions = new Array<CodeFoldRegion>();
    /** Scan line each region was closed on, parallel to {@link #regions}. */
    private final IntArray regionEmitLines = new IntArray();

    private int state = IncrementalCodeStructureProvider.START_STATE;
    private int lineIndex;
    private int lastContentLine = -1;
    private int indentLevel;

    public CodeStructureScanner(IncrementalCodeStructureProvider provider) {
        if (provider == null) {
            throw new IllegalArgumentException("provider cannot be null");
        }
        this.provider = provider;
    }

    /** Returns the scanner to the top of a document, dropping the stack and any collected regions. */
    public void reset() {
        blockKeys.clear();
        blockStartLines.clear();
        regions.clear();
        regionEmitLines.clear();
        state = IncrementalCodeStructureProvider.START_STATE;
        lineIndex = 0;
        lastContentLine = -1;
    }

    /**
     * Analyses the next line and returns its indent level.
     *
     * <p>The caller must pass lines in order without gaps: the provider's state and the stack both
     * describe the position just before this line. To jump, restore a {@link Checkpoint} instead.
     */
    public int scanLine(CharSequence line) {
        indentLevel = blockKeys.size;
        state = provider.analyzeLine(line == null ? "" : line, state, this);
        lineIndex++;
        return indentLevel;
    }

    /**
     * Runs the provider's end-of-file handling. Call once after the last line; {@link #getLineIndex()}
     * then reports one past the last line, as {@link IncrementalCodeStructureProvider#finish} documents.
     */
    public void finish() {
        provider.finish(state, this);
    }

    /** Regions closed so far. Owned by the scanner; clear it to collect only a window's worth. */
    public Array<CodeFoldRegion> getRegions() {
        return regions;
    }

    /**
     * Scan line each entry of {@link #getRegions()} was closed on, same size and order. Non-decreasing,
     * since it is just the scan position.
     */
    public IntArray getRegionEmitLines() {
        return regionEmitLines;
    }

    /** Provider state at the start of the next line to be scanned. */
    public int getState() {
        return state;
    }

    // --- CodeStructureLineContext ---

    @Override
    public int getLineIndex() {
        return lineIndex;
    }

    @Override
    public void setIndentLevel(int level) {
        indentLevel = Math.max(0, level);
    }

    @Override
    public int getBlockDepth() {
        return blockKeys.size;
    }

    @Override
    public int getBlockKey(int index) {
        return index >= 0 && index < blockKeys.size ? blockKeys.get(index) : 0;
    }

    @Override
    public int getBlockStartLine(int index) {
        return index >= 0 && index < blockStartLines.size ? blockStartLines.get(index) : -1;
    }

    @Override
    public void openBlock(int key) {
        blockKeys.add(key);
        blockStartLines.add(lineIndex);
    }

    @Override
    public void closeBlock(int endLine) {
        if (blockKeys.size == 0) {
            return;
        }
        // Depth is the frame's own index: the stack is push/pop only, so a frame sitting at index i was
        // pushed when the depth was i. That is why no depth is stored alongside the key.
        int depth = blockKeys.size - 1;
        int startLine = blockStartLines.pop();
        blockKeys.pop();
        if (endLine > startLine) {
            regions.add(new CodeFoldRegion(startLine, endLine, depth));
            // The line the scan was ON when this closed. Not the same as endLine for an indentation-based
            // block, and equal to the line count for one closed by finish(). An incremental caller splices
            // on this, so a region a re-scan will re-emit can be told from one it has to keep.
            regionEmitLines.add(lineIndex);
        }
    }

    @Override
    public void markContentLine() {
        lastContentLine = lineIndex;
    }

    @Override
    public int getLastContentLine() {
        return lastContentLine;
    }

    // --- checkpoints ---

    /**
     * Everything needed to resume scanning at one line: the provider's state plus the caller-owned stack
     * around it.
     *
     * <p>Held by the editor so an edit low in a document does not have to re-scan from line 0. The line
     * numbers inside are why {@link #shiftLines} exists — an edit above a checkpoint moves both the line
     * it describes and the start lines of every block open at that point.
     */
    public static final class Checkpoint {
        /** Line this checkpoint describes the start of. */
        public int line;
        public int state = IncrementalCodeStructureProvider.START_STATE;
        public int lastContentLine = -1;
        public final IntArray blockKeys = new IntArray();
        public final IntArray blockStartLines = new IntArray();

        /**
         * Adds {@code delta} to every line number at or after {@code fromLine}.
         *
         * <p>Applied per line number rather than to the checkpoint as a whole, because a block can have
         * opened above an edit and still be open below it: its start line must not move while the
         * checkpoint's own line does.
         */
        public void shiftLines(int fromLine, int delta) {
            if (delta == 0) {
                return;
            }
            if (line >= fromLine) {
                line += delta;
            }
            if (lastContentLine >= fromLine) {
                lastContentLine += delta;
            }
            for (int i = 0; i < blockStartLines.size; i++) {
                int start = blockStartLines.get(i);
                if (start >= fromLine) {
                    blockStartLines.set(i, start + delta);
                }
            }
        }

        /** Whether this describes the same position and stack as {@code other}, so scanning may stop. */
        public boolean matches(Checkpoint other) {
            if (other == null || other.state != state || other.lastContentLine != lastContentLine) {
                return false;
            }
            if (other.blockKeys.size != blockKeys.size) {
                return false;
            }
            for (int i = 0; i < blockKeys.size; i++) {
                if (other.blockKeys.get(i) != blockKeys.get(i)
                    || other.blockStartLines.get(i) != blockStartLines.get(i)) {
                    return false;
                }
            }
            return true;
        }

        public void set(Checkpoint other) {
            line = other.line;
            state = other.state;
            lastContentLine = other.lastContentLine;
            blockKeys.clear();
            blockKeys.addAll(other.blockKeys);
            blockStartLines.clear();
            blockStartLines.addAll(other.blockStartLines);
        }
    }

    /** Copies the scanner's current position into {@code into}, which is reused to avoid allocating. */
    public void captureCheckpoint(Checkpoint into) {
        into.line = lineIndex;
        into.state = state;
        into.lastContentLine = lastContentLine;
        into.blockKeys.clear();
        into.blockKeys.addAll(blockKeys);
        into.blockStartLines.clear();
        into.blockStartLines.addAll(blockStartLines);
    }

    /** Positions the scanner at a captured checkpoint. Collected regions are left alone. */
    public void restoreCheckpoint(Checkpoint from) {
        lineIndex = from.line;
        state = from.state;
        lastContentLine = from.lastContentLine;
        blockKeys.clear();
        blockKeys.addAll(from.blockKeys);
        blockStartLines.clear();
        blockStartLines.addAll(from.blockStartLines);
    }

    /** Start lines of the blocks open at the scanner's current position, outermost first. */
    public IntArray getOpenBlockStartLines() {
        return blockStartLines;
    }
}
