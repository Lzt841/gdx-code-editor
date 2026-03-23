package com.lzt841.editor;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.Application.ApplicationType;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.InputListener;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.Widget;
import com.badlogic.gdx.scenes.scene2d.utils.Drawable;
import com.badlogic.gdx.utils.Align;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.IntMap;
import com.badlogic.gdx.utils.IntFloatMap;
import com.badlogic.gdx.utils.IntArray;
import com.badlogic.gdx.utils.ObjectSet;
import com.badlogic.gdx.utils.TimeUtils;
import com.lzt841.editor.highlight.CodeHighlighter;
import com.lzt841.editor.highlight.CodeBracketIgnoreSpan;
import com.lzt841.editor.highlight.CodeHighlightSpan;
import com.lzt841.editor.highlight.JavaCodeHighlighter;
import com.lzt841.editor.input.CodeEditorInteractionContext;
import com.lzt841.editor.input.CodeEditorInteractionListener;
import com.lzt841.editor.input.CodeEditorInteractionMode;
import com.lzt841.editor.input.CodeEditorOnscreenKeyboard;
import com.lzt841.editor.structure.BraceCodeStructureProvider;
import com.lzt841.editor.structure.CodeFoldRegion;
import com.lzt841.editor.structure.CodeStructureInfo;
import com.lzt841.editor.structure.CodeStructureProvider;


/** A scene2d widget for large-text code editing. */
public class CodeEditor extends Widget {
    private static final float DEFAULT_TOP_BAR_HEIGHT = 0f;
    private static final float DEFAULT_STATUS_BAR_HEIGHT = 0f;
    private static final float DEFAULT_ROW_PADDING = 7f;
    private static final float DEFAULT_LEFT_PADDING = 14f;
    private static final float DEFAULT_RIGHT_PADDING = 22f;
    private static final float DEFAULT_GUTTER_MIN_WIDTH = 36f;
    private static final float DEFAULT_GUTTER_LEFT_PADDING = 4f;
    private static final float DEFAULT_GUTTER_FOLD_GAP = 8f;
    private static final float DEFAULT_FOLD_INDICATOR_SIZE = 10f;
    private static final float DEFAULT_FOLD_INDICATOR_RIGHT_PADDING = 8f;
    private static final float DEFAULT_SCROLLBAR_WIDTH = 8f;
    private static final float DEFAULT_SCROLLBAR_HIT_WIDTH = 24f;
    private static final float DEFAULT_SCROLLBAR_GAP = 10f;
    private static final float DEFAULT_SCROLLBAR_MARGIN = 4f;
    private static final float DEFAULT_SCROLLBAR_MIN_THUMB_SIZE = 24f;
    private static final float DEFAULT_SCROLLBAR_HIT_INSET = 6f;
    private static final float DEFAULT_GUIDE_SPACING = 18f;
    private static final float DEFAULT_GUIDE_OFFSET_X = -6f;
    private static final float DEFAULT_FOLD_BADGE_GAP = 10f;
    private static final float DEFAULT_FOLD_BADGE_HORIZONTAL_PADDING = 12f;
    private static final float DEFAULT_FOLD_BADGE_VERTICAL_PADDING = 4f;
    private static final float DEFAULT_SELECTION_HANDLE_RADIUS = 10f;
    private static final float DEFAULT_SELECTION_HANDLE_TOUCH_RADIUS_MULTIPLIER = 1.8f;
    private static final float WHEEL_SCROLL_ROWS = 3f;
    private static final long CURSOR_BLINK_NS = 500_000_000L;
    private static final float AUTO_SCROLL_MAX_SPEED = 1200f;
    private static final float AUTO_SCROLL_EDGE = 36f;
    private static final float TOUCH_FLING_DAMPING = 5.5f;
    private static final float TOUCH_FLING_MIN_SPEED = 120f;
    private static final float TOUCH_BOUNCE_STIFFNESS = 14f;
    private static final float TOUCH_OVERSCROLL_LIMIT = 96f;
    private static final float TOUCH_OVERSCROLL_DAMPING = 0.35f;
    private static final float TOUCH_SLOP = 18f;
    private static final long LONG_PRESS_NS = 450_000_000L;
    private static final long DOUBLE_TAP_NS = 300_000_000L;
    private static final float KEY_REPEAT_INITIAL_DELAY = 0.42f;
    private static final float KEY_REPEAT_INTERVAL = 0.045f;
    private static final float MIN_ZOOM_SCALE = 0.75f;
    private static final float MAX_ZOOM_SCALE = 2.5f;
    private static final int MAX_TOUCH_POINTERS = 20;
    private static final float[][] DEFAULT_RAINBOW_BRACKET_PALETTE = {
        {0.976f, 0.392f, 0.380f, 1f},
        {0.988f, 0.690f, 0.278f, 1f},
        {0.973f, 0.902f, 0.345f, 1f},
        {0.431f, 0.839f, 0.478f, 1f},
        {0.345f, 0.757f, 0.996f, 1f},
        {0.753f, 0.541f, 0.992f, 1f}
    };
    private static final float[][] DEFAULT_RAINBOW_GUIDE_PALETTE = {
        {0.976f, 0.392f, 0.380f, 0.24f},
        {0.988f, 0.690f, 0.278f, 0.24f},
        {0.973f, 0.902f, 0.345f, 0.24f},
        {0.431f, 0.839f, 0.478f, 0.24f},
        {0.345f, 0.757f, 0.996f, 0.24f},
        {0.753f, 0.541f, 0.992f, 0.24f}
    };

    private final GlyphLayout glyphLayout = new GlyphLayout();
    private final CodeDocument document = new CodeDocument();
    private final Array<CodeEditorContentListener> contentListeners = new Array<>();
    private final Array<LineLayout> lineLayouts = new Array<>();
    private final Array<FoldRegion> foldRegions = new Array<>();
    private final IntMap<FoldRegion> foldRegionsByStart = new IntMap<>();
    private final ObjectSet<String> collapsedRegionKeys = new ObjectSet<>();
    private final IntFloatMap glyphWidthCache = new IntFloatMap();
    private final IntFloatMap glyphAdvanceCache = new IntFloatMap();
    private final boolean[] touchPointersDown = new boolean[MAX_TOUCH_POINTERS];
    private final float[] touchPointerX = new float[MAX_TOUCH_POINTERS];
    private final float[] touchPointerY = new float[MAX_TOUCH_POINTERS];

    private CodeEditorStyle style;
    private CodeHighlighter highlighter = new JavaCodeHighlighter();
    private CodeStructureProvider structureProvider = new BraceCodeStructureProvider();
    private CodeEditorInteractionMode interactionMode = CodeEditorInteractionMode.AUTO;
    private CodeEditorInteractionListener interactionListener;
    private CodeEditorOnscreenKeyboard onscreenKeyboard = CodeEditorOnscreenKeyboard.DEFAULT;
    private boolean[] hiddenLines = new boolean[0];
    private int[] visualRowsPerLine = new int[0];
    private int[] visualRowStart = new int[0];
    private int totalVisualRows = 1;
    private Array<Array<CodeBracketIgnoreSpan>> bracketIgnoreLines = new Array<>();

    private float lineHeight;
    private float scrollX;
    private float scrollY;
    private float maxLineWidth;
    private float preferredCursorX = -1f;
    private float zoomScale = 1f;
    private float baseFontScaleX = 1f;
    private float baseFontScaleY = 1f;
    private float baseFontLineHeight;
    private long blinkOrigin = TimeUtils.nanoTime();
    private int analyzedVersion = -1;
    private int analyzedWrapWidth = -1;
    private boolean analyzedWrapEnabled;
    private boolean disabled;
    private boolean lineNumbersFixed = true;
    private boolean readOnly;
    private boolean wrapEnabled;
    private boolean rainbowBracketsEnabled;
    private boolean rainbowGuidesEnabled;
    private boolean searchCaseSensitive;
    private String messageText = "";
    private String searchText = "";
    private int selectionAnchorLine = -1;
    private int selectionAnchorColumn = -1;
    private boolean draggingSelection;
    private boolean draggingScrollbar;
    private boolean draggingHorizontalScrollbar;
    private boolean draggingTouchScroll;
    private boolean draggingStartHandle;
    private boolean draggingEndHandle;
    private boolean pendingSelectionMove;
    private boolean draggingSelectedText;
    private boolean pendingTouchPress;
    private boolean longPressTriggered;
    private float scrollbarDragOffsetX;
    private float scrollbarDragOffsetY;
    private float lastDragX;
    private float lastDragY;
    private float touchDownX;
    private float touchDownY;
    private float touchScrollVelocityX;
    private float touchScrollVelocityY;
    private long lastTouchDragTimeNanos;
    private long touchDownTimeNanos;
    private long lastTapTimeNanos;
    private float lastTapX = Float.NaN;
    private float lastTapY = Float.NaN;
    private long lastMouseTapTimeNanos;
    private float lastMouseTapX = Float.NaN;
    private float lastMouseTapY = Float.NaN;
    private int handleDragFixedLine = -1;
    private int handleDragFixedColumn = -1;
    private int draggedSelectionStartLine = -1;
    private int draggedSelectionStartColumn = -1;
    private int draggedSelectionEndLine = -1;
    private int draggedSelectionEndColumn = -1;
    private int draggedSelectionDropLine = -1;
    private int draggedSelectionDropColumn = -1;
    private int repeatingDeleteKey = -1;
    private float repeatDeleteDelayRemaining;
    private float repeatDeleteIntervalRemaining;
    private boolean pinchZooming;
    private float pinchInitialDistance;
    private float pinchInitialScale = 1f;
    private int searchMatchCount;
    private Array<Array<SearchMatch>> searchMatches = new Array<>();
    private final Array<SearchMatchRef> flatSearchMatches = new Array<>();
    private int currentSearchMatchLine = -1;
    private int currentSearchMatchStart = -1;
    private int currentSearchMatchEnd = -1;
    private CodeEditorContentChangeType pendingContentChangeType = CodeEditorContentChangeType.UNKNOWN;
    private String draggedSelectionText = "";
    private boolean deferredMutationProcessingPending;

    public CodeEditor(CodeEditorStyle style) {
        setStyle(style);
        setTouchable(com.badlogic.gdx.scenes.scene2d.Touchable.enabled);
        addListener(new EditorInputListener());
    }

    public CodeEditor(Skin skin) {
        this(skin.get(CodeEditorStyle.class));
    }

    public CodeEditor(Skin skin, String styleName) {
        this(skin.get(styleName, CodeEditorStyle.class));
    }

    public void setStyle(CodeEditorStyle style) {
        if (style == null) {
            throw new IllegalArgumentException("style cannot be null");
        }
        this.style = style;
        this.baseFontScaleX = style.font.getData().scaleX;
        this.baseFontScaleY = style.font.getData().scaleY;
        this.baseFontLineHeight = style.font.getLineHeight() / Math.max(0.0001f, baseFontScaleY);
        updateFontMetrics();
        glyphWidthCache.clear();
        glyphAdvanceCache.clear();
        invalidateLayout();
        invalidateHierarchy();
    }

    public CodeEditorStyle getStyle() {
        return style;
    }

    public float getZoomScale() {
        return zoomScale;
    }

    public void setZoomScale(float zoomScale) {
        setZoomScaleInternal(zoomScale, getWidth() * 0.5f, getHeight() * 0.5f);
    }

    public CodeHighlighter getHighlighter() {
        return highlighter;
    }

    public void setHighlighter(CodeHighlighter highlighter) {
        if (highlighter == null) {
            throw new IllegalArgumentException("highlighter cannot be null");
        }
        this.highlighter = highlighter;
        invalidateLayout();
    }

    public CodeStructureProvider getStructureProvider() {
        return structureProvider;
    }

    public void setStructureProvider(CodeStructureProvider structureProvider) {
        if (structureProvider == null) {
            throw new IllegalArgumentException("structureProvider cannot be null");
        }
        this.structureProvider = structureProvider;
        invalidateLayout();
    }

    public CodeEditorInteractionMode getInteractionMode() {
        return interactionMode;
    }

    public void setInteractionMode(CodeEditorInteractionMode interactionMode) {
        this.interactionMode = interactionMode == null ? CodeEditorInteractionMode.AUTO : interactionMode;
    }

    public CodeEditorInteractionListener getInteractionListener() {
        return interactionListener;
    }

    public void setInteractionListener(CodeEditorInteractionListener interactionListener) {
        this.interactionListener = interactionListener;
    }

    public void addContentListener(CodeEditorContentListener listener) {
        if (listener == null || contentListeners.contains(listener, true)) {
            return;
        }
        contentListeners.add(listener);
    }

    public void removeContentListener(CodeEditorContentListener listener) {
        if (listener == null) {
            return;
        }
        contentListeners.removeValue(listener, true);
    }

    public void clearContentListeners() {
        contentListeners.clear();
    }

    public CodeEditorOnscreenKeyboard getOnscreenKeyboard() {
        return onscreenKeyboard;
    }

    public void setOnscreenKeyboard(CodeEditorOnscreenKeyboard onscreenKeyboard) {
        this.onscreenKeyboard = onscreenKeyboard == null ? CodeEditorOnscreenKeyboard.DEFAULT : onscreenKeyboard;
    }

    public void setDisabled(boolean disabled) {
        this.disabled = disabled;
        if (disabled && getStage() != null && getStage().getKeyboardFocus() == this) {
            getStage().setKeyboardFocus(null);
        }
    }

    public boolean isDisabled() {
        return disabled;
    }

    public void setReadOnly(boolean readOnly) {
        this.readOnly = readOnly;
        if (readOnly) {
            onscreenKeyboard.show(false);
        }
    }

    public boolean isReadOnly() {
        return readOnly;
    }

    public void setMessageText(String messageText) {
        this.messageText = messageText == null ? "" : messageText;
    }

    public String getMessageText() {
        return messageText;
    }

    public String getSearchText() {
        return searchText;
    }

    public void setSearchText(String searchText) {
        String normalized = searchText == null ? "" : searchText;
        if (this.searchText.equals(normalized)) {
            return;
        }
        this.searchText = normalized;
        clearCurrentSearchMatch();
        invalidateLayout();
    }

    public boolean isSearchCaseSensitive() {
        return searchCaseSensitive;
    }

    public void setSearchCaseSensitive(boolean searchCaseSensitive) {
        if (this.searchCaseSensitive == searchCaseSensitive) {
            return;
        }
        this.searchCaseSensitive = searchCaseSensitive;
        clearCurrentSearchMatch();
        invalidateLayout();
    }

    public int getSearchMatchCount() {
        return searchMatchCount;
    }

    public int getCurrentSearchMatchOrdinal() {
        int index = findCurrentSearchMatchIndex();
        return index >= 0 ? index + 1 : 0;
    }

    public boolean hasCurrentSearchMatch() {
        return findCurrentSearchMatchIndex() >= 0;
    }

    public boolean findNextSearchMatch() {
        ensureLayout();
        if (flatSearchMatches.size == 0) {
            clearCurrentSearchMatch();
            return false;
        }

        int currentIndex = findCurrentSearchMatchIndex();
        int targetIndex;
        if (currentIndex >= 0) {
            targetIndex = (currentIndex + 1) % flatSearchMatches.size;
        } else {
            targetIndex = findSearchMatchIndexFromCursor(true);
        }
        activateSearchMatch(targetIndex);
        return true;
    }

    public boolean findPreviousSearchMatch() {
        ensureLayout();
        if (flatSearchMatches.size == 0) {
            clearCurrentSearchMatch();
            return false;
        }

        int currentIndex = findCurrentSearchMatchIndex();
        int targetIndex;
        if (currentIndex >= 0) {
            targetIndex = (currentIndex - 1 + flatSearchMatches.size) % flatSearchMatches.size;
        } else {
            targetIndex = findSearchMatchIndexFromCursor(false);
        }
        activateSearchMatch(targetIndex);
        return true;
    }

    public boolean replaceCurrentSearchMatch(String replacement) {
        if (disabled || readOnly) {
            return false;
        }
        ensureLayout();
        if (flatSearchMatches.size == 0) {
            clearCurrentSearchMatch();
            return false;
        }

        SearchMatchRef target = resolveSearchMatchForReplace();
        if (target == null) {
            return false;
        }

        String safeReplacement = replacement == null ? "" : replacement;
        clearSelection();
        markPendingContentChange(CodeEditorContentChangeType.REPLACE_CURRENT);
        document.beginCompoundEdit();
        try {
            replaceSearchMatch(target, safeReplacement);
        } finally {
            document.endCompoundEdit();
        }
        clearCurrentSearchMatch();
        onDocumentMutated();
        if (!searchText.isEmpty() && flatSearchMatches.size > 0) {
            findNextSearchMatch();
        }
        return true;
    }

    public int replaceAllSearchMatches(String replacement) {
        if (disabled || readOnly) {
            return 0;
        }
        ensureLayout();
        if (flatSearchMatches.size == 0) {
            clearCurrentSearchMatch();
            return 0;
        }

        String safeReplacement = replacement == null ? "" : replacement;
        Array<SearchMatchRef> matchesToReplace = new Array<>(flatSearchMatches);
        expandCollapsedRegionsForSearchMatches(matchesToReplace);
        ensureLayout();
        matchesToReplace = new Array<>(flatSearchMatches);
        if (matchesToReplace.size == 0) {
            clearCurrentSearchMatch();
            return 0;
        }

        clearSelection();
        markPendingContentChange(CodeEditorContentChangeType.REPLACE_ALL);
        document.beginCompoundEdit();
        try {
            for (int i = matchesToReplace.size - 1; i >= 0; i--) {
                replaceSearchMatch(matchesToReplace.get(i), safeReplacement);
            }
        } finally {
            document.endCompoundEdit();
        }

        clearCurrentSearchMatch();
        onDocumentMutated();
        return matchesToReplace.size;
    }

    public boolean isLineNumbersFixed() {
        return lineNumbersFixed;
    }

    public void setLineNumbersFixed(boolean lineNumbersFixed) {
        if (this.lineNumbersFixed == lineNumbersFixed) {
            return;
        }
        this.lineNumbersFixed = lineNumbersFixed;
        invalidateLayout();
        invalidateHierarchy();
    }

