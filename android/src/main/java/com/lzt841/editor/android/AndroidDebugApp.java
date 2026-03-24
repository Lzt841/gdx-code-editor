package com.lzt841.editor.android;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Files;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.PixmapPacker;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.graphics.g2d.freetype.FreeTypeFontGenerator;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.InputListener;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.Cell;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.ui.TextField;
import com.badlogic.gdx.scenes.scene2d.utils.BaseDrawable;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.scenes.scene2d.utils.Drawable;
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable;
import com.badlogic.gdx.utils.ScreenUtils;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.viewport.ScreenViewport;
import com.lzt841.editor.CodeEditor;
import com.lzt841.editor.CodeEditorContentChangeEvent;
import com.lzt841.editor.CodeEditorContentListener;
import com.lzt841.editor.highlight.BuiltinCodeHighlighters;
import com.lzt841.editor.highlight.CodeHighlighter;
import com.lzt841.editor.input.CodeEditorInteractionContext;
import com.lzt841.editor.input.CodeEditorInteractionListener;
import com.lzt841.editor.input.CodeEditorInteractionMode;
import com.lzt841.editor.structure.BraceCodeStructureProvider;
import com.lzt841.editor.structure.CodeStructureProvider;
import com.lzt841.editor.structure.PythonIndentCodeStructureProvider;

/** Touch-first portrait debug app for Android. */
public class AndroidDebugApp extends ApplicationAdapter {
    private BitmapFont font;
    private FreeTypeFontGenerator fontGenerator;
    private PixmapPacker fontPacker;
    private Texture whitePixel;
    private Stage stage;
    private CodeEditor editor;
    private Table root;
    private Table controlsTable;
    private ScrollPane controlsPane;
    private Cell<ScrollPane> controlsPaneCell;
    private TextField searchField;
    private TextField replaceField;
    private Label stateLabel;
    private Label eventLabel;
    private DemoProfile[] profiles;
    private int profileIndex;
    private String lastEvent = "Ready";
    private TextButton sampleButton;
    private TextButton wrapButton;
    private TextButton lineNumberButton;
    private TextButton readOnlyButton;
    private TextButton disabledButton;
    private TextButton undoButton;
    private TextButton redoButton;
    private TextButton zoomButton;
    private final Vector2 focusScrollTmp = new Vector2();
    private final Vector2 popupStageTmp = new Vector2();
    private Table popupMenu;
    private Label popupTitleLabel;
    private Label popupDetailLabel;
    private CodeEditorInteractionContext activePopupContext;

