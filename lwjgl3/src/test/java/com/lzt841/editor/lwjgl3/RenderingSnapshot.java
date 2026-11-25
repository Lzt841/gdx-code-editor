package com.lzt841.editor.lwjgl3;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.PixmapIO;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.freetype.FreeTypeFontGenerator;
import com.badlogic.gdx.graphics.glutils.FrameBuffer;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.utils.ScreenUtils;
import com.badlogic.gdx.utils.viewport.ScreenViewport;
import com.lzt841.editor.CodeDiagnostic;
import com.lzt841.editor.CodeDiagnosticSeverity;
import com.lzt841.editor.CodeEditor;
import com.lzt841.editor.render.CodeEditorRowRenderContext;
import com.lzt841.editor.render.CodeEditorRowRenderer;
import com.lzt841.editor.render.CodeWhitespaceMode;

/** Generates a real-OpenGL reference sheet without opening an interactive demo window. */
public final class RenderingSnapshot extends ApplicationAdapter {
    private static final int WIDTH = 1440, HEIGHT = 1000;
    private final String outputFolder;
    private Stage stage;
    private BitmapFont font;
    private Texture pixel;
    private FrameBuffer buffer;
    private boolean captured;

    private RenderingSnapshot(String outputFolder) { this.outputFolder = outputFolder; }

    public static void main(String[] args) {
        Lwjgl3ApplicationConfiguration config = new Lwjgl3ApplicationConfiguration();
        config.setTitle("Editor rendering snapshot");
        config.setWindowedMode(WIDTH, HEIGHT);
        config.setInitialVisible(false);
        config.setIdleFPS(30);
        config.disableAudio(true);
        new Lwjgl3Application(new RenderingSnapshot(args[0]), config);
    }

    @Override public void create() {
        font = createFont();
        font.setUseIntegerPositions(false);
        Pixmap onePixel = new Pixmap(1, 1, Pixmap.Format.RGBA8888);
        onePixel.setColor(Color.WHITE);
        onePixel.fill();
        pixel = new Texture(onePixel);
        onePixel.dispose();
        stage = new Stage(new ScreenViewport());
        stage.getViewport().update(WIDTH, HEIGHT, true);
        buffer = new FrameBuffer(Pixmap.Format.RGBA8888, WIDTH, HEIGHT, false);

        CodeEditor dark = panel(24, 525, false, "All whitespace / trailing tint / rulers at 40 and 60");
        dark.setText("// TODO: Custom underline and alternating row backgrounds\n"
            + "public void render() {\n"
            + "\tString message = \"Spaces and tabs\";   \n"
            + "\t// Tab arrows use their actual tab-stop width.\n"
            + "\tSystem.out.println(message);\n"
            + "\t  \n"
            + "} // return arrows mark real newlines\n");
        dark.addRowRenderer(reviewLayer());

        CodeEditor light = panel(744, 525, true, "Selection whitespace / light theme / real line breaks");
        light.setText("// Only selected spaces get dots in SELECTION mode.\n"
            + "public void render() {\n"
            + "\tString text = \"Select this text\";\n"
            + "\tSystem.out.println(text);\n"
            + "}\n");
        light.setWhitespaceMode(CodeWhitespaceMode.SELECTION);
        light.setSelection(1, 0, 3, 25);

        CodeEditor wrapped = panel(24, 45, false, "Soft wrapping / continuation indent / zoom 1.25x");
        wrapped.setWrapEnabled(true);
        wrapped.setWrapContinuationIndentEnabled(true);
        wrapped.setWrapContinuationIndentColumns(1);
        wrapped.setZoomScale(1.25f);
        wrapped.setText("public void wrap() {\n"
            + "\t// TODO: This long comment wraps under its own indentation, with markers following the same measured coordinates as the text and caret.\n"
            + "\tString sentence = \"A wrapped row has no return arrow; a real document newline has one.\";\n"
            + "}\n");
        wrapped.addRowRenderer(reviewLayer());

        CodeEditor folded = panel(744, 45, false, "Collapsed suffix / diagnostics / custom review overlays");
        folded.setText("// TODO: Folded closing text keeps its own decorations\n"
            + "public void folded() {\n"
            + "\tString hidden = \"not drawn\";\n"
            + "} // TODO: suffix remains visible\n"
            + "\n"
            + "// The renderer receives the suffix's original columns.\n"
            + "public int value = 42;   \n");
        folded.setDiagnostics(com.badlogic.gdx.utils.Array.with(new CodeDiagnostic(3, 5, 3, 9,
            CodeDiagnosticSeverity.WARNING, "Review this TODO")));
        folded.toggleFoldAt(1);
        folded.addRowRenderer(reviewLayer());
    }

