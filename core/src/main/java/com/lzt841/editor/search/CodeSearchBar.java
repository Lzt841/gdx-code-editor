package com.lzt841.editor.search;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.InputListener;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.Cell;
import com.badlogic.gdx.scenes.scene2d.ui.HorizontalGroup;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.ui.TextField;
import com.badlogic.gdx.scenes.scene2d.ui.TextTooltip;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.scenes.scene2d.utils.Disableable;
import com.badlogic.gdx.scenes.scene2d.utils.UIUtils;
import com.lzt841.editor.CodeEditor;
import com.lzt841.editor.CodeEditorTextRange;

/**
 * Optional Scene2D toolbar for finding and replacing text in one editor. Add it to the same Stage
 * as the editor, and register {@link #createShortcutListener()} on the Stage to enable shortcuts.
 */
public class CodeSearchBar extends Table {
    private final CodeEditor editor;
    private final Table collapsedRow = new Table();
    private final Table findRow = new Table();
    private final Table replaceRow = new Table();
    private final HorizontalGroup options = new HorizontalGroup().left().rowLeft().space(4f).wrapSpace(4f).wrap();
    private final Cell<Table> firstRow;
    private final Cell<HorizontalGroup> optionsCell;
    private final Cell<Table> replaceCell;
    private final Cell<Label> statusCell;
    private final TextField findField;
    private final TextField replaceField;
    private final Label status;
    private final Color statusTextColor;
    private final TextButton openButton;
    private final TextButton previousButton;
    private final TextButton nextButton;
    private final TextButton caseButton;
    private final TextButton wordButton;
    private final TextButton regexButton;
    private final TextButton scopeButton;
    private final TextButton replaceToggle;
    private final TextButton replaceButton;
    private final TextButton replaceAllButton;
    private final float controlHeight;

    private boolean open;
    private boolean replaceVisible;
    private String replaceFeedback = "";
    private int replaceFeedbackVersion = -1;
    private boolean availabilityKnown;
    private boolean lastAvailable;
    private int lastDocumentVersion = -1;
    private int lastCursorLine = -1;
    private int lastCursorColumn = -1;
    private boolean lastHasSelection;
    private String lastEditorSearchText = "";
    private CodeEditorTextRange lastSearchRange;
    private boolean lastCaseSensitive;
    private boolean lastWholeWord;
    private boolean lastRegexEnabled;
    private boolean lastReadOnly;

