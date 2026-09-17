package com.lzt841.editor;

import com.badlogic.gdx.graphics.g2d.TextureRegion;

/**
 * One inline image the editor draws in place of a character, which is how a {@code BitmapFont}-only
 * editor shows emoji, icons, or any glyph the font does not carry.
 *
 * <p><b>Placement.</b> The image is centred on the band the font's own ink occupies — from the cap height
 * down to the descent line — so an icon lines up with the letters beside it instead of with the padding
 * the row adds above and below. {@code baselineOffset} shifts it from that centre, a positive value
 * dropping it, measured in editor pixels: 0 is the default and the right answer for an emoji, and a small
 * positive value hangs it the way a glyph with a descender would sit. An image taller than the row is
 * scaled to fit with its aspect ratio held, so no image can paint the neighbouring rows.
 *
 * <p><b>Advance.</b> {@link #width} is both the drawn width and the horizontal space the column occupies:
 * it is what cursor placement, click hit-testing, wrapping, and text alignment all measure against. An
 * image whose width does not match its pixels will still be hit-tested correctly, because every consumer
 * reads the same number.
 *
 * <p><b>One column.</b> The editor collapses a supplementary-plane character — two {@code char}s in a
 * Java string — onto a single column: answer from the high surrogate and return null from the low one.
 * The low surrogate is then neither measured nor drawn, so a U+1F600 emoji occupies exactly the width of
 * the image and the next glyph starts flush against it. Answering for both halves doubles the measured
 * width of the glyph; answering from the low surrogate alone draws the image one column late. A
 * surrogate pair the provider does not answer for is still two glyphs and keeps both advances.
 *
 * <p>Instances are immutable and may be cached by the producer; the editor keeps only references.
 */
public final class InlineImage {
    /** What is drawn, at {@link #width} by {@link #height}. */
    public final TextureRegion region;
    /** Horizontal space the column occupies. */
    public final float width;
    /** Drawn height. */
    public final float height;
    /** Distance the bottom edge sits below the text baseline; never negative in practice. */
    public final float baselineOffset;

    public InlineImage(TextureRegion region, float width, float height, float baselineOffset) {
        if (region == null) {
            throw new IllegalArgumentException("region cannot be null");
        }
        this.region = region;
        this.width = width;
        this.height = height;
        this.baselineOffset = baselineOffset;
    }

    /** Image filling a square column, sitting on the baseline. */
    public InlineImage(TextureRegion region, float size) {
        this(region, size, size, 0f);
    }

    @Override
    public String toString() {
        return "InlineImage{" + width + "x" + height + " offset=" + baselineOffset + "}";
    }
}
