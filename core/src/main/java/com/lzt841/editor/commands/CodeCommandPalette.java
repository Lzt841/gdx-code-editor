package com.lzt841.editor.commands;

import com.badlogic.gdx.Application.ApplicationType;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.graphics.Color;
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
import com.badlogic.gdx.scenes.scene2d.ui.Window;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.scenes.scene2d.utils.Disableable;
import com.badlogic.gdx.utils.Align;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Disposable;
import com.lzt841.editor.CodeCommandRegistry;
import com.lzt841.editor.CodeEditor;
import com.lzt841.editor.CodeEditorCommand;
import com.lzt841.editor.CodeKeyStroke;

/**
 * Optional searchable, modal Scene2D command picker for one editor. Opening preserves the editor's
 * selection; execution closes the picker and restores focus before invoking the command. Call all
 * methods on the GDX thread. Styles/fonts are borrowed and never disposed by this widget.
 */
public class CodeCommandPalette extends Window implements Disposable {
    public static class CodeCommandPaletteStyle {
        public WindowStyle window;
        public TextField.TextFieldStyle query;
        public TextButton.TextButtonStyle buttons;
        public Label.LabelStyle status;
        public ScrollPane.ScrollPaneStyle scroll = new ScrollPane.ScrollPaneStyle();
        public float maxWidth = 680f;
        public float maxHeight = 460f;
        public float edgeMargin = 12f;
        public float rowHeight = 36f;
        public int maxVisibleRows = 8;
        /** Polls external enable conditions only while open. Registry changes refresh immediately. */
        public float refreshInterval = 0.15f;

        public CodeCommandPaletteStyle(TextField.TextFieldStyle fieldStyle,
                                       TextButton.TextButtonStyle buttonStyle, Label.LabelStyle labelStyle) {
            if (fieldStyle == null || fieldStyle.font == null || buttonStyle == null || buttonStyle.font == null
                || labelStyle == null || labelStyle.font == null) {
                throw new IllegalArgumentException("field, button and label styles with fonts are required");
            }
            query = fieldStyle;
            buttons = new TextButton.TextButtonStyle(buttonStyle);
            if (buttons.checked == null) buttons.checked = buttons.down != null ? buttons.down : buttons.over;
            status = labelStyle;
            window = new WindowStyle(labelStyle.font,
                labelStyle.fontColor == null ? Color.WHITE : labelStyle.fontColor,
                fieldStyle.focusedBackground != null ? fieldStyle.focusedBackground : fieldStyle.background);
        }
    }

    public interface ExecutionListener {
        /** Called after the picker closes and the command returns, including a false/no-op result. */
        void executed(CodeEditorCommand command, boolean handled);
    }

    private final CodeEditor editor;
    private final CodeCommandRegistry commands;
    private final CodeCommandPaletteStyle paletteStyle;
    private final TextField queryField;
    private final Table results = new Table();
    private final ScrollPane scrollPane;
    private final Cell<ScrollPane> resultsCell;
    private final Label status;
    private final TextButton runButton;
    private final Array<CommandRow> rows = new Array<>();
    private Stage openStage;
    private Actor previousKeyboardFocus;
    private Actor previousScrollFocus;
    private int selectedIndex = -1;
    private boolean open;
    private boolean disposed;
    private boolean enabledOnly;
    private float refreshElapsed;
    private long lastRegistryRevision = -1;
    private ExecutionListener executionListener;

