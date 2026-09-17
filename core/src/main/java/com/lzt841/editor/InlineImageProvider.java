package com.lzt841.editor;

/**
 * Replaces characters of a line with inline images — emoji, icons, or any glyph the editor's single
 * {@link com.badlogic.gdx.graphics.g2d.BitmapFont} does not carry.
 *
 * <p><b>Called during measurement, not just during drawing.</b> The editor asks for an image wherever it
 * computes a column's advance, so the answer feeds wrapping, cursor placement, click hit-testing, and
 * text alignment as well as rendering. That is what makes an image column behave like a glyph column:
 * no special-case code anywhere else in the editor. It also means the provider must be
 * <em>cheap</em> — O(1), no allocation, no I/O — because a long file measures a column at a time, and
 * the answer is not cached by the editor. A lookup keyed on the codepoint is the right shape.
 *
 * <p><b>Called on every line, not only the ones on screen.</b> Re-measuring the document — a resize, a
 * zoom, a wrap-width change, the provider being (re)installed — walks every line's columns, visible or
 * not, and the longest line is measured for the scroll extent even when it is off screen. The cost
 * contract is per call, and a per-call cost that is fine for a visible row is multiplied by the whole
 * document here. The text passed in is the line as the editor measures it, which in password mode is
 * already masked; the editor does not call the provider at all in that mode, so a producer never has to
 * distinguish masked from real text.
 *
 * <p><b>Supplementary characters.</b> A codepoint above U+FFFF arrives as a surrogate pair, and the
 * editor asks once per {@code char}, so answer from the <em>high</em> surrogate and return null from the
 * low one: the pair collapses onto one column, and the low surrogate is then neither measured nor drawn.
 * The {@code column} and {@code lineText} you are given are the high surrogate's; a lookup on the
 * codepoint via {@link Character#toCodePoint(char, char)} is the right shape. See
 * {@link InlineImage} for what happens to a pair answered from the wrong half.
 *
 * <p><b>Stability.</b> The editor holds no state between calls, so a provider that changes its mind — a
 * texture atlas swapped, a setting toggled — must call {@code CodeEditor.setInlineImageProvider} again,
 * even with the same instance, to force a re-measure. Results that drift without that call produce a
 * layout that matches stale measurements.
 *
 * @see InlineImage
 */
public interface InlineImageProvider {
    /**
     * The image to draw at {@code column} of {@code line}, or null to render the character as a glyph.
     *
     * @param line document line index
     * @param column character index into the line, in {@code [0, lineText.length())}
     * @param lineText the line's text as the editor measures it
     */
    InlineImage imageAt(int line, int column, CharSequence lineText);
}
