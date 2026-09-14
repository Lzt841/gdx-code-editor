package com.lzt841.editor.completion;

import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.utils.Array;
import com.lzt841.editor.CodeEditor;
import com.lzt841.editor.CodeEditorCaretListener;
import com.lzt841.editor.CodeEditorInputInterceptor;
import com.lzt841.editor.CodeEditorAction;
import com.lzt841.editor.CodeEditorPosition;
import com.lzt841.editor.CodeEditorTextRange;
import com.lzt841.editor.CodeKeymap;
import com.lzt841.editor.hints.CodeHintPanel;

/**
 * Drives completion for one {@link CodeEditor}: trigger, request, filter, popup placement, key
 * handling and commit.
 *
 * <p>Attach it with {@link #install()} after the editor is on a {@link Stage}. It adds itself as a
 * caret listener and adds the popup to the stage, so it is self-contained. Call
 * {@link #uninstall()} if you destroy the editor.
 *
 * <p>A typical setup:
 *
 * <pre>{@code
 * CodeCompletionController completion = new CodeCompletionController(editor, myProvider);
 * completion.install();
 * }</pre>
 *
 * <p>The editor keeps keyboard focus. {@link #install()} registers a
 * {@link CodeEditorInputInterceptor}, so the editor offers keys to the popup first and only handles
 * them itself when the popup declines.
 */
public class CodeCompletionController {
    private static final int DEFAULT_MAX_VISIBLE = 10;

    private final CodeEditor editor;
    private final CodeCompletionProvider provider;
    private final CodeCompletionPopup popup;
    private final Vector2 scratch = new Vector2();
    private final Array<CodeCompletionItem> filtered = new Array<>();
    private final Array<CodeCompletionItem> lastRawItems = new Array<>();
    private final CaretWatcher caretWatcher = new CaretWatcher();
    private final CodeEditorInputInterceptor inputInterceptor = new CodeEditorInputInterceptor() {
        @Override
        public boolean onKeyDown(CodeEditor source, int keycode) {
            return handleKeyDown(keycode);
        }

        @Override
        public void afterKeyTyped(CodeEditor source, char character) {
            onCharacterTyped(character);
        }
    };

    private int requestSerial;
    private int lastRequestVersion = -1;
    private boolean open;
    private boolean installed;
    private CodeEditorTextRange replaceRange;
    private CodeSnippetSession snippetSession;
    private String currentPrefix = "";
    private char[] extraTriggerCharacters = new char[0];
    private int maxVisibleItems = DEFAULT_MAX_VISIBLE;
    /** Created on first use, so a setup that never supplies documentation pays nothing for it. */
    private CodeHintPanel documentationPanel;
    private CodeCompletionItem documentedItem;
    private boolean documentationEnabled = true;
    private float documentationGap = 6f;
    private float documentationMaxWidth = 360f;
    /** Chords used while the list is open. Separate from the editor's map, so Up/Down do not clash. */
    private CodeKeymap popupKeymap = CodeKeymap.completionDefaults();

    public CodeCompletionController(CodeEditor editor, CodeCompletionProvider provider) {
        this(editor, provider, null);
    }

    public CodeCompletionController(
        CodeEditor editor,
        CodeCompletionProvider provider,
        CodeCompletionPopup popup
    ) {
        if (editor == null || provider == null) {
            throw new IllegalArgumentException("editor and provider must not be null");
        }
        this.editor = editor;
        this.provider = provider;
        this.popup = popup != null ? popup : new CodeCompletionPopup(defaultPopupStyle(editor));
        this.popup.setActivationListener(new CodeCompletionPopup.ItemActivationListener() {
            @Override
            public void onItemActivated(CodeCompletionItem item) {
                accept(item);
            }
        });
        this.popup.setSelectionListener(new CodeCompletionPopup.SelectionListener() {
            @Override
            public void onSelectionChanged(CodeCompletionPopup source, CodeCompletionItem item) {
                syncDocumentationPanel();
            }
        });
    }

    /** Adds the popup to the stage and starts listening for caret movement. */
    public void install() {
        if (installed) {
            return;
        }
        Stage stage = editor.getStage();
        if (stage != null && popup.getStage() != stage) {
            stage.addActor(popup);
        }
        editor.addCaretListener(caretWatcher);
        editor.addInputInterceptor(inputInterceptor);
        installed = true;
    }