    @Override
    public void create() {
        font = createUiFont();
        whitePixel = createWhitePixel();
        profiles = createProfiles();

        stage = new Stage(new ScreenViewport());
        editor = new CodeEditor(createEditorStyle());
        editor.setInteractionMode(CodeEditorInteractionMode.TOUCH);
        editor.setMessageText("Tap to type, pinch to zoom, and long press to test touch interactions.");
        editor.setWrapEnabled(false);
        editor.setLineNumbersFixed(true);
        editor.setRainbowBracketsEnabled(true);
        editor.setRainbowGuidesEnabled(true);
        editor.setInteractionListener(new DebugInteractionListener());
        editor.addContentListener(new CodeEditorContentListener() {
            @Override
            public void onContentChanged(CodeEditor editor, CodeEditorContentChangeEvent event) {
                lastEvent = event.type.name() + "  v" + event.documentVersion
                    + "  @" + (event.cursorLine + 1) + ":" + (event.cursorColumn + 1);
            }
        });

        TextureRegion region = new TextureRegion(whitePixel);
        Drawable panel = new TextureRegionDrawable(region).tint(new Color(0.055f, 0.078f, 0.105f, 1f));
        Drawable card = new TextureRegionDrawable(region).tint(new Color(0.063f, 0.09f, 0.121f, 1f));
        Label.LabelStyle labelStyle = new Label.LabelStyle(font, new Color(0.84f, 0.89f, 0.94f, 1f));
        Label.LabelStyle titleStyle = new Label.LabelStyle(font, new Color(0.98f, 0.83f, 0.43f, 1f));
        Label.LabelStyle popupMutedStyle = new Label.LabelStyle(font, new Color(0.67f, 0.76f, 0.84f, 1f));
        TextField.TextFieldStyle fieldStyle = new TextField.TextFieldStyle();
        fieldStyle.font = font;
        fieldStyle.fontColor = Color.WHITE;
        fieldStyle.focusedFontColor = Color.WHITE;
        fieldStyle.messageFont = font;
        fieldStyle.messageFontColor = new Color(0.55f, 0.63f, 0.72f, 1f);
        fieldStyle.cursor = new TextureRegionDrawable(region).tint(new Color(0.98f, 0.83f, 0.43f, 1f));
        fieldStyle.selection = new TextureRegionDrawable(region).tint(new Color(0.231f, 0.447f, 0.686f, 0.55f));
        fieldStyle.background = new TextureRegionDrawable(region).tint(new Color(0.071f, 0.102f, 0.137f, 1f));
        TextButton.TextButtonStyle buttonStyle = new TextButton.TextButtonStyle();
        buttonStyle.up = new TextureRegionDrawable(region).tint(new Color(0.109f, 0.164f, 0.219f, 1f));
        buttonStyle.down = new TextureRegionDrawable(region).tint(new Color(0.176f, 0.274f, 0.356f, 1f));
        buttonStyle.checked = buttonStyle.down;
        buttonStyle.font = font;
        buttonStyle.fontColor = new Color(0.9f, 0.94f, 0.98f, 1f);
        popupTitleLabel = new Label("", titleStyle);
        popupDetailLabel = new Label("", popupMutedStyle);
        popupDetailLabel.setWrap(true);

        root = new Table();
        root.setFillParent(true);
        root.pad(12f);
        root.defaults().pad(6f);
        stage.addActor(root);

        Label title = new Label("Android Portrait Debug", titleStyle);
        title.setWrap(true);
        Label intro = new Label(
            "Touch-first layout for phone portrait mode. Long press editor text to test callbacks, pinch to zoom, and drag handles to verify selection auto-scroll.",
            labelStyle
        );
        intro.setWrap(true);
        root.add(title).growX();
        root.row();
        root.add(intro).growX();
        root.row();

        Table editorCard = new Table();
        editorCard.setBackground(card);
        editorCard.pad(8f);
        editorCard.setClip(true);
        editorCard.add(editor).grow();
        root.add(editorCard).grow().minHeight(0f);
        root.row();

        controlsTable = new Table();
        controlsTable.setBackground(panel);
        controlsTable.pad(10f);
        controlsTable.defaults().growX().padBottom(6f);

        sampleButton = button("Sample", buttonStyle, new Runnable() {
            @Override
            public void run() {
                applyProfile((profileIndex + 1) % profiles.length);
            }
        });
        wrapButton = button("Wrap", buttonStyle, new Runnable() {
            @Override
            public void run() {
                editor.setWrapEnabled(!editor.isWrapEnabled());
            }
        });
        lineNumberButton = button("Line Numbers", buttonStyle, new Runnable() {
            @Override
            public void run() {
                editor.setLineNumbersFixed(!editor.isLineNumbersFixed());
            }
        });
        readOnlyButton = button("Read Only", buttonStyle, new Runnable() {
            @Override
            public void run() {
                editor.setReadOnly(!editor.isReadOnly());
            }
        });
        disabledButton = button("Disabled", buttonStyle, new Runnable() {
            @Override
            public void run() {
                editor.setDisabled(!editor.isDisabled());
            }
        });
        undoButton = button("Undo", buttonStyle, new Runnable() {
            @Override
            public void run() {
                if (!editor.undo()) {
                    lastEvent = "Undo: nothing to revert";
                }
            }
        });
        redoButton = button("Redo", buttonStyle, new Runnable() {
            @Override
            public void run() {
                if (!editor.redo()) {
                    lastEvent = "Redo: nothing to reapply";
                }
            }
        });
        zoomButton = button("Zoom", buttonStyle, new Runnable() {
            @Override
            public void run() {
                float next = editor.getZoomScale() >= 1.5f ? 1f : editor.getZoomScale() + 0.25f;
                editor.setZoomScale(next);
            }
        });

        searchField = new TextField("value", fieldStyle);
        searchField.setMessageText("Search");
        replaceField = new TextField("", fieldStyle);
        replaceField.setMessageText("Replace");
        TextButton searchButton = button("Apply Search", buttonStyle, new Runnable() {
            @Override
            public void run() {
                editor.setSearchText(searchField.getText());
                lastEvent = "Search: " + editor.getSearchMatchCount() + " matches";
            }
        });
        TextButton prevButton = button("Prev Match", buttonStyle, new Runnable() {
            @Override
            public void run() {
                if (!editor.findPreviousSearchMatch()) {
                    lastEvent = "Search: no previous match";
                }
            }
        });
        TextButton nextButton = button("Next Match", buttonStyle, new Runnable() {
            @Override
            public void run() {
                if (!editor.findNextSearchMatch()) {
                    lastEvent = "Search: no next match";
                }
            }
        });
        TextButton replaceCurrentButton = button("Replace Current", buttonStyle, new Runnable() {
            @Override
            public void run() {
                editor.setSearchText(searchField.getText());
                if (!editor.replaceCurrentSearchMatch(replaceField.getText())) {
                    lastEvent = "Replace: no editable match";
                }
            }
        });
        TextButton replaceAllButton = button("Replace All", buttonStyle, new Runnable() {
            @Override
            public void run() {
                editor.setSearchText(searchField.getText());
                int count = editor.replaceAllSearchMatches(replaceField.getText());
                lastEvent = "Replace all: " + count + " matches";
            }
        });

        stateLabel = new Label("", labelStyle);
        stateLabel.setWrap(true);
        eventLabel = new Label("", labelStyle);
        eventLabel.setWrap(true);

        Label quickTitle = new Label("Quick Toggles", titleStyle);
        Label searchTitle = new Label("Search And Replace", titleStyle);
        Label stateTitle = new Label("State", titleStyle);

        controlsTable.add(sampleButton).colspan(2);
        controlsTable.row();
        controlsTable.add(quickTitle).colspan(2).left().padTop(2f).padBottom(2f);
        controlsTable.row();
        controlsTable.add(wrapButton);
        controlsTable.add(lineNumberButton);
        controlsTable.row();
        controlsTable.add(readOnlyButton);
        controlsTable.add(disabledButton);
        controlsTable.row();
        controlsTable.add(undoButton);
        controlsTable.add(redoButton);
        controlsTable.row();
        controlsTable.add(zoomButton).colspan(2);
        controlsTable.row();
        controlsTable.add(searchTitle).colspan(2).left().padTop(6f).padBottom(2f);
        controlsTable.row();
        controlsTable.add(searchField).colspan(2);
        controlsTable.row();
        controlsTable.add(searchButton).colspan(2);
        controlsTable.row();
        controlsTable.add(prevButton);
        controlsTable.add(nextButton);
        controlsTable.row();
        controlsTable.add(replaceField).colspan(2);
        controlsTable.row();
        controlsTable.add(replaceCurrentButton);
        controlsTable.add(replaceAllButton);
        controlsTable.row();
        controlsTable.add(stateTitle).colspan(2).left().padTop(6f).padBottom(2f);
        controlsTable.row();
        controlsTable.add(stateLabel).colspan(2);
        controlsTable.row();
        controlsTable.add(eventLabel).colspan(2);

        controlsPane = new ScrollPane(controlsTable);
        controlsPane.setFadeScrollBars(false);
        controlsPane.setScrollingDisabled(true, false);
        controlsPane.setOverscroll(false, true);
        controlsPaneCell = root.add(controlsPane).growX().minHeight(220f);

        popupMenu = createPopupMenu(buttonStyle);
        stage.addActor(popupMenu);
        stage.addCaptureListener(new InputListener() {
            @Override
            public boolean touchDown(InputEvent event, float x, float y, int pointer, int button) {
                if (popupMenu == null || !popupMenu.isVisible()) {
                    return false;
                }
                Actor target = event.getTarget();
                if (!isDescendantOf(target, popupMenu)) {
                    hidePopupMenu();
                }
                return false;
            }
        });

        Gdx.input.setInputProcessor(stage);
        applyProfile(0);
        updateResponsiveLayout();
        refreshLabels();
    }

