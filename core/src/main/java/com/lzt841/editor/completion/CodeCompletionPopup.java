package com.lzt841.editor.completion;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.InputListener;
import com.badlogic.gdx.scenes.scene2d.utils.Drawable;
import com.badlogic.gdx.utils.Array;

/**
 * The built-in completion list. A plain {@link Actor} that draws a scrollable list of candidates and
 * reports clicks; it holds no editor state and takes no keyboard focus, because
 * {@link CodeCompletionController} routes keys to it.
 *
 * <p>Positioning is the caller's job: {@link CodeCompletionController} places it near the caret and
 * flips it above when there is not enough room below.
 *
 * <p>Replace the visuals either by setting fields on {@link CodeCompletionPopupStyle} or by
 * subclassing and overriding {@link #drawItem}.
 */
public class CodeCompletionPopup extends Actor {
    /** Visual configuration. Every drawable may be null, in which case that layer is skipped. */
    public static class CodeCompletionPopupStyle {
        public BitmapFont font;
        public Drawable background;
        public Drawable selection;
        public Drawable scrollbarTrack;
        public Drawable scrollbarKnob;
        public Color labelColor = new Color(0.85f, 0.89f, 0.94f, 1f);
        public Color selectedLabelColor = new Color(1f, 1f, 1f, 1f);
        public Color detailColor = new Color(0.55f, 0.63f, 0.71f, 1f);
        public Color matchColor = new Color(0.369f, 0.709f, 0.992f, 1f);
        /** Per-kind label colour; null entries fall back to {@link #labelColor}. */
        public Color[] kindColors;
        public float itemHeight = 22f;
        public float horizontalPadding = 8f;
        public float detailGap = 16f;
        public float scrollbarWidth = 6f;
        public float maxWidth = 520f;
        public float minWidth = 160f;
        /**
         * Baseline nudge, matching {@code CodeEditorStyle.textBaselineOffset}. Tweak this if the row
         * text sits high or low for your font.
         */
        public float textBaselineOffset = -6f;
        /** Rows shown before the list scrolls. */
        public int maxVisibleItems = 10;
    }

    private final Array<CodeCompletionItem> items = new Array<>();
    private final GlyphLayout glyphLayout = new GlyphLayout();
    private CodeCompletionPopupStyle style;
    private String prefix = "";
    private int selectedIndex;
    private int scrollOffset;
    private ItemActivationListener activationListener;
    private SelectionListener selectionListener;

    public CodeCompletionPopup(CodeCompletionPopupStyle style) {
        setStyle(style);
        setVisible(false);
        setTouchable(com.badlogic.gdx.scenes.scene2d.Touchable.enabled);
        addListener(new InputListener() {
            @Override
            public boolean touchDown(InputEvent event, float x, float y, int pointer, int button) {
                int index = indexAt(y);
                if (index < 0) {
                    return false;
                }
                setSelectedIndex(index);
                return true;
            }

            @Override
            public void touchUp(InputEvent event, float x, float y, int pointer, int button) {
                int index = indexAt(y);
                if (index >= 0 && index == selectedIndex && activationListener != null) {
                    activationListener.onItemActivated(items.get(index));
                }
            }

            @Override
            public boolean scrolled(InputEvent event, float x, float y, float amountX, float amountY) {
                scrollBy((int) Math.signum(amountY));
                return true;
            }
        });
    }

    public void setStyle(CodeCompletionPopupStyle style) {
        if (style == null || style.font == null) {
            throw new IllegalArgumentException("CodeCompletionPopupStyle and its font must not be null");
        }
        this.style = style;
        invalidateSize();
    }

    public CodeCompletionPopupStyle getStyle() {
        return style;
    }

    /** Notified when an item is clicked or otherwise activated. */
    public void setActivationListener(ItemActivationListener listener) {
        this.activationListener = listener;
    }

    /**
     * Notified whenever the highlighted row changes. {@link CodeCompletionController} uses this slot to
     * keep its documentation panel in step, so replace it only if you are driving the popup yourself.
     */
    public void setSelectionListener(SelectionListener listener) {
        this.selectionListener = listener;
    }

