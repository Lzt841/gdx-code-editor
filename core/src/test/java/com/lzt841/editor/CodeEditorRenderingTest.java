package com.lzt841.editor;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Graphics;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.TextureData;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.scenes.scene2d.utils.BaseDrawable;
import com.badlogic.gdx.utils.Array;
import com.lzt841.editor.highlight.BuiltinCodeHighlighters;
import com.lzt841.editor.highlight.CodeTextStyle;
import com.lzt841.editor.highlight.CodeHighlightSpan;
import com.lzt841.editor.render.CodeEditorRowRenderContext;
import com.lzt841.editor.render.CodeEditorRowRenderer;
import com.lzt841.editor.render.CodeWhitespaceMode;
import java.lang.reflect.Proxy;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

/** Records the real draw pipeline without a display or GL rasterizer. */
public class CodeEditorRenderingTest {
    private GL20 previousGl, previousGl20;
    private Graphics previousGraphics;
    private Texture pixel;
    private CodeEditor editor;
    private RecordingBatch recording;
    private final Array<Row> rows = new Array<>();

    @Before public void createEditor() {
        previousGl = Gdx.gl;
        previousGl20 = Gdx.gl20;
        previousGraphics = Gdx.graphics;
        Gdx.graphics = (Graphics) Proxy.newProxyInstance(Graphics.class.getClassLoader(), new Class<?>[]{Graphics.class},
            (proxy, method, args) -> defaultValue(method.getReturnType()));
        Gdx.gl = Gdx.gl20 = (GL20) Proxy.newProxyInstance(GL20.class.getClassLoader(), new Class<?>[]{GL20.class},
            (proxy, method, args) -> method.getName().equals("glGenTexture") ? 1 : defaultValue(method.getReturnType()));
        pixel = new Texture(new PixelData());
        BitmapFont.BitmapFontData data = new BitmapFont.BitmapFontData();
        data.lineHeight = 16f;
        data.spaceXadvance = 8f;
        data.capHeight = 12f;
        data.down = -16f;
        for (char ch = 32; ch < 127; ch++) {
            BitmapFont.Glyph glyph = new BitmapFont.Glyph();
            glyph.id = ch;
            glyph.xadvance = 8;
            glyph.width = ch == ' ' ? 0 : 8;
            glyph.height = 12;
            data.setGlyph(ch, glyph);
        }
        CodeEditor.CodeEditorStyle style = new CodeEditor.CodeEditorStyle();
        style.font = new BitmapFont(data, new TextureRegion(pixel), false);
        style.whitePixelTexture = pixel;
        editor = new CodeEditor(style);
        editor.setOnscreenKeyboard(visible -> { });
        editor.setInteractionMode(com.lzt841.editor.input.CodeEditorInteractionMode.MOUSE);
        editor.setHighlighter(BuiltinCodeHighlighters.plainText());
        editor.setClipAreaEnabled(false);
        editor.setScrollbarsVisible(false);
        editor.setOverscrollEnabled(false);
        editor.setSize(640f, 480f);
        editor.setPosition(10f, 20f);
        recording = new RecordingBatch();
    }

    @After public void restoreGl() {
        if (pixel != null) pixel.dispose();
        Gdx.gl = previousGl;
        Gdx.gl20 = previousGl20;
        Gdx.graphics = previousGraphics;
    }

    private void draw(String text) {
        editor.setText(text);
        draw();
    }

    private void draw() {
        recording.rects.clear();
        recording.images.clear();
        recording.glyphs.clear();
        recording.events.clear();
        rows.clear();
        editor.draw(recording.batch, 1f);
    }

    private void captureRows() {
        editor.addRowRenderer(new CodeEditorRowRenderer() {
            @Override public void drawForeground(Batch batch, CodeEditorRowRenderContext row) { rows.add(new Row(row)); }
        });
    }

    private Array<Rect> marks(Color color, boolean stroke) {
        Array<Rect> result = new Array<>();
        for (Rect rect : recording.rects) {
            if (rect.stroke == stroke && rect.color.toIntBits() == color.toIntBits()) result.add(rect);
        }
        return result;
    }

    @Test public void effectsDefaultOffAndTogglingPreservesDocumentAndHistory() {
        draw("a b\t \n");
        assertEquals(0, recording.rects.size);
        int version = editor.getDocumentVersion();
        editor.setWhitespaceMode(CodeWhitespaceMode.ALL);
        editor.setLineEndingsVisible(true);
        editor.setTrailingWhitespaceHighlighted(true);
        editor.setColumnRulers(80, 120);
        draw();
        assertEquals(version, editor.getDocumentVersion());
        assertEquals("a b\t \n", editor.getText());
        assertFalse(editor.canUndo());
        assertTrue(recording.rects.size > 0);
    }

    @Test public void spacesAndTabsUseMeasuredCellsAndDoNotNeedArrowGlyphs() {
        captureRows();
        editor.setWhitespaceMode(CodeWhitespaceMode.ALL);
        draw("a b\tc  \n\t \n");
        Array<Rect> dots = marks(editor.getStyle().whitespaceColor, false);
        assertEquals(4, dots.size);
        assertEquals(rows.get(0).xs[1] + 3f, dots.get(0).x, 0.001f);
        assertEquals(rows.get(0).bottom + editor.getLineHeight() * 0.5f - 1f, dots.get(0).y, 0.001f);
        Array<Rect> strokes = marks(editor.getStyle().whitespaceColor, true);
        assertEquals(6, strokes.size);
        // Tab after three cells advances 8px; a tab at the next line's start advances 32px.
        assertEquals(8f * 0.6f, strokes.get(0).width, 0.001f);
        assertEquals(26f, strokes.get(3).width, 0.001f);
    }

