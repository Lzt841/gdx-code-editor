package com.lzt841.editor.navigation;

import com.badlogic.gdx.Application.ApplicationType;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.InputListener;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.ui.TextField;
import com.badlogic.gdx.scenes.scene2d.ui.Window;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.scenes.scene2d.utils.Disableable;
import com.badlogic.gdx.utils.Disposable;
import com.lzt841.editor.CodeEditor;

/**
 * Optional modal navigation UI for one editor. Input is a positive, 1-based logical line number or
 * {@code line:column}; omitted columns mean 1. Columns count UTF-16 code units (a tab counts as one),
 * including the insertion position after the last character. Out-of-range coordinates are rejected;
 * surrogate midpoints snap to the end of the pair through {@link CodeEditor#setCursorPosition}.
 * Opening/cancelling preserves editor state. All calls use the GDX thread; styles/fonts are borrowed.
 */
public class CodeGoToLineDialog extends Window implements Disposable {
    public static class CodeGoToLineDialogStyle {
        public WindowStyle window;
        public TextField.TextFieldStyle location;
        public TextButton.TextButtonStyle buttons;
        public Label.LabelStyle status;
        public float maxWidth = 480f;
        public float edgeMargin = 12f;
        public float controlHeight = 36f;

        public CodeGoToLineDialogStyle(TextField.TextFieldStyle fieldStyle,
                                      TextButton.TextButtonStyle buttonStyle, Label.LabelStyle labelStyle) {
            if (fieldStyle == null || fieldStyle.font == null || buttonStyle == null || buttonStyle.font == null
                || labelStyle == null || labelStyle.font == null) {
                throw new IllegalArgumentException("field, button and label styles with fonts are required");
            }
            location = fieldStyle;
            buttons = buttonStyle;
            status = labelStyle;
            window = new WindowStyle(labelStyle.font,
                labelStyle.fontColor == null ? Color.WHITE : labelStyle.fontColor,
                fieldStyle.focusedBackground != null ? fieldStyle.focusedBackground : fieldStyle.background);
        }
    }

    private final CodeEditor editor;
    private final CodeGoToLineDialogStyle dialogStyle;
    private final TextField locationField;
    private final Label status;
    private final TextButton goButton;
    private final TextButton cancelButton;
    private Stage openStage;
    private Actor previousKeyboardFocus;
    private Actor previousScrollFocus;
    private boolean open;
    private boolean disposed;
    private long openGeneration;
    private boolean validTarget;
    private int targetLine;
    private int targetColumn;
    private int lastDocumentVersion = -1;

    public CodeGoToLineDialog(CodeEditor editor, CodeGoToLineDialogStyle style) {
        super("Go to Line / Column", requireStyle(style));
        if (editor == null) throw new IllegalArgumentException("editor is required");
        this.editor = editor;
        dialogStyle = style;
        setName("editorGoToLineDialog");
        setModal(true);
        setMovable(false);
        setResizable(false);
        setKeepWithinStage(true);
        padLeft(12f);
        padRight(12f);
        padBottom(12f);
        padTop(Math.max(getPadTop(), getTitleLabel().getPrefHeight()) + 12f);
        defaults().space(6f);

        locationField = new TextField("", style.location);
        locationField.setMessageText("Line or line:column");
        locationField.setOnlyFontChars(false);
        locationField.setFocusTraversal(false);
        locationField.setProgrammaticChangeEvents(true);
        locationField.addListener(new ChangeListener() {
            @Override public void changed(ChangeEvent event, Actor actor) { refreshTarget(); }
        });
        status = new Label("", style.status);
        status.setEllipsis(true);
        goButton = button("Go", this::jump);
        cancelButton = button("Cancel", this::close);
        Table actions = new Table();
        actions.defaults().space(6f).height(controlHeight());
        actions.add(goButton).growX().minWidth(0f);
        actions.add(cancelButton).growX().minWidth(0f);
        add(locationField).growX().minWidth(0f).height(controlHeight());
        row();
        add(status).growX().minWidth(0f);
        row();
        add(actions).growX().minWidth(0f);

        addCaptureListener(new InputListener() {
            @Override public boolean keyDown(InputEvent event, int keycode) {
                if (!open) return false;
                if (keycode == Input.Keys.ESCAPE) close();
                else if (keycode != Input.Keys.ENTER && keycode != Input.Keys.NUMPAD_ENTER) return false;
                // Submit on keyTyped: closing on keyDown routes the following newline to the editor.
                event.stop();
                return true;
            }

            @Override public boolean keyTyped(InputEvent event, char character) {
                if (!open || (character != '\r' && character != '\n')) return false;
                jump();
                event.stop();
                return true;
            }
        });
        refreshTarget();
    }