    public boolean isWrapEnabled() {
        return wrapEnabled;
    }

    public void setWrapEnabled(boolean wrapEnabled) {
        if (this.wrapEnabled == wrapEnabled) {
            return;
        }
        this.wrapEnabled = wrapEnabled;
        invalidateLayout();
        invalidateHierarchy();
    }

    private void updateFontMetrics() {
        this.lineHeight = baseFontLineHeight * zoomScale + style.rowPadding;
    }

    public boolean isRainbowBracketsEnabled() {
        return rainbowBracketsEnabled;
    }

    public void setRainbowBracketsEnabled(boolean rainbowBracketsEnabled) {
        if (this.rainbowBracketsEnabled == rainbowBracketsEnabled) {
            return;
        }
        this.rainbowBracketsEnabled = rainbowBracketsEnabled;
        invalidateLayout();
    }

    public boolean isRainbowGuidesEnabled() {
        return rainbowGuidesEnabled;
    }

    public void setRainbowGuidesEnabled(boolean rainbowGuidesEnabled) {
        this.rainbowGuidesEnabled = rainbowGuidesEnabled;
    }

    public void setText(String text) {
        markPendingContentChange(CodeEditorContentChangeType.SET_TEXT);
        document.setText(text);
        clearSelection();
        invalidateLayout();
        ensureLayout();
        ensureCursorVisible();
        notifyContentChanged();
    }

    public int getLineCount() {
        return document.getLineCount();
    }

    public String getText() {
        return document.getText();
    }

    public int getDocumentVersion() {
        return document.getVersion();
    }

    public int getCursorLine() {
        return document.getCursorLine();
    }

    public int getCursorColumn() {
        return document.getCursorColumn();
    }

    public boolean hasSelection() {
        return getSelectionRange() != null;
    }

    @Override
    public void layout() {
        ensureLayout();
    }

    @Override
    public void act(float delta) {
        flushDeferredMutationProcessing();
        super.act(delta);
        ensureStageScrollFocus();
        updateKeyRepeat(delta);
        if (pendingTouchPress && !longPressTriggered && TimeUtils.nanoTime() - touchDownTimeNanos >= LONG_PRESS_NS) {
            longPressTriggered = true;
            pendingTouchPress = false;
            if (getSelectionRange() == null) {
                selectWordAt(touchDownX, touchDownY);
            }
            notifyLongPress(touchDownX, touchDownY);
        }
        if (draggingSelection || draggingStartHandle || draggingEndHandle || draggingSelectedText) {
            applySelectionAutoScroll(delta);
        }
        if (!draggingTouchScroll && !draggingScrollbar
            && (Math.abs(touchScrollVelocityX) > 0f || Math.abs(touchScrollVelocityY) > 0f)) {
            applyTouchFling(delta);
        }
        if (!draggingTouchScroll && !draggingScrollbar) {
            applyTouchBounce(delta);
        }
    }

    @Override
    public void draw(Batch batch, float parentAlpha) {
        flushDeferredMutationProcessing();
        ensureLayout();
        float originalScaleX = style.font.getData().scaleX;
        float originalScaleY = style.font.getData().scaleY;
        style.font.getData().setScale(getEffectiveFontScaleX(), getEffectiveFontScaleY());

        try {
            Drawable background = style.background;
            if (disabled && style.disabledBackground != null) {
                background = style.disabledBackground;
            } else if (isFocused() && style.focusedBackground != null) {
                background = style.focusedBackground;
            }
            if (background != null) {
                background.draw(batch, getX(), getY(), getWidth(), getHeight());
            }

            drawRows(batch);
            if (draggingSelectedText) {
                drawDraggedSelectionDropCaret(batch);
            } else {
                drawCaret(batch);
            }
            drawSelectionHandles(batch);
        } finally {
            style.font.getData().setScale(originalScaleX, originalScaleY);
        }
    }

    @Override
    public float getPrefWidth() {
        return 0f;
    }

    @Override
    public float getPrefHeight() {
        return 0f;
    }

    @Override
    public float getMinWidth() {
        return 0f;
    }

    @Override
    public float getMinHeight() {
        return 0f;
    }

    private void invalidateLayout() {
        analyzedVersion = -1;
    }

    private void ensureLayout() {
        int wrapWidth = wrapEnabled ? Math.max(1, Math.round(getWrapWidth())) : -1;
        if (document.getVersion() == analyzedVersion
            && wrapWidth == analyzedWrapWidth
            && wrapEnabled == analyzedWrapEnabled) {
            return;
        }

        Array<String> lines = document.snapshotLines();
        int lineCount = lines.size;

        lineLayouts.clear();
        foldRegions.clear();
        foldRegionsByStart.clear();

        CodeStructureInfo structureInfo = structureProvider.analyze(lines);
        int[] indentLevels = structureInfo.indentLevels;
        Array<Array<CodeHighlightSpan>> highlightLines = highlighter.highlight(lines, style);
        bracketIgnoreLines = normalizeBracketIgnoreLines(highlighter.getBracketIgnoreSpans(lines), lines.size);
        Array<Array<CodeHighlightSpan>> rainbowBracketLines = buildRainbowBracketSpans(lines, bracketIgnoreLines);
        searchMatches = buildSearchMatches(lines);
        hiddenLines = new boolean[lineCount];
        visualRowsPerLine = new int[lineCount];
        visualRowStart = new int[lineCount];
        maxLineWidth = 0f;

        for (int i = 0; i < lineCount; i++) {
            String line = lines.get(i);
            Array<CodeHighlightSpan> highlight = i < highlightLines.size ? highlightLines.get(i) : new Array<>(0);
            Array<CodeHighlightSpan> rainbowBrackets = i < rainbowBracketLines.size
                ? rainbowBracketLines.get(i)
                : new Array<>(0);
            LineLayout layout = new LineLayout(line, safeIndentLevel(indentLevels, i), buildHighlightTokens(line, highlight, rainbowBrackets));
            layout.ensurePrefixWidths(this);
            maxLineWidth = Math.max(maxLineWidth, layout.measureRange(0, line.length()));
            if (wrapEnabled) {
                wrapLine(layout, wrapWidth);
            } else {
                layout.segmentStarts.clear();
                layout.segmentEnds.clear();
                layout.segmentStarts.add(0);
                layout.segmentEnds.add(line.length());
            }
            lineLayouts.add(layout);
        }

        for (CodeFoldRegion region : structureInfo.foldRegions) {
            if (region.endLine > region.startLine) {
                foldRegions.add(new FoldRegion(region.startLine, region.endLine, region.depth, lines));
            }
        }

        for (FoldRegion region : foldRegions) {
            FoldRegion current = foldRegionsByStart.get(region.startLine);
            if (current == null || region.endLine > current.endLine) {
                foldRegionsByStart.put(region.startLine, region);
            }
        }

        for (FoldRegion region : foldRegions) {
            region.collapsed = collapsedRegionKeys.contains(region.key);
            if (region.collapsed) {
                for (int line = region.startLine + 1; line <= region.endLine && line < hiddenLines.length; line++) {
                    hiddenLines[line] = true;
                }
            }
        }

        totalVisualRows = 0;
        for (int i = 0; i < lineCount; i++) {
            visualRowStart[i] = totalVisualRows;
            visualRowsPerLine[i] = hiddenLines[i] ? 0 : lineLayouts.get(i).getVisualRowCount();
            totalVisualRows += visualRowsPerLine[i];
        }
        if (totalVisualRows == 0) {
            totalVisualRows = 1;
        }

        if (document.getCursorLine() < hiddenLines.length && hiddenLines[document.getCursorLine()]) {
            FoldRegion region = findContainingRegion(document.getCursorLine());
            if (region != null) {
                int column = Math.min(document.getCursorColumn(), document.getLineLength(region.startLine));
                document.moveCursorTo(region.startLine, column);
            }
        }

        analyzedVersion = document.getVersion();
        analyzedWrapWidth = wrapWidth;
        analyzedWrapEnabled = wrapEnabled;
        clampScroll();
    }

    private void wrapLine(LineLayout layout, int wrapWidth) {
        layout.segmentStarts.clear();
        layout.segmentEnds.clear();
        layout.ensurePrefixWidths(this);

        String text = layout.text;
        if (text.isEmpty()) {
            layout.segmentStarts.add(0);
            layout.segmentEnds.add(0);
            return;
        }

        int segmentStart = 0;
        while (segmentStart < text.length()) {
            int bestBreak = -1;
            int index = segmentStart;

            while (index < text.length()) {
                float segmentWidth = layout.measureRange(segmentStart, index + 1);
                if (segmentWidth > wrapWidth) {
                    break;
                }
                if (isWrapOpportunity(text, index)) {
                    bestBreak = index + 1;
                }
                index++;
            }

            if (index >= text.length()) {
                layout.segmentStarts.add(segmentStart);
                layout.segmentEnds.add(text.length());
                return;
            }

            int breakIndex;
            if (index == segmentStart) {
                breakIndex = segmentStart + 1;
            } else if (bestBreak > segmentStart) {
                breakIndex = bestBreak;
            } else {
                breakIndex = index;
            }

            layout.segmentStarts.add(segmentStart);
            layout.segmentEnds.add(breakIndex);
            segmentStart = trimWrappedIndent(text, breakIndex);
        }
    }

    private Array<HighlightToken> buildHighlightTokens(
        String text,
        Array<CodeHighlightSpan> syntaxSpans,
        Array<CodeHighlightSpan> overlaySpans
    ) {
        int length = text.length();
        if (length == 0) {
            return new Array<>(0);
        }

        Array<ColorSpan> spans = new Array<>();
        collectColorSpans(spans, syntaxSpans, length, 0);
        collectColorSpans(spans, overlaySpans, length, 1);
        if (spans.size == 0) {
            return new Array<>(0);
        }

        IntArray boundaries = new IntArray(spans.size * 2 + 2);
        boundaries.add(0);
        boundaries.add(length);
        for (ColorSpan span : spans) {
            boundaries.add(span.start);
            boundaries.add(span.end);
        }
        boundaries.sort();

        IntArray uniqueBoundaries = new IntArray(boundaries.size);
        for (int i = 0; i < boundaries.size; i++) {
            int value = boundaries.get(i);
            if (uniqueBoundaries.size == 0 || uniqueBoundaries.get(uniqueBoundaries.size - 1) != value) {
                uniqueBoundaries.add(value);
            }
        }

        Array<HighlightToken> tokens = new Array<>();
        for (int i = 0; i < uniqueBoundaries.size - 1; i++) {
            int start = uniqueBoundaries.get(i);
            int end = uniqueBoundaries.get(i + 1);
            if (start >= end) {
                continue;
            }

            Color selectedColor = null;
            int selectedPriority = Integer.MIN_VALUE;
            for (ColorSpan span : spans) {
                if (start >= span.start && start < span.end && span.priority >= selectedPriority) {
                    selectedColor = span.color;
                    selectedPriority = span.priority;
                }
            }

            if (selectedColor == null) {
                continue;
            }

            HighlightToken previous = tokens.size == 0 ? null : tokens.peek();
            if (previous != null && previous.end == start && sameColor(previous.color, selectedColor)) {
                tokens.set(tokens.size - 1, new HighlightToken(previous.start, end, previous.color));
            } else {
                tokens.add(new HighlightToken(start, end, selectedColor));
            }
        }
        return tokens;
    }

    private void collectColorSpans(Array<ColorSpan> target, Array<CodeHighlightSpan> source, int lineLength, int priority) {
        if (source == null) {
            return;
        }
        for (CodeHighlightSpan span : source) {
            if (span == null || span.color == null) {
                continue;
            }
            int safeStart = Math.max(0, Math.min(span.start, lineLength));
            int safeEnd = Math.max(safeStart, Math.min(span.end, lineLength));
            if (safeStart < safeEnd) {
                target.add(new ColorSpan(safeStart, safeEnd, span.color, priority));
            }
        }
    }

    private Array<Array<CodeHighlightSpan>> buildRainbowBracketSpans(
        Array<String> lines,
        Array<Array<CodeBracketIgnoreSpan>> ignoredLines
    ) {
        Array<Array<CodeHighlightSpan>> result = new Array<>(lines.size);
        for (int i = 0; i < lines.size; i++) {
            result.add(new Array<CodeHighlightSpan>());
        }
        if (!rainbowBracketsEnabled) {
            return result;
        }

        Array<BracketFrame> stack = new Array<>();
        ScanState state = new ScanState();
        String brackets = "()[]{}";

        for (int lineIndex = 0; lineIndex < lines.size; lineIndex++) {
            state.inLineComment = false;
            String line = lines.get(lineIndex);
            Array<CodeHighlightSpan> spans = result.get(lineIndex);
            Array<CodeBracketIgnoreSpan> ignored = lineIndex < ignoredLines.size
                ? ignoredLines.get(lineIndex)
                : new Array<CodeBracketIgnoreSpan>(0);
            for (int column = 0; column < line.length(); column++) {
                if (isIgnoredBracketPosition(ignored, column)) {
                    continue;
                }
                updateScanState(state, line, column);
                if (!state.isCode) {
                    continue;
                }

                char current = line.charAt(column);
                int bracketIndex = brackets.indexOf(current);
                if (bracketIndex < 0) {
                    continue;
                }

                if (bracketIndex % 2 == 0) {
                    int depth = stack.size;
                    spans.add(new CodeHighlightSpan(column, column + 1, getRainbowBracketColor(depth)));
                    stack.add(new BracketFrame(current, depth));
                    continue;
                }

                char expectedOpen = brackets.charAt(bracketIndex - 1);
                int matchIndex = findMatchingOpenBracketIndex(stack, expectedOpen);
                int depth = matchIndex >= 0 ? stack.get(matchIndex).depth : Math.max(0, stack.size - 1);
                spans.add(new CodeHighlightSpan(column, column + 1, getRainbowBracketColor(depth)));
                if (matchIndex >= 0) {
                    while (stack.size > matchIndex) {
                        stack.pop();
                    }
                }
            }
        }

        return result;
    }

    private Array<Array<CodeBracketIgnoreSpan>> normalizeBracketIgnoreLines(
        Array<Array<CodeBracketIgnoreSpan>> source,
        int lineCount
    ) {
        Array<Array<CodeBracketIgnoreSpan>> normalized = new Array<>(lineCount);
        for (int i = 0; i < lineCount; i++) {
            Array<CodeBracketIgnoreSpan> spans = source != null && i < source.size && source.get(i) != null
                ? source.get(i)
                : new Array<CodeBracketIgnoreSpan>(0);
            normalized.add(spans);
        }
        return normalized;
    }

    private Array<Array<SearchMatch>> buildSearchMatches(Array<String> lines) {
        Array<Array<SearchMatch>> result = new Array<>(lines.size);
        int previousCurrentLine = currentSearchMatchLine;
        int previousCurrentStart = currentSearchMatchStart;
        int previousCurrentEnd = currentSearchMatchEnd;
        searchMatchCount = 0;
        flatSearchMatches.clear();
        if (searchText == null || searchText.isEmpty()) {
            clearCurrentSearchMatch();
            for (int i = 0; i < lines.size; i++) {
                result.add(new Array<SearchMatch>(0));
            }
            return result;
        }

        String needle = searchCaseSensitive ? searchText : searchText.toLowerCase();
        for (int lineIndex = 0; lineIndex < lines.size; lineIndex++) {
            String line = lines.get(lineIndex);
            Array<SearchMatch> lineMatches = new Array<>();
            String haystack = searchCaseSensitive ? line : line.toLowerCase();
            int index = 0;
            while (index <= haystack.length() - needle.length()) {
                int found = haystack.indexOf(needle, index);
                if (found < 0) {
                    break;
                }
                SearchMatch match = new SearchMatch(found, found + needle.length());
                lineMatches.add(match);
                flatSearchMatches.add(new SearchMatchRef(lineIndex, match));
                searchMatchCount++;
                index = Math.max(found + 1, found + needle.length());
            }
            result.add(lineMatches);
        }
        restoreCurrentSearchMatch(previousCurrentLine, previousCurrentStart, previousCurrentEnd);
        return result;
    }

    private int findMatchingOpenBracketIndex(Array<BracketFrame> stack, char expectedOpen) {
        for (int i = stack.size - 1; i >= 0; i--) {
            if (stack.get(i).open == expectedOpen) {
                return i;
            }
        }
        return -1;
    }

    private int safeIndentLevel(int[] indentLevels, int index) {
        if (indentLevels == null || index < 0 || index >= indentLevels.length) {
            return 0;
        }
        return indentLevels[index];
    }

    private void onDocumentMutated() {
        invalidateLayout();
        ensureLayout();
        ensureCursorVisible();
        resetPreferredColumn();
        refreshBlink();
        notifyContentChanged();
    }

    private void onDocumentMutatedDeferred() {
        invalidateLayout();
        resetPreferredColumn();
        refreshBlink();
        deferredMutationProcessingPending = true;
    }

    private void flushDeferredMutationProcessing() {
        if (!deferredMutationProcessingPending) {
            return;
        }
        deferredMutationProcessingPending = false;
        ensureLayout();
        ensureCursorVisible();
        notifyContentChanged();
    }

    private void notifyContentChanged() {
        if (contentListeners.size == 0) {
            pendingContentChangeType = CodeEditorContentChangeType.UNKNOWN;
            return;
        }
        CodeEditorContentChangeType type = pendingContentChangeType;
        pendingContentChangeType = CodeEditorContentChangeType.UNKNOWN;
        CodeEditorContentChangeEvent event = new CodeEditorContentChangeEvent(
            type,
            document.getText(),
            document.getVersion(),
            document.getCursorLine(),
            document.getCursorColumn()
        );
        for (CodeEditorContentListener listener : contentListeners) {
            listener.onContentChanged(this, event);
        }
    }

    private void markPendingContentChange(CodeEditorContentChangeType type) {
        pendingContentChangeType = type == null ? CodeEditorContentChangeType.UNKNOWN : type;
    }