    @Test public void trailingModeIncludesWhitespaceOnlyLinesButExcludesInternalSpaces() {
        editor.setWhitespaceMode(CodeWhitespaceMode.TRAILING);
        draw("a b\tc  \n\t \n");
        assertEquals(3, marks(editor.getStyle().whitespaceColor, false).size);
        assertEquals(3, marks(editor.getStyle().whitespaceColor, true).size);
    }

    @Test public void selectionModeNormalizesReverseMultilineSelection() {
        editor.setText("a b c\n  x  \n");
        editor.setWhitespaceMode(CodeWhitespaceMode.SELECTION);
        draw();
        assertEquals(0, recording.rects.size);
        editor.setSelection(1, 2, 0, 2);
        draw();
        assertEquals(3, marks(editor.getStyle().whitespaceColor, false).size);
        editor.setSelection(0, 0, 1, 0);
        draw();
        assertEquals(2, marks(editor.getStyle().whitespaceColor, false).size);
    }

    @Test public void lineBreaksAreIndependentOfMarkerModeAndExcludeSoftWrapsAndEof() {
        captureRows();
        editor.setLineEndingsVisible(true);
        editor.setWrapEnabled(true);
        editor.setSize(165f, 480f);
        draw("word word word word word\n\nlast word word word");
        assertTrue(rows.size > 3);
        assertEquals(8, marks(editor.getStyle().whitespaceColor, true).size);
        assertEquals(0, marks(editor.getStyle().whitespaceColor, false).size);
    }

    @Test public void lineBreakBeyondViewportIsCulledAndReturnsWhenVisible() {
        captureRows();
        editor.setLineEndingsVisible(true);
        editor.setWrapEnabled(true);
        editor.setSize(150f, 480f);
        draw("abc def ghi                         \nlast");
        // Existing wrapping keeps this run of trailing spaces on its final row, beyond the clip.
        assertTrue(rows.get(rows.size - 2).xs[rows.get(rows.size - 2).xs.length - 1] > editor.getRight());
        assertEquals(0, marks(editor.getStyle().whitespaceColor, true).size);
        editor.setWidth(500f);
        draw();
        assertEquals(4, marks(editor.getStyle().whitespaceColor, true).size);
    }

    @Test public void trailingTintIsIndependentAndMeasuresTabsAsTheirActualAdvance() {
        captureRows();
        editor.setTrailingWhitespaceHighlighted(true);
        draw("ab \t\ntext");
        Array<Rect> tints = marks(editor.getStyle().trailingWhitespaceColor, false);
        assertEquals(1, tints.size);
        assertEquals(rows.get(0).xs[2], tints.first().x, 0.001f);
        assertEquals(16f, tints.first().width, 0.001f);
        assertEquals(0, marks(editor.getStyle().whitespaceColor, false).size);
    }

    @Test public void wrappedRowsUseContinuationIndentAndLogicalTrailingClassification() {
        editor.setWrapEnabled(true);
        editor.setWrapContinuationIndentEnabled(true);
        editor.setWrapContinuationIndentColumns(1);
        editor.setWhitespaceMode(CodeWhitespaceMode.ALL);
        editor.setTrailingWhitespaceHighlighted(true);
        editor.setSize(210f, 480f);
        captureRows();
        draw("  alpha beta gamma delta z ");
        assertTrue(rows.size > 1);
        assertTrue(rows.get(1).x > rows.first().x);
        for (Rect dot : marks(editor.getStyle().whitespaceColor, false)) {
            boolean matched = false;
            for (Row row : rows) {
                if (Math.abs(dot.y - (row.bottom + row.height * 0.5f - 1f)) > 0.001f) continue;
                for (int column = row.start; column < row.end; column++) {
                    if (row.text.charAt(column) == ' ' && Math.abs(dot.x - (row.xs[column - row.start] + 3f)) < 0.001f) matched = true;
                }
            }
            assertTrue("dot follows the measured wrapped row", matched);
        }
        for (Rect tint : marks(editor.getStyle().trailingWhitespaceColor, false)) {
            assertEquals(rows.peek().bottom + 2f, tint.y, 0.001f);
        }
    }

    @Test public void rulersUseSpaceAdvancesAndFollowHorizontalScrollAndZoom() {
        captureRows();
        editor.setColumnRulers(4, 8);
        draw("a very long line to make horizontal scrolling available");
        Array<Rect> rulers = marks(editor.getStyle().columnRulerColor, false);
        assertEquals(2, rulers.size);
        assertEquals(rows.first().x + 32f, rulers.first().x, 0.001f);
        editor.setSize(180f, 200f);
        editor.setScroll(16f, 0f);
        draw();
        rulers = marks(editor.getStyle().columnRulerColor, false);
        assertEquals(rows.first().x + 32f, rulers.first().x, 0.001f);
        editor.setZoomScale(2f);
        editor.setScroll(0f, 0f);
        draw();
        rulers = marks(editor.getStyle().columnRulerColor, false);
        assertEquals(rows.first().x + 64f, rulers.first().x, 0.001f);
        assertEquals(2f, rulers.first().width, 0.001f);
    }