    /**
     * Replaces the list contents and resets the selection to the first row.
     *
     * @param items candidates, already filtered and sorted
     * @param prefix the typed prefix, highlighted inside each label
     */
    public void setItems(Array<CodeCompletionItem> items, String prefix) {
        this.items.clear();
        if (items != null) {
            this.items.addAll(items);
        }
        this.prefix = prefix == null ? "" : prefix;
        selectedIndex = 0;
        scrollOffset = 0;
        invalidateSize();
        notifySelectionChanged();
    }

    public Array<CodeCompletionItem> getItems() {
        return items;
    }

    public boolean isEmpty() {
        return items.size == 0;
    }

    public int getSelectedIndex() {
        return selectedIndex;
    }

    /** Currently highlighted item, or null when the list is empty. */
    public CodeCompletionItem getSelectedItem() {
        return items.size == 0 ? null : items.get(clampIndex(selectedIndex));
    }

    public void setSelectedIndex(int index) {
        if (items.size == 0) {
            selectedIndex = 0;
            return;
        }
        int previous = selectedIndex;
        selectedIndex = clampIndex(index);
        scrollSelectionIntoView();
        if (selectedIndex != previous) {
            notifySelectionChanged();
        }
    }

    /** Moves the selection by {@code delta} rows, wrapping at both ends. */
    public void moveSelection(int delta) {
        if (items.size == 0) {
            return;
        }
        int previous = selectedIndex;
        int next = selectedIndex + delta;
        while (next < 0) {
            next += items.size;
        }
        selectedIndex = next % items.size;
        scrollSelectionIntoView();
        if (selectedIndex != previous) {
            notifySelectionChanged();
        }
    }

    /** Number of rows currently drawn. */
    public int getVisibleItemCount() {
        return Math.min(items.size, Math.max(1, style.maxVisibleItems));
    }

    private int clampIndex(int index) {
        return Math.max(0, Math.min(index, items.size - 1));
    }

    private void scrollBy(int rows) {
        int maxOffset = Math.max(0, items.size - getVisibleItemCount());
        scrollOffset = Math.max(0, Math.min(scrollOffset + rows, maxOffset));
    }

    private void scrollSelectionIntoView() {
        int visible = getVisibleItemCount();
        if (selectedIndex < scrollOffset) {
            scrollOffset = selectedIndex;
        } else if (selectedIndex >= scrollOffset + visible) {
            scrollOffset = selectedIndex - visible + 1;
        }
        scrollOffset = Math.max(0, Math.min(scrollOffset, Math.max(0, items.size - visible)));
    }

    /** Row index at a local y, or -1 when outside the list. */
    private int indexAt(float localY) {
        if (items.size == 0) {
            return -1;
        }
        float fromTop = getHeight() - localY;
        int row = (int) (fromTop / style.itemHeight);
        if (row < 0 || row >= getVisibleItemCount()) {
            return -1;
        }
        int index = scrollOffset + row;
        return index < items.size ? index : -1;
    }

    /** Recomputes the actor size from the widest visible label. */
    public void invalidateSize() {
        float widest = style.minWidth;
        for (int i = 0; i < items.size; i++) {
            CodeCompletionItem item = items.get(i);
            float width = measure(item.label) + style.horizontalPadding * 2f;
            if (!item.detail.isEmpty()) {
                width += style.detailGap + measure(item.detail);
            }
            widest = Math.max(widest, width);
        }
        boolean scrolling = items.size > getVisibleItemCount();
        if (scrolling) {
            widest += style.scrollbarWidth;
        }
        setSize(Math.min(widest, style.maxWidth), getVisibleItemCount() * style.itemHeight);
    }

    private float measure(String text) {
        if (text == null || text.isEmpty()) {
            return 0f;
        }
        glyphLayout.setText(style.font, text);
        return glyphLayout.width;
    }

    @Override
    public void draw(Batch batch, float parentAlpha) {
        if (items.size == 0) {
            return;
        }
        if (style.background != null) {
            style.background.draw(batch, getX(), getY(), getWidth(), getHeight());
        }

        int visible = getVisibleItemCount();
        float rowWidth = getWidth() - (items.size > visible ? style.scrollbarWidth : 0f);
        for (int row = 0; row < visible; row++) {
            int index = scrollOffset + row;
            if (index >= items.size) {
                break;
            }
            float rowTop = getY() + getHeight() - row * style.itemHeight;
            float rowBottom = rowTop - style.itemHeight;
            boolean selected = index == selectedIndex;
            if (selected && style.selection != null) {
                style.selection.draw(batch, getX(), rowBottom, rowWidth, style.itemHeight);
            }
            drawItem(batch, items.get(index), getX(), rowBottom, rowWidth, style.itemHeight, selected);
        }

        if (items.size > visible) {
            drawScrollbar(batch, visible);
        }
    }

