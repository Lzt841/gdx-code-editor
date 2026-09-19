package com.lzt841.editor.lwjgl3;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.glutils.PixmapTextureData;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.BitmapFont.BitmapFontData;
import com.badlogic.gdx.graphics.g2d.BitmapFont.Glyph;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.graphics.g2d.freetype.FreeType;
import com.badlogic.gdx.graphics.g2d.freetype.FreeTypeFontGenerator;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.IntMap;
import com.badlogic.gdx.utils.IntSet;
import com.lzt841.editor.InlineImage;
import com.lzt841.editor.InlineImageProvider;
import java.lang.reflect.Field;
import java.nio.ByteBuffer;

/**
 * Draws the emoji the editor's own bitmap font cannot carry.
 *
 * <p>The editor renders with a single {@link BitmapFont} generated from a CJK font, and a bitmap font is
 * keyed by {@code char}, so a supplementary-plane emoji cannot be in it at all: it arrives as a
 * notdef box for each of its two surrogate halves, which is why an emoji shows as two squares. This
 * provider rasterises the code points that font does not carry, from a real emoji font, into an atlas
 * of its own, and answers with an {@link InlineImage} so the editor draws the raster instead of the
 * boxes — one column wide, and deleted as one unit by the document's grapheme walk.
 *
 * <p>It is not a colour-emoji renderer. libGDX 1.14 exposes no FT_LOAD_COLOR, so a colour font's CBDT
 * and COLR layers are unreachable; what this draws is the monochrome outline, baked into the editor's
 * text colour. That is what a monochrome glyph is — a coverage mask — so it composites identically
 * with the glyphs beside it, and it stays legible on any background.
 *
 * <p>The members of an emoji sequence that carry no ink of their own — the zero-width joiner, the
 * variation selectors, the tag characters of a flag's tag sequence — are answered with a shared
 * zero-width image, so they draw no box and take no column. Without that, a ZWJ family sequence would
 * render as its members separated by visible joiner boxes.
 *
 * <p>The editor asks for every column of every line it measures, not only the visible ones, so
 * {@link #imageAt} answers from a cache and never allocates. The first sight of a code point does the
 * rasterisation and the atlas upload inline; that is safe inside the measure pass because
 * {@link com.badlogic.gdx.graphics.g2d.SpriteBatch#flush} re-binds its texture before it draws, so a
 * texture bound by the upload cannot leave a pending batch drawing against the wrong one.
 */
public class EmojiImageProvider implements InlineImageProvider {
    /** A side of the atlas. 1024 holds several hundred rasters at the size the editor uses. */
    private static final int PAGE_SIZE = 1024;
    /** The gap between two packed rasters, so linear filtering cannot bleed one into its neighbour. */
    private static final int PAGE_PADDING = 2;
    /** An emoji is sized against the line height, the same share of it the rich-text symbols take. */
    private static final float SIZE_RATIO = 0.8f;
    /** FT_LOAD_RENDER: rasterise the glyph while loading it. libGDX 1.14 gives this flag no name. */
    private static final int FT_LOAD_RENDER = 4;
    /** A bitmap font keeps its glyphs in pages of 512 chars, addressed by this shift and mask. */
    private static final int GLYPH_PAGE_BITS = 9;
    private static final int GLYPH_PAGE_MASK = 511;

    private static final int ZWJ = 0x200D;
    private static final int VARIATION_SELECTOR_FIRST = 0xFE00;
    private static final int VARIATION_SELECTOR_LAST = 0xFE0F;
    private static final int TAG_FIRST = 0xE0020;
    private static final int TAG_LAST = 0xE007F;

    /** Where a font that carries emoji is looked for, in descending order of how good it is likely to be. */
    private static final String[] EMOJI_FONT_PATHS = {
        "C:/Windows/Fonts/seguiemj.ttf",
        "C:/Windows/Fonts/seguisym.ttf",
        "/System/Library/Fonts/Apple Color Emoji.ttc",
        "/System/Library/Fonts/Supplemental/Arial Unicode.ttf",
        "/usr/share/fonts/truetype/noto/NotoEmoji-Regular.ttf",
        "/usr/share/fonts/opentype/noto/NotoEmoji-Regular.ttf",
    };