    @Test public void offscreenRulersAndWhitespaceAreCulledOnLongLines() {
        editor.setColumnRulers(1, 80, 120);
        editor.setWhitespaceMode(CodeWhitespaceMode.ALL);
        editor.setSize(180f, 200f);
        StringBuilder line = new StringBuilder();
        for (int i = 0; i < 12000; i++) line.append(' ');
        editor.setText(line.toString());
        editor.setScroll(40000f, 0f);
        draw();
        assertTrue(editor.getScrollX() > 39000f);
        assertEquals(0, marks(editor.getStyle().columnRulerColor, false).size);
        int dots = marks(editor.getStyle().whitespaceColor, false).size;
        assertTrue(dots > 0 && dots < 30);
    }

    @Test public void foldSuffixGetsMeasuredCallbacksMarkersAndItsOwnDiagnostics() {
        captureRows();
        editor.setWhitespaceMode(CodeWhitespaceMode.ALL);
        editor.setLineEndingsVisible(true);
        editor.setText("{\n  hidden\n} tail here\nnext");
        editor.setDiagnostics(Array.with(new CodeDiagnostic(2, 2, 2, 6, CodeDiagnosticSeverity.WARNING, "suffix warning")));
        assertTrue(editor.toggleFoldAt(0));
        draw();
        assertEquals(3, rows.size);
        assertEquals(0, rows.get(0).line);
        Row suffix = rows.get(1);
        assertTrue(suffix.suffix);
        assertEquals(2, suffix.line);
        assertEquals(rows.get(0).visualRow, suffix.visualRow);
        assertEquals(rows.get(0).bottom, suffix.bottom, 0f);
        assertEquals(2, marks(editor.getStyle().whitespaceColor, false).size);
        assertEquals(suffix.xs[1] + 3f, marks(editor.getStyle().whitespaceColor, false).first().x, 0.001f);
        assertEquals(4, marks(editor.getStyle().whitespaceColor, true).size);
        Array<Rect> squiggle = marks(editor.getStyle().diagnosticWarningColor, true);
        assertTrue(squiggle.size > 0);
        assertEquals(suffix.xs[2], squiggle.first().x, 0.001f);
    }

    @Test public void truncatedFoldSuffixDoesNotPretendToEndTheLogicalLine() {
        editor.setLineEndingsVisible(true);
        editor.setText("{\n hidden\n} " + repeat('a', 100) + "\nlast");
        assertTrue(editor.toggleFoldAt(0));
        draw();
        assertEquals(0, marks(editor.getStyle().whitespaceColor, true).size);
    }

    @Test public void passwordModeSuppressesTextDecorationsAndHostCallbacks() {
        captureRows();
        editor.setWhitespaceMode(CodeWhitespaceMode.ALL);
        editor.setTrailingWhitespaceHighlighted(true);
        editor.setLineEndingsVisible(true);
        editor.setPasswordMode(true);
        draw("secret \t\nnext");
        assertEquals(0, recording.rects.size);
        assertEquals(0, rows.size);
        editor.setPasswordMode(false);
        draw();
        assertTrue(recording.rects.size > 0);
        assertTrue(rows.size > 0);
    }

    @Test public void imageWidthsAndSurrogateBoundariesAreAvailableToCustomLayers() {
        InlineImage image = new InlineImage(new TextureRegion(pixel), 20f, 12f, 0f);
        editor.setInlineImageProvider((line, column, text) -> text.charAt(column) == '\uD83D' ? image : null);
        editor.addRowRenderer(new CodeEditorRowRenderer() {
            @Override public void drawForeground(Batch batch, CodeEditorRowRenderContext row) {
                assertEquals(20f, row.getColumnX(3) - row.getColumnX(1), 0.001f);
                assertEquals(row.getColumnX(3), row.getColumnX(2), 0f);
                assertEquals(row.getTextX(), row.getColumnX(-10), 0f);
                assertEquals(row.getTextEndX(), row.getColumnX(999), 0f);
            }
        });
        draw("a\uD83D\uDE00 b");
    }

    @Test public void spacesReplacedByInlineImagesDoNotReceiveDots() {
        InlineImage image = new InlineImage(new TextureRegion(pixel), 20f, 12f, 0f);
        editor.setInlineImageProvider((line, column, text) -> text.charAt(column) == ' ' ? image : null);
        editor.setWhitespaceMode(CodeWhitespaceMode.ALL);
        draw("a b");
        assertEquals(0, marks(editor.getStyle().whitespaceColor, false).size);
    }

    @Test public void customPassesSurroundSelectionAndTextAndKeepRegistrationOrder() {
        editor.setText("text");
        editor.setSelection(0, 0, 0, 2);
        editor.getStyle().selection = new BaseDrawable() {
            @Override public void draw(Batch batch, float x, float y, float width, float height) { recording.events.add("selection"); }
        };
        editor.addRowRenderer(eventRenderer("one"));
        editor.addRowRenderer(eventRenderer("two"));
        draw();
        assertEquals("one-bg", recording.events.get(0));
        assertEquals("two-bg", recording.events.get(1));
        assertEquals("selection", recording.events.get(2));
        assertTrue(recording.events.indexOf("text", false) > 2);
        int firstForeground = recording.events.indexOf("one-fg", false);
        assertTrue(firstForeground > recording.events.indexOf("text", false));
        assertEquals("two-fg", recording.events.get(firstForeground + 1));
    }