    private void deleteSelectionIfPresent() {
        SelectionRange selection = getSelectionRange();
        if (selection == null) {
            return;
        }

        document.deleteRange(selection.startLine, selection.startColumn, selection.endLine, selection.endColumn);
        clearSelection();
    }

    public void selectAllText() {
        selectionAnchorLine = 0;
        selectionAnchorColumn = 0;
        document.moveCursorTo(document.getLineCount() - 1, document.getLineLength(document.getLineCount() - 1));
        ensureCursorVisible();
        refreshBlink();
    }

    private void clearSelection() {
        selectionAnchorLine = -1;
        selectionAnchorColumn = -1;
        draggingSelection = false;
        handleDragFixedLine = -1;
        handleDragFixedColumn = -1;
        clearSelectedTextDragState();
    }

    private void clearCurrentSearchMatch() {
        currentSearchMatchLine = -1;
        currentSearchMatchStart = -1;
        currentSearchMatchEnd = -1;
    }

    private int findCurrentSearchMatchIndex() {
        for (int i = 0; i < flatSearchMatches.size; i++) {
            SearchMatchRef ref = flatSearchMatches.get(i);
            if (ref.line == currentSearchMatchLine
                && ref.match.start == currentSearchMatchStart
                && ref.match.end == currentSearchMatchEnd) {
                return i;
            }
        }
        return -1;
    }

    private void restoreCurrentSearchMatch(int line, int start, int end) {
        if (line < 0 || start < 0 || end < 0) {
            clearCurrentSearchMatch();
            return;
        }
        for (SearchMatchRef ref : flatSearchMatches) {
            if (ref.line == line && ref.match.start == start && ref.match.end == end) {
                currentSearchMatchLine = line;
                currentSearchMatchStart = start;
                currentSearchMatchEnd = end;
                return;
            }
        }
        clearCurrentSearchMatch();
    }

    private int findSearchMatchIndexFromCursor(boolean forward) {
        int cursorLine = document.getCursorLine();
        int cursorColumn = document.getCursorColumn();
        if (forward) {
            for (int i = 0; i < flatSearchMatches.size; i++) {
                SearchMatchRef ref = flatSearchMatches.get(i);
                if (ref.line > cursorLine || (ref.line == cursorLine && ref.match.start >= cursorColumn)) {
                    return i;
                }
            }
            return 0;
        }

        for (int i = flatSearchMatches.size - 1; i >= 0; i--) {
            SearchMatchRef ref = flatSearchMatches.get(i);
            if (ref.line < cursorLine || (ref.line == cursorLine && ref.match.end <= cursorColumn)) {
                return i;
            }
        }
        return flatSearchMatches.size - 1;
    }

    private void activateSearchMatch(int index) {
        if (index < 0 || index >= flatSearchMatches.size) {
            clearCurrentSearchMatch();
            return;
        }
        SearchMatchRef ref = flatSearchMatches.get(index);
        currentSearchMatchLine = ref.line;
        currentSearchMatchStart = ref.match.start;
        currentSearchMatchEnd = ref.match.end;
        clearSelection();
        document.moveCursorTo(ref.line, ref.match.start);
        resetPreferredColumn();
        ensureCursorVisible();
        refreshBlink();
    }

    private SearchMatchRef resolveSearchMatchForReplace() {
        int currentIndex = findCurrentSearchMatchIndex();
        if (currentIndex >= 0 && currentIndex < flatSearchMatches.size) {
            return flatSearchMatches.get(currentIndex);
        }
        if (flatSearchMatches.size == 0) {
            return null;
        }
        int fallbackIndex = findSearchMatchIndexFromCursor(true);
        if (fallbackIndex < 0 || fallbackIndex >= flatSearchMatches.size) {
            return null;
        }
        return flatSearchMatches.get(fallbackIndex);
    }

    private void replaceSearchMatch(SearchMatchRef target, String replacement) {
        if (target == null) {
            return;
        }
        expandCollapsedRegionContainingLine(target.line);
        int line = Math.max(0, Math.min(target.line, document.getLineCount() - 1));
        int lineLength = document.getLineLength(line);
        int start = Math.max(0, Math.min(target.match.start, lineLength));
        int end = Math.max(start, Math.min(target.match.end, lineLength));
        document.moveCursorTo(line, start);
        document.deleteRange(line, start, line, end);
        document.insertText(replacement);
    }

    private void expandCollapsedRegionsForSearchMatches(Iterable<SearchMatchRef> matches) {
        if (matches == null) {
            return;
        }

        ObjectSet<String> keysToExpand = new ObjectSet<>();
        for (SearchMatchRef match : matches) {
            if (match == null) {
                continue;
            }
            for (FoldRegion region : foldRegions) {
                if (region.collapsed && region.startLine < match.line && match.line <= region.endLine) {
                    keysToExpand.add(region.key);
                }
            }
        }

        if (keysToExpand.isEmpty()) {
            return;
        }

        float previousScroll = scrollY;
        removeCollapsedRegionKeys(keysToExpand);
        invalidateLayout();
        ensureLayout();
        scrollY = Math.max(getMinScroll(), Math.min(previousScroll, getMaxScroll()));
    }

    private boolean expandCollapsedRegionContainingLine(int line) {
        ObjectSet<String> keysToExpand = new ObjectSet<>();
        for (FoldRegion region : foldRegions) {
            if (region.collapsed && region.startLine < line && line <= region.endLine) {
                keysToExpand.add(region.key);
            }
        }
        if (keysToExpand.isEmpty()) {
            return false;
        }

        float previousScroll = scrollY;
        removeCollapsedRegionKeys(keysToExpand);
        invalidateLayout();
        ensureLayout();
        scrollY = Math.max(getMinScroll(), Math.min(previousScroll, getMaxScroll()));
        return true;
    }

    private void removeCollapsedRegionKeys(ObjectSet<String> keys) {
        for (String key : keys) {
            collapsedRegionKeys.remove(key);
        }
    }

    private void beginSelectionIfNeeded(boolean selecting) {
        if (selectionAnchorLine < 0) {
            selectionAnchorLine = document.getCursorLine();
            selectionAnchorColumn = document.getCursorColumn();
        }
    }

    private SelectionRange getSelectionRange() {
        if (selectionAnchorLine < 0) {
            return null;
        }

        int startLine = selectionAnchorLine;
        int startColumn = selectionAnchorColumn;
        int endLine = document.getCursorLine();
        int endColumn = document.getCursorColumn();
        if (startLine == endLine && startColumn == endColumn) {
            return null;
        }
        if (startLine > endLine || (startLine == endLine && startColumn > endColumn)) {
            int tempLine = startLine;
            int tempColumn = startColumn;
            startLine = endLine;
            startColumn = endColumn;
            endLine = tempLine;
            endColumn = tempColumn;
        }
        return new SelectionRange(startLine, startColumn, endLine, endColumn);
    }

    private String getSelectedText() {
        SelectionRange selection = getSelectionRange();
        if (selection == null) {
            return "";
        }
        return document.getTextRange(selection.startLine, selection.startColumn, selection.endLine, selection.endColumn);
    }

    private void selectWordAt(float x, float y) {
        CodePoint point = getCodePointAt(x, y, true);
        if (point == null) {
            return;
        }

        String line = document.getLine(point.line);
        if (line.isEmpty()) {
            document.moveCursorTo(point.line, 0);
            clearSelection();
            refreshBlink();
            return;
        }

        int pivot = Math.min(point.column, Math.max(0, line.length() - 1));
        if (pivot > 0 && pivot == line.length()) {
            pivot--;
        }

        if (!isWordChar(line.charAt(pivot))) {
            document.moveCursorTo(point.line, pivot);
            clearSelection();
            refreshBlink();
            return;
        }

        int start = pivot;
        int end = pivot + 1;
        while (start > 0 && isWordChar(line.charAt(start - 1))) {
            start--;
        }
        while (end < line.length() && isWordChar(line.charAt(end))) {
            end++;
        }

        selectionAnchorLine = point.line;
        selectionAnchorColumn = start;
        document.moveCursorTo(point.line, end);
        ensureCursorVisible();
        refreshBlink();
    }

    private void notifyLongPress(float x, float y) {
        if (interactionListener == null) {
            return;
        }
        CodePoint point = getCodePointAt(x, y, true);
        if (point == null) {
            return;
        }
        interactionListener.onLongPress(this, createInteractionContext(x, y, point, true));
    }

    private void notifyDoubleClick(float x, float y, boolean touch) {
        if (interactionListener == null) {
            return;
        }
        CodePoint point = getCodePointAt(x, y, true);
        if (point == null) {
            return;
        }
        interactionListener.onDoubleClick(this, createInteractionContext(x, y, point, touch));
    }

    private boolean notifySecondaryClick(float x, float y) {
        if (interactionListener == null) {
            return false;
        }
        CodePoint point = getCodePointAt(x, y, true);
        if (point == null) {
            return false;
        }
        return interactionListener.onSecondaryClick(this, createInteractionContext(x, y, point, false));
    }

    private CodeEditorInteractionContext createInteractionContext(float x, float y, CodePoint point, boolean touch) {
        return new CodeEditorInteractionContext(x, y, point.line, point.column, getSelectedText(), touch);
    }

    private boolean useTouchInteractions() {
        if (interactionMode == CodeEditorInteractionMode.TOUCH) {
            return true;
        }
        if (interactionMode == CodeEditorInteractionMode.MOUSE) {
            return false;
        }
        ApplicationType type = Gdx.app.getType();
        return type == ApplicationType.Android || type == ApplicationType.iOS;
    }

    private boolean isWordChar(char c) {
        return Character.isLetterOrDigit(c) || c == '_';
    }

    public boolean copySelection() {
        String selected = getSelectedText();
        if (selected.isEmpty()) {
            return false;
        }
        Gdx.app.getClipboard().setContents(selected);
        return true;
    }

    public boolean cutSelection() {
        if (disabled || readOnly) {
            return false;
        }
        String selected = getSelectedText();
        if (selected.isEmpty()) {
            return false;
        }
        Gdx.app.getClipboard().setContents(selected);
        expandCollapsedRegionsForEdit(EditIntent.DELETE);
        markPendingContentChange(CodeEditorContentChangeType.CUT);
        deleteSelectionIfPresent();
        onDocumentMutated();
        return true;
    }

    public boolean pasteClipboard() {
        if (disabled || readOnly) {
            return false;
        }
        markPendingContentChange(CodeEditorContentChangeType.PASTE);
        replaceSelectionWith(Gdx.app.getClipboard().getContents(), true);
        return true;
    }

    public boolean canUndo() {
        return document.canUndo();
    }

    public boolean canRedo() {
        return document.canRedo();
    }

    private void replaceSelectionWith(String text) {
        replaceSelectionWith(text, false);
    }

    private void replaceSelectionWith(String text, boolean deferPostMutationProcessing) {
        expandCollapsedRegionsForEdit(EditIntent.INSERT);
        document.beginCompoundEdit();
        try {
            deleteSelectionIfPresent();
            document.insertText(text);
        } finally {
            document.endCompoundEdit();
        }
        if (deferPostMutationProcessing) {
            onDocumentMutatedDeferred();
        } else {
            onDocumentMutated();
        }
    }

    public boolean undo() {
        if (!canUndo() || disabled || readOnly) {
            return false;
        }
        if (!document.undo()) {
            return false;
        }
        clearSelection();
        markPendingContentChange(CodeEditorContentChangeType.UNDO);
        onDocumentMutated();
        return true;
    }

    public boolean redo() {
        if (!canRedo() || disabled || readOnly) {
            return false;
        }
        if (!document.redo()) {
            return false;
        }
        clearSelection();
        markPendingContentChange(CodeEditorContentChangeType.REDO);
        onDocumentMutated();
        return true;
    }

    private void startDeleteKeyRepeat(int keycode) {
        repeatingDeleteKey = keycode;
        repeatDeleteDelayRemaining = KEY_REPEAT_INITIAL_DELAY;
        repeatDeleteIntervalRemaining = KEY_REPEAT_INTERVAL;
    }

    private void stopDeleteKeyRepeat(int keycode) {
        if (repeatingDeleteKey == keycode) {
            repeatingDeleteKey = -1;
        }
    }

    private void updateKeyRepeat(float delta) {
        if (repeatingDeleteKey == -1) {
            return;
        }
        if (disabled || readOnly || !isFocused()) {
            repeatingDeleteKey = -1;
            return;
        }

        if (repeatDeleteDelayRemaining > 0f) {
            repeatDeleteDelayRemaining -= delta;
            if (repeatDeleteDelayRemaining > 0f) {
                return;
            }
        }

        repeatDeleteIntervalRemaining -= delta;
        while (repeatDeleteIntervalRemaining <= 0f) {
            if (!performDeleteKey(repeatingDeleteKey)) {
                repeatingDeleteKey = -1;
                return;
            }
            repeatDeleteIntervalRemaining += KEY_REPEAT_INTERVAL;
        }
    }

    private boolean performDeleteKey(int keycode) {
        if (keycode == Input.Keys.BACKSPACE) {
            expandCollapsedRegionsForEdit(EditIntent.BACKSPACE);
            markPendingContentChange(CodeEditorContentChangeType.DELETE);
            if (getSelectionRange() != null) {
                deleteSelectionIfPresent();
                onDocumentMutated();
                return true;
            }

            int lineBefore = document.getCursorLine();
            int columnBefore = document.getCursorColumn();
            document.backspace();
            if (lineBefore == document.getCursorLine() && columnBefore == document.getCursorColumn()) {
                return false;
            }
            onDocumentMutated();
            return true;
        }

        if (keycode == Input.Keys.FORWARD_DEL) {
            expandCollapsedRegionsForEdit(EditIntent.DELETE);
            markPendingContentChange(CodeEditorContentChangeType.DELETE);
            if (getSelectionRange() != null) {
                deleteSelectionIfPresent();
                onDocumentMutated();
                return true;
            }

            int versionBefore = document.getVersion();
            document.deleteForward();
            if (versionBefore == document.getVersion()) {
                return false;
            }
            onDocumentMutated();
            return true;
        }

        return false;
    }

    private void clearSelectedTextDragState() {
        pendingSelectionMove = false;
        draggingSelectedText = false;
        draggedSelectionText = "";
        draggedSelectionStartLine = -1;
        draggedSelectionStartColumn = -1;
        draggedSelectionEndLine = -1;
        draggedSelectionEndColumn = -1;
        draggedSelectionDropLine = -1;
        draggedSelectionDropColumn = -1;
    }

    private boolean beginSelectedTextDrag(float x, float y) {
        if (useTouchInteractions() || readOnly) {
            return false;
        }
        SelectionRange selection = getSelectionRange();
        if (selection == null) {
            return false;
        }
        CodePoint point = getCodePointAt(x, y, false);
        if (!isPointInsideSelection(selection, point)) {
            return false;
        }
        pendingSelectionMove = true;
        draggingSelectedText = false;
        draggedSelectionText = getSelectedText();
        draggedSelectionStartLine = selection.startLine;
        draggedSelectionStartColumn = selection.startColumn;
        draggedSelectionEndLine = selection.endLine;
        draggedSelectionEndColumn = selection.endColumn;
        draggedSelectionDropLine = point.line;
        draggedSelectionDropColumn = point.column;
        return true;
    }

    private void updateSelectedTextDrag(float x, float y) {
        CodePoint point = getCodePointAt(x, y, true);
        if (point == null) {
            return;
        }
        draggedSelectionDropLine = point.line;
        draggedSelectionDropColumn = point.column;
        refreshBlink();
    }

    private boolean finishSelectedTextDrag(float x, float y) {
        SelectionRange selection = getDraggedSelectionRange();
        if (selection == null || draggedSelectionText.isEmpty()) {
            clearSelectedTextDragState();
            return false;
        }

        CodePoint dropPoint = getCodePointAt(x, y, true);
        if (dropPoint == null) {
            clearSelectedTextDragState();
            return false;
        }
        if (isPointWithinSelectionBounds(selection, dropPoint)) {
            clearSelectedTextDragState();
            refreshBlink();
            return true;
        }

        CodePoint adjustedDropPoint = adjustPointAfterSelectionDeletion(dropPoint, selection);
        expandCollapsedRegionsForEdit(EditIntent.DELETE);
        document.beginCompoundEdit();
        try {
            document.deleteRange(selection.startLine, selection.startColumn, selection.endLine, selection.endColumn);
            document.moveCursorTo(adjustedDropPoint.line, adjustedDropPoint.column);
            selectionAnchorLine = adjustedDropPoint.line;
            selectionAnchorColumn = adjustedDropPoint.column;
            markPendingContentChange(CodeEditorContentChangeType.UNKNOWN);
            document.insertText(draggedSelectionText);
        } finally {
            document.endCompoundEdit();
        }
        clearSelectedTextDragState();
        onDocumentMutated();
        return true;
    }

    private SelectionRange getDraggedSelectionRange() {
        if (draggedSelectionStartLine < 0 || draggedSelectionEndLine < 0) {
            return null;
        }
        return new SelectionRange(
            draggedSelectionStartLine,
            draggedSelectionStartColumn,
            draggedSelectionEndLine,
            draggedSelectionEndColumn
        );
    }

    private boolean isPointInsideSelection(SelectionRange selection, CodePoint point) {
        if (selection == null || point == null) {
            return false;
        }
        return compareCodePointToSelectionStart(point, selection) >= 0
            && compareCodePointToSelectionEnd(point, selection) < 0;
    }

    private boolean isPointWithinSelectionBounds(SelectionRange selection, CodePoint point) {
        if (selection == null || point == null) {
            return false;
        }
        return compareCodePointToSelectionStart(point, selection) >= 0
            && compareCodePointToSelectionEnd(point, selection) <= 0;
    }

    private int compareCodePointToSelectionStart(CodePoint point, SelectionRange selection) {
        return comparePosition(point.line, point.column, selection.startLine, selection.startColumn);
    }

    private int compareCodePointToSelectionEnd(CodePoint point, SelectionRange selection) {
        return comparePosition(point.line, point.column, selection.endLine, selection.endColumn);
    }

    private int comparePosition(int lineA, int columnA, int lineB, int columnB) {
        if (lineA != lineB) {
            return lineA < lineB ? -1 : 1;
        }
        if (columnA == columnB) {
            return 0;
        }
        return columnA < columnB ? -1 : 1;
    }