    public void uninstall() {
        if (!installed) {
            return;
        }
        cancelSnippet();
        editor.removeCaretListener(caretWatcher);
        editor.removeInputInterceptor(inputInterceptor);
        popup.remove();
        if (documentationPanel != null) {
            documentationPanel.remove();
        }
        dismiss();
        installed = false;
    }

    public CodeCompletionPopup getPopup() {
        return popup;
    }

    public boolean isOpen() {
        return open;
    }

    /**
     * Extra characters that should open the popup, in addition to those the provider reports via
     * {@link CodeCompletionProvider#isTriggerCharacter(char)}.
     */
    public void setExtraTriggerCharacters(char... characters) {
        extraTriggerCharacters = characters == null ? new char[0] : characters;
    }

    /**
     * The keymap used while the list is open. Never null. Mutate it to rebind popup navigation:
     *
     * <pre>{@code
     * completion.getPopupKeymap().unbindAction(CodeEditorAction.COMPLETION_ACCEPT)
     *     .bind(com.badlogic.gdx.Input.Keys.ENTER, CodeEditorAction.COMPLETION_ACCEPT);   // Enter, not Tab
     * }</pre>
     *
     * <p>The chord that <em>opens</em> the list is not here: it is
     * {@link CodeEditorAction#COMPLETION_TRIGGER} in the editor's own keymap, because the popup is
     * closed at that point.
     */
    public CodeKeymap getPopupKeymap() {
        return popupKeymap;
    }

    /** Replaces the popup keymap; null restores {@link CodeKeymap#completionDefaults()}. */
    public void setPopupKeymap(CodeKeymap popupKeymap) {
        this.popupKeymap = popupKeymap != null ? popupKeymap : CodeKeymap.completionDefaults();
    }

    public void setMaxVisibleItems(int maxVisibleItems) {
        this.maxVisibleItems = Math.max(1, maxVisibleItems);
        popup.getStyle().maxVisibleItems = this.maxVisibleItems;
    }

    /**
     * Whether the documentation side panel is shown for candidates that carry
     * {@link CodeCompletionItem#documentation}. On by default; items without documentation never show
     * a panel either way, so leaving it on costs nothing until a provider fills the field.
     */
    public void setDocumentationEnabled(boolean documentationEnabled) {
        this.documentationEnabled = documentationEnabled;
        if (!documentationEnabled) {
            hideDocumentation();
        } else {
            syncDocumentationPanel();
        }
    }

    public boolean isDocumentationEnabled() {
        return documentationEnabled;
    }

    /**
     * The documentation panel, created on first call. Use it to restyle the panel; its text, size and
     * position are managed here and will be overwritten.
     */
    public CodeHintPanel getDocumentationPanel() {
        if (documentationPanel == null) {
            CodeHintPanel.CodeHintPanelStyle style = CodeHintPanel.styleFrom(editor);
            style.maxWidth = documentationMaxWidth;
            documentationPanel = new CodeHintPanel(style);
        }
        return documentationPanel;
    }

    /** Gap in pixels between the list and the documentation panel. */
    public void setDocumentationGap(float documentationGap) {
        this.documentationGap = Math.max(0f, documentationGap);
    }

    /** Wrap width of the documentation panel. Wider panels wrap less but crowd the code more. */
    public void setDocumentationMaxWidth(float documentationMaxWidth) {
        this.documentationMaxWidth = Math.max(80f, documentationMaxWidth);
        if (documentationPanel != null) {
            CodeHintPanel.CodeHintPanelStyle style = documentationPanel.getStyle();
            style.maxWidth = this.documentationMaxWidth;
            // Re-applying the style re-wraps the current text at the new width, so the panel is
            // already the right size here; the sync below only has to place it again.
            documentationPanel.setStyle(style);
            syncDocumentationPanel();
        }
    }

    /** Opens the popup at the caret, asking the provider for a fresh list. */
    public void trigger(CodeCompletionTrigger reason, char triggerCharacter) {
        if (editor.isReadOnly() || editor.isDisabled()) {
            return;
        }
        if (editor.isInStringOrComment(editor.getCursorLine(), editor.getCursorColumn())) {
            if (reason != CodeCompletionTrigger.MANUAL) {
                return;
            }
        }
        request(reason, triggerCharacter);
    }