    /**
     * Whether a candidate font can draw any emoji at all. A font is accepted only if one of these has a
     * real glyph with ink, so a font whose entire emoji range is the notdef box is skipped rather than
     * installed as the drawer of every emoji.
     */
    private static final int[] PROBE_CODE_POINTS = {0x1F600, 0x1F680, 0x1F389, 0x2764};

    private final BitmapFont font;
    private final Color textColor;
    private final float emojiSize;
    private final int pixelSize;

    private final FreeType.Face emojiFace;
    private final FreeType.Face editorFace;

    /** The answer for a code point this provider has already resolved, and the ones it has given up on. */
    private final IntMap<InlineImage> images = new IntMap<>();
    private final IntSet unresolved = new IntSet();

    private final Array<Texture> pages = new Array<>();
    private final Array<FreeType.Face> faces = new Array<>();
    private final Array<FreeTypeFontGenerator> generators = new Array<>();

    /** Drawn once, shared by every code point that must take no space and draw no ink. */
    private InlineImage blankImage;
    /** Drawn once, shared by every supplementary code point no candidate font can render. */
    private InlineImage unknownImage;

    private Texture blankTexture;

    // The pack cursor starts off-page so the very first raster allocates a page rather than assuming one.
    private int packX = PAGE_SIZE;
    private int packY = PAGE_SIZE;
    private int rowHeight;

    /**
     * @param font the editor's own font, asked whether it already draws a code point
     * @param editorFontFile the file that font was generated from, or null if it is not a FreeType font:
     *     an incremental font loads its glyphs on demand, so the font object itself cannot answer the
     *     question, and the face has to
     * @param lineHeight the editor's line height, which the emoji is sized against
     * @param textColor the colour the rasters are baked in
     */
    public EmojiImageProvider(BitmapFont font, FileHandle editorFontFile, float lineHeight, Color textColor) {
        this.font = font;
        this.textColor = new Color(textColor);
        this.emojiSize = lineHeight * SIZE_RATIO;
        this.pixelSize = Math.max(1, Math.round(emojiSize));
        this.editorFace = openFace(editorFontFile);
        this.emojiFace = openEmojiFace();
        this.blankImage = blankImage();
    }

    @Override
    public InlineImage imageAt(int line, int column, CharSequence lineText) {
        if (column < 0 || column >= lineText.length()) {
            return null;
        }
        char character = lineText.charAt(column);
        int codePoint;
        if (Character.isHighSurrogate(character) && column + 1 < lineText.length()
            && Character.isLowSurrogate(lineText.charAt(column + 1))) {
            // A supplementary character is answered as a whole from its first char; the editor gives the
            // second one no advance and draws nothing there, which is what collapses the pair to a column.
            codePoint = Character.toCodePoint(character, lineText.charAt(column + 1));
        } else if (Character.isLowSurrogate(character)) {
            // Answered with the char before it. Returning an image here as well would draw it twice.
            return null;
        } else {
            codePoint = character;
        }
        InlineImage cached = images.get(codePoint);
        if (cached != null) {
            return cached;
        }
        if (unresolved.contains(codePoint)) {
            return null;
        }
        InlineImage resolved = resolve(codePoint);
        if (resolved == null) {
            unresolved.add(codePoint);
            return null;
        }
        images.put(codePoint, resolved);
        return resolved;
    }

    /**
     * The image for a code point, or null if the editor's own glyph should be drawn.
     */
    private InlineImage resolve(int codePoint) {
        if (isZeroWidth(codePoint)) {
            return blankImage;
        }
        if (isUnsubstitutable(codePoint)) {
            // A control character or a combining mark needs shaping and placement this provider does not
            // do; the editor's font is also the better answer wherever it already carries the glyph.
            return null;
        }
        InlineImage raster = rasterize(emojiFace, codePoint);
        if (raster != null) {
            return raster;
        }
        if (Character.toChars(codePoint).length == 2) {
            // Nothing draws this, and a bitmap font would draw it as two notdef boxes — one for each
            // surrogate half. One box of our own, in one column, is the honest representation.
            return unknownImage();
        }
        return null;
    }