    @Test public void rendererTintFontAndScaleChangesDoNotLeakToNextLayer() {
        editor.addRowRenderer(new CodeEditorRowRenderer() {
            @Override public void drawBackground(Batch batch, CodeEditorRowRenderContext row) {
                batch.setColor(Color.RED);
                editor.getStyle().font.setColor(Color.GREEN);
                editor.getStyle().font.getData().setScale(5f);
            }
        });
        editor.addRowRenderer(new CodeEditorRowRenderer() {
            @Override public void drawBackground(Batch batch, CodeEditorRowRenderContext row) {
                assertEquals(Color.WHITE, batch.getColor());
                assertEquals(1f, editor.getStyle().font.getData().scaleX, 0f);
                assertNotEquals(Color.GREEN, editor.getStyle().font.getColor());
            }
        });
        draw("text\nnext");
    }

    @Test public void throwingRendererRestoresStateAndDoesNotLeaveSnapshotOpen() {
        CodeEditorRowRenderer bad = new CodeEditorRowRenderer() {
            @Override public void drawBackground(Batch batch, CodeEditorRowRenderContext row) {
                batch.setColor(Color.RED);
                editor.getStyle().font.setColor(Color.GREEN);
                editor.getStyle().font.getData().setScale(5f);
                throw new IllegalStateException("expected");
            }
        };
        editor.addRowRenderer(bad);
        try { draw("text"); fail(); }
        catch (IllegalStateException expected) { assertEquals("expected", expected.getMessage()); }
        assertEquals(Color.WHITE, recording.color);
        assertEquals(1f, editor.getStyle().font.getData().scaleX, 0f);
        assertNotEquals(Color.GREEN, editor.getStyle().font.getColor());
        assertTrue(editor.removeRowRenderer(bad));
        captureRows();
        draw();
        assertEquals(1, rows.size);
    }

    @Test public void registrationUsesIdentityCopiesAndSafeCallbackSnapshots() {
        CodeEditorRowRenderer late = eventRenderer("late");
        CodeEditorRowRenderer first = new CodeEditorRowRenderer() {
            @Override public void drawBackground(Batch batch, CodeEditorRowRenderContext row) {
                recording.events.add("first");
                editor.removeRowRenderer(this);
                editor.addRowRenderer(late);
            }
        };
        editor.addRowRenderer(first);
        editor.addRowRenderer(first);
        editor.addRowRenderer(null);
        Array<CodeEditorRowRenderer> copy = editor.getRowRenderers();
        copy.clear();
        assertEquals(1, editor.getRowRenderers().size);
        draw("text");
        assertEquals("first", recording.events.first());
        assertFalse(recording.events.contains("late-bg", false));
        assertTrue(recording.events.contains("late-fg", false));
        draw();
        assertEquals("late-bg", recording.events.first());
    }

    @Test public void callbacksCoverEmptyRowsAndOnlyVerticallyVisibleLines() {
        captureRows();
        draw("\n");
        assertEquals(2, rows.size);
        assertEquals(0, rows.first().start);
        assertEquals(0, rows.first().end);
        StringBuilder text = new StringBuilder();
        for (int i = 0; i < 10000; i++) text.append("line\n");
        editor.setText(text.toString());
        editor.setSize(200f, 70f);
        editor.setScroll(0f, editor.getLineHeight() * 5000f);
        draw();
        assertTrue(rows.size > 0 && rows.size < 6);
        assertEquals(5000, rows.first().line);
    }

    @Test public void sharedSettingsNormalizeAndDefensivelyCopyRulers() {
        CodeEditor other = EditorTestSupport.createEditor("a b");
        other.setSettings(editor.getSettings());
        int[] columns = {120, -2, 80, 120, 0, 40};
        editor.setColumnRulers(columns);
        columns[0] = 1;
        assertArrayEquals(new int[]{40, 80, 120}, other.getColumnRulers());
        int[] copy = editor.getSettings().getColumnRulers();
        copy[0] = 999;
        copy = editor.getColumnRulers();
        copy[0] = 777;
        assertArrayEquals(new int[]{40, 80, 120}, editor.getColumnRulers());
        other.setWhitespaceMode(CodeWhitespaceMode.SELECTION);
        other.setLineEndingsVisible(true);
        other.setTrailingWhitespaceHighlighted(true);
        assertEquals(CodeWhitespaceMode.SELECTION, editor.getWhitespaceMode());
        assertTrue(editor.isLineEndingsVisible());
        assertTrue(editor.isTrailingWhitespaceHighlighted());
        editor.setWhitespaceMode(null);
        editor.setColumnRulers((int[]) null);
        assertEquals(CodeWhitespaceMode.NONE, other.getWhitespaceMode());
        assertArrayEquals(new int[0], other.getColumnRulers());
    }

