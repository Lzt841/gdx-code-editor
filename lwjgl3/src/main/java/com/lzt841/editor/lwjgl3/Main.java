package com.lzt841.editor.lwjgl3;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Files;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.g2d.PixmapPacker;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.graphics.g2d.freetype.FreeTypeFontGenerator;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.InputListener;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.ui.TextField;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.scenes.scene2d.utils.Drawable;
import com.badlogic.gdx.scenes.scene2d.utils.BaseDrawable;
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable;
import com.badlogic.gdx.utils.ScreenUtils;
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

/** Desktop entry point with an interactive debug panel for the code editor. */
public class Main extends ApplicationAdapter {
    private BitmapFont font;
    private Texture whitePixel;
    private Stage stage;
    private CodeEditor editor;
    private Table root;

    private TextButton.TextButtonStyle debugButtonStyle;
    private Label.LabelStyle debugLabelStyle;
    private Label.LabelStyle debugTitleStyle;
    private Label.LabelStyle debugMutedStyle;
    private TextField.TextFieldStyle debugTextFieldStyle;
    private ScrollPane.ScrollPaneStyle debugScrollStyle;
    private Drawable cardBackground;
    private Drawable sidebarBackground;
    private Drawable popupBackground;

    private Label statusLabel;
    private Label metricsLabel;
    private Label eventLabel;
    private Label popupTitleLabel;
    private Label popupDetailLabel;
    private TextField searchField;
    private TextField replaceField;

    private TextButton profileButton;
    private TextButton interactionButton;
    private TextButton wrapButton;
    private TextButton lineNumberButton;
    private TextButton rainbowBracketButton;
    private TextButton rainbowGuideButton;
    private TextButton previousMatchButton;
    private TextButton nextMatchButton;
    private TextButton applySearchButton;
    private TextButton replaceCurrentButton;
    private TextButton replaceAllButton;
    private TextButton undoButton;
    private TextButton redoButton;
    private TextButton selectAllButton;
    private TextButton copyButton;
    private TextButton cutButton;
    private TextButton pasteButton;
    private TextButton searchCaseButton;
    private TextButton zoomInButton;
    private TextButton zoomOutButton;
    private TextButton zoomResetButton;
    private TextButton readOnlyButton;
    private TextButton disabledButton;
    private TextButton popupCopyButton;
    private TextButton popupSearchButton;
    private TextButton popupWrapButton;
    private TextButton popupCloseButton;

    private DemoProfile[] profiles;
    private int profileIndex;
    private String lastEventText = "Ready";
    private Table popupMenu;
    private CodeEditorInteractionContext activePopupContext;
    private final Vector2 popupStagePosition = new Vector2();