    @Override
    public void render() {
        refreshLabels();
        ScreenUtils.clear(0.047f, 0.071f, 0.102f, 1f);
        stage.act(Gdx.graphics.getDeltaTime());
        keepFocusedControlVisible();
        stage.draw();
    }

    @Override
    public void resize(int width, int height) {
        stage.getViewport().update(width, height, true);
        updateResponsiveLayout();
    }

    @Override
    public void dispose() {
        stage.dispose();
        whitePixel.dispose();
        font.dispose();
        if (fontPacker != null) {
            fontPacker.dispose();
            fontPacker = null;
        }
        if (fontGenerator != null) {
            fontGenerator.dispose();
            fontGenerator = null;
        }
    }

    private TextButton button(String text, TextButton.TextButtonStyle style, final Runnable action) {
        final TextButton button = new TextButton(text, style);
        button.pad(10f, 12f, 10f, 12f);
        button.addListener(new ClickListener() {
            @Override
            public void clicked(com.badlogic.gdx.scenes.scene2d.InputEvent event, float x, float y) {
                action.run();
                button.setChecked(false);
                refreshLabels();
            }
        });
        return button;
    }

    private Table createPopupMenu(TextButton.TextButtonStyle buttonStyle) {
        Table popup = new Table();
        popup.setVisible(false);
        popup.setBackground(createSolidDrawable(new Color(0.078f, 0.114f, 0.153f, 0.98f)));
        popup.pad(12f);
        popup.defaults().growX().padBottom(6f);

        popup.add(popupTitleLabel).left().padBottom(2f);
        popup.row();
        popup.add(popupDetailLabel).width(260f).padBottom(10f);
        popup.row();
        popup.add(createPopupButton("Copy Selection", buttonStyle, new Runnable() {
            @Override
            public void run() {
                if (activePopupContext == null || activePopupContext.selectedText.isEmpty()) {
                    lastEvent = "Popup: no selection to copy";
                } else {
                    Gdx.app.getClipboard().setContents(activePopupContext.selectedText);
                    lastEvent = "Popup: copied " + Math.min(activePopupContext.selectedText.length(), 24) + " chars";
                }
                hidePopupMenu();
            }
        }));
        popup.row();
        popup.add(createPopupButton("Search Selection", buttonStyle, new Runnable() {
            @Override
            public void run() {
                if (activePopupContext == null || activePopupContext.selectedText.isEmpty()) {
                    lastEvent = "Popup: no selection to search";
                } else {
                    searchField.setText(activePopupContext.selectedText);
                    editor.setSearchText(activePopupContext.selectedText);
                    lastEvent = "Popup: search = " + shorten(activePopupContext.selectedText, 28);
                }
                hidePopupMenu();
            }
        }));
        popup.row();
        popup.add(createPopupButton("Select All", buttonStyle, new Runnable() {
            @Override
            public void run() {
                editor.selectAllText();
                lastEvent = "Popup: selected all text";
                hidePopupMenu();
            }
        }));
        popup.row();
        popup.add(createPopupButton("Paste", buttonStyle, new Runnable() {
            @Override
            public void run() {
                if (!editor.pasteClipboard()) {
                    lastEvent = "Popup: editor is not editable";
                } else {
                    lastEvent = "Popup: pasted clipboard";
                }
                hidePopupMenu();
            }
        }));
        popup.row();
        popup.add(createPopupButton("Dismiss", buttonStyle, new Runnable() {
            @Override
            public void run() {
                hidePopupMenu();
            }
        })).padBottom(0f);
        popup.pack();
        return popup;
    }