    @Test public void styleCopiesNewColorsByValueAndSupportsNullColors() {
        CodeEditor.CodeEditorStyle source = editor.getStyle();
        source.whitespaceDotSize = 3f;
        source.whitespaceMarkerThickness = 2f;
        source.columnRulerWidth = 4f;
        CodeEditor.CodeEditorStyle copy = new CodeEditor.CodeEditorStyle(source);
        copy.whitespaceColor.set(Color.RED);
        copy.trailingWhitespaceColor.set(Color.BLUE);
        copy.columnRulerColor.set(Color.GREEN);
        assertNotEquals(source.whitespaceColor, copy.whitespaceColor);
        assertNotEquals(source.trailingWhitespaceColor, copy.trailingWhitespaceColor);
        assertNotEquals(source.columnRulerColor, copy.columnRulerColor);
        assertEquals(3f, copy.whitespaceDotSize, 0f);
        assertEquals(2f, copy.whitespaceMarkerThickness, 0f);
        assertEquals(4f, copy.columnRulerWidth, 0f);
        source.whitespaceColor = source.trailingWhitespaceColor = source.columnRulerColor = null;
        copy = new CodeEditor.CodeEditorStyle(source);
        assertNull(copy.whitespaceColor);
        assertNull(copy.trailingWhitespaceColor);
        assertNull(copy.columnRulerColor);
        editor.setWhitespaceMode(CodeWhitespaceMode.ALL);
        editor.setTrailingWhitespaceHighlighted(true);
        editor.setColumnRulers(4);
        draw("a  ");
        assertEquals(0, recording.rects.size);
    }

    @Test public void themesDeriveVisibleMarkerColorsForDarkAndLightBackgrounds() {
        CodeEditor.CodeEditorStyle dark = CodeEditor.CodeEditorStyle.theme(editor.getStyle().font)
            .whitePixelTexture(pixel).backgroundColor(Color.BLACK).textColor(Color.WHITE).build();
        CodeEditor.CodeEditorStyle light = CodeEditor.CodeEditorStyle.theme(editor.getStyle().font)
            .whitePixelTexture(pixel).backgroundColor(Color.WHITE).textColor(Color.BLACK).build();
        assertTrue(dark.whitespaceColor.r > light.whitespaceColor.r);
        assertTrue(dark.whitespaceColor.a > 0f && dark.whitespaceColor.a < 1f);
        assertTrue(light.columnRulerColor.a > 0f && light.columnRulerColor.a < 1f);
        assertTrue(dark.whitespaceDotSize > 0f && light.whitespaceMarkerThickness > 0f);
    }

    @Test public void missingWhitePixelTextureGracefullySkipsBuiltInEffects() {
        editor.getStyle().whitePixelTexture = null;
        editor.setWhitespaceMode(CodeWhitespaceMode.ALL);
        editor.setLineEndingsVisible(true);
        editor.setTrailingWhitespaceHighlighted(true);
        editor.setColumnRulers(4);
        captureRows();
        draw("a  \n");
        assertEquals(0, recording.rects.size);
        assertEquals(2, rows.size);
    }

    @Test public void renderingSettingsReuseHighlightCaches() {
        int[] calls = {0};
        editor.setHighlighter((lines, style) -> {
            calls[0]++;
            Array<Array<com.lzt841.editor.highlight.CodeHighlightSpan>> result = new Array<>();
            for (int i = 0; i < lines.size; i++) result.add(new Array<>());
            return result;
        });
        draw("a b\nnext ");
        int before = calls[0];
        editor.setWhitespaceMode(CodeWhitespaceMode.ALL);
        editor.setLineEndingsVisible(true);
        editor.setTrailingWhitespaceHighlighted(true);
        editor.setColumnRulers(4, 8);
        draw();
        assertEquals(before, calls[0]);
        assertTrue(before > 0);
    }

    @Test public void markerSizeAndPositionFollowZoomWithoutLeakingBatchTint() {
        captureRows();
        editor.setWhitespaceMode(CodeWhitespaceMode.ALL);
        editor.setZoomScale(2f);
        draw("a b");
        Rect dot = marks(editor.getStyle().whitespaceColor, false).first();
        assertEquals(4f, dot.width, 0.001f);
        assertEquals(rows.first().xs[1] + 6f, dot.x, 0.001f);
        assertEquals(Color.WHITE, recording.color);
    }

    @Test public void readOnlyAndDisabledEditorsRetainRenderingAids() {
        captureRows();
        editor.setReadOnly(true);
        editor.setDisabled(true);
        editor.setWhitespaceMode(CodeWhitespaceMode.ALL);
        editor.setTrailingWhitespaceHighlighted(true);
        draw("a b  ");
        assertEquals(3, marks(editor.getStyle().whitespaceColor, false).size);
        assertEquals(1, marks(editor.getStyle().trailingWhitespaceColor, false).size);
        assertEquals(1, rows.size);
    }

    @Test public void normalizedEqualRulersDoNotEmitAnotherSharedSettingsUpdate() {
        CodeEditorSettings settings = editor.getSettings();
        int[] updates = {0};
        settings.addListener(changed -> updates[0]++);
        settings.setColumnRulers(80, 40, 80, -1);
        assertEquals(1, updates[0]);
        settings.setColumnRulers(40, 80, 0);
        assertEquals(1, updates[0]);
        assertArrayEquals(new int[]{40, 80}, settings.getColumnRulers());
    }