    public CodeSearchBar(CodeEditor editor, TextField.TextFieldStyle fieldStyle,
                         TextButton.TextButtonStyle buttonStyle, Label.LabelStyle labelStyle) {
        this.editor = editor;
        controlHeight = isMobile() && Gdx.graphics != null ? Math.max(32f, 44f * Gdx.graphics.getDensity()) : 32f;
        findField = field("Find", fieldStyle);
        replaceField = field("Replace with", fieldStyle);
        status = new Label("", labelStyle);
        statusTextColor = labelStyle.fontColor == null ? new Color(Color.WHITE) : new Color(labelStyle.fontColor);
        status.setWrap(true);

        openButton = command("Find", buttonStyle, () -> open(false));
        previousButton = command("<", buttonStyle, () -> navigate(false));
        nextButton = command(">", buttonStyle, () -> navigate(true));
        TextButton closeButton = command("X", buttonStyle, this::close);
        caseButton = command("Aa", buttonStyle, () -> {
            editor.setSearchCaseSensitive(!editor.isSearchCaseSensitive());
            optionsChanged();
        });
        wordButton = command("Word", buttonStyle, () -> {
            editor.setSearchWholeWord(!editor.isSearchWholeWord());
            optionsChanged();
        });
        regexButton = command(".*", buttonStyle, () -> {
            editor.setSearchRegexEnabled(!editor.isSearchRegexEnabled());
            optionsChanged();
        });
        scopeButton = command("Selection", buttonStyle, () -> {
            if (editor.getSearchRange() == null) {
                editor.setSearchRangeToSelection();
            } else {
                editor.clearSearchRange();
            }
            optionsChanged();
        });
        replaceToggle = command("Replace", buttonStyle, () -> {
            setReplaceVisible(!replaceVisible);
            if (replaceVisible && !editor.isReadOnly()) {
                focus(replaceField, false);
            }
        });
        replaceButton = command("One", buttonStyle, () -> replace(false));
        replaceAllButton = command("All", buttonStyle, () -> replace(true));

        if (!isMobile()) {
            TextTooltip.TextTooltipStyle tooltip = new TextTooltip.TextTooltipStyle(labelStyle,
                fieldStyle.focusedBackground == null ? fieldStyle.background : fieldStyle.focusedBackground);
            tooltip.wrapWidth = 240f;
            describe(openButton, "Find (" + (UIUtils.isMac ? "Cmd" : "Ctrl") + "+F)", tooltip);
            describe(previousButton, "Previous match (Shift+Enter)", tooltip);
            describe(nextButton, "Next match (Enter)", tooltip);
            describe(closeButton, "Close search (Esc)", tooltip);
            describe(caseButton, "Match case", tooltip);
            describe(wordButton, "Match whole words", tooltip);
            describe(regexButton, "Use a regular expression", tooltip);
            describe(scopeButton, "Search in the selection", tooltip);
            describe(replaceButton, "Replace one match", tooltip);
            describe(replaceAllButton, "Replace all matches", tooltip);
        }

        collapsedRow.right().add(openButton);
        findRow.defaults().space(4f).minHeight(controlHeight);
        findRow.add(findField).growX().minWidth(0f);
        findRow.add(previousButton);
        findRow.add(nextButton);
        findRow.add(closeButton);

        options.addActor(caseButton);
        options.addActor(wordButton);
        options.addActor(regexButton);
        options.addActor(scopeButton);
        options.addActor(replaceToggle);

        replaceRow.defaults().space(4f).minHeight(controlHeight);
        replaceRow.add(replaceField).growX().minWidth(0f);
        replaceRow.add(replaceButton);
        replaceRow.add(replaceAllButton);

        defaults().space(4f);
        firstRow = add(collapsedRow).growX().minWidth(0f);
        row();
        optionsCell = add((HorizontalGroup) null).growX().minWidth(0f);
        row();
        replaceCell = add((Table) null).growX().minWidth(0f);
        row();
        statusCell = add((Label) null).growX().minWidth(0f);
        addCaptureListener(new InputListener() {
            @Override
            public boolean keyTyped(InputEvent event, char character) {
                // IMEs may deliver Enter as a typed character without a keyDown event.
                if (!open || (character != '\n' && character != '\r')) {
                    return false;
                }
                if (event.getTarget() == findField) {
                    navigate(!UIUtils.shift());
                } else if (event.getTarget() == replaceField) {
                    replace(false);
                } else {
                    return false;
                }
                event.stop();
                return true;
            }
        });
        findField.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                if (open && isAvailable()) {
                    replaceFeedback = "";
                    editor.setSearchText(findField.getText());
                    updateResults();
                }
            }
        });
    }

    /** Opens the toolbar, prefilling a single-line editor selection when available. */
    public boolean open(boolean showReplace) {
        if (!isAvailable()) {
            return false;
        }
        if (!open) {
            CodeEditorTextRange selection = editor.getSelection();
            if (selection != null && selection.isSingleLine()) {
                return openForText(editor.getSelectionText(), showReplace);
            }
            if (editor.getSearchText().isEmpty()) {
                editor.setSearchText(findField.getText());
            }
        }
        show(showReplace);
        return true;
    }

    /** Opens a literal search for single-line text, for example from an editor context menu. */
    public boolean openForText(String text, boolean showReplace) {
        if (!isAvailable() || text == null || text.isEmpty() || text.indexOf('\n') >= 0 || text.indexOf('\r') >= 0) {
            return false;
        }
        editor.setSearchRegexEnabled(false);
        editor.clearSearchRange();
        editor.setSearchText(text);
        replaceFeedback = "";
        show(showReplace);
        return true;
    }

    private void show(boolean showReplace) {
        if (!open) {
            open = true;
            firstRow.setActor(findRow);
            optionsCell.setActor(options);
            statusCell.setActor(status);
            statusCell.minHeight(status.getStyle().font.getLineHeight());
        }
        setReplaceVisible(showReplace);
        updateResults();
        focus(replaceVisible && !editor.getSearchText().isEmpty() ? replaceField : findField, true);
    }

    /** Closes the toolbar and clears its search highlights and selection scope. */
    public void close() {
        if (!open) {
            return;
        }
        Stage stage = getStage();
        Actor focus = stage == null ? null : stage.getKeyboardFocus();
        if (focus != null && focus.isDescendantOf(this)) {
            stage.setKeyboardFocus(isAvailable() && editor.getStage() == stage ? editor : null);
            setSoftKeyboardVisible(false);
        }
        open = false;
        firstRow.setActor(collapsedRow);
        optionsCell.setActor(null);
        setReplaceVisible(false);
        statusCell.setActor(null);
        statusCell.minHeight(0f);
        editor.setSearchText("");
        editor.clearSearchRange();
        replaceFeedback = "";
        invalidateHierarchy();
    }

    /** Install as a Stage capture listener. Shortcuts act only while this editor or toolbar has focus. */
    public InputListener createShortcutListener() {
        return new InputListener() {
            @Override
            public boolean keyDown(InputEvent event, int keycode) {
                Actor target = event.getTarget();
                boolean inToolbar = target.isDescendantOf(CodeSearchBar.this);
                if (!inToolbar && target != editor) {
                    return false;
                }
                if (open && inToolbar && keycode == Input.Keys.ESCAPE) {
                    close();
                    event.stop();
                    return true;
                }
                if ((keycode == Input.Keys.F || keycode == Input.Keys.H) && UIUtils.ctrl()
                    && !UIUtils.shift() && !UIUtils.alt() && open(keycode == Input.Keys.H)) {
                    event.stop();
                    return true;
                }
                return false;
            }
        };
    }

    @Override
    public void act(float delta) {
        super.act(delta);
        boolean available = isAvailable();
        if (!availabilityKnown || available != lastAvailable) {
            availabilityKnown = true;
            lastAvailable = available;
            openButton.setDisabled(!available);
        }
        if (!available) {
            close();
        } else if (open && resultsChanged()) {
            updateResults();
        }
    }

    private boolean isAvailable() {
        return !editor.isDisabled() && !editor.isPasswordMode();
    }

    private void setReplaceVisible(boolean visible) {
        visible = visible && !editor.isReadOnly();
        if (replaceVisible == visible) {
            return;
        }
        replaceVisible = visible;
        boolean moveFocus = !visible && open && replaceField.hasKeyboardFocus();
        replaceCell.setActor(visible ? replaceRow : null);
        replaceToggle.setChecked(visible);
        invalidateHierarchy();
        if (moveFocus) {
            focus(findField, false);
        }
    }

    private void navigate(boolean forward) {
        if (!isAvailable()) {
            return;
        }
        replaceFeedback = "";
        if (forward) {
            editor.findNextSearchMatch();
        } else {
            editor.findPreviousSearchMatch();
        }
        updateResults();
    }

    private void replace(boolean all) {
        if (!isAvailable() || editor.isReadOnly()) {
            return;
        }
        int count = all ? editor.replaceAllSearchMatches(replaceField.getText())
            : editor.replaceCurrentSearchMatch(replaceField.getText()) ? 1 : 0;
        replaceFeedback = count == 0 ? "" : count == 1 ? "Replaced 1 match" : "Replaced " + count + " matches";
        replaceFeedbackVersion = editor.getDocumentVersion();
        updateResults();
    }

    private void optionsChanged() {
        replaceFeedback = "";
        updateResults();
    }

    private void updateResults() {
        String query = editor.getSearchText();
        if (!query.equals(lastEditorSearchText)) {
            replaceFeedback = "";
            findField.setText(query);
        }
        boolean readOnly = editor.isReadOnly();
        if (readOnly) {
            setReplaceVisible(false);
        }
        int count = editor.getSearchMatchCount();
        int ordinal = editor.getCurrentSearchMatchOrdinal();
        String error = editor.getSearchRegexError();
        String result = error != null ? "Invalid regex: " + error
            : query.isEmpty() ? ""
            : count == 0 ? "No matches"
            : ordinal == 0 ? count + (count == 1 ? " match" : " matches")
            : ordinal + " of " + count;
        if (replaceFeedbackVersion == editor.getDocumentVersion() && !replaceFeedback.isEmpty() && error == null) {
            result = replaceFeedback + "  |  " + result;
        }
        if (!result.equals(status.getText().toString())) {
            status.setText(result);
        }
        Color errorColor = editor.getStyle().diagnosticErrorColor;
        Color resultColor = error != null && errorColor != null ? errorColor : statusTextColor;
        if (!status.getColor().equals(resultColor)) {
            status.setColor(resultColor);
        }
        setCheckedIfChanged(caseButton, editor.isSearchCaseSensitive());
        setCheckedIfChanged(wordButton, editor.isSearchWholeWord());
        setCheckedIfChanged(regexButton, editor.isSearchRegexEnabled());
        boolean scoped = editor.getSearchRange() != null;
        setCheckedIfChanged(scopeButton, scoped);
        setDisabledIfChanged(scopeButton, !scoped && !editor.hasSelection());
        setDisabledIfChanged(previousButton, count == 0);
        setDisabledIfChanged(nextButton, count == 0);
        setDisabledIfChanged(replaceToggle, readOnly);
        setDisabledIfChanged(replaceButton, count == 0);
        setDisabledIfChanged(replaceAllButton, count == 0);
        rememberResultsState();
    }

    private boolean resultsChanged() {
        CodeEditorTextRange searchRange = editor.getSearchRange();
        return editor.getDocumentVersion() != lastDocumentVersion
            || editor.getCursorLine() != lastCursorLine
            || editor.getCursorColumn() != lastCursorColumn
            || editor.hasSelection() != lastHasSelection
            || !editor.getSearchText().equals(lastEditorSearchText)
            || !sameRange(searchRange, lastSearchRange)
            || editor.isSearchCaseSensitive() != lastCaseSensitive
            || editor.isSearchWholeWord() != lastWholeWord
            || editor.isSearchRegexEnabled() != lastRegexEnabled
            || editor.isReadOnly() != lastReadOnly;
    }

    private void rememberResultsState() {
        lastDocumentVersion = editor.getDocumentVersion();
        lastCursorLine = editor.getCursorLine();
        lastCursorColumn = editor.getCursorColumn();
        lastHasSelection = editor.hasSelection();
        lastEditorSearchText = editor.getSearchText();
        lastSearchRange = editor.getSearchRange();
        lastCaseSensitive = editor.isSearchCaseSensitive();
        lastWholeWord = editor.isSearchWholeWord();
        lastRegexEnabled = editor.isSearchRegexEnabled();
        lastReadOnly = editor.isReadOnly();
    }

    private static boolean sameRange(CodeEditorTextRange left, CodeEditorTextRange right) {
        return left == null ? right == null : left.equals(right);
    }

    private static void setCheckedIfChanged(TextButton button, boolean checked) {
        if (button.isChecked() != checked) {
            button.setChecked(checked);
        }
    }

    private static void setDisabledIfChanged(Disableable actor, boolean disabled) {
        if (actor.isDisabled() != disabled) {
            actor.setDisabled(disabled);
        }
    }

    private void focus(TextField field, boolean selectAll) {
        Stage stage = getStage();
        if (stage != null && stage.setKeyboardFocus(field)) {
            if (selectAll) {
                field.selectAll();
            }
            setSoftKeyboardVisible(true);
        }
    }

    private void setSoftKeyboardVisible(boolean visible) {
        if (isMobile() && Gdx.input != null) {
            Gdx.input.setOnscreenKeyboardVisible(visible);
        }
    }

    private static boolean isMobile() {
        return UIUtils.isAndroid || UIUtils.isIos;
    }

    private static TextField field(String placeholder, TextField.TextFieldStyle style) {
        TextField field = new TextField("", style);
        field.setMessageText(placeholder);
        field.setOnlyFontChars(false);
        return field;
    }

    private TextButton command(String text, TextButton.TextButtonStyle style, Runnable action) {
        TextButton button = new TextButton(text, style) {
            @Override
            public float getPrefWidth() {
                return Math.max(controlHeight, super.getPrefWidth());
            }

            @Override
            public float getPrefHeight() {
                return Math.max(controlHeight, super.getPrefHeight());
            }
        };
        button.setProgrammaticChangeEvents(false);
        button.pad(4f, 8f, 4f, 8f);
        button.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                button.setChecked(false);
                action.run();
            }
        });
        return button;
    }

    private static void describe(TextButton button, String text, TextTooltip.TextTooltipStyle style) {
        button.addListener(new TextTooltip(text, style));
    }
}