    private CodePoint adjustPointAfterSelectionDeletion(CodePoint point, SelectionRange selection) {
        if (comparePosition(point.line, point.column, selection.endLine, selection.endColumn) <= 0) {
            return point;
        }
        if (selection.startLine == selection.endLine) {
            if (point.line != selection.startLine) {
                return point;
            }
            return new CodePoint(point.line, Math.max(selection.startColumn, point.column - (selection.endColumn - selection.startColumn)));
        }

        int removedLineCount = selection.endLine - selection.startLine;
        if (point.line == selection.endLine) {
            return new CodePoint(selection.startLine, selection.startColumn + Math.max(0, point.column - selection.endColumn));
        }
        if (point.line > selection.endLine) {
            return new CodePoint(point.line - removedLineCount, point.column);
        }
        return point;
    }

    private void drawDraggedSelectionDropCaret(Batch batch) {
        if (!draggingSelectedText || style.cursor == null || draggedSelectionDropLine < 0) {
            return;
        }
        SelectionRange selection = getDraggedSelectionRange();
        if (selection == null) {
            return;
        }
        CodePoint point = new CodePoint(draggedSelectionDropLine, draggedSelectionDropColumn);
        if (isPointWithinSelectionBounds(selection, point)) {
            return;
        }
        CursorPlacement placement = getCursorPlacement(draggedSelectionDropLine, draggedSelectionDropColumn);
        if (placement == null) {
            return;
        }
        float rowBottom = rowBottom(placement.row);
        style.cursor.draw(batch, getX() + placement.x, getY() + rowBottom + 3f, 1.5f, lineHeight - 6f);
    }

    private void drawRows(Batch batch) {
        int startRow = Math.max(0, (int) Math.floor(scrollY / lineHeight));
        int endRow = Math.min(totalVisualRows - 1, (int) Math.ceil((scrollY + getContentHeight()) / lineHeight));
        SelectionRange selection = getSelectionRange();
        FoldRegion activeBlock = getActiveBlockRegion();
        BracketMatch bracketMatch = getBracketMatch();

        if (document.getLineCount() == 1 && document.getLineLength(0) == 0 && !isFocused() && !messageText.isEmpty()) {
            style.font.setColor(style.messageFontColor);
            style.font.draw(batch, messageText, getX() + getTextRenderX(), getTextBaseline(getY() + rowBottom(0)));
        }

        drawVisibleRowBackgrounds(batch, startRow, endRow, activeBlock);
        drawVisibleIndentGuides(batch, startRow, endRow, activeBlock);

        for (int row = startRow; row <= endRow; row++) {
            int line = findLineByVisualRow(row);
            if (line < 0 || line >= lineLayouts.size) {
                continue;
            }

            LineLayout layout = lineLayouts.get(line);
            int segment = row - visualRowStart[line];
            int start = layout.segmentStarts.get(segment);
            int end = layout.segmentEnds.get(segment);
            float rowBottom = getY() + rowBottom(row);
            float baseline = getTextBaseline(rowBottom);

            drawSearchHighlights(batch, layout, line, start, end, rowBottom);
            drawSelection(batch, selection, layout, line, segment, start, end, rowBottom);
            drawBracketHighlight(batch, bracketMatch, layout, line, segment, start, end, rowBottom);

            drawStyledRange(batch, layout, start, end, getX() + getTextRenderX(), baseline);

            FoldRegion region = foldRegionsByStart.get(line);
            if (segment == 0 && region != null && region.collapsed) {
                String badge = "  ... " + (region.endLine - region.startLine) + " folded lines";
                float badgeX = getX() + getTextRenderX() + layout.measureRange(start, end) + style.foldBadgeGap;
                if (style.foldBadge != null) {
                    float badgeWidth = measureText(badge) + style.foldBadgeHorizontalPadding;
                    float badgeHorizontalInset = style.foldBadgeHorizontalPadding * 0.5f;
                    style.foldBadge.draw(
                        batch,
                        badgeX - badgeHorizontalInset,
                        rowBottom + style.foldBadgeVerticalPadding,
                        badgeWidth,
                        lineHeight - style.foldBadgeVerticalPadding * 2f
                    );
                }
                style.font.setColor(style.gutterFontColor);
                style.font.draw(batch, badge, badgeX, baseline);
            }
        }

        drawGutterOverlay(batch, startRow, endRow);
        drawScrollbar(batch);
    }

    private void drawGutterOverlay(Batch batch, int startRow, int endRow) {
        if (style.gutterBackground != null) {
            style.gutterBackground.draw(
                batch,
                getX() + getGutterRenderX(),
                getY() + style.statusBarHeight,
                getGutterWidth(),
                getContentHeight()
            );
        }

        for (int row = startRow; row <= endRow; row++) {
            int line = findLineByVisualRow(row);
            if (line < 0 || line >= lineLayouts.size) {
                continue;
            }

            int segment = row - visualRowStart[line];
            float rowBottom = getY() + rowBottom(row);
            float baseline = getTextBaseline(rowBottom);
            style.font.setColor(style.gutterFontColor);
            if (segment == 0) {
                drawFoldIndicator(batch, line, rowBottom, baseline);

                String lineNumber = Integer.toString(line + 1);
                float numberWidth = measureText(lineNumber);
                float lineNumberRight = getGutterLineNumberRightX();
                style.font.draw(
                    batch,
                    lineNumber,
                    getX() + getGutterRenderX() + lineNumberRight - numberWidth,
                    baseline
                );
            } else {
                float continuationWidth = measureText(".");
                style.font.draw(
                    batch,
                    ".",
                    getX() + getGutterRenderX() + getGutterLineNumberRightX() - continuationWidth,
                    baseline
                );
            }
        }
    }

    private void drawVisibleRowBackgrounds(Batch batch, int startRow, int endRow, FoldRegion activeBlock) {
        for (int row = startRow; row <= endRow; row++) {
            int line = findLineByVisualRow(row);
            if (line < 0 || line >= lineLayouts.size) {
                continue;
            }

            float rowBottom = getY() + rowBottom(row);
            float rowContentX = getX() + (lineNumbersFixed ? getGutterWidth() : 0f);
            float rowContentWidth = getWidth() - (lineNumbersFixed ? getGutterWidth() : 0f);

            if (activeBlock != null && style.currentBlock != null && line >= activeBlock.startLine && line <= activeBlock.endLine) {
                style.currentBlock.draw(batch, rowContentX, rowBottom, rowContentWidth, lineHeight);
            }

            if (line == document.getCursorLine() && style.currentLine != null) {
                style.currentLine.draw(batch, rowContentX, rowBottom, rowContentWidth, lineHeight);
            }
        }
    }

    private void drawStyledRange(Batch batch, LineLayout layout, int start, int end, float x, float baseline) {
        Color baseColor = disabled ? style.disabledFontColor : style.fontColor;
        int cursor = start;
        for (HighlightToken token : layout.tokens) {
            if (token.end <= start || token.start >= end) {
                continue;
            }

            if (cursor < token.start) {
                int plainEnd = Math.min(token.start, end);
                drawText(batch, layout.text, cursor, plainEnd, x, baseline, baseColor);
                x += layout.measureRange(cursor, plainEnd);
            }

            int tokenStart = Math.max(token.start, start);
            int tokenEnd = Math.min(token.end, end);
            drawText(batch, layout.text, tokenStart, tokenEnd, x, baseline, disabled ? baseColor : token.color);
            x += layout.measureRange(tokenStart, tokenEnd);
            cursor = tokenEnd;
        }

        if (cursor < end) {
            drawText(batch, layout.text, cursor, end, x, baseline, baseColor);
        }
    }

    private void drawText(Batch batch, String text, int start, int end, float x, float y, Color color) {
        if (start >= end) {
            return;
        }
        style.font.setColor(color);
        style.font.draw(batch, text, x, y, start, end, 0f, Align.left, false);
    }

    private float getTextBaseline(float rowBottom) {
        return rowBottom + lineHeight + style.textBaselineOffset;
    }

    private void drawCaret(Batch batch) {
        if (!isFocused() || disabled) {
            return;
        }
        if (((TimeUtils.nanoTime() - blinkOrigin) / CURSOR_BLINK_NS) % 2 != 0) {
            return;
        }

        CursorPlacement placement = getCursorPlacement();
        if (placement == null) {
            return;
        }

        float rowBottom = rowBottom(placement.row);
        if (rowBottom + lineHeight < style.statusBarHeight || rowBottom > contentTopY()) {
            return;
        }

        if (style.cursor != null) {
            style.cursor.draw(batch, getX() + placement.x, getY() + rowBottom + 3f, 1.5f, lineHeight - 6f);
        }
    }

    private void drawSelection(
        Batch batch,
        SelectionRange selection,
        LineLayout layout,
        int line,
        int segment,
        int start,
        int end,
        float rowBottom
    ) {
        if (selection == null || style.selection == null) {
            return;
        }
        if (line < selection.startLine || line > selection.endLine) {
            return;
        }

        int selectedStart = start;
        int selectedEnd = end;
        if (line == selection.startLine) {
            selectedStart = Math.max(selectedStart, selection.startColumn);
        }
        if (line == selection.endLine) {
            selectedEnd = Math.min(selectedEnd, selection.endColumn);
        }
        if (selectedStart >= selectedEnd) {
            return;
        }

        float x = getX() + getTextRenderX() + layout.measureRange(start, selectedStart);
        float width = layout.measureRange(selectedStart, selectedEnd);
        style.selection.draw(batch, x, rowBottom + 2f, Math.max(1f, width), lineHeight - 4f);
    }

    private void drawSearchHighlights(
        Batch batch,
        LineLayout layout,
        int line,
        int start,
        int end,
        float rowBottom
    ) {
        if (style.searchHighlight == null || line < 0 || line >= searchMatches.size) {
            return;
        }
        Array<SearchMatch> lineMatches = searchMatches.get(line);
        if (lineMatches == null || lineMatches.size == 0) {
            return;
        }
        for (SearchMatch match : lineMatches) {
            int highlightStart = Math.max(start, match.start);
            int highlightEnd = Math.min(end, match.end);
            if (highlightStart >= highlightEnd) {
                continue;
            }
            float x = getX() + getTextRenderX() + layout.measureRange(start, highlightStart);
            float width = layout.measureRange(highlightStart, highlightEnd);
            Drawable highlight = isCurrentSearchMatch(line, match) && style.currentSearchHighlight != null
                ? style.currentSearchHighlight
                : style.searchHighlight;
            if (highlight != null) {
                highlight.draw(batch, x, rowBottom + 2f, Math.max(1f, width), lineHeight - 4f);
            }
        }
    }

    private boolean isCurrentSearchMatch(int line, SearchMatch match) {
        return line == currentSearchMatchLine
            && match.start == currentSearchMatchStart
            && match.end == currentSearchMatchEnd;
    }

    private void drawBracketHighlight(
        Batch batch,
        BracketMatch bracketMatch,
        LineLayout layout,
        int line,
        int segment,
        int start,
        int end,
        float rowBottom
    ) {
        if (bracketMatch == null || style.bracketMatch == null) {
            return;
        }
        drawBracketHighlightAt(batch, bracketMatch.anchorLine, bracketMatch.anchorColumn, layout, line, segment, start, end, rowBottom);
        drawBracketHighlightAt(batch, bracketMatch.matchLine, bracketMatch.matchColumn, layout, line, segment, start, end, rowBottom);
    }

    private void drawBracketHighlightAt(
        Batch batch,
        int targetLine,
        int targetColumn,
        LineLayout layout,
        int line,
        int segment,
        int start,
        int end,
        float rowBottom
    ) {
        if (line != targetLine || targetColumn < start || targetColumn >= end || targetColumn >= layout.text.length()) {
            return;
        }
        float x = getX() + getTextRenderX() + layout.measureRange(start, targetColumn);
        float width = Math.max(1f, glyphWidth(layout.text.charAt(targetColumn)));
        style.bracketMatch.draw(batch, x, rowBottom + 2f, width, lineHeight - 4f);
    }

    private void drawVisibleIndentGuides(Batch batch, int startRow, int endRow, FoldRegion activeBlock) {
        if (startRow > endRow) {
            return;
        }

        int maxVisibleIndent = 0;
        for (int row = startRow; row <= endRow; row++) {
            int line = findLineByVisualRow(row);
            if (line >= 0 && line < lineLayouts.size) {
                maxVisibleIndent = Math.max(maxVisibleIndent, lineLayouts.get(line).indentLevel);
            }
        }
        if (maxVisibleIndent <= 0) {
            return;
        }

        for (int depth = 0; depth < maxVisibleIndent; depth++) {
            int runStartRow = -1;
            boolean runEmphasized = false;
            for (int row = startRow; row <= endRow + 1; row++) {
                boolean active = false;
                boolean emphasized = false;
                if (row <= endRow) {
                    int line = findLineByVisualRow(row);
                    if (line >= 0 && line < lineLayouts.size) {
                        active = shouldDrawGuideForLineDepth(line, depth);
                        emphasized = active && isGuideEmphasized(activeBlock, line, depth);
                    }
                }

                if (!active) {
                    if (runStartRow >= 0) {
                        drawGuideRun(batch, depth, runStartRow, row - 1, runEmphasized);
                        runStartRow = -1;
                    }
                    continue;
                }

                if (runStartRow < 0) {
                    runStartRow = row;
                    runEmphasized = emphasized;
                    continue;
                }

                if (runEmphasized != emphasized) {
                    drawGuideRun(batch, depth, runStartRow, row - 1, runEmphasized);
                    runStartRow = row;
                    runEmphasized = emphasized;
                }
            }
        }
    }

    private boolean shouldDrawGuideForLineDepth(int line, int depth) {
        if (line < 0 || line >= lineLayouts.size) {
            return false;
        }
        if (lineLayouts.get(line).indentLevel <= depth) {
            return false;
        }
        for (FoldRegion region : foldRegions) {
            if (region.depth == depth && region.endLine == line) {
                return false;
            }
        }
        return true;
    }

    private boolean isGuideEmphasized(FoldRegion activeBlock, int line, int depth) {
        return activeBlock != null
            && line > activeBlock.startLine
            && line < activeBlock.endLine
            && depth == activeBlock.depth;
    }

    private void drawGuideRun(Batch batch, int depth, int startRow, int endRow, boolean emphasized) {
        if (endRow < startRow) {
            return;
        }
        float guideX = getX() + getTextRenderX() + depth * getEffectiveGuideSpacing() + getEffectiveGuideOffsetX();
        float y = getY() + rowBottom(endRow);
        float height = (endRow - startRow + 1) * lineHeight;
        Color guideColor = rainbowGuidesEnabled ? getRainbowGuideColor(depth, emphasized) : null;
        if (guideColor != null && drawColoredBar(batch, guideX, y, 1f, height, guideColor)) {
            return;
        }
        if (style.guide != null) {
            style.guide.draw(batch, guideX, y, 1f, height);
        }
    }

    private boolean drawColoredBar(Batch batch, float x, float y, float width, float height, Color color) {
        if (style.whitePixelTexture == null || color == null) {
            return false;
        }
        Color previous = batch.getColor();
        float previousR = previous.r;
        float previousG = previous.g;
        float previousB = previous.b;
        float previousA = previous.a;
        batch.setColor(color);
        batch.draw(style.whitePixelTexture, x, y, width, height);
        batch.setColor(previousR, previousG, previousB, previousA);
        return true;
    }

    private float getEffectiveGuideSpacing() {
        return style.guideSpacing * zoomScale;
    }

    private float getEffectiveGuideOffsetX() {
        return style.guideOffsetX * zoomScale;
    }

    private void drawFoldIndicator(Batch batch, int line, float rowBottom, float baseline) {
        FoldRegion region = foldRegionsByStart.get(line);
        if (region == null) {
            return;
        }

        Drawable indicator = region.collapsed ? style.foldCollapsed : style.foldExpanded;
        if (indicator == null) {
            return;
        }

        float indicatorWidth = getFoldIndicatorWidth();
        float indicatorHeight = getFoldIndicatorHeight();
        float x = getX() + getGutterRenderX() + getGutterWidth() - style.foldIndicatorRightPadding - indicatorWidth;
        float y = rowBottom + (lineHeight - indicatorHeight) * 0.5f;
        indicator.draw(batch, x, y, indicatorWidth, indicatorHeight);
    }

    private void drawScrollbar(Batch batch) {
        if (style.scrollbarTrack == null || style.scrollbarKnob == null) {
            return;
        }

        if (hasVerticalScrollbar()) {
            float x = getX() + getWidth() - style.scrollbarWidth - style.scrollbarMargin;
            float y = getY() + getScrollbarTrackY();
            float trackHeight = getScrollbarTrackHeight();
            style.scrollbarTrack.draw(batch, x, y, style.scrollbarWidth, trackHeight);

            float thumbHeight = getScrollbarThumbHeight();
            float thumbY = getY() + getScrollbarThumbY();
            style.scrollbarKnob.draw(batch, x, thumbY, style.scrollbarWidth, thumbHeight);
        }

        if (hasHorizontalScrollbar()) {
            float x = getX() + getHorizontalScrollbarTrackX();
            float y = getY() + getHorizontalScrollbarTrackY();
            float trackWidth = getHorizontalScrollbarTrackWidth();
            style.scrollbarTrack.draw(batch, x, y, trackWidth, style.scrollbarWidth);

            float thumbWidth = getHorizontalScrollbarThumbWidth();
            float thumbX = getX() + getHorizontalScrollbarThumbX();
            style.scrollbarKnob.draw(batch, thumbX, y, thumbWidth, style.scrollbarWidth);
        }
    }