    @Test public void diagnosticSquigglesPreserveBatchTintForCustomForegroundAndSiblingActors() {
        editor.setText("text");
        editor.setDiagnostics(Array.with(new CodeDiagnostic(0, 0, 0, 4, CodeDiagnosticSeverity.ERROR, "test")));
        recording.batch.setColor(0.25f, 0.5f, 0.75f, 1f);
        float original = recording.batch.getPackedColor();
        editor.addRowRenderer(new CodeEditorRowRenderer() {
            @Override public void drawForeground(Batch batch, CodeEditorRowRenderContext row) {
                assertEquals(original, batch.getPackedColor(), 0f);
            }
        });
        draw();
        assertTrue(marks(editor.getStyle().diagnosticErrorColor, true).size > 0);
        assertEquals(original, recording.batch.getPackedColor(), 0f);
    }

    @Test public void richTextDecorationKindsRestoreTheOriginalPackedTint() {
        int[] flags = {CodeTextStyle.BACKGROUND, CodeTextStyle.UNDERLINE, CodeTextStyle.UNDERLINE_DOUBLE,
            CodeTextStyle.STRIKETHROUGH, CodeTextStyle.UNDERLINE_WAVY, CodeTextStyle.UNDERLINE_DASHED,
            CodeTextStyle.UNDERLINE_DOTTED};
        for (int flag : flags) {
            editor.setHighlighter((lines, style) -> Array.with(Array.with(new CodeHighlightSpan(0, 4,
                Color.WHITE, new CodeTextStyle(flag, Color.RED, Color.BLUE, null)))));
            recording.batch.setColor(0.25f, 0.5f, 0.75f, 1f);
            float original = recording.batch.getPackedColor();
            draw("text");
            assertTrue("decoration rendered for flag " + flag, recording.rects.size > 0);
            assertEquals("tint preserved for flag " + flag, original, recording.batch.getPackedColor(), 0f);
        }
    }

    @Test public void inlineImagesUseWhiteTintThenRestoreThePreviousTint() {
        InlineImage image = new InlineImage(new TextureRegion(pixel), 20f, 12f, 0f);
        editor.setInlineImageProvider((line, column, text) -> text.charAt(column) == '*' ? image : null);
        recording.batch.setColor(0.25f, 0.5f, 0.75f, 1f);
        float original = recording.batch.getPackedColor();
        draw("a*b");
        assertEquals(Color.WHITE.toFloatBits(), recording.imageTint, 0f);
        assertEquals(original, recording.batch.getPackedColor(), 0f);
    }

    @Test public void inlineImageDimensionsAndSurrogateHitAreaFollowZoom() {
        InlineImage image = new InlineImage(new TextureRegion(pixel), 20f, 12f, 1f);
        editor.setInlineImageProvider((line, column, text) -> text.charAt(column) == '\uD83D' ? image : null);
        captureRows();
        for (float zoom : new float[]{1f, 2f, 0.5f}) {
            editor.setZoomScale(zoom);
            draw("a\uD83D\uDE00b");
            Row row = rows.first();
            Rect actual = recording.images.first();
            assertEquals(20f * zoom, row.xs[3] - row.xs[1], 0.001f);
            assertEquals(20f * zoom, actual.width, 0.001f);
            assertEquals(12f * zoom, actual.height, 0.001f);
            float localY = row.bottom - editor.getY() + row.height / 2f;
            InlineImageHit hit = editor.getInlineImageAt(actual.x - editor.getX() + actual.width * 0.9f, localY);
            assertNotNull(hit);
            assertEquals(1, hit.column);
            assertNull(editor.getInlineImageAt(row.xs[3] - editor.getX() + 1f, localY));
            assertEquals(3, editor.getPositionAtLocal(actual.x - editor.getX() + actual.width * 0.9f, localY, false).column);
        }
    }

    @Test public void oversizedInlineImageStaysCenteredInItsMeasuredZoomedColumn() {
        InlineImage image = new InlineImage(new TextureRegion(pixel), 40f, 80f, 100f);
        editor.setInlineImageProvider((line, column, text) -> text.charAt(column) == '*' ? image : null);
        editor.setZoomScale(2f);
        captureRows();
        draw("a*b");
        Row row = rows.first();
        Rect actual = recording.images.first();
        assertEquals(80f, row.xs[2] - row.xs[1], 0.001f);
        assertEquals(row.height, actual.height, 0.001f);
        assertEquals(actual.height / 2f, actual.width, 0.001f);
        assertEquals((row.xs[1] + row.xs[2] - actual.width) / 2f, actual.x, 0.001f);
        assertEquals(row.bottom, actual.y, 0.001f);
    }

    @Test public void imageZoomChangesWrappingAndKeepsFollowingTextAligned() {
        InlineImage image = new InlineImage(new TextureRegion(pixel), 40f, 12f, 0f);
        editor.setInlineImageProvider((line, column, text) -> text.charAt(column) == '*' ? image : null);
        editor.setWrapEnabled(true);
        editor.setSize(180f, 480f);
        captureRows();
        draw("*ab*ab*ab*ab");
        int before = rows.size;
        editor.setZoomScale(2f);
        draw();
        assertTrue(rows.size > before);
        for (Row row : rows) {
            for (int column = row.start; column < row.end; column++) {
                if (row.text.charAt(column) == '*') {
                    assertEquals(80f, row.xs[column - row.start + 1] - row.xs[column - row.start], 0.001f);
                }
            }
        }
    }