    @Override
    public void create() {
        font = createUiFont();
        font.setUseIntegerPositions(false);
        whitePixel = createWhitePixel();

        createUiStyles();
        createProfiles();

        stage = new Stage(new ScreenViewport());
        root = new Table();
        root.setFillParent(true);
        root.pad(12f);
        root.defaults().pad(8f);
        stage.addActor(root);

        editor = new CodeEditor(createEditorStyle());
        editor.setMessageText("Pick a sample and start testing the editor.");
        editor.setWrapEnabled(false);
        editor.setLineNumbersFixed(true);
        editor.setRainbowBracketsEnabled(true);
        editor.setRainbowGuidesEnabled(true);
        editor.setSearchText("value");
        editor.setReadOnly(false);
        editor.setDisabled(false);
        editor.setInteractionMode(CodeEditorInteractionMode.AUTO);
        editor.setInteractionListener(new DebugInteractionListener());
        editor.addContentListener(new CodeEditorContentListener() {
            @Override
            public void onContentChanged(CodeEditor editor, CodeEditorContentChangeEvent event) {
                lastEventText = "Content: " + event.type.name()
                    + "  v" + event.documentVersion
                    + "  @" + (event.cursorLine + 1) + ":" + (event.cursorColumn + 1)
                    + "  len=" + event.text.length();
            }
        });
        applyProfile(0);

        ScrollPane sidebar = createSidebar();
        root.add(sidebar).minWidth(320f).top().fillY();

        Table body = new Table();
        body.setClip(true);
        body.add(editor).expand().fill();
        root.add(body).expand().fill();

        popupMenu = createPopupMenu();
        stage.addActor(popupMenu);
        stage.addCaptureListener(new InputListener() {
            @Override
            public boolean touchDown(InputEvent event, float x, float y, int pointer, int button) {
                if (!popupMenu.isVisible()) {
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
        refreshDebugPanel();
    }

    @Override
    public void render() {
        refreshDebugPanel();
        ScreenUtils.clear(0.047f, 0.071f, 0.102f, 1f);
        stage.act(Gdx.graphics.getDeltaTime());
        stage.draw();
    }

    @Override
    public void resize(int width, int height) {
        if (stage != null) {
            stage.getViewport().update(width, height, true);
        }
    }

    @Override
    public void dispose() {
        stage.dispose();
        whitePixel.dispose();
        font.dispose();
    }

    private void createUiStyles() {
        TextureRegion region = new TextureRegion(whitePixel);
        Drawable buttonUp = new TextureRegionDrawable(region).tint(new Color(0.109f, 0.164f, 0.219f, 1f));
        Drawable buttonDown = new TextureRegionDrawable(region).tint(new Color(0.176f, 0.274f, 0.356f, 1f));
        Drawable buttonOver = new TextureRegionDrawable(region).tint(new Color(0.133f, 0.203f, 0.274f, 1f));

        debugButtonStyle = new TextButton.TextButtonStyle();
        debugButtonStyle.up = buttonUp;
        debugButtonStyle.down = buttonDown;
        debugButtonStyle.over = buttonOver;
        debugButtonStyle.checked = buttonDown;
        debugButtonStyle.font = font;
        debugButtonStyle.fontColor = new Color(0.88f, 0.92f, 0.96f, 1f);
        debugButtonStyle.downFontColor = new Color(1f, 1f, 1f, 1f);

        debugLabelStyle = new Label.LabelStyle(font, new Color(0.84f, 0.89f, 0.94f, 1f));
        debugTitleStyle = new Label.LabelStyle(font, new Color(0.98f, 0.83f, 0.43f, 1f));
        debugMutedStyle = new Label.LabelStyle(font, new Color(0.61f, 0.7f, 0.78f, 1f));
        debugTextFieldStyle = new TextField.TextFieldStyle();
        debugTextFieldStyle.font = font;
        debugTextFieldStyle.fontColor = new Color(0.92f, 0.95f, 0.98f, 1f);
        debugTextFieldStyle.focusedFontColor = debugTextFieldStyle.fontColor;
        debugTextFieldStyle.disabledFontColor = new Color(0.56f, 0.63f, 0.72f, 1f);
        debugTextFieldStyle.messageFont = font;
        debugTextFieldStyle.messageFontColor = new Color(0.46f, 0.55f, 0.63f, 1f);
        debugTextFieldStyle.background = new TextureRegionDrawable(region).tint(new Color(0.071f, 0.102f, 0.137f, 1f));
        debugTextFieldStyle.focusedBackground = new TextureRegionDrawable(region).tint(new Color(0.094f, 0.137f, 0.184f, 1f));
        debugTextFieldStyle.disabledBackground = new TextureRegionDrawable(region).tint(new Color(0.055f, 0.078f, 0.105f, 1f));
        debugTextFieldStyle.cursor = new TextureRegionDrawable(region).tint(new Color(0.98f, 0.83f, 0.43f, 1f));
        debugTextFieldStyle.selection = new TextureRegionDrawable(region).tint(new Color(0.231f, 0.447f, 0.686f, 0.55f));
        cardBackground = new TextureRegionDrawable(region).tint(new Color(0.063f, 0.09f, 0.121f, 1f));
        sidebarBackground = new TextureRegionDrawable(region).tint(new Color(0.055f, 0.078f, 0.105f, 1f));
        popupBackground = new TextureRegionDrawable(region).tint(new Color(0.075f, 0.106f, 0.145f, 0.98f));

        debugScrollStyle = new ScrollPane.ScrollPaneStyle();
        debugScrollStyle.background = sidebarBackground;
        debugScrollStyle.vScroll = new TextureRegionDrawable(region).tint(new Color(0.082f, 0.121f, 0.156f, 1f));
        debugScrollStyle.vScrollKnob = new TextureRegionDrawable(region).tint(new Color(0.255f, 0.39f, 0.514f, 1f));

        statusLabel = new Label("", debugLabelStyle);
        statusLabel.setWrap(true);
        metricsLabel = new Label("", debugMutedStyle);
        metricsLabel.setWrap(true);
        eventLabel = new Label("", debugMutedStyle);
        eventLabel.setWrap(true);
        popupTitleLabel = new Label("", debugTitleStyle);
        popupDetailLabel = new Label("", debugMutedStyle);
        popupDetailLabel.setWrap(true);
    }

    private ScrollPane createSidebar() {
        Table sidebar = new Table();
        sidebar.top().left();
        sidebar.pad(14f);
        sidebar.defaults().growX().padBottom(8f);

        Label title = new Label("Code Editor Debug", debugTitleStyle);
        Label intro = new Label(
            "Toggle editor capabilities, switch built-in highlighters, and quickly reproduce touch or mouse issues from one place.",
            debugMutedStyle
        );
        intro.setWrap(true);

        profileButton = createActionButton("", new Runnable() {
            @Override
            public void run() {
                applyProfile((profileIndex + 1) % profiles.length);
            }
        });
        interactionButton = createActionButton("", new Runnable() {
            @Override
            public void run() {
                cycleInteractionMode();
            }
        });
        wrapButton = createActionButton("", new Runnable() {
            @Override
            public void run() {
                editor.setWrapEnabled(!editor.isWrapEnabled());
            }
        });
        lineNumberButton = createActionButton("", new Runnable() {
            @Override
            public void run() {
                editor.setLineNumbersFixed(!editor.isLineNumbersFixed());
            }
        });
        rainbowBracketButton = createActionButton("", new Runnable() {
            @Override
            public void run() {
                editor.setRainbowBracketsEnabled(!editor.isRainbowBracketsEnabled());
            }
        });
        rainbowGuideButton = createActionButton("", new Runnable() {
            @Override
            public void run() {
                editor.setRainbowGuidesEnabled(!editor.isRainbowGuidesEnabled());
            }
        });
        previousMatchButton = createActionButton("", new Runnable() {
            @Override
            public void run() {
                if (!editor.findPreviousSearchMatch()) {
                    lastEventText = "Search: no previous match";
                } else {
                    lastEventText = "Search: moved to previous match";
                }
            }
        });
        nextMatchButton = createActionButton("", new Runnable() {
            @Override
            public void run() {
                if (!editor.findNextSearchMatch()) {
                    lastEventText = "Search: no next match";
                } else {
                    lastEventText = "Search: moved to next match";
                }
            }
        });
        searchField = new TextField(editor.getSearchText(), debugTextFieldStyle);
        searchField.setMessageText("Search text");
        searchField.setTextFieldFilter(new TextField.TextFieldFilter() {
            @Override
            public boolean acceptChar(TextField textField, char c) {
                return c != '\r';
            }
        });
        replaceField = new TextField("", debugTextFieldStyle);
        replaceField.setMessageText("Replacement");
        replaceField.setTextFieldFilter(new TextField.TextFieldFilter() {
            @Override
            public boolean acceptChar(TextField textField, char c) {
                return c != '\r';
            }
        });
        applySearchButton = createActionButton("", new Runnable() {
            @Override
            public void run() {
                editor.setSearchText(searchField.getText());
                lastEventText = editor.getSearchMatchCount() > 0
                    ? "Search: found " + editor.getSearchMatchCount() + " matches"
                    : "Search: no matches";
            }
        });
        replaceCurrentButton = createActionButton("", new Runnable() {
            @Override
            public void run() {
                editor.setSearchText(searchField.getText());
                if (editor.replaceCurrentSearchMatch(replaceField.getText())) {
                    lastEventText = "Replace: current or next match updated";
                } else {
                    lastEventText = "Replace: no editable match";
                }
            }
        });
        replaceAllButton = createActionButton("", new Runnable() {
            @Override
            public void run() {
                editor.setSearchText(searchField.getText());
                int replaced = editor.replaceAllSearchMatches(replaceField.getText());
                lastEventText = "Replace all: " + replaced + " matches updated";
            }
        });
        undoButton = createActionButton("", new Runnable() {
            @Override
            public void run() {
                if (!editor.undo()) {
                    lastEventText = "Undo: nothing to revert";
                }
            }
        });
        redoButton = createActionButton("", new Runnable() {
            @Override
            public void run() {
                if (!editor.redo()) {
                    lastEventText = "Redo: nothing to reapply";
                }
            }
        });
        selectAllButton = createActionButton("", new Runnable() {
            @Override
            public void run() {
                editor.selectAllText();
                lastEventText = "Selection: selected all text";
            }
        });
        copyButton = createActionButton("", new Runnable() {
            @Override
            public void run() {
                if (!editor.copySelection()) {
                    lastEventText = "Copy: no selection";
                } else {
                    lastEventText = "Copy: selection copied";
                }
            }
        });
        cutButton = createActionButton("", new Runnable() {
            @Override
            public void run() {
                if (!editor.cutSelection()) {
                    lastEventText = editor.isReadOnly() || editor.isDisabled()
                        ? "Cut: editor is not editable"
                        : "Cut: no selection";
                }
            }
        });
        pasteButton = createActionButton("", new Runnable() {
            @Override
            public void run() {
                if (!editor.pasteClipboard()) {
                    lastEventText = "Paste: editor is not editable";
                }
            }
        });
        searchCaseButton = createActionButton("", new Runnable() {
            @Override
            public void run() {
                editor.setSearchCaseSensitive(!editor.isSearchCaseSensitive());
                editor.setSearchText(searchField.getText());
                lastEventText = "Search case: " + (editor.isSearchCaseSensitive() ? "Sensitive" : "Ignore case");
            }
        });
        zoomInButton = createActionButton("", new Runnable() {
            @Override
            public void run() {
                editor.setZoomScale(editor.getZoomScale() + 0.1f);
                lastEventText = "Zoom: " + formatZoom(editor.getZoomScale());
            }
        });
        zoomOutButton = createActionButton("", new Runnable() {
            @Override
            public void run() {
                editor.setZoomScale(editor.getZoomScale() - 0.1f);
                lastEventText = "Zoom: " + formatZoom(editor.getZoomScale());
            }
        });
        zoomResetButton = createActionButton("", new Runnable() {
            @Override
            public void run() {
                editor.setZoomScale(1f);
                lastEventText = "Zoom: reset to 1.00x";
            }
        });
        readOnlyButton = createActionButton("", new Runnable() {
            @Override
            public void run() {
                editor.setReadOnly(!editor.isReadOnly());
            }
        });
        disabledButton = createActionButton("", new Runnable() {
            @Override
            public void run() {
                editor.setDisabled(!editor.isDisabled());
            }
        });

        TextButton reloadButton = createActionButton("Reload Current Sample", new Runnable() {
            @Override
            public void run() {
                applyProfile(profileIndex);
            }
        });
        TextButton jumpTopButton = createActionButton("Move Cursor To Start", new Runnable() {
            @Override
            public void run() {
                if (!editor.isDisabled()) {
                    editor.setDisabled(false);
                    editor.setReadOnly(false);
                }
                editor.setText(profiles[profileIndex].text);
            }
        });

        sidebar.add(title).padBottom(4f);
        sidebar.row();
        sidebar.add(intro).padBottom(14f);
        sidebar.row();
        sidebar.add(profileButton);
        sidebar.row();
        sidebar.add(interactionButton);
        sidebar.row();
        sidebar.add(wrapButton);
        sidebar.row();
        sidebar.add(lineNumberButton);
        sidebar.row();
        sidebar.add(rainbowBracketButton);
        sidebar.row();
        sidebar.add(rainbowGuideButton);
        sidebar.row();
        sidebar.add(new Label("Search", debugTitleStyle)).padBottom(2f);
        sidebar.row();
        sidebar.add(searchField);
        sidebar.row();
        sidebar.add(applySearchButton);
        sidebar.row();
        sidebar.add(searchCaseButton);
        sidebar.row();
        sidebar.add(previousMatchButton);
        sidebar.row();
        sidebar.add(nextMatchButton);
        sidebar.row();
        sidebar.add(new Label("Replace", debugTitleStyle)).padBottom(2f);
        sidebar.row();
        sidebar.add(replaceField);
        sidebar.row();
        sidebar.add(replaceCurrentButton);
        sidebar.row();
        sidebar.add(replaceAllButton);
        sidebar.row();
        sidebar.add(new Label("Edit Actions", debugTitleStyle)).padBottom(2f);
        sidebar.row();
        sidebar.add(undoButton);
        sidebar.row();
        sidebar.add(redoButton);
        sidebar.row();
        sidebar.add(selectAllButton);
        sidebar.row();
        sidebar.add(copyButton);
        sidebar.row();
        sidebar.add(cutButton);
        sidebar.row();
        sidebar.add(pasteButton);
        sidebar.row();
        sidebar.add(new Label("View", debugTitleStyle)).padBottom(2f);
        sidebar.row();
        sidebar.add(zoomInButton);
        sidebar.row();
        sidebar.add(zoomOutButton);
        sidebar.row();
        sidebar.add(zoomResetButton);
        sidebar.row();
        sidebar.add(readOnlyButton);
        sidebar.row();
        sidebar.add(disabledButton).padBottom(14f);
        sidebar.row();
        sidebar.add(reloadButton);
        sidebar.row();
        sidebar.add(jumpTopButton).padBottom(14f);
        sidebar.row();
        sidebar.add(new Label("Current State", debugTitleStyle)).padBottom(2f);
        sidebar.row();
        sidebar.add(createInfoCard(statusLabel, 96f));
        sidebar.row();
        sidebar.add(new Label("Metrics", debugTitleStyle)).padBottom(2f);
        sidebar.row();
        sidebar.add(createInfoCard(metricsLabel, 72f));
        sidebar.row();
        sidebar.add(new Label("Last Event", debugTitleStyle)).padBottom(2f);
        sidebar.row();
        sidebar.add(createInfoCard(eventLabel, 72f));

        ScrollPane pane = new ScrollPane(sidebar, debugScrollStyle);
        pane.setFadeScrollBars(false);
        pane.setScrollingDisabled(true, false);
        pane.setOverscroll(false, true);
        return pane;
    }

    private Table createInfoCard(Label label, float minHeight) {
        Table card = new Table();
        card.setBackground(cardBackground);
        card.pad(10f);
        card.add(label).growX().minHeight(minHeight);
        return card;
    }

    private TextButton createActionButton(String text, final Runnable action) {
        final TextButton button = new TextButton(text, debugButtonStyle);
        button.pad(10f, 14f, 10f, 14f);
        button.addListener(new ClickListener() {
            @Override
            public void clicked(com.badlogic.gdx.scenes.scene2d.InputEvent event, float x, float y) {
                action.run();
                button.setChecked(false);
                refreshDebugPanel();
            }
        });
        return button;
    }

    private Table createPopupMenu() {
        Table popup = new Table();
        popup.setVisible(false);
        popup.setBackground(popupBackground);
        popup.pad(12f);
        popup.defaults().growX().padBottom(6f);

        popupCopyButton = createPopupActionButton("Copy Selection", new Runnable() {
            @Override
            public void run() {
                if (activePopupContext == null || activePopupContext.selectedText.isEmpty()) {
                    lastEventText = "Popup: no selection to copy";
                } else {
                    Gdx.app.getClipboard().setContents(activePopupContext.selectedText);
                    lastEventText = "Popup: copied " + Math.min(activePopupContext.selectedText.length(), 24) + " chars";
                }
                hidePopupMenu();
            }
        });
        popupSearchButton = createPopupActionButton("Search Selection", new Runnable() {
            @Override
            public void run() {
                if (activePopupContext == null || activePopupContext.selectedText.isEmpty()) {
                    lastEventText = "Popup: no selection to search";
                } else {
                    editor.setSearchText(activePopupContext.selectedText);
                    lastEventText = "Popup: search = " + shorten(activePopupContext.selectedText, 28);
                }
                refreshDebugPanel();
                hidePopupMenu();
            }
        });
        popupWrapButton = createPopupActionButton("Toggle Wrap", new Runnable() {
            @Override
            public void run() {
                editor.setWrapEnabled(!editor.isWrapEnabled());
                lastEventText = "Popup: wrap " + onOff(editor.isWrapEnabled());
                refreshDebugPanel();
                hidePopupMenu();
            }
        });
        popupCloseButton = createPopupActionButton("Dismiss", new Runnable() {
            @Override
            public void run() {
                hidePopupMenu();
            }
        });

        popup.add(popupTitleLabel).padBottom(2f);
        popup.row();
        popup.add(popupDetailLabel).width(240f).padBottom(10f);
        popup.row();
        popup.add(popupCopyButton);
        popup.row();
        popup.add(popupSearchButton);
        popup.row();
        popup.add(popupWrapButton);
        popup.row();
        popup.add(popupCloseButton).padBottom(0f);
        popup.pack();
        return popup;
    }

    private TextButton createPopupActionButton(String text, final Runnable action) {
        final TextButton button = new TextButton(text, debugButtonStyle);
        button.pad(8f, 12f, 8f, 12f);
        button.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                action.run();
                button.setChecked(false);
            }
        });
        return button;
    }

    private void showPopupMenu(CodeEditorInteractionContext context, String triggerLabel) {
        activePopupContext = context;
        popupTitleLabel.setText(triggerLabel + " Menu");
        popupDetailLabel.setText(
            "At " + (context.line + 1) + ":" + (context.column + 1)
                + "\nSelection: " + (context.selectedText.isEmpty() ? "(none)" : shorten(context.selectedText, 36))
        );

        boolean hasSelection = !context.selectedText.isEmpty();
        popupCopyButton.setDisabled(!hasSelection);
        popupSearchButton.setDisabled(!hasSelection);

        popupMenu.pack();
        popupStagePosition.set(context.x, context.y);
        editor.localToStageCoordinates(popupStagePosition);

        float x = popupStagePosition.x + 10f;
        float y = popupStagePosition.y - popupMenu.getPrefHeight() - 10f;
        x = Math.max(12f, Math.min(x, stage.getViewport().getWorldWidth() - popupMenu.getPrefWidth() - 12f));
        y = Math.max(12f, Math.min(y, stage.getViewport().getWorldHeight() - popupMenu.getPrefHeight() - 12f));

        popupMenu.setPosition(x, y);
        popupMenu.toFront();
        popupMenu.setVisible(true);
    }

    private void hidePopupMenu() {
        popupMenu.setVisible(false);
        activePopupContext = null;
    }

    private boolean isDescendantOf(Actor actor, Actor candidateParent) {
        Actor current = actor;
        while (current != null) {
            if (current == candidateParent) {
                return true;
            }
            current = current.getParent();
        }
        return false;
    }

    private String shorten(String text, int maxLength) {
        if (text == null || text.length() <= maxLength) {
            return text == null ? "" : text;
        }
        return text.substring(0, Math.max(0, maxLength - 3)) + "...";
    }

    private void refreshDebugPanel() {
        if (editor == null) {
            return;
        }

        DemoProfile profile = profiles[profileIndex];
        profileButton.setText("Language Sample: " + profile.name);
        interactionButton.setText("Interaction Mode: " + editor.getInteractionMode().name());
        wrapButton.setText("Auto Wrap: " + onOff(editor.isWrapEnabled()));
        lineNumberButton.setText("Line Numbers Fixed: " + onOff(editor.isLineNumbersFixed()));
        rainbowBracketButton.setText("Rainbow Brackets: " + onOff(editor.isRainbowBracketsEnabled()));
        rainbowGuideButton.setText("Rainbow Guides: " + onOff(editor.isRainbowGuidesEnabled()));
        applySearchButton.setText("Apply Search");
        previousMatchButton.setText("Previous Match");
        nextMatchButton.setText("Next Match");
        searchCaseButton.setText("Search Case: " + (editor.isSearchCaseSensitive() ? "Sensitive" : "Ignore Case"));
        replaceCurrentButton.setText("Replace Current");
        replaceAllButton.setText("Replace All");
        undoButton.setText("Undo: " + onOff(editor.canUndo()));
        redoButton.setText("Redo: " + onOff(editor.canRedo()));
        selectAllButton.setText("Select All");
        copyButton.setText("Copy Selection");
        cutButton.setText("Cut Selection");
        pasteButton.setText("Paste Clipboard");
        zoomInButton.setText("Zoom In");
        zoomOutButton.setText("Zoom Out");
        zoomResetButton.setText("Zoom Reset");
        readOnlyButton.setText("Read Only: " + onOff(editor.isReadOnly()));
        disabledButton.setText("Disabled: " + onOff(editor.isDisabled()));

        statusLabel.setText(
            "Sample: " + profile.name + "\n"
                + "Highlighter: " + profile.description + "\n"
                + "Disabled: " + onOff(editor.isDisabled()) + "   Read only: " + onOff(editor.isReadOnly()) + "\n"
                + "Wrap: " + onOff(editor.isWrapEnabled()) + "   Fixed line numbers: " + onOff(editor.isLineNumbersFixed()) + "\n"
                + "Rainbow brackets: " + onOff(editor.isRainbowBracketsEnabled())
                + "   Rainbow guides: " + onOff(editor.isRainbowGuidesEnabled()) + "\n"
                + "Search case: " + (editor.isSearchCaseSensitive() ? "Sensitive" : "Ignore case") + "\n"
                + "Search: " + (editor.getSearchText().isEmpty() ? "(none)" : editor.getSearchText()) + "\n"
                + "Current match: " + (editor.hasCurrentSearchMatch() ? editor.getCurrentSearchMatchOrdinal() : 0)
                + "/" + editor.getSearchMatchCount() + "\n"
                + "Zoom: " + formatZoom(editor.getZoomScale()) + "\n"
                + "Menu demo: mouse right click or touch long press"
        );
        metricsLabel.setText(
            "Lines: " + editor.getLineCount() + "\n"
                + "Cursor: " + (editor.getCursorLine() + 1) + ":" + (editor.getCursorColumn() + 1) + "\n"
                + "Selection: " + onOff(editor.hasSelection()) + "\n"
                + "Undo/Redo: " + onOff(editor.canUndo()) + "/" + onOff(editor.canRedo()) + "\n"
                + "Matches: " + editor.getSearchMatchCount() + "\n"
                + "Tips: Shift + wheel for horizontal scroll, pinch in touch mode to zoom, and drag selected text in mouse mode to move it."
        );
        eventLabel.setText(lastEventText);
    }

    private String formatZoom(float zoomScale) {
        return String.format(java.util.Locale.ROOT, "%.2fx", zoomScale);
    }

    private void cycleInteractionMode() {
        CodeEditorInteractionMode current = editor.getInteractionMode();
        switch (current) {
            case AUTO:
                editor.setInteractionMode(CodeEditorInteractionMode.MOUSE);
                break;
            case MOUSE:
                editor.setInteractionMode(CodeEditorInteractionMode.TOUCH);
                break;
            default:
                editor.setInteractionMode(CodeEditorInteractionMode.AUTO);
                break;
        }
    }

    private void createProfiles() {
        profiles = new DemoProfile[] {
            new DemoProfile("Java", "Brace structure + Java highlight", BuiltinCodeHighlighters.java(), new BraceCodeStructureProvider(), createJavaDemo()),
            new DemoProfile("Kotlin", "Brace structure + Kotlin highlight", BuiltinCodeHighlighters.kotlin(), new BraceCodeStructureProvider(), createKotlinDemo()),
            new DemoProfile("JavaScript", "Brace structure + JS/TS highlight", BuiltinCodeHighlighters.javascript(), new BraceCodeStructureProvider(), createJavaScriptDemo()),
            new DemoProfile("Python", "Indent structure + Python highlight", BuiltinCodeHighlighters.python(), new PythonIndentCodeStructureProvider(), createPythonDemo()),
            new DemoProfile("JSON", "Brace structure + JSON highlight", BuiltinCodeHighlighters.json(), new BraceCodeStructureProvider(), createJsonDemo()),
            new DemoProfile("XML", "Brace structure + XML highlight", BuiltinCodeHighlighters.xml(), new BraceCodeStructureProvider(), createXmlDemo()),
            new DemoProfile("Plain Text", "Brace structure disabled-like plain text", BuiltinCodeHighlighters.plainText(), new BraceCodeStructureProvider(), createPlainTextDemo())
        };
    }

    private void applyProfile(int index) {
        profileIndex = index;
        DemoProfile profile = profiles[profileIndex];
        editor.setHighlighter(profile.highlighter);
        editor.setStructureProvider(profile.structureProvider);
        editor.setText(profile.text);
        if (searchField != null) {
            searchField.setText(editor.getSearchText());
        }
        lastEventText = "Loaded sample: " + profile.name;
    }

    private String onOff(boolean value) {
        return value ? "On" : "Off";
    }

    private CodeEditor.CodeEditorStyle createEditorStyle() {
        TextureRegion region = new TextureRegion(whitePixel);
        CodeEditor.CodeEditorStyle style = new CodeEditor.CodeEditorStyle();
        style.font = font;
        style.background = new TextureRegionDrawable(region).tint(new Color(0.051f, 0.074f, 0.102f, 1f));
        style.focusedBackground = new TextureRegionDrawable(region).tint(new Color(0.066f, 0.094f, 0.129f, 1f));
        style.disabledBackground = new TextureRegionDrawable(region).tint(new Color(0.043f, 0.059f, 0.078f, 1f));
        style.gutterBackground = new TextureRegionDrawable(region).tint(new Color(0.060f, 0.086f, 0.115f, 1f));
        style.currentBlock = new TextureRegionDrawable(region).tint(new Color(0.071f, 0.106f, 0.145f, 0.72f));
        style.currentLine = new TextureRegionDrawable(region).tint(new Color(0.094f, 0.133f, 0.184f, 1f));
        style.cursor = new TextureRegionDrawable(region).tint(new Color(0.984f, 0.824f, 0.43f, 1f));
        style.selection = new TextureRegionDrawable(region).tint(new Color(0.196f, 0.376f, 0.612f, 0.55f));
        style.searchHighlight = new TextureRegionDrawable(region).tint(new Color(0.945f, 0.741f, 0.255f, 0.28f));
        style.currentSearchHighlight = new TextureRegionDrawable(region).tint(new Color(1f, 0.824f, 0.286f, 0.5f));
        style.selectionHandle = new TextureRegionDrawable(region).tint(new Color(0.274f, 0.561f, 0.898f, 0.95f));
        style.bracketMatch = new TextureRegionDrawable(region).tint(new Color(0.933f, 0.722f, 0.275f, 0.35f));
        style.guide = new TextureRegionDrawable(region).tint(new Color(0.184f, 0.266f, 0.333f, 0.8f));
        style.scrollbarTrack = new TextureRegionDrawable(region).tint(new Color(0.094f, 0.129f, 0.172f, 1f));
        style.scrollbarKnob = new TextureRegionDrawable(region).tint(new Color(0.263f, 0.357f, 0.431f, 1f));
        style.foldBadge = new TextureRegionDrawable(region).tint(new Color(0.184f, 0.266f, 0.333f, 1f));
        style.foldExpanded = createChevronDrawable(false, new Color(0.72f, 0.79f, 0.86f, 1f));
        style.foldCollapsed = createChevronDrawable(true, new Color(0.72f, 0.79f, 0.86f, 1f));
        style.gutterLeftPadding = 6f;
        style.gutterFoldIndicatorGap = 8f;
        style.foldIndicatorSize = 10f;
        style.foldIndicatorRightPadding = 8f;
        style.selectionHandleRadius = 11f;
        style.rainbowBracketColors = new Color[] {
            new Color(0.976f, 0.392f, 0.380f, 1f),
            new Color(0.988f, 0.690f, 0.278f, 1f),
            new Color(0.973f, 0.902f, 0.345f, 1f),
            new Color(0.431f, 0.839f, 0.478f, 1f),
            new Color(0.345f, 0.757f, 0.996f, 1f),
            new Color(0.753f, 0.541f, 0.992f, 1f)
        };
        style.rainbowGuideColors = new Color[] {
            new Color(0.976f, 0.392f, 0.380f, 0.24f),
            new Color(0.988f, 0.690f, 0.278f, 0.24f),
            new Color(0.973f, 0.902f, 0.345f, 0.24f),
            new Color(0.431f, 0.839f, 0.478f, 0.24f),
            new Color(0.345f, 0.757f, 0.996f, 0.24f),
            new Color(0.753f, 0.541f, 0.992f, 0.24f)
        };
        style.whitePixelTexture = whitePixel;
        return style;
    }

    private Drawable createChevronDrawable(final boolean collapsed, final Color color) {
        final Color tint = new Color(color);
        return new BaseDrawable() {
            {
                setMinWidth(10f);
                setMinHeight(10f);
            }

            @Override
            public void draw(Batch batch, float x, float y, float width, float height) {
                float thickness = Math.max(1.35f, Math.min(width, height) * 0.14f);
                if (collapsed) {
                    drawSegment(batch, x + width * 0.36f, y + height * 0.24f, x + width * 0.68f, y + height * 0.5f, thickness, tint);
                    drawSegment(batch, x + width * 0.36f, y + height * 0.76f, x + width * 0.68f, y + height * 0.5f, thickness, tint);
                } else {
                    drawSegment(batch, x + width * 0.24f, y + height * 0.62f, x + width * 0.5f, y + height * 0.34f, thickness, tint);
                    drawSegment(batch, x + width * 0.76f, y + height * 0.62f, x + width * 0.5f, y + height * 0.34f, thickness, tint);
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

    private Texture createWhitePixel() {
        Pixmap pixmap = new Pixmap(1, 1, Pixmap.Format.RGBA8888);
        pixmap.setColor(Color.WHITE);
        pixmap.fill();
        Texture texture = new Texture(pixmap);
        pixmap.dispose();
        return texture;
    }

    private BitmapFont createUiFont() {
        FileHandle fontFile = resolveDesktopFontFile();
        if (fontFile == null) {
            BitmapFont fallback = new BitmapFont();
            fallback.getData().setScale(1.15f);
            return fallback;
        }

        FreeTypeFontGenerator generator = new FreeTypeFontGenerator(fontFile);
        try {
            FreeTypeFontGenerator.FreeTypeFontParameter parameter = new FreeTypeFontGenerator.FreeTypeFontParameter();
            parameter.size = 21;
            parameter.minFilter = Texture.TextureFilter.Linear;
            parameter.magFilter = Texture.TextureFilter.Linear;
            parameter.characters = FreeTypeFontGenerator.DEFAULT_CHARS
                + "代码编辑器调试搜索替换撤销重做高亮折叠";
            parameter.incremental=true;
            parameter.packer = new PixmapPacker(512, 512, Pixmap.Format.RGBA8888, 2, false);
            BitmapFont generated = generator.generateFont(parameter);

            return generated;
        } finally {
            //generator.dispose();
        }
    }

    private FileHandle resolveDesktopFontFile() {
        String[] candidates = {
            "C:/Windows/Fonts/msyh.ttc",
            "C:/Windows/Fonts/consola.ttf",
            "C:/Windows/Fonts/msyh.ttc",
            "/System/Library/Fonts/Supplemental/Menlo.ttc",
            "/System/Library/Fonts/Supplemental/Arial Unicode.ttf",
            "/usr/share/fonts/truetype/dejavu/DejaVuSansMono.ttf",
            "/usr/share/fonts/opentype/noto/NotoSansCJK-Regular.ttc"
        };
        for (String path : candidates) {
            FileHandle handle = Gdx.files.getFileHandle(path, Files.FileType.Absolute);
            if (handle.exists()) {
                return handle;
            }
        }
        return null;
    }

    private String createJavaDemo() {
        StringBuilder builder = new StringBuilder();
        builder.append("package demo;\n\n");
        builder.append("import java.util.ArrayList;\n");
        builder.append("import java.util.HashMap;\n");
        builder.append("import java.util.List;\n");
        builder.append("import java.util.Map;\n\n");
        builder.append("public class SuperEditorDemo {\n");
        builder.append("    private final Map<String, Integer> counters = new HashMap<>();\n");
        builder.append("    private final List<String> logs = new ArrayList<>();\n\n");
        builder.append("    public void boot() {\n");
        builder.append("        for (int round = 0; round < 3; round++) {\n");
        builder.append("            if (round % 2 == 0) {\n");
        builder.append("                logs.add(\"warmup-\" + round);\n");
        builder.append("            } else {\n");
        builder.append("                logs.add(\"hot-\" + round);\n");
        builder.append("            }\n");
        builder.append("        }\n");
        builder.append("    }\n\n");

        for (int i = 0; i < 200; i++) {
            builder.append("    public int computeBlock").append(i).append("(List<String> values) {\n");
            builder.append("        int total = 0;\n");
            builder.append("        for (int index = 0; index < values.size(); index++) {\n");
            builder.append("            String value = values.get(index);\n");
            builder.append("            if (value == null || value.isEmpty()) {\n");
            builder.append("                continue;\n");
            builder.append("            }\n");
            builder.append("            if (value.startsWith(\"item\")) {\n");
            builder.append("                total += value.length() + index;\n");
            builder.append("            } else if (value.contains(\"-\") || value.contains(\"_\")) {\n");
            builder.append("                total += value.replace(\"-\", \"\").replace(\"_\", \"\").length();\n");
            builder.append("            } else {\n");
            builder.append("                total += parseFallback(value, index);\n");
            builder.append("            }\n");
            builder.append("        }\n");
            builder.append("        counters.put(\"block").append(i).append("\", total);\n");
            builder.append("        return total;\n");
            builder.append("    }\n\n");
        }

        builder.append("    private int parseFallback(String value, int index) {\n");
        builder.append("        /* multi-line comment state test */\n");
        builder.append("        int score = value.length() * 3;\n");
        builder.append("        return score > 24 ? score + index : score - index;\n");
        builder.append("    }\n");
        builder.append("}\n");
        return builder.toString();
    }

    private String createKotlinDemo() {
        return ""
            + "package demo.kotlin\n\n"
            + "data class EditorState(val text: String, val version: Int)\n\n"
            + "class KotlinSample {\n"
            + "    private val history = mutableListOf<EditorState>()\n\n"
            + "    fun record(text: String) {\n"
            + "        val snapshot = EditorState(text, history.size)\n"
            + "        history += snapshot\n"
            + "    }\n\n"
            + "    fun render(): String {\n"
            + "        return history.joinToString(separator = \"\\n\") { state ->\n"
            + "            \"${state.version}: ${state.text.trim()}\"\n"
            + "        }\n"
            + "    }\n"
            + "}\n";
    }

    private String createJavaScriptDemo() {
        return ""
            + "export class DebugToolbar {\n"
            + "  constructor(editor) {\n"
            + "    this.editor = editor;\n"
            + "    this.actions = new Map();\n"
            + "  }\n\n"
            + "  register(name, callback) {\n"
            + "    this.actions.set(name, callback);\n"
            + "  }\n\n"
            + "  trigger(name) {\n"
            + "    const action = this.actions.get(name);\n"
            + "    if (!action) {\n"
            + "      console.warn(`Missing action: ${name}`);\n"
            + "      return false;\n"
            + "    }\n"
            + "    return action(this.editor);\n"
            + "  }\n"
            + "}\n";
    }

    private String createPythonDemo() {
        return ""
            + "from dataclasses import dataclass\n\n"
            + "@dataclass\n"
            + "class Snapshot:\n"
            + "    line: int\n"
            + "    column: int\n"
            + "    text: str\n\n"
            + "def summarize(snapshots: list[Snapshot]) -> str:\n"
            + "    rows = []\n"
            + "    for snap in snapshots:\n"
            + "        rows.append(f\"{snap.line}:{snap.column} -> {snap.text.strip()}\")\n"
            + "    return \"\\n\".join(rows)\n\n"
            + "if __name__ == \"__main__\":\n"
            + "    print(summarize([Snapshot(1, 4, \"hello\")]))\n";
    }

    private String createJsonDemo() {
        return ""
            + "{\n"
            + "  \"editor\": {\n"
            + "    \"language\": \"json\",\n"
            + "    \"wrap\": false,\n"
            + "    \"lineNumbersFixed\": true,\n"
            + "    \"features\": [\"highlight\", \"fold\", \"touch\", \"mouse\"]\n"
            + "  },\n"
            + "  \"metrics\": {\n"
            + "    \"cursorLine\": 1,\n"
            + "    \"cursorColumn\": 1,\n"
            + "    \"selection\": null\n"
            + "  }\n"
            + "}\n";
    }

    private String createXmlDemo() {
        return ""
            + "<editor-demo mode=\"touch\" wrap=\"false\">\n"
            + "    <toolbar>\n"
            + "        <action id=\"toggle-wrap\">Toggle wrap</action>\n"
            + "        <action id=\"next-language\">Next sample</action>\n"
            + "    </toolbar>\n"
            + "    <document><![CDATA[\n"
            + "        <code>virtualized rendering</code>\n"
            + "    ]]></document>\n"
            + "</editor-demo>\n";
    }

    private String createPlainTextDemo() {
        return ""
            + "Large-text editor checklist\n"
            + "===========================\n\n"
            + "1. Verify long line horizontal scrolling.\n"
            + "2. Verify touch handles and auto-scroll.\n"
            + "3. Verify disabled vs read-only behavior.\n"
            + "4. Verify fold toggle and syntax switching.\n";
    }

    private final class DebugInteractionListener implements CodeEditorInteractionListener {
        @Override
        public boolean onLongPress(CodeEditor editor, CodeEditorInteractionContext context) {
            lastEventText = "Long press at " + (context.line + 1) + ":" + (context.column + 1)
                + (context.touch ? " (touch)" : " (mouse)");
            showPopupMenu(context, "Touch");
            return true;
        }

        @Override
        public boolean onSecondaryClick(CodeEditor editor, CodeEditorInteractionContext context) {
            lastEventText = "Secondary click at " + (context.line + 1) + ":" + (context.column + 1);
            showPopupMenu(context, "Mouse");
            return true;
        }

        @Override
        public boolean onDoubleClick(CodeEditor editor, CodeEditorInteractionContext context) {
            lastEventText = "Double click at " + (context.line + 1) + ":" + (context.column + 1)
                + (context.touch ? " (touch)" : " (mouse)");
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