    private CodeEditor panel(float x, float y, boolean light, String title) {
        Label label = new Label(title, new Label.LabelStyle(font, new Color(0.82f, 0.88f, 0.96f, 1f)));
        label.setPosition(x, y + 425f);
        stage.addActor(label);
        CodeEditor.CodeEditorStyle style = CodeEditor.CodeEditorStyle.theme(font)
            .whitePixelTexture(pixel)
            .backgroundColor(light ? new Color(0.96f, 0.97f, 0.99f, 1f) : new Color(0.055f, 0.078f, 0.109f, 1f))
            .gutterColor(light ? new Color(0.88f, 0.91f, 0.95f, 1f) : new Color(0.028f, 0.039f, 0.051f, 1f))
            .textColor(light ? new Color(0.13f, 0.18f, 0.26f, 1f) : new Color(0.93f, 0.96f, 0.99f, 1f))
            .gutterTextColor(light ? new Color(0.3f, 0.4f, 0.5f, 1f) : new Color(0.64f, 0.72f, 0.8f, 1f))
            .build();
        CodeEditor editor = new CodeEditor(style);
        editor.setBounds(x, y, 672f, 410f);
        editor.setWhitespaceMode(CodeWhitespaceMode.ALL);
        editor.setLineEndingsVisible(true);
        editor.setTrailingWhitespaceHighlighted(true);
        editor.setColumnRulers(40, 60);
        editor.setOverscrollEnabled(false);
        stage.addActor(editor);
        return editor;
    }

    private BitmapFont createFont() {
        String[] candidates = {"C:/Windows/Fonts/consola.ttf", "/System/Library/Fonts/Menlo.ttc",
            "/usr/share/fonts/truetype/dejavu/DejaVuSansMono.ttf"};
        for (String candidate : candidates) {
            if (!Gdx.files.absolute(candidate).exists()) continue;
            FreeTypeFontGenerator generator = new FreeTypeFontGenerator(Gdx.files.absolute(candidate));
            try {
                FreeTypeFontGenerator.FreeTypeFontParameter parameters = new FreeTypeFontGenerator.FreeTypeFontParameter();
                parameters.size = 18;
                parameters.minFilter = parameters.magFilter = Texture.TextureFilter.Linear;
                return generator.generateFont(parameters);
            } finally {
                generator.dispose();
            }
        }
        BitmapFont fallback = new BitmapFont();
        fallback.getData().setScale(1.35f);
        fallback.getRegion().getTexture().setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
        return fallback;
    }

    private CodeEditorRowRenderer reviewLayer() {
        return new CodeEditorRowRenderer() {
            @Override public void drawBackground(Batch batch, CodeEditorRowRenderContext row) {
                if (!row.isFoldSuffix() && (row.getLine() & 1) == 1) {
                    batch.setColor(0.2f, 0.5f, 0.9f, 0.08f);
                    batch.draw(pixel, row.getContentLeft(), row.getRowBottom(),
                        row.getContentRight() - row.getContentLeft(), row.getLineHeight());
                }
            }
            @Override public void drawForeground(Batch batch, CodeEditorRowRenderContext row) {
                for (int at = row.getLineText().indexOf("TODO"); at >= 0; at = row.getLineText().indexOf("TODO", at + 4)) {
                    int from = Math.max(row.getStartColumn(), at), to = Math.min(row.getEndColumn(), at + 4);
                    if (to <= from) continue;
                    batch.setColor(1f, 0.72f, 0.28f, 1f);
                    batch.draw(pixel, row.getColumnX(from), row.getRowBottom() + 3f,
                        row.getColumnX(to) - row.getColumnX(from), 2f);
                }
            }
        };
    }

    @Override public void render() {
        if (captured) return;
        captured = true;
        buffer.begin();
        stage.getViewport().apply();
        ScreenUtils.clear(0.025f, 0.035f, 0.055f, 1f);
        stage.act(0f);
        stage.draw();
        Pixmap snapshot = Pixmap.createFromFrameBuffer(0, 0, WIDTH, HEIGHT);
        buffer.end();
        com.badlogic.gdx.files.FileHandle output = Gdx.files.absolute(outputFolder).child("rendering-effects.png");
        output.parent().mkdirs();
        PixmapIO.writePNG(output, snapshot, -1, true);
        snapshot.dispose();
        System.out.println("Rendering snapshot: " + output.path());
        Gdx.app.exit();
    }

    @Override public void dispose() {
        if (stage != null) stage.dispose();
        if (buffer != null) buffer.dispose();
        if (font != null) font.dispose();
        if (pixel != null) pixel.dispose();
    }
}