    @Test public void preScaledFontUsesItsActualHeightAndRestoresBothScales() {
        BitmapFont font = editor.getStyle().font;
        font.getData().setScale(1.5f, 2f);
        editor.setStyle(editor.getStyle());
        assertEquals(32f + editor.getStyle().rowPadding, editor.getLineHeight(), 0.001f);
        editor.setZoomScale(0.5f);
        assertEquals(16f + editor.getStyle().rowPadding, editor.getLineHeight(), 0.001f);
        draw("one\ntwo");
        assertEquals(1.5f, font.getData().scaleX, 0f);
        assertEquals(2f, font.getData().scaleY, 0f);
    }

    @Test public void missingGlyphUsesTheFontsReplacementAdvanceAndKerning() {
        BitmapFont font = editor.getStyle().font;
        BitmapFont.Glyph replacement = new BitmapFont.Glyph();
        replacement.xadvance = 13;
        replacement.width = 13;
        replacement.height = 12;
        replacement.setKerning('b', -2);
        font.getData().missingGlyph = replacement;
        font.getData().setScale(1.5f, 2f);
        editor.setStyle(editor.getStyle());
        editor.setZoomScale(2f);
        captureRows();
        draw("a\u2603b");
        Row row = rows.first();
        assertEquals(33f, row.xs[2] - row.xs[1], 0.001f);
        GlyphDraw following = glyphAt(row.xs[2], row.bottom, row.height);
        assertNotNull("the glyph after the replacement matches the measured caret column", following);
    }

    @Test public void unsupportedCharactersReserveStableSpaceWhenTheFontHasNoReplacement() {
        BitmapFont font = editor.getStyle().font;
        font.getData().setScale(1.5f, 2f);
        editor.setStyle(editor.getStyle());
        editor.setZoomScale(2f);
        captureRows();
        draw("a\u2603b");
        Row row = rows.first();
        assertEquals(24f, row.xs[2] - row.xs[1], 0.001f);
        assertNotNull("skipped font glyph must not pull the next character left", glyphAt(row.xs[2], row.bottom, row.height));
        float advance = row.xs[2] - row.xs[1];
        font.getData().setScale(3f, 4f);
        editor.setHighlighter(BuiltinCodeHighlighters.plainText());
        draw();
        assertEquals(advance, rows.first().xs[2] - rows.first().xs[1], 0.001f);
    }

    @Test public void unsupportedSpacesKeepTabsAndRulersAtTheSameZoomedStops() {
        BitmapFont.BitmapFontData data = editor.getStyle().font.getData();
        data.setGlyph(' ', null);
        data.setScale(1.5f);
        editor.setStyle(editor.getStyle());
        editor.setZoomScale(2f);
        editor.setColumnRulers(4);
        captureRows();
        draw(" \ta");
        Row row = rows.first();
        assertEquals(24f, row.xs[1] - row.xs[0], 0.001f);
        assertEquals(row.xs[2], marks(editor.getStyle().columnRulerColor, false).first().x, 0.001f);
    }

    @Test public void gradientContinuesAcrossTabAndImageGaps() {
        gradientStyle(CodeTextStyle.GRADIENT);
        InlineImage image = new InlineImage(new TextureRegion(pixel), 24f, 12f, 0f);
        editor.setInlineImageProvider((line, column, text) -> text.charAt(column) == '*' ? image : null);
        captureRows();
        draw("ab\tcd*ef");
        Row row = rows.first();
        assertTrue(glyphAt(row.xs[0], row.bottom, row.height).color.b < 0.2f);
        assertTrue(glyphAt(row.xs[6], row.bottom, row.height).color.b > 0.8f);
        assertGradientSamples(row);
    }

    @Test public void gradientContinuesAcrossWrappedRowsAndBoldPassesKeepTheirColors() {
        gradientStyle(CodeTextStyle.GRADIENT | CodeTextStyle.BOLD | CodeTextStyle.ITALIC);
        editor.getStyle().fakeBoldOffset = 2f;
        editor.setWrapEnabled(true);
        editor.setSize(180f, 480f);
        captureRows();
        draw("abcdefghijklmnopqrstuvwxyz");
        assertTrue(rows.size > 1);
        for (Row row : rows) assertGradientSamples(row);
        assertTrue(recording.glyphs.size >= 26 * 3);
    }

    private void gradientStyle(int flags) {
        editor.getStyle().gradientBandWidth = 1f;
        editor.setHighlighter((lines, style) -> {
            Array<Array<CodeHighlightSpan>> result = new Array<>();
            for (String line : lines) result.add(Array.with(new CodeHighlightSpan(0, line.length(), Color.RED,
                new CodeTextStyle(flags, null, null, Color.BLUE))));
            return result;
        });
    }

    private void assertGradientSamples(Row row) {
        float total = 0f;
        for (Row visible : rows) total += visible.xs[visible.xs.length - 1] - visible.xs[0];
        float before = 0f;
        for (Row visible : rows) {
            if (visible == row) break;
            before += visible.xs[visible.xs.length - 1] - visible.xs[0];
        }
        for (int column = row.start; column < row.end; column++) {
            char ch = row.text.charAt(column);
            if (ch == '\t' || ch == '*') continue;
            int offset = column - row.start;
            GlyphDraw glyph = glyphAt(row.xs[offset], row.bottom, row.height);
            assertNotNull(glyph);
            float sample = (before + (row.xs[offset] + row.xs[offset + 1]) / 2f - row.x) / total;
            assertEquals("gradient uses the full token width at column " + column, sample, glyph.color.b, 0.008f);
        }
    }