    private static boolean isZeroWidth(int codePoint) {
        return codePoint == ZWJ
            || codePoint >= VARIATION_SELECTOR_FIRST && codePoint <= VARIATION_SELECTOR_LAST
            || codePoint >= TAG_FIRST && codePoint <= TAG_LAST;
    }

    private boolean isUnsubstitutable(int codePoint) {
        if (codePoint < 0x20 || codePoint == 0x7F) {
            return true;
        }
        int type = Character.getType(codePoint);
        if (type == Character.NON_SPACING_MARK || type == Character.COMBINING_SPACING_MARK) {
            // A diacritic has no width of its own and is drawn against the glyph before it; giving it a
            // square image would give it a column it should not have. An enclosing mark is different: the
            // keycap is a shape in its own right, and it is what makes a keycap sequence recognisable.
            return true;
        }
        return editorFontCarries(codePoint);
    }

    /**
     * Whether the editor's own font draws this code point, and so should be left to. A supplementary code
     * point cannot be in a {@code char}-keyed font at all, which makes this a BMP question.
     *
     * <p>An incremental font rasterises a missing glyph the first time it is asked for it, so the glyph
     * array holds only what has already been used and cannot answer the question. The file it was
     * generated from can: a code point a font maps has a glyph index, and 0 is the notdef index.
     */
    private boolean editorFontCarries(int codePoint) {
        if (codePoint > Character.MAX_VALUE) {
            return false;
        }
        char character = (char) codePoint;
        BitmapFontData data = font.getData();
        Glyph[] page = data.glyphs[character >> GLYPH_PAGE_BITS];
        if (page != null && page[character & GLYPH_PAGE_MASK] != null) {
            return true;
        }
        return editorFace != null && editorFace.getCharIndex(codePoint) != 0;
    }

    /**
     * Rasterises {@code codePoint} from {@code face} and packs it into the atlas.
     */
    private InlineImage rasterize(FreeType.Face face, int codePoint) {
        if (face == null || face.getCharIndex(codePoint) == 0) {
            return null;
        }
        if (!face.loadChar(codePoint, FT_LOAD_RENDER)) {
            return null;
        }
        FreeType.Bitmap bitmap = face.getGlyph().getBitmap();
        if (bitmap.getWidth() <= 0 || bitmap.getRows() <= 0) {
            return null;
        }
        Pixmap raster = rasterizeGlyph(bitmap, pixelSize, textColor);
        boolean hasInk = false;
        ByteBuffer pixels = raster.getPixels();
        for (int i = 0; i < raster.getWidth() * raster.getHeight(); i++) {
            if ((pixels.get(i * 4 + 3) & 0xFF) != 0) {
                hasInk = true;
                break;
            }
        }
        if (!hasInk) {
            // The font maps the code point to a glyph that draws nothing. Leaving the column to the
            // editor's own box is a clearer failure than an image that silently eats the column.
            raster.dispose();
            return null;
        }
        TextureRegion region = pack(raster);
        raster.dispose();
        return new InlineImage(region, emojiSize, emojiSize, 0f);
    }