    public CodeCommandPalette(CodeEditor editor, CodeCommandPaletteStyle style) {
        super("Commands", requireStyle(style));
        if (editor == null) throw new IllegalArgumentException("editor is required");
        this.editor = editor;
        commands = editor.getCommands();
        paletteStyle = style;
        setName("editorCommandPalette");
        setModal(true);
        setMovable(false);
        setResizable(false);
        setKeepWithinStage(true);
        padLeft(12f);
        padRight(12f);
        padBottom(12f);
        padTop(Math.max(getPadTop(), getTitleLabel().getPrefHeight()) + 12f);
        defaults().space(6f);

        queryField = new TextField("", style.query);
        queryField.setMessageText("Search commands...");
        // Queries and command IDs must survive even when the chosen UI font lacks a glyph.
        queryField.setOnlyFontChars(false);
        queryField.setFocusTraversal(false);
        queryField.setProgrammaticChangeEvents(true);
        queryField.addListener(new ChangeListener() {
            @Override public void changed(ChangeEvent event, Actor actor) {
                if (open) refresh(false);
            }
        });
        results.top().defaults().growX();
        scrollPane = new ScrollPane(results, style.scroll);
        scrollPane.setScrollingDisabled(true, false);
        scrollPane.setFadeScrollBars(false);
        scrollPane.setOverscroll(false, false);
        scrollPane.setSmoothScrolling(false);
        status = new Label("", style.status);
        status.setEllipsis(true);
        runButton = button("Run (Enter)", this::executeSelected);
        TextButton closeButton = button("Close (Esc)", this::close);
        Table actions = new Table();
        actions.defaults().space(6f).height(rowHeight());
        actions.add(runButton).growX().minWidth(0f);
        actions.add(closeButton).growX().minWidth(0f);

        add(queryField).growX().minWidth(0f).height(rowHeight());
        row();
        resultsCell = add(scrollPane).grow().minSize(0f, 0f);
        row();
        add(status).growX().minWidth(0f);
        row();
        add(actions).growX().minWidth(0f);

        addCaptureListener(new InputListener() {
            @Override public boolean keyDown(InputEvent event, int keycode) {
                if (!open) return false;
                if (keycode == Input.Keys.ESCAPE) {
                    close();
                } else if (keycode == Input.Keys.UP) {
                    moveSelection(-1);
                } else if (keycode == Input.Keys.DOWN) {
                    moveSelection(1);
                } else if (keycode == Input.Keys.PAGE_UP) {
                    setSelectedIndex(selectedIndex - Math.max(1, paletteStyle.maxVisibleRows));
                } else if (keycode == Input.Keys.PAGE_DOWN) {
                    setSelectedIndex(selectedIndex + Math.max(1, paletteStyle.maxVisibleRows));
                } else if (keycode == Input.Keys.ENTER || keycode == Input.Keys.NUMPAD_ENTER) {
                    executeSelected();
                } else {
                    return false;
                }
                event.stop();
                return true;
            }

            @Override public boolean keyTyped(InputEvent event, char character) {
                if (!open || (character != '\n' && character != '\r')) return false;
                executeSelected(); // IMEs can send Enter without a keyDown.
                event.stop();
                return true;
            }

            @Override public boolean touchDown(InputEvent event, float x, float y, int pointer, int button) {
                if (open && (x < 0 || y < 0 || x >= getWidth() || y >= getHeight())) {
                    close();
                    event.stop();
                    return true; // Dismiss without forwarding the click into the editor.
                }
                return false;
            }
        });
    }

    private static WindowStyle requireStyle(CodeCommandPaletteStyle style) {
        if (style == null || style.window == null || style.window.titleFont == null
            || style.query == null || style.query.font == null || style.buttons == null || style.buttons.font == null
            || style.status == null || style.status.font == null || style.scroll == null
            || !positiveFinite(style.maxWidth) || !positiveFinite(style.maxHeight)
            || !positiveFinite(style.rowHeight) || !positiveFinite(style.refreshInterval)
            || style.edgeMargin < 0f || Float.isNaN(style.edgeMargin) || Float.isInfinite(style.edgeMargin)
            || style.maxVisibleRows < 1) {
            throw new IllegalArgumentException("valid palette styles and positive dimensions are required");
        }
        return style.window;
    }

    private static boolean positiveFinite(float number) {
        return number > 0f && !Float.isInfinite(number);
    }