    public void triggerManually() {
        trigger(CodeCompletionTrigger.MANUAL, '\0');
    }

    public void dismiss() {
        requestSerial++;
        open = false;
        popup.setVisible(false);
        popup.setItems(null, "");
        hideDocumentation();
        lastRawItems.clear();
        filtered.clear();
        replaceRange = null;
        currentPrefix = "";
    }

    /**
     * Called by the editor for every key down. Returns true when the popup consumed the key, in
     * which case the editor should not also handle it.
     */
    public boolean handleKeyDown(int keycode) {
        if (!open) {
            // The opening chord lives in the editor's keymap, since no popup exists yet to own it.
            if (editor.getKeymap().actionForCurrentKeyDown(keycode) == CodeEditorAction.COMPLETION_TRIGGER) {
                triggerManually();
                return true;
            }
            return false;
        }

        CodeEditorAction action = popupKeymap.actionForCurrentKeyDown(keycode);
        if (action == null) {
            return false;
        }
        switch (action) {
            case COMPLETION_PREVIOUS:
                popup.moveSelection(-1);
                return true;
            case COMPLETION_NEXT:
                popup.moveSelection(1);
                return true;
            case COMPLETION_PAGE_UP:
                popup.moveSelection(-popup.getVisibleItemCount());
                return true;
            case COMPLETION_PAGE_DOWN:
                popup.moveSelection(popup.getVisibleItemCount());
                return true;
            case COMPLETION_ACCEPT:
                return acceptSelected();
            case COMPLETION_DISMISS:
                dismiss();
                return true;
            case COMPLETION_FIRST:
                popup.setSelectedIndex(0);
                return true;
            case COMPLETION_LAST:
                popup.setSelectedIndex(popup.getItems().size - 1);
                return true;
            default:
                return false;
        }
    }

    /**
     * Called by the editor after a character is typed. Opens or refreshes the popup when the
     * character is a trigger, and dismisses it when the character is a word terminator.
     */
    public void onCharacterTyped(char character) {
        if (isTriggerCharacter(character)) {
            trigger(CodeCompletionTrigger.CHARACTER, character);
            return;
        }
        if (open && !isWordCharacter(character) && !isTriggerCharacter(character)) {
            dismiss();
        }
    }

    /** Commits the currently selected item. */
    public boolean acceptSelected() {
        CodeCompletionItem item = popup.getSelectedItem();
        if (item == null) {
            dismiss();
            return false;
        }
        return accept(item);
    }

    public boolean accept(CodeCompletionItem item) {
        if (item == null) {
            return false;
        }
        CodeEditorTextRange range = replaceRange;
        if (range == null) {
            range = editor.getWordRangeAtCursor();
        }
        if (item.isSnippet()) {
            // Dismissed first, so the popup is gone before the session installs its own interceptor and
            // takes Tab. Accepting leaves the caret inside the inserted text, which would otherwise
            // look to the caret watcher like a reason to re-filter a list that is on its way out.
            dismiss();
            return acceptSnippet(item, range);
        }
        boolean ok;
        if (range != null) {
            ok = editor.replaceRange(range, item.insertText);
        } else {
            ok = editor.insertTextAtCursor(item.insertText);
        }
        dismiss();
        return ok;
    }

    /**
     * Inserts a snippet item and keeps the session, so Tab reaches it while the popup is closed.
     *
     * <p>A snippet with nothing to navigate returns no session: the text is inserted, the caret goes to
     * {@code $0}, and nothing is left installed.
     */
    private boolean acceptSnippet(CodeCompletionItem item, CodeEditorTextRange range) {
        cancelSnippet();
        int versionBefore = editor.getDocumentVersion();
        CodeSnippetSession session = CodeSnippetSession.start(editor, item.insertText, range);
        if (session == null) {
            // Either the insert failed or the snippet had no stops. The version tells which, and only
            // the first is a failure to report.
            return editor.getDocumentVersion() != versionBefore;
        }
        snippetSession = session;
        session.setFinishListener(new CodeSnippetSession.FinishListener() {
            @Override
            public void onSnippetFinished(CodeSnippetSession finished, boolean completed) {
                if (snippetSession == finished) {
                    snippetSession = null;
                }
            }
        });
        return true;
    }