    private static WindowStyle requireStyle(CodeGoToLineDialogStyle style) {
        if (style == null || style.window == null || style.window.titleFont == null
            || style.location == null || style.location.font == null || style.buttons == null
            || style.buttons.font == null || style.status == null || style.status.font == null) {
            throw new IllegalArgumentException("go-to-line styles with fonts are required");
        }
        return style.window;
    }

    private float controlHeight() {
        return Gdx.app != null && (Gdx.app.getType() == ApplicationType.Android || Gdx.app.getType() == ApplicationType.iOS)
            && Gdx.graphics != null ? Math.max(dialogStyle.controlHeight, 44f * Gdx.graphics.getDensity())
            : dialogStyle.controlHeight;
    }

    private TextButton button(String text, Runnable action) {
        TextButton button = new TextButton(text, dialogStyle.buttons);
        button.getLabel().setEllipsis(true);
        button.getLabelCell().minWidth(0f);
        button.addListener(new ChangeListener() {
            @Override public void changed(ChangeEvent event, Actor actor) {
                if (!button.isDisabled() && button.isChecked()) {
                    button.setChecked(false);
                    action.run();
                }
            }
        });
        return button;
    }

    /** Prefills and selects the current logical line/column, preserving the editor selection. */
    public boolean open() {
        Stage stage = editor.getStage();
        if (!available(stage) || stage.getWidth() <= 0f || stage.getHeight() <= 0f) return false;
        if (open && (openStage != stage || getStage() != stage)) close();
        if (!available(stage)) return false;
        long generation = ++openGeneration;
        if (!open) {
            previousKeyboardFocus = stage.getKeyboardFocus();
            previousScrollFocus = stage.getScrollFocus();
            openStage = stage;
            open = true;
            setVisible(true);
            stage.addActor(this);
        }
        locationField.setText((editor.getCursorLine() + 1) + ":" + (editor.getCursorColumn() + 1));
        if (!currentOpen(stage, generation)) return false;
        refreshTarget();
        updateGeometry();
        toFront();
        if (!available(stage) || !visible(this) || getStage() != stage || !stage.setKeyboardFocus(locationField)) {
            if (currentOpen(stage, generation)) close();
            return false;
        }
        // Focus listeners can unload the extension, move focus, or reopen the window.
        if (!currentOpen(stage, generation)) return false;
        if (!available(stage) || !visible(this) || getStage() != stage || stage.getKeyboardFocus() != locationField) {
            close();
            return false;
        }
        locationField.selectAll();
        showKeyboard(true);
        return currentOpen(stage, generation);
    }

    private boolean currentOpen(Stage stage, long generation) {
        return open && openStage == stage && openGeneration == generation;
    }

    public boolean isOpen() { return open; }
    public boolean isDisposed() { return disposed; }
    public TextField getLocationField() { return locationField; }
    public TextButton getGoButton() { return goButton; }
    public TextButton getCancelButton() { return cancelButton; }
    public String getStatusText() { return status.getText().toString(); }

    /** Validates against the current document. Invalid input stays open and leaves editor state intact. */
    public boolean jump() {
        Stage stage = openStage;
        if (!open || !available(stage) || !visible(this) || getStage() != stage
            || !belongsToDialog(stage.getKeyboardFocus())) return false;
        refreshTarget();
        if (!validTarget) return false;
        int line = targetLine;
        int column = targetColumn;
        long generation = openGeneration;
        int version = editor.getDocumentVersion();
        close(true);
        // Restoring editor focus invokes host listeners synchronously. Never apply a stale request.
        if (!canCompleteJump(stage, generation, version)) return false;
        editor.refreshStructureNow();
        // A custom structure provider can also invoke host callbacks.
        if (!canCompleteJump(stage, generation, version)) return false;
        editor.setCursorPosition(line, column);
        return true;
    }

    private boolean canCompleteJump(Stage stage, long generation, int version) {
        return !open && openGeneration == generation && available(stage)
            && stage.getKeyboardFocus() == editor && editor.getDocumentVersion() == version;
    }

