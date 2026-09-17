package com.lzt841.editor;

/**
 * An inline image the pointer is on, reported by {@link InlineImageListener} when the image is clicked
 * or hovered.
 *
 * <p><b>Why a handle and not the {@link InlineImage} itself.</b> The image is what the producer
 * returned from {@link InlineImageProvider#imageAt}; this is where it is. A listener needs the
 * {@link #line} and {@link #column} to look the thing up in its own model — an emoji reaction needs the
 * message it belongs to, a diagnostic icon needs the error it stands for — and the image alone does not
 * carry that. Both are immutable, so the handle is cheap to keep.
 *
 * <p><b>Column is the image's column.</b> For a supplementary-plane character that is the high
 * surrogate of the pair; the low surrogate is not a column the editor reports, because it is neither
 * measured nor drawn.
 *
 * @see InlineImageListener
 */
public final class InlineImageHit {
    /** Document line the image is on. */
    public final int line;
    /** Character column the image replaces. */
    public final int column;
    /** The image the provider returned for that column. */
    public final InlineImage image;

    public InlineImageHit(int line, int column, InlineImage image) {
        if (image == null) {
            throw new IllegalArgumentException("image cannot be null");
        }
        this.line = line;
        this.column = column;
        this.image = image;
    }

    @Override
    public String toString() {
        return "InlineImageHit{line=" + line + ", column=" + column + ", image=" + image + "}";
    }
}