    /** The live snippet session, or null when no snippet is being filled in. */
    public CodeSnippetSession getSnippetSession() {
        return snippetSession != null && snippetSession.isActive() ? snippetSession : null;
    }

    /** Ends any live snippet session, leaving its text in place. */
    public void cancelSnippet() {
        CodeSnippetSession session = snippetSession;
        snippetSession = null;
        if (session != null) {
            session.cancel();
        }
    }

    /**
     * Asks the provider for candidates. Each request carries a serial; a response whose serial is no
     * longer current is discarded, so a slow provider cannot repopulate a popup the user has since
     * dismissed or moved away from.
     */
    private void request(CodeCompletionTrigger reason, char triggerCharacter) {
        final int serial = ++requestSerial;
        int line = editor.getCursorLine();
        int column = editor.getCursorColumn();
        String prefix = editor.getWordPrefixAtCursor();
        CodeEditorTextRange word = editor.getWordRangeAtCursor();
        // Only the part up to the caret is being replaced; a suffix after the caret is left alone.
        CodeEditorTextRange target = word == null
            ? new CodeEditorTextRange(line, column, line, column)
            : new CodeEditorTextRange(word.startLine, word.startColumn, line, column);

        lastRequestVersion = editor.getDocumentVersion();
        final CodeCompletionRequest request = new CodeCompletionRequest(
            editor,
            line,
            column,
            editor.getCursorOffset(),
            lastRequestVersion,
            prefix,
            target,
            reason,
            triggerCharacter
        );

        provider.provide(request, new CodeCompletionResponse() {
            private boolean delivered;

            @Override
            public void complete(Array<CodeCompletionItem> items) {
                if (delivered || serial != requestSerial) {
                    return;
                }
                delivered = true;
                onItemsReady(request, items);
            }
        });
    }

    private void onItemsReady(CodeCompletionRequest request, Array<CodeCompletionItem> items) {
        lastRawItems.clear();
        if (items != null) {
            lastRawItems.addAll(items);
        }
        replaceRange = request.replaceRange;
        currentPrefix = request.prefix;
        refilter();
    }

    /**
     * Re-filters the last provider result against the current prefix. Lets typing narrow the list
     * without another provider round trip, which matters when the provider is expensive.
     */
    private void refilter() {
        filtered.clear();
        String prefix = currentPrefix;
        for (int i = 0; i < lastRawItems.size; i++) {
            CodeCompletionItem item = lastRawItems.get(i);
            if (matches(item, prefix)) {
                filtered.add(item);
            }
        }
        sortByRelevance(filtered, prefix);

        if (filtered.size == 0) {
            open = false;
            popup.setVisible(false);
            hideDocumentation();
            return;
        }

        popup.getStyle().maxVisibleItems = maxVisibleItems;
        popup.setItems(filtered, prefix);
        open = true;
        popup.setVisible(true);
        popup.toFront();
        reposition();
        // setItems already fired the selection listener, but the popup had not moved yet; sync again
        // now that it has, so the panel lands beside the popup's final position.
        syncDocumentationPanel();
    }