    private void drawSelectionHandles(Batch batch) {
        SelectionRange selection = getSelectionRange();
        if (selection == null || style.selectionHandle == null || !shouldShowTouchHandles()) {
            return;
        }
        HandlePlacement start = getHandlePlacement(selection.startLine, selection.startColumn, false);
        HandlePlacement end = getHandlePlacement(selection.endLine, selection.endColumn, true);
        if (start != null) {
            style.selectionHandle.draw(
                batch,
                getX() + start.x - style.selectionHandleRadius,
                getY() + start.y - style.selectionHandleRadius,
                style.selectionHandleRadius * 2f,
                style.selectionHandleRadius * 2f
            );
        }
        if (end != null) {
            style.selectionHandle.draw(
                batch,
                getX() + end.x - style.selectionHandleRadius,
                getY() + end.y - style.selectionHandleRadius,
                style.selectionHandleRadius * 2f,
                style.selectionHandleRadius * 2f
            );
        }
    }

    private boolean shouldShowTouchHandles() {
        return useTouchInteractions();
    }

    private void moveCursorByVisualRows(int delta) {
        ensureLayout();
        CursorPlacement placement = getCursorPlacement();
        if (placement == null) {
            return;
        }

        float targetX = preferredCursorX >= 0f ? preferredCursorX : placement.x + scrollX - getTextStartX();
        int targetRow = clamp(placement.row + delta, 0, totalVisualRows - 1);
        moveCursorToVisualRow(targetRow, targetX);
        preferredCursorX = targetX;
        ensureCursorVisible();
    }

    private void ensureCursorVisible() {
        CursorPlacement placement = getCursorPlacement();
        if (placement == null) {
            return;
        }

        float rowTop = placement.row * lineHeight;
        float rowBottom = rowTop + lineHeight;
        float viewportBottom = scrollY + getContentHeight();

        if (rowTop < scrollY) {
            scrollY = rowTop;
        } else if (rowBottom > viewportBottom) {
            scrollY = rowBottom - getContentHeight();
        }

        float viewportLeft = getHorizontalViewportLeft();
        float viewportRight = getHorizontalViewportRight();
        float contentX = placement.x + scrollX;
        if (placement.x < viewportLeft) {
            scrollX = contentX - viewportLeft;
        } else if (placement.x > viewportRight) {
            scrollX = contentX - viewportRight;
        }

        clampScroll();
    }

    private void placeCursor(float x, float y) {
        int row = rowAt(y);
        if (row < 0 || row >= totalVisualRows) {
            return;
        }

        placeCursorAtRow(x, row);
    }

    private void placeCursorForDrag(float x, float y) {
        int row = rowAtClamped(y);
        if (row < 0 || row >= totalVisualRows) {
            return;
        }

        placeCursorAtRow(x, row);
    }

    private void placeCursorAtRow(float x, int row) {
        if (row < 0 || row >= totalVisualRows) {
            return;
        }

        int line = findLineByVisualRow(row);
        if (line < 0 || line >= lineLayouts.size) {
            return;
        }

        LineLayout layout = lineLayouts.get(line);
        int segment = clamp(row - visualRowStart[line], 0, layout.getVisualRowCount() - 1);
        int start = layout.segmentStarts.get(segment);
        int end = layout.segmentEnds.get(segment);
        float localX = Math.max(0f, x + scrollX - getTextStartX());
        int column = findColumnForX(layout, start, end, localX);
        document.moveCursorTo(line, column);
        resetPreferredColumn();
        ensureCursorVisible();
    }

    private CodePoint getCodePointAt(float x, float y, boolean clampY) {
        int row = clampY ? rowAtClamped(y) : rowAt(y);
        if (row < 0 || row >= totalVisualRows) {
            return null;
        }

        int line = findLineByVisualRow(row);
        if (line < 0 || line >= lineLayouts.size) {
            return null;
        }

        LineLayout layout = lineLayouts.get(line);
        int segment = clamp(row - visualRowStart[line], 0, layout.getVisualRowCount() - 1);
        int start = layout.segmentStarts.get(segment);
        int end = layout.segmentEnds.get(segment);
        float localX = Math.max(0f, x + scrollX - getTextStartX());
        int column = findColumnForX(layout, start, end, localX);
        return new CodePoint(line, column);
    }

    private HandlePlacement getHandlePlacement(int line, int column, boolean endHandle) {
        CursorPlacement placement = getCursorPlacement(line, column);
        if (placement == null) {
            return null;
        }
        float rowBottom = rowBottom(placement.row);
        float y = endHandle ? rowBottom + 2f : rowBottom + lineHeight - 2f;
        return new HandlePlacement(placement.x, y);
    }

    private boolean beginHandleDrag(float x, float y) {
        SelectionRange selection = getSelectionRange();
        if (selection == null) {
            return false;
        }

        HandlePlacement start = getHandlePlacement(selection.startLine, selection.startColumn, false);
        HandlePlacement end = getHandlePlacement(selection.endLine, selection.endColumn, true);
        if (isNearHandle(x, y, start)) {
            draggingStartHandle = true;
            draggingEndHandle = false;
            pendingTouchPress = false;
            longPressTriggered = false;
            handleDragFixedLine = selection.endLine;
            handleDragFixedColumn = selection.endColumn;
            touchScrollVelocityX = 0f;
            touchScrollVelocityY = 0f;
            return true;
        }
        if (isNearHandle(x, y, end)) {
            draggingStartHandle = false;
            draggingEndHandle = true;
            pendingTouchPress = false;
            longPressTriggered = false;
            handleDragFixedLine = selection.startLine;
            handleDragFixedColumn = selection.startColumn;
            touchScrollVelocityX = 0f;
            touchScrollVelocityY = 0f;
            return true;
        }
        return false;
    }

    private boolean isNearHandle(float x, float y, HandlePlacement handle) {
        if (handle == null) {
            return false;
        }
        float dx = x - handle.x;
        float dy = y - handle.y;
        float radius = style.selectionHandleRadius * style.selectionHandleTouchRadiusMultiplier;
        return dx * dx + dy * dy <= radius * radius;
    }

    private void updateSelectionHandleDrag(float x, float y) {
        if (handleDragFixedLine < 0) {
            return;
        }
        CodePoint point = getCodePointAt(x, y, true);
        if (point == null) {
            return;
        }

        selectionAnchorLine = handleDragFixedLine;
        selectionAnchorColumn = handleDragFixedColumn;
        document.moveCursorTo(point.line, point.column);
        ensureCursorVisible();
        refreshBlink();
    }

    private boolean isPotentialDoubleTap(float x, float y, long nowNanos) {
        if (lastTapTimeNanos == 0L) {
            return false;
        }
        if (nowNanos - lastTapTimeNanos > DOUBLE_TAP_NS) {
            return false;
        }
        float dx = x - lastTapX;
        float dy = y - lastTapY;
        return dx * dx + dy * dy <= TOUCH_SLOP * TOUCH_SLOP;
    }

    private boolean isMouseDoubleClick(float x, float y, long nowNanos) {
        if (lastMouseTapTimeNanos == 0L) {
            return false;
        }
        if (nowNanos - lastMouseTapTimeNanos > DOUBLE_TAP_NS) {
            return false;
        }
        float dx = x - lastMouseTapX;
        float dy = y - lastMouseTapY;
        return dx * dx + dy * dy <= TOUCH_SLOP * TOUCH_SLOP;
    }

    private CursorPlacement getCursorPlacement(int line, int column) {
        ensureLayout();
        if (line < 0 || line >= lineLayouts.size) {
            return null;
        }
        if (line < hiddenLines.length && hiddenLines[line]) {
            return null;
        }

        LineLayout layout = lineLayouts.get(line);
        int safeColumn = Math.max(0, Math.min(column, layout.text.length()));
        int segment = layout.findSegmentForColumn(safeColumn);
        int start = layout.segmentStarts.get(segment);
        float x = getTextStartX() + layout.measureRange(start, safeColumn) - scrollX;
        int row = visualRowStart[line] + segment;
        return new CursorPlacement(row, x);
    }

    private FoldRegion findRelevantRegion(int line) {
        FoldRegion direct = foldRegionsByStart.get(line);
        if (direct != null) {
            return direct;
        }
        return findContainingRegion(line);
    }

    private FoldRegion findContainingRegion(int line) {
        FoldRegion best = null;
        int bestSpan = Integer.MAX_VALUE;
        for (FoldRegion region : foldRegions) {
            if (region.startLine < line && line <= region.endLine) {
                int span = region.endLine - region.startLine;
                if (span < bestSpan) {
                    best = region;
                    bestSpan = span;
                }
            }
        }
        return best;
    }

    private void toggleFold(FoldRegion region) {
        if (region == null) {
            return;
        }

        float previousScroll = scrollY;
        if (collapsedRegionKeys.contains(region.key)) {
            collapsedRegionKeys.remove(region.key);
        } else {
            collapsedRegionKeys.add(region.key);
        }

        invalidateLayout();
        ensureLayout();
        scrollY = Math.max(getMinScroll(), Math.min(previousScroll, getMaxScroll()));
        refreshBlink();
    }

    private boolean expandCollapsedRegion(FoldRegion region) {
        if (region == null || !collapsedRegionKeys.contains(region.key)) {
            return false;
        }
        float previousScroll = scrollY;
        collapsedRegionKeys.remove(region.key);
        invalidateLayout();
        ensureLayout();
        scrollY = Math.max(getMinScroll(), Math.min(previousScroll, getMaxScroll()));
        return true;
    }

    private boolean expandCollapsedRegionAtLine(int line) {
        return expandCollapsedRegion(foldRegionsByStart.get(line));
    }

    private void expandCollapsedRegionsForEdit(EditIntent intent) {
        boolean changed = false;
        SelectionRange selection = getSelectionRange();
        if (selection != null) {
            for (FoldRegion region : foldRegions) {
                if (region.collapsed && region.startLine <= selection.endLine && region.endLine >= selection.startLine) {
                    collapsedRegionKeys.remove(region.key);
                    changed = true;
                }
            }
        }

        int cursorLine = document.getCursorLine();
        if (intent == EditIntent.BACKSPACE && document.getCursorColumn() == 0) {
            for (FoldRegion region : foldRegions) {
                if (region.collapsed && region.endLine + 1 == cursorLine) {
                    collapsedRegionKeys.remove(region.key);
                    changed = true;
                }
            }
        }

        if (intent == EditIntent.DELETE && cursorLine < document.getLineCount()
            && document.getCursorColumn() >= document.getLineLength(cursorLine)) {
            changed |= expandCollapsedRegionAtLine(cursorLine);
        }

        if (intent == EditIntent.INSERT || intent == EditIntent.DELETE || intent == EditIntent.ENTER
            || intent == EditIntent.TAB || intent == EditIntent.TYPE) {
            changed |= expandCollapsedRegionAtLine(cursorLine);
        }

        if (changed) {
            invalidateLayout();
            ensureLayout();
        }
    }

    private void applySelectionAutoScroll(float delta) {
        float deltaScrollX = 0f;
        float deltaScrollY = 0f;
        float viewportLeft = getHorizontalViewportLeft();
        float viewportRight = getHorizontalViewportRight();
        if (lastDragX < viewportLeft) {
            float distance = Math.min(AUTO_SCROLL_EDGE, viewportLeft - lastDragX);
            deltaScrollX = -AUTO_SCROLL_MAX_SPEED * (distance / AUTO_SCROLL_EDGE) * delta;
        } else if (lastDragX > viewportRight) {
            float distance = Math.min(AUTO_SCROLL_EDGE, lastDragX - viewportRight);
            deltaScrollX = AUTO_SCROLL_MAX_SPEED * (distance / AUTO_SCROLL_EDGE) * delta;
        }
        if (lastDragY > contentTopY()) {
            float distance = Math.min(AUTO_SCROLL_EDGE, lastDragY - contentTopY());
            deltaScrollY = -AUTO_SCROLL_MAX_SPEED * (distance / AUTO_SCROLL_EDGE) * delta;
        } else if (lastDragY < style.statusBarHeight) {
            float distance = Math.min(AUTO_SCROLL_EDGE, style.statusBarHeight - lastDragY);
            deltaScrollY = AUTO_SCROLL_MAX_SPEED * (distance / AUTO_SCROLL_EDGE) * delta;
        }

        if (deltaScrollX == 0f && deltaScrollY == 0f) {
            return;
        }

        float previousScrollX = scrollX;
        float previousScrollY = scrollY;
        scrollX += deltaScrollX;
        scrollY += deltaScrollY;
        clampScroll();
        if (scrollX != previousScrollX || scrollY != previousScrollY) {
            if (draggingSelectedText) {
                updateSelectedTextDrag(lastDragX, lastDragY);
            } else if (draggingStartHandle || draggingEndHandle) {
                updateSelectionHandleDrag(lastDragX, lastDragY);
            } else {
                placeCursorForDrag(lastDragX, lastDragY);
            }
        }
    }

    private void applyTouchFling(float delta) {
        if (Math.abs(touchScrollVelocityX) < TOUCH_FLING_MIN_SPEED
            && Math.abs(touchScrollVelocityY) < TOUCH_FLING_MIN_SPEED) {
            touchScrollVelocityX = 0f;
            touchScrollVelocityY = 0f;
            return;
        }

        applyTouchScrollDelta(touchScrollVelocityX * delta, touchScrollVelocityY * delta);
        suppressOutwardFlingAtBounds();

        float damping = Math.max(0f, 1f - TOUCH_FLING_DAMPING * delta);
        touchScrollVelocityX *= damping;
        touchScrollVelocityY *= damping;
        if (Math.abs(touchScrollVelocityX) < TOUCH_FLING_MIN_SPEED) {
            touchScrollVelocityX = 0f;
        }
        if (Math.abs(touchScrollVelocityY) < TOUCH_FLING_MIN_SPEED) {
            touchScrollVelocityY = 0f;
        }
    }

    private void suppressOutwardFlingAtBounds() {
        float minScrollX = getMinScrollX();
        float maxScrollX = getMaxScrollX();
        if (scrollX <= minScrollX + 0.5f && touchScrollVelocityX < 0f) {
            touchScrollVelocityX = 0f;
        } else if (scrollX >= maxScrollX - 0.5f && touchScrollVelocityX > 0f) {
            touchScrollVelocityX = 0f;
        }

        float minScrollY = getMinScroll();
        float maxScrollY = getMaxScroll();
        if (scrollY <= minScrollY + 0.5f && touchScrollVelocityY < 0f) {
            touchScrollVelocityY = 0f;
        } else if (scrollY >= maxScrollY - 0.5f && touchScrollVelocityY > 0f) {
            touchScrollVelocityY = 0f;
        }
    }

    private void applyTouchBounce(float delta) {
        float minScrollX = getMinScrollX();
        float maxScrollX = getMaxScrollX();
        if (scrollX < minScrollX) {
            float distance = minScrollX - scrollX;
            scrollX += distance * Math.min(1f, TOUCH_BOUNCE_STIFFNESS * delta);
            if (Math.abs(minScrollX - scrollX) < 0.5f) {
                scrollX = minScrollX;
            }
        } else if (scrollX > maxScrollX) {
            float distance = scrollX - maxScrollX;
            scrollX -= distance * Math.min(1f, TOUCH_BOUNCE_STIFFNESS * delta);
            if (Math.abs(scrollX - maxScrollX) < 0.5f) {
                scrollX = maxScrollX;
            }
        }

        float minScroll = getMinScroll();
        float maxScroll = getMaxScroll();
        if (scrollY < minScroll) {
            float distance = minScroll - scrollY;
            scrollY += distance * Math.min(1f, TOUCH_BOUNCE_STIFFNESS * delta);
            if (Math.abs(minScroll - scrollY) < 0.5f) {
                scrollY = minScroll;
            }
        } else if (scrollY > maxScroll) {
            float distance = scrollY - maxScroll;
            scrollY -= distance * Math.min(1f, TOUCH_BOUNCE_STIFFNESS * delta);
            if (Math.abs(scrollY - maxScroll) < 0.5f) {
                scrollY = maxScroll;
            }
        }
    }

    private void applyTouchScrollDelta(float deltaX, float deltaY) {
        scrollX = applyTouchScrollAxis(scrollX, deltaX, getMinScrollX(), getMaxScrollX());
        scrollY = applyTouchScrollAxis(scrollY, deltaY, getMinScroll(), getMaxScroll());
    }

    private float applyTouchScrollAxis(float current, float delta, float min, float max) {
        float next = current + delta;
        if (current < min) {
            if (delta < 0f) {
                next = current + delta * TOUCH_OVERSCROLL_DAMPING;
            }
            return Math.max(min - TOUCH_OVERSCROLL_LIMIT, Math.min(max + TOUCH_OVERSCROLL_LIMIT, next));
        }
        if (current > max) {
            if (delta > 0f) {
                next = current + delta * TOUCH_OVERSCROLL_DAMPING;
            }
            return Math.max(min - TOUCH_OVERSCROLL_LIMIT, Math.min(max + TOUCH_OVERSCROLL_LIMIT, next));
        }
        if (next < min) {
            return Math.max(min - TOUCH_OVERSCROLL_LIMIT, min + (next - min) * TOUCH_OVERSCROLL_DAMPING);
        }
        if (next > max) {
            return Math.min(max + TOUCH_OVERSCROLL_LIMIT, max + (next - max) * TOUCH_OVERSCROLL_DAMPING);
        }
        return next;
    }

    private boolean isInVerticalScrollbarHitArea(float x, float y) {
        return hasVerticalScrollbar()
            && x >= getWidth() - style.scrollbarHitWidth
            && x <= getWidth()
            && y >= style.statusBarHeight
            && y <= contentTopY();
    }

    private boolean isInHorizontalScrollbarHitArea(float x, float y) {
        return hasHorizontalScrollbar()
            && x >= getHorizontalScrollbarTrackX()
            && x <= getHorizontalScrollbarTrackX() + getHorizontalScrollbarTrackWidth()
            && y >= getHorizontalScrollbarTrackY() - style.scrollbarHitInset
            && y <= getHorizontalScrollbarTrackY() + style.scrollbarWidth + style.scrollbarHitInset;
    }

    private boolean hasVerticalScrollbar() {
        return totalVisualRows * lineHeight > getContentHeight();
    }

    private boolean hasHorizontalScrollbar() {
        return getMaxScrollX() > 0f;
    }

    private float getMinScrollX() {
        return 0f;
    }

    private float getMaxScrollX() {
        if (wrapEnabled) {
            return 0f;
        }
        return Math.max(0f, maxLineWidth - getHorizontalViewportWidth());
    }