    private GlyphDraw glyphAt(float x, float bottom, float height) {
        for (GlyphDraw glyph : recording.glyphs) {
            if (Math.abs(glyph.x - x) < 0.01f && glyph.y >= bottom && glyph.y < bottom + height) return glyph;
        }
        return null;
    }

    private CodeEditorRowRenderer eventRenderer(final String name) {
        return new CodeEditorRowRenderer() {
            @Override public void drawBackground(Batch batch, CodeEditorRowRenderContext row) { recording.events.add(name + "-bg"); }
            @Override public void drawForeground(Batch batch, CodeEditorRowRenderContext row) { recording.events.add(name + "-fg"); }
        };
    }

    private static String repeat(char ch, int count) {
        char[] chars = new char[count];
        java.util.Arrays.fill(chars, ch);
        return new String(chars);
    }

    private static final class Row {
        final int line, visualRow, start, end;
        final boolean suffix;
        final String text;
        final float x, bottom, height;
        final float[] xs;
        Row(CodeEditorRowRenderContext row) {
            line = row.getLine(); visualRow = row.getVisualRow(); start = row.getStartColumn(); end = row.getEndColumn();
            suffix = row.isFoldSuffix(); text = row.getLineText();
            x = row.getTextX(); bottom = row.getRowBottom(); height = row.getLineHeight();
            xs = new float[end - start + 1];
            for (int i = start; i <= end; i++) xs[i - start] = row.getColumnX(i);
        }
    }

    private static final class Rect {
        final Color color;
        final float x, y, width, height;
        final boolean stroke;
        Rect(Color color, float x, float y, float width, float height, boolean stroke) {
            this.color = new Color(color); this.x = x; this.y = y; this.width = width; this.height = height; this.stroke = stroke;
        }
    }

    private static final class RecordingBatch {
        final Color color = new Color(Color.WHITE);
        final Array<Rect> rects = new Array<>();
        final Array<Rect> images = new Array<>();
        final Array<GlyphDraw> glyphs = new Array<>();
        final Array<String> events = new Array<>();
        final Matrix4 matrix = new Matrix4();
        float imageTint;
        final Batch batch = (Batch) Proxy.newProxyInstance(Batch.class.getClassLoader(), new Class<?>[]{Batch.class}, (proxy, method, args) -> {
            String name = method.getName();
            if (name.equals("getColor")) return color;
            if (name.equals("getPackedColor")) return color.toFloatBits();
            if (name.equals("setPackedColor")) { Color.abgr8888ToColor(color, (Float) args[0]); return null; }
            if (name.equals("setColor")) {
                if (args.length == 1) color.set((Color) args[0]);
                else color.set((Float) args[0], (Float) args[1], (Float) args[2], (Float) args[3]);
                return null;
            }
            if (name.equals("getTransformMatrix") || name.equals("getProjectionMatrix")) return matrix;
            if (name.equals("draw")) {
                if (args[1] instanceof float[]) {
                    events.add("text");
                    float[] vertices = (float[]) args[1];
                    int end = (Integer) args[2] + (Integer) args[3];
                    for (int at = (Integer) args[2]; at < end; at += 20) glyphs.add(new GlyphDraw(vertices, at));
                } else if (args.length == 5 && args[0] instanceof TextureRegion) {
                    imageTint = color.toFloatBits();
                    images.add(new Rect(color, (Float) args[1], (Float) args[2], (Float) args[3], (Float) args[4], false));
                }
                else if (args.length == 5 && args[0] instanceof Texture) {
                    rects.add(new Rect(color, (Float) args[1], (Float) args[2], (Float) args[3], (Float) args[4], false));
                } else if (args.length == 10 && args[0] instanceof TextureRegion) {
                    rects.add(new Rect(color, (Float) args[1], (Float) args[2], (Float) args[5], (Float) args[6], true));
                }
            }
            return defaultValue(method.getReturnType());
        });
    }

    private static final class GlyphDraw {
        final float x, y;
        final Color color = new Color();
        GlyphDraw(float[] vertices, int at) {
            x = vertices[at]; y = vertices[at + 1];
            Color.abgr8888ToColor(color, vertices[at + 2]);
        }
    }

    private static Object defaultValue(Class<?> type) {
        if (type == boolean.class) return false;
        if (type == int.class) return 0;
        if (type == long.class) return 0L;
        if (type == float.class) return 0f;
        if (type == double.class) return 0d;
        return null;
    }

    private static final class PixelData implements TextureData {
        @Override public TextureDataType getType() { return TextureDataType.Custom; }
        @Override public boolean isPrepared() { return true; }
        @Override public void prepare() { }
        @Override public Pixmap consumePixmap() { throw new UnsupportedOperationException(); }
        @Override public boolean disposePixmap() { return false; }
        @Override public void consumeCustomData(int target) { }
        @Override public int getWidth() { return 1; }
        @Override public int getHeight() { return 1; }
        @Override public Pixmap.Format getFormat() { return Pixmap.Format.RGBA8888; }
        @Override public boolean useMipMaps() { return false; }
        @Override public boolean isManaged() { return false; }
    }
}