    private TextButton createPopupButton(String text, TextButton.TextButtonStyle style, final Runnable action) {
        final TextButton button = new TextButton(text, style);
        button.pad(8f, 12f, 8f, 12f);
        button.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                action.run();
                button.setChecked(false);
                refreshLabels();
            }
        });
        return button;
    }

    private void showPopupMenu(CodeEditorInteractionContext context) {
        if (popupMenu == null || stage == null) {
            return;
        }
        activePopupContext = context;
        popupTitleLabel.setText("Long Press Menu");
        popupDetailLabel.setText(
            "At " + (context.line + 1) + ":" + (context.column + 1)
                + "\nSelection: " + (context.selectedText.isEmpty() ? "(none)" : shorten(context.selectedText, 32))
        );
        popupMenu.pack();

        float margin = 14f;
        float desiredWidth = Math.min(stage.getViewport().getWorldWidth() - margin * 2f, 320f);
        popupMenu.setWidth(Math.max(220f, desiredWidth));
        popupMenu.invalidateHierarchy();
        popupMenu.pack();

        popupStageTmp.set(context.x, context.y);
        editor.localToStageCoordinates(popupStageTmp);

        float x = Math.max(margin, Math.min(
            popupStageTmp.x - popupMenu.getWidth() * 0.2f,
            stage.getViewport().getWorldWidth() - popupMenu.getWidth() - margin
        ));
        float y = popupStageTmp.y - popupMenu.getHeight() - 12f;
        if (y < margin) {
            y = Math.min(
                popupStageTmp.y + 12f,
                stage.getViewport().getWorldHeight() - popupMenu.getHeight() - margin
            );
        }

        popupMenu.setPosition(x, y);
        popupMenu.toFront();
        popupMenu.setVisible(true);
    }

    private void hidePopupMenu() {
        if (popupMenu != null) {
            popupMenu.setVisible(false);
        }
        activePopupContext = null;
    }

    private void refreshLabels() {
        if (editor == null) {
            return;
        }
        DemoProfile profile = profiles[profileIndex];
        sampleButton.setText("Sample: " + profile.name);
        wrapButton.setText("Wrap: " + onOff(editor.isWrapEnabled()));
        lineNumberButton.setText("Line Numbers: " + onOff(editor.isLineNumbersFixed()));
        readOnlyButton.setText("Read Only: " + onOff(editor.isReadOnly()));
        disabledButton.setText("Disabled: " + onOff(editor.isDisabled()));
        undoButton.setText("Undo: " + onOff(editor.canUndo()));
        redoButton.setText("Redo: " + onOff(editor.canRedo()));
        zoomButton.setText("Zoom: " + String.format(java.util.Locale.ROOT, "%.2fx", editor.getZoomScale()));
        stateLabel.setText(
            "Highlighter: " + profile.description + "\n"
                + "Cursor: " + (editor.getCursorLine() + 1) + ":" + (editor.getCursorColumn() + 1) + "\n"
                + "Selection: " + onOff(editor.hasSelection()) + "\n"
                + "Matches: " + editor.getSearchMatchCount() + "\n"
                + "Tips: pinch to zoom, long press to test touch callback, drag handles to test auto-scroll."
        );
        eventLabel.setText(lastEvent);
    }

    private void applyProfile(int index) {
        hidePopupMenu();
        profileIndex = index;
        DemoProfile profile = profiles[index];
        editor.setHighlighter(profile.highlighter);
        editor.setStructureProvider(profile.structureProvider);
        editor.setText(profile.text);
        lastEvent = "Loaded sample: " + profile.name;
    }

    private DemoProfile[] createProfiles() {
        return new DemoProfile[] {
            new DemoProfile("Java", "Brace structure + Java highlight", BuiltinCodeHighlighters.java(), new BraceCodeStructureProvider(), createJavaSample()),
            new DemoProfile("Python", "Indent structure + Python highlight", BuiltinCodeHighlighters.python(), new PythonIndentCodeStructureProvider(), createPythonSample()),
            new DemoProfile("JSON", "Brace structure + JSON highlight", BuiltinCodeHighlighters.json(), new BraceCodeStructureProvider(), createJsonSample())
        };
    }

    private CodeEditor.CodeEditorStyle createEditorStyle() {
        CodeEditor.CodeEditorStyle style = CodeEditor.CodeEditorStyle.theme(font)
            .whitePixelTexture(whitePixel)
            .themeColor(new Color(0.298f, 0.592f, 0.922f, 1f))
            .backgroundColor(new Color(0.055f, 0.078f, 0.109f, 1f))
            .gutterColor(new Color(0.022f, 0.031f, 0.043f, 1f))
            .textColor(new Color(0.93f, 0.96f, 0.99f, 1f))
            .gutterTextColor(new Color(0.64f, 0.72f, 0.8f, 1f))
            .textBaselineOffset(-9f)
            .build();
        style.gutterMinWidth = 58f;
        style.gutterLeftPadding = 10f;
        style.gutterFoldIndicatorGap = 10f;
        style.foldIndicatorSize = 14f;
        style.foldIndicatorRightPadding = 10f;
        style.textLeftPadding = 18f;
        style.textRightPadding = 28f;
        style.rowPadding = 8f;
        style.scrollbarHitWidth = 28f;
        style.scrollbarGap = 12f;
        style.scrollbarMargin = 5f;
        style.scrollbarMinThumbSize = 32f;
        style.selectionHandleTouchRadiusMultiplier = 2f;
        style.guideSpacing = 20f;
        style.guideOffsetX = -7f;
        style.foldBadgeGap = 12f;
        style.foldBadgeHorizontalPadding = 14f;
        style.foldBadgeVerticalPadding = 5f;
        return style;
    }

    private Drawable createSolidDrawable(final Color color) {
        final Color tint = new Color(color);
        return new BaseDrawable() {
            @Override
            public void draw(Batch batch, float x, float y, float width, float height) {
                Color previous = batch.getColor();
                float previousR = previous.r;
                float previousG = previous.g;
                float previousB = previous.b;
                float previousA = previous.a;
                batch.setColor(tint);
                batch.draw(whitePixel, x, y, width, height);
                batch.setColor(previousR, previousG, previousB, previousA);
            }
        };
    }

    private Drawable createGutterDrawable(final Color fillColor, final Color borderColor, final float borderWidth) {
        final Color fill = new Color(fillColor);
        final Color border = new Color(borderColor);
        return new BaseDrawable() {
            @Override
            public void draw(Batch batch, float x, float y, float width, float height) {
                Color previous = batch.getColor();
                float previousR = previous.r;
                float previousG = previous.g;
                float previousB = previous.b;
                float previousA = previous.a;

                batch.setColor(fill);
                batch.draw(whitePixel, x, y, width, height);

                float actualBorder = Math.max(1f, Math.min(borderWidth, width));
                batch.setColor(border);
                batch.draw(whitePixel, x + width - actualBorder, y, actualBorder, height);

                batch.setColor(previousR, previousG, previousB, previousA);
            }
        };
    }

    private Drawable createChevronDrawable(final boolean collapsed, final Color color) {
        final Color tint = new Color(color);
        return new BaseDrawable() {
            {
                setMinWidth(14f);
                setMinHeight(14f);
            }

            @Override
            public void draw(Batch batch, float x, float y, float width, float height) {
                float thickness = Math.max(1.6f, Math.min(width, height) * 0.16f);
                if (collapsed) {
                    drawSegment(batch, x + width * 0.34f, y + height * 0.22f, x + width * 0.7f, y + height * 0.5f, thickness, tint);
                    drawSegment(batch, x + width * 0.34f, y + height * 0.78f, x + width * 0.7f, y + height * 0.5f, thickness, tint);
                } else {
                    drawSegment(batch, x + width * 0.22f, y + height * 0.64f, x + width * 0.5f, y + height * 0.3f, thickness, tint);
                    drawSegment(batch, x + width * 0.78f, y + height * 0.64f, x + width * 0.5f, y + height * 0.3f, thickness, tint);
                }
            }
        };
    }

    private void drawSegment(Batch batch, float x1, float y1, float x2, float y2, float thickness, Color color) {
        Color previous = batch.getColor();
        float previousR = previous.r;
        float previousG = previous.g;
        float previousB = previous.b;
        float previousA = previous.a;

        float dx = x2 - x1;
        float dy = y2 - y1;
        float length = (float) Math.sqrt(dx * dx + dy * dy);
        float angle = (float) Math.toDegrees(Math.atan2(dy, dx));

        batch.setColor(color);
        batch.draw(
            whitePixel,
            x1,
            y1 - thickness * 0.5f,
            0f,
            thickness * 0.5f,
            length,
            thickness,
            1f,
            1f,
            angle,
            0,
            0,
            1,
            1,
            false,
            false
        );
        batch.setColor(previousR, previousG, previousB, previousA);
    }

    private BitmapFont createUiFont() {
        FileHandle fontFile = resolveAndroidFontFile();
        if (fontFile == null) {
            BitmapFont fallback = new BitmapFont();
            fallback.getData().setScale(1.7f);
            fallback.setUseIntegerPositions(false);
            return fallback;
        }

        fontGenerator = new FreeTypeFontGenerator(fontFile);
        fontPacker = new PixmapPacker(512, 512, Pixmap.Format.RGBA8888, 2, false);
        FreeTypeFontGenerator.FreeTypeFontParameter parameter = new FreeTypeFontGenerator.FreeTypeFontParameter();
        parameter.size = 30;
        parameter.minFilter = Texture.TextureFilter.Linear;
        parameter.magFilter = Texture.TextureFilter.Linear;
        parameter.incremental = true;
        parameter.packer = fontPacker;
        parameter.characters = FreeTypeFontGenerator.DEFAULT_CHARS
            + "代码编辑器调试安卓竖屏搜索替换触摸缩放选择撤销重做行号高亮折叠";
        BitmapFont generated = fontGenerator.generateFont(parameter);
        generated.setUseIntegerPositions(false);
        return generated;
    }

    private void updateResponsiveLayout() {
        if (controlsPaneCell == null || stage == null || root == null) {
            return;
        }
        float worldHeight = stage.getViewport().getWorldHeight();
        float controlsHeight = Math.max(220f, Math.min(worldHeight * 0.42f, 420f));
        controlsPaneCell.height(controlsHeight);
        root.invalidateHierarchy();
    }

    private void keepFocusedControlVisible() {
        if (controlsPane == null || controlsTable == null || stage == null) {
            return;
        }
        Actor focus = stage.getKeyboardFocus();
        if (focus == null || !isDescendantOf(focus, controlsTable)) {
            return;
        }
        focusScrollTmp.set(0f, 0f);
        focus.localToAscendantCoordinates(controlsTable, focusScrollTmp);
        controlsPane.scrollTo(
            Math.max(0f, focusScrollTmp.x - 12f),
            Math.max(0f, focusScrollTmp.y - 24f),
            focus.getWidth() + 24f,
            focus.getHeight() + 48f,
            false,
            true
        );
    }

    private boolean isDescendantOf(Actor actor, Actor parent) {
        Actor current = actor;
        while (current != null) {
            if (current == parent) {
                return true;
            }
            current = current.getParent();
        }
        return false;
    }

    private BitmapFont createUiFontLegacy() {
        FileHandle fontFile = resolveAndroidFontFile();
        if (fontFile == null) {
            BitmapFont fallback = new BitmapFont();
            fallback.getData().setScale(1.6f);
            fallback.setUseIntegerPositions(false);
            return fallback;
        }

        FreeTypeFontGenerator generator = new FreeTypeFontGenerator(fontFile);
        try {
            FreeTypeFontGenerator.FreeTypeFontParameter parameter = new FreeTypeFontGenerator.FreeTypeFontParameter();
            parameter.size = 32;
            parameter.minFilter = Texture.TextureFilter.Linear;
            parameter.magFilter = Texture.TextureFilter.Linear;
            parameter.characters = FreeTypeFontGenerator.DEFAULT_CHARS
                + "代码编辑器调试安卓竖屏搜索替换触摸缩放选择撤销重做行号高亮折叠";
            parameter.incremental=true;
            parameter.packer = new PixmapPacker(512, 512, Pixmap.Format.RGBA8888, 2, false);
            BitmapFont generated = generator.generateFont(parameter);
            return generated;
        } finally {
            //generator.dispose();
        }
    }

    private FileHandle resolveAndroidFontFile() {
        String[] candidates = {
            "/system/fonts/NotoSansCJK-Regular.ttc",
            "/system/fonts/NotoSansSC-Regular.otf",
            "/system/fonts/Roboto-Regular.ttf",
            "/system/fonts/DroidSans.ttf"
        };
        for (String path : candidates) {
            FileHandle handle = Gdx.files.getFileHandle(path, Files.FileType.Absolute);
            if (handle.exists()) {
                return handle;
            }
        }
        return null;
    }

    private Texture createWhitePixel() {
        Pixmap pixmap = new Pixmap(1, 1, Pixmap.Format.RGBA8888);
        pixmap.setColor(Color.WHITE);
        pixmap.fill();
        Texture texture = new Texture(pixmap);
        pixmap.dispose();
        return texture;
    }

    private String createJavaSample() {
        StringBuilder builder = new StringBuilder();
        builder.append("public class AndroidDebugSample {\n");
        for (int i = 0; i < 80; i++) {
            builder.append("    public String block").append(i).append("(String value) {\n");
            builder.append("        return value == null ? \"fallback\" : value.trim() + ").append(i).append(";\n");
            builder.append("    }\n");
        }
        builder.append("}\n");
        return builder.toString();
    }

    private String createPythonSample() {
        return ""
            + "def summarize(rows):\n"
            + "    result = []\n"
            + "    for row in rows:\n"
            + "        result.append(str(row).strip())\n"
            + "    return '\\n'.join(result)\n";
    }

    private String createJsonSample() {
        return ""
            + "{\n"
            + "  \"platform\": \"android\",\n"
            + "  \"orientation\": \"portrait\",\n"
            + "  \"features\": [\"touch\", \"search\", \"replace\", \"zoom\"]\n"
            + "}\n";
    }

    private String onOff(boolean value) {
        return value ? "On" : "Off";
    }

    private String shorten(String text, int maxLength) {
        if (text == null || text.length() <= maxLength) {
            return text == null ? "" : text;
        }
        return text.substring(0, Math.max(0, maxLength - 1)) + "...";
    }

    private final class DebugInteractionListener implements CodeEditorInteractionListener {
        @Override
        public boolean onLongPress(CodeEditor editor, CodeEditorInteractionContext context) {
            lastEvent = "Long press at " + (context.line + 1) + ":" + (context.column + 1);
            showPopupMenu(context);
            return true;
        }

        @Override
        public boolean onSecondaryClick(CodeEditor editor, CodeEditorInteractionContext context) {
            return false;
        }

        @Override
        public boolean onDoubleClick(CodeEditor editor, CodeEditorInteractionContext context) {
            lastEvent = "Double click at " + (context.line + 1) + ":" + (context.column + 1);
            return false;
        }
    }

    private static final class DemoProfile {
        final String name;
        final String description;
        final CodeHighlighter highlighter;
        final CodeStructureProvider structureProvider;
        final String text;

        DemoProfile(String name, String description, CodeHighlighter highlighter, CodeStructureProvider structureProvider, String text) {
            this.name = name;
            this.description = description;
            this.highlighter = highlighter;
            this.structureProvider = structureProvider;
            this.text = text;
        }
    }
}