    /**
     * Draws one row. Override to change the row's appearance while keeping the list behaviour;
     * {@code x}/{@code y} are the row's bottom-left in stage coordinates.
     */
    protected void drawItem(
        Batch batch,
        CodeCompletionItem item,
        float x,
        float y,
        float width,
        float height,
        boolean selected
    ) {
        // Same baseline rule the editor uses for its rows, so the popup lines up with the code.
        float baseline = y + style.font.getLineHeight() + style.textBaselineOffset
            + (height - style.font.getLineHeight()) * 0.5f;
        float textX = x + style.horizontalPadding;

        Color labelColor = selected ? style.selectedLabelColor : colorForKind(item.kind);
        int matchLength = matchedPrefixLength(item);
        if (matchLength > 0) {
            String head = item.label.substring(0, matchLength);
            style.font.setColor(style.matchColor);
            style.font.draw(batch, head, textX, baseline);
            float headWidth = measure(head);
            style.font.setColor(labelColor);
            style.font.draw(batch, item.label.substring(matchLength), textX + headWidth, baseline);
        } else {
            style.font.setColor(labelColor);
            style.font.draw(batch, item.label, textX, baseline);
        }

        if (!item.detail.isEmpty()) {
            float detailWidth = measure(item.detail);
            float detailX = x + width - style.horizontalPadding - detailWidth;
            if (detailX > textX + measure(item.label) + style.detailGap * 0.5f) {
                style.font.setColor(style.detailColor);
                style.font.draw(batch, item.detail, detailX, baseline);
            }
        }
    }

    /**
     * How many leading characters of the label the typed prefix matches, for highlighting. Only a
     * case-insensitive leading match is highlighted; a provider doing fuzzy matching should set
     * {@link CodeCompletionItem#filterText} accordingly.
     */
    private int matchedPrefixLength(CodeCompletionItem item) {
        if (prefix.isEmpty() || item.label.length() < prefix.length()) {
            return 0;
        }
        for (int i = 0; i < prefix.length(); i++) {
            if (Character.toLowerCase(item.label.charAt(i)) != Character.toLowerCase(prefix.charAt(i))) {
                return 0;
            }
        }
        return prefix.length();
    }

    private Color colorForKind(CodeCompletionItemKind kind) {
        if (style.kindColors == null || kind == null) {
            return style.labelColor;
        }
        int ordinal = kind.ordinal();
        if (ordinal >= style.kindColors.length || style.kindColors[ordinal] == null) {
            return style.labelColor;
        }
        return style.kindColors[ordinal];
    }

    private void drawScrollbar(Batch batch, int visible) {
        float trackX = getX() + getWidth() - style.scrollbarWidth;
        if (style.scrollbarTrack != null) {
            style.scrollbarTrack.draw(batch, trackX, getY(), style.scrollbarWidth, getHeight());
        }
        if (style.scrollbarKnob == null) {
            return;
        }
        float knobHeight = Math.max(style.itemHeight, getHeight() * visible / (float) items.size);
        int maxOffset = Math.max(1, items.size - visible);
        float travel = getHeight() - knobHeight;
        float knobY = getY() + travel * (1f - scrollOffset / (float) maxOffset);
        style.scrollbarKnob.draw(batch, trackX, knobY, style.scrollbarWidth, knobHeight);
    }

    private void notifySelectionChanged() {
        if (selectionListener != null) {
            selectionListener.onSelectionChanged(this, getSelectedItem());
        }
    }

    /** Notified when the user activates an item. */
    public interface ItemActivationListener {
        void onItemActivated(CodeCompletionItem item);
    }

    /** Notified when the highlighted row changes, including when the list is replaced. */
    public interface SelectionListener {
        /** @param item the newly selected item, or null when the list became empty */
        void onSelectionChanged(CodeCompletionPopup popup, CodeCompletionItem item);
    }
}