    private float getMinScroll() {
        return 0f;
    }

    private float getMaxScroll() {
        return Math.max(0f, totalVisualRows * lineHeight - getContentHeight());
    }

    private float getScrollbarTrackY() {
        return style.statusBarHeight + style.scrollbarMargin;
    }

    private float getScrollbarTrackHeight() {
        return getContentHeight() - style.scrollbarMargin * 2f;
    }

    private float getScrollbarThumbHeight() {
        float contentHeight = getContentHeight();
        float totalHeight = totalVisualRows * lineHeight;
        return Math.max(style.scrollbarMinThumbSize, getScrollbarTrackHeight() * (contentHeight / totalHeight));
    }

    private float getScrollbarThumbY() {
        float maxScroll = Math.max(0f, totalVisualRows * lineHeight - getContentHeight());
        if (maxScroll <= 0f) {
            return getScrollbarTrackY();
        }
        float thumbHeight = getScrollbarThumbHeight();
        float thumbTravel = getScrollbarTrackHeight() - thumbHeight;
        return getScrollbarTrackY() + thumbTravel * (1f - (scrollY / maxScroll));
    }

    private float getHorizontalScrollbarTrackX() {
        return getHorizontalViewportLeft();
    }

    private float getHorizontalScrollbarTrackY() {
        return style.scrollbarMargin;
    }

    private float getHorizontalScrollbarTrackWidth() {
        return Math.max(style.scrollbarMinThumbSize, getHorizontalViewportRight() - getHorizontalScrollbarTrackX());
    }

    private float getHorizontalScrollbarThumbWidth() {
        float contentWidth = Math.max(1f, maxLineWidth);
        return Math.max(style.scrollbarMinThumbSize, getHorizontalScrollbarTrackWidth() * (getHorizontalViewportWidth() / contentWidth));
    }

    private float getHorizontalScrollbarThumbX() {
        float maxScrollX = getMaxScrollX();
        if (maxScrollX <= 0f) {
            return getHorizontalScrollbarTrackX();
        }
        float thumbWidth = getHorizontalScrollbarThumbWidth();
        float thumbTravel = getHorizontalScrollbarTrackWidth() - thumbWidth;
        return getHorizontalScrollbarTrackX() + thumbTravel * (scrollX / maxScrollX);
    }

    private void beginVerticalScrollbarDrag(float y) {
        draggingScrollbar = true;
        draggingHorizontalScrollbar = false;
        draggingSelection = false;
        draggingTouchScroll = false;

        float thumbY = getScrollbarThumbY();
        float thumbHeight = getScrollbarThumbHeight();
        if (y >= thumbY && y <= thumbY + thumbHeight) {
            scrollbarDragOffsetY = y - thumbY;
        } else {
            scrollbarDragOffsetY = thumbHeight * 0.5f;
            updateVerticalScrollbarFromDrag(y);
        }
    }

    private void updateVerticalScrollbarFromDrag(float y) {
        if (!hasVerticalScrollbar()) {
            return;
        }

        float thumbHeight = getScrollbarThumbHeight();
        float thumbTravel = getScrollbarTrackHeight() - thumbHeight;
        float desiredThumbY = Math.max(
            getScrollbarTrackY(),
            Math.min(y - scrollbarDragOffsetY, getScrollbarTrackY() + thumbTravel)
        );
        float maxScroll = Math.max(0f, totalVisualRows * lineHeight - getContentHeight());
        float ratio = thumbTravel <= 0f ? 0f : (desiredThumbY - getScrollbarTrackY()) / thumbTravel;
        scrollY = maxScroll * (1f - ratio);
        clampScroll();
    }

    private void beginHorizontalScrollbarDrag(float x) {
        draggingScrollbar = true;
        draggingHorizontalScrollbar = true;
        draggingSelection = false;
        draggingTouchScroll = false;

        float thumbX = getHorizontalScrollbarThumbX();
        float thumbWidth = getHorizontalScrollbarThumbWidth();
        if (x >= thumbX && x <= thumbX + thumbWidth) {
            scrollbarDragOffsetX = x - thumbX;
        } else {
            scrollbarDragOffsetX = thumbWidth * 0.5f;
            updateHorizontalScrollbarFromDrag(x);
        }
    }

    private void updateHorizontalScrollbarFromDrag(float x) {
        if (!hasHorizontalScrollbar()) {
            return;
        }

        float thumbWidth = getHorizontalScrollbarThumbWidth();
        float thumbTravel = getHorizontalScrollbarTrackWidth() - thumbWidth;
        float desiredThumbX = Math.max(
            getHorizontalScrollbarTrackX(),
            Math.min(x - scrollbarDragOffsetX, getHorizontalScrollbarTrackX() + thumbTravel)
        );
        float maxScrollX = getMaxScrollX();
        float ratio = thumbTravel <= 0f ? 0f : (desiredThumbX - getHorizontalScrollbarTrackX()) / thumbTravel;
        scrollX = maxScrollX * ratio;
        clampScroll();
    }

    private FoldRegion getActiveBlockRegion() {
        FoldRegion direct = foldRegionsByStart.get(document.getCursorLine());
        if (direct != null) {
            return direct;
        }
        return findContainingRegion(document.getCursorLine());
    }

    private BracketMatch getBracketMatch() {
        int line = document.getCursorLine();
        int column = document.getCursorColumn();

        if (line < 0 || line >= document.getLineCount()) {
            return null;
        }

        String currentLine = document.getLine(line);
        if (column > 0 && column - 1 < currentLine.length()) {
            char candidate = currentLine.charAt(column - 1);
            BracketMatch match = isIgnoredBracketPosition(line, column - 1) ? null : findMatchingBracket(line, column - 1, candidate);
            if (match != null) {
                return match;
            }
        }
        if (column < currentLine.length()) {
            char candidate = currentLine.charAt(column);
            if (isIgnoredBracketPosition(line, column)) {
                return null;
            }
            return findMatchingBracket(line, column, candidate);
        }
        return null;
    }

    private BracketMatch findMatchingBracket(int line, int column, char candidate) {
        String brackets = "()[]{}";
        int bracketIndex = brackets.indexOf(candidate);
        if (bracketIndex < 0) {
            return null;
        }

        boolean forward = bracketIndex % 2 == 0;
        char open = forward ? candidate : brackets.charAt(bracketIndex - 1);
        char close = forward ? brackets.charAt(bracketIndex + 1) : candidate;
        int depth = 0;
        ScanState state = new ScanState();

        for (int currentLine = 0; currentLine < document.getLineCount(); currentLine++) {
            state.inLineComment = false;
            String text = document.getLine(currentLine);
            for (int currentColumn = 0; currentColumn < text.length(); currentColumn++) {
                if (isIgnoredBracketPosition(currentLine, currentColumn)) {
                    continue;
                }
                updateScanState(state, text, currentColumn);
                if (!state.isCode) {
                    continue;
                }

                char current = text.charAt(currentColumn);
                if (currentLine == line && currentColumn == column) {
                    depth = 1;
                    if (!forward) {
                        return searchBackwardForBracket(line, column, open, close);
                    }
                    continue;
                }
                if (depth == 0) {
                    continue;
                }
                if (current == open) {
                    depth++;
                } else if (current == close) {
                    depth--;
                    if (depth == 0) {
                        return new BracketMatch(line, column, currentLine, currentColumn);
                    }
                }
            }
        }
        return null;
    }

    private BracketMatch searchBackwardForBracket(int line, int column, char open, char close) {
        Array<CodePosition> positions = new Array<>();
        ScanState state = new ScanState();

        for (int currentLine = 0; currentLine <= line; currentLine++) {
            state.inLineComment = false;
            String text = document.getLine(currentLine);
            int limit = currentLine == line ? column + 1 : text.length();
            for (int currentColumn = 0; currentColumn < limit; currentColumn++) {
                if (isIgnoredBracketPosition(currentLine, currentColumn)) {
                    continue;
                }
                updateScanState(state, text, currentColumn);
                if (!state.isCode) {
                    continue;
                }
                char current = text.charAt(currentColumn);
                if (current == open || current == close) {
                    positions.add(new CodePosition(currentLine, currentColumn, current));
                }
            }
        }

        int depth = 0;
        for (int i = positions.size - 1; i >= 0; i--) {
            CodePosition position = positions.get(i);
            if (position.line == line && position.column == column) {
                depth = 1;
                continue;
            }
            if (depth == 0) {
                continue;
            }
            if (position.value == close) {
                depth++;
            } else if (position.value == open) {
                depth--;
                if (depth == 0) {
                    return new BracketMatch(line, column, position.line, position.column);
                }
            }
        }
        return null;
    }

    private void updateScanState(ScanState state, String text, int index) {
        char current = text.charAt(index);
        char next = index + 1 < text.length() ? text.charAt(index + 1) : 0;

        state.isCode = false;
        if (state.inLineComment) {
            return;
        }
        if (state.inBlockComment) {
            if (current == '*' && next == '/') {
                state.inBlockComment = false;
            }
            return;
        }
        if (state.inString) {
            if (state.escaped) {
                state.escaped = false;
            } else if (current == '\\') {
                state.escaped = true;
            } else if (current == state.stringQuote) {
                state.inString = false;
            }
            return;
        }

        if (current == '/' && next == '/') {
            state.inLineComment = true;
            return;
        }
        if (current == '/' && next == '*') {
            state.inBlockComment = true;
            return;
        }
        if (current == '"' || current == '\'') {
            state.inString = true;
            state.stringQuote = current;
            state.escaped = false;
            return;
        }
        state.isCode = true;
    }

    private int rowAt(float localY) {
        if (localY < style.statusBarHeight || localY > contentTopY()) {
            return -1;
        }
        float distanceFromTop = contentTopY() - localY;
        return (int) Math.floor((scrollY + distanceFromTop) / lineHeight);
    }

    private int rowAtClamped(float localY) {
        if (totalVisualRows <= 0) {
            return -1;
        }
        if (localY > contentTopY()) {
            return clamp((int) Math.floor(scrollY / lineHeight), 0, totalVisualRows - 1);
        }
        if (localY < style.statusBarHeight) {
            return clamp((int) Math.floor((scrollY + getContentHeight() - 1f) / lineHeight), 0, totalVisualRows - 1);
        }
        return clamp(rowAt(localY), 0, totalVisualRows - 1);
    }

    private int findLineByVisualRow(int row) {
        int low = 0;
        int high = visualRowStart.length - 1;

        while (low <= high) {
            int mid = (low + high) >>> 1;
            int start = visualRowStart[mid];
            int count = visualRowsPerLine[mid];
            if (row < start) {
                high = mid - 1;
            } else if (row >= start + count) {
                low = mid + 1;
            } else {
                return mid;
            }
        }

        return clamp(low, 0, visualRowStart.length - 1);
    }

    private int findColumnForX(LineLayout layout, int start, int end, float targetX) {
        int low = start;
        int high = end;
        float base = layout.prefixWidths[start];

        while (low < high) {
            int mid = (low + high) >>> 1;
            float left = layout.prefixWidths[mid] - base;
            float right = layout.prefixWidths[mid + 1] - base;
            if (targetX < left + (right - left) * 0.5f) {
                high = mid;
            } else {
                low = mid + 1;
            }
        }
        return low;
    }

    private float measureRange(String text, int start, int end) {
        return measureText(text.substring(start, Math.min(end, text.length())));
    }

    private CursorPlacement getCursorPlacement() {
        return getCursorPlacement(document.getCursorLine(), document.getCursorColumn());
    }

    private void moveCursorToVisualRow(int row, float targetX) {
        int line = findLineByVisualRow(row);
        if (line < 0 || line >= lineLayouts.size) {
            return;
        }

        LineLayout layout = lineLayouts.get(line);
        int segment = clamp(row - visualRowStart[line], 0, layout.getVisualRowCount() - 1);
        int start = layout.segmentStarts.get(segment);
        int end = layout.segmentEnds.get(segment);
        int column = findColumnForX(layout, start, end, targetX);
        document.moveCursorTo(line, column);
    }

    private boolean isWrapOpportunity(String text, int index) {
        char current = text.charAt(index);
        if (Character.isWhitespace(current)) {
            return true;
        }
        return ",.;:+-*/=%&|!?)>]}".indexOf(current) >= 0;
    }

    private int trimWrappedIndent(String text, int index) {
        int next = index;
        while (next < text.length() && Character.isWhitespace(text.charAt(next)) && text.charAt(next) != '\t') {
            next++;
        }
        return next;
    }

    private float measureText(String text) {
        float originalScaleX = style.font.getData().scaleX;
        float originalScaleY = style.font.getData().scaleY;
        style.font.getData().setScale(getEffectiveFontScaleX(), getEffectiveFontScaleY());
        try {
            glyphLayout.setText(style.font, text);
            return glyphLayout.width;
        } finally {
            style.font.getData().setScale(originalScaleX, originalScaleY);
        }
    }

    private float glyphWidth(char character) {
        if (character == '\t') {
            return glyphWidth(' ') * CodeDocument.INDENT_SIZE;
        }
        if (glyphWidthCache.containsKey(character)) {
            return glyphWidthCache.get(character, style.font.getSpaceXadvance());
        }
        BitmapFont.Glyph glyph = style.font.getData().getGlyph(character);
        float width;
        if (glyph == null) {
            width = Math.max(6f, style.font.getSpaceXadvance() * zoomScale);
        } else {
            width = Math.max(1f, glyph.xadvance * getEffectiveFontScaleX());
        }
        glyphWidthCache.put(character, width);
        return width;
    }

    private float glyphAdvance(String text, int index) {
        if (text == null || index < 0 || index >= text.length()) {
            return 0f;
        }

        char current = text.charAt(index);
        if (current == '\t') {
            return glyphWidth(' ') * CodeDocument.INDENT_SIZE;
        }

        char next = index + 1 < text.length() ? text.charAt(index + 1) : 0;
        int cacheKey = (current << 16) | next;
        if (glyphAdvanceCache.containsKey(cacheKey)) {
            return glyphAdvanceCache.get(cacheKey, 0f);
        }

        BitmapFont.Glyph glyph = style.font.getData().getGlyph(current);
        float advance;
        if (glyph == null) {
            advance = Math.max(6f, style.font.getSpaceXadvance() * getEffectiveFontScaleX());
        } else {
            advance = glyph.xadvance * getEffectiveFontScaleX();
            if (next != 0) {
                advance += glyph.getKerning(next) * getEffectiveFontScaleX();
            }
            advance = Math.max(1f, advance);
        }
        glyphAdvanceCache.put(cacheKey, advance);
        return advance;
    }

    private float getEffectiveFontScaleX() {
        return baseFontScaleX * zoomScale;
    }

    private float getEffectiveFontScaleY() {
        return baseFontScaleY * zoomScale;
    }

    private float getContentHeight() {
        return Math.max(1f, getHeight() - style.topBarHeight - style.statusBarHeight);
    }

    private float getGutterWidth() {
        float numberWidth = measureText(Integer.toString(Math.max(1, document.getLineCount())));
        return Math.max(
            style.gutterMinWidth,
            style.gutterLeftPadding + numberWidth + style.gutterFoldIndicatorGap + getFoldIndicatorWidth() + style.foldIndicatorRightPadding
        );
    }

    private float getGutterRenderX() {
        return lineNumbersFixed ? 0f : -scrollX;
    }

    private float getGutterLineNumberRightX() {
        return getGutterWidth() - style.foldIndicatorRightPadding - getFoldIndicatorWidth() - style.gutterFoldIndicatorGap;
    }

    private float getFoldIndicatorWidth() {
        if (style.foldIndicatorSize > 0f) {
            return style.foldIndicatorSize;
        }
        float expandedWidth = style.foldExpanded == null ? 0f : style.foldExpanded.getMinWidth();
        float collapsedWidth = style.foldCollapsed == null ? 0f : style.foldCollapsed.getMinWidth();
        return Math.max(expandedWidth, collapsedWidth);
    }

    private float getFoldIndicatorHeight() {
        if (style.foldIndicatorSize > 0f) {
            return style.foldIndicatorSize;
        }
        float expandedHeight = style.foldExpanded == null ? 0f : style.foldExpanded.getMinHeight();
        float collapsedHeight = style.foldCollapsed == null ? 0f : style.foldCollapsed.getMinHeight();
        return Math.max(expandedHeight, collapsedHeight);
    }

    private boolean isInsideGutter(float x) {
        float gutterX = getGutterRenderX();
        return x >= gutterX && x <= gutterX + getGutterWidth();
    }

    private float getTextStartX() {
        return getGutterWidth() + style.textLeftPadding;
    }

    private float getTextRenderX() {
        return getTextStartX() - scrollX;
    }

    private float getHorizontalViewportLeft() {
        return lineNumbersFixed ? getTextStartX() : style.textLeftPadding;
    }

    private float getHorizontalViewportRight() {
        float rightInset = style.textRightPadding;
        if (hasVerticalScrollbar()) {
            rightInset += style.scrollbarWidth + style.scrollbarMargin;
        }
        return Math.max(getHorizontalViewportLeft() + 1f, getWidth() - rightInset);
    }

    private float getHorizontalViewportWidth() {
        return Math.max(1f, getHorizontalViewportRight() - getHorizontalViewportLeft());
    }

    private float getWrapWidth() {
        return Math.max(
            1f,
            getWidth() - getTextStartX() - style.textRightPadding - style.scrollbarWidth - style.scrollbarGap
        );
    }

    private float contentTopY() {
        return getHeight() - style.topBarHeight;
    }

    private float rowBottom(int row) {
        float offset = row * lineHeight - scrollY;
        return contentTopY() - offset - lineHeight;
    }

    private void clampScroll() {
        scrollX = Math.max(getMinScrollX(), Math.min(scrollX, getMaxScrollX()));
        scrollY = Math.max(getMinScroll(), Math.min(scrollY, getMaxScroll()));
    }

    private void resetPreferredColumn() {
        preferredCursorX = -1f;
    }

    private void refreshBlink() {
        blinkOrigin = TimeUtils.nanoTime();
    }