    /** Case-insensitive leading match; empty prefix matches everything. */
    private boolean matches(CodeCompletionItem item, String prefix) {
        if (prefix.isEmpty()) {
            return true;
        }
        String text = item.matchText();
        if (text.length() < prefix.length()) {
            return false;
        }
        for (int i = 0; i < prefix.length(); i++) {
            if (Character.toLowerCase(text.charAt(i)) != Character.toLowerCase(prefix.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    /**
     * Orders by exact-case prefix match, then declared priority, then length, then alphabetically.
     * Insertion sort: the list is short and usually already nearly sorted.
     */
    private void sortByRelevance(Array<CodeCompletionItem> list, String prefix) {
        for (int i = 1; i < list.size; i++) {
            CodeCompletionItem current = list.get(i);
            int j = i - 1;
            while (j >= 0 && compare(list.get(j), current, prefix) > 0) {
                list.set(j + 1, list.get(j));
                j--;
            }
            list.set(j + 1, current);
        }
    }

    private int compare(CodeCompletionItem a, CodeCompletionItem b, String prefix) {
        boolean exactA = !prefix.isEmpty() && a.matchText().startsWith(prefix);
        boolean exactB = !prefix.isEmpty() && b.matchText().startsWith(prefix);
        if (exactA != exactB) {
            return exactA ? -1 : 1;
        }
        if (a.priority != b.priority) {
            return b.priority - a.priority;
        }
        int lengthDelta = a.matchText().length() - b.matchText().length();
        if (lengthDelta != 0) {
            return lengthDelta;
        }
        return a.label.compareTo(b.label);
    }

    /**
     * Places the popup under the caret, flipping above it when there is not enough room below and
     * pulling it left when it would run past the stage's right edge.
     */
    private void reposition() {
        Stage stage = editor.getStage();
        if (stage == null) {
            return;
        }
        if (popup.getStage() != stage) {
            stage.addActor(popup);
        }
        if (editor.getStagePositionAt(editor.getCursorLine(), editor.getCursorColumn(), scratch) == null) {
            dismiss();
            return;
        }

        popup.invalidateSize();
        float width = popup.getWidth();
        float height = popup.getHeight();
        float x = scratch.x;
        float belowY = scratch.y - height;
        float aboveY = scratch.y + editor.getLineHeight();

        float y;
        if (belowY >= 0f) {
            y = belowY;
        } else if (aboveY + height <= stage.getViewport().getWorldHeight()) {
            y = aboveY;
        } else {
            // Neither side fits: keep it on screen, preferring to clip the bottom.
            y = Math.max(0f, Math.min(belowY, stage.getViewport().getWorldHeight() - height));
        }

        float maxX = stage.getViewport().getWorldWidth() - width;
        popup.setPosition(Math.max(0f, Math.min(x, maxX)), y);
    }

    private void hideDocumentation() {
        documentedItem = null;
        if (documentationPanel != null) {
            documentationPanel.setVisible(false);
            documentationPanel.setText("");
        }
    }

    /**
     * Shows, hides and places the documentation panel for the current selection. Called on every
     * selection change and after every reposition, so it must stay cheap: the wrapped text is only
     * rebuilt when the selected item actually changed.
     */
    private void syncDocumentationPanel() {
        if (!documentationEnabled || !open || !popup.isVisible()) {
            hideDocumentation();
            return;
        }
        CodeCompletionItem item = popup.getSelectedItem();
        if (item == null || item.documentation.isEmpty()) {
            hideDocumentation();
            return;
        }
        Stage stage = editor.getStage();
        if (stage == null) {
            hideDocumentation();
            return;
        }

        CodeHintPanel panel = getDocumentationPanel();
        if (panel.getStage() != stage) {
            stage.addActor(panel);
        }
        if (item != documentedItem) {
            documentedItem = item;
            panel.setText(item.documentation);
        }
        if (panel.getWidth() <= 0f || panel.getHeight() <= 0f) {
            panel.setVisible(false);
            return;
        }
        panel.setVisible(true);
        // Panel first, then popup: where they overlap on a narrow stage the list stays readable.
        panel.toFront();
        popup.toFront();
        positionDocumentationPanel(stage, panel);
    }

    /**
     * Puts the panel beside the list, preferring the right side and falling back to the left when the
     * stage runs out of room. Vertically it is top-aligned with the list, clamped into the stage.
     */
    private void positionDocumentationPanel(Stage stage, CodeHintPanel panel) {
        float worldWidth = stage.getViewport().getWorldWidth();
        float worldHeight = stage.getViewport().getWorldHeight();
        float width = panel.getWidth();
        float height = panel.getHeight();

        float right = popup.getX() + popup.getWidth() + documentationGap;
        float left = popup.getX() - documentationGap - width;
        float x;
        if (right + width <= worldWidth) {
            x = right;
        } else if (left >= 0f) {
            x = left;
        } else {
            // Neither side fits; keep it on stage and let it overlap the list.
            x = Math.max(0f, worldWidth - width);
        }

        float top = popup.getY() + popup.getHeight();
        float y = top - height;
        y = Math.max(0f, Math.min(y, worldHeight - height));
        panel.setPosition(x, y);
    }

    private boolean isTriggerCharacter(char character) {
        if (provider.isTriggerCharacter(character)) {
            return true;
        }
        for (char candidate : extraTriggerCharacters) {
            if (candidate == character) {
                return true;
            }
        }
        return false;
    }

    private static boolean isWordCharacter(char character) {
        return Character.isLetterOrDigit(character) || character == '_' || character == '$';
    }

    /** Default style, derived from the editor's own so the popup matches without extra setup. */
    private static CodeCompletionPopup.CodeCompletionPopupStyle defaultPopupStyle(CodeEditor editor) {
        CodeEditor.CodeEditorStyle editorStyle = editor.getStyle();
        CodeCompletionPopup.CodeCompletionPopupStyle style =
            new CodeCompletionPopup.CodeCompletionPopupStyle();
        style.font = editorStyle.font;
        style.background = editorStyle.focusedBackground != null
            ? editorStyle.focusedBackground
            : editorStyle.background;
        style.selection = editorStyle.selection;
        style.scrollbarTrack = editorStyle.scrollbarTrack;
        style.scrollbarKnob = editorStyle.scrollbarKnob;
        style.labelColor.set(editorStyle.fontColor);
        style.detailColor.set(editorStyle.gutterFontColor);
        style.matchColor.set(editorStyle.keywordColor);
        style.itemHeight = Math.max(16f, editorStyle.font.getLineHeight() + 6f);
        style.textBaselineOffset = editorStyle.textBaselineOffset;
        style.scrollbarWidth = editorStyle.scrollbarWidth;
        style.kindColors = buildKindColours(editorStyle);
        return style;
    }

    private static com.badlogic.gdx.graphics.Color[] buildKindColours(CodeEditor.CodeEditorStyle s) {
        com.badlogic.gdx.graphics.Color[] colours =
            new com.badlogic.gdx.graphics.Color[CodeCompletionItemKind.values().length];
        colours[CodeCompletionItemKind.KEYWORD.ordinal()] = s.keywordColor;
        colours[CodeCompletionItemKind.CLASS.ordinal()] = s.typeColor;
        colours[CodeCompletionItemKind.INTERFACE.ordinal()] = s.typeColor;
        colours[CodeCompletionItemKind.ENUM.ordinal()] = s.typeColor;
        colours[CodeCompletionItemKind.STRUCT.ordinal()] = s.typeColor;
        colours[CodeCompletionItemKind.TYPE_PARAMETER.ordinal()] = s.typeColor;
        colours[CodeCompletionItemKind.METHOD.ordinal()] = s.annotationColor;
        colours[CodeCompletionItemKind.FUNCTION.ordinal()] = s.annotationColor;
        colours[CodeCompletionItemKind.CONSTRUCTOR.ordinal()] = s.annotationColor;
        colours[CodeCompletionItemKind.CONSTANT.ordinal()] = s.literalColor;
        colours[CodeCompletionItemKind.ENUM_MEMBER.ordinal()] = s.literalColor;
        colours[CodeCompletionItemKind.SNIPPET.ordinal()] = s.stringColor;
        return colours;
    }

    /**
     * Keeps the popup in step with the caret. Edits are handled by {@link #onCharacterTyped} (for
     * trigger and terminator characters) and by a re-filter here (for ordinary typing). A caret
     * move that is not an edit — an arrow key, a click — dismisses, because the user has left the
     * word they were completing.
     */
    private final class CaretWatcher implements CodeEditorCaretListener {
        @Override
        public void onCaretMoved(
            CodeEditor source,
            CodeEditorPosition position,
            CodeEditorTextRange selection,
            boolean causedByEdit
        ) {
            if (!open) {
                return;
            }
            if (!causedByEdit || replaceRange == null || selection != null) {
                dismiss();
                return;
            }
            if (position.line != replaceRange.startLine || position.column < replaceRange.startColumn) {
                dismiss();
                return;
            }
            currentPrefix = source.getWordPrefixAt(position.line, position.column);
            replaceRange = new CodeEditorTextRange(
                replaceRange.startLine,
                replaceRange.startColumn,
                position.line,
                position.column
            );
            refilter();
        }
    }
}
