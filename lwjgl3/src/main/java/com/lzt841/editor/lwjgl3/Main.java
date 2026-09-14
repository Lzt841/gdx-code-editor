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
import com.lzt841.editor.CodeDiagnostic;
import com.lzt841.editor.CodeDiagnosticSeverity;
import com.lzt841.editor.CodeEditor;
import com.lzt841.editor.CodeEditorContentChangeEvent;
import com.lzt841.editor.CodeEditorContentListener;
import com.lzt841.editor.CodeEditorPosition;
import com.lzt841.editor.CodeBracketAutoEditStrategy;
import com.lzt841.editor.CodeEditorAction;
import com.lzt841.editor.CodeKeyStroke;
import com.lzt841.editor.CodeIndentStrategy;
import com.lzt841.editor.CodeLineMark;
import com.lzt841.editor.CodeEditorTextRange;
import com.lzt841.editor.completion.CodeCompletionController;
import com.lzt841.editor.completion.CodeCompletionInsertFormat;
import com.lzt841.editor.completion.CodeCompletionItem;
import com.lzt841.editor.completion.CodeCompletionItemKind;
import com.lzt841.editor.completion.CodeCompletionProvider;
import com.lzt841.editor.completion.CodeCompletionRequest;
import com.lzt841.editor.completion.CodeCompletionResponse;
import com.lzt841.editor.hints.CodeHoverController;
import com.lzt841.editor.hints.CodeHoverProvider;
import com.lzt841.editor.hints.CodeHoverResponse;
import com.lzt841.editor.hints.CodeSignatureHelp;
import com.lzt841.editor.hints.CodeSignatureHelpController;
import com.lzt841.editor.hints.CodeSignatureHelpProvider;
import com.lzt841.editor.hints.CodeSignatureHelpResponse;
import com.lzt841.editor.navigation.CodeNavigationController;
import com.lzt841.editor.navigation.CodeNavigationKind;
import com.lzt841.editor.navigation.CodeNavigationListener;
import com.lzt841.editor.navigation.CodeNavigationRequest;
import com.lzt841.editor.navigation.CodeNavigationTarget;
import com.lzt841.editor.navigation.CodeRenameRequest;
import com.lzt841.editor.navigation.WordCodeNavigationProvider;
import com.lzt841.editor.highlight.BuiltinCodeHighlighters;
import com.lzt841.editor.highlight.CodeHighlighter;
import com.lzt841.editor.highlight.CodeSemanticToken;
import com.lzt841.editor.highlight.CodeSemanticTokenType;
import com.lzt841.editor.input.CodeEditorInteractionContext;
import com.lzt841.editor.input.CodeEditorInteractionListener;
import com.lzt841.editor.input.CodeEditorInteractionMode;
import com.lzt841.editor.structure.BraceCodeStructureProvider;
import com.lzt841.editor.structure.CodeStructureProvider;
import com.lzt841.editor.structure.CodeSymbol;
import com.lzt841.editor.structure.CodeSymbolKind;
import com.lzt841.editor.structure.JavaCodeSymbolProvider;
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
    private TextButton wrapIndentButton;
    private TextButton lineNumberButton;
    private TextButton lineNumberFixedButton;
    private TextButton scrollbarButton;
    private TextButton transientHandleButton;
    private TextButton passwordModeButton;
    private TextButton zoomEnabledButton;
    private TextButton overscrollButton;
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
    private TextButton searchWholeWordButton;
    private TextButton searchRegexButton;
    private TextButton searchRangeButton;
    private TextButton zoomInButton;
    private TextButton zoomOutButton;
    private TextButton zoomResetButton;
    private TextButton readOnlyButton;
    private TextButton disabledButton;
    private TextButton popupCopyButton;
    private TextButton popupSearchButton;
    private TextButton popupWrapButton;
    private TextButton popupCloseButton;
    private TextButton clipAreaButton;
    private TextButton selectWordButton;
    private TextButton selectLineButton;
    private TextButton lineMarkDemoButton;
    private boolean lineMarksEnabled;
    private TextButton collapseAllButton;
    private TextButton expandAllButton;
    private TextButton collapseTopLevelButton;
    private TextButton goToSymbolButton;
    private int nextSymbolIndex;
    private TextButton indentStrategyButton;
    private TextButton autoEditButton;
    private TextButton keymapButton;
    private boolean altKeymapEnabled;
    private int indentStrategyIndex;
    private TextButton stress1kButton;
    private TextButton stress10kButton;
    private TextButton stress100kButton;
    private TextButton stressJumpEndButton;
    private TextButton stressTypeBurstButton;
    private TextButton completionButton;
    private TextButton completionDocButton;
    private TextButton hoverButton;
    private TextButton signatureHelpButton;
    private TextButton diagnosticsButton;
    private TextButton semanticHighlightButton;
    private boolean semanticHighlightEnabled;
    private TextButton navigationButton;
    private TextButton goToDefinitionButton;
    private TextButton findReferencesButton;
    private TextButton renameButton;
    private TextField renameField;
    private Label perfLabel;

    // Rolling frame timing, so the effect of an edit on frame cost is visible.
    private final float[] frameSamples = new float[120];
    private int frameSampleIndex;
    private int frameSampleCount;
    private long lastSetTextNanos;
    private long lastTypingBurstNanos;
    private int lastTypingBurstChars;
    private long lastJumpNanos;
    private String cachedSymbolBreadcrumb = "(pending)";
    private int cachedBreadcrumbLine = -1;
    private int cachedBreadcrumbVersion = -1;

    private CodeCompletionController completionController;
    private CodeHoverController hoverController;
    private CodeSignatureHelpController signatureHelpController;
    private CodeNavigationController navigationController;
    private boolean diagnosticsEnabled;

    private DemoProfile[] profiles;
    private int profileIndex;
    private String lastEventText = "Ready";
    private Table popupMenu;
    private Table editorFrame;
    private CodeEditorInteractionContext activePopupContext;
    private final Vector2 popupStagePosition = new Vector2();
    private boolean customClipInsetEnabled;

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
        editor.setTransientCaretHandleEnabled(true);
        editor.setSearchText("value");
        editor.setReadOnly(false);
        editor.setDisabled(false);
        editor.setInteractionMode(CodeEditorInteractionMode.AUTO);
        editor.setInteractionListener(new DebugInteractionListener());
        editor.addContentListener(new CodeEditorContentListener() {
            @Override
            public void onContentChanged(CodeEditor editor, CodeEditorContentChangeEvent event) {
                // Deliberately not event.getText(): that rebuilds the whole document, which is the
                // cost this event was changed to avoid. The range and length are enough here.
                lastEventText = "Content: " + event.type.name()
                    + "  v" + event.documentVersion
                    + "  @" + (event.cursorLine + 1) + ":" + (event.cursorColumn + 1)
                    + "  lines " + (event.startLine + 1) + ".." + (event.endLine + 1)
                    + " (-" + event.removedLineCount + "/+" + event.insertedLineCount + ")"
                    + (event.isWholeDocument() ? " [whole doc]" : "")
                    + "  len=" + event.getTextLength();
            }
        });
        applyProfile(0);

        ScrollPane sidebar = createSidebar();
        root.add(sidebar).minWidth(300f).prefWidth(300f).top().fillY();

        editorFrame = new Table();
        editorFrame.setBackground(new TextureRegionDrawable(new TextureRegion(whitePixel))
            .tint(new Color(0.09f, 0.14f, 0.2f, 1f)));
        editorFrame.pad(10f);
        // Do not clip the frame: selection handles hang below the caret and must remain visible.
        editorFrame.setClip(false);
        editorFrame.add(editor).expand().fill();
        root.add(editorFrame).expand().fill();

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
        stage.setKeyboardFocus(editor);
        refreshDebugPanel();
        applyCustomClipInset();
    }

    @Override
    public void render() {
        recordFrameTime(Gdx.graphics.getDeltaTime());
        refreshDebugPanel();
        if (customClipInsetEnabled) {
            applyCustomClipInset();
        }
        ScreenUtils.clear(0.047f, 0.071f, 0.102f, 1f);
        stage.act(Gdx.graphics.getDeltaTime());
        stage.draw();
    }

    @Override
    public void resize(int width, int height) {
        if (stage != null) {
            stage.getViewport().update(width, height, true);
            if (customClipInsetEnabled) {
                applyCustomClipInset();
            }
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
        perfLabel = new Label("", debugLabelStyle);
        perfLabel.setWrap(true);
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
            "Toggle editor capabilities and samples. Use Select Word/Line to test handles; Custom Clip Inset verifies scissor vs free handles.",
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
        wrapIndentButton = createActionButton("", new Runnable() {
            @Override
            public void run() {
                editor.setWrapContinuationIndentEnabled(!editor.isWrapContinuationIndentEnabled());
            }
        });
        lineNumberButton = createActionButton("", new Runnable() {
            @Override
            public void run() {
                editor.setLineNumbersVisible(!editor.isLineNumbersVisible());
            }
        });
        lineNumberFixedButton = createActionButton("", new Runnable() {
            @Override
            public void run() {
                editor.setLineNumbersFixed(!editor.isLineNumbersFixed());
            }
        });
        scrollbarButton = createActionButton("", new Runnable() {
            @Override
            public void run() {
                editor.setScrollbarsVisible(!editor.isScrollbarsVisible());
            }
        });
        transientHandleButton = createActionButton("", new Runnable() {
            @Override
            public void run() {
                editor.setTransientCaretHandleEnabled(!editor.isTransientCaretHandleEnabled());
            }
        });
        passwordModeButton = createActionButton("", new Runnable() {
            @Override
            public void run() {
                editor.setPasswordMode(!editor.isPasswordMode());
            }
        });
        zoomEnabledButton = createActionButton("", new Runnable() {
            @Override
            public void run() {
                editor.setZoomEnabled(!editor.isZoomEnabled());
            }
        });
        overscrollButton = createActionButton("", new Runnable() {
            @Override
            public void run() {
                editor.setOverscrollEnabled(!editor.isOverscrollEnabled());
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
        searchWholeWordButton = createActionButton("", new Runnable() {
            @Override
            public void run() {
                editor.setSearchWholeWord(!editor.isSearchWholeWord());
                editor.setSearchText(searchField.getText());
                lastEventText = "Search whole word: " + onOff(editor.isSearchWholeWord())
                    + " (" + editor.getSearchMatchCount() + " matches)";
            }
        });
        searchRegexButton = createActionButton("", new Runnable() {
            @Override
            public void run() {
                editor.setSearchRegexEnabled(!editor.isSearchRegexEnabled());
                editor.setSearchText(searchField.getText());
                if (!editor.isSearchPatternValid()) {
                    lastEventText = "Search regex: bad pattern (" + editor.getSearchRegexError() + ")";
                } else {
                    lastEventText = "Search regex: " + onOff(editor.isSearchRegexEnabled())
                        + " (" + editor.getSearchMatchCount() + " matches)";
                }
            }
        });
        searchRangeButton = createActionButton("", new Runnable() {
            @Override
            public void run() {
                if (editor.getSearchRange() != null) {
                    editor.clearSearchRange();
                    lastEventText = "Search range: whole document";
                } else if (editor.setSearchRangeToSelection()) {
                    lastEventText = "Search range: selection ("
                        + editor.getSearchMatchCount() + " matches)";
                } else {
                    lastEventText = "Search range: select something first";
                }
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
        clipAreaButton = createActionButton("", new Runnable() {
            @Override
            public void run() {
                customClipInsetEnabled = !customClipInsetEnabled;
                applyCustomClipInset();
                lastEventText = customClipInsetEnabled
                    ? "Clip: custom inset enabled (text clips, handles stay free)"
                    : "Clip: full editor bounds";
            }
        });
        selectWordButton = createActionButton("Select Word At Cursor", new Runnable() {
            @Override
            public void run() {
                stage.setKeyboardFocus(editor);
                // Desktop AUTO does not enable touch handles; force TOUCH for handle tests.
                if (editor.getInteractionMode() != CodeEditorInteractionMode.TOUCH) {
                    editor.setInteractionMode(CodeEditorInteractionMode.TOUCH);
                }
                editor.selectWordAtCursor();
                lastEventText = editor.hasSelection()
                    ? "Selection: word — drag blue bulb under the caret"
                    : "Selection: no word at cursor";
            }
        });
        selectLineButton = createActionButton("Select Current Line", new Runnable() {
            @Override
            public void run() {
                stage.setKeyboardFocus(editor);
                if (editor.getInteractionMode() != CodeEditorInteractionMode.TOUCH) {
                    editor.setInteractionMode(CodeEditorInteractionMode.TOUCH);
                }
                editor.selectLineAtCursor();
                lastEventText = editor.hasSelection()
                    ? "Selection: line — drag blue bulb under the caret"
                    : "Selection: empty line";
            }
        });

        lineMarkDemoButton = createActionButton("", new Runnable() {
            @Override
            public void run() {
                toggleLineMarks();
            }
        });
        collapseAllButton = createActionButton("Collapse All Folds", new Runnable() {
            @Override
            public void run() {
                lastEventText = "Collapsed " + editor.collapseAllFolds() + " regions";
            }
        });
        expandAllButton = createActionButton("Expand All Folds", new Runnable() {
            @Override
            public void run() {
                lastEventText = "Expanded " + editor.expandAllFolds() + " regions";
            }
        });
        collapseTopLevelButton = createActionButton("Collapse Depth <= 1", new Runnable() {
            @Override
            public void run() {
                lastEventText = "Collapsed " + editor.collapseAllFolds(1) + " top-level regions";
            }
        });
        goToSymbolButton = createActionButton("Go to Next Symbol", new Runnable() {
            @Override
            public void run() {
                com.badlogic.gdx.utils.Array<CodeSymbol> found = editor.findSymbols("");
                if (found.size == 0) {
                    lastEventText = "No symbols in this sample";
                    return;
                }
                CodeSymbol symbol = found.get(nextSymbolIndex % found.size);
                nextSymbolIndex++;
                editor.goToSymbol(symbol);
                lastEventText = "Go to " + symbol.kind + " " + symbol.label();
            }
        });

        keymapButton = createActionButton("", new Runnable() {
            @Override
            public void run() {
                toggleKeymap();
            }
        });
        autoEditButton = createActionButton("", new Runnable() {
            @Override
            public void run() {
                toggleAutoEdit();
            }
        });
        indentStrategyButton = createActionButton("", new Runnable() {
            @Override
            public void run() {
                cycleIndentStrategy();
            }
        });

        stress1kButton = createActionButton("Load 1,000 Lines", new Runnable() {
            @Override
            public void run() {
                loadGeneratedText(1000);
            }
        });
        stress10kButton = createActionButton("Load 10,000 Lines", new Runnable() {
            @Override
            public void run() {
                loadGeneratedText(10000);
            }
        });
        stress100kButton = createActionButton("Load 100,000 Lines", new Runnable() {
            @Override
            public void run() {
                loadGeneratedText(100000);
            }
        });
        stressJumpEndButton = createActionButton("Jump To Last Line (timed)", new Runnable() {
            @Override
            public void run() {
                // Worst case for lazy highlighting: the lexer state cache has to reach the last line.
                int last = Math.max(0, editor.getLineCount() - 1);
                long start = System.nanoTime();
                editor.setCursorPosition(last, editor.getLineLength(last));
                editor.scrollLineToCenter(last);
                lastJumpNanos = System.nanoTime() - start;
                lastEventText = "Jumped to line " + (last + 1) + " in " + formatMillis(lastJumpNanos);
            }
        });
        stressTypeBurstButton = createActionButton("Type 200 Chars (timed)", new Runnable() {
            @Override
            public void run() {
                runTypingBurst(200);
            }
        });
        completionButton = createActionButton("", new Runnable() {
            @Override
            public void run() {
                toggleCompletion();
            }
        });
        completionDocButton = createActionButton("", new Runnable() {
            @Override
            public void run() {
                toggleCompletionDocumentation();
            }
        });
        hoverButton = createActionButton("", new Runnable() {
            @Override
            public void run() {
                toggleHover();
            }
        });
        signatureHelpButton = createActionButton("", new Runnable() {
            @Override
            public void run() {
                toggleSignatureHelp();
            }
        });
        diagnosticsButton = createActionButton("", new Runnable() {
            @Override
            public void run() {
                toggleDiagnostics();
            }
        });
        navigationButton = createActionButton("", new Runnable() {
            @Override
            public void run() {
                toggleNavigation();
            }
        });
        goToDefinitionButton = createActionButton("Go to Definition (F12)", new Runnable() {
            @Override
            public void run() {
                if (navigationController != null) {
                    navigationController.goToDefinition();
                } else {
                    lastEventText = "Navigation: turn it on first";
                }
            }
        });
        findReferencesButton = createActionButton("Find References (Shift-F12)", new Runnable() {
            @Override
            public void run() {
                if (navigationController != null) {
                    navigationController.findReferences();
                } else {
                    lastEventText = "Navigation: turn it on first";
                }
            }
        });
        renameField = new TextField("", debugTextFieldStyle);
        renameField.setMessageText("New name");
        renameField.setTextFieldFilter(new TextField.TextFieldFilter() {
            @Override
            public boolean acceptChar(TextField textField, char c) {
                return c != '\r';
            }
        });
        renameButton = createActionButton("Rename (Shift-F6)", new Runnable() {
            @Override
            public void run() {
                if (navigationController == null) {
                    lastEventText = "Navigation: turn it on first";
                    return;
                }
                String name = renameField.getText();
                if (name == null || name.isEmpty()) {
                    lastEventText = "Rename: type a new name first";
                    return;
                }
                navigationController.renameTo(name);
            }
        });
        semanticHighlightButton = createActionButton("", new Runnable() {
            @Override
            public void run() {
                toggleSemanticHighlight();
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
                if (editor.isDisabled()) {
                    editor.setDisabled(false);
                }
                if (editor.isReadOnly()) {
                    editor.setReadOnly(false);
                }
                editor.setText(profiles[profileIndex].text);
                lastEventText = "Cursor/sample reset";
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
        sidebar.add(new Label("Clip / Handles", debugTitleStyle)).padBottom(2f);
        sidebar.row();
        sidebar.add(clipAreaButton);
        sidebar.row();
        sidebar.add(selectWordButton);
        sidebar.row();
        sidebar.add(selectLineButton);
        sidebar.row();
        sidebar.add(transientHandleButton);
        sidebar.row();
        sidebar.add(new Label("Display", debugTitleStyle)).padBottom(2f);
        sidebar.row();
        sidebar.add(wrapButton);
        sidebar.row();
        sidebar.add(wrapIndentButton);
        sidebar.row();
        sidebar.add(lineNumberButton);
        sidebar.row();
        sidebar.add(lineNumberFixedButton);
        sidebar.row();
        sidebar.add(scrollbarButton);
        sidebar.row();
        sidebar.add(passwordModeButton);
        sidebar.row();
        sidebar.add(zoomEnabledButton);
        sidebar.row();
        sidebar.add(overscrollButton);
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
        sidebar.add(searchWholeWordButton);
        sidebar.row();
        sidebar.add(searchRegexButton);
        sidebar.row();
        sidebar.add(searchRangeButton);
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
        sidebar.add(indentStrategyButton);
        sidebar.row();
        sidebar.add(autoEditButton);
        sidebar.row();
        sidebar.add(keymapButton);
        sidebar.row();
        sidebar.add(lineMarkDemoButton);
        sidebar.row();
        sidebar.add(collapseAllButton);
        sidebar.row();
        sidebar.add(collapseTopLevelButton);
        sidebar.row();
        sidebar.add(expandAllButton);
        sidebar.row();
        sidebar.add(goToSymbolButton).padBottom(14f);
        sidebar.row();
        sidebar.add(new Label("Stress Test", debugTitleStyle)).padBottom(2f);
        sidebar.row();
        sidebar.add(stress1kButton);
        sidebar.row();
        sidebar.add(stress10kButton);
        sidebar.row();
        sidebar.add(stress100kButton);
        sidebar.row();
        sidebar.add(stressJumpEndButton);
        sidebar.row();
        sidebar.add(stressTypeBurstButton).padBottom(14f);
        sidebar.row();
        sidebar.add(new Label("Perf", debugTitleStyle)).padBottom(2f);
        sidebar.row();
        sidebar.add(createInfoCard(perfLabel, 92f));
        sidebar.row();
        sidebar.add(new Label("Tooling", debugTitleStyle)).padBottom(2f);
        sidebar.row();
        sidebar.add(completionButton);
        sidebar.row();
        sidebar.add(completionDocButton);
        sidebar.row();
        sidebar.add(hoverButton);
        sidebar.row();
        sidebar.add(signatureHelpButton);
        sidebar.row();
        sidebar.add(navigationButton);
        sidebar.row();
        sidebar.add(goToDefinitionButton);
        sidebar.row();
        sidebar.add(findReferencesButton);
        sidebar.row();
        sidebar.add(renameField);
        sidebar.row();
        sidebar.add(renameButton);
        sidebar.row();
        sidebar.add(diagnosticsButton);
        sidebar.row();
        sidebar.add(semanticHighlightButton).padBottom(14f);
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
                } else if (editor.copySelection()) {
                    lastEventText = "Popup: copied selection";
                } else {
                    lastEventText = "Popup: clipboard refused the selection";
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
        wrapIndentButton.setText("Wrap Indent: " + onOff(editor.isWrapContinuationIndentEnabled()));
        lineNumberButton.setText("Line Numbers Visible: " + onOff(editor.isLineNumbersVisible()));
        lineNumberFixedButton.setText("Line Numbers Fixed: " + onOff(editor.isLineNumbersFixed()));
        scrollbarButton.setText("Scrollbars Visible: " + onOff(editor.isScrollbarsVisible()));
        transientHandleButton.setText("Transient Handle: " + onOff(editor.isTransientCaretHandleEnabled()));
        passwordModeButton.setText("Password Mode: " + onOff(editor.isPasswordMode()));
        zoomEnabledButton.setText("Pinch Zoom Enabled: " + onOff(editor.isZoomEnabled()));
        overscrollButton.setText("Overscroll: " + onOff(editor.isOverscrollEnabled()));
        rainbowBracketButton.setText("Rainbow Brackets: " + onOff(editor.isRainbowBracketsEnabled()));
        rainbowGuideButton.setText("Rainbow Guides: " + onOff(editor.isRainbowGuidesEnabled()));
        clipAreaButton.setText("Custom Clip Inset: " + onOff(customClipInsetEnabled));
        applySearchButton.setText("Apply Search");
        previousMatchButton.setText("Previous Match");
        nextMatchButton.setText("Next Match");
        searchCaseButton.setText("Search Case: " + (editor.isSearchCaseSensitive() ? "Sensitive" : "Ignore Case"));
        searchWholeWordButton.setText("Whole Word: " + onOff(editor.isSearchWholeWord()));
        searchRegexButton.setText("Regex: " + onOff(editor.isSearchRegexEnabled())
            + (editor.isSearchPatternValid() ? "" : " (bad)"));
        searchRangeButton.setText("Search In: " + (editor.getSearchRange() != null ? "Selection" : "Document"));
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
        lineMarkDemoButton.setText("Demo Line Marks: " + onOff(lineMarksEnabled)
            + (lineMarksEnabled ? " (" + editor.getLineMarks().size + ")" : ""));
        indentStrategyButton.setText("Indent: " + describeIndentStrategy()
            + "   (Tab / Shift-Tab on a selection)");
        autoEditButton.setText("Auto Close Brackets: " + onOff(editor.getAutoEditStrategy() != null));
        keymapButton.setText(altKeymapEnabled
            ? "Keymap: Ctrl-D undo, F3 fold"
            : "Keymap: defaults");
        completionButton.setText("Completion: " + onOff(completionController != null));
        completionDocButton.setText("  Doc Panel: " + (completionController == null
            ? "n/a"
            : onOff(completionController.isDocumentationEnabled())));
        hoverButton.setText("Hover Tooltip: " + onOff(hoverController != null));
        signatureHelpButton.setText("Parameter Hints: " + onOff(signatureHelpController != null));
        navigationButton.setText("Navigation: " + onOff(navigationController != null)
            + (navigationController == null ? "" : "  F12 / Shift-F12 / Shift-F6"));
        diagnosticsButton.setText("Demo Diagnostics: " + onOff(diagnosticsEnabled));
        semanticHighlightButton.setText("Semantic Tokens: " + onOff(semanticHighlightEnabled));

        // Read this before building the string: describing the symbol path flushes the pending pass, so
        // evaluating it inline would always report "Off" and hide the very thing this line is for.
        boolean structurePending = editor.isStructureAnalysisPending();
        refreshSymbolBreadcrumb(structurePending);

        perfLabel.setText(
            "Lines: " + editor.getLineCount()
                + "   Chars: " + editor.getTextLength() + "\n"
                + "Frame avg: " + String.format(java.util.Locale.ROOT, "%.2f ms", averageFrameMillis())
                + "   worst: " + String.format(java.util.Locale.ROOT, "%.2f ms", worstFrameMillis()) + "\n"
                + "setText: " + formatMillis(lastSetTextNanos) + "\n"
                + "Typing burst: " + (lastTypingBurstChars == 0 ? "(none)"
                    : lastTypingBurstChars + " chars in " + formatMillis(lastTypingBurstNanos)) + "\n"
                + "Jump to end: " + (lastJumpNanos == 0 ? "(none)" : formatMillis(lastJumpNanos)) + "\n"
                + "Structure pass pending: " + onOff(structurePending) + "\n"
                + "Visible lines: " + (editor.getFirstVisibleDocumentLine() + 1)
                + ".." + (editor.getLastVisibleDocumentLine() + 1) + "\n"
                + "Lexer valid to: " + editor.getHighlightResyncLine()
                + " / populated " + editor.getHighlightPopulatedLine() + "\n"
                + "Materialized layouts: " + editor.getMaterializedLineCount() + "\n"
                + "Folds: " + editor.getFoldRegionCount() + " regions, "
                + editor.getCollapsedFoldCount() + " collapsed\n"
                + "Symbols: " + cachedSymbolBreadcrumb + "\n"
                + "Semantic tokens: " + (editor.getSemanticTokenVersion() < 0
                    ? "(none pushed)"
                    : editor.getSemanticTokenCount() + " @v" + editor.getSemanticTokenVersion()
                        + (editor.isSemanticTokensStale() ? " STALE" : " fresh")) + "\n"
                + "Row mapping: " + (editor.isRowMappingIdentity() ? "identity (fast)" : "array")
                + "   visual rows " + editor.getVisualRowCount()
                + (editor.isWrapRemeasurePending() ? "   wrap re-measure pending" : "")
        );

        statusLabel.setText(
            "Sample: " + profile.name + "\n"
                + "Highlighter: " + profile.description + "\n"
                + "Interaction: " + editor.getInteractionMode().name() + "\n"
                + "Custom clip: " + onOff(customClipInsetEnabled)
                + "   Clip enabled: " + onOff(editor.isClipAreaEnabled()) + "\n"
                + "Disabled: " + onOff(editor.isDisabled()) + "   Read only: " + onOff(editor.isReadOnly()) + "\n"
                + "Wrap: " + onOff(editor.isWrapEnabled())
                + "   indent: " + onOff(editor.isWrapContinuationIndentEnabled())
                + "   Line numbers: " + onOff(editor.isLineNumbersVisible())
                + "   Fixed gutter: " + onOff(editor.isLineNumbersFixed()) + "\n"
                + "Scrollbars: " + onOff(editor.isScrollbarsVisible())
                + "   Transient handle: " + onOff(editor.isTransientCaretHandleEnabled()) + "\n"
                + "Password mode: " + onOff(editor.isPasswordMode()) + "\n"
                + "Pinch zoom: " + onOff(editor.isZoomEnabled())
                + "   Overscroll: " + onOff(editor.isOverscrollEnabled()) + "\n"
                + "Rainbow brackets: " + onOff(editor.isRainbowBracketsEnabled())
                + "   Rainbow guides: " + onOff(editor.isRainbowGuidesEnabled()) + "\n"
                + "Search: " + (editor.getSearchText().isEmpty() ? "(none)" : editor.getSearchText())
                + "  " + (editor.hasCurrentSearchMatch() ? editor.getCurrentSearchMatchOrdinal() : 0)
                + "/" + editor.getSearchMatchCount() + "\n"
                + "Whole word: " + onOff(editor.isSearchWholeWord())
                + "   Regex: " + onOff(editor.isSearchRegexEnabled())
                + "   In: " + (editor.getSearchRange() != null ? "selection" : "document") + "\n"
                + "Zoom: " + formatZoom(editor.getZoomScale()) + "\n"
                + "Handle test: Select Word/Line, then drag blue bulbs"
        );
        metricsLabel.setText(
            "Lines: " + editor.getLineCount() + "\n"
                + "Cursor: " + (editor.getCursorLine() + 1) + ":" + (editor.getCursorColumn() + 1) + "\n"
                + "Selection: " + onOff(editor.hasSelection()) + "\n"
                + "Undo/Redo: " + onOff(editor.canUndo()) + "/" + onOff(editor.canRedo()) + "\n"
                + "Matches: " + editor.getSearchMatchCount() + "\n"
                + "Tips: double-click word, drag handles, toggle custom clip, long-press for menu."
        );
        eventLabel.setText(lastEventText);
    }

    /**
     * Generates Java-like text of a given line count and times {@code setText}. The content is varied
     * on purpose — nested braces, strings, comments, long lines — so highlighting, folding and the
     * horizontal extent all get exercised rather than a best case.
     */
    private void loadGeneratedText(int lineCount) {
        StringBuilder builder = new StringBuilder(lineCount * 42);
        builder.append("package com.example.generated;\n\n");
        builder.append("/** Generated stress-test source with ").append(lineCount).append(" lines. */\n");
        builder.append("public class Generated {\n");
        int line = 4;
        int method = 0;
        while (line < lineCount - 1) {
            builder.append("    /** Method ").append(method).append(" documentation. */\n");
            builder.append("    public int method").append(method).append("(int seed) {\n");
            builder.append("        String label = \"value-").append(method).append("\";\n");
            builder.append("        int total = seed * ").append(method + 1).append(";\n");
            builder.append("        if (total > 100) {\n");
            builder.append("            for (int i = 0; i < total; i++) {\n");
            builder.append("                total += (i % 3 == 0) ? i : -i; // adjust\n");
            builder.append("            }\n");
            builder.append("        }\n");
            if ((method % 7) == 0) {
                // One deliberately long line every so often, to test horizontal extent.
                builder.append("        String wide = \"")
                    .append("qwertyuiopasdfghjklzxcvbnm0123456789")
                    .append("qwertyuiopasdfghjklzxcvbnm0123456789")
                    .append("qwertyuiopasdfghjklzxcvbnm0123456789\";\n");
                line++;
            }
            builder.append("        return total + label.length();\n");
            builder.append("    }\n\n");
            line += 11;
            method++;
        }
        builder.append("}\n");

        long start = System.nanoTime();
        editor.setText(builder.toString());
        lastSetTextNanos = System.nanoTime() - start;
        editor.setCursorPosition(0, 0);
        resetFrameSamples();
        lastEventText = "Loaded " + editor.getLineCount() + " lines in "
            + formatMillis(lastSetTextNanos);
    }

    /**
     * Inserts characters one at a time through the document API and reports the total, which is the
     * closest headless approximation of holding down a key.
     */
    private void runTypingBurst(int characterCount) {
        if (editor.getLineCount() == 0) {
            return;
        }
        int middle = editor.getLineCount() / 2;
        editor.setCursorPosition(middle, 0);
        long start = System.nanoTime();
        for (int i = 0; i < characterCount; i++) {
            editor.insertTextAtCursor("x");
        }
        lastTypingBurstNanos = System.nanoTime() - start;
        lastTypingBurstChars = characterCount;
        lastEventText = "Typed " + characterCount + " chars in " + formatMillis(lastTypingBurstNanos)
            + " (" + formatMillis(lastTypingBurstNanos / Math.max(1, characterCount)) + " each)";
    }

    private void resetFrameSamples() {
        frameSampleIndex = 0;
        frameSampleCount = 0;
    }

    private void recordFrameTime(float deltaSeconds) {
        frameSamples[frameSampleIndex] = deltaSeconds;
        frameSampleIndex = (frameSampleIndex + 1) % frameSamples.length;
        if (frameSampleCount < frameSamples.length) {
            frameSampleCount++;
        }
    }

    private float averageFrameMillis() {
        if (frameSampleCount == 0) {
            return 0f;
        }
        float total = 0f;
        for (int i = 0; i < frameSampleCount; i++) {
            total += frameSamples[i];
        }
        return total / frameSampleCount * 1000f;
    }

    private float worstFrameMillis() {
        float worst = 0f;
        for (int i = 0; i < frameSampleCount; i++) {
            worst = Math.max(worst, frameSamples[i]);
        }
        return worst * 1000f;
    }

    private static String formatMillis(long nanos) {
        return String.format(java.util.Locale.ROOT, "%.2f ms", nanos / 1_000_000.0);
    }

    private void toggleCompletion() {
        if (completionController != null) {
            completionController.uninstall();
            completionController = null;
            return;
        }
        completionController = new CodeCompletionController(editor, new DemoCompletionProvider());
        completionController.install();
    }

    /** Turns the documentation side panel on and off, to compare the list with and without it. */
    private void toggleCompletionDocumentation() {
        if (completionController == null) {
            return;
        }
        completionController.setDocumentationEnabled(!completionController.isDocumentationEnabled());
    }

    private void toggleHover() {
        if (hoverController != null) {
            hoverController.uninstall();
            hoverController = null;
            return;
        }
        hoverController = new CodeHoverController(editor, new DemoHoverProvider());
        hoverController.install();
    }

    private void toggleSignatureHelp() {
        if (signatureHelpController != null) {
            signatureHelpController.uninstall();
            signatureHelpController = null;
            return;
        }
        signatureHelpController = new CodeSignatureHelpController(editor, new DemoSignatureHelpProvider());
        signatureHelpController.install();
    }

    /**
     * Word-level definition, references and rename. A real language backend would replace the
     * {@link WordCodeNavigationProvider}; the listener here is the host-side UI, which this library
     * does not draw.
     */
    private void toggleNavigation() {
        if (navigationController != null) {
            navigationController.uninstall();
            navigationController = null;
            lastEventText = "Navigation: off";
            return;
        }
        navigationController = new CodeNavigationController(editor, new WordCodeNavigationProvider());
        navigationController.setListener(new DemoNavigationListener());
        navigationController.install();
        lastEventText = "Navigation: F12 definition, Shift-F12 references, Shift-F6 rename";
    }

    /** Marks every occurrence of "value" so the squiggles and gutter ticks are visible. */
    private void toggleSemanticHighlight() {
        semanticHighlightEnabled = !semanticHighlightEnabled;
        if (!semanticHighlightEnabled) {
            editor.clearSemanticTokens();
            lastEventText = "Semantic tokens cleared";
            return;
        }
        long started = System.nanoTime();
        // Captured before the analysis, and handed back with the result. Nothing here is async, so the
        // push cannot actually be rejected — but this is the call shape a real producer needs, and
        // writing it the other way round in a demo is how the version parameter ends up unused in
        // everyone's integration.
        int version = editor.getDocumentVersion();
        com.badlogic.gdx.utils.Array<CodeSemanticToken> tokens = analyzeSemanticTokens();
        if (!editor.setSemanticTokens(tokens, version)) {
            lastEventText = "Semantic push rejected: document moved on";
            semanticHighlightEnabled = false;
            return;
        }
        lastEventText = "Pushed " + tokens.size + " semantic tokens in "
            + formatMillis(System.nanoTime() - started);
    }

    /**
     * A deliberately small stand-in for a resolver: it uses the symbol tree to learn which names are
     * fields and which are methods, then colours every occurrence of those names accordingly, treating
     * anything declared inside a method body as a local.
     *
     * <p>This is not a Java front end and does not pretend to be. What it does demonstrate is the thing
     * the lexical highlighter cannot do at any speed: the same identifier is coloured as a field on one
     * line and as a local on another, because the answer depends on scope rather than on the characters.
     * A real integration would put an LSP server or a compiler here.
     */
    private com.badlogic.gdx.utils.Array<CodeSemanticToken> analyzeSemanticTokens() {
        com.badlogic.gdx.utils.Array<CodeSemanticToken> tokens = new com.badlogic.gdx.utils.Array<>();
        com.badlogic.gdx.utils.Array<CodeSymbol> all = editor.findSymbols("");
        if (all.size == 0) {
            return tokens;
        }

        java.util.HashSet<String> fields = new java.util.HashSet<String>();
        java.util.HashSet<String> methods = new java.util.HashSet<String>();
        java.util.HashSet<String> types = new java.util.HashSet<String>();
        for (int i = 0; i < all.size; i++) {
            CodeSymbol symbol = all.get(i);
            if (symbol.name == null || symbol.name.isEmpty()) {
                continue;
            }
            switch (symbol.kind) {
                case FIELD:
                case PROPERTY:
                case CONSTANT:
                    fields.add(symbol.name);
                    break;
                case METHOD:
                case FUNCTION:
                case CONSTRUCTOR:
                    methods.add(symbol.name);
                    break;
                case CLASS:
                case INTERFACE:
                case ENUM:
                case STRUCT:
                case ANNOTATION:
                    types.add(symbol.name);
                    break;
                default:
                    break;
            }
        }

        // Locals and parameters, per method body. Scanning bodies rather than the whole file is what
        // makes a name a local *here* and a field *there*, which is the point of the exercise.
        java.util.HashMap<String, int[]> localScopes = new java.util.HashMap<String, int[]>();
        java.util.HashSet<String> parameters = new java.util.HashSet<String>();
        for (int i = 0; i < all.size; i++) {
            CodeSymbol symbol = all.get(i);
            if (symbol.kind != CodeSymbolKind.METHOD
                && symbol.kind != CodeSymbolKind.FUNCTION
                && symbol.kind != CodeSymbolKind.CONSTRUCTOR) {
                continue;
            }
            collectParameterNames(symbol, parameters);
            collectLocalNames(symbol, localScopes);
        }

        int limit = Math.min(editor.getLineCount(), 4000);
        for (int line = 0; line < limit; line++) {
            String text = editor.getLineText(line);
            int i = 0;
            while (i < text.length()) {
                if (!Character.isJavaIdentifierStart(text.charAt(i))) {
                    i++;
                    continue;
                }
                int start = i;
                while (i < text.length() && Character.isJavaIdentifierPart(text.charAt(i))) {
                    i++;
                }
                String name = text.substring(start, i);
                CodeSemanticTokenType type = classifyIdentifier(
                    name, line, localScopes, parameters, fields, methods, types);
                if (type != null) {
                    tokens.add(new CodeSemanticToken(line, start, i, type));
                }
            }
        }
        return tokens;
    }

    /**
     * Resolution order: a local in scope shadows a field of the same name, which is the distinction the
     * whole feature exists to draw. Parameters are checked before fields for the same reason.
     */
    private CodeSemanticTokenType classifyIdentifier(
        String name,
        int line,
        java.util.HashMap<String, int[]> localScopes,
        java.util.HashSet<String> parameters,
        java.util.HashSet<String> fields,
        java.util.HashSet<String> methods,
        java.util.HashSet<String> types
    ) {
        int[] scope = localScopes.get(name);
        if (scope != null && line >= scope[0] && line <= scope[1]) {
            return CodeSemanticTokenType.VARIABLE;
        }
        if (parameters.contains(name)) {
            return CodeSemanticTokenType.PARAMETER;
        }
        if (fields.contains(name)) {
            return CodeSemanticTokenType.PROPERTY;
        }
        if (methods.contains(name)) {
            return CodeSemanticTokenType.METHOD;
        }
        if (types.contains(name)) {
            return CodeSemanticTokenType.CLASS;
        }
        return null;
    }

    /** Names between the parentheses of a method's declaration line, taking the last word of each part. */
    private void collectParameterNames(CodeSymbol method, java.util.HashSet<String> out) {
        if (method.selectionStartLine < 0 || method.selectionStartLine >= editor.getLineCount()) {
            return;
        }
        String header = editor.getLineText(method.selectionStartLine);
        int open = header.indexOf('(');
        int close = header.lastIndexOf(')');
        if (open < 0 || close <= open + 1) {
            return;
        }
        String[] parts = header.substring(open + 1, close).split(",");
        for (String part : parts) {
            String trimmed = part.trim();
            int space = trimmed.lastIndexOf(' ');
            String candidate = space < 0 ? trimmed : trimmed.substring(space + 1).trim();
            if (!candidate.isEmpty() && Character.isJavaIdentifierStart(candidate.charAt(0))) {
                out.add(candidate);
            }
        }
    }

    /** Names on {@code Type name =} lines inside a method body, recorded with that body's line range. */
    private void collectLocalNames(CodeSymbol method, java.util.HashMap<String, int[]> out) {
        int from = Math.max(0, method.startLine);
        int to = Math.min(editor.getLineCount() - 1, method.endLine);
        for (int line = from; line <= to; line++) {
            String text = editor.getLineText(line);
            int equals = text.indexOf('=');
            if (equals <= 0) {
                continue;
            }
            int paren = text.indexOf('(');
            // A '(' before the '=' means the assignment target is a call or a cast, not a declaration.
            if (paren >= 0 && paren < equals) {
                continue;
            }
            String before = text.substring(0, equals).trim();
            int space = before.lastIndexOf(' ');
            if (space < 0) {
                continue;
            }
            String candidate = before.substring(space + 1).trim();
            if (candidate.isEmpty() || !Character.isJavaIdentifierStart(candidate.charAt(0))) {
                continue;
            }
            out.put(candidate, new int[] {from, to});
        }
    }

    private void toggleDiagnostics() {
        diagnosticsEnabled = !diagnosticsEnabled;
        if (!diagnosticsEnabled) {
            editor.clearDiagnostics();
            return;
        }
        com.badlogic.gdx.utils.Array<CodeDiagnostic> found = new com.badlogic.gdx.utils.Array<>();
        int limit = Math.min(editor.getLineCount(), 4000);
        for (int line = 0; line < limit; line++) {
            String text = editor.getLineText(line);
            int index = text.indexOf("value");
            while (index >= 0) {
                CodeDiagnosticSeverity severity = found.size % 3 == 0
                    ? CodeDiagnosticSeverity.ERROR
                    : (found.size % 3 == 1 ? CodeDiagnosticSeverity.WARNING : CodeDiagnosticSeverity.INFORMATION);
                found.add(new CodeDiagnostic(
                    line, index, line, index + 5, severity,
                    "Demo diagnostic on \"value\" at line " + (line + 1)
                ));
                index = text.indexOf("value", index + 5);
            }
        }
        editor.setDiagnostics(found);
        lastEventText = "Added " + found.size + " demo diagnostics";
    }

    /** Completion over Java keywords plus the identifiers already present in the document. */
    private final class DemoCompletionProvider implements CodeCompletionProvider {
        private final String[] keywords = {
            "abstract", "assert", "boolean", "break", "byte", "case", "catch", "char", "class",
            "continue", "default", "do", "double", "else", "enum", "extends", "final", "finally",
            "float", "for", "if", "implements", "import", "instanceof", "int", "interface", "long",
            "new", "package", "private", "protected", "public", "return", "short", "static",
            "super", "switch", "this", "throw", "throws", "try", "void", "while"
        };

        @Override
        public void provide(CodeCompletionRequest request, CodeCompletionResponse response) {
            com.badlogic.gdx.utils.Array<CodeCompletionItem> items = new com.badlogic.gdx.utils.Array<>();
            for (String keyword : keywords) {
                items.add(new CodeCompletionItem(
                    keyword,
                    keyword,
                    "keyword",
                    documentationFor(keyword),
                    CodeCompletionItemKind.KEYWORD,
                    0,
                    null,
                    null
                ));
            }
            addSnippets(items);
            // Identifiers from nearby lines, so the list reflects the actual document.
            int from = Math.max(0, request.line - 200);
            int to = Math.min(request.editor.getLineCount() - 1, request.line + 200);
            com.badlogic.gdx.utils.ObjectSet<String> seen = new com.badlogic.gdx.utils.ObjectSet<>();
            for (int line = from; line <= to; line++) {
                String text = request.editor.getLineText(line);
                int index = 0;
                while (index < text.length()) {
                    if (!Character.isJavaIdentifierStart(text.charAt(index))) {
                        index++;
                        continue;
                    }
                    int end = index;
                    while (end < text.length() && Character.isJavaIdentifierPart(text.charAt(end))) {
                        end++;
                    }
                    String word = text.substring(index, end);
                    if (word.length() > 2 && seen.add(word)) {
                        items.add(new CodeCompletionItem(
                            word,
                            word,
                            "local",
                            "Identifier collected from line " + (line + 1) + " of the open document.\n"
                                + "The demo provider scans 200 lines either side of the caret, so this\n"
                                + "list changes as you move around the file.",
                            CodeCompletionItemKind.VARIABLE,
                            1,
                            null,
                            null
                        ));
                    }
                    index = end;
                }
            }
            response.complete(items);
        }

        @Override
        public boolean isTriggerCharacter(char character) {
            return character == '.';
        }

        /**
         * Snippet entries, to exercise placeholder tabbing.
         *
         * <p>{@code fori} is the one worth trying first: the three {@code $1}s are mirrors, so renaming
         * the counter at the first stop rewrites the condition and the increment as you type. Priority
         * 2 floats them above the keywords they share a prefix with, so {@code for} then Tab reaches the
         * snippet rather than the bare keyword.
         */
        private void addSnippets(com.badlogic.gdx.utils.Array<CodeCompletionItem> items) {
            items.add(snippetItem(
                "fori",
                "for (int ${1:i} = 0; $1 < ${2:count}; $1++) {\n\t$0\n}",
                "for loop with mirrored counter"
            ));
            items.add(snippetItem(
                "foreach",
                "for (${1:Object} ${2:item} : ${3:items}) {\n\t$0\n}",
                "enhanced for loop"
            ));
            items.add(snippetItem(
                "ifelse",
                "if (${1:condition}) {\n\t$2\n} else {\n\t$0\n}",
                "if / else"
            ));
            items.add(snippetItem(
                "trycatch",
                "try {\n\t$1\n} catch (${2:Exception} ${3:e}) {\n\t$0\n}",
                "try / catch"
            ));
            items.add(snippetItem(
                "sout",
                "System.out.println(${1:\"$TM_SELECTED_TEXT\"});$0",
                "println, seeded with the selection"
            ));
            items.add(snippetItem(
                "visibility",
                "${1|public,private,protected|} ${2:void} ${3:name}() {\n\t$0\n}",
                "method, first choice preselected"
            ));
        }

        private CodeCompletionItem snippetItem(String label, String body, String detail) {
            return new CodeCompletionItem(
                label,
                body,
                detail,
                "Snippet. Accepting it inserts the body and selects the first placeholder;\n"
                    + "Tab and Shift-Tab move between stops, Escape leaves the text as it is.\n"
                    + "Stops that share a number mirror each other as you type.\n\n"
                    + body,
                CodeCompletionItemKind.SNIPPET,
                2,
                null,
                null,
                CodeCompletionInsertFormat.SNIPPET
            );
        }

        /** Blurbs for the documentation side panel; anything unlisted gets a generic line. */
        private String documentationFor(String keyword) {
            if ("class".equals(keyword)) {
                return "class Name { ... }\n\nDeclares a reference type. A top-level class may be\n"
                    + "public or package-private, and only one public class is allowed per file.";
            }
            if ("for".equals(keyword)) {
                return "for (init; condition; update) { ... }\n\nAlso the enhanced form\n"
                    + "for (T item : iterable) { ... }, which works on arrays and Iterable.";
            }
            if ("synchronized".equals(keyword)) {
                return "synchronized (monitor) { ... }\n\nAcquires the monitor for the duration of the\n"
                    + "block. As a method modifier the monitor is this, or the Class for a static method.";
            }
            if ("instanceof".equals(keyword)) {
                return "value instanceof Type\n\nTrue when value is non-null and assignable to Type.";
            }
            return "Java language keyword: " + keyword + ".\n\nThis text comes from the demo provider "
                + "and is rendered by the completion documentation side panel.";
        }
    }

    /** Hover showing the word under the pointer, plus any diagnostics there. */
    private final class DemoHoverProvider implements CodeHoverProvider {
        @Override
        public void provideHover(CodeEditor source, CodeEditorPosition position, CodeHoverResponse response) {
            StringBuilder builder = new StringBuilder();
            CodeEditorTextRange word = source.getWordRangeAt(position.line, position.column);
            if (word != null) {
                builder.append(source.getTextRange(word)).append("\n");
                builder.append("line ").append(position.line + 1)
                    .append(", column ").append(position.column + 1)
                    .append(", offset ").append(position.offset);
            }
            CodeDiagnostic diagnostic = source.getPrimaryDiagnosticAt(position.line, position.column);
            if (diagnostic != null) {
                if (builder.length() > 0) {
                    builder.append("\n");
                }
                builder.append(diagnostic.severity).append(": ").append(diagnostic.message);
            }
            response.complete(builder.length() == 0 ? null : builder.toString());
        }
    }

    /** Signature help for the demo's own generated methods. */
    private final class DemoSignatureHelpProvider implements CodeSignatureHelpProvider {
        @Override
        public void provideSignatureHelp(
            CodeEditor source,
            CodeEditorPosition position,
            String functionName,
            int activeParameter,
            CodeSignatureHelpResponse response
        ) {
            if (functionName == null || functionName.isEmpty()) {
                response.complete(null);
                return;
            }
            String label;
            if (functionName.startsWith("method")) {
                label = functionName + "(int seed)";
            } else if (functionName.endsWith("indexOf")) {
                label = "indexOf(String needle, int fromIndex)";
            } else if (functionName.endsWith("substring")) {
                label = "substring(int beginIndex, int endIndex)";
            } else {
                label = functionName + "(Object... arguments)";
            }
            response.complete(CodeSignatureHelp.parseParenthesised(label, activeParameter));
        }
    }

    /**
     * The host-side of navigation. A real IDE would show a results list and a rename dialog; the demo
     * uses the status line, and for several targets it just cycles through them on each request.
     */
    private final class DemoNavigationListener implements CodeNavigationListener {
        @Override
        public void onNoTarget(CodeNavigationController controller, CodeNavigationRequest request) {
            lastEventText = "Navigation: no " + kindLabel(request) + " found"
                + (request.hasWord() ? " for '" + request.word + "'" : " (not on a word)");
        }

        @Override
        public boolean onMultipleTargets(
            CodeNavigationController controller,
            CodeNavigationRequest request,
            com.badlogic.gdx.utils.Array<CodeNavigationTarget> targets
        ) {
            // Cycle through the list so Shift-F12 / Find References is useful without a picker UI.
            int currentLine = editor.getCursorLine();
            int currentColumn = editor.getCursorColumn();
            int next = 0;
            for (int i = 0; i < targets.size; i++) {
                CodeNavigationTarget candidate = targets.get(i);
                if (candidate.getLine() == currentLine && candidate.getColumn() == currentColumn) {
                    next = (i + 1) % targets.size;
                    break;
                }
            }
            CodeNavigationTarget current = targets.get(next);
            lastEventText = "Navigation: " + targets.size + " " + kindLabel(request)
                + (request.hasWord() ? " of '" + request.word + "'" : "")
                + " — " + (next + 1) + "/" + targets.size
                + " at " + (current.getLine() + 1) + ":" + (current.getColumn() + 1);
            controller.navigateTo(current, request);
            return true;
        }

        @Override
        public boolean onNavigateToOtherDocument(
            CodeNavigationController controller,
            CodeNavigationRequest request,
            CodeNavigationTarget target
        ) {
            lastEventText = "Navigation: " + target.documentId + " is another document, not opened";
            return true;
        }

        @Override
        public void onNavigated(
            CodeNavigationController controller,
            CodeNavigationRequest request,
            CodeNavigationTarget target
        ) {
            if (request == null || request.kind == CodeNavigationKind.REFERENCES) {
                return;
            }
            lastEventText = "Navigation: " + kindLabel(request)
                + (request.hasWord() ? " of '" + request.word + "'" : "")
                + " at " + (target.getLine() + 1) + ":" + (target.getColumn() + 1);
        }

        @Override
        public boolean onRenameRequested(
            CodeNavigationController controller,
            CodeEditorTextRange range,
            String oldName
        ) {
            if (renameField != null) {
                renameField.setText(oldName);
                renameField.setCursorPosition(oldName.length());
            }
            lastEventText = "Rename: '" + oldName + "' — edit the field and press Rename";
            return true;
        }

        @Override
        public void onRenameApplied(CodeNavigationController controller, CodeRenameRequest request, int editCount) {
            lastEventText = "Rename: " + editCount + " occurrence"
                + (editCount == 1 ? "" : "s")
                + " of '" + request.oldName + "' -> '" + request.newName + "'";
        }

        @Override
        public void onRenameFailed(CodeNavigationController controller, String message) {
            lastEventText = "Rename: " + (message == null || message.isEmpty() ? "failed" : message);
        }

        private String kindLabel(CodeNavigationRequest request) {
            if (request == null || request.kind == null) {
                return "target";
            }
            switch (request.kind) {
                case REFERENCES:
                    return "references";
                case DECLARATION:
                    return "declaration";
                case TYPE_DEFINITION:
                    return "type definition";
                case IMPLEMENTATION:
                    return "implementation";
                default:
                    return "definition";
            }
        }
    }

    /**
     * Puts a mix of marks on the document: a coloured bar every 7th line, an execution-line highlight,
     * and a couple of icon marks reusing the fold drawables as stand-in breakpoint glyphs.
     */
    private void toggleLineMarks() {
        lineMarksEnabled = !lineMarksEnabled;
        if (!lineMarksEnabled) {
            editor.clearLineMarks();
            lastEventText = "Line marks cleared";
            return;
        }

        com.badlogic.gdx.utils.Array<CodeLineMark> marks = new com.badlogic.gdx.utils.Array<>();
        int limit = Math.min(editor.getLineCount(), 4000);
        for (int line = 0; line < limit; line += 7) {
            marks.add(CodeLineMark.bar(line, new Color(0.35f, 0.72f, 0.42f, 1f)));
        }
        for (int line = 3; line < limit; line += 53) {
            // Higher priority, so it wins over the bar on lines that carry both.
            marks.add(new CodeLineMark(line, editor.getStyle().foldCollapsed,
                new Color(0.92f, 0.35f, 0.33f, 1f), false, 10, "Breakpoint", null));
        }
        if (limit > 12) {
            marks.add(new CodeLineMark(11, null, new Color(0.96f, 0.76f, 0.26f, 1f), true, 20,
                "Execution line", null));
        }
        editor.setLineMarks(marks);
        lastEventText = "Added " + marks.size + " line marks";
    }

    /** Cycles indent strategies so tabs, 2-space and 4-space can all be tried in the demo. */
    /**
     * Rebinds two actions and back again, to show that the keymap is live. Ctrl-D for undo and F3 for
     * folding are deliberately odd choices, so it is obvious which map is in effect.
     */
    private void toggleKeymap() {
        altKeymapEnabled = !altKeymapEnabled;
        if (altKeymapEnabled) {
            editor.getKeymap()
                .unbindAction(CodeEditorAction.UNDO)
                .unbindAction(CodeEditorAction.TOGGLE_FOLD)
                .bind(CodeKeyStroke.ctrl(com.badlogic.gdx.Input.Keys.D), CodeEditorAction.UNDO)
                .bind(com.badlogic.gdx.Input.Keys.F3, CodeEditorAction.TOGGLE_FOLD);
            lastEventText = "Keymap: undo is Ctrl-D, fold is F3";
            return;
        }
        editor.setKeymap(null);
        lastEventText = "Keymap: back to defaults";
    }

    /** Turns bracket and quote auto-closing on and off. */
    private void toggleAutoEdit() {
        if (editor.getAutoEditStrategy() != null) {
            editor.setAutoEditStrategy(null);
            lastEventText = "Auto edit: off";
            return;
        }
        editor.setAutoEditStrategy(new CodeBracketAutoEditStrategy());
        lastEventText = "Auto edit: brackets, quotes, pair delete, smart Enter";
    }

    private void cycleIndentStrategy() {
        indentStrategyIndex = (indentStrategyIndex + 1) % 4;
        switch (indentStrategyIndex) {
            case 1:
                editor.setIndentStrategy(CodeIndentStrategy.spaces(2));
                break;
            case 2:
                editor.setIndentStrategy(CodeIndentStrategy.tabs(4));
                break;
            case 3:
                editor.setIndentStrategy(CodeIndentStrategy.tabs(8));
                break;
            case 0:
            default:
                editor.setIndentStrategy(CodeIndentStrategy.spaces(4));
                break;
        }
        lastEventText = "Indent: " + describeIndentStrategy();
    }

    private String describeIndentStrategy() {
        CodeIndentStrategy strategy = editor.getIndentStrategy();
        return (strategy.useTabs ? "tabs" : "spaces") + " x" + strategy.indentWidth;
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
            new DemoProfile("Java", "Brace structure + Java highlight", BuiltinCodeHighlighters.java(),
                new BraceCodeStructureProvider().setSymbolProvider(new JavaCodeSymbolProvider()), createJavaDemo()),
            new DemoProfile("Kotlin", "Brace structure + Kotlin highlight", BuiltinCodeHighlighters.kotlin(),
                new BraceCodeStructureProvider().setSymbolProvider(new JavaCodeSymbolProvider()), createKotlinDemo()),
            new DemoProfile("JavaScript", "Brace structure + JS/TS highlight", BuiltinCodeHighlighters.javascript(),
                new BraceCodeStructureProvider().setSymbolProvider(new JavaCodeSymbolProvider()), createJavaScriptDemo()),
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

    /**
     * Recomputes the breadcrumb only when the caret or the document actually moved on, and never while a
     * structure pass is still pending.
     *
     * <p>{@link CodeEditor#getSymbolPath(int)} flushes that pass, and the flush discards every
     * materialized line layout. Calling it from the per-frame panel refresh therefore re-ran the whole
     * O(document) analysis and a full viewport re-highlight on every frame while typing, which is exactly
     * what the debounce exists to avoid.
     */
    private void refreshSymbolBreadcrumb(boolean structurePending) {
        if (structurePending) {
            return;
        }
        int line = editor.getCursorLine();
        int version = editor.getDocumentVersion();
        if (line == cachedBreadcrumbLine && version == cachedBreadcrumbVersion) {
            return;
        }
        cachedBreadcrumbLine = line;
        cachedBreadcrumbVersion = version;
        cachedSymbolBreadcrumb = describeSymbolBreadcrumb();
    }

    /** Innermost symbol at the caret, prefixed by its ancestors, or a count when the caret is outside any. */
    private String describeSymbolBreadcrumb() {
        com.badlogic.gdx.utils.Array<CodeSymbol> path = editor.getSymbolPath(editor.getCursorLine());
        if (path.size == 0) {
            return editor.getSymbolCount() + " roots (caret outside)";
        }
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < path.size; i++) {
            if (i > 0) {
                builder.append(" / ");
            }
            builder.append(path.get(i).name);
        }
        return builder.toString();
    }

    private void applyCustomClipInset() {
        if (editor == null) {
            return;
        }
        if (!customClipInsetEnabled) {
            editor.clearClipArea();
            editor.setClipAreaEnabled(true);
            return;
        }
        // Small inset so custom scissor is visible; default (off) uses full widget bounds.
        float inset = 12f;
        float width = Math.max(1f, editor.getWidth() - inset * 2f);
        float height = Math.max(1f, editor.getHeight() - inset * 2f);
        editor.setClipArea(inset, inset, width, height);
    }

    private CodeEditor.CodeEditorStyle createEditorStyle() {
        CodeEditor.CodeEditorStyle style = CodeEditor.CodeEditorStyle.theme(font)
            .whitePixelTexture(whitePixel)
            .themeColor(new Color(0.274f, 0.561f, 0.898f, 1f))
            .backgroundColor(new Color(0.051f, 0.074f, 0.102f, 1f))
            .gutterColor(new Color(0.060f, 0.086f, 0.115f, 1f))
            .textColor(new Color(0.93f, 0.96f, 0.99f, 1f))
            .gutterTextColor(new Color(0.64f, 0.72f, 0.8f, 1f))
            .textBaselineOffset(-6f)
            .build();
        style.gutterLeftPadding = 6f;
        style.gutterFoldIndicatorGap = 8f;
        style.foldIndicatorSize = 10f;
        style.foldIndicatorRightPadding = 8f;
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
        // Without this the identifier kinds a lexer cannot produce — locals, parameters, fields —
        // resolve to no colour and the Semantic Tokens toggle would appear to do nothing.
        style.applyDefaultSemanticTokenColors();
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

        for (int i = 0; i < 48; i++) {
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
