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
import com.lzt841.editor.search.CodeSearchBar;
import com.lzt841.editor.commands.CodeCommandPalette.CodeCommandPaletteStyle;
import com.lzt841.editor.extensions.CodeCommandPaletteExtension;
import com.lzt841.editor.extensions.CodeCompletionExtension;
import com.lzt841.editor.extensions.CodeSnippetExtension;
import com.lzt841.editor.extensions.CodeLineEditingExtension;
import com.lzt841.editor.completion.CodeCompletionItem;
import com.lzt841.editor.extensions.CodeStandardCommandsExtension;
import com.lzt841.editor.extensions.CodeTextEditingExtension;
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
    private Cell<ScrollPane> controlsPaneCell;
    private CodeSearchBar searchBar;
    private Label stateLabel;
    private Label eventLabel;
    private DemoProfile[] profiles;
    private int profileIndex;
    private String lastEvent = "Ready";
    private TextButton sampleButton;
    private TextButton wrapButton;
    private TextButton lineNumberButton;
    private TextButton lineNumberFixedButton;
    private TextButton scrollbarButton;
    private TextButton transientHandleButton;
    private TextButton passwordModeButton;
    private TextButton readOnlyButton;
    private TextButton disabledButton;
    private TextButton undoButton;
    private TextButton redoButton;
    private TextButton zoomButton;
    private TextButton zoomEnabledButton;
    private TextButton overscrollButton;
    private final Vector2 popupStageTmp = new Vector2();
    private Table popupMenu;
    private Label popupTitleLabel;
    private Label popupDetailLabel;
    private Cell<Label> popupDetailCell;
    private TextButton popupCopyButton;
    private TextButton popupSearchButton;
    private CodeEditorInteractionContext activePopupContext;
    private float uiScale = 1f;

    @Override
    public void create() {
        uiScale = resolveUiScale();
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
        TextureRegionDrawable fieldBackground = new TextureRegionDrawable(region);
        fieldBackground.setLeftWidth(dp(12f));
        fieldBackground.setRightWidth(dp(12f));
        fieldStyle.background = fieldBackground.tint(new Color(0.085f, 0.12f, 0.16f, 1f));
        fieldStyle.focusedBackground = fieldBackground.tint(new Color(0.14f, 0.21f, 0.28f, 1f));
        fieldStyle.disabledBackground = fieldBackground.tint(new Color(0.055f, 0.078f, 0.105f, 1f));
        TextButton.TextButtonStyle buttonStyle = new TextButton.TextButtonStyle();
        buttonStyle.up = new TextureRegionDrawable(region).tint(new Color(0.109f, 0.164f, 0.219f, 1f));
        buttonStyle.down = new TextureRegionDrawable(region).tint(new Color(0.176f, 0.274f, 0.356f, 1f));
        buttonStyle.checked = buttonStyle.down;
        buttonStyle.font = font;
        buttonStyle.fontColor = new Color(0.9f, 0.94f, 0.98f, 1f);
        buttonStyle.disabledFontColor = fieldStyle.disabledFontColor = new Color(0.52f, 0.62f, 0.72f, 1f);
        popupTitleLabel = new Label("", titleStyle);
        popupDetailLabel = new Label("", popupMutedStyle);
        popupDetailLabel.setWrap(true);
        CodeCommandPaletteStyle paletteStyle = new CodeCommandPaletteStyle(fieldStyle, buttonStyle, labelStyle);
        paletteStyle.window.background = panel;
        paletteStyle.maxWidth = dp(680f);
        paletteStyle.maxHeight = dp(460f);
        paletteStyle.edgeMargin = dp(12f);
        paletteStyle.rowHeight = dp(44f);
        CodeCommandPaletteExtension paletteExtension = new CodeCommandPaletteExtension(paletteStyle);
        editor.getExtensions().install(new CodeStandardCommandsExtension());
        editor.getExtensions().install(new CodeTextEditingExtension());
        editor.getExtensions().install(new CodeLineEditingExtension());
        editor.getExtensions().install(new CodeSnippetExtension("demo.snippets",
            CodeCompletionItem.snippet("fori", "for (int ${1:i} = 0; $1 < ${2:count}; $1++) {\n\t$0\n}", "for loop"),
            CodeCompletionItem.snippet("ifelse", "if (${1:condition}) {\n\t$2\n} else {\n\t$0\n}", "if / else"),
            CodeCompletionItem.snippet("sout", "System.out.println(${1:value});$0", "print value")));
        editor.getExtensions().install(new CodeCompletionExtension());
        editor.getExtensions().install(paletteExtension);
        paletteExtension.getPalette().setExecutionListener((command, handled) ->
            lastEvent = command.label + (handled ? ": completed" : ": not applied"));

        root = new Table();
        root.setFillParent(true);
        root.pad(dp(12f));
        root.defaults().pad(dp(6f));
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
        editorCard.pad(dp(8f));
        // Handles hang below the caret; parent clip would cut them and the editor edges.
        editorCard.setClip(false);
        searchBar = new CodeSearchBar(editor, fieldStyle, buttonStyle, labelStyle);
        searchBar.setBackground(panel);
        searchBar.pad(dp(8f));
        editorCard.add(searchBar).growX().minWidth(0f);
        editorCard.row();
        editorCard.add(editor).grow().minHeight(0f);
        searchBar.toFront();
        stage.addCaptureListener(searchBar.createShortcutListener());
        root.add(editorCard).grow().minHeight(0f);
        root.row();

        Table controlsTable = new Table();
        controlsTable.setBackground(panel);
        controlsTable.pad(dp(10f));
        controlsTable.defaults().growX().padBottom(dp(6f));

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
                editor.setLineNumbersVisible(!editor.isLineNumbersVisible());
            }
        });
        lineNumberFixedButton = button("Line Numbers Fixed", buttonStyle, new Runnable() {
            @Override
            public void run() {
                editor.setLineNumbersFixed(!editor.isLineNumbersFixed());
            }
        });
        scrollbarButton = button("Scrollbars", buttonStyle, new Runnable() {
            @Override
            public void run() {
                editor.setScrollbarsVisible(!editor.isScrollbarsVisible());
            }
        });
        transientHandleButton = button("Transient Handle", buttonStyle, new Runnable() {
            @Override
            public void run() {
                editor.setTransientCaretHandleEnabled(!editor.isTransientCaretHandleEnabled());
            }
        });
        passwordModeButton = button("Password Mode", buttonStyle, new Runnable() {
            @Override
            public void run() {
                editor.setPasswordMode(!editor.isPasswordMode());
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
        zoomEnabledButton = button("Pinch Zoom", buttonStyle, new Runnable() {
            @Override
            public void run() {
                editor.setZoomEnabled(!editor.isZoomEnabled());
            }
        });
        overscrollButton = button("Overscroll", buttonStyle, new Runnable() {
            @Override
            public void run() {
                editor.setOverscrollEnabled(!editor.isOverscrollEnabled());
            }
        });

        stateLabel = new Label("", labelStyle);
        stateLabel.setWrap(true);
        eventLabel = new Label("", labelStyle);
        eventLabel.setWrap(true);

        Label quickTitle = new Label("Quick Toggles", titleStyle);
        Label stateTitle = new Label("State", titleStyle);

        controlsTable.add(sampleButton).colspan(2);
        controlsTable.row();
        controlsTable.add(button("Command Palette", buttonStyle, () -> {
            if (!editor.getCommands().execute(CodeCommandPaletteExtension.SHOW)) {
                lastEvent = "Enable the editor and leave password mode to open commands.";
            }
        })).colspan(2);
        controlsTable.row();
        controlsTable.add(button("Show Completions", buttonStyle, () -> {
            if (!editor.getCommands().execute(CodeCompletionExtension.TRIGGER)) {
                lastEvent = "Completion requires a writable editor outside password mode.";
            }
        })).colspan(2);
        controlsTable.row();
        controlsTable.add(button("Duplicate Lines", buttonStyle, () -> {
            boolean changed = editor.getCommands().execute(CodeLineEditingExtension.DUPLICATE_DOWN);
            lastEvent = changed ? "Duplicated current/selected lines" : "Line duplication was not applied";
            if (changed) stage.setKeyboardFocus(editor);
        }));
        controlsTable.add(button("Delete Lines", buttonStyle, () -> {
            boolean changed = editor.getCommands().execute(CodeLineEditingExtension.DELETE);
            lastEvent = changed ? "Deleted current/selected lines" : "Line deletion was not applied";
            if (changed) stage.setKeyboardFocus(editor);
        }));
        controlsTable.row();
        controlsTable.add(quickTitle).colspan(2).left().padTop(dp(2f)).padBottom(dp(2f));
        controlsTable.row();
        controlsTable.add(wrapButton);
        controlsTable.add(lineNumberButton);
        controlsTable.row();
        controlsTable.add(lineNumberFixedButton);
        controlsTable.add(scrollbarButton);
        controlsTable.row();
        controlsTable.add(transientHandleButton);
        controlsTable.add(passwordModeButton);
        controlsTable.row();
        controlsTable.add(zoomEnabledButton);
        controlsTable.add(overscrollButton);
        controlsTable.row();
        controlsTable.add(zoomButton).colspan(2);
        controlsTable.row();
        controlsTable.add(readOnlyButton);
        controlsTable.add(disabledButton);
        controlsTable.row();
        controlsTable.add(undoButton);
        controlsTable.add(redoButton);
        controlsTable.row();
        controlsTable.add(stateTitle).colspan(2).left().padTop(dp(6f)).padBottom(dp(2f));
        controlsTable.row();
        controlsTable.add(stateLabel).colspan(2);
        controlsTable.row();
        controlsTable.add(eventLabel).colspan(2);

        ScrollPane controlsPane = new ScrollPane(controlsTable);
        controlsPane.setFadeScrollBars(false);
        controlsPane.setScrollingDisabled(true, false);
        controlsPane.setOverscroll(false, true);
        controlsPaneCell = root.add(controlsPane).growX();

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
        stage.draw();
    }

    @Override
    public void resize(int width, int height) {
        stage.getViewport().update(width, height, true);
        updateResponsiveLayout();
    }

    @Override
    public void dispose() {
        if (editor != null) editor.getExtensions().dispose();
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
        button.pad(dp(10f), dp(12f), dp(10f), dp(12f));
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
        popup.pad(dp(12f));
        popup.defaults().growX().padBottom(dp(6f));

        popup.add(popupTitleLabel).left().padBottom(dp(2f));
        popup.row();
        popupDetailCell = popup.add(popupDetailLabel).width(dp(260f)).padBottom(dp(10f));
        popup.row();
        popupCopyButton = createPopupButton("Copy Selection", buttonStyle, new Runnable() {
            @Override
            public void run() {
                if (activePopupContext == null || activePopupContext.selectedText == null
                    || activePopupContext.selectedText.isEmpty()) {
                    lastEvent = "Popup: no selection to copy";
                } else if (editor.isPasswordMode()) {
                    lastEvent = "Popup: copying is unavailable in password mode";
                } else if (editor.copySelection()) {
                    lastEvent = "Popup: copied selection";
                } else {
                    lastEvent = "Popup: clipboard refused the selection";
                }
                hidePopupMenu();
            }
        });
        popup.add(popupCopyButton);
        popup.row();
        popupSearchButton = createPopupButton("Search Selection", buttonStyle, new Runnable() {
            @Override
            public void run() {
                if (activePopupContext != null) {
                    searchBar.openForText(activePopupContext.selectedText, false);
                }
                hidePopupMenu();
            }
        });
        popup.add(popupSearchButton);
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
        button.pad(dp(8f), dp(12f), dp(8f), dp(12f));
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
        boolean passwordMode = editor.isPasswordMode();
        boolean hasSelection = context.selectedText != null && !context.selectedText.isEmpty();
        popupCopyButton.setDisabled(passwordMode || !hasSelection);
        popupSearchButton.setDisabled(passwordMode || !hasSelection
            || context.selectedText.indexOf('\n') >= 0);
        popupTitleLabel.setText("Long Press Menu");
        String selectionSummary = passwordMode
            ? "(hidden)"
            : (hasSelection ? shorten(context.selectedText, 32) : "(none)");
        popupDetailLabel.setText(
            "At " + (context.line + 1) + ":" + (context.column + 1)
                + "\nSelection: " + selectionSummary
        );
        popupMenu.pack();

        float stageWidth = stage.getViewport().getWorldWidth();
        float margin = Math.min(dp(14f), stageWidth * 0.04f);
        float availableWidth = Math.max(1f, stageWidth - margin * 2f);
        float desiredWidth = Math.min(availableWidth, dp(320f));
        popupDetailCell.width(Math.max(1f, Math.min(dp(260f), desiredWidth - dp(24f))));
        popupMenu.setWidth(desiredWidth);
        popupMenu.invalidateHierarchy();
        popupMenu.pack();

        popupStageTmp.set(context.x, context.y);
        editor.localToStageCoordinates(popupStageTmp);

        float x = Math.max(margin, Math.min(
            popupStageTmp.x - popupMenu.getWidth() * 0.2f,
            stageWidth - popupMenu.getWidth() - margin
        ));
        float y = popupStageTmp.y - popupMenu.getHeight() - dp(12f);
        if (y < margin) {
            y = Math.min(
                popupStageTmp.y + dp(12f),
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
        lineNumberButton.setText("Line Numbers: " + onOff(editor.isLineNumbersVisible()));
        lineNumberFixedButton.setText("Fixed Gutter: " + onOff(editor.isLineNumbersFixed()));
        scrollbarButton.setText("Scrollbars: " + onOff(editor.isScrollbarsVisible()));
        transientHandleButton.setText("Transient Handle: " + onOff(editor.isTransientCaretHandleEnabled()));
        passwordModeButton.setText("Password Mode: " + onOff(editor.isPasswordMode()));
        readOnlyButton.setText("Read Only: " + onOff(editor.isReadOnly()));
        disabledButton.setText("Disabled: " + onOff(editor.isDisabled()));
        undoButton.setText("Undo: " + onOff(editor.canUndo()));
        redoButton.setText("Redo: " + onOff(editor.canRedo()));
        zoomButton.setText("Zoom: " + String.format(java.util.Locale.ROOT, "%.2fx", editor.getZoomScale()));
        zoomEnabledButton.setText("Pinch Zoom: " + onOff(editor.isZoomEnabled()));
        overscrollButton.setText("Overscroll: " + onOff(editor.isOverscrollEnabled()));
        stateLabel.setText(
            "Highlighter: " + profile.description + "\n"
                + "Cursor: " + (editor.getCursorLine() + 1) + ":" + (editor.getCursorColumn() + 1) + "\n"
                + "Selection: " + onOff(editor.hasSelection()) + "\n"
                + "Line numbers: " + onOff(editor.isLineNumbersVisible())
                + "   Fixed gutter: " + onOff(editor.isLineNumbersFixed()) + "\n"
                + "Scrollbars: " + onOff(editor.isScrollbarsVisible())
                + "   Transient handle: " + onOff(editor.isTransientCaretHandleEnabled()) + "\n"
                + "Password mode: " + onOff(editor.isPasswordMode()) + "\n"
                + "Pinch zoom: " + onOff(editor.isZoomEnabled())
                + "   Overscroll: " + onOff(editor.isOverscrollEnabled()) + "\n"
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
        editor.clearSearchRange();
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
            .textBaselineOffset(dp(-9f))
            .build();
        style.gutterMinWidth = dp(58f);
        style.gutterLeftPadding = dp(10f);
        style.gutterFoldIndicatorGap = dp(10f);
        style.foldIndicatorSize = dp(14f);
        style.foldIndicatorRightPadding = dp(10f);
        style.textLeftPadding = dp(18f);
        style.textRightPadding = dp(28f);
        style.rowPadding = dp(8f);
        style.scrollbarWidth = dp(8f);
        style.scrollbarHitWidth = dp(28f);
        style.scrollbarGap = dp(12f);
        style.scrollbarMargin = dp(5f);
        style.scrollbarMinThumbSize = dp(32f);
        style.scrollbarHitInset = dp(6f);
        style.selectionHandleTouchRadiusMultiplier = 1.25f;
        style.guideSpacing = dp(20f);
        style.guideOffsetX = dp(-7f);
        style.foldBadgeGap = dp(12f);
        style.foldBadgeHorizontalPadding = dp(14f);
        style.foldBadgeVerticalPadding = dp(5f);
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
                setMinWidth(dp(14f));
                setMinHeight(dp(14f));
            }

            @Override
            public void draw(Batch batch, float x, float y, float width, float height) {
                float thickness = Math.max(dp(1.6f), Math.min(width, height) * 0.16f);
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
            fallback.getData().setScale(1.1f * uiScale);
            fallback.setUseIntegerPositions(false);
            return fallback;
        }

        fontGenerator = new FreeTypeFontGenerator(fontFile);
        fontPacker = new PixmapPacker(512, 512, Pixmap.Format.RGBA8888, 2, false);
        FreeTypeFontGenerator.FreeTypeFontParameter parameter = new FreeTypeFontGenerator.FreeTypeFontParameter();
        parameter.size = Math.max(1, Math.round(16f * uiScale));
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
        float controlsHeight = Math.min(worldHeight * 0.34f, dp(360f));
        controlsPaneCell.height(controlsHeight);
        root.invalidateHierarchy();
    }

    private float resolveUiScale() {
        float density = Gdx.graphics == null ? 1f : Gdx.graphics.getDensity();
        return density > 0f ? density : 1f;
    }

    private float dp(float value) {
        return value * uiScale;
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