    private void setZoomScaleInternal(float requestedScale, float anchorX, float anchorY) {
        float oldZoomScale = zoomScale;
        float oldLineHeight = lineHeight;
        float oldTextStartX = getTextStartX();
        float clampedScale = Math.max(MIN_ZOOM_SCALE, Math.min(requestedScale, MAX_ZOOM_SCALE));
        if (Math.abs(clampedScale - zoomScale) < 0.001f) {
            return;
        }

        ensureLayout();
        float anchorContentX = scrollX + anchorX - oldTextStartX;
        float anchorContentY = scrollY + (contentTopY() - anchorY);

        zoomScale = clampedScale;
        updateFontMetrics();
        glyphWidthCache.clear();
        glyphAdvanceCache.clear();
        invalidateLayout();
        ensureLayout();

        float horizontalScale = oldZoomScale <= 0f ? 1f : zoomScale / oldZoomScale;
        float verticalScale = oldLineHeight <= 0f ? 1f : lineHeight / oldLineHeight;
        scrollX = anchorContentX * horizontalScale - anchorX + getTextStartX();
        scrollY = anchorContentY * verticalScale - (contentTopY() - anchorY);
        clampScroll();
        refreshBlink();
    }

    private void updateTouchPointer(int pointer, float x, float y) {
        if (pointer < 0 || pointer >= MAX_TOUCH_POINTERS) {
            return;
        }
        touchPointersDown[pointer] = true;
        touchPointerX[pointer] = x;
        touchPointerY[pointer] = y;
    }

    private void clearTouchPointer(int pointer) {
        if (pointer < 0 || pointer >= MAX_TOUCH_POINTERS) {
            return;
        }
        touchPointersDown[pointer] = false;
    }

    private int countActiveTouchPointers() {
        int count = 0;
        for (boolean down : touchPointersDown) {
            if (down) {
                count++;
            }
        }
        return count;
    }

    private boolean getFirstTwoActivePointers(int[] pointers) {
        int found = 0;
        for (int i = 0; i < touchPointersDown.length; i++) {
            if (!touchPointersDown[i]) {
                continue;
            }
            pointers[found++] = i;
            if (found == 2) {
                return true;
            }
        }
        return false;
    }

    private void beginPinchZoom() {
        int[] pointers = new int[2];
        if (!getFirstTwoActivePointers(pointers)) {
            return;
        }
        float dx = touchPointerX[pointers[1]] - touchPointerX[pointers[0]];
        float dy = touchPointerY[pointers[1]] - touchPointerY[pointers[0]];
        float distance = (float) Math.sqrt(dx * dx + dy * dy);
        if (distance <= 0f) {
            return;
        }
        pinchZooming = true;
        pinchInitialDistance = distance;
        pinchInitialScale = zoomScale;
        pendingTouchPress = false;
        longPressTriggered = false;
        draggingTouchScroll = false;
        draggingSelection = false;
        draggingStartHandle = false;
        draggingEndHandle = false;
        draggingScrollbar = false;
        draggingHorizontalScrollbar = false;
        touchScrollVelocityX = 0f;
        touchScrollVelocityY = 0f;
        handleDragFixedLine = -1;
        handleDragFixedColumn = -1;
    }

    private void updatePinchZoom() {
        int[] pointers = new int[2];
        if (!getFirstTwoActivePointers(pointers)) {
            pinchZooming = false;
            return;
        }
        float x0 = touchPointerX[pointers[0]];
        float y0 = touchPointerY[pointers[0]];
        float x1 = touchPointerX[pointers[1]];
        float y1 = touchPointerY[pointers[1]];
        float dx = x1 - x0;
        float dy = y1 - y0;
        float distance = (float) Math.sqrt(dx * dx + dy * dy);
        if (pinchInitialDistance <= 0f || distance <= 0f) {
            return;
        }
        float anchorX = (x0 + x1) * 0.5f;
        float anchorY = (y0 + y1) * 0.5f;
        setZoomScaleInternal(pinchInitialScale * (distance / pinchInitialDistance), anchorX, anchorY);
    }

    private boolean isFocused() {
        return getStage() != null && getStage().getKeyboardFocus() == this;
    }

    private boolean isEditingLocked() {
        return disabled || readOnly;
    }

    private void ensureStageScrollFocus() {
        if (getStage() == null) {
            return;
        }
        if (isFocused() && getStage().getScrollFocus() != this) {
            getStage().setScrollFocus(this);
        }
    }

    private Color getRainbowBracketColor(int depth) {
        return getPaletteColor(style.rainbowBracketColors, DEFAULT_RAINBOW_BRACKET_PALETTE, depth);
    }

    private Color getRainbowGuideColor(int depth, boolean emphasized) {
        Color base = getPaletteColor(style.rainbowGuideColors, DEFAULT_RAINBOW_GUIDE_PALETTE, depth);
        if (!emphasized) {
            return base;
        }
        return new Color(
            Math.max(0f, base.r * 0.84f),
            Math.max(0f, base.g * 0.84f),
            Math.max(0f, base.b * 0.84f),
            Math.min(0.72f, base.a + 0.28f)
        );
    }

    private static Color getPaletteColor(Color[] configuredPalette, float[][] fallbackPalette, int depth) {
        Color[] palette = configuredPalette;
        if (palette == null || palette.length == 0) {
            palette = createPalette(fallbackPalette);
        }
        int safeDepth = Math.max(0, depth);
        return palette[safeDepth % palette.length];
    }

    private static Color[] createPalette(float[][] rgbaPalette) {
        Color[] colors = new Color[rgbaPalette.length];
        for (int i = 0; i < rgbaPalette.length; i++) {
            float[] rgba = rgbaPalette[i];
            colors[i] = new Color(rgba[0], rgba[1], rgba[2], rgba[3]);
        }
        return colors;
    }

    private static Color[] copyPalette(Color[] colors, float[][] fallbackPalette) {
        Color[] source = colors;
        if (source == null || source.length == 0) {
            source = createPalette(fallbackPalette);
        }
        Color[] copy = new Color[source.length];
        for (int i = 0; i < source.length; i++) {
            copy[i] = new Color(source[i]);
        }
        return copy;
    }

    private boolean isIgnoredBracketPosition(int line, int column) {
        if (line < 0 || line >= bracketIgnoreLines.size) {
            return false;
        }
        return isIgnoredBracketPosition(bracketIgnoreLines.get(line), column);
    }

    private boolean isIgnoredBracketPosition(Array<CodeBracketIgnoreSpan> spans, int column) {
        if (spans == null || spans.size == 0) {
            return false;
        }
        for (CodeBracketIgnoreSpan span : spans) {
            if (column >= span.start && column < span.end) {
                return true;
            }
        }
        return false;
    }