    /**
     * Rasterises a FreeType bitmap into a square pixmap of side {@code size}, holding the glyph's aspect
     * and baking the colour in.
     *
     * <p>The result is straight-alpha — RGB is the colour, A is the coverage — because that is what the
     * editor's batch blends with. Premultiplying the colour by the coverage would darken the soft edges
     * twice, the same way a dimmed bold pass did.
     */
    static Pixmap rasterizeGlyph(FreeType.Bitmap bitmap, int size, Color color) {
        int sourceWidth = bitmap.getWidth();
        int sourceRows = bitmap.getRows();
        // Hold the aspect: a flag pole is not square, and stretching it would be a visible distortion.
        int longest = Math.max(sourceWidth, sourceRows);
        int targetWidth = Math.max(1, Math.round(size * (sourceWidth / (float) longest)));
        int targetHeight = Math.max(1, Math.round(size * (sourceRows / (float) longest)));

        Pixmap pixmap = new Pixmap(targetWidth, targetHeight, Pixmap.Format.RGBA8888);
        ByteBuffer target = pixmap.getPixels();
        int base = Color.rgba8888(color) & 0xFFFFFF00;

        ByteBuffer source = bitmap.getBuffer();
        int pitch = bitmap.getPitch();
        int stride = Math.abs(pitch);
        int pixelMode = bitmap.getPixelMode();
        // A negative pitch means FreeType stored the rows bottom-up, so the buffer starts at the last row.
        int firstRowOffset = pitch >= 0 ? 0 : (sourceRows - 1) * stride;

        // Average the source pixels each target pixel covers, so a glyph rasterised larger than the
        // column it is drawn into does not alias when the atlas is sampled back down.
        float scaleX = sourceWidth / (float) targetWidth;
        float scaleY = sourceRows / (float) targetHeight;
        for (int targetY = 0; targetY < targetHeight; targetY++) {
            int sourceY0 = (int) (targetY * scaleY);
            int sourceY1 = Math.max(sourceY0 + 1, (int) ((targetY + 1) * scaleY));
            for (int targetX = 0; targetX < targetWidth; targetX++) {
                int sourceX0 = (int) (targetX * scaleX);
                int sourceX1 = Math.max(sourceX0 + 1, (int) ((targetX + 1) * scaleX));
                int coverage = 0;
                int samples = 0;
                for (int sourceY = sourceY0; sourceY < sourceY1 && sourceY < sourceRows; sourceY++) {
                    int rowOffset = pitch >= 0 ? sourceY * stride : firstRowOffset - sourceY * stride;
                    for (int sourceX = sourceX0; sourceX < sourceX1 && sourceX < sourceWidth; sourceX++) {
                        coverage += grayAt(source, rowOffset + sourceX, pixelMode);
                        samples++;
                    }
                }
                int alpha = samples == 0 ? 0 : Math.min(255, Math.round(coverage / (float) samples));
                target.putInt((targetY * targetWidth + targetX) * 4, base | alpha);
            }
        }
        return pixmap;
    }

    /**
     * One pixel of a FreeType bitmap as 0–255 coverage, unpacking a monochrome bitmap on the way.
     */
    private static int grayAt(ByteBuffer source, int offset, int pixelMode) {
        if (pixelMode == FreeType.FT_PIXEL_MODE_MONO) {
            // Monochrome rows are packed most-significant bit first.
            return (source.get(offset >> 3) & (1 << (7 - (offset & 7)))) != 0 ? 255 : 0;
        }
        return source.get(offset) & 0xFF;
    }

    /**
     * Opens the first candidate that draws some emoji, or null if none can.
     */
    private FreeType.Face openEmojiFace() {
        for (String path : EMOJI_FONT_PATHS) {
            FreeType.Face candidate = openFace(new FileHandle(path));
            if (candidate != null && drawsEmoji(candidate)) {
                return candidate;
            }
            if (candidate != null) {
                candidate.dispose();
                faces.removeValue(candidate, true);
                generators.pop();
            }
        }
        return null;
    }