    private void refreshTarget() {
        if (disposed) return;
        lastDocumentVersion = editor.getDocumentVersion();
        String text = locationField.getText().trim();
        int separator = text.indexOf(':');
        int line = positiveInteger(separator < 0 ? text : text.substring(0, separator));
        int column = separator < 0 ? 1 : positiveInteger(text.substring(separator + 1));
        validTarget = false;
        if (line < 1 || column < 1) {
            status.setText("Use line or line:column with positive whole numbers.");
        } else if (line > editor.getLineCount()) {
            status.setText("Line must be between 1 and " + editor.getLineCount() + ".");
        } else {
            int length = editor.getLineLength(line - 1);
            if (column - 1 > length) {
                status.setText("Column must be between 1 and " + ((long) length + 1) + ".");
            } else {
                targetLine = line - 1;
                targetColumn = column - 1;
                validTarget = true;
                status.setText("Line " + line + " of " + editor.getLineCount() + ", column " + column
                    + " of " + ((long) length + 1) + ".");
            }
        }
        goButton.setDisabled(!validTarget);
    }

    private static int positiveInteger(String text) {
        text = text.trim();
        if (text.isEmpty()) return -1;
        int value = 0;
        for (int index = 0; index < text.length(); index++) {
            char character = text.charAt(index);
            if (character < '0' || character > '9') return -1;
            int digit = character - '0';
            if (value > (Integer.MAX_VALUE - digit) / 10) return -1;
            value = value * 10 + digit;
        }
        return value == 0 ? -1 : value;
    }

    private boolean available(Stage stage) {
        return !disposed && stage != null && editor.getStage() == stage && !editor.isDisabled()
            && !editor.isPasswordMode() && visible(editor);
    }

    private static boolean visible(Actor actor) {
        for (Actor ancestor = actor; ancestor != null; ancestor = ancestor.getParent()) {
            if (!ancestor.isVisible()) return false;
        }
        return true;
    }

    private void updateGeometry() {
        if (!open || openStage == null) return;
        float margin = Math.max(0f, dialogStyle.edgeMargin);
        float width = Math.min(dialogStyle.maxWidth, Math.max(0f, openStage.getWidth() - 2f * margin));
        setWidth(width);
        setHeight(Math.min(getPrefHeight(), Math.max(0f, openStage.getHeight() - 2f * margin)));
        setPosition((openStage.getWidth() - width) / 2f, Math.max(0f, openStage.getHeight() - getHeight() - margin));
    }

    @Override public void act(float delta) {
        if (open) {
            if (!available(openStage) || !visible(this) || getStage() != openStage
                || !belongsToDialog(openStage.getKeyboardFocus())) {
                close();
                return;
            }
            if (lastDocumentVersion != editor.getDocumentVersion()) refreshTarget();
            updateGeometry();
        }
        super.act(delta);
    }

    /** Cancels navigation, restoring eligible previous focus without stealing another widget's focus. */
    public void close() { close(false); }

    private void close(boolean focusEditor) {
        if (!open) { super.remove(); return; }
        open = false;
        long generation = openGeneration;
        Stage stage = openStage;
        openStage = null;
        Actor keyboard = previousKeyboardFocus;
        Actor scroll = previousScrollFocus;
        previousKeyboardFocus = null;
        previousScrollFocus = null;
        boolean restoreKeyboard = belongsToDialog(stage.getKeyboardFocus());
        boolean restoreScroll = belongsToDialog(stage.getScrollFocus());
        if (restoreKeyboard) showKeyboard(false);
        super.remove();
        if (open || openGeneration != generation) return;
        if (restoreKeyboard && stage.getKeyboardFocus() == null) {
            stage.setKeyboardFocus(focusEditor && restorable(editor, stage) ? editor
                : !focusEditor && restorable(keyboard, stage) ? keyboard
                : restorable(editor, stage) ? editor : null);
        }
        if (!open && openGeneration == generation && restoreScroll && stage.getScrollFocus() == null) {
            stage.setScrollFocus(restorable(scroll, stage) ? scroll : null);
        }
    }

    private boolean belongsToDialog(Actor actor) {
        return actor != null && actor.isDescendantOf(this);
    }

    private boolean restorable(Actor actor, Stage stage) {
        return actor != null && actor.getStage() == stage && !belongsToDialog(actor) && visible(actor)
            && !(actor == editor && editor.isDisabled())
            && !(actor instanceof Disableable && ((Disableable) actor).isDisabled());
    }

    private void showKeyboard(boolean show) {
        if (Gdx.app != null && (Gdx.app.getType() == ApplicationType.Android || Gdx.app.getType() == ApplicationType.iOS)) {
            locationField.getOnscreenKeyboard().show(show);
        }
    }

    @Override public boolean remove() {
        if (!open) return super.remove();
        close();
        return true;
    }

    @Override public void dispose() {
        if (disposed) return;
        disposed = true;
        close();
    }
}