    private float rowHeight() {
        boolean mobile = Gdx.app != null && (Gdx.app.getType() == ApplicationType.Android
            || Gdx.app.getType() == ApplicationType.iOS);
        return mobile && Gdx.graphics != null ? Math.max(paletteStyle.rowHeight, 44f * Gdx.graphics.getDensity())
            : paletteStyle.rowHeight;
    }

    private TextButton button(String text, Runnable action) {
        TextButton button = new TextButton(text, paletteStyle.buttons);
        button.setProgrammaticChangeEvents(false);
        button.addListener(new ClickListener() {
            @Override public void clicked(InputEvent event, float x, float y) {
                if (!button.isDisabled()) {
                    button.setChecked(false);
                    action.run();
                }
            }
        });
        return button;
    }

    public boolean open() {
        return open("");
    }

    /** Opens on the editor's current Stage, without changing its caret or selection. */
    public boolean open(String query) {
        Stage stage = editor.getStage();
        if (disposed || commands.isDisposed() || editor.isDisabled() || editor.isPasswordMode() || stage == null
            || stage.getWidth() <= 0f || stage.getHeight() <= 0f) return false;
        if (open) {
            if (openStage == stage && getStage() == stage) {
                setQuery(query);
                refresh(false);
                toFront();
                if (!stage.setKeyboardFocus(queryField)) { close(); return false; }
                queryField.selectAll();
                return true;
            }
            close();
        }
        queryField.setText(query == null ? "" : query);
        previousKeyboardFocus = stage.getKeyboardFocus();
        previousScrollFocus = stage.getScrollFocus();
        openStage = stage;
        open = true;
        setVisible(true);
        stage.addActor(this);
        refresh(false);
        if (!open || disposed || commands.isDisposed() || getStage() != stage || editor.getStage() != stage) {
            close();
            return false;
        }
        if (!stage.setKeyboardFocus(queryField)) {
            close();
            return false;
        }
        if (!open || disposed || getStage() != stage) return false;
        queryField.selectAll();
        showKeyboard(true);
        return true;
    }

    public boolean isOpen() { return open; }
    public boolean isDisposed() { return disposed; }
    public String getQuery() { return queryField.getText(); }
    public TextField getQueryField() { return queryField; }
    public ScrollPane getResultsPane() { return scrollPane; }
    public String getStatusText() { return status.getText().toString(); }
    public int getResultCount() { return rows.size; }
    public int getSelectedIndex() { return selectedIndex; }
    public boolean isEnabledOnly() { return enabledOnly; }

    public void setQuery(String query) {
        queryField.setText(query == null ? "" : query);
    }

    public void setEnabledOnly(boolean enabledOnly) {
        if (this.enabledOnly == enabledOnly) return;
        this.enabledOnly = enabledOnly;
        refresh(true);
    }

    public void setExecutionListener(ExecutionListener listener) { executionListener = listener; }

    public CodeEditorCommand getSelectedCommand() {
        return selectedIndex < 0 || selectedIndex >= rows.size ? null : rows.get(selectedIndex).command;
    }

    /** Independent metadata snapshot, including unavailable commands unless enabledOnly is set. */
    public Array<CodeEditorCommand> getMatches() {
        Array<CodeEditorCommand> snapshot = new Array<>(rows.size);
        for (CommandRow row : rows) snapshot.add(row.command);
        return snapshot;
    }

    public void setSelectedIndex(int index) {
        selectedIndex = rows.size == 0 ? -1 : Math.max(0, Math.min(index, rows.size - 1));
        updateSelection();
    }

    /** Moves through all matches, wrapping at both ends; unavailable rows remain discoverable. */
    public void moveSelection(int delta) {
        if (rows.size == 0) return;
        long next = (selectedIndex + (long) delta) % rows.size;
        setSelectedIndex((int) (next < 0 ? next + rows.size : next));
    }

    public void refresh() { refresh(true); }