    private static boolean sameColor(Color first, Color second) {
        return first == second || (first != null && second != null && first.toIntBits() == second.toIntBits());
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static final class CursorPlacement {
        final int row;
        final float x;

        CursorPlacement(int row, float x) {
            this.row = row;
            this.x = x;
        }
    }

    private static final class FoldRegion {
        final int startLine;
        final int endLine;
        final int depth;
        final String key;
        boolean collapsed;

        FoldRegion(int startLine, int endLine, int depth, Array<String> lines) {
            this.startLine = startLine;
            this.endLine = endLine;
            this.depth = depth;
            this.key = startLine + ":" + sanitize(lines.get(startLine)) + "->" + sanitize(lines.get(endLine)) + ":" + depth;
        }

        private static String sanitize(String line) {
            String trimmed = line.trim();
            return trimmed.length() > 40 ? trimmed.substring(0, 40) : trimmed;
        }
    }

    private static final class BracketFrame {
        final char open;
        final int depth;

        BracketFrame(char open, int depth) {
            this.open = open;
            this.depth = depth;
        }
    }

    private static final class LineLayout {
        final String text;
        final int indentLevel;
        final Array<HighlightToken> tokens;
        final IntArray segmentStarts = new IntArray();
        final IntArray segmentEnds = new IntArray();
        float[] prefixWidths;

        LineLayout(String text, int indentLevel, Array<HighlightToken> tokens) {
            this.text = text;
            this.indentLevel = indentLevel;
            this.tokens = tokens;
        }

        int getVisualRowCount() {
            return Math.max(1, segmentEnds.size);
        }

        int findSegmentForColumn(int column) {
            int clampedColumn = Math.max(0, Math.min(column, text.length()));
            for (int i = 0; i < segmentEnds.size; i++) {
                if (clampedColumn <= segmentEnds.get(i)) {
                    return i;
                }
            }
            return Math.max(0, segmentEnds.size - 1);
        }

        void ensurePrefixWidths(CodeEditor editor) {
            if (prefixWidths != null && prefixWidths.length == text.length() + 1) {
                return;
            }
            prefixWidths = new float[text.length() + 1];
            for (int i = 0; i < text.length(); i++) {
                prefixWidths[i + 1] = prefixWidths[i] + editor.glyphAdvance(text, i);
            }
        }

        float measureRange(int start, int end) {
            int safeStart = Math.max(0, Math.min(start, text.length()));
            int safeEnd = Math.max(safeStart, Math.min(end, text.length()));
            return prefixWidths[safeEnd] - prefixWidths[safeStart];
        }
    }

    private static final class ColorSpan {
        final int start;
        final int end;
        final Color color;
        final int priority;

        ColorSpan(int start, int end, Color color, int priority) {
            this.start = start;
            this.end = end;
            this.color = color;
            this.priority = priority;
        }
    }

    private static final class SearchMatch {
        final int start;
        final int end;

        SearchMatch(int start, int end) {
            this.start = start;
            this.end = end;
        }
    }

    private static final class SearchMatchRef {
        final int line;
        final SearchMatch match;

        SearchMatchRef(int line, SearchMatch match) {
            this.line = line;
            this.match = match;
        }
    }

    private static final class HighlightToken {
        final int start;
        final int end;
        final Color color;

        HighlightToken(int start, int end, Color color) {
            this.start = start;
            this.end = end;
            this.color = color;
        }
    }

    private static final class SelectionRange {
        final int startLine;
        final int startColumn;
        final int endLine;
        final int endColumn;

        SelectionRange(int startLine, int startColumn, int endLine, int endColumn) {
            this.startLine = startLine;
            this.startColumn = startColumn;
            this.endLine = endLine;
            this.endColumn = endColumn;
        }
    }

    private static final class BracketMatch {
        final int anchorLine;
        final int anchorColumn;
        final int matchLine;
        final int matchColumn;

        BracketMatch(int anchorLine, int anchorColumn, int matchLine, int matchColumn) {
            this.anchorLine = anchorLine;
            this.anchorColumn = anchorColumn;
            this.matchLine = matchLine;
            this.matchColumn = matchColumn;
        }
    }

    private static final class CodePoint {
        final int line;
        final int column;

        CodePoint(int line, int column) {
            this.line = line;
            this.column = column;
        }
    }

    private static final class HandlePlacement {
        final float x;
        final float y;

        HandlePlacement(float x, float y) {
            this.x = x;
            this.y = y;
        }
    }

    private static final class CodePosition {
        final int line;
        final int column;
        final char value;

        CodePosition(int line, int column, char value) {
            this.line = line;
            this.column = column;
            this.value = value;
        }
    }

    private static final class ScanState {
        boolean inLineComment;
        boolean inBlockComment;
        boolean inString;
        boolean escaped;
        boolean isCode;
        char stringQuote;
    }

    private enum EditIntent {
        INSERT,
        DELETE,
        BACKSPACE,
        ENTER,
        TAB,
        TYPE
    }

    public static class CodeEditorStyle {
        public BitmapFont font;
        public Color fontColor = new Color(0.85f, 0.89f, 0.94f, 1f);
        public Color disabledFontColor = new Color(0.48f, 0.54f, 0.61f, 1f);
        public Color gutterFontColor = new Color(0.55f, 0.63f, 0.71f, 1f);
        public Color messageFontColor = new Color(0.39f, 0.46f, 0.53f, 1f);
        public Color keywordColor = new Color(0.369f, 0.709f, 0.992f, 1f);
        public Color typeColor = new Color(0.514f, 0.867f, 0.639f, 1f);
        public Color stringColor = new Color(0.984f, 0.63f, 0.396f, 1f);
        public Color commentColor = new Color(0.486f, 0.588f, 0.486f, 1f);
        public Color numberColor = new Color(0.914f, 0.761f, 0.384f, 1f);
        public Color annotationColor = new Color(0.965f, 0.522f, 0.722f, 1f);
        public Color literalColor = new Color(0.875f, 0.506f, 0.506f, 1f);
        public Color[] rainbowBracketColors = copyPalette(null, DEFAULT_RAINBOW_BRACKET_PALETTE);
        public Color[] rainbowGuideColors = copyPalette(null, DEFAULT_RAINBOW_GUIDE_PALETTE);
        public Drawable background;
        public Drawable focusedBackground;
        public Drawable disabledBackground;
        public Drawable gutterBackground;
        public Drawable currentBlock;
        public Drawable currentLine;
        public Drawable cursor;
        public Drawable selection;
        public Drawable searchHighlight;
        public Drawable currentSearchHighlight;
        public Drawable selectionHandle;
        public Drawable bracketMatch;
        public Drawable guide;
        public Drawable foldExpanded;
        public Drawable foldCollapsed;
        public Drawable scrollbarTrack;
        public Drawable scrollbarKnob;
        public Drawable foldBadge;
        public Texture whitePixelTexture;
        public float topBarHeight = DEFAULT_TOP_BAR_HEIGHT;
        public float statusBarHeight = DEFAULT_STATUS_BAR_HEIGHT;
        public float rowPadding = DEFAULT_ROW_PADDING;
        public float textBaselineOffset = -6f;
        public float textLeftPadding = DEFAULT_LEFT_PADDING;
        public float textRightPadding = DEFAULT_RIGHT_PADDING;
        public float gutterMinWidth = DEFAULT_GUTTER_MIN_WIDTH;
        public float gutterLeftPadding = DEFAULT_GUTTER_LEFT_PADDING;
        public float gutterFoldIndicatorGap = DEFAULT_GUTTER_FOLD_GAP;
        public float foldIndicatorSize = DEFAULT_FOLD_INDICATOR_SIZE;
        public float foldIndicatorRightPadding = DEFAULT_FOLD_INDICATOR_RIGHT_PADDING;
        public float scrollbarWidth = DEFAULT_SCROLLBAR_WIDTH;
        public float scrollbarHitWidth = DEFAULT_SCROLLBAR_HIT_WIDTH;
        public float scrollbarGap = DEFAULT_SCROLLBAR_GAP;
        public float scrollbarMargin = DEFAULT_SCROLLBAR_MARGIN;
        public float scrollbarMinThumbSize = DEFAULT_SCROLLBAR_MIN_THUMB_SIZE;
        public float scrollbarHitInset = DEFAULT_SCROLLBAR_HIT_INSET;
        public float guideSpacing = DEFAULT_GUIDE_SPACING;
        public float guideOffsetX = DEFAULT_GUIDE_OFFSET_X;
        public float foldBadgeGap = DEFAULT_FOLD_BADGE_GAP;
        public float foldBadgeHorizontalPadding = DEFAULT_FOLD_BADGE_HORIZONTAL_PADDING;
        public float foldBadgeVerticalPadding = DEFAULT_FOLD_BADGE_VERTICAL_PADDING;
        public float selectionHandleRadius = DEFAULT_SELECTION_HANDLE_RADIUS;
        public float selectionHandleTouchRadiusMultiplier = DEFAULT_SELECTION_HANDLE_TOUCH_RADIUS_MULTIPLIER;

        public CodeEditorStyle() {
        }

        public CodeEditorStyle(CodeEditorStyle style) {
            this.font = style.font;
            this.fontColor = new Color(style.fontColor);
            this.disabledFontColor = new Color(style.disabledFontColor);
            this.gutterFontColor = new Color(style.gutterFontColor);
            this.messageFontColor = new Color(style.messageFontColor);
            this.keywordColor = new Color(style.keywordColor);
            this.typeColor = new Color(style.typeColor);
            this.stringColor = new Color(style.stringColor);
            this.commentColor = new Color(style.commentColor);
            this.numberColor = new Color(style.numberColor);
            this.annotationColor = new Color(style.annotationColor);
            this.literalColor = new Color(style.literalColor);
            this.rainbowBracketColors = copyPalette(style.rainbowBracketColors, DEFAULT_RAINBOW_BRACKET_PALETTE);
            this.rainbowGuideColors = copyPalette(style.rainbowGuideColors, DEFAULT_RAINBOW_GUIDE_PALETTE);
            this.background = style.background;
            this.focusedBackground = style.focusedBackground;
            this.disabledBackground = style.disabledBackground;
            this.gutterBackground = style.gutterBackground;
            this.currentBlock = style.currentBlock;
            this.currentLine = style.currentLine;
            this.cursor = style.cursor;
            this.selection = style.selection;
            this.searchHighlight = style.searchHighlight;
            this.currentSearchHighlight = style.currentSearchHighlight;
            this.selectionHandle = style.selectionHandle;
            this.bracketMatch = style.bracketMatch;
            this.guide = style.guide;
            this.foldExpanded = style.foldExpanded;
            this.foldCollapsed = style.foldCollapsed;
            this.scrollbarTrack = style.scrollbarTrack;
            this.scrollbarKnob = style.scrollbarKnob;
            this.foldBadge = style.foldBadge;
            this.whitePixelTexture = style.whitePixelTexture;
            this.topBarHeight = style.topBarHeight;
            this.statusBarHeight = style.statusBarHeight;
            this.rowPadding = style.rowPadding;
            this.textBaselineOffset = style.textBaselineOffset;
            this.textLeftPadding = style.textLeftPadding;
            this.textRightPadding = style.textRightPadding;
            this.gutterMinWidth = style.gutterMinWidth;
            this.gutterLeftPadding = style.gutterLeftPadding;
            this.gutterFoldIndicatorGap = style.gutterFoldIndicatorGap;
            this.foldIndicatorSize = style.foldIndicatorSize;
            this.foldIndicatorRightPadding = style.foldIndicatorRightPadding;
            this.scrollbarWidth = style.scrollbarWidth;
            this.scrollbarHitWidth = style.scrollbarHitWidth;
            this.scrollbarGap = style.scrollbarGap;
            this.scrollbarMargin = style.scrollbarMargin;
            this.scrollbarMinThumbSize = style.scrollbarMinThumbSize;
            this.scrollbarHitInset = style.scrollbarHitInset;
            this.guideSpacing = style.guideSpacing;
            this.guideOffsetX = style.guideOffsetX;
            this.foldBadgeGap = style.foldBadgeGap;
            this.foldBadgeHorizontalPadding = style.foldBadgeHorizontalPadding;
            this.foldBadgeVerticalPadding = style.foldBadgeVerticalPadding;
            this.selectionHandleRadius = style.selectionHandleRadius;
            this.selectionHandleTouchRadiusMultiplier = style.selectionHandleTouchRadiusMultiplier;
        }
    }

    private final class EditorInputListener extends InputListener {
        @Override
        public boolean touchDown(InputEvent event, float x, float y, int pointer, int button) {
            if (disabled) {
                return false;
            }
            if (button != Input.Buttons.LEFT && button != Input.Buttons.RIGHT && button != -1) {
                return false;
            }
            boolean touchInteraction = useTouchInteractions();
            if (getStage() != null) {
                getStage().setScrollFocus(CodeEditor.this);
                getStage().setKeyboardFocus(CodeEditor.this);
            }
            if (touchInteraction) {
                updateTouchPointer(pointer, x, y);
                if (countActiveTouchPointers() >= 2) {
                    beginPinchZoom();
                    return true;
                }
            }

            ensureLayout();
            lastDragX = x;
            lastDragY = y;
            touchDownX = x;
            touchDownY = y;
            touchScrollVelocityX = 0f;
            touchScrollVelocityY = 0f;
            lastTouchDragTimeNanos = TimeUtils.nanoTime();
            touchDownTimeNanos = lastTouchDragTimeNanos;
            pendingTouchPress = false;
            longPressTriggered = false;
            pendingSelectionMove = false;
            draggingSelectedText = false;
            draggingStartHandle = false;
            draggingEndHandle = false;
            draggingHorizontalScrollbar = false;
            handleDragFixedLine = -1;
            handleDragFixedColumn = -1;

            if (!touchInteraction && button == Input.Buttons.RIGHT) {
                return notifySecondaryClick(x, y);
            }

            if (touchInteraction && beginHandleDrag(x, y)) {
                return true;
            }

            if (isInVerticalScrollbarHitArea(x, y)) {
                beginVerticalScrollbarDrag(y);
                return true;
            }

            if (isInHorizontalScrollbarHitArea(x, y)) {
                beginHorizontalScrollbarDrag(x);
                return true;
            }

            int row = rowAt(y);
            if (row < 0 || row >= totalVisualRows) {
                clearSelection();
                if (isInsideGutter(x) || touchInteraction) {
                    draggingTouchScroll = true;
                }
                return true;
            }

            int line = findLineByVisualRow(row);
            int segment = row - visualRowStart[line];
            FoldRegion region = foldRegionsByStart.get(line);
            if (segment == 0 && region != null && isInsideGutter(x)) {
                toggleFold(region);
                return true;
            }
            if (segment == 0 && region != null && region.collapsed) {
                expandCollapsedRegion(region);
            }

            if (touchInteraction) {
                pendingTouchPress = true;
                draggingTouchScroll = false;
                return true;
            }

            if (isInsideGutter(x)) {
                clearSelection();
                draggingTouchScroll = true;
                return true;
            }

            if (button == Input.Buttons.LEFT && beginSelectedTextDrag(x, y)) {
                draggingSelection = false;
                draggingTouchScroll = false;
                refreshBlink();
                return true;
            }

            clearSelection();
            draggingSelection = true;
            draggingTouchScroll = false;
            placeCursor(x, y);
            selectionAnchorLine = document.getCursorLine();
            selectionAnchorColumn = document.getCursorColumn();
            refreshBlink();
            return true;
        }

        @Override
        public boolean keyDown(InputEvent event, int keycode) {
            if (disabled) {
                return false;
            }
            if (isModifierKey(keycode)) {
                return true;
            }
            boolean ctrl = Gdx.input.isKeyPressed(Input.Keys.CONTROL_LEFT)
                || Gdx.input.isKeyPressed(Input.Keys.CONTROL_RIGHT);
            boolean shift = Gdx.input.isKeyPressed(Input.Keys.SHIFT_LEFT)
                || Gdx.input.isKeyPressed(Input.Keys.SHIFT_RIGHT);
            boolean movementKey = keycode == Input.Keys.LEFT
                || keycode == Input.Keys.RIGHT
                || keycode == Input.Keys.UP
                || keycode == Input.Keys.DOWN
                || keycode == Input.Keys.PAGE_UP
                || keycode == Input.Keys.PAGE_DOWN
                || keycode == Input.Keys.HOME
                || keycode == Input.Keys.END;

            if (ctrl) {
                if (keycode == Input.Keys.Z) {
                    if (readOnly) {
                        return true;
                    }
                    if (shift) {
                        redo();
                    } else {
                        undo();
                    }
                    return true;
                }
                if (keycode == Input.Keys.Y) {
                    if (readOnly) {
                        return true;
                    }
                    redo();
                    return true;
                }
                if (keycode == Input.Keys.A) {
                    selectAllText();
                    return true;
                }
                if (keycode == Input.Keys.C) {
                    if (getSelectionRange() != null) {
                        copySelection();
                    } else {
                        Gdx.app.getClipboard().setContents(document.getLine(document.getCursorLine()));
                    }
                    return true;
                }
                if (keycode == Input.Keys.X) {
                    if (readOnly) {
                        return true;
                    }
                    if (getSelectionRange() != null) {
                        cutSelection();
                    }
                    return true;
                }
                if (keycode == Input.Keys.V) {
                    if (readOnly) {
                        return true;
                    }
                    pasteClipboard();
                    return true;
                }
            }

            if (shift && movementKey) {
                beginSelectionIfNeeded(true);
            }

            switch (keycode) {
                case Input.Keys.LEFT:
                    document.moveCursorLeft();
                    break;
                case Input.Keys.RIGHT:
                    document.moveCursorRight();
                    break;
                case Input.Keys.UP:
                    moveCursorByVisualRows(-1);
                    break;
                case Input.Keys.DOWN:
                    moveCursorByVisualRows(1);
                    break;
                case Input.Keys.PAGE_UP:
                    moveCursorByVisualRows(-Math.max(1, (int) (getContentHeight() / lineHeight) + 1));
                    break;
                case Input.Keys.PAGE_DOWN:
                    moveCursorByVisualRows(Math.max(1, (int) (getContentHeight() / lineHeight) + 1));
                    break;
                case Input.Keys.HOME:
                    document.moveCursorHome();
                    break;
                case Input.Keys.END:
                    document.moveCursorEnd();
                    break;
                case Input.Keys.BACKSPACE:
                    if (readOnly) {
                        return true;
                    }
                    startDeleteKeyRepeat(keycode);
                    if (!performDeleteKey(keycode)) {
                        stopDeleteKeyRepeat(keycode);
                    }
                    return true;
                case Input.Keys.FORWARD_DEL:
                    if (readOnly) {
                        return true;
                    }
                    startDeleteKeyRepeat(keycode);
                    if (!performDeleteKey(keycode)) {
                        stopDeleteKeyRepeat(keycode);
                    }
                    return true;
                case Input.Keys.ENTER:
                    if (readOnly) {
                        return true;
                    }
                    expandCollapsedRegionsForEdit(EditIntent.ENTER);
                    markPendingContentChange(CodeEditorContentChangeType.INSERT);
                    document.beginCompoundEdit();
                    try {
                        deleteSelectionIfPresent();
                        document.insertNewLine();
                    } finally {
                        document.endCompoundEdit();
                    }
                    onDocumentMutated();
                    return true;
                case Input.Keys.TAB:
                    if (readOnly) {
                        return true;
                    }
                    expandCollapsedRegionsForEdit(EditIntent.TAB);
                    markPendingContentChange(CodeEditorContentChangeType.INSERT);
                    document.beginCompoundEdit();
                    try {
                        deleteSelectionIfPresent();
                        document.insertText("    ");
                    } finally {
                        document.endCompoundEdit();
                    }
                    onDocumentMutated();
                    return true;
                case Input.Keys.F2:
                    toggleFold(findRelevantRegion(document.getCursorLine()));
                    return true;
                default:
                    return false;
            }

            if (!shift) {
                clearSelection();
            }
            resetPreferredColumn();
            ensureCursorVisible();
            refreshBlink();
            return true;
        }

        @Override
        public boolean keyUp(InputEvent event, int keycode) {
            if (keycode == Input.Keys.BACKSPACE || keycode == Input.Keys.FORWARD_DEL) {
                stopDeleteKeyRepeat(keycode);
            }
            return false;
        }

        @Override
        public boolean keyTyped(InputEvent event, char character) {
            if (disabled) {
                return false;
            }
            boolean ctrl = Gdx.input.isKeyPressed(Input.Keys.CONTROL_LEFT)
                || Gdx.input.isKeyPressed(Input.Keys.CONTROL_RIGHT);
            if (ctrl || character == 0 || character == 8 || character == 9 || character == 13 || character == 127) {
                return false;
            }

            if (readOnly) {
                return true;
            }

            if (!Character.isISOControl(character)) {
                expandCollapsedRegionsForEdit(EditIntent.TYPE);
                markPendingContentChange(CodeEditorContentChangeType.INSERT);
                document.beginCompoundEdit();
                try {
                    if (character == '}') {
                        document.dedentBeforeClosingBrace();
                    }
                    deleteSelectionIfPresent();
                    document.insertChar(character);
                } finally {
                    document.endCompoundEdit();
                }
                onDocumentMutatedDeferred();
                return true;
            }

            return false;
        }

        @Override
        public void touchDragged(InputEvent event, float x, float y, int pointer) {
            if (disabled) {
                return;
            }
            float previousDragX = lastDragX;
            float previousDragY = lastDragY;
            long previousTimeNanos = lastTouchDragTimeNanos;
            long nowNanos = TimeUtils.nanoTime();
            lastDragX = x;
            lastDragY = y;
            lastTouchDragTimeNanos = nowNanos;
            if (useTouchInteractions()) {
                updateTouchPointer(pointer, x, y);
                if (pinchZooming || countActiveTouchPointers() >= 2) {
                    if (!pinchZooming) {
                        beginPinchZoom();
                    }
                    updatePinchZoom();
                    return;
                }
            }

            if (draggingScrollbar) {
                if (draggingHorizontalScrollbar) {
                    updateHorizontalScrollbarFromDrag(x);
                } else {
                    updateVerticalScrollbarFromDrag(y);
                }
                return;
            }
            if (draggingStartHandle || draggingEndHandle) {
                updateSelectionHandleDrag(x, y);
                return;
            }
            if (pendingSelectionMove) {
                float dx = x - touchDownX;
                float dy = y - touchDownY;
                if (dx * dx + dy * dy > TOUCH_SLOP * TOUCH_SLOP) {
                    pendingSelectionMove = false;
                    draggingSelectedText = true;
                }
                if (!draggingSelectedText) {
                    return;
                }
            }
            if (draggingSelectedText) {
                updateSelectedTextDrag(x, y);
                return;
            }
            if (pendingTouchPress) {
                float dx = x - touchDownX;
                float dy = y - touchDownY;
                if (dx * dx + dy * dy > TOUCH_SLOP * TOUCH_SLOP) {
                    pendingTouchPress = false;
                    draggingTouchScroll = true;
                    touchScrollVelocityX = 0f;
                    touchScrollVelocityY = 0f;
                }
            }
            if (draggingTouchScroll) {
                float scrollDeltaX = -(x - previousDragX);
                float scrollDeltaY = y - previousDragY;
                float scrollBeforeX = scrollX;
                float scrollBeforeY = scrollY;
                applyTouchScrollDelta(scrollDeltaX, scrollDeltaY);
                float elapsedSeconds = Math.max(0.001f, (nowNanos - previousTimeNanos) / 1_000_000_000f);
                float sampledVelocityX = (scrollX - scrollBeforeX) / elapsedSeconds;
                float sampledVelocityY = (scrollY - scrollBeforeY) / elapsedSeconds;
                touchScrollVelocityX = touchScrollVelocityX * 0.25f + sampledVelocityX * 0.75f;
                touchScrollVelocityY = touchScrollVelocityY * 0.25f + sampledVelocityY * 0.75f;
                return;
            }
            if (!draggingSelection) {
                return;
            }
            placeCursorForDrag(x, y);
            refreshBlink();
        }

        @Override
        public void touchUp(InputEvent event, float x, float y, int pointer, int button) {
            long nowNanos = TimeUtils.nanoTime();
            boolean pinchWasActive = pinchZooming;
            clearTouchPointer(pointer);
            if (useTouchInteractions() && countActiveTouchPointers() < 2) {
                pinchZooming = false;
            }
            if (pinchWasActive) {
                pendingTouchPress = false;
                longPressTriggered = false;
                draggingTouchScroll = false;
                draggingSelection = false;
                draggingStartHandle = false;
                draggingEndHandle = false;
                clearSelectedTextDragState();
                draggingScrollbar = false;
                draggingHorizontalScrollbar = false;
                touchScrollVelocityX = 0f;
                touchScrollVelocityY = 0f;
                return;
            }
            boolean handleDrag = draggingStartHandle || draggingEndHandle;
            boolean selectedTextDrag = draggingSelectedText;
            boolean pendingSelectionMoveTap = pendingSelectionMove;
            boolean shouldFling = draggingTouchScroll
                && (Math.abs(touchScrollVelocityX) >= TOUCH_FLING_MIN_SPEED
                || Math.abs(touchScrollVelocityY) >= TOUCH_FLING_MIN_SPEED);
            boolean wasPendingTap = pendingTouchPress && !longPressTriggered;
            boolean touchInteraction = useTouchInteractions();
            boolean simpleMouseClick = !touchInteraction
                && button == Input.Buttons.LEFT
                && Math.abs(x - touchDownX) <= TOUCH_SLOP
                && Math.abs(y - touchDownY) <= TOUCH_SLOP
                && !selectedTextDrag;
            draggingScrollbar = false;
            draggingHorizontalScrollbar = false;
            draggingTouchScroll = false;
            draggingSelection = false;
            draggingStartHandle = false;
            draggingEndHandle = false;
            handleDragFixedLine = -1;
            handleDragFixedColumn = -1;
            pendingTouchPress = false;
            longPressTriggered = false;
            pendingSelectionMove = false;
            if (!shouldFling) {
                touchScrollVelocityX = 0f;
                touchScrollVelocityY = 0f;
            }
            if (disabled) {
                return;
            }
            if (selectedTextDrag) {
                finishSelectedTextDrag(x, y);
            } else if (pendingSelectionMoveTap) {
                if (isMouseDoubleClick(x, y, nowNanos)) {
                    selectWordAt(x, y);
                    notifyDoubleClick(x, y, false);
                    lastMouseTapTimeNanos = 0L;
                } else {
                    clearSelection();
                    placeCursor(x, y);
                    lastMouseTapTimeNanos = nowNanos;
                    lastMouseTapX = x;
                    lastMouseTapY = y;
                }
            } else
            if (wasPendingTap && touchInteraction) {
                if (isPotentialDoubleTap(x, y, nowNanos)) {
                    selectWordAt(x, y);
                    notifyDoubleClick(x, y, true);
                    lastTapTimeNanos = 0L;
                } else {
                    clearSelection();
                    placeCursor(x, y);
                    if (shouldShowKeyboardForTouchTap(x, y)) {
                        onscreenKeyboard.show(true);
                    }
                    lastTapTimeNanos = nowNanos;
                    lastTapX = x;
                    lastTapY = y;
                }
            } else if (simpleMouseClick) {
                if (isMouseDoubleClick(x, y, nowNanos)) {
                    selectWordAt(x, y);
                    notifyDoubleClick(x, y, false);
                    lastMouseTapTimeNanos = 0L;
                } else {
                    clearSelection();
                    placeCursor(x, y);
                    lastMouseTapTimeNanos = nowNanos;
                    lastMouseTapX = x;
                    lastMouseTapY = y;
                }
            }
            if (handleDrag || selectedTextDrag) {
                refreshBlink();
            }
            if (selectionAnchorLine >= 0 && getSelectionRange() == null) {
                clearSelection();
            }
        }

        @Override
        public boolean scrolled(InputEvent event, float x, float y, float amountX, float amountY) {
            ensureLayout();
            boolean shift = Gdx.input.isKeyPressed(Input.Keys.SHIFT_LEFT)
                || Gdx.input.isKeyPressed(Input.Keys.SHIFT_RIGHT);
            touchScrollVelocityX = 0f;
            touchScrollVelocityY = 0f;
            if (Math.abs(amountX) > 0f || shift) {
                float horizontalAmount = Math.abs(amountX) > 0f ? amountX : amountY;
                scrollX += horizontalAmount * lineHeight * WHEEL_SCROLL_ROWS;
            }
            if (!shift || Math.abs(amountX) > 0f) {
                scrollY += amountY * lineHeight * WHEEL_SCROLL_ROWS;
            }
            clampScroll();
            return true;
        }
    }

    private boolean shouldShowKeyboardForTouchTap(float x, float y) {
        if (readOnly || !useTouchInteractions()) {
            return false;
        }
        if (isInsideGutter(x) || isInVerticalScrollbarHitArea(x, y) || isInHorizontalScrollbarHitArea(x, y)) {
            return false;
        }
        int row = rowAt(y);
        return row >= 0 && row < totalVisualRows;
    }

    private static boolean isModifierKey(int keycode) {
        return keycode == Input.Keys.CONTROL_LEFT
            || keycode == Input.Keys.CONTROL_RIGHT
            || keycode == Input.Keys.SHIFT_LEFT
            || keycode == Input.Keys.SHIFT_RIGHT
            || keycode == Input.Keys.ALT_LEFT
            || keycode == Input.Keys.ALT_RIGHT;
    }

}