    private static boolean drawsEmoji(FreeType.Face face) {
        for (int codePoint : PROBE_CODE_POINTS) {
            if (face.getCharIndex(codePoint) == 0) {
                continue;
            }
            if (!face.loadChar(codePoint, FT_LOAD_RENDER)) {
                continue;
            }
            FreeType.Bitmap bitmap = face.getGlyph().getBitmap();
            if (bitmap.getWidth() <= 0 || bitmap.getRows() <= 0) {
                continue;
            }
            ByteBuffer pixels = bitmap.getBuffer();
            int stride = Math.abs(bitmap.getPitch());
            for (int y = 0; y < bitmap.getRows(); y++) {
                for (int x = 0; x < bitmap.getWidth(); x++) {
                    if (grayAt(pixels, y * stride + x, bitmap.getPixelMode()) != 0) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /**
     * The shared image for a code point that must draw nothing and take no column.
     */
    private InlineImage blankImage() {
        Pixmap pixmap = new Pixmap(1, 1, Pixmap.Format.RGBA8888);
        blankTexture = new Texture(new PixmapTextureData(pixmap, null, false, false));
        pixmap.dispose();
        return new InlineImage(new TextureRegion(blankTexture), 0f, 0f, 0f);
    }

    /**
     * The shared image for a supplementary code point nothing could render: an empty rounded box the
     * width of a column, drawn once and reused for all of them.
     */
    private InlineImage unknownImage() {
        if (unknownImage == null) {
            Pixmap pixmap = new Pixmap(pixelSize, pixelSize, Pixmap.Format.RGBA8888);
            pixmap.setColor(textColor);
            int inset = Math.max(1, pixelSize / 8);
            // A stroke this wide keeps its weight when the atlas is sampled with linear filtering.
            pixmap.drawRectangle(inset, inset, pixelSize - 2 * inset, pixelSize - 2 * inset);
            for (int i = 1; i < inset; i++) {
                pixmap.drawRectangle(inset - i, inset - i,
                    pixelSize - 2 * inset + 2 * i, pixelSize - 2 * inset + 2 * i);
            }
            TextureRegion region = pack(pixmap);
            pixmap.dispose();
            unknownImage = new InlineImage(region, emojiSize, emojiSize, 0f);
        }
        return unknownImage;
    }

    /**
     * Uploads a raster into the atlas and returns where it went.
     */
    private TextureRegion pack(Pixmap raster) {
        int width = raster.getWidth();
        int height = raster.getHeight();
        if (packX + width + PAGE_PADDING > PAGE_SIZE) {
            packX = PAGE_PADDING;
            packY += rowHeight + PAGE_PADDING;
            rowHeight = 0;
        }
        if (packY + height + PAGE_PADDING > PAGE_SIZE || pages.size == 0) {
            newPage();
        }
        Texture page = pages.peek();
        int x = packX;
        int y = packY;
        page.draw(raster, x, y);
        packX += width + PAGE_PADDING;
        rowHeight = Math.max(rowHeight, height);
        return new TextureRegion(page, x, y, width, height);
    }

    private void newPage() {
        Pixmap pixmap = new Pixmap(PAGE_SIZE, PAGE_SIZE, Pixmap.Format.RGBA8888);
        Texture page = new Texture(new PixmapTextureData(pixmap, null, false, false));
        pixmap.dispose();
        page.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
        pages.add(page);
        packX = PAGE_PADDING;
        packY = PAGE_PADDING;
        rowHeight = 0;
    }

    private FreeType.Face openFace(FileHandle file) {
        if (file == null || !file.exists()) {
            return null;
        }
        try {
            FreeTypeFontGenerator generator = new FreeTypeFontGenerator(file);
            FreeType.Face face = faceOf(generator);
            if (!face.setPixelSizes(pixelSize, pixelSize)) {
                throw new IllegalStateException("setPixelSizes failed");
            }
            generators.add(generator);
            faces.add(face);
            return face;
        } catch (Exception error) {
            // A font that cannot be opened is the same as one that is not installed.
            Gdx.app.error("EmojiImageProvider", "could not open " + file, error);
            return null;
        }
    }

    /**
     * The face a generator opened. The field is package-private and the generator does not expose it,
     * because it is only meant to reach the rasteriser through a {@link FreeTypeFontGenerator}.
     */
    private static FreeType.Face faceOf(FreeTypeFontGenerator generator) throws Exception {
        Field faceField = FreeTypeFontGenerator.class.getDeclaredField("face");
        faceField.setAccessible(true);
        return (FreeType.Face) faceField.get(generator);
    }

    /** Frees the atlas and the faces. The generators are held, not disposed: see the field. */
    public void dispose() {
        if (blankTexture != null) {
            blankTexture.dispose();
        }
        for (Texture page : pages) {
            page.dispose();
        }
        for (FreeType.Face face : faces) {
            face.dispose();
        }
        // Deliberately not disposing the generators. Their FreeType library is the one the editor's own
        // font still rasterises from, and FreeTypeFontGenerator.dispose() would free it out from under it.
    }
}
