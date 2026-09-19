package com.lzt841.editor.lwjgl3;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.lzt841.editor.InlineImage;
import com.lzt841.editor.InlineImageProvider;

/**
 * Stand-in for an emoji atlas: cuts tiles out of {@code libgdx.png} and hands the right one back for a
 * few marker characters, so the demo can show an inline image column without shipping an emoji font.
 *
 * <p>Answers only for the characters the demo text actually uses, which is what keeps this O(1) — the
 * editor asks once per column per measurement, so a provider that scanned or allocated would be visible
 * in a large file. BMP symbols and real supplementary-plane emoji both go through it: the latter arrive
 * as surrogate pairs, and this provider answers from the high surrogate, which is what makes the pair
 * collapse into one column.
 *
 * <p>Everything this has no tile for falls through to the {@link EmojiImageProvider}, which rasterises
 * real glyphs from an emoji font; the two share the editor's one provider slot without it having to know
 * there are two.
 */
public class RichTextImageProvider implements InlineImageProvider {
    private final TextureRegion[] tiles;
    private final float size;
    private final EmojiImageProvider emoji;

    public RichTextImageProvider(float lineHeight, EmojiImageProvider emoji) {
        this.size = lineHeight * 0.8f;
        this.emoji = emoji;
        this.tiles = loadTiles();
    }

    private TextureRegion[] loadTiles() {
        try {
            // The demo's working directory is the project's assets folder, where libgdx.png lives.
            Texture texture = new Texture(Gdx.files.internal("libgdx.png"));
            texture.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
            int columns = 4;
            int rows = 2;
            TextureRegion[] regions = new TextureRegion[columns * rows];
            int tileWidth = texture.getWidth() / columns;
            int tileHeight = texture.getHeight() / rows;
            int index = 0;
            for (int row = 0; row < rows; row++) {
                for (int column = 0; column < columns; column++) {
                    regions[index++] = new TextureRegion(
                        texture, column * tileWidth, row * tileHeight, tileWidth, tileHeight);
                }
            }
            return regions;
        } catch (Exception error) {
            // No atlas, no images: the editor draws the characters as glyphs instead, so the demo degrades
            // rather than crashing over a missing asset.
            Gdx.app.error("RichTextImageProvider", "libgdx.png not available; drawing glyphs instead", error);
            return new TextureRegion[0];
        }
    }

    @Override
    public InlineImage imageAt(int line, int column, CharSequence lineText) {
        if (tiles.length == 0 || column >= lineText.length()) {
            return null;
        }
        char character = lineText.charAt(column);
        int tile = -1;
        if (Character.isHighSurrogate(character) && column + 1 < lineText.length()) {
            // A real emoji is a surrogate pair: the editor asks once per char and collapses the pair onto
            // this column, so answer here and let the low surrogate return null below.
            char low = lineText.charAt(column + 1);
            if (Character.isLowSurrogate(low)) {
                tile = tileForCodePoint(Character.toCodePoint(character, low));
            }
        } else if (!Character.isLowSurrogate(character)) {
            tile = tileForCharacter(character);
        }
        if (tile < 0) {
            // Not one of the demo tiles; the emoji font gets a chance at it instead.
            return emoji.imageAt(line, column, lineText);
        }
        // The editor centres the image on the text's own ink band, so a square tile the size it asked for
        // already frames the letters beside it; an offset of 0 is the right answer for an emoji.
        return new InlineImage(tiles[tile], size, size, 0f);
    }

    /** Emoji stand-ins, picked so they do not collide with the highlighter's markers. */
    private int tileForCharacter(char character) {
        switch (character) {
            case '★':
                return 0;
            case '☆':
                return 1;
            case '✔':
                return 2;
            case '✖':
                return 3;
            case '▶':
                return 4;
            case '◀':
                return 5;
            case '♥':
                return 6;
            case '♠':
                return 7;
            default:
                return -1;
        }
    }

    /** Real supplementary-plane emoji, which is what the surrogate-pair handling exists for. */
    private int tileForCodePoint(int codePoint) {
        switch (codePoint) {
            case 0x1F600: // 😀
                return 0;
            case 0x1F680: // 🚀
                return 4;
            case 0x1F389: // 🎉
                return 2;
            default:
                return -1;
        }
    }

    /** Disposes the atlas this provider cut its tiles from. */
    public void dispose() {
        if (tiles.length > 0) {
            tiles[0].getTexture().dispose();
        }
    }
}