    private void refresh(boolean preserveSelection) {
        if (disposed) return;
        CodeEditorCommand selected = preserveSelection ? getSelectedCommand() : null;
        long revision = commands.getRevision();
        Array<CodeEditorCommand> matches = commands.findCommands(getQuery(), enabledOnly);
        Array<CommandRow> next = new Array<>(matches.size);
        boolean changed = matches.size != rows.size;
        for (int index = 0; index < matches.size; index++) {
            CodeEditorCommand command = matches.get(index);
            boolean enabled = commands.isEnabled(command.id);
            String shortcut = shortcutsFor(command.id);
            CommandRow previous = index < rows.size ? rows.get(index) : null;
            if (previous != null && previous.command == command && previous.enabled == enabled
                && previous.shortcut.equals(shortcut)) {
                next.add(previous);
            } else {
                next.add(new CommandRow(command, enabled, shortcut));
                changed = true;
            }
        }
        if (disposed) return; // A user-supplied condition may have unloaded the palette.
        if (changed) {
            results.clearChildren();
            rows.clear();
            rows.addAll(next);
            for (CommandRow row : rows) {
                results.add(row.button).growX().minWidth(0f).height(rowHeight());
                results.row();
            }
        }
        int selection = 0;
        if (selected != null) {
            for (int index = 0; index < rows.size; index++) {
                if (rows.get(index).command == selected) { selection = index; break; }
            }
        }
        selectedIndex = rows.size == 0 ? -1 : selection;
        lastRegistryRevision = revision;
        refreshElapsed = 0f;
        updateGeometry();
        updateSelection();
    }

    private String shortcutsFor(String id) {
        StringBuilder text = new StringBuilder();
        for (CodeKeyStroke stroke : commands.getShortcuts(id)) {
            if (text.length() > 0) text.append(", ");
            String chord = stroke.toString();
            text.append(editor.getKeymap().isCommandActsAsControl() ? chord.replace("Ctrl+", "Ctrl/Cmd+") : chord);
        }
        return text.toString();
    }

    private void updateSelection() {
        for (int index = 0; index < rows.size; index++) rows.get(index).button.setChecked(index == selectedIndex);
        CommandRow selected = selectedIndex < 0 ? null : rows.get(selectedIndex);
        runButton.setDisabled(selected == null || !selected.enabled);
        status.setText(rows.size == 0 ? commands.size() == 0 ? "No commands registered" : "No matching commands"
            : rows.size + (rows.size == 1 ? " command  |  " : " commands  |  ")
                + selected.command.id + (selected.enabled ? "" : " (unavailable)"));
        if (open && selected != null) {
            validate();
            scrollPane.validate();
            results.validate();
            scrollPane.scrollTo(0f, selected.button.getY(), selected.button.getWidth(), selected.button.getHeight(), false, false);
        }
    }

    private void updateGeometry() {
        if (!open || openStage == null) return;
        float margin = paletteStyle.edgeMargin;
        float width = Math.min(paletteStyle.maxWidth, Math.max(0f, openStage.getWidth() - 2f * margin));
        float maxHeight = Math.min(paletteStyle.maxHeight, Math.max(0f, openStage.getHeight() - 2f * margin));
        // A fixed cell height would also raise its minimum, pushing query/footer controls outside
        // a short window. Keep only a preferred height so the scrollable portion can shrink.
        resultsCell.prefHeight(rowHeight() * Math.min(Math.max(1, rows.size), paletteStyle.maxVisibleRows));
        setWidth(width);
        setHeight(Math.min(maxHeight, getPrefHeight()));
        setPosition((openStage.getWidth() - width) / 2f, Math.max(0f, openStage.getHeight() - getHeight() - margin));
    }

    /** Rechecks both identity and availability; a removed/reused ID never executes a stale row. */
    public boolean executeSelected() {
        if (!open || disposed || commands.isDisposed() || editor.isDisabled() || editor.isPasswordMode()) return false;
        CodeEditorCommand selected = getSelectedCommand();
        if (selected == null) return false;
        if (commands.getCommand(selected.id) != selected || !commands.isEnabled(selected.id)) {
            refresh();
            return false;
        }
        if (!open || disposed) return false;
        close();
        // Restoring focus can itself run host listeners which unregister or replace a command.
        boolean handled = commands.getCommand(selected.id) == selected && commands.execute(selected.id);
        if (executionListener != null) executionListener.executed(selected, handled);
        return handled;
    }

