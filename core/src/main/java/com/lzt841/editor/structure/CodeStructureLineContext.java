package com.lzt841.editor.structure;

/**
 * What an {@link IncrementalCodeStructureProvider} may read and report while analysing one line.
 *
 * <p><strong>The block stack lives here, not in the provider.</strong> That is the whole point of the
 * split: a provider's only cross-line state is then a small {@code int} (see
 * {@link IncrementalCodeStructureProvider#START_STATE}), and everything that carries a line number —
 * the stack frames, the fold regions, the last content line — is owned by the caller, which is what
 * lets an edit shift those numbers without the provider being involved.
 *
 * <p>A provider must not hold onto an instance of this: it is only valid for the duration of the
 * {@link IncrementalCodeStructureProvider#analyzeLine} call it was passed to.
 */
public interface CodeStructureLineContext {

    /** Line currently being analysed. */
    int getLineIndex();

    /**
     * Records this line's nesting depth, used for indent guides. Defaults to the block depth at the
     * start of the line if never called, which is what a provider wants for a line that only contains
     * content.
     */
    void setIndentLevel(int level);

    /** Open blocks, outermost first. */
    int getBlockDepth();

    /**
     * The key the block at {@code index} was opened with, or 0 if out of range.
     *
     * <p>The key is whatever the provider needs to decide later whether this block should close: an
     * indent width for an indentation-based language, unused for a brace-based one. The caller only
     * stores it.
     */
    int getBlockKey(int index);

    /** Line the block at {@code index} was opened on, or -1 if out of range. */
    int getBlockStartLine(int index);

    /** Opens a block starting at the current line. */
    void openBlock(int key);

    /**
     * Closes the innermost open block, producing a fold region from its start line to {@code endLine}.
     *
     * <p>A region is only produced when {@code endLine} is below the block's start line, so a block
     * that opens and closes on one line folds to nothing. Does nothing when no block is open, so a
     * stray closing brace is not an error.
     *
     * <p>{@code endLine} is explicit because the two cases differ: a brace closes on the line the
     * {@code }} is on, while an indentation-based block closes at the last content line <em>above</em>
     * the line that de-dented. See {@link #getLastContentLine()}.
     */
    void closeBlock(int endLine);

    /**
     * Marks this line as carrying content, so {@link #getLastContentLine()} will report it afterwards.
     * A provider should call this for any line that is not blank or comment-only.
     */
    void markContentLine();

    /**
     * The most recent line marked with {@link #markContentLine()}, or -1 if there has been none.
     *
     * <p>Tracked by the caller rather than the provider because it is a line number, and line numbers
     * have to survive an edit shifting them.
     */
    int getLastContentLine();
}