    @Override public void act(float delta) {
        if (open) {
            Actor focus = openStage.getKeyboardFocus();
            if (disposed || !isVisible() || commands.isDisposed() || editor.isDisabled() || editor.isPasswordMode()
                || getStage() != openStage || editor.getStage() != openStage
                || focus == null || !focus.isDescendantOf(this)) {
                close();
                return;
            }
            refreshElapsed += Math.max(0f, delta);
            if (commands.getRevision() != lastRegistryRevision || refreshElapsed >= paletteStyle.refreshInterval) refresh();
            updateGeometry();
        }
        super.act(delta);
    }

    public void close() {
        if (!open) { super.remove(); return; }
        open = false;
        Stage stage = openStage;
        openStage = null;
        Actor keyboard = previousKeyboardFocus;
        Actor scroll = previousScrollFocus;
        previousKeyboardFocus = null;
        previousScrollFocus = null;
        boolean restoreKeyboard = belongsToPalette(stage.getKeyboardFocus());
        boolean restoreScroll = belongsToPalette(stage.getScrollFocus());
        if (restoreKeyboard) showKeyboard(false);
        super.remove();
        if (restoreKeyboard && stage.getKeyboardFocus() == null) stage.setKeyboardFocus(restorable(keyboard, stage) ? keyboard
            : restorable(editor, stage) ? editor : null);
        if (restoreScroll && stage.getScrollFocus() == null) stage.setScrollFocus(restorable(scroll, stage) ? scroll : null);
    }

    private boolean belongsToPalette(Actor actor) {
        return actor != null && actor.isDescendantOf(this);
    }

    private boolean restorable(Actor actor, Stage stage) {
        if (actor == null || actor.getStage() != stage || belongsToPalette(actor)
            || (actor instanceof Disableable && ((Disableable) actor).isDisabled())
            || (actor == editor && editor.isDisabled())) return false;
        for (Actor ancestor = actor; ancestor != null; ancestor = ancestor.getParent()) {
            if (!ancestor.isVisible()) return false;
        }
        return true;
    }

    private void showKeyboard(boolean visible) {
        if (Gdx.app != null && (Gdx.app.getType() == ApplicationType.Android || Gdx.app.getType() == ApplicationType.iOS)) {
            queryField.getOnscreenKeyboard().show(visible);
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
        executionListener = null;
        rows.clear();
        results.clearChildren();
    }

    private final class CommandRow {
        final CodeEditorCommand command;
        final boolean enabled;
        final String shortcut;
        final TextButton button;

        CommandRow(CodeEditorCommand command, boolean enabled, String shortcut) {
            this.command = command;
            this.enabled = enabled;
            this.shortcut = shortcut;
            button = button(command.category.isEmpty() ? command.label : command.category + ": " + command.label, () -> {
                for (int index = 0; index < rows.size; index++) {
                    if (rows.get(index) == this) { setSelectedIndex(index); executeSelected(); break; }
                }
            });
            button.setDisabled(!enabled);
            button.getLabel().setAlignment(Align.left);
            button.getLabel().setEllipsis(true);
            button.getLabelCell().growX().minWidth(0f);
            Label hint = new Label(shortcut, paletteStyle.status);
            hint.setEllipsis(true);
            if (!enabled) hint.setColor(paletteStyle.buttons.disabledFontColor == null ? Color.GRAY
                : paletteStyle.buttons.disabledFontColor);
            button.add(hint).right().minWidth(0f).maxWidth(paletteStyle.maxWidth * 0.45f).padLeft(shortcut.isEmpty() ? 0f : 12f);
        }
    }
}
