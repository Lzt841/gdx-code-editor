package com.lzt841.editor;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.Application.ApplicationType;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.utils.ScissorStack;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.InputListener;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.Widget;
import com.badlogic.gdx.scenes.scene2d.utils.BaseDrawable;
import com.badlogic.gdx.scenes.scene2d.utils.Drawable;
import com.badlogic.gdx.utils.Align;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.IntMap;
import com.badlogic.gdx.utils.IntFloatMap;
import com.badlogic.gdx.utils.ObjectMap;
import com.badlogic.gdx.utils.IntArray;
import com.badlogic.gdx.utils.ScreenUtils;
import com.badlogic.gdx.utils.TimeUtils;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import com.lzt841.editor.highlight.CodeHighlighter;
import com.lzt841.editor.highlight.CodeBracketIgnoreSpan;
import com.lzt841.editor.highlight.CodeHighlightSpan;
import com.lzt841.editor.highlight.CodeSemanticToken;
import com.lzt841.editor.highlight.CodeSemanticTokenType;
import com.lzt841.editor.highlight.IncrementalCodeHighlighter;
import com.lzt841.editor.highlight.JavaCodeHighlighter;
import com.lzt841.editor.input.CodeEditorInteractionContext;
import com.lzt841.editor.input.CodeEditorInteractionListener;
import com.lzt841.editor.input.CodeEditorInteractionMode;
import com.lzt841.editor.input.CodeEditorOnscreenKeyboard;
import com.lzt841.editor.structure.BraceCodeStructureProvider;
import com.lzt841.editor.structure.CodeFoldRegion;
import com.lzt841.editor.structure.CodeStructureInfo;
import com.lzt841.editor.structure.CodeStructureProvider;
import com.lzt841.editor.structure.CodeStructureScanner;
import com.lzt841.editor.structure.CodeSymbol;
import com.lzt841.editor.structure.IncrementalCodeStructureProvider;


/**
 * A scene2d widget for large-text code editing.
 *
 * <h2>How it scales</h2>
 *
 * <p>Per-line work is deferred until a line is actually drawn or measured. A keystroke patches the
 * per-line arrays from the document's edit journal, so its cost is proportional to the lines the edit
 * touched rather than to the document. Concretely:
 *
 * <ul>
 *   <li><b>Layout and highlighting</b> are per line, built on demand by {@code layoutFor} and capped
 *       to a few thousand cached lines. Highlighting needs an
 *       {@link IncrementalCodeHighlighter} for this; a plain {@link CodeHighlighter} still colours the
 *       whole document once per version, which stays correct but does not scale.
 *   <li><b>Structure analysis</b> (folding, indent depth) is O(document), so above 20,000 lines it is
 *       debounced: the previous fold regions stay in use until typing pauses. Query
 *       {@link #isStructureAnalysisPending()} if you need to know.
 *   <li><b>Lexer state</b> is cached per line and advanced with a per-frame budget, so opening an
 *       unterminated block comment cannot stall a keystroke even though it changes every line below.
 *   <li><b>Horizontal extent</b> is estimated from the longest line by character count and refined as
 *       lines are measured. Exact for monospaced fonts; with a proportional font the scrollbar may be
 *       slightly off until the wider line is drawn.
 *   <li><b>Search</b> only rescans lines an edit touched, and does nothing while no search is set.
 * </ul>
 *
 * <p><b>Word wrap</b> needs each line's visual row count, which needs measuring. Those counts are
 * cached per line and maintained incrementally, so a keystroke measures only the lines it changed. The
 * unavoidable cost is a change to the wrap width itself — a window resize — which invalidates every
 * count; above {@value #WRAP_REMEASURE_SYNC_LINE_LIMIT} lines that re-measure is deferred until the
 * width stops changing, so dragging a window edge does not stall. Wrap mode also cannot use the
 * identity row mapping, so it keeps the per-line row arrays that the non-wrapped path skips.
 */
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
    private static final float DEFAULT_SELECTION_HANDLE_TOUCH_RADIUS_MULTIPLIER = 1.25f;
    private static final float SELECTION_HANDLE_TOUCH_RADIUS_MULTIPLIER_MAX = 1.35f;
    private static final int HANDLE_CORNER_TOP_LEFT = 0;
    private static final int HANDLE_CORNER_TOP_RIGHT = 1;
    private static final int HANDLE_CORNER_TOP_CENTER = 2;
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
    private static final int TOUCH_SCROLL_AXIS_NONE = 0;
    private static final int TOUCH_SCROLL_AXIS_HORIZONTAL = 1;
    private static final int TOUCH_SCROLL_AXIS_VERTICAL = 2;
    private static final long LONG_PRESS_NS = 450_000_000L;
    private static final long DOUBLE_TAP_NS = 300_000_000L;
    private static final long CARET_HANDLE_VISIBLE_NS = 1_000_000_000L;
    private static final float KEY_REPEAT_INITIAL_DELAY = 0.42f;
    private static final float KEY_REPEAT_INTERVAL = 0.045f;
    private static final float MIN_ZOOM_SCALE = 0.5f;
    private static final float MAX_ZOOM_SCALE = 3.0f;
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
    /** Upper bound on simultaneously materialized {@link LineLayout} objects. */
    private static final int MAX_MATERIALIZED_LINES = 3000;
    /** Lines between bracket stack snapshots, bounding rainbow bracket replay. */
    private static final int BRACKET_CHECKPOINT_INTERVAL = 256;
    /** Lines of bracket-ignore ranges kept cached; a bracket scan's working set, not the document. */
    private static final int IGNORE_CACHE_SIZE = 512;
    /**
     * How far bracket matching will look from the caret, in lines. A caret next to an unmatched bracket
     * would otherwise scan the whole document, every frame.
     */
    private static final int BRACKET_MATCH_SCAN_LINES = 2000;
    private static final String BRACKET_CHARACTERS = "()[]{}";
    private static final String WRAP_OPPORTUNITY_CHARACTERS = ",.;:+-*/=%&|!?)>]}";
    private static final Array<HighlightToken> EMPTY_TOKENS = new Array<>(0);
    /** Documents at or below this many lines get structure analysis synchronously. */
    private static final int STRUCTURE_SYNC_LINE_LIMIT = 20000;
    /**
     * Lines between structure scanner checkpoints. Sets the cost of an incremental pass: a re-scan starts
     * at the checkpoint before the edit and can only stop at the next one, so an edit costs roughly two
     * intervals of scanning rather than the whole document.
     */
    private static final int STRUCTURE_CHECKPOINT_INTERVAL = 256;
    /** Quiet period before a deferred structure analysis runs, in seconds. */
    private static final float STRUCTURE_ANALYSIS_DELAY = 0.18f;
    /**
     * Quiet period before symbol extraction runs, in seconds.
     *
     * <p>Symbols have their own timer, separate from the structure pass, because they cost far more than
     * the scan does. The incremental scan is bounded by
     * {@link #STRUCTURE_CHECKPOINT_INTERVAL}, but symbol extraction walks every fold region, and measured
     * on a 100k-line file with 28k regions that was roughly two thirds of the whole pass. Folding and
     * indent guides have to keep up with the caret; an outline or breadcrumb does not, so it waits longer
     * and never lands on a keystroke.
     */
    private static final float SYMBOL_ANALYSIS_DELAY = 0.25f;
    /** Above this many lines, a wrap-width change defers the full re-measure until the width settles. */
    private static final int WRAP_REMEASURE_SYNC_LINE_LIMIT = 20000;
    /** Quiet period before a deferred wrap re-measure runs, in seconds. */
    private static final float WRAP_REMEASURE_DELAY = 0.15f;
    /** Lines of lexical state advanced per frame when catching up after an edit. */
    private static final int HIGHLIGHT_STATE_BUDGET_PER_FRAME = 20000;
    /** Extra lines highlighted above and below the viewport, so small scrolls do no work. */
    private static final int HIGHLIGHT_MARGIN_LINES = 40;

    private static final LineLayout EMPTY_LINE_LAYOUT = LineLayout.empty();

    private static final FoldDisplayProvider DEFAULT_FOLD_DISPLAY_PROVIDER = new FoldDisplayProvider() {
        @Override
        public String getCollapsedText(CodeEditor editor, FoldDisplayContext context) {
            return "...";
        }
    };

    private final GlyphLayout glyphLayout = new GlyphLayout();
    private final CodeDocument document = new CodeDocument();
    private final Array<CodeEditorContentListener> contentListeners = new Array<>();
    /** Scratch copies used while notifying, so a listener may edit or unregister from its callback. */
    private final Array<CodeEditorContentListener> contentListenerScratch = new Array<>();
    private final Array<CodeEditorCaretListener> caretListenerScratch = new Array<>();
    private int contentNotifyDepth;
    private final Array<CodeEditorCaretListener> caretListeners = new Array<>();
    private final Array<CodeEditorInputInterceptor> inputInterceptors = new Array<>();
    private final Array<CodeEditorHoverListener> hoverListeners = new Array<>();
    private final Array<CodeEditorScrollListener> scrollListeners = new Array<>();
    /** Scratch copy used while notifying, so a listener may unregister from its own callback. */
    private final Array<CodeEditorScrollListener> scrollListenerScratch = new Array<>();
    /**
     * Offset the previous scroll notification reported, or NaN before the first {@link #act(float)}.
     *
     * <p>The notifier compares offsets rather than instrumenting the places that move them, which is the
     * same trade {@link #notifyCaretMovedIfNeeded()} makes and the reason wheel, scrollbar, keyboard,
     * {@link #setScroll(float, float)} and pane synchronization all report without being wired up
     * individually.
     */
    private float lastNotifiedScrollX = Float.NaN;
    private float lastNotifiedScrollY = Float.NaN;
    /** True from the frame the offset first moves until the first frame it stops. */
    private boolean scrollInProgress;
    /** Pan and fling state already reported, so each gesture edge fires exactly once. */
    private boolean touchScrollDragNotified;
    private boolean flingNotified;
    /**
     * Rows drawn with a segment index their layout did not have, and so drawn with the nearest segment
     * it did. Always zero unless {@code countWrapRows} and {@code wrapLine} disagree; see
     * {@link #getRowSegmentClampCount()}.
     */
    private long rowSegmentClampCount;
    /**
     * Per-line layout, materialized on demand by {@link #layoutFor(int)}. Entries are null until a
     * line is drawn or measured, and are evicted when the cache grows past
     * {@link #MAX_MATERIALIZED_LINES}, so a 100k line document never pays for glyph measurement of
     * lines it has not shown.
     */
    private final Array<LineLayout> lineLayouts = new Array<>();
    /** Approximate count of non-null {@link #lineLayouts} entries, to decide when to evict. */
    private int materializedLineCount;
    /** Guards against {@link #layoutFor(int)} re-entering {@link #ensureLayout()}. */
    private boolean layoutSyncInProgress;
    /** Row mapping needs rebuilding although the document has not changed, i.e. a fold toggled. */
    private boolean rowMappingDirty;
    /** Line range to report in the next content-change event, accumulated from the edit journal. */
    private int pendingChangeStartLine = Integer.MAX_VALUE;
    private int pendingChangeEndLine = -1;
    private int pendingChangeRemovedLines;
    private int pendingChangeInsertedLines;
    /** Any line is hidden by a collapsed fold, so the hidden-line array is meaningful. */
    private boolean anyLineHidden;
    /**
     * Visual row N is document line N, because nothing is collapsed and wrapping is off. The row arrays
     * are not maintained while this holds; read them through {@link #visualRowStartOf(int)}.
     */
    private boolean rowMappingIsIdentity = true;
    /**
     * Visual row count per line in wrap mode, maintained incrementally so a keystroke only re-wraps the
     * lines it touched instead of the document. Zero means "not measured at the current width yet".
     */
    private int[] wrapRowCounts = new int[0];
    /** Wrap width the cached counts were measured at; a change invalidates all of them. */
    private int wrapRowCountsWidth = -1;
    /** Line count the cached counts correspond to, to detect an array that fell out of alignment. */
    private int wrapRowCountsLineCount = -1;
    private int wrapRowsDirtyFrom = Integer.MAX_VALUE;
    private int wrapRowsDirtyTo = -1;
    private boolean wrapRemeasurePending;
    private float wrapRemeasureDelayRemaining;
    private int wrapRemeasureTargetWidth = -1;
    /**
     * Whether wrapped continuation rows are indented to align under the start of their line's text.
     *
     * <p>Off by default: switching it on changes the row count of every indented line that wraps, and an
     * existing caller's scroll extent and click mapping would move underneath it.
     */
    private boolean wrapContinuationIndentEnabled;
    /** Extra indent columns added past the line's own leading whitespace, in space widths. */
    private int wrapContinuationIndentColumns;
    /** Search results need a refresh although the document has not changed. */
    private boolean searchDirty;
    /** Diagnostic marks, drawn as squiggles under the range and as a gutter tick. */
    private final Array<CodeDiagnostic> diagnostics = new Array<>();
    /**
     * Semantic tokens per line, or null for a line with none.
     *
     * <p>Indexed by line, not a flat list scanned per row like {@link #diagnostics}, because there is
     * roughly one semantic token per identifier: a 100k-line file yields hundreds of thousands, and a
     * scan per drawn row would be O(document) sixty times a second. The array is kept the same length
     * as {@link #lineLayouts} by the same splices, so a line's tokens are one index away.
     *
     * <p>Null entries are the common case for blank and comment-only lines and cost one reference.
     */
    private Array<Array<CodeSemanticToken>> semanticTokenLines = new Array<>();
    /** Total tokens held, so {@link #hasSemanticTokens()} does not walk the array. */
    private int semanticTokenCount;
    /** Document version the current tokens were produced against, or -1 when there are none. */
    private int semanticTokenVersion = -1;
    /** Whether edits have moved lines since the tokens were pushed. */
    private boolean semanticTokensShifted;
    /** Master switch, so a caller can compare with and without without dropping the tokens. */
    private boolean semanticHighlightEnabled = true;
    /** Gets first refusal on typed characters, Backspace and Enter. Null means plain insertion. */
    private CodeAutoEditStrategy autoEditStrategy;
    /** Re-entrancy guard, so a strategy's own edits cannot recurse back into the strategy. */
    private boolean autoEditInProgress;
    /** Document version when the outermost {@link #beginCompoundEdit()} opened, or -1. */
    private int compoundEditStartVersion = -1;
    /** Caret carried across an {@link #applyEdits(Array)} batch. Only live inside that call. */
    private int editBatchCaretLine;
    private int editBatchCaretColumn;
    /** Named-action bindings. Never null; {@link CodeKeymap#editorDefaults()} until replaced. */
    private CodeKeymap keymap = CodeKeymap.editorDefaults();
    /** Action whose key is being held for key-repeat, or null. */
    private CodeEditorAction repeatingDeleteAction;
    /** Line marks in insertion order, and the winning mark per line for O(1) lookup while drawing. */
    private final Array<CodeLineMark> lineMarks = new Array<>();
    private final IntMap<CodeLineMark> lineMarksByLine = new IntMap<>();
    /** Whether any mark has an icon, which is what reserves the gutter's icon column. */
    private boolean anyLineMarkHasIcon;
    private final Color scratchTintColor = new Color();
    private CodeEditorPosition hoverPosition;
    private float hoverPointerX;
    private float hoverPointerY;
    private float hoverElapsed;
    private float hoverDelay = 0.45f;
    private boolean hoverPending;
    private boolean hoverActive;
    private int lastNotifiedCaretLine = -1;
    private int lastNotifiedCaretColumn = -1;
    private int lastNotifiedDocumentVersion = -1;
    private CodeEditorTextRange lastNotifiedSelection;
    private final Array<FoldRegion> foldRegions = new Array<>();
    private final IntMap<FoldRegion> foldRegionsByStart = new IntMap<>();
    /** Root symbols from the last structure analysis. Empty until a provider produces them. */
    private final Array<CodeSymbol> symbols = new Array<CodeSymbol>(0);
    /**
     * Depths of the fold regions ending on each line, so the per-frame indent-guide test is a lookup
     * rather than a scan of every region.
     */
    private final IntMap<IntArray> foldDepthsByEndLine = new IntMap<>();
    /**
     * Start lines of the regions the user has collapsed.
     *
     * <p>Tracked by line number and shifted by {@link #spliceCollapsedLines(int, int, int)} on every
     * edit. The previous implementation keyed on a string containing the start line and its text, so
     * inserting a line anywhere above a collapsed region changed its key and the fold silently sprang
     * open.
     */
    private final IntArray collapsedLines = new IntArray();
    private final IntFloatMap glyphWidthCache = new IntFloatMap();
    private final IntFloatMap glyphAdvanceCache = new IntFloatMap();
    private final Vector2 scratchVector = new Vector2();
    private final Vector3 scratchVector3 = new Vector3();
    private final Rectangle clipLocalBounds = new Rectangle();
    private final CodeEditorSettings.Listener settingsListener = new CodeEditorSettings.Listener() {
        @Override
        public void onSettingsChanged(CodeEditorSettings settings) {
            applySettings(settings);
        }
    };
    private final boolean[] touchPointersDown = new boolean[MAX_TOUCH_POINTERS];
    private final float[] touchPointerX = new float[MAX_TOUCH_POINTERS];
    private final float[] touchPointerY = new float[MAX_TOUCH_POINTERS];
    private final SelectionHandleOverlay selectionHandleOverlay = new SelectionHandleOverlay();

    private CodeEditorStyle style;
    private CodeEditorSettings settings;
    private CodeHighlighter highlighter = new JavaCodeHighlighter();
    private CodeStructureProvider structureProvider = new BraceCodeStructureProvider();
    private FoldDisplayProvider foldDisplayProvider = DEFAULT_FOLD_DISPLAY_PROVIDER;
    private CodeEditorInteractionMode interactionMode = CodeEditorInteractionMode.AUTO;
    private CodeEditorInteractionListener interactionListener;
    private CodeEditorOnscreenKeyboard onscreenKeyboard = CodeEditorOnscreenKeyboard.DEFAULT;
    private boolean[] hiddenLines = new boolean[0];
    private int[] visualRowsPerLine = new int[0];
    private int[] visualRowStart = new int[0];
    private int totalVisualRows = 1;
    private Array<Array<CodeBracketIgnoreSpan>> bracketIgnoreLines = new Array<>();

    /** Reused across {@link #layoutFor(int)} calls so building a line allocates no span arrays. */
    private final Array<CodeHighlightSpan> scratchHighlightSpans = new Array<>();
    private final Array<CodeHighlightSpan> scratchRainbowSpans = new Array<>();
    private final Array<CodeHighlightSpan> scratchSemanticSpans = new Array<>();
    private final Array<BracketFrame> scratchBracketStack = new Array<>();
    /** Bracket stack snapshots every {@link #BRACKET_CHECKPOINT_INTERVAL} lines. */
    private final Array<IntArray> bracketCheckpoints = new Array<>();
    /** Reused by the fold-expanding helpers so they allocate nothing per call. */
    private final IntArray scratchExpandLines = new IntArray();
    /** Cheap per-line bracket-ignore ranges, so bracket matching never builds a layout. */
    private final IntArray ignoreCacheLines = new IntArray();
    private final Array<IntArray> ignoreCacheRanges = new Array<>();
    private final Array<CodeBracketIgnoreSpan> scratchIgnoreCacheSpans = new Array<>();
    private int ignoreCacheVersion = -1;
    private int ignoreCacheLastLine = -1;
    private IntArray ignoreCacheLastRanges;
    /** Memoized bracket pair, so the per-frame lookup does not rescan. */
    private BracketMatch bracketMatchCache;
    private int bracketMatchCacheVersion = -1;
    private int bracketMatchCacheLine = -1;
    private int bracketMatchCacheColumn = -1;
    /** Memoized active block, keyed by the structure version since fold regions only change with it. */
    private FoldRegion activeBlockCache;
    private int activeBlockCacheVersion = -2;
    private int activeBlockCacheLine = -1;
    private final Array<ColorSpan> scratchColorSpans = new Array<>();
    private final IntArray scratchBoundaries = new IntArray();
    private final IntArray scratchUniqueBoundaries = new IntArray();

    /** Spans from a non-incremental highlighter, computed per document version. */
    private Array<Array<CodeHighlightSpan>> legacyHighlightLines = new Array<>();
    private int legacyHighlightVersion = -1;

    /** Lexical state at the start of each line, for {@link IncrementalCodeHighlighter}. */
    private int[] lineStartStates = new int[] {IncrementalCodeHighlighter.START_STATE};
    /** Highest line whose entry in {@link #lineStartStates} is known correct. */
    /**
     * First line whose start-state needs revalidation. Everything below it is known correct. Equal to
     * the document's line count when the whole cache is valid.
     */
    private int highlightResyncFrom;
    /** Highest line whose start-state has been computed at least once this document. */
    private int highlightStatesPopulatedThrough;
    /** Indent depth per line, refreshed by the debounced structure pass. */
    private int[] indentLevels = new int[0];
    private int structureAnalyzedVersion = -1;
    private boolean structureAnalysisPending;
    private float structureAnalysisDelayRemaining;
    /** Document version {@link #symbols} was extracted from, tracked separately from the structure pass. */
    private int symbolAnalyzedVersion = -1;
    private boolean symbolAnalysisPending;
    private float symbolAnalysisDelayRemaining;
    /** Drives an {@link IncrementalCodeStructureProvider}; null until the first incremental pass. */
    private CodeStructureScanner structureScanner;
    /** Scanner position snapshots, sorted by line, first entry at line 0. */
    private final Array<CodeStructureScanner.Checkpoint> structureCheckpoints = new Array<>();
    /** Raw regions from the last analysis, in the order the provider closed them. */
    private final Array<CodeFoldRegion> structureRegions = new Array<>();
    /**
     * Scan line each entry of {@link #structureRegions} was closed on.
     *
     * <p>Not the same as its end line — an indentation block closes at a line above the one that
     * de-dented, and a block closed at end of file is emitted at the line count. The splice keys on this,
     * so a region a re-scan will re-emit can be told from one it has to keep.
     */
    private final IntArray structureRegionEmitLines = new IntArray();
    /** Whether the incremental structure cache can be trusted; false forces a full pass. */
    private boolean structureCacheValid;
    /**
     * Document version the per-line caches have been spliced up to.
     *
     * <p>The structure pass can be reached from {@code act()} before {@link #ensureLayout()} has replayed
     * the edit journal. Without this the cache would look clean while the text had moved under it, and a
     * line-count-preserving edit would keep its old fold regions.
     */
    private int lineArraysSyncedVersion = -1;
    /** First line whose structure may have changed, or {@link Integer#MAX_VALUE} when clean. */
    private int structureDirtyFrom = Integer.MAX_VALUE;
    /**
     * Last line any pending edit touched, or -1 when clean. Convergence may only be declared strictly
     * below this, or a checkpoint sitting between two edits would "match" a cache that is itself still
     * stale further down.
     */
    private int structureDirtyTo = -1;
    /** Lines the last structure pass scanned, for {@link #getStructureScanLineCount()}. */
    private int lastStructureScanLines;
    /** Widest line measured so far; only ever grows, so the scroll extent cannot oscillate. */
    private float measuredMaxLineWidth;
    private int estimatedWidestLine = -1;
    private float estimatedWidestLineWidth;

    private float lineHeight;
    private float scrollX;
    private float scrollY;
    private float maxLineWidth;
    private float clipAreaX;
    private float clipAreaY;
    private float clipAreaWidth;
    private float clipAreaHeight;
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
    private boolean clipAreaEnabled = true;
    private boolean customClipArea;
    private boolean lineNumbersVisible = true;
    private boolean lineNumbersFixed = true;
    private boolean magnifierEnabled = true;
    private boolean transientCaretHandleEnabled = true;
    private boolean passwordMode;
    private boolean overscrollEnabled = true;
    private boolean scrollbarsVisible = true;
    private boolean readOnly;
    private boolean wrapEnabled;
    private boolean rainbowBracketsEnabled;
    private boolean rainbowGuidesEnabled;
    private boolean searchCaseSensitive;
    private boolean searchWholeWord;
    private boolean searchRegexEnabled;
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
    private boolean draggingCaretHandle;
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
    private int touchScrollAxisLock = TOUCH_SCROLL_AXIS_NONE;
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
    private float handleDragPointerOffsetX;
    private float handleDragPointerOffsetY;
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
    private boolean zoomEnabled = true;
    private float pinchInitialDistance;
    private float pinchInitialScale = 1f;
    private int searchMatchCount;
    private int searchAnalyzedVersion = -1;
    private int searchDirtyFrom = Integer.MAX_VALUE;
    private int searchDirtyTo = -1;
    private Array<Array<SearchMatch>> searchMatches = new Array<>();
    private final Array<SearchMatchRef> flatSearchMatches = new Array<>();
    private int currentSearchMatchLine = -1;
    private int currentSearchMatchStart = -1;
    private int currentSearchMatchEnd = -1;
    /** Confines matching to one range, so Replace All can be scoped to a selection. Null means whole document. */
    private CodeEditorTextRange searchRange;
    /** Compiled form of {@link #searchText} while {@link #searchRegexEnabled}; null when invalid or unused. */
    private Pattern searchPattern;
    /** Reused across lines so a rescan of a 100k line document does not allocate one matcher per line. */
    private Matcher searchMatcher;
    /** Message from the last failed compile, or null. Surfaced so a UI can mark the field red. */
    private String searchRegexError;
    private boolean searchPatternDirty = true;
    private CodeEditorContentChangeType pendingContentChangeType = CodeEditorContentChangeType.UNKNOWN;
    private String draggedSelectionText = "";
    private boolean deferredMutationProcessingPending;
    private long transientCaretHandleUntilNanos;
    private boolean hadInputFocus;
    private InputFilter inputFilter;
    private char passwordCharacter = '*';

    public CodeEditor(CodeEditorStyle style) {
        setStyle(style);
        setSettings(new CodeEditorSettings());
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
        // The scanner is bound to its provider, and the cached checkpoints describe the old one's states.
        invalidateStructureCache();
        invalidateLayout();
    }

    public FoldDisplayProvider getFoldDisplayProvider() {
        return foldDisplayProvider;
    }

    public void setFoldDisplayProvider(FoldDisplayProvider foldDisplayProvider) {
        this.foldDisplayProvider = foldDisplayProvider == null ? DEFAULT_FOLD_DISPLAY_PROVIDER : foldDisplayProvider;
        invalidateLayout();
    }

    public CodeEditorInteractionMode getInteractionMode() {
        return interactionMode;
    }

    public void setInteractionMode(CodeEditorInteractionMode interactionMode) {
        if (settings != null) {
            settings.setInteractionMode(interactionMode);
            return;
        }
        applyInteractionMode(interactionMode);
    }

    private void applyInteractionMode(CodeEditorInteractionMode interactionMode) {
        this.interactionMode = interactionMode == null ? CodeEditorInteractionMode.AUTO : interactionMode;
    }

    public CodeEditorInteractionListener getInteractionListener() {
        return interactionListener;
    }

    public void setInteractionListener(CodeEditorInteractionListener interactionListener) {
        this.interactionListener = interactionListener;
    }

    /**
     * Registers a hover listener, notified once the mouse has rested over the same document position
     * for {@link #getHoverDelay()} seconds. Used for tooltips such as diagnostic messages.
     */
    public void addHoverListener(CodeEditorHoverListener listener) {
        if (listener != null && !hoverListeners.contains(listener, true)) {
            hoverListeners.add(listener);
        }
    }

    public void removeHoverListener(CodeEditorHoverListener listener) {
        hoverListeners.removeValue(listener, true);
        if (hoverListeners.size == 0) {
            cancelHover();
        }
    }

    public void clearHoverListeners() {
        hoverListeners.clear();
        cancelHover();
    }

    /**
     * Registers a listener for scroll movement and for the touch pan and fling gestures behind it.
     *
     * <p>Adding the first listener also clears the gesture state already reported, so a listener
     * registered while the user is mid-pan or mid-fling is told about the gesture that is still running
     * rather than only about the next one.
     */
    public void addScrollListener(CodeEditorScrollListener listener) {
        if (listener == null || scrollListeners.contains(listener, true)) {
            return;
        }
        if (scrollListeners.size == 0) {
            // The offset may already be midway through a scroll nobody was listening to. Re-seed rather
            // than report the next change as a delta from an origin this listener never saw.
            lastNotifiedScrollX = Float.NaN;
            lastNotifiedScrollY = Float.NaN;
            scrollInProgress = false;
            touchScrollDragNotified = false;
            flingNotified = false;
        }
        scrollListeners.add(listener);
    }

    public void removeScrollListener(CodeEditorScrollListener listener) {
        scrollListeners.removeValue(listener, true);
    }

    public void clearScrollListeners() {
        scrollListeners.clear();
    }

    /** Seconds the pointer must rest before a hover fires. */
    public float getHoverDelay() {
        return hoverDelay;
    }

    public void setHoverDelay(float hoverDelay) {
        this.hoverDelay = Math.max(0f, hoverDelay);
    }

    /** Position currently hovered, or null when nothing is. */
    public CodeEditorPosition getHoveredPosition() {
        return hoverActive ? hoverPosition : null;
    }

    /**
     * Records the pointer position and restarts the hover delay when it moved to a different document
     * position. Called from {@code mouseMoved}, so it must stay cheap.
     */
    private void updateHoverTarget(float localX, float localY) {
        if (hoverListeners.size == 0) {
            return;
        }
        hoverPointerX = localX;
        hoverPointerY = localY;

        CodeEditorPosition position = getPositionAtLocal(localX, localY, false);
        if (position == null) {
            cancelHover();
            return;
        }
        if (hoverPosition != null
            && position.line == hoverPosition.line
            && position.column == hoverPosition.column) {
            // Same position: let the existing timer keep running.
            return;
        }
        if (hoverActive) {
            notifyHoverEnd();
        }
        hoverPosition = position;
        hoverElapsed = 0f;
        hoverPending = true;
    }

    private void cancelHover() {
        if (hoverActive) {
            notifyHoverEnd();
        }
        hoverPending = false;
        hoverElapsed = 0f;
        hoverPosition = null;
    }

    /** Advances the hover timer and fires once the pointer has been still long enough. */
    private void updateHover(float delta) {
        if (!hoverPending || hoverListeners.size == 0 || hoverPosition == null) {
            return;
        }
        hoverElapsed += delta;
        if (hoverElapsed < hoverDelay) {
            return;
        }
        hoverPending = false;
        hoverActive = true;
        for (int i = 0; i < hoverListeners.size; i++) {
            hoverListeners.get(i).onHoverStart(this, hoverPosition, hoverPointerX, hoverPointerY);
        }
    }

    private void notifyHoverEnd() {
        hoverActive = false;
        for (int i = 0; i < hoverListeners.size; i++) {
            hoverListeners.get(i).onHoverEnd(this);
        }
    }

    /**
     * Registers an interceptor that sees keyboard input before the editor. Used by overlays such as a
     * completion popup, which needs Up, Down, Enter and Escape for itself.
     */
    public void addInputInterceptor(CodeEditorInputInterceptor interceptor) {
        if (interceptor != null && !inputInterceptors.contains(interceptor, true)) {
            inputInterceptors.add(interceptor);
        }
    }

    public void removeInputInterceptor(CodeEditorInputInterceptor interceptor) {
        inputInterceptors.removeValue(interceptor, true);
    }

    public void clearInputInterceptors() {
        inputInterceptors.clear();
    }

    private boolean dispatchInterceptorKeyDown(int keycode) {
        for (int i = 0; i < inputInterceptors.size; i++) {
            if (inputInterceptors.get(i).onKeyDown(this, keycode)) {
                return true;
            }
        }
        return false;
    }

    private boolean dispatchInterceptorKeyTyped(char character) {
        for (int i = 0; i < inputInterceptors.size; i++) {
            if (inputInterceptors.get(i).onKeyTyped(this, character)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Offers a typed character to the auto-edit strategy. Guarded against re-entrancy: the strategy
     * edits through the public mutators, and a mutator that somehow fed a character back here would
     * otherwise recurse.
     */
    private boolean dispatchAutoEditCharacter(char character) {
        if (autoEditStrategy == null || autoEditInProgress) {
            return false;
        }
        autoEditInProgress = true;
        try {
            return autoEditStrategy.onCharacterTyped(this, character);
        } finally {
            autoEditInProgress = false;
        }
    }

    private boolean dispatchAutoEditBackspace() {
        if (autoEditStrategy == null || autoEditInProgress) {
            return false;
        }
        autoEditInProgress = true;
        try {
            return autoEditStrategy.onBackspace(this);
        } finally {
            autoEditInProgress = false;
        }
    }

    private boolean dispatchAutoEditEnter() {
        if (autoEditStrategy == null || autoEditInProgress) {
            return false;
        }
        autoEditInProgress = true;
        try {
            return autoEditStrategy.onEnter(this);
        } finally {
            autoEditInProgress = false;
        }
    }

    private void dispatchInterceptorAfterKeyTyped(char character) {
        for (int i = 0; i < inputInterceptors.size; i++) {
            inputInterceptors.get(i).afterKeyTyped(this, character);
        }
    }

    /**
     * Registers a listener for caret and selection movement, including moves that change no text.
     */
    public void addCaretListener(CodeEditorCaretListener listener) {
        if (listener != null && !caretListeners.contains(listener, true)) {
            caretListeners.add(listener);
        }
    }

    public void removeCaretListener(CodeEditorCaretListener listener) {
        caretListeners.removeValue(listener, true);
    }

    public void clearCaretListeners() {
        caretListeners.clear();
    }

    /**
     * Fires {@link CodeEditorCaretListener} when the caret, the selection or the document version has
     * changed since the previous frame. Comparing state is cheaper and far more reliable than
     * instrumenting the twenty-odd places that move the caret.
     */
    private void notifyCaretMovedIfNeeded() {
        if (caretListeners.size == 0) {
            lastNotifiedCaretLine = document.getCursorLine();
            lastNotifiedCaretColumn = document.getCursorColumn();
            lastNotifiedDocumentVersion = document.getVersion();
            lastNotifiedSelection = getSelection();
            return;
        }

        int line = document.getCursorLine();
        int column = document.getCursorColumn();
        int version = document.getVersion();
        CodeEditorTextRange selection = getSelection();
        boolean selectionChanged = lastNotifiedSelection == null
            ? selection != null
            : !lastNotifiedSelection.equals(selection);

        if (line == lastNotifiedCaretLine
            && column == lastNotifiedCaretColumn
            && version == lastNotifiedDocumentVersion
            && !selectionChanged) {
            return;
        }

        boolean causedByEdit = version != lastNotifiedDocumentVersion;
        lastNotifiedCaretLine = line;
        lastNotifiedCaretColumn = column;
        lastNotifiedDocumentVersion = version;
        lastNotifiedSelection = selection;

        CodeEditorPosition position = new CodeEditorPosition(line, column, document.toOffset(line, column));
        // Snapshotted for the same reason as the content listeners: a listener may unregister itself
        // from this callback, which a for-each over the live array would turn into a skipped listener.
        caretListenerScratch.clear();
        caretListenerScratch.addAll(caretListeners);
        try {
            for (int i = 0; i < caretListenerScratch.size; i++) {
                caretListenerScratch.get(i).onCaretMoved(this, position, selection, causedByEdit);
            }
        } finally {
            caretListenerScratch.clear();
        }
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
        searchPatternDirty = true;
        clearCurrentSearchMatch();
        invalidateSearch();
    }

    public boolean isSearchCaseSensitive() {
        return searchCaseSensitive;
    }

    public void setSearchCaseSensitive(boolean searchCaseSensitive) {
        if (this.searchCaseSensitive == searchCaseSensitive) {
            return;
        }
        this.searchCaseSensitive = searchCaseSensitive;
        searchPatternDirty = true;
        clearCurrentSearchMatch();
        invalidateSearch();
    }

    /** Whether a match must be delimited by non-word characters on both sides. */
    public boolean isSearchWholeWord() {
        return searchWholeWord;
    }

    /**
     * Restricts matches to whole words. A word character is a letter, a digit or {@code _}, the same
     * definition double-click selection uses. Applies to regex matches too, so {@code \w+} in whole
     * word mode will not report the tail of a longer identifier.
     */
    public void setSearchWholeWord(boolean searchWholeWord) {
        if (this.searchWholeWord == searchWholeWord) {
            return;
        }
        this.searchWholeWord = searchWholeWord;
        clearCurrentSearchMatch();
        invalidateSearch();
    }

    /** Whether {@link #getSearchText()} is treated as a regular expression. */
    public boolean isSearchRegexEnabled() {
        return searchRegexEnabled;
    }

    /**
     * Switches between literal and regular expression matching.
     *
     * <p>Patterns are applied one line at a time, which is what keeps the incremental per-line match
     * cache usable on a large document. Two consequences follow: a pattern cannot match across a line
     * break, and {@code ^} and {@code $} anchor to the start and end of each line rather than the
     * document. {@link Pattern#CASE_INSENSITIVE} is set unless {@link #isSearchCaseSensitive()}.
     *
     * <p>An invalid pattern is not an error: the match count simply drops to zero and
     * {@link #getSearchRegexError()} explains why, so a search field can report the problem while the
     * user is still typing it.
     */
    public void setSearchRegexEnabled(boolean searchRegexEnabled) {
        if (this.searchRegexEnabled == searchRegexEnabled) {
            return;
        }
        this.searchRegexEnabled = searchRegexEnabled;
        searchPatternDirty = true;
        clearCurrentSearchMatch();
        invalidateSearch();
    }

    /**
     * Why the current pattern failed to compile, or null when it is valid, empty, or regex mode is
     * off. Compiles on demand, so it can be polled as the user types without running a search.
     */
    public String getSearchRegexError() {
        if (!searchRegexEnabled) {
            return null;
        }
        ensureSearchPattern();
        return searchRegexError;
    }

    /** Whether the current pattern compiles. Always true outside regex mode, or for empty text. */
    public boolean isSearchPatternValid() {
        if (!searchRegexEnabled || searchText.isEmpty()) {
            return true;
        }
        ensureSearchPattern();
        return searchPattern != null;
    }

    /**
     * The range matching is confined to, or null when the whole document is searched.
     *
     * <p>This is a fixed range, not a live view of the selection: it does not follow later edits. A
     * Replace All inside it will shift text, so re-establish it afterwards if you need it again.
     */
    public CodeEditorTextRange getSearchRange() {
        return searchRange;
    }

    /** Confines matching to {@code range}, or to the whole document when null. */
    public void setSearchRange(CodeEditorTextRange range) {
        if (range != null && range.isEmpty()) {
            range = null;
        }
        if (searchRange == null ? range == null : searchRange.equals(range)) {
            return;
        }
        searchRange = range;
        clearCurrentSearchMatch();
        invalidateSearch();
    }

    /**
     * Confines matching to the current selection, or clears the restriction when nothing is selected.
     *
     * @return whether a range was set
     */
    public boolean setSearchRangeToSelection() {
        CodeEditorTextRange selection = getSelection();
        setSearchRange(selection);
        return searchRange != null;
    }

    /** Lifts any range restriction, so the whole document is searched again. */
    public void clearSearchRange() {
        setSearchRange(null);
    }

    /** Number of matches in the document, or inside {@link #getSearchRange()} when one is set. */
    public int getSearchMatchCount() {
        // Brings the match cache up to date, so a count read right after changing an option is not a
        // frame behind. Cheap when nothing changed.
        ensureLayout();
        return searchMatchCount;
    }

    /** 1-based index of the active match, or 0 when none is active. */
    public int getCurrentSearchMatchOrdinal() {
        ensureLayout();
        int index = findCurrentSearchMatchIndex();
        return index >= 0 ? index + 1 : 0;
    }

    public boolean hasCurrentSearchMatch() {
        ensureLayout();
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

        String safeReplacement = expandReplacement(sanitizeInputText(replacement, true), target);
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

        String safeReplacement = sanitizeInputText(replacement, true);
        Array<SearchMatchRef> matchesToReplace = new Array<>(flatSearchMatches);
        expandCollapsedRegionsForSearchMatches(matchesToReplace);
        ensureLayout();
        matchesToReplace = new Array<>(flatSearchMatches);
        if (matchesToReplace.size == 0) {
            clearCurrentSearchMatch();
            return 0;
        }

        // Group references have to be resolved against the untouched document, so they are expanded
        // up front rather than inside the edit loop, which rewrites the text as it walks backwards.
        String[] expanded = null;
        if (needsReplacementExpansion(safeReplacement)) {
            expanded = new String[matchesToReplace.size];
            for (int i = 0; i < matchesToReplace.size; i++) {
                expanded[i] = expandReplacement(safeReplacement, matchesToReplace.get(i));
            }
        }

        clearSelection();
        markPendingContentChange(CodeEditorContentChangeType.REPLACE_ALL);
        document.beginCompoundEdit();
        try {
            for (int i = matchesToReplace.size - 1; i >= 0; i--) {
                replaceSearchMatch(matchesToReplace.get(i),
                    expanded == null ? safeReplacement : expanded[i], false);
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

    public boolean isLineNumbersVisible() {
        return lineNumbersVisible;
    }

    public boolean isClipAreaEnabled() {
        return clipAreaEnabled;
    }

    public void setClipAreaEnabled(boolean clipAreaEnabled) {
        this.clipAreaEnabled = clipAreaEnabled;
    }

    public boolean hasCustomClipArea() {
        return customClipArea;
    }

    /**
     * Sets a custom clip rectangle in actor-local coordinates (origin at the
     * bottom-left of this editor). Enables clipping if it was disabled.
     */
    public void setClipArea(float x, float y, float width, float height) {
        float normalizedWidth = Math.max(0f, width);
        float normalizedHeight = Math.max(0f, height);
        if (customClipArea
            && clipAreaEnabled
            && Float.compare(clipAreaX, x) == 0
            && Float.compare(clipAreaY, y) == 0
            && Float.compare(clipAreaWidth, normalizedWidth) == 0
            && Float.compare(clipAreaHeight, normalizedHeight) == 0) {
            return;
        }
        customClipArea = true;
        clipAreaEnabled = true;
        clipAreaX = x;
        clipAreaY = y;
        clipAreaWidth = normalizedWidth;
        clipAreaHeight = normalizedHeight;
    }

    /** Clears a custom clip rectangle and returns to clipping the full editor bounds. */
    public void clearClipArea() {
        customClipArea = false;
    }

    public void setLineNumbersVisible(boolean lineNumbersVisible) {
        if (settings != null) {
            settings.setLineNumbersVisible(lineNumbersVisible);
            return;
        }
        applyLineNumbersVisible(lineNumbersVisible);
    }

    private void applyLineNumbersVisible(boolean lineNumbersVisible) {
        if (this.lineNumbersVisible == lineNumbersVisible) {
            return;
        }
        this.lineNumbersVisible = lineNumbersVisible;
        invalidateLayout();
        invalidateHierarchy();
    }

    public void setLineNumbersFixed(boolean lineNumbersFixed) {
        if (settings != null) {
            settings.setLineNumbersFixed(lineNumbersFixed);
            return;
        }
        applyLineNumbersFixed(lineNumbersFixed);
    }

    private void applyLineNumbersFixed(boolean lineNumbersFixed) {
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
        if (settings != null) {
            settings.setWrapEnabled(wrapEnabled);
            return;
        }
        applyWrapEnabled(wrapEnabled);
    }

    private void applyWrapEnabled(boolean wrapEnabled) {
        if (this.wrapEnabled == wrapEnabled) {
            return;
        }
        this.wrapEnabled = wrapEnabled;
        invalidateLayout();
        invalidateHierarchy();
    }

    /** Whether wrapped rows align under the start of their line's text. Off by default. */
    public boolean isWrapContinuationIndentEnabled() {
        return wrapContinuationIndentEnabled;
    }

    /**
     * Indents wrapped continuation rows to line up with the start of their line's own text, so a wrapped
     * statement reads as a continuation rather than as a new line at column zero.
     *
     * <p>Has no effect unless {@link #setWrapEnabled(boolean)} is on, but it is remembered either way, so
     * the two can be configured in any order.
     *
     * <p><strong>This changes how many rows an indented line occupies</strong>, because a continuation row
     * is narrower by the indent. Scroll extent, the row a given line starts at, and the mapping from a
     * click to a column all shift — which is why it defaults to off rather than being applied to every
     * existing caller.
     *
     * <p>The indent is capped at half the wrap width, so a deeply indented line still gets usable room
     * instead of wrapping every few characters.
     */
    public void setWrapContinuationIndentEnabled(boolean wrapContinuationIndentEnabled) {
        if (settings != null) {
            settings.setWrapContinuationIndentEnabled(wrapContinuationIndentEnabled);
            return;
        }
        applyWrapContinuationIndentEnabled(wrapContinuationIndentEnabled);
    }

    private void applyWrapContinuationIndentEnabled(boolean enabled) {
        if (this.wrapContinuationIndentEnabled == enabled) {
            return;
        }
        this.wrapContinuationIndentEnabled = enabled;
        invalidateLayout();
        invalidateHierarchy();
    }

    /** Extra continuation-indent columns past the line's own leading whitespace. */
    public int getWrapContinuationIndentColumns() {
        return wrapContinuationIndentColumns;
    }

    /**
     * Adds {@code columns} space widths of indent past the line's own leading whitespace, so a
     * continuation is visibly deeper than the text it continues rather than exactly aligned with it.
     *
     * <p>Negative values are treated as zero. Only meaningful while
     * {@link #setWrapContinuationIndentEnabled(boolean)} is on.
     */
    public void setWrapContinuationIndentColumns(int columns) {
        if (settings != null) {
            settings.setWrapContinuationIndentColumns(columns);
            return;
        }
        applyWrapContinuationIndentColumns(columns);
    }

    private void applyWrapContinuationIndentColumns(int columns) {
        int safe = Math.max(0, columns);
        if (this.wrapContinuationIndentColumns == safe) {
            return;
        }
        this.wrapContinuationIndentColumns = safe;
        // Only the layout depends on this, and only while the indent is on, so an off editor stays cheap.
        if (wrapContinuationIndentEnabled) {
            invalidateLayout();
            invalidateHierarchy();
        }
    }

    public boolean isMagnifierEnabled() {
        return magnifierEnabled;
    }

    public InputFilter getInputFilter() {
        return inputFilter;
    }

    public void setInputFilter(InputFilter inputFilter) {
        this.inputFilter = inputFilter;
    }

    public boolean isPasswordMode() {
        return passwordMode;
    }

    public void setPasswordMode(boolean passwordMode) {
        if (settings != null) {
            settings.setPasswordMode(passwordMode);
            return;
        }
        applyPasswordMode(passwordMode);
    }

    private void applyPasswordMode(boolean passwordMode) {
        if (this.passwordMode == passwordMode) {
            return;
        }
        this.passwordMode = passwordMode;
        invalidateLayout();
        invalidateHierarchy();
    }

    public char getPasswordCharacter() {
        return passwordCharacter;
    }

    public void setPasswordCharacter(char passwordCharacter) {
        if (passwordCharacter == 0 || passwordCharacter == '\n' || passwordCharacter == '\r') {
            throw new IllegalArgumentException("passwordCharacter must be a visible single-line character");
        }
        if (this.passwordCharacter == passwordCharacter) {
            return;
        }
        this.passwordCharacter = passwordCharacter;
        if (passwordMode) {
            invalidateLayout();
            invalidateHierarchy();
        }
    }

    public boolean isTransientCaretHandleEnabled() {
        return transientCaretHandleEnabled;
    }

    public void setTransientCaretHandleEnabled(boolean transientCaretHandleEnabled) {
        if (settings != null) {
            settings.setTransientCaretHandleEnabled(transientCaretHandleEnabled);
            return;
        }
        applyTransientCaretHandleEnabled(transientCaretHandleEnabled);
    }

    private void applyTransientCaretHandleEnabled(boolean transientCaretHandleEnabled) {
        if (this.transientCaretHandleEnabled == transientCaretHandleEnabled) {
            return;
        }
        this.transientCaretHandleEnabled = transientCaretHandleEnabled;
        if (!transientCaretHandleEnabled) {
            hideTransientCaretHandle();
            if (draggingCaretHandle) {
                draggingCaretHandle = false;
                handleDragFixedLine = -1;
                handleDragFixedColumn = -1;
                handleDragPointerOffsetX = 0f;
                handleDragPointerOffsetY = 0f;
            }
        }
    }

    public boolean isOverscrollEnabled() {
        return overscrollEnabled;
    }

    public void setOverscrollEnabled(boolean overscrollEnabled) {
        if (settings != null) {
            settings.setOverscrollEnabled(overscrollEnabled);
            return;
        }
        applyOverscrollEnabled(overscrollEnabled);
    }

    private void applyOverscrollEnabled(boolean overscrollEnabled) {
        this.overscrollEnabled = overscrollEnabled;
        if (!overscrollEnabled) {
            touchScrollVelocityX = 0f;
            touchScrollVelocityY = 0f;
            clampScroll();
        }
    }

    public boolean isScrollbarsVisible() {
        return scrollbarsVisible;
    }

    public void setScrollbarsVisible(boolean scrollbarsVisible) {
        if (settings != null) {
            settings.setScrollbarsVisible(scrollbarsVisible);
            return;
        }
        applyScrollbarsVisible(scrollbarsVisible);
    }

    private void applyScrollbarsVisible(boolean scrollbarsVisible) {
        if (this.scrollbarsVisible == scrollbarsVisible) {
            return;
        }
        this.scrollbarsVisible = scrollbarsVisible;
        draggingScrollbar = false;
        draggingHorizontalScrollbar = false;
        clampScroll();
        invalidateLayout();
        invalidateHierarchy();
    }

    public boolean isZoomEnabled() {
        return zoomEnabled;
    }

    public void setZoomEnabled(boolean zoomEnabled) {
        if (settings != null) {
            settings.setZoomEnabled(zoomEnabled);
            return;
        }
        applyZoomEnabled(zoomEnabled);
    }

    private void applyZoomEnabled(boolean zoomEnabled) {
        this.zoomEnabled = zoomEnabled;
        if (!zoomEnabled) {
            pinchZooming = false;
        }
    }

    public void setMagnifierEnabled(boolean magnifierEnabled) {
        if (settings != null) {
            settings.setMagnifierEnabled(magnifierEnabled);
            return;
        }
        applyMagnifierEnabled(magnifierEnabled);
    }

    private void applyMagnifierEnabled(boolean magnifierEnabled) {
        this.magnifierEnabled = magnifierEnabled;
    }

    public CodeEditorSettings getSettings() {
        return settings;
    }

    public void setSettings(CodeEditorSettings settings) {
        if (settings == null) {
            throw new IllegalArgumentException("settings cannot be null");
        }
        if (this.settings == settings) {
            applySettings(settings);
            return;
        }
        if (this.settings != null) {
            this.settings.removeListener(settingsListener);
        }
        this.settings = settings;
        this.settings.addListener(settingsListener);
        applySettings(settings);
    }

    private void applySettings(CodeEditorSettings settings) {
        if (settings == null) {
            return;
        }
        applyWrapEnabled(settings.isWrapEnabled());
        // Columns before the flag: applying the flag is what triggers the re-layout, so setting the
        // width afterwards would either re-layout a second time or, while off, be skipped entirely.
        applyWrapContinuationIndentColumns(settings.getWrapContinuationIndentColumns());
        applyWrapContinuationIndentEnabled(settings.isWrapContinuationIndentEnabled());
        applyLineNumbersVisible(settings.isLineNumbersVisible());
        applyLineNumbersFixed(settings.isLineNumbersFixed());
        applyScrollbarsVisible(settings.isScrollbarsVisible());
        applyOverscrollEnabled(settings.isOverscrollEnabled());
        applyZoomEnabled(settings.isZoomEnabled());
        applyMagnifierEnabled(settings.isMagnifierEnabled());
        applyTransientCaretHandleEnabled(settings.isTransientCaretHandleEnabled());
        applyPasswordMode(settings.isPasswordMode());
        applyRainbowBracketsEnabled(settings.isRainbowBracketsEnabled());
        applyRainbowGuidesEnabled(settings.isRainbowGuidesEnabled());
        applyInteractionMode(settings.getInteractionMode());
    }

    private void updateFontMetrics() {
        this.lineHeight = baseFontLineHeight * zoomScale + style.rowPadding;
    }

    public boolean isRainbowBracketsEnabled() {
        return rainbowBracketsEnabled;
    }

    public void setRainbowBracketsEnabled(boolean rainbowBracketsEnabled) {
        if (settings != null) {
            settings.setRainbowBracketsEnabled(rainbowBracketsEnabled);
            return;
        }
        applyRainbowBracketsEnabled(rainbowBracketsEnabled);
    }

    private void applyRainbowBracketsEnabled(boolean rainbowBracketsEnabled) {
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
        if (settings != null) {
            settings.setRainbowGuidesEnabled(rainbowGuidesEnabled);
            return;
        }
        applyRainbowGuidesEnabled(rainbowGuidesEnabled);
    }

    private void applyRainbowGuidesEnabled(boolean rainbowGuidesEnabled) {
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

    // ---------------------------------------------------------------------------------------------
    // Query API: caret geometry and coordinate mapping.
    //
    // These exist so an overlay such as a completion popup, a hover tooltip or a parameter hint can
    // position itself against the caret or an arbitrary document position without reaching into the
    // editor's internals.
    // ---------------------------------------------------------------------------------------------

    /** Caret position as line, column and document offset. */
    public CodeEditorPosition getCursorPosition() {
        int line = document.getCursorLine();
        int column = document.getCursorColumn();
        return new CodeEditorPosition(line, column, document.toOffset(line, column));
    }

    /** Caret document character offset. */
    public int getCursorOffset() {
        return document.toOffset(document.getCursorLine(), document.getCursorColumn());
    }

    /** Converts a line/column pair to a document character offset. */
    public int toOffset(int line, int column) {
        return document.toOffset(line, column);
    }

    /** Converts a document character offset to a line/column position. */
    public CodeEditorPosition toPosition(int offset) {
        int line = document.lineAtOffset(offset);
        int column = document.columnAtOffset(offset);
        return new CodeEditorPosition(line, column, document.toOffset(line, column));
    }

    /**
     * X of the caret in this actor's local coordinates, or {@link Float#NaN} when the caret is inside
     * a collapsed fold and has no on-screen position.
     */
    public float getCursorLocalX() {
        CursorPlacement placement = getCursorPlacement();
        return placement == null ? Float.NaN : placement.x;
    }

    /** Y of the caret's row <em>bottom</em> in local coordinates, or NaN when it is not visible. */
    public float getCursorLocalY() {
        CursorPlacement placement = getCursorPlacement();
        return placement == null ? Float.NaN : rowBottom(placement.row);
    }

    /**
     * Local X for any document position, or NaN when that position is hidden by a collapsed fold.
     * Materializes the line's layout, so prefer caret-relative queries in hot paths.
     */
    public float getLocalXAt(int line, int column) {
        CursorPlacement placement = getCursorPlacement(line, column);
        return placement == null ? Float.NaN : placement.x;
    }

    /** Local Y of the row bottom for any document position, or NaN when hidden. */
    public float getLocalYAt(int line, int column) {
        CursorPlacement placement = getCursorPlacement(line, column);
        return placement == null ? Float.NaN : rowBottom(placement.row);
    }

    /**
     * Writes the caret position in stage coordinates into {@code out} and returns it, or returns
     * {@code null} when the caret is not visible or the editor is not on a stage.
     *
     * <p>The point is the caret's baseline-left, i.e. the bottom-left of its row, which is where a
     * popup that should appear <em>below</em> the caret wants to anchor. Add
     * {@link #getLineHeight()} for the top.
     */
    public Vector2 getCursorStagePosition(Vector2 out) {
        return getStagePositionAt(document.getCursorLine(), document.getCursorColumn(), out);
    }

    /** Stage coordinates of any document position, or {@code null} when hidden or not on a stage. */
    public Vector2 getStagePositionAt(int line, int column, Vector2 out) {
        if (out == null || getStage() == null) {
            return null;
        }
        CursorPlacement placement = getCursorPlacement(line, column);
        if (placement == null) {
            return null;
        }
        out.set(placement.x, rowBottom(placement.row));
        localToStageCoordinates(out);
        return out;
    }

    /**
     * Document position under a point in this actor's local coordinates, or {@code null} when the
     * point is outside the text area.
     *
     * @param clampToNearest when true, a point above or below the text maps to the nearest row rather
     *     than returning null
     */
    public CodeEditorPosition getPositionAtLocal(float x, float y, boolean clampToNearest) {
        ensureLayout();
        CodePoint point = getCodePointAt(x, y, clampToNearest);
        if (point == null) {
            return null;
        }
        return new CodeEditorPosition(point.line, point.column, document.toOffset(point.line, point.column));
    }

    /** Document position under a point in stage coordinates, or {@code null} when outside the text. */
    public CodeEditorPosition getPositionAtStage(float stageX, float stageY, boolean clampToNearest) {
        scratchVector.set(stageX, stageY);
        stageToLocalCoordinates(scratchVector);
        return getPositionAtLocal(scratchVector.x, scratchVector.y, clampToNearest);
    }

    /** Height of one text row in pixels, at the current zoom. */
    public float getLineHeight() {
        return lineHeight;
    }

    /** First document line that can currently be on screen. */
    public int getFirstVisibleDocumentLine() {
        ensureLayout();
        return getFirstVisibleLine();
    }

    /** Last document line that can currently be on screen. */
    public int getLastVisibleDocumentLine() {
        ensureLayout();
        return getLastVisibleLine();
    }

    /** Whether a document line is hidden inside a collapsed fold. */
    public boolean isLineHidden(int line) {
        ensureLayout();
        return isHiddenLine(line);
    }

    /**
     * Hidden-line test that respects {@link #anyLineHidden}: the array is not cleared while nothing is
     * collapsed, so it must not be read directly.
     */
    private boolean isHiddenLine(int line) {
        return anyLineHidden && line >= 0 && line < hiddenLines.length && hiddenLines[line];
    }

    // ---------------------------------------------------------------------------------------------
    // Query API: document text, words and tokens.
    // ---------------------------------------------------------------------------------------------

    /** Text of one line, without its terminator. */
    public String getLineText(int line) {
        if (line < 0 || line >= document.getLineCount()) {
            return "";
        }
        return document.getLine(line);
    }

    /** Length of one line in characters. */
    public int getLineLength(int line) {
        if (line < 0 || line >= document.getLineCount()) {
            return 0;
        }
        return document.getLineLength(line);
    }

    /** Total document length in characters, counting the newline between lines. */
    public int getTextLength() {
        return document.getTextLength();
    }

    /** Text between two positions. */
    public String getTextRange(int startLine, int startColumn, int endLine, int endColumn) {
        return document.getTextRange(startLine, startColumn, endLine, endColumn);
    }

    /** Text covered by {@code range}. */
    public String getTextRange(CodeEditorTextRange range) {
        if (range == null) {
            return "";
        }
        return document.getTextRange(range.startLine, range.startColumn, range.endLine, range.endColumn);
    }

    /**
     * The identifier-like word containing or immediately preceding a position, or {@code null} when
     * there is none.
     *
     * <p>This is the range a completion normally replaces: for {@code foo.ba|r} it returns
     * {@code bar}, and for {@code foo.|} it returns {@code null}.
     */
    public CodeEditorTextRange getWordRangeAt(int line, int column) {
        if (line < 0 || line >= document.getLineCount()) {
            return null;
        }
        CharSequence text = document.getLineSequence(line);
        int safeColumn = Math.max(0, Math.min(column, text.length()));
        int start = safeColumn;
        while (start > 0 && isWordChar(text.charAt(start - 1))) {
            start--;
        }
        int end = safeColumn;
        while (end < text.length() && isWordChar(text.charAt(end))) {
            end++;
        }
        if (start == end) {
            return null;
        }
        return new CodeEditorTextRange(line, start, line, end);
    }

    /** {@link #getWordRangeAt(int, int)} at the caret. */
    public CodeEditorTextRange getWordRangeAtCursor() {
        return getWordRangeAt(document.getCursorLine(), document.getCursorColumn());
    }

    /**
     * The word characters immediately before a position, which is the prefix a completion list should
     * filter on. Empty when the preceding character is not a word character.
     */
    public String getWordPrefixAt(int line, int column) {
        if (line < 0 || line >= document.getLineCount()) {
            return "";
        }
        CharSequence text = document.getLineSequence(line);
        int end = Math.max(0, Math.min(column, text.length()));
        int start = end;
        while (start > 0 && isWordChar(text.charAt(start - 1))) {
            start--;
        }
        return start == end ? "" : text.subSequence(start, end).toString();
    }

    /** {@link #getWordPrefixAt(int, int)} at the caret. */
    public String getWordPrefixAtCursor() {
        return getWordPrefixAt(document.getCursorLine(), document.getCursorColumn());
    }

    /** Character at a position, or {@code '\0'} when out of range. */
    public char getCharAt(int line, int column) {
        if (line < 0 || line >= document.getLineCount()) {
            return '\0';
        }
        if (column < 0 || column >= document.getLineLength(line)) {
            return '\0';
        }
        return document.charAt(line, column);
    }

    /**
     * Indent depth of a line as reported by the structure provider.
     *
     * <p>Forces a pending analysis to finish first, so this agrees with {@link #getFoldRegions()} and
     * {@link #getSymbols()} rather than describing an older version of the text.
     */
    public int getIndentLevel(int line) {
        ensureLayout();
        flushStructureAnalysis();
        return safeIndentLevel(indentLevels, line);
    }

    /**
     * Whether a position sits inside a string or comment, as reported by the highlighter's
     * bracket-ignore ranges. Useful for suppressing completion inside literals.
     */
    public boolean isInStringOrComment(int line, int column) {
        ensureLayout();
        return isIgnoredBracketPosition(line, column);
    }

    // ---------------------------------------------------------------------------------------------
    // Query API: selection.
    // ---------------------------------------------------------------------------------------------

    /** Current selection, or {@code null} when nothing is selected. */
    public CodeEditorTextRange getSelection() {
        SelectionRange range = getSelectionRange();
        if (range == null) {
            return null;
        }
        return new CodeEditorTextRange(range.startLine, range.startColumn, range.endLine, range.endColumn);
    }

    /** Selected text, or an empty string when nothing is selected. */
    public String getSelectionText() {
        return getSelectedText();
    }

    /** Selects a range and leaves the caret at its end. */
    public void setSelection(int startLine, int startColumn, int endLine, int endColumn) {
        int safeStartLine = clamp(startLine, 0, document.getLineCount() - 1);
        int safeEndLine = clamp(endLine, 0, document.getLineCount() - 1);
        selectionAnchorLine = safeStartLine;
        selectionAnchorColumn = clamp(startColumn, 0, document.getLineLength(safeStartLine));
        document.moveCursorTo(safeEndLine, endColumn);
        preferredCursorX = -1f;
        ensureCursorVisible();
        refreshBlink();
    }

    /** Clears the selection, keeping the caret where it is. */
    public void clearSelectionRange() {
        clearSelection();
    }

    /** Moves the caret, clearing any selection, and scrolls it into view. */
    public void setCursorPosition(int line, int column) {
        clearSelection();
        document.moveCursorTo(line, column);
        preferredCursorX = -1f;
        expandCollapsedRegionContainingLine(document.getCursorLine());
        ensureCursorVisible();
        refreshBlink();
    }

    /** Moves the caret to a document character offset. */
    public void setCursorOffset(int offset) {
        setCursorPosition(document.lineAtOffset(offset), document.columnAtOffset(offset));
    }

    // ---------------------------------------------------------------------------------------------
    // Query API: scrolling.
    // ---------------------------------------------------------------------------------------------

    /** Scrolls so a line is visible, without moving the caret. */
    public void scrollToLine(int line) {
        ensureLayout();
        int safeLine = clamp(line, 0, document.getLineCount() - 1);
        if (rowMappingIsIdentity || safeLine < visualRowStart.length) {
            float rowTop = visualRowStartOf(safeLine) * lineHeight;
            float rowBottom = rowTop + lineHeight;
            if (rowTop < scrollY) {
                scrollY = rowTop;
            } else if (rowBottom > scrollY + getContentHeight()) {
                scrollY = rowBottom - getContentHeight();
            }
            clampScroll();
        }
    }

    /** Scrolls a line to the middle of the viewport where possible. */
    public void scrollLineToCenter(int line) {
        ensureLayout();
        int safeLine = clamp(line, 0, document.getLineCount() - 1);
        if (rowMappingIsIdentity || safeLine < visualRowStart.length) {
            scrollY = visualRowStartOf(safeLine) * lineHeight - getContentHeight() * 0.5f + lineHeight * 0.5f;
            clampScroll();
        }
    }

    /** Scrolls the caret into view. */
    public void revealCursor() {
        ensureLayout();
        ensureCursorVisible();
    }

    /**
     * Sets the horizontal scroll offset in pixels, clamped to the current content bounds.
     *
     * <p>Use {@link #setScroll(float, float)} when synchronizing both axes so the editor never
     * observes an intermediate coordinate.
     */
    public void setScrollX(float scrollX) {
        ensureLayout();
        this.scrollX = clamp(scrollX, getMinScrollX(), getMaxScrollX());
    }

    /** Sets the vertical scroll offset in pixels, clamped to the current content bounds. */
    public void setScrollY(float scrollY) {
        ensureLayout();
        this.scrollY = clamp(scrollY, getMinScroll(), getMaxScroll());
    }

    /**
     * Sets both scroll offsets in pixels as one update, which is the preferred operation for
     * synchronizing diff panes.
     */
    public void setScroll(float scrollX, float scrollY) {
        ensureLayout();
        this.scrollX = clamp(scrollX, getMinScrollX(), getMaxScrollX());
        this.scrollY = clamp(scrollY, getMinScroll(), getMaxScroll());
    }

    /** Horizontal scroll offset in pixels; {@code 0} is the left edge of the content. */
    public float getScrollX() {
        return scrollX;
    }

    /** Vertical scroll offset in pixels; {@code 0} is the first visual row. */
    public float getScrollY() {
        return scrollY;
    }

    /**
     * Whether a scroll is under way: true from the frame the offset first moves until the first frame it
     * stops.
     *
     * <p>Covers every source, not only touch. A wheel tick, a scrollbar thumb drag, the editor scrolling
     * itself to keep the caret visible, {@link #setScroll(float, float)}, pinch zoom and a linked diff
     * pane all move the same offset and all report here. For the touch gestures specifically, see
     * {@link #isTouchScrollDragging()} and {@link #isFlinging()}.
     *
     * <p>Maintained by {@link #act(float)}. On a widget that is not being acted on — off-stage, or inside
     * a halted stage — it keeps whatever it last saw. The two touch queries read live state and carry no
     * such caveat.
     */
    public boolean isScrolling() {
        return scrollInProgress;
    }

    /**
     * Whether a drag is panning the content right now, as opposed to a wheel, a scrollbar thumb or a
     * programmatic move.
     *
     * <p>"Touch" is the gesture's name in this class, not a claim about the pointer: a mouse drag that
     * started in the gutter pans the same way and sets the same state, because that is the state the
     * fling and the axis lock are built on.
     */
    public boolean isTouchScrollDragging() {
        return draggingTouchScroll;
    }

    /**
     * Whether the content is coasting after a touch release.
     *
     * <p>False while a finger is still down, even though {@link #getTouchScrollVelocityX()} is non-zero
     * then: the velocity is sampled throughout the drag precisely so it is ready the moment the finger
     * lifts.
     */
    public boolean isFlinging() {
        if (draggingTouchScroll || draggingScrollbar) {
            return false;
        }
        return Math.abs(touchScrollVelocityX) >= TOUCH_FLING_MIN_SPEED
            || Math.abs(touchScrollVelocityY) >= TOUCH_FLING_MIN_SPEED;
    }

    /** Velocity the current pan is tracking, in pixels per second; zero when nothing is panning. */
    public float getTouchScrollVelocityX() {
        return touchScrollVelocityX;
    }

    /** Velocity the current pan is tracking, in pixels per second; zero when nothing is panning. */
    public float getTouchScrollVelocityY() {
        return touchScrollVelocityY;
    }

    /** Total height of all visual rows in pixels. */
    public float getContentHeightPixels() {
        ensureLayout();
        return totalVisualRows * lineHeight;
    }

    /** Widest line in pixels. Estimated from the longest line by character count; see the class docs. */
    public float getContentWidthPixels() {
        ensureLayout();
        return maxLineWidth;
    }

    // ---------------------------------------------------------------------------------------------
    // Mutation API for tooling.
    // ---------------------------------------------------------------------------------------------

    /**
     * Replaces a document range with {@code replacement} as one undo step and leaves the caret at the
     * end of the inserted text. This is what a completion popup calls to commit a candidate: pass the
     * word range being completed and the full candidate text.
     *
     * @return false when the editor is read-only or disabled
     */
    public boolean replaceRange(int startLine, int startColumn, int endLine, int endColumn, String replacement) {
        if (disabled || readOnly) {
            return false;
        }
        String safe = sanitizeInputText(replacement, true);
        clearSelection();
        markPendingContentChange(CodeEditorContentChangeType.REPLACE_CURRENT);
        document.beginCompoundEdit();
        try {
            document.deleteRange(startLine, startColumn, endLine, endColumn);
            if (!safe.isEmpty()) {
                // Placed explicitly, because insertText goes to the caret and deleteRange only moves
                // the caret when it actually removed something. Without this an empty range — a plain
                // insertion point, which is what a completion with no typed prefix passes — inserted
                // wherever the caret happened to be instead of where the caller asked.
                document.moveCursorTo(startLine, startColumn);
                document.insertText(safe);
            }
        } finally {
            document.endCompoundEdit();
        }
        onDocumentMutated();
        return true;
    }

    /** {@link #replaceRange(int, int, int, int, String)} over a range object. */
    public boolean replaceRange(CodeEditorTextRange range, String replacement) {
        if (range == null) {
            return false;
        }
        return replaceRange(range.startLine, range.startColumn, range.endLine, range.endColumn, replacement);
    }

    /**
     * Applies a batch of edits as a single undo step. This is the operation behind a rename, a quick
     * fix or a formatter: every position in {@code edits} refers to the document as it is now, so a
     * producer can walk it once and emit edits in any order.
     *
     * <p>All or nothing. The batch is checked first with {@link #canApplyEdits(Array)} and rejected
     * whole if anything is wrong, rather than applying the good half — a half-applied rename is worse
     * than a rejected one. Edits are then applied last-to-first so that the earlier positions stay
     * valid while the document shifts underneath them.
     *
     * <p>The caret is carried along: it keeps its place in the text rather than being left wherever the
     * document-first edit happened to end. A caret inside a replaced range lands at the end of that
     * range's replacement.
     *
     * @return false when the editor is read-only or disabled, when {@code edits} is null or empty, or
     *     when {@link #canApplyEdits(Array)} rejects it
     */
    public boolean applyEdits(Array<CodeEditorTextEdit> edits) {
        return applyEdits(edits, null);
    }

    /**
     * {@link #applyEdits(Array)} with a name for the resulting undo step, so a rename or a quick fix can
     * appear in a menu as what it was. See {@link #getUndoLabel()}.
     */
    public boolean applyEdits(Array<CodeEditorTextEdit> edits, String label) {
        if (disabled || readOnly || !canApplyEdits(edits)) {
            return false;
        }
        Array<CodeEditorTextEdit> ordered = new Array<CodeEditorTextEdit>(edits);
        // Stable, so two edits at the same position keep the caller's order; combined with the
        // descending walk below that inserts them left to right as listed.
        ordered.sort(EDIT_ORDER);

        editBatchCaretLine = document.getCursorLine();
        editBatchCaretColumn = document.getCursorColumn();
        beginCompoundEdit(label);
        try {
            for (int i = ordered.size - 1; i >= 0; i--) {
                CodeEditorTextEdit edit = ordered.get(i);
                if (edit.isNoOp()) {
                    continue;
                }
                // Before the edit is applied, while the caret and the edit are still in the same
                // coordinate space. Walking backwards means every edit still to come starts before
                // this one, so nothing later moves what this call produces.
                trackBatchCaret(edit);
                replaceRange(edit.range, edit.replacement);
            }
            clearSelection();
            document.moveCursorTo(editBatchCaretLine, editBatchCaretColumn);
            // Set last on purpose: each replaceRange above marks REPLACE_CURRENT, and the event is
            // only built when the group closes, so marking earlier would be overwritten.
            markPendingContentChange(CodeEditorContentChangeType.REPLACE_ALL);
        } finally {
            endCompoundEdit();
        }
        return true;
    }

    /**
     * Whether {@link #applyEdits(Array)} would accept this batch: every range in document bounds,
     * start not after end, and no two ranges overlapping.
     *
     * <p>Worth calling separately when a caller wants to report "cannot do that" without attempting
     * the edit — a rename UI checking a provider's output, for instance. Says nothing about read-only
     * or disabled state, which {@link #applyEdits(Array)} also rejects on.
     */
    public boolean canApplyEdits(Array<CodeEditorTextEdit> edits) {
        if (edits == null || edits.size == 0) {
            return false;
        }
        for (int i = 0; i < edits.size; i++) {
            CodeEditorTextEdit edit = edits.get(i);
            if (edit == null || !isRangeInBounds(edit.range)) {
                return false;
            }
        }
        Array<CodeEditorTextEdit> ordered = new Array<CodeEditorTextEdit>(edits);
        ordered.sort(EDIT_ORDER);
        for (int i = 1; i < ordered.size; i++) {
            CodeEditorTextRange previous = ordered.get(i - 1).range;
            CodeEditorTextRange current = ordered.get(i).range;
            if (comparePositions(previous.endLine, previous.endColumn, current.startLine, current.startColumn) > 0) {
                return false;
            }
        }
        return true;
    }

    private boolean isRangeInBounds(CodeEditorTextRange range) {
        if (range == null) {
            return false;
        }
        if (range.startLine < 0 || range.endLine >= document.getLineCount()) {
            return false;
        }
        if (range.startColumn < 0 || range.startColumn > document.getLineLength(range.startLine)) {
            return false;
        }
        if (range.endColumn < 0 || range.endColumn > document.getLineLength(range.endLine)) {
            return false;
        }
        return comparePositions(range.startLine, range.startColumn, range.endLine, range.endColumn) <= 0;
    }

    private static int comparePositions(int leftLine, int leftColumn, int rightLine, int rightColumn) {
        if (leftLine != rightLine) {
            return leftLine < rightLine ? -1 : 1;
        }
        if (leftColumn != rightColumn) {
            return leftColumn < rightColumn ? -1 : 1;
        }
        return 0;
    }

    /**
     * Moves {@link #editBatchCaretLine}/{@link #editBatchCaretColumn} across one pending edit. A caret
     * before the range does not move, one inside it lands at the end of the replacement, and one after
     * it shifts by the range's line and column delta.
     */
    private void trackBatchCaret(CodeEditorTextEdit edit) {
        CodeEditorTextRange range = edit.range;
        int line = editBatchCaretLine;
        int column = editBatchCaretColumn;
        if (comparePositions(line, column, range.startLine, range.startColumn) <= 0) {
            return;
        }
        int newlines = 0;
        int lastNewline = -1;
        for (int i = 0; i < edit.replacement.length(); i++) {
            if (edit.replacement.charAt(i) == '\n') {
                newlines++;
                lastNewline = i;
            }
        }
        int endLine = range.startLine + newlines;
        int endColumn = newlines == 0
            ? range.startColumn + edit.replacement.length()
            : edit.replacement.length() - lastNewline - 1;
        if (comparePositions(line, column, range.endLine, range.endColumn) <= 0) {
            editBatchCaretLine = endLine;
            editBatchCaretColumn = endColumn;
            return;
        }
        // After the range: the caret keeps its distance from the range's end, which moved to
        // endLine/endColumn. Only a caret on the range's own end line has its column changed.
        editBatchCaretLine = line + (endLine - range.endLine);
        editBatchCaretColumn = line == range.endLine ? endColumn + (column - range.endColumn) : column;
    }

    private static final java.util.Comparator<CodeEditorTextEdit> EDIT_ORDER =
        new java.util.Comparator<CodeEditorTextEdit>() {
            @Override
            public int compare(CodeEditorTextEdit left, CodeEditorTextEdit right) {
                return comparePositions(
                    left.range.startLine, left.range.startColumn,
                    right.range.startLine, right.range.startColumn);
            }
        };

    /**
     * Replaces the word at the caret with {@code replacement}, or inserts at the caret when there is
     * no word there. The usual "accept this completion" operation.
     */
    public boolean completeWordAtCursor(String replacement) {
        CodeEditorTextRange word = getWordRangeAtCursor();
        if (word == null) {
            return insertTextAtCursor(replacement);
        }
        // Only the part of the word up to the caret is being completed; keep any suffix after it.
        return replaceRange(word.startLine, word.startColumn, document.getCursorLine(), document.getCursorColumn(), replacement);
    }

    /** Inserts text at the caret, replacing any selection, as one undo step. */
    public boolean insertTextAtCursor(String text) {
        if (disabled || readOnly) {
            return false;
        }
        String safe = sanitizeInputText(text, true);
        if (safe.isEmpty()) {
            return false;
        }
        markPendingContentChange(CodeEditorContentChangeType.INSERT);
        replaceSelectionWith(safe, false);
        return true;
    }

    /**
     * Starts grouping every following mutation into one undo step, until the matching
     * {@link #endCompoundEdit()}. Calls nest; only the outermost pair has an effect.
     *
     * <p>Worth using for any run of programmatic edits, and not only for the undo grouping: the layout,
     * highlight and search bookkeeping is brought up to date once when the group closes rather than
     * after every mutation, which is the difference between linear and quadratic on a large document.
     *
     * <p>Always close it in a {@code finally} block. A group left open keeps the editor's caches from
     * being refreshed, so the view will stop following the text.
     */
    public void beginCompoundEdit() {
        beginCompoundEdit(null);
    }

    /**
     * {@link #beginCompoundEdit()} with a name for the resulting undo step, readable afterwards through
     * {@link #getUndoLabel()} so a menu can read "Undo Rename" instead of "Undo".
     *
     * <p>Only the outermost call's label is kept — the editor's own mutators open unlabelled groups of
     * their own, and an inner one must not clear the name the caller chose.
     */
    public void beginCompoundEdit(String label) {
        if (!document.isCompoundEditInProgress()) {
            compoundEditStartVersion = document.getVersion();
        }
        document.beginCompoundEdit(label);
    }

    /**
     * Closes the group opened by {@link #beginCompoundEdit()} and refreshes the editor once. Safe to
     * call unbalanced: an extra call with no group open does nothing.
     */
    public void endCompoundEdit() {
        if (!document.isCompoundEditInProgress()) {
            return;
        }
        document.endCompoundEdit();
        if (document.isCompoundEditInProgress()) {
            return;
        }
        int startVersion = compoundEditStartVersion;
        compoundEditStartVersion = -1;
        // A group that changed nothing must not fire a content change event. notifyContentChanged()
        // has no such guard of its own, and with no recorded range it would report the whole document.
        if (document.getVersion() != startVersion) {
            onDocumentMutated();
        }
    }

    /** Whether a {@link #beginCompoundEdit()} group is currently open. */
    public boolean isCompoundEditInProgress() {
        return document.isCompoundEditInProgress();
    }

    /** Deletes a document range as one undo step. */
    public boolean deleteRange(int startLine, int startColumn, int endLine, int endColumn) {
        if (disabled || readOnly) {
            return false;
        }
        clearSelection();
        markPendingContentChange(CodeEditorContentChangeType.DELETE);
        document.deleteRange(startLine, startColumn, endLine, endColumn);
        onDocumentMutated();
        return true;
    }

    // ---------------------------------------------------------------------------------------------
    // Diagnostics.
    // ---------------------------------------------------------------------------------------------

    /**
     * Replaces the diagnostic marks. Ranges are drawn as squiggles with a tick in the gutter, and can
     * be queried by position for a hover tooltip.
     *
     * <p>Diagnostics are not tied to the document version: after an edit their line and column
     * numbers refer to the text as it was when they were produced. Whoever produces them should push a
     * fresh set, typically from a {@link CodeEditorContentListener}.
     */
    public void setDiagnostics(Array<CodeDiagnostic> newDiagnostics) {
        diagnostics.clear();
        if (newDiagnostics != null) {
            diagnostics.addAll(newDiagnostics);
        }
    }

    public void clearDiagnostics() {
        diagnostics.clear();
    }

    /** Live view of the current diagnostics; do not mutate. */
    public Array<CodeDiagnostic> getDiagnostics() {
        return diagnostics;
    }

    /** Diagnostics covering a position, appended to {@code out}, which is returned for chaining. */
    public Array<CodeDiagnostic> getDiagnosticsAt(int line, int column, Array<CodeDiagnostic> out) {
        Array<CodeDiagnostic> target = out == null ? new Array<CodeDiagnostic>() : out;
        for (int i = 0; i < diagnostics.size; i++) {
            CodeDiagnostic diagnostic = diagnostics.get(i);
            if (diagnostic.covers(line, column)) {
                target.add(diagnostic);
            }
        }
        return target;
    }

    /** Diagnostics covering a position, in a fresh array. */
    public Array<CodeDiagnostic> getDiagnosticsAt(int line, int column) {
        return getDiagnosticsAt(line, column, null);
    }

    /** The most severe diagnostic covering a position, or null when there is none. */
    public CodeDiagnostic getPrimaryDiagnosticAt(int line, int column) {
        CodeDiagnostic best = null;
        for (int i = 0; i < diagnostics.size; i++) {
            CodeDiagnostic diagnostic = diagnostics.get(i);
            if (!diagnostic.covers(line, column)) {
                continue;
            }
            if (best == null || diagnostic.severity.ordinal() < best.severity.ordinal()) {
                best = diagnostic;
            }
        }
        return best;
    }

    // ---------------------------------------------------------------------------------------------
    // Semantic highlighting.
    // ---------------------------------------------------------------------------------------------

    /**
     * Replaces the semantic tokens, which are drawn on top of the lexical highlighting.
     *
     * <p>The lexical highlighter is a per-line state machine, so it cannot distinguish a local variable
     * from a field from a parameter — that needs scope information a single line does not carry. Push
     * tokens from something that has it and the editor recolours those ranges.
     *
     * <p>This overload assumes the tokens describe the document as it is now. Prefer
     * {@link #setSemanticTokens(Array, int)} whenever the tokens were produced asynchronously: it
     * rejects a batch that arrived after the text moved on, which is otherwise the standard way to get
     * colours a line or two out of place.
     *
     * <p>Tokens on lines an edit touches are dropped as it happens and the rest are shifted, so typing
     * does not smear colour across the file — it leaves a hole on the edited line until the next push.
     * Push a fresh set from a {@link CodeEditorContentListener}, or poll {@link #isSemanticTokensStale()}.
     *
     * @param tokens tokens in any order; invalid ones (inverted, empty, off-document) are skipped
     */
    public void setSemanticTokens(Array<CodeSemanticToken> tokens) {
        setSemanticTokens(tokens, document.getVersion());
    }

    /**
     * Replaces the semantic tokens, rejecting the batch if it was produced against an older document.
     *
     * <p>Call {@link #getDocumentVersion()} before starting the analysis and pass that value back here.
     * A stale batch is dropped whole rather than partially applied: after an insertion its line numbers
     * are wrong from the edit point down, so applying the part above the edit and discarding the rest
     * would mean the file is coloured correctly at the top and silently not at the bottom, which looks
     * like a bug in the producer rather than a stale push.
     *
     * @param version the document version the tokens were computed from
     * @return false if the document has changed since {@code version}, in which case nothing was applied
     */
    public boolean setSemanticTokens(Array<CodeSemanticToken> tokens, int version) {
        if (version != document.getVersion()) {
            return false;
        }
        clearSemanticTokens();
        if (tokens == null || tokens.size == 0) {
            // An empty push still records the version: it means "this document has no semantic tokens",
            // which is a real answer and not the same as never having been analysed.
            semanticTokenVersion = version;
            return true;
        }

        int lineCount = document.getLineCount();
        resizeSemanticTokenLines(lineCount);
        for (int i = 0; i < tokens.size; i++) {
            CodeSemanticToken token = tokens.get(i);
            if (token == null || !token.isValid() || token.line >= lineCount) {
                continue;
            }
            Array<CodeSemanticToken> lineTokens = semanticTokenLines.get(token.line);
            if (lineTokens == null) {
                // Sized for the common case rather than the worst: most lines hold a handful.
                lineTokens = new Array<CodeSemanticToken>(4);
                semanticTokenLines.set(token.line, lineTokens);
            }
            lineTokens.add(token);
            semanticTokenCount++;
        }
        semanticTokenVersion = version;
        semanticTokensShifted = false;
        // Colours changed on lines that may already be materialized, and a layout caches its tokens.
        recolorAllLineLayouts();
        return true;
    }

    /** Drops every semantic token, returning to purely lexical colouring. */
    public void clearSemanticTokens() {
        if (semanticTokenCount == 0 && semanticTokenVersion < 0) {
            return;
        }
        for (int i = 0; i < semanticTokenLines.size; i++) {
            semanticTokenLines.set(i, null);
        }
        semanticTokenCount = 0;
        semanticTokenVersion = -1;
        semanticTokensShifted = false;
        recolorAllLineLayouts();
    }

    /** Whether any semantic token is currently held. */
    public boolean hasSemanticTokens() {
        return semanticTokenCount > 0;
    }

    /** How many semantic tokens are held, after invalid ones were skipped and edited lines dropped. */
    public int getSemanticTokenCount() {
        return semanticTokenCount;
    }

    /** Document version the tokens were produced against, or -1 if none have been pushed. */
    public int getSemanticTokenVersion() {
        return semanticTokenVersion;
    }

    /**
     * Whether the tokens no longer describe the document exactly.
     *
     * <p>True once an edit has moved or dropped any of them. What remains is still positioned
     * correctly — the point of the shifting is that unedited lines keep their colours — so this is a
     * cue to re-analyse, not a reason to stop drawing.
     */
    public boolean isSemanticTokensStale() {
        return semanticTokenVersion >= 0
            && (semanticTokensShifted || semanticTokenVersion != document.getVersion());
    }

    /**
     * The tokens on one line, or null when it has none.
     *
     * <p>Live view; do not mutate. Order is the push order, not column order.
     *
     * <p>The {@code line} you passed is the authoritative one. {@link CodeSemanticToken#line} on the
     * returned tokens can be out of date after an edit moved them — see that field for why it is not
     * rewritten.
     */
    public Array<CodeSemanticToken> getSemanticTokensAtLine(int line) {
        if (line < 0 || line >= semanticTokenLines.size) {
            return null;
        }
        return semanticTokenLines.get(line);
    }

    /** The token covering a position, highest priority being the last pushed, or null. */
    public CodeSemanticToken getSemanticTokenAt(int line, int column) {
        Array<CodeSemanticToken> lineTokens = getSemanticTokensAtLine(line);
        if (lineTokens == null) {
            return null;
        }
        // Backwards: later pushes win on overlap, matching how the spans are layered when drawn.
        for (int i = lineTokens.size - 1; i >= 0; i--) {
            CodeSemanticToken token = lineTokens.get(i);
            if (token.covers(column)) {
                return token;
            }
        }
        return null;
    }

    /** Whether semantic tokens are drawn. Tokens are retained either way. */
    public boolean isSemanticHighlightEnabled() {
        return semanticHighlightEnabled;
    }

    /**
     * Turns semantic colouring on or off without dropping the tokens, so a caller can offer the
     * comparison as a setting without re-running the analysis.
     */
    public void setSemanticHighlightEnabled(boolean enabled) {
        if (semanticHighlightEnabled == enabled) {
            return;
        }
        semanticHighlightEnabled = enabled;
        if (semanticTokenCount > 0) {
            recolorAllLineLayouts();
        }
    }

    /**
     * Colour a token is drawn in: its own {@code color} if set, else the style's colour for its type,
     * else null, which means the lexical colour shows through unchanged.
     */
    public Color getSemanticTokenColor(CodeSemanticToken token) {
        if (token == null) {
            return null;
        }
        if (token.color != null) {
            return token.color;
        }
        return style.getSemanticTokenColor(token.type);
    }

    /** Colour used to draw a severity, from the style. */
    public Color getDiagnosticColor(CodeDiagnosticSeverity severity) {
        if (severity == null) {
            return style.diagnosticErrorColor;
        }
        switch (severity) {
            case WARNING:
                return style.diagnosticWarningColor;
            case INFORMATION:
                return style.diagnosticInformationColor;
            case HINT:
                return style.diagnosticHintColor;
            case ERROR:
            default:
                return style.diagnosticErrorColor;
        }
    }

    /**
     * Tab and Shift-Tab.
     *
     * <p>With a selection spanning more than one line, or with Shift held, this indents or dedents whole
     * lines. Previously Tab with a multi-line selection replaced the selection with spaces, which is not
     * what any editor does.
     */
    private void handleIndent() {
        SelectionRange selection = getSelectionRange();
        boolean multiLineSelection = selection != null && selection.startLine != selection.endLine;
        if (multiLineSelection) {
            shiftLinesIndent(selection.startLine, selection.endLine, true);
            return;
        }
        String tabInsertion = sanitizeInputText(document.getIndentStrategy().oneIndent(), false);
        if (tabInsertion.isEmpty()) {
            return;
        }
        expandCollapsedRegionsForEdit(EditIntent.TAB);
        markPendingContentChange(CodeEditorContentChangeType.INSERT);
        document.beginCompoundEdit();
        try {
            deleteSelectionIfPresent();
            document.insertText(tabInsertion);
        } finally {
            document.endCompoundEdit();
        }
        onDocumentMutated();
    }

    private void handleDedent() {
        SelectionRange selection = getSelectionRange();
        int fromLine = selection != null ? selection.startLine : document.getCursorLine();
        int toLine = selection != null ? selection.endLine : document.getCursorLine();
        shiftLinesIndent(fromLine, toLine, false);
    }

    /**
     * Indents or dedents a line range by one level as a single undo step, keeping any selection over the
     * same lines so repeated Tab presses keep working.
     *
     * @return true when any line changed
     */
    public boolean shiftLinesIndent(int fromLine, int toLine, boolean increase) {
        if (disabled || readOnly) {
            return false;
        }
        SelectionRange selection = getSelectionRange();
        markPendingContentChange(CodeEditorContentChangeType.INSERT);
        if (!document.shiftLinesIndent(fromLine, toLine, increase)) {
            return false;
        }
        if (selection != null) {
            // Re-select whole lines: the columns have moved, and selecting the range is what lets the
            // user press Tab repeatedly.
            int startLine = clamp(selection.startLine, 0, document.getLineCount() - 1);
            int endLine = clamp(selection.endLine, 0, document.getLineCount() - 1);
            selectionAnchorLine = startLine;
            selectionAnchorColumn = 0;
            document.moveCursorTo(endLine, document.getLineLength(endLine));
        }
        onDocumentMutated();
        return true;
    }

    // ---------------------------------------------------------------------------------------------
    // Folding.
    // ---------------------------------------------------------------------------------------------

    /**
     * Root symbols of the last structure analysis, in document order.
     *
     * <p>Forces a pending analysis first, so the result describes the current text. The returned array
     * is a shallow copy: the {@link CodeSymbol} instances and their children are the editor's. Do not
     * mutate them. Cheap to poll when you only need the count: {@link #getSymbolCount()}.
     */
    public Array<CodeSymbol> getSymbols() {
        ensureLayout();
        flushSymbolAnalysis();
        return new Array<CodeSymbol>(symbols);
    }

    /**
     * How many root symbols the last analysis produced.
     *
     * <p>Cheap: no allocation and no flush, so it is safe to poll from a status display. The flip side is
     * that this can disagree with {@link #getSymbols()}, which forces a pending analysis: before the first
     * layout it reports 0, and after an edit it reports the previous text's count until the deferred symbol
     * pass runs. {@link #isSymbolAnalysisPending()} tells you when that is the case.
     */
    public int getSymbolCount() {
        return symbols.size;
    }

    /**
     * Whether symbols are waiting to be re-extracted.
     *
     * <p>Symbols run on a longer timer than fold regions, so after an edit the tree describes slightly
     * older text while folding and indent guides are already current. Any of the symbol query methods
     * forces it; this is for a status display that would rather not.
     */
    public boolean isSymbolAnalysisPending() {
        return symbolAnalysisPending;
    }

    /** Re-extracts symbols now instead of waiting for the timer. */
    public void refreshSymbolsNow() {
        ensureLayout();
        flushSymbolAnalysis();
    }

    /**
     * The innermost symbol whose full range contains {@code line}, or null. A breadcrumb is
     * {@link #getSymbolPath(int)}; this is the last entry of that path.
     */
    public CodeSymbol getSymbolAt(int line) {
        Array<CodeSymbol> path = getSymbolPath(line);
        return path.size == 0 ? null : path.peek();
    }

    /**
     * The chain of symbols from a root down to the innermost one containing {@code line}, or an empty
     * array when none does. Each entry contains the next. Useful as a breadcrumb.
     */
    public Array<CodeSymbol> getSymbolPath(int line) {
        ensureLayout();
        flushSymbolAnalysis();
        Array<CodeSymbol> path = new Array<CodeSymbol>(4);
        fillSymbolPath(symbols, line, path);
        return path;
    }

    /**
     * Every symbol whose name contains {@code query}, case-insensitive, in document order. An empty
     * or null query returns every symbol. Recurses into children, so a go-to-symbol dialog can show a
     * flat list.
     */
    public Array<CodeSymbol> findSymbols(String query) {
        ensureLayout();
        flushSymbolAnalysis();
        Array<CodeSymbol> matches = new Array<CodeSymbol>();
        String needle = query == null ? "" : query.toLowerCase(java.util.Locale.ROOT);
        collectMatchingSymbols(symbols, needle, matches);
        return matches;
    }

    /**
     * Moves the caret to the symbol's name and reveals it.
     *
     * <p>Returns false when the editor is disabled, or when the symbol no longer fits the document. A
     * {@link CodeSymbol} is a snapshot: hold one across an edit that deletes its lines and it is stale.
     * Rather than clamping such a symbol to the last line and reporting success, this rejects it, so a
     * caller can tell the difference between "went there" and "that is gone now".
     */
    public boolean goToSymbol(CodeSymbol symbol) {
        if (symbol == null || disabled) {
            return false;
        }
        ensureLayout();
        // The fold regions consulted below come from the structure pass, so it has to be current. Symbols
        // too: this is handed a snapshot, and deciding it is stale requires an up-to-date tree to check it
        // against.
        flushSymbolAnalysis();
        int line = symbol.selectionStartLine;
        if (line < 0 || line >= document.getLineCount()) {
            return false;
        }
        int column = clamp(symbol.selectionStartColumn, 0, document.getLineLength(line));
        // setCursorPosition already expands any fold containing the line and reveals the caret.
        setCursorPosition(line, column);
        return true;
    }

    private static boolean fillSymbolPath(Array<CodeSymbol> nodes, int line, Array<CodeSymbol> path) {
        for (int i = 0; i < nodes.size; i++) {
            CodeSymbol node = nodes.get(i);
            if (!node.containsLine(line)) {
                continue;
            }
            path.add(node);
            fillSymbolPath(node.children, line, path);
            return true;
        }
        return false;
    }

    private static void collectMatchingSymbols(Array<CodeSymbol> nodes, String needle, Array<CodeSymbol> out) {
        for (int i = 0; i < nodes.size; i++) {
            CodeSymbol node = nodes.get(i);
            if (needle.isEmpty() || node.name.toLowerCase(java.util.Locale.ROOT).indexOf(needle) >= 0) {
                out.add(node);
            }
            collectMatchingSymbols(node.children, needle, out);
        }
    }

    public Array<CodeFoldRegionInfo> getFoldRegions() {
        ensureLayout();
        flushStructureAnalysis();
        Array<CodeFoldRegionInfo> result = new Array<>(foldRegions.size);
        for (int i = 0; i < foldRegions.size; i++) {
            FoldRegion region = foldRegions.get(i);
            result.add(new CodeFoldRegionInfo(region.startLine, region.endLine, region.depth, region.collapsed));
        }
        return result;
    }

    /**
     * How many foldable regions the last structure analysis found.
     *
     * <p>Cheap: no allocation and no flush, so it is safe to poll from a status display.
     * {@link #getFoldRegions()} allocates and forces a pending analysis, so do not call that per frame.
     */
    public int getFoldRegionCount() {
        return foldRegions.size;
    }

    /** How many regions are currently collapsed. Cheap, like {@link #getFoldRegionCount()}. */
    public int getCollapsedFoldCount() {
        return collapsedLines.size;
    }

    /** The region starting on a line, or the innermost one containing it, or null. */
    public CodeFoldRegionInfo getFoldRegionAt(int line) {
        ensureLayout();
        flushStructureAnalysis();
        FoldRegion region = findRelevantRegion(line);
        if (region == null) {
            return null;
        }
        return new CodeFoldRegionInfo(region.startLine, region.endLine, region.depth, region.collapsed);
    }

    /** Whether a foldable region starts on this line. */
    public boolean isFoldable(int line) {
        ensureLayout();
        flushStructureAnalysis();
        return foldRegionsByStart.get(line) != null;
    }

    /** Whether the region starting on this line is collapsed. */
    public boolean isFoldCollapsed(int line) {
        ensureLayout();
        FoldRegion region = foldRegionsByStart.get(line);
        return region != null && region.collapsed;
    }

    /**
     * Collapses or expands the region starting on {@code line}.
     *
     * @return true when a region there changed state
     */
    public boolean setFoldCollapsed(int line, boolean collapsed) {
        ensureLayout();
        flushStructureAnalysis();
        FoldRegion region = foldRegionsByStart.get(line);
        if (region == null || region.collapsed == collapsed) {
            return false;
        }
        toggleFold(region);
        return true;
    }

    /** Toggles the region starting on {@code line}, or the innermost one containing it. */
    public boolean toggleFoldAt(int line) {
        ensureLayout();
        flushStructureAnalysis();
        FoldRegion region = findRelevantRegion(line);
        if (region == null) {
            return false;
        }
        toggleFold(region);
        return true;
    }

    /**
     * Collapses every region, optionally only those at or below a nesting depth.
     *
     * @param maxDepth collapse regions with {@code depth <= maxDepth}; pass
     *     {@link Integer#MAX_VALUE} for all. Depth 0 is the outermost level, so 0 collapses only
     *     top-level blocks.
     * @return number of regions collapsed
     */
    public int collapseAllFolds(int maxDepth) {
        return applyFoldStateToAll(true, maxDepth);
    }

    /** Collapses every region at any depth. */
    public int collapseAllFolds() {
        return applyFoldStateToAll(true, Integer.MAX_VALUE);
    }

    /** Expands every region. */
    public int expandAllFolds() {
        return applyFoldStateToAll(false, Integer.MAX_VALUE);
    }

    private int applyFoldStateToAll(boolean collapsed, int maxDepth) {
        ensureLayout();
        flushStructureAnalysis();
        int changed = 0;
        float previousScroll = scrollY;
        for (int i = 0; i < foldRegions.size; i++) {
            FoldRegion region = foldRegions.get(i);
            if (region.depth > maxDepth) {
                continue;
            }
            if (isCollapsedStartLine(region.startLine) == collapsed) {
                continue;
            }
            setCollapsed(region.startLine, collapsed);
            changed++;
        }
        if (changed == 0) {
            return 0;
        }
        invalidateRowMapping();
        ensureLayout();
        scrollY = Math.max(getMinScroll(), Math.min(previousScroll, getMaxScroll()));
        refreshBlink();
        return changed;
    }

    /**
     * Start lines of the currently collapsed regions, for persisting fold state.
     *
     * <p>Pass the result back to {@link #setFoldState(IntArray)} after reloading the same text. Line
     * numbers, so they only make sense against text that has not changed shape since.
     */
    public IntArray getFoldState() {
        ensureLayout();
        IntArray state = new IntArray(collapsedLines.size);
        for (int i = 0; i < collapsedLines.size; i++) {
            state.add(collapsedLines.get(i));
        }
        return state;
    }

    /** Restores fold state captured by {@link #getFoldState()}. Unknown lines are ignored. */
    public void setFoldState(IntArray startLines) {
        ensureLayout();
        flushStructureAnalysis();
        collapsedLines.clear();
        if (startLines != null) {
            for (int i = 0; i < startLines.size; i++) {
                int line = startLines.get(i);
                if (foldRegionsByStart.get(line) != null) {
                    setCollapsed(line, true);
                }
            }
        }
        invalidateRowMapping();
        ensureLayout();
        clampScroll();
        refreshBlink();
    }

    /** Expands whatever fold hides {@code line}, so it becomes visible. Returns true if anything changed. */
    public boolean revealLine(int line) {
        ensureLayout();
        return expandCollapsedRegionContainingLine(line);
    }

    /** Indent strategy: tabs or spaces, width, and the smart-indent rules. */
    public CodeIndentStrategy getIndentStrategy() {
        return document.getIndentStrategy();
    }

    /**
     * Replaces the indent strategy. Changes the rendered tab width too, so the layout is rebuilt.
     */
    public void setIndentStrategy(CodeIndentStrategy indentStrategy) {
        document.setIndentStrategy(indentStrategy);
        updateFontMetrics();
        glyphWidthCache.clear();
        glyphAdvanceCache.clear();
        invalidateLayout();
        invalidateHierarchy();
    }

    /**
     * The auto-edit strategy, or null when there is none. Null is the default, so typing behaves the
     * same as it always has until one is installed.
     */
    public CodeAutoEditStrategy getAutoEditStrategy() {
        return autoEditStrategy;
    }

    /**
     * Installs an auto-edit strategy, which gets first refusal on typed characters, Backspace and
     * Enter. {@link CodeBracketAutoEditStrategy} is the built-in one:
     *
     * <pre>{@code
     * editor.setAutoEditStrategy(new CodeBracketAutoEditStrategy());
     * }</pre>
     *
     * @param autoEditStrategy the strategy, or null to go back to plain insertion
     */
    public void setAutoEditStrategy(CodeAutoEditStrategy autoEditStrategy) {
        this.autoEditStrategy = autoEditStrategy;
    }

    /**
     * The keymap the editor uses for {@code keyDown}. Never null: a freshly constructed editor has
     * {@link CodeKeymap#editorDefaults()}. Replace it, or mutate the returned map in place:
     *
     * <pre>{@code
     * editor.getKeymap().unbindAction(CodeEditorAction.TOGGLE_FOLD)
     *     .bind(Input.Keys.F3, CodeEditorAction.TOGGLE_FOLD);
     * }</pre>
     */
    public CodeKeymap getKeymap() {
        return keymap;
    }

    /**
     * Replaces the keymap. Passing null restores {@link CodeKeymap#editorDefaults()}. The completion
     * popup has its own map, so this does not rebind Up/Down while a list is open.
     */
    public void setKeymap(CodeKeymap keymap) {
        this.keymap = keymap != null ? keymap : CodeKeymap.editorDefaults();
    }

    /**
     * Runs a named action as if its chord had been pressed. Shift currently held extends a movement
     * into a selection, matching the keyboard. Returns whether the action was recognised; a recognised
     * action that is a no-op (undo at the bottom of the stack) still returns true.
     */
    public boolean performAction(CodeEditorAction action) {
        if (action == null || disabled) {
            return false;
        }
        // No originating keycode, so key-repeat is not armed: one call is one edit.
        return performAction(action, keymap.shiftPressed(), -1);
    }

    /**
     * @param keycode the physical key that triggered this, or -1 when invoked programmatically. Only
     *     used to arm key-repeat for the delete actions, so a rebound Backspace still repeats.
     */
    private boolean performAction(CodeEditorAction action, boolean shiftHeld, int keycode) {
        switch (action) {
            case MOVE_LEFT:
                return performMovement(shiftHeld, false, new Runnable() {
                    @Override public void run() { document.moveCursorLeft(); }
                });
            case MOVE_RIGHT:
                return performMovement(shiftHeld, false, new Runnable() {
                    @Override public void run() { document.moveCursorRight(); }
                });
            case MOVE_UP:
                return performMovement(shiftHeld, true, new Runnable() {
                    @Override public void run() { moveCursorByVisualRows(-1); }
                });
            case MOVE_DOWN:
                return performMovement(shiftHeld, true, new Runnable() {
                    @Override public void run() { moveCursorByVisualRows(1); }
                });
            case MOVE_PAGE_UP:
                return performMovement(shiftHeld, true, new Runnable() {
                    @Override public void run() {
                        moveCursorByVisualRows(-Math.max(1, (int) (getContentHeight() / lineHeight) + 1));
                    }
                });
            case MOVE_PAGE_DOWN:
                return performMovement(shiftHeld, true, new Runnable() {
                    @Override public void run() {
                        moveCursorByVisualRows(Math.max(1, (int) (getContentHeight() / lineHeight) + 1));
                    }
                });
            case MOVE_LINE_START:
                return performMovement(shiftHeld, false, new Runnable() {
                    @Override public void run() { document.moveCursorHome(); }
                });
            case MOVE_LINE_END:
                return performMovement(shiftHeld, false, new Runnable() {
                    @Override public void run() { document.moveCursorEnd(); }
                });
            case BACKSPACE:
                return performBackspace(keycode);
            case DELETE:
                return performForwardDelete(keycode);
            case NEW_LINE:
                return performNewLine();
            case INDENT:
                if (readOnly) {
                    return true;
                }
                handleIndent();
                return true;
            case DEDENT:
                if (readOnly) {
                    return true;
                }
                handleDedent();
                return true;
            case TOGGLE_FOLD:
                toggleFold(findRelevantRegion(document.getCursorLine()));
                return true;
            case UNDO:
                if (!readOnly) {
                    undo();
                }
                return true;
            case REDO:
                if (!readOnly) {
                    redo();
                }
                return true;
            case SELECT_ALL:
                selectAllText();
                return true;
            case COPY:
                if (getSelectionRange() != null) {
                    copySelection();
                } else {
                    writeToClipboard(document.getLine(document.getCursorLine()));
                }
                return true;
            case CUT:
                if (!readOnly && getSelectionRange() != null) {
                    cutSelection();
                }
                return true;
            case PASTE:
                if (!readOnly) {
                    pasteClipboard();
                }
                return true;
            default:
                // Completion actions are owned by CodeCompletionController, not the editor.
                return false;
        }
    }

    /**
     * @param keepsPreferredColumn true for the vertical moves, which walk rows and want to hold the
     *     screen column they started from. <strong>Resetting here for those was a real bug:</strong>
     *     {@link #moveCursorByVisualRows(int)} stores the preferred x and this method cleared it one line
     *     later, so every Up/Down re-derived the x from the caret's current position. Walking down
     *     through a short line therefore lost the original column instead of restoring it below, and in
     *     wrap mode the caret could stick at a segment boundary.
     */
    private boolean performMovement(boolean shiftHeld, boolean keepsPreferredColumn, Runnable move) {
        if (shiftHeld) {
            beginSelectionIfNeeded(true);
        }
        move.run();
        if (!shiftHeld) {
            clearSelection();
        }
        if (!keepsPreferredColumn) {
            resetPreferredColumn();
        }
        ensureCursorVisible();
        refreshBlink();
        return true;
    }

    private boolean performBackspace(int keycode) {
        return performDeleteWithRepeat(CodeEditorAction.BACKSPACE, keycode);
    }

    private boolean performForwardDelete(int keycode) {
        return performDeleteWithRepeat(CodeEditorAction.DELETE, keycode);
    }

    /**
     * Runs a delete action once, arming key-repeat when it came from a real key press. Repeat is keyed
     * by the pressed keycode so {@code keyUp} can cancel it, and carries the action so a key rebound to
     * Backspace repeats as Backspace.
     */
    private boolean performDeleteWithRepeat(CodeEditorAction action, int keycode) {
        if (readOnly) {
            return true;
        }
        if (keycode >= 0) {
            startDeleteKeyRepeat(keycode, action);
        }
        if (!performDeleteAction(action) && keycode >= 0) {
            stopDeleteKeyRepeat(keycode);
        }
        return true;
    }

    private boolean performNewLine() {
        if (readOnly) {
            return true;
        }
        if (!acceptsInputCharacter('\n')) {
            return true;
        }
        expandCollapsedRegionsForEdit(EditIntent.ENTER);
        if (dispatchAutoEditEnter()) {
            return true;
        }
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
    }

    // ---------------------------------------------------------------------------------------------
    // Line marks: breakpoints, bookmarks, version-control bars.
    // ---------------------------------------------------------------------------------------------

    /**
     * Replaces the line marks. When several marks share a line the highest
     * {@link CodeLineMark#priority} is drawn, so a breakpoint can outrank a change bar.
     *
     * <p>Like diagnostics, marks are positioned by line number and do not follow edits by themselves;
     * re-push them from a {@link CodeEditorContentListener} if they must track moving text.
     */
    public void setLineMarks(Array<CodeLineMark> marks) {
        lineMarks.clear();
        if (marks != null) {
            lineMarks.addAll(marks);
        }
        rebuildLineMarkIndex();
    }

    /** Adds one mark. */
    public void addLineMark(CodeLineMark mark) {
        if (mark == null) {
            return;
        }
        lineMarks.add(mark);
        rebuildLineMarkIndex();
    }

    /**
     * Removes every mark on a line.
     *
     * @return true when something was removed
     */
    public boolean removeLineMarksAt(int line) {
        boolean removed = false;
        for (int i = lineMarks.size - 1; i >= 0; i--) {
            if (lineMarks.get(i).line == line) {
                lineMarks.removeIndex(i);
                removed = true;
            }
        }
        if (removed) {
            rebuildLineMarkIndex();
        }
        return removed;
    }

    public void clearLineMarks() {
        if (lineMarks.size == 0) {
            return;
        }
        lineMarks.clear();
        rebuildLineMarkIndex();
    }

    /** Live view of the marks; do not mutate. */
    public Array<CodeLineMark> getLineMarks() {
        return lineMarks;
    }

    /** The mark drawn on a line — the highest priority one — or null. */
    public CodeLineMark getLineMarkAt(int line) {
        return lineMarksByLine.get(line);
    }

    public boolean hasLineMarkAt(int line) {
        return lineMarksByLine.get(line) != null;
    }

    /**
     * Rebuilds the per-line index and notes whether any mark needs an icon column, which changes the
     * gutter width. Done once per mutation rather than per frame.
     */
    private void rebuildLineMarkIndex() {
        lineMarksByLine.clear();
        anyLineMarkHasIcon = false;
        for (int i = 0; i < lineMarks.size; i++) {
            CodeLineMark mark = lineMarks.get(i);
            if (mark == null || mark.line < 0) {
                continue;
            }
            anyLineMarkHasIcon |= mark.icon != null;
            CodeLineMark current = lineMarksByLine.get(mark.line);
            if (current == null || mark.priority >= current.priority) {
                lineMarksByLine.put(mark.line, mark);
            }
        }
        // The gutter width depends on whether an icon column is needed.
        invalidateHierarchy();
    }

    /**
     * Runs {@code edits} so they undo and redo as a single step.
     *
     * <p>The editor's layout, highlight and search bookkeeping is also brought up to date once when
     * {@code edits} returns rather than after every mutation inside it, so this is the right wrapper
     * for a long run of programmatic edits and not only for the undo grouping. One consequence:
     * content change listeners are notified once for the whole group.
     */
    public void runAsSingleUndoStep(Runnable edits) {
        runAsSingleUndoStep(null, edits);
    }

    /** {@link #runAsSingleUndoStep(Runnable)} with a name for the step; see {@link #getUndoLabel()}. */
    public void runAsSingleUndoStep(String label, Runnable edits) {
        if (edits == null) {
            return;
        }
        beginCompoundEdit(label);
        try {
            edits.run();
        } finally {
            endCompoundEdit();
        }
    }

    @Override
    public void layout() {
        ensureLayout();
    }

    @Override
    public void act(float delta) {
        flushDeferredMutationProcessing();
        updateStructureAnalysisDebounce(delta);
        updateSymbolAnalysisDebounce(delta);
        updateWrapRemeasureDebounce(delta);
        updateHighlightStateBudget();
        notifyCaretMovedIfNeeded();
        updateHover(delta);
        super.act(delta);
        boolean focused = isFocused();
        if (hadInputFocus && !focused) {
            hideTouchHandlesImmediately();
        }
        hadInputFocus = focused;
        syncSelectionHandleOverlay();
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
        if (draggingSelection || draggingStartHandle || draggingEndHandle || draggingCaretHandle || draggingSelectedText) {
            applySelectionAutoScroll(delta);
        }
        if (!draggingTouchScroll && !draggingScrollbar
            && (Math.abs(touchScrollVelocityX) > 0f || Math.abs(touchScrollVelocityY) > 0f)) {
            applyTouchFling(delta);
        }
        if (!draggingTouchScroll && !draggingScrollbar) {
            applyTouchBounce(delta);
        }
        // Last, so the offset compared is the one this frame settles on after the fling and the bounce.
        notifyScrollListenersIfNeeded();
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

            // Background unclipped; content scissored with expanded screen-space scissors.
            boolean clipped = false;
            if (clipAreaEnabled) {
                batch.flush();
                clipped = beginEditorClip();
            }
            try {
                drawRows(batch);
                if (draggingSelectedText) {
                    drawDraggedSelectionDropCaret(batch);
                } else {
                    drawCaret(batch);
                }
            } finally {
                if (clipped) {
                    batch.flush();
                    endEditorClip();
                }
            }
            if (getStage() == null) {
                drawSelectionHandles(batch);
            }
        } finally {
            style.font.getData().setScale(originalScaleX, originalScaleY);
        }
    }

    @Override
    protected void setStage(Stage stage) {
        Stage previousStage = getStage();
        if (previousStage == stage) {
            super.setStage(stage);
            return;
        }
        detachSelectionHandleOverlay(previousStage);
        super.setStage(stage);
        attachSelectionHandleOverlay(stage);
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

    /**
     * Throws away every cached per-line result. Correct but expensive, so it is reserved for changes
     * that alter how lines are rendered or measured: style, highlighter, structure provider, wrap,
     * password mode, zoom. Ordinary edits must not use it — see {@link #onDocumentMutated()}.
     */
    private void invalidateLayout() {
        analyzedVersion = -1;
        structureAnalyzedVersion = -1;
        searchAnalyzedVersion = -1;
        legacyHighlightVersion = -1;
        highlightResyncFrom = 0;
        highlightStatesPopulatedThrough = 0;
        bracketCheckpoints.clear();
        // These are keyed by document version, so a change that alters lexing without editing the text
        // (a new highlighter, password mode) would otherwise keep serving stale answers.
        ignoreCacheVersion = -1;
        ignoreCacheLastLine = -1;
        ignoreCacheLastRanges = null;
        bracketMatchCacheVersion = -1;
        activeBlockCacheVersion = -2;
        // Row counts depend on the font and the wrap width, both of which a style change can alter.
        wrapRowCountsWidth = -1;
        wrapRowCountsLineCount = -1;
        wrapRemeasurePending = false;
        discardAllLineLayouts();
    }

    /**
     * Forces the row mapping to be rebuilt while keeping cached line layouts. Used for folding, which
     * changes which lines are visible but not how any line looks.
     */
    private void invalidateRowMapping() {
        rowMappingDirty = true;
    }

    /** Marks the search results stale without discarding layouts. */
    private void invalidateSearch() {
        searchAnalyzedVersion = -1;
        markSearchDirty(0, Math.max(0, document.getLineCount() - 1));
        searchDirty = true;
    }

    /**
     * Brings the per-line bookkeeping arrays up to date with the document. Deliberately does
     * <em>not</em> build {@link LineLayout} objects: those are materialized by
     * {@link #layoutFor(int)} only for lines that are drawn or measured, which is what keeps a
     * keystroke in a 100k line document cheap.
     */
    private void ensureLayout() {
        int wrapWidth = wrapEnabled ? Math.max(1, Math.round(getWrapWidth())) : -1;
        boolean geometryChanged = wrapWidth != analyzedWrapWidth || wrapEnabled != analyzedWrapEnabled;
        if (document.getVersion() == analyzedVersion && !geometryChanged && !rowMappingDirty && !searchDirty) {
            return;
        }
        if (layoutSyncInProgress) {
            return;
        }

        layoutSyncInProgress = true;
        try {
            int lineCount = document.getLineCount();
            // Folding only changes which lines are visible, so it asks for a row-mapping rebuild and
            // must not land in the discard-everything branch: that would reset the lexer state cache and
            // force a synchronous re-scan of the document.
            boolean rebuildOnly = (rowMappingDirty || searchDirty)
                && document.getVersion() == analyzedVersion && !geometryChanged;
            if (!rebuildOnly) {
                if (geometryChanged || analyzedVersion < 0) {
                    discardAllLineLayouts();
                    invalidateHighlightStateFrom(0);
                    resizeLineArrays(lineCount);
                    discardSemanticTokensAfterRebuild();
                    // A geometry change alters no text, so only a version change means the content moved.
                    if (document.getVersion() != analyzedVersion) {
                        markWholeDocumentChanged();
                        // No journal was replayed, so the structure cache never saw those edits.
                        invalidateStructureCache();
                    }
                } else if (!syncLineArraysFromJournal(lineCount)) {
                    discardAllLineLayouts();
                    invalidateHighlightStateFrom(0);
                    resizeLineArrays(lineCount);
                    discardSemanticTokensAfterRebuild();
                    markWholeDocumentChanged();
                    // The replay can half-apply its splices before giving up, so nothing it touched is
                    // trustworthy.
                    invalidateStructureCache();
                }
                lineArraysSyncedVersion = document.getVersion();
            }

            ensureStructure(lineCount);
            refreshSearchMatches();
            rebuildHiddenLines(lineCount);
            rebuildVisualRows(lineCount);
            revealCursorIfHidden();
            updateMaxLineWidth();

            analyzedVersion = document.getVersion();
            analyzedWrapWidth = wrapWidth;
            analyzedWrapEnabled = wrapEnabled;
            rowMappingDirty = false;
            searchDirty = false;
        } finally {
            layoutSyncInProgress = false;
        }
        clampScroll();
    }

    /**
     * Applies the document's edit journal to the per-line arrays, so an edit costs work proportional
     * to the lines it touched instead of to the document.
     *
     * @return false when the journal could not reach back far enough and a full rebuild is needed
     */
    private boolean syncLineArraysFromJournal(final int lineCount) {
        if (analyzedVersion < document.getJournalBaseVersion()) {
            return false;
        }
        final boolean[] ok = {true};
        boolean replayed = document.replayEditsSince(analyzedVersion, new CodeDocument.LineEditVisitor() {
            @Override
            public void onLineEdit(int startLine, int removedCount, int insertedCount) {
                if (!ok[0]) {
                    return;
                }
                if (!spliceLineLayouts(startLine, removedCount, insertedCount)) {
                    ok[0] = false;
                    return;
                }
                spliceSemanticTokens(startLine, removedCount, insertedCount);
                invalidateHighlightStateFrom(startLine);
                notePendingContentRange(startLine, startLine + Math.max(0, insertedCount - 1),
                    removedCount, insertedCount);
            }
        });
        if (!replayed || !ok[0]) {
            return false;
        }
        resizeLineArrays(lineCount);
        return lineLayouts.size == lineCount;
    }

    /** Removes {@code removedCount} layout slots at {@code startLine} and inserts {@code insertedCount} empty ones. */
    private boolean spliceLineLayouts(int startLine, int removedCount, int insertedCount) {
        if (startLine < 0 || startLine > lineLayouts.size) {
            return false;
        }
        int removable = Math.min(removedCount, lineLayouts.size - startLine);
        // A same-line edit (the common path: typing, Replace Current, most Replace All hits) must not
        // shift the tail of a 100k-slot array. Nulling the changed slots in place is O(the range).
        if (insertedCount == removable) {
            for (int i = 0; i < removable; i++) {
                // discardLineLayout nulls the slot itself.
                discardLineLayout(startLine + i);
            }
            if (searchMatches.size >= startLine + removable) {
                for (int i = 0; i < removable; i++) {
                    searchMatches.set(startLine + i, null);
                }
                markSearchDirty(startLine, startLine + insertedCount - 1);
            }
        } else {
            for (int i = 0; i < removable; i++) {
                discardLineLayout(startLine + removable - 1 - i);
                lineLayouts.removeIndex(startLine + removable - 1 - i);
            }
            for (int i = 0; i < insertedCount; i++) {
                lineLayouts.insert(startLine + i, null);
            }

            // Keep the per-line search results aligned so only the replaced lines are re-searched.
            if (searchMatches.size >= startLine + removable) {
                for (int i = 0; i < removable; i++) {
                    searchMatches.removeIndex(startLine + removable - 1 - i);
                }
                for (int i = 0; i < insertedCount; i++) {
                    searchMatches.insert(startLine + i, null);
                }
                markSearchDirty(startLine, startLine + insertedCount - 1);
            }
        }
        invalidateBracketCheckpointsFrom(startLine);
        spliceHighlightStates(startLine, removable, insertedCount);
        spliceWrapRowCounts(startLine, removable, insertedCount);
        spliceCollapsedLines(startLine, removable, insertedCount);
        spliceStructure(startLine, removable, insertedCount);
        // Inserted slots are null, so they add nothing; the removal loop above already accounted for
        // the layouts it dropped.
        return true;
    }

    /**
     * Keeps {@link #lineStartStates} aligned with a line-count change so a later line cannot
     * "converge" against a leftover value that now belongs to a different line.
     *
     * <p>{@code lineStartStates[i]} is the lexer state at the start of line {@code i}. After replacing
     * {@code removedCount} lines at {@code startLine} with {@code insertedCount} lines, the start
     * state of {@code startLine} itself is still valid (it only depends on the lines above). The
     * start states of the inserted lines, and of everything after them, have to be revalidated.
     * Shifting the stored tail lets that revalidation still stop early if the splice's outgoing
     * state matches what used to follow the removed range.
     */
    private void spliceHighlightStates(int startLine, int removedCount, int insertedCount) {
        int delta = insertedCount - removedCount;
        int populated = highlightStatesPopulatedThrough;
        if (populated <= startLine) {
            invalidateHighlightStateFrom(startLine);
            return;
        }
        if (delta != 0) {
            int oldTail = populated;
            ensureHighlightStateCapacity(oldTail + Math.max(0, delta) + 1);
            if (delta > 0) {
                for (int i = oldTail; i > startLine; i--) {
                    int dest = i + delta;
                    if (dest < lineStartStates.length) {
                        lineStartStates[dest] = lineStartStates[i];
                    }
                }
            } else {
                for (int i = startLine + 1; i <= oldTail + delta; i++) {
                    int src = i - delta;
                    if (src < lineStartStates.length) {
                        lineStartStates[i] = lineStartStates[src];
                    }
                }
            }
            highlightStatesPopulatedThrough = Math.max(startLine, oldTail + delta);
        }
        invalidateHighlightStateFrom(startLine);
    }

    private void resizeLineArrays(int lineCount) {
        while (lineLayouts.size > lineCount) {
            discardLineLayout(lineLayouts.size - 1);
            lineLayouts.removeIndex(lineLayouts.size - 1);
        }
        while (lineLayouts.size < lineCount) {
            lineLayouts.add(null);
        }
        if (hiddenLines.length != lineCount) {
            hiddenLines = new boolean[lineCount];
        }
        if (visualRowsPerLine.length != lineCount) {
            visualRowsPerLine = new int[lineCount];
        }
        if (visualRowStart.length != lineCount) {
            visualRowStart = new int[lineCount];
        }
        ensureHighlightStateCapacity(lineCount);
        if (indentLevels.length != lineCount) {
            int[] grown = new int[lineCount];
            System.arraycopy(indentLevels, 0, grown, 0, Math.min(indentLevels.length, lineCount));
            indentLevels = grown;
        }
    }

    /**
     * Sizes {@link #semanticTokenLines} to exactly {@code lineCount}.
     *
     * <p>Called only from a push, so an editor nobody pushes tokens to never allocates the array at all
     * — the reason it is not resized alongside the others in {@link #resizeLineArrays(int)}.
     *
     * <p>Exactly, not just grown, because {@link #spliceSemanticTokens(int, int, int)} keeps the size in
     * step by arithmetic: it removes and inserts the same counts the document did, so it preserves an
     * exact size but cannot repair a stale one. Leaving a longer array behind after the document shrank
     * would let a later splice remove slots that no line owns and drift the two apart for good.
     */
    private void resizeSemanticTokenLines(int lineCount) {
        while (semanticTokenLines.size > lineCount) {
            semanticTokenLines.removeIndex(semanticTokenLines.size - 1);
        }
        while (semanticTokenLines.size < lineCount) {
            semanticTokenLines.add(null);
        }
    }

    /**
     * Keeps {@link #semanticTokenLines} aligned with a line-count change.
     *
     * <p>Tokens on the replaced lines are dropped rather than adjusted. Their columns described text
     * that no longer exists, and the editor cannot re-derive them the way it re-runs the lexer or the
     * search: only the producer knows what the new text means. So an edited line falls back to lexical
     * colouring until the next push, while every other line keeps its tokens.
     */
    private void spliceSemanticTokens(int startLine, int removedCount, int insertedCount) {
        // Bailing out past the end leaves the array shorter than the document, which is safe rather
        // than merely tolerable: the array is exact at every push, so an edit starting at or past its
        // end can only be appending lines that had no tokens to begin with. The shortfall stays at the
        // tail, every earlier slot keeps pointing at the line it belongs to, and getSemanticTokensAtLine
        // answers null for the rest. The next push makes the size exact again.
        if (semanticTokenCount == 0 || startLine < 0 || startLine >= semanticTokenLines.size) {
            return;
        }
        int removable = Math.min(removedCount, semanticTokenLines.size - startLine);
        for (int i = 0; i < removable; i++) {
            dropSemanticTokensAt(startLine + i);
        }
        if (insertedCount == removable) {
            // Same-line edit, the typing path: the slots are already null, so there is nothing to move.
            if (removable > 0) {
                semanticTokensShifted = true;
            }
            return;
        }
        for (int i = 0; i < removable; i++) {
            semanticTokenLines.removeIndex(startLine + removable - 1 - i);
        }
        for (int i = 0; i < insertedCount; i++) {
            semanticTokenLines.insert(startLine + i, null);
        }
        semanticTokensShifted = true;
    }

    /** Drops one line's tokens, keeping {@link #semanticTokenCount} honest. */
    private void dropSemanticTokensAt(int line) {
        Array<CodeSemanticToken> lineTokens = semanticTokenLines.get(line);
        if (lineTokens == null) {
            return;
        }
        semanticTokenCount -= lineTokens.size;
        semanticTokenLines.set(line, null);
    }

    /**
     * Drops every token when the per-line arrays were rebuilt from scratch instead of spliced, because
     * how the lines moved is then unknown and keeping the tokens would paint the wrong identifiers.
     *
     * <p>Gated on the document version rather than on the rebuild itself. A rebuild happens for reasons
     * that change no text — the first layout, and a wrap-width change — and tokens pushed against the
     * version now current are still exactly right whatever made the layout throw its work away.
     */
    private void discardSemanticTokensAfterRebuild() {
        if (semanticTokenVersion < 0 || semanticTokenVersion == document.getVersion()) {
            return;
        }
        if (semanticTokenCount == 0) {
            semanticTokensShifted = true;
            return;
        }
        for (int i = 0; i < semanticTokenLines.size; i++) {
            semanticTokenLines.set(i, null);
        }
        semanticTokenCount = 0;
        semanticTokensShifted = true;
    }

    /**
     * Drops every cached layout while keeping the array's length, so the colours are rebuilt on the next
     * draw and nothing else is.
     *
     * <p>Deliberately neither of the two neighbouring helpers.
     * {@link #discardAllLineLayouts()} empties the array, which is only safe when the caller follows it
     * with {@link #resizeLineArrays(int)} or forces that to happen by clearing
     * {@code analyzedVersion} — {@link #ensureLayout()} returns early when the version has not moved,
     * so an unpaired call leaves {@code lineLayouts} empty and every line then draws as the empty
     * layout. {@link #invalidateLayout()} does force the resize, but it also resets the lexer state
     * cache and the wrap measurements, and a recolour changes neither the lexing nor the text width.
     */
    private void recolorAllLineLayouts() {
        for (int i = 0; i < lineLayouts.size; i++) {
            lineLayouts.set(i, null);
        }
        materializedLineCount = 0;
    }

    private void discardAllLineLayouts() {
        lineLayouts.clear();
        materializedLineCount = 0;
        measuredMaxLineWidth = 0f;
        estimatedWidestLine = -1;
        estimatedWidestLineWidth = 0f;
    }

    private void discardLineLayout(int line) {
        if (line < 0 || line >= lineLayouts.size || lineLayouts.get(line) == null) {
            return;
        }
        lineLayouts.set(line, null);
        materializedLineCount--;
    }

    /**
     * Recomputes which lines are hidden by collapsed folds.
     *
     * <p>Skips the O(lines) clearing pass entirely when nothing is collapsed, which is the normal case
     * and the one that has to stay cheap while typing in a large document. {@link #anyLineHidden}
     * records the result so row mapping can take its own fast path.
     */
    private void rebuildHiddenLines(int lineCount) {
        boolean anyCollapsed = false;
        for (FoldRegion region : foldRegions) {
            region.collapsed = isCollapsedStartLine(region.startLine);
            anyCollapsed |= region.collapsed;
        }

        if (!anyCollapsed) {
            if (anyLineHidden) {
                for (int i = 0; i < lineCount; i++) {
                    hiddenLines[i] = false;
                }
                anyLineHidden = false;
            }
            return;
        }

        for (int i = 0; i < lineCount; i++) {
            hiddenLines[i] = false;
        }
        for (FoldRegion region : foldRegions) {
            if (region.collapsed) {
                for (int line = region.startLine + 1; line <= region.endLine && line < lineCount; line++) {
                    hiddenLines[line] = true;
                }
            }
        }
        anyLineHidden = true;
    }

    /**
     * Recomputes visual row offsets. Without wrapping every visible line is exactly one row, so this
     * stays a cheap integer pass even at 100k lines; with wrapping it has to consult each line's
     * layout, which materializes them.
     */
    private void rebuildVisualRows(int lineCount) {
        // With no wrapping and nothing collapsed, row N is line N. Recognising that turns the
        // per-keystroke row-mapping cost from O(lines) into O(1), which matters at 100k lines.
        if (!wrapEnabled && !anyLineHidden) {
            rowMappingIsIdentity = true;
            totalVisualRows = Math.max(1, lineCount);
            return;
        }

        rowMappingIsIdentity = false;
        if (wrapEnabled) {
            refreshWrapRowCounts(lineCount);
        }
        // visualRowStart is a prefix sum, so it has to be rewritten whole. This is a pass of trivial
        // integer arithmetic with no measuring or allocation — the expensive part is the row counts
        // above, which are cached. If this ever shows up in a profile, the fix is a prefix-sum tree.
        totalVisualRows = 0;
        for (int i = 0; i < lineCount; i++) {
            visualRowStart[i] = totalVisualRows;
            if (hiddenLines[i]) {
                visualRowsPerLine[i] = 0;
            } else {
                // wrapRowCounts is maintained incrementally; without wrapping every line is one row.
                visualRowsPerLine[i] = wrapEnabled ? Math.max(1, wrapRowCounts[i]) : 1;
            }
            totalVisualRows += visualRowsPerLine[i];
        }
        if (totalVisualRows == 0) {
            totalVisualRows = 1;
        }
    }

    /**
     * Brings {@link #wrapRowCounts} up to date, recomputing only the lines an edit touched.
     *
     * <p>A row count needs glyph advances but not highlighting, so {@link #countWrapRows(int, int)}
     * avoids building a layout — which would also drag in the lexer state for that line. Only a change
     * to the wrap width itself (a resize) invalidates every line.
     */
    private void refreshWrapRowCounts(int lineCount) {
        int wrapWidth = Math.max(1, Math.round(getWrapWidth()));
        if (wrapRowCounts.length < lineCount) {
            int[] grown = new int[Math.max(lineCount, wrapRowCounts.length * 2)];
            System.arraycopy(wrapRowCounts, 0, grown, 0, wrapRowCounts.length);
            wrapRowCounts = grown;
        }

        boolean widthChanged = wrapWidth != wrapRowCountsWidth;
        boolean countMismatch = wrapRowCountsLineCount != lineCount;
        if (widthChanged || countMismatch) {
            // Nothing cached is usable: every row count depends on the wrap width, and a line-count
            // mismatch means the array is no longer aligned with the document.
            if (wrapRowCountsWidth > 0 && lineCount > WRAP_REMEASURE_SYNC_LINE_LIMIT && !countMismatch) {
                // A resize fires every frame while the user drags. Keep the previous counts, which are
                // only slightly wrong, until the width settles.
                wrapRemeasurePending = true;
                wrapRemeasureDelayRemaining = WRAP_REMEASURE_DELAY;
                wrapRemeasureTargetWidth = wrapWidth;
                return;
            }
            measureAllWrapRows(lineCount, wrapWidth);
            return;
        }

        int from = Math.max(0, wrapRowsDirtyFrom);
        int to = Math.min(lineCount - 1, wrapRowsDirtyTo);
        for (int line = from; line <= to; line++) {
            wrapRowCounts[line] = countWrapRows(line, wrapWidth);
        }
        wrapRowsDirtyFrom = Integer.MAX_VALUE;
        wrapRowsDirtyTo = -1;
    }

    /**
     * Measures every line's wrap row count. Unavoidable when the wrap width changes, because each count
     * depends on it, and it is what makes wrap mode inherently more expensive than not wrapping. Runs
     * once per width, not once per keystroke.
     */
    private void measureAllWrapRows(int lineCount, int wrapWidth) {
        if (wrapWidth != wrapRowCountsWidth) {
            // Every materialized layout was split at the previous width and now has the wrong number of
            // segments for the rows this measure is about to hand it. ensureLayout usually discards them
            // itself, but not on the paths that reach here without a geometry change: the deferred
            // re-measure fires long after the resize that scheduled it, and a line-count mismatch while
            // one is pending lands here too.
            discardAllLineLayouts();
        }
        // Recorded before the loop rather than after, so a layout materialized part-way through — none
        // does today, but the field is what layoutWrapWidth answers with — already wraps at the width
        // being measured instead of the one being replaced.
        wrapRowCountsWidth = wrapWidth;
        for (int line = 0; line < lineCount; line++) {
            wrapRowCounts[line] = countWrapRows(line, wrapWidth);
        }
        wrapRowCountsLineCount = lineCount;
        wrapRowsDirtyFrom = Integer.MAX_VALUE;
        wrapRowsDirtyTo = -1;
        wrapRemeasurePending = false;
        wrapRemeasureDelayRemaining = 0f;
    }

    /** Runs a deferred full wrap re-measure once the wrap width has stopped changing. */
    private void updateWrapRemeasureDebounce(float delta) {
        if (!wrapRemeasurePending || !wrapEnabled) {
            return;
        }
        int wrapWidth = Math.max(1, Math.round(getWrapWidth()));
        if (wrapWidth != wrapRemeasureTargetWidth) {
            // Still resizing: restart the wait at the newest width.
            wrapRemeasureTargetWidth = wrapWidth;
            wrapRemeasureDelayRemaining = WRAP_REMEASURE_DELAY;
            return;
        }
        wrapRemeasureDelayRemaining -= delta;
        if (wrapRemeasureDelayRemaining > 0f) {
            return;
        }
        int lineCount = document.getLineCount();
        if (wrapRowCounts.length < lineCount) {
            wrapRowCounts = new int[lineCount];
        }
        measureAllWrapRows(lineCount, wrapWidth);
        rebuildVisualRows(lineCount);
        clampScroll();
    }

    /**
     * How many visual rows a line occupies at {@code wrapWidth}, using the same break rules as
     * {@link #wrapLine}, without allocating a layout or prefix-width array.
     */
    private int countWrapRows(int line, int wrapWidth) {
        // Same text wrapLine sees, including password masking. The two must agree on the row count
        // or the mapping between visual rows and document lines is silently wrong.
        String text = getDisplayLineText(document.getLine(line));
        int length = text.length();
        if (length == 0) {
            return 1;
        }

        // Tab stops are measured from the start of the line, exactly as LineLayout.prefixWidths does,
        // so absoluteWidth tracks the line while the comparison uses the width since the segment start.
        // If these two ever disagree the row count and the drawn segments diverge, which corrupts the
        // row mapping.
        // Continuation rows are narrower by the indent they are pushed right by. wrapLine applies the
        // identical subtraction from the identical helper; if these two ever drift the row count stops
        // matching the drawn segments.
        float continuationIndent = continuationIndentWidth(text, wrapWidth);
        int rows = 0;
        int segmentStart = 0;
        float absoluteWidth = 0f;
        float segmentStartWidth = 0f;
        while (segmentStart < length) {
            rows++;
            float available = rows > 1 ? Math.max(1f, wrapWidth - continuationIndent) : wrapWidth;
            int bestBreak = -1;
            int index = segmentStart;
            while (index < length) {
                char current = text.charAt(index);
                if (current == '\t') {
                    absoluteWidth += getTabAdvanceAtWidth(absoluteWidth);
                } else {
                    char next = index + 1 < length ? text.charAt(index + 1) : 0;
                    absoluteWidth += glyphAdvance(current, next);
                }
                if (absoluteWidth - segmentStartWidth > available) {
                    break;
                }
                if (isWrapOpportunity(current)) {
                    bestBreak = index + 1;
                }
                index++;
            }

            if (index >= length) {
                return rows;
            }

            int breakIndex;
            if (index == segmentStart) {
                breakIndex = segmentStart + 1;
            } else if (bestBreak > segmentStart) {
                breakIndex = bestBreak;
            } else {
                breakIndex = index;
            }
            // Every branch above gives breakIndex > segmentStart and trimWrappedIndent only moves
            // forward, so the loop always advances.
            int nextStart = trimWrappedIndent(text, breakIndex);
            // The inner loop may have run past the break, so re-walk from this segment's start, whose
            // width is known, up to the next one. Each character is therefore visited at most twice
            // overall, keeping this linear in the line length rather than quadratic in the row count.
            absoluteWidth = advanceWidth(text, segmentStart, nextStart, segmentStartWidth);
            segmentStartWidth = absoluteWidth;
            segmentStart = nextStart;
        }
        return Math.max(1, rows);
    }

    /**
     * How far a wrapped continuation row of {@code text} is pushed right, in pixels.
     *
     * <p><strong>This is the single source of that number.</strong> {@link #wrapLine} subtracts it from
     * the available width, {@link #countWrapRows} must subtract exactly the same value or the counted
     * rows and the drawn segments diverge, and the draw and hit-test paths add it back as an x offset.
     * Any two of those disagreeing puts the caret on the wrong character, so every caller goes through
     * here rather than recomputing.
     *
     * <p>Clamped to half the wrap width. Without a clamp a deeply indented long line leaves almost no
     * room per row and the row count explodes; the clamp is expressed against the same {@code int}
     * {@code wrapWidth} both callers hold, so it cannot round differently between them.
     */
    private float continuationIndentWidth(CharSequence text, int wrapWidth) {
        if (!wrapContinuationIndentEnabled) {
            return 0f;
        }
        int indentEnd = 0;
        while (indentEnd < text.length() && isIndentWhitespace(text.charAt(indentEnd))) {
            indentEnd++;
        }
        // Measured from absolute zero, because a tab's advance depends on where it starts.
        float width = advanceWidth(text, 0, indentEnd, 0f);
        if (wrapContinuationIndentColumns > 0) {
            // A plain multiple of the space advance, not a measured run: allocating a string here would
            // cost one allocation per line on a full re-measure, and spaces have no tab stops to thread.
            width += glyphAdvance(' ', ' ') * wrapContinuationIndentColumns;
        }
        float limit = wrapWidth * 0.5f;
        return width > limit ? limit : width;
    }

    /** Leading whitespace for indent purposes: spaces and tabs, never a line separator. */
    private boolean isIndentWhitespace(char character) {
        return character == ' ' || character == '\t';
    }

    /**
     * Advances {@code startWidth}, the width of {@code text[0, from)}, over {@code text[from, to)}.
     * Tab stops depend on the absolute width, which is why this threads it through rather than summing
     * the range on its own.
     */
    private float advanceWidth(CharSequence text, int from, int to, float startWidth) {
        float width = startWidth;
        int stop = Math.min(to, text.length());
        for (int i = Math.max(0, from); i < stop; i++) {
            char current = text.charAt(i);
            if (current == '\t') {
                width += getTabAdvanceAtWidth(width);
            } else {
                char next = i + 1 < text.length() ? text.charAt(i + 1) : 0;
                width += glyphAdvance(current, next);
            }
        }
        return width;
    }

    /**
     * Shifts collapsed-region start lines through a line-count change, so a fold survives editing above
     * it.
     *
     * <p>Known limitation: the journal reports only (start line, removed, inserted), with no column, so
     * splitting a fold's own header line cannot be told apart from typing inside it. Pressing Enter at
     * column 0 of a collapsed header therefore leaves the fold on the now-empty line and it appears to
     * expand. Re-collapsing is one keystroke, and the previous behaviour lost the fold on <em>any</em>
     * edit above it, so this is a deliberate trade rather than an oversight.
     */
    private void spliceCollapsedLines(int startLine, int removedCount, int insertedCount) {
        if (collapsedLines.size == 0) {
            return;
        }
        int delta = insertedCount - removedCount;
        int removedEnd = startLine + removedCount;

        // Rewrite in place. Lines below the splice shift by the delta. A line inside the replaced range
        // is clamped to the last surviving index of that range rather than dropped: the header text may
        // have merged upward (deleting the newline before it) or simply been edited in place, and the
        // journal carries no column to tell those apart from a real deletion.
        // pruneStaleCollapsedLines(), which runs after the next structure analysis, removes whatever no
        // longer starts a region — so guessing generously here cannot leave a fold hiding wrong content.
        for (int i = collapsedLines.size - 1; i >= 0; i--) {
            int line = collapsedLines.get(i);
            if (line < startLine) {
                continue;
            }
            if (line >= removedEnd) {
                collapsedLines.set(i, line + delta);
            } else {
                int clamped = Math.min(line, startLine + Math.max(0, insertedCount - 1));
                if (collapsedLines.indexOf(clamped) >= 0 && clamped != line) {
                    collapsedLines.removeIndex(i);
                } else {
                    collapsedLines.set(i, clamped);
                }
            }
        }
    }

    /** Marks a line range's cached wrap row counts as stale. */
    private void markWrapRowsDirty(int fromLine, int toLine) {
        wrapRowsDirtyFrom = Math.min(wrapRowsDirtyFrom, fromLine);
        wrapRowsDirtyTo = Math.max(wrapRowsDirtyTo, toLine);
    }

    /**
     * Keeps {@link #wrapRowCounts} aligned with a line-count change, so an edit does not shift every
     * later line's cached row count onto the wrong line.
     */
    private void spliceWrapRowCounts(int startLine, int removedCount, int insertedCount) {
        if (wrapRowCounts.length == 0 || wrapRowCountsLineCount < 0) {
            return;
        }
        int delta = insertedCount - removedCount;
        int oldLineCount = document.getLineCount() - delta;
        // Keep the alignment marker in step, otherwise the next refresh sees a line-count mismatch and
        // re-measures the whole document, which is exactly what this splice exists to avoid.
        wrapRowCountsLineCount = document.getLineCount();
        if (delta > 0) {
            int needed = document.getLineCount();
            if (wrapRowCounts.length < needed) {
                int[] grown = new int[Math.max(needed, wrapRowCounts.length * 2)];
                System.arraycopy(wrapRowCounts, 0, grown, 0, wrapRowCounts.length);
                wrapRowCounts = grown;
            }
            for (int i = Math.min(oldLineCount, wrapRowCounts.length - delta) - 1; i >= startLine; i--) {
                wrapRowCounts[i + delta] = wrapRowCounts[i];
            }
        } else if (delta < 0) {
            for (int i = startLine; i < oldLineCount + delta && i - delta < wrapRowCounts.length; i++) {
                wrapRowCounts[i] = wrapRowCounts[i - delta];
            }
        }
        // The replaced lines themselves must be re-measured; 0 marks them unmeasured.
        for (int i = startLine; i < startLine + insertedCount && i < wrapRowCounts.length; i++) {
            wrapRowCounts[i] = 0;
        }
        markWrapRowsDirty(startLine, startLine + Math.max(0, insertedCount - 1));
    }

    private void revealCursorIfHidden() {
        int cursorLine = document.getCursorLine();
        if (!isHiddenLine(cursorLine)) {
            return;
        }
        FoldRegion region = findContainingRegion(cursorLine);
        if (region == null) {
            return;
        }
        if (!isCollapsedSuffixCursorVisible(region, cursorLine, document.getCursorColumn())) {
            int column = Math.min(document.getCursorColumn(), document.getLineLength(region.startLine));
            document.moveCursorTo(region.startLine, column);
        }
    }

    /**
     * Width every materialized {@link LineLayout} has to be split at, so the segments a layout has agree
     * with the rows the mapping gives its line.
     *
     * <p>Deliberately <em>not</em> a fresh {@link #getWrapWidth()} sample. The row mapping is a prefix
     * sum of {@link #countWrapRows(int, int)} results, and those were measured at
     * {@link #wrapRowCountsWidth}; a layout split at any other width can end up with fewer segments than
     * the mapping hands it rows, which is what made {@code drawRows} read a segment index with nothing
     * behind it. Sampling live here is how the two drifted apart:
     *
     * <ul>
     *   <li>The samples are taken at different moments, and above
     *       {@link #WRAP_REMEASURE_SYNC_LINE_LIMIT} lines {@link #refreshWrapRowCounts(int)} keeps the
     *       previous width's counts on purpose while a resize settles.
     *   <li>{@link #getWrapWidth()} is not a function of the widget size alone — it subtracts the
     *       scrollbar when one is showing, and whether one shows depends on {@link #totalVisualRows},
     *       which the row mapping has just changed.
     * </ul>
     *
     * <p>The fallback covers a layout materialized before any row mapping exists.
     */
    private int layoutWrapWidth() {
        if (wrapRowCountsWidth > 0) {
            return wrapRowCountsWidth;
        }
        return Math.max(1, Math.round(getWrapWidth()));
    }

    /**
     * Returns the layout for one line, building it if this is the first time it is needed. Callers
     * must not hold the result across an edit.
     */
    private LineLayout layoutFor(int line) {
        // Callers such as bracket matching can run from input handling, before draw() has brought the
        // arrays up to date. Materializing against a stale array would cache a layout under the wrong
        // line index, so sync first. The guard keeps ensureLayout's own use of layoutFor (wrap mode)
        // from recursing.
        if (!layoutSyncInProgress && document.getVersion() != analyzedVersion) {
            ensureLayout();
        }
        if (line < 0 || line >= lineLayouts.size || line >= document.getLineCount()) {
            return EMPTY_LINE_LAYOUT;
        }
        LineLayout existing = lineLayouts.get(line);
        if (existing != null) {
            return existing;
        }

        String displayLine = getDisplayLineText(document.getLine(line));
        LineLayout layout = new LineLayout(displayLine, safeIndentLevel(indentLevels, line), EMPTY_TOKENS);
        // Published before it is filled in, so an indirect re-entry for this same line sees a usable
        // (if momentarily empty) layout rather than recursing forever.
        lineLayouts.set(line, layout);
        materializedLineCount++;

        buildLineContent(line, displayLine, layout);
        layout.ensurePrefixWidths(this);
        if (wrapEnabled) {
            // The mapping's width, not a fresh sample: see layoutWrapWidth.
            wrapLine(layout, layoutWrapWidth());
        } else {
            layout.segmentStarts.clear();
            layout.segmentEnds.clear();
            layout.segmentStarts.add(0);
            layout.segmentEnds.add(displayLine.length());
            layout.continuationIndent = 0f;
        }

        evictColdLineLayouts(line);
        noteMeasuredLineWidth(layout);
        return layout;
    }

    /**
     * Keeps the materialized set bounded by dropping layouts far from the viewport. Scans the whole
     * array, but only when the cap is exceeded, so the cost is amortized over many materializations
     * rather than paid per edit.
     */
    private void evictColdLineLayouts(int protectedLine) {
        if (materializedLineCount <= MAX_MATERIALIZED_LINES) {
            return;
        }
        // Not while ensureLayout is rebuilding: the visible-line lookup reads row arrays that are only
        // half updated, and in wrap mode it would evict lines the rebuild is still walking.
        if (layoutSyncInProgress) {
            return;
        }
        int keepFrom = Math.max(0, getFirstVisibleLine() - HIGHLIGHT_MARGIN_LINES);
        int keepTo = Math.min(lineLayouts.size - 1, getLastVisibleLine() + HIGHLIGHT_MARGIN_LINES);
        int surviving = 0;
        for (int line = 0; line < lineLayouts.size; line++) {
            if (lineLayouts.get(line) == null) {
                continue;
            }
            if (line == protectedLine || (line >= keepFrom && line <= keepTo)) {
                surviving++;
                continue;
            }
            lineLayouts.set(line, null);
        }
        materializedLineCount = surviving;
    }

    /**
     * Runs the structure provider, which is O(document) and so cannot run per keystroke on a large
     * file. Small documents are analyzed immediately; large ones are deferred to
     * {@link #updateStructureAnalysisDebounce(float)} and keep using the previous fold regions and
     * indent levels until then.
     */
    private void ensureStructure(int lineCount) {
        if (structureAnalyzedVersion == document.getVersion()) {
            return;
        }
        if (lineCount > STRUCTURE_SYNC_LINE_LIMIT && structureAnalyzedVersion >= 0) {
            structureAnalysisPending = true;
            structureAnalysisDelayRemaining = STRUCTURE_ANALYSIS_DELAY;
            return;
        }
        runStructureAnalysis();
    }

    private void runStructureAnalysis() {
        int lineCount = document.getLineCount();
        Array<String> lines = document.sharedLineSnapshot();

        if (passwordMode) {
            // Nothing about a masked document may leak through folding, indent guides or an outline.
            invalidateStructureCache();
            structureRegions.clear();
            structureRegionEmitLines.clear();
            indentLevels = new int[lineCount];
            setSymbols(null);
            symbolAnalysisPending = false;
            symbolAnalysisDelayRemaining = 0f;
        } else {
            runStructurePass(lineCount, lines);
        }
        applyStructureResults(lineCount, lines);
    }

    /**
     * Fills {@link #structureRegions} and {@link #indentLevels}, incrementally when it can.
     *
     * <p>Symbols are *not* produced here on the incremental path — they get their own deferred pass. See
     * {@link #SYMBOL_ANALYSIS_DELAY}.
     */
    private void runStructurePass(int lineCount, Array<String> lines) {
        IncrementalCodeStructureProvider incremental = structureProvider instanceof IncrementalCodeStructureProvider
            ? (IncrementalCodeStructureProvider) structureProvider
            : null;
        if (incremental == null) {
            // A plain provider only has the whole-document API, so this stays exactly as it always was.
            invalidateStructureCache();
            CodeStructureInfo info = structureProvider.analyze(lines);
            structureRegions.clear();
            structureRegionEmitLines.clear();
            structureRegions.addAll(info.foldRegions);
            indentLevels = info.indentLevels != null && info.indentLevels.length == lineCount
                ? info.indentLevels
                : new int[lineCount];
            // A plain provider computes symbols inside analyze(), so there is no separate pass to defer and
            // deferring would mean throwing away work already done. This path keeps its old timing exactly.
            setSymbols(info.symbols);
            symbolAnalysisPending = false;
            symbolAnalysisDelayRemaining = 0f;
            return;
        }
        if (!runIncrementalStructurePass(incremental, lineCount, lines)) {
            runFullStructurePass(incremental, lineCount, lines);
        }
        // One authoritative list, so the fold regions and the symbol input cannot disagree. Filtering only
        // at the point foldRegions is built is what let a stale region reach the symbol provider.
        pruneStructureRegions(lineCount);
        // Regions are current now; symbols are derived from them on their own timer. The previous symbols
        // stay visible until then rather than being cleared, so an outline does not blank out while typing.
        symbolAnalysisPending = true;
        symbolAnalysisDelayRemaining = SYMBOL_ANALYSIS_DELAY;
    }

    /**
     * Extracts symbols from the current region set, if the structure they describe is current.
     *
     * <p>Re-arms instead of running when the structure pass has not caught up. Symbols are attributed to
     * parents by comparing member lines against region bounds, so running this against a newer text than
     * the regions describe would put members under the wrong parents — worse than being briefly stale.
     */
    private void runSymbolAnalysis() {
        if (structureAnalysisPending || structureAnalyzedVersion != document.getVersion()) {
            symbolAnalysisDelayRemaining = SYMBOL_ANALYSIS_DELAY;
            return;
        }
        symbolAnalysisPending = false;
        symbolAnalysisDelayRemaining = 0f;
        if (passwordMode || !(structureProvider instanceof IncrementalCodeStructureProvider)) {
            setSymbols(null);
            return;
        }
        IncrementalCodeStructureProvider incremental = (IncrementalCodeStructureProvider) structureProvider;
        setSymbols(incremental.extractSymbols(document.sharedLineSnapshot(), structureRegions));
    }

    /** Replaces the symbol tree and records the version it describes. */
    private void setSymbols(Array<CodeSymbol> newSymbols) {
        symbols.clear();
        if (newSymbols != null) {
            symbols.addAll(newSymbols);
        }
        symbolAnalyzedVersion = document.getVersion();
    }

    /** Runs the deferred symbol pass once the document has been quiet briefly. */
    private void updateSymbolAnalysisDebounce(float delta) {
        if (!symbolAnalysisPending) {
            return;
        }
        symbolAnalysisDelayRemaining -= delta;
        if (symbolAnalysisDelayRemaining > 0f) {
            return;
        }
        runSymbolAnalysis();
    }

    /** Forces symbols to describe the current text, structure pass included. */
    private void flushSymbolAnalysis() {
        flushStructureAnalysis();
        if (symbolAnalysisPending || symbolAnalyzedVersion != document.getVersion()) {
            runSymbolAnalysis();
        }
    }

    /** Scans the whole document, laying down checkpoints as it goes. */
    private void runFullStructurePass(IncrementalCodeStructureProvider provider, int lineCount, Array<String> lines) {
        CodeStructureScanner scanner = structureScannerFor(provider);
        scanner.reset();
        structureCheckpoints.clear();
        appendStructureCheckpoint(scanner);

        int[] levels = new int[lineCount];
        for (int line = 0; line < lineCount; line++) {
            levels[line] = scanner.scanLine(lines.get(line));
            if ((line + 1) % STRUCTURE_CHECKPOINT_INTERVAL == 0) {
                appendStructureCheckpoint(scanner);
            }
        }
        scanner.finish();

        indentLevels = levels;
        structureRegions.clear();
        structureRegions.addAll(scanner.getRegions());
        structureRegionEmitLines.clear();
        structureRegionEmitLines.addAll(scanner.getRegionEmitLines());
        structureCacheValid = true;
        structureDirtyFrom = Integer.MAX_VALUE;
        structureDirtyTo = -1;
        lastStructureScanLines = lineCount;
    }

    /**
     * Re-scans only from the checkpoint before the edit until the scanner's position agrees with a stored
     * checkpoint again, splicing the result into the cached regions.
     *
     * <p>Convergence is what bounds this. Once a recomputed position matches the stored one for a line,
     * the provider is pure and the text below is unchanged, so every region below must be the old one with
     * its line numbers shifted — which {@link #spliceStructure} has already done. That is why the
     * checkpoint comparison includes the block stack's start lines and not just the lexical state.
     *
     * @return false when there is no usable cache and a full pass is needed
     */
    private boolean runIncrementalStructurePass(
        IncrementalCodeStructureProvider provider,
        int lineCount,
        Array<String> lines
    ) {
        if (!structureCacheValid || structureCheckpoints.isEmpty() || structureDirtyFrom > lineCount) {
            return false;
        }
        if (lineArraysSyncedVersion != document.getVersion()) {
            return false;
        }
        if (indentLevels.length != lineCount) {
            // The splice keeps this exact, so a mismatch means the cache missed an edit.
            return false;
        }
        if (structureDirtyFrom == Integer.MAX_VALUE) {
            lastStructureScanLines = 0;
            return true;
        }

        int resumeIndex = 0;
        for (int i = structureCheckpoints.size - 1; i >= 0; i--) {
            if (structureCheckpoints.get(i).line <= structureDirtyFrom) {
                resumeIndex = i;
                break;
            }
        }
        CodeStructureScanner.Checkpoint resume = structureCheckpoints.get(resumeIndex);
        if (resume.line > lineCount) {
            return false;
        }

        CodeStructureScanner scanner = structureScannerFor(provider);
        scanner.reset();
        scanner.restoreCheckpoint(resume);

        Array<CodeStructureScanner.Checkpoint> fresh = new Array<>();
        CodeStructureScanner.Checkpoint probe = new CodeStructureScanner.Checkpoint();
        int nextStored = resumeIndex + 1;
        int convergedAt = -1;

        int line = resume.line;
        while (line < lineCount) {
            indentLevels[line] = scanner.scanLine(lines.get(line));
            line++;

            while (nextStored < structureCheckpoints.size && structureCheckpoints.get(nextStored).line < line) {
                // Passed a stored checkpoint without landing on it, which happens when an edit moved it off
                // the interval. It cannot be compared against, so it is superseded by the fresh ones.
                nextStored++;
            }
            boolean atStored = nextStored < structureCheckpoints.size
                && structureCheckpoints.get(nextStored).line == line;
            if (atStored && line > structureDirtyTo) {
                scanner.captureCheckpoint(probe);
                if (probe.matches(structureCheckpoints.get(nextStored))) {
                    convergedAt = line;
                    break;
                }
            }
            if (line % STRUCTURE_CHECKPOINT_INTERVAL == 0 || atStored) {
                CodeStructureScanner.Checkpoint captured = new CodeStructureScanner.Checkpoint();
                scanner.captureCheckpoint(captured);
                fresh.add(captured);
            }
            if (atStored) {
                nextStored++;
            }
        }
        if (convergedAt < 0) {
            // Ran to the end, so the blocks still open are this document's business, not a stale cache's.
            scanner.finish();
        }

        spliceStructureCheckpoints(resumeIndex, fresh, convergedAt);
        // No convergence means the re-scan is authoritative all the way down, so nothing old survives it.
        // Not lineCount + 1: a cached emit line can sit past the line count, having never been shifted.
        spliceStructureRegions(resume.line, convergedAt < 0 ? Integer.MAX_VALUE : convergedAt,
            scanner.getRegions(), scanner.getRegionEmitLines());
        lastStructureScanLines = line - resume.line;
        structureDirtyFrom = Integer.MAX_VALUE;
        structureDirtyTo = -1;
        return true;
    }

    /**
     * Replaces the checkpoints the re-scan covered. Those at or after {@code convergedAt} are kept: the
     * match at that line means everything below it is unchanged.
     */
    private void spliceStructureCheckpoints(
        int resumeIndex,
        Array<CodeStructureScanner.Checkpoint> fresh,
        int convergedAt
    ) {
        Array<CodeStructureScanner.Checkpoint> tail = new Array<>();
        if (convergedAt >= 0) {
            for (int i = 0; i < structureCheckpoints.size; i++) {
                if (structureCheckpoints.get(i).line >= convergedAt) {
                    tail.add(structureCheckpoints.get(i));
                }
            }
        }
        structureCheckpoints.truncate(resumeIndex + 1);
        structureCheckpoints.addAll(fresh);
        structureCheckpoints.addAll(tail);
    }

    /**
     * Splices re-scanned regions into the cached list, keyed on the line each region was <em>closed</em>
     * on rather than the line it ends at.
     *
     * <p>The distinction is load-bearing. An indentation block closes at a line above the one that
     * de-dented, and one closed at end of file is emitted at the line count, so a document ending in blank
     * lines can hold a region whose end line is above the convergence point even though nothing re-emits
     * it. Keyed on the end line those regions would be dropped and lost.
     */
    private void spliceStructureRegions(
        int fromLine,
        int cutoffLine,
        Array<CodeFoldRegion> rescanned,
        IntArray rescannedEmitLines
    ) {
        Array<CodeFoldRegion> merged = new Array<>(structureRegions.size);
        IntArray mergedEmits = new IntArray(structureRegionEmitLines.size);

        for (int i = 0; i < structureRegions.size; i++) {
            if (structureRegionEmitLines.get(i) < fromLine) {
                merged.add(structureRegions.get(i));
                mergedEmits.add(structureRegionEmitLines.get(i));
            }
        }
        merged.addAll(rescanned);
        mergedEmits.addAll(rescannedEmitLines);
        for (int i = 0; i < structureRegions.size; i++) {
            if (structureRegionEmitLines.get(i) >= cutoffLine) {
                merged.add(structureRegions.get(i));
                mergedEmits.add(structureRegionEmitLines.get(i));
            }
        }

        structureRegions.clear();
        structureRegions.addAll(merged);
        structureRegionEmitLines.clear();
        structureRegionEmitLines.addAll(mergedEmits);
    }

    /** Whether a cached line number fell inside the text an edit replaced. */
    private static boolean wasRemoved(int line, int startLine, int removedEnd) {
        return line >= startLine && line < removedEnd;
    }

    /** Drops regions that no longer fit the document, keeping the emit lines parallel. */
    private void pruneStructureRegions(int lineCount) {
        for (int i = structureRegions.size - 1; i >= 0; i--) {
            CodeFoldRegion region = structureRegions.get(i);
            if (region.startLine < 0 || region.endLine >= lineCount || region.endLine <= region.startLine) {
                structureRegions.removeIndex(i);
                structureRegionEmitLines.removeIndex(i);
            }
        }
    }

    private CodeStructureScanner structureScannerFor(IncrementalCodeStructureProvider provider) {
        if (structureScanner == null) {
            structureScanner = new CodeStructureScanner(provider);
        }
        return structureScanner;
    }

    private void appendStructureCheckpoint(CodeStructureScanner scanner) {
        CodeStructureScanner.Checkpoint checkpoint = new CodeStructureScanner.Checkpoint();
        scanner.captureCheckpoint(checkpoint);
        structureCheckpoints.add(checkpoint);
    }

    /**
     * Moves the cached structure through an edit, so the next pass only has to re-scan near it.
     *
     * <p>Every line number the cache holds is shifted here: the checkpoints' own lines and the block start
     * lines inside them, the regions' two endpoints, the emit lines, and {@link #indentLevels}. Shifting
     * is <strong>per line number, not per record</strong>, because a block can open above an edit and
     * still be open below it — its start line must not move while the checkpoint describing it does.
     *
     * <p>Nothing is validated here. A region or a frame whose lines were deleted becomes meaningless, and
     * that is safe: the convergence test compares the whole stack including start lines, so a stale record
     * fails to match and only costs a longer re-scan.
     */
    private void spliceStructure(int startLine, int removedCount, int insertedCount) {
        if (!structureCacheValid) {
            return;
        }
        int delta = insertedCount - removedCount;
        int removedEnd = startLine + removedCount;

        for (int i = structureCheckpoints.size - 1; i > 0; i--) {
            int line = structureCheckpoints.get(i).line;
            // Strictly inside the replaced range: the line it describes is gone. Index 0 is line 0 and is
            // always kept, so the list can never become empty.
            if (line > startLine && line < removedEnd) {
                structureCheckpoints.removeIndex(i);
            }
        }
        for (int i = 0; i < structureCheckpoints.size; i++) {
            structureCheckpoints.get(i).shiftLines(removedEnd, delta);
        }

        // Drop anything describing text that is gone. A region whose start, end or closing line was inside
        // the replaced range cannot be that same region afterwards, and keeping it is not merely untidy: an
        // emit line inside the range is never shifted, so a net deletion can leave one sitting *above* the
        // convergence point, where the splice would keep it. That is exactly what the differential test
        // caught, as a phantom symbol with the fold regions still agreeing.
        for (int i = structureRegions.size - 1; i >= 0; i--) {
            CodeFoldRegion region = structureRegions.get(i);
            if (wasRemoved(region.startLine, startLine, removedEnd)
                || wasRemoved(region.endLine, startLine, removedEnd)
                || wasRemoved(structureRegionEmitLines.get(i), startLine, removedEnd)) {
                structureRegions.removeIndex(i);
                structureRegionEmitLines.removeIndex(i);
            }
        }
        if (delta != 0) {
            for (int i = 0; i < structureRegions.size; i++) {
                CodeFoldRegion region = structureRegions.get(i);
                int start = region.startLine >= removedEnd ? region.startLine + delta : region.startLine;
                int end = region.endLine >= removedEnd ? region.endLine + delta : region.endLine;
                if (start != region.startLine || end != region.endLine) {
                    structureRegions.set(i, new CodeFoldRegion(start, end, region.depth));
                }
                int emit = structureRegionEmitLines.get(i);
                if (emit >= removedEnd) {
                    structureRegionEmitLines.set(i, emit + delta);
                }
            }
        }

        spliceIndentLevels(startLine, removedCount, insertedCount);

        int touchedEnd = startLine + Math.max(0, insertedCount - 1);
        // Shift the old end of the dirty span, then extend it, but never past what still exists: an old
        // end that sat inside the removed range refers to a line that is gone.
        if (structureDirtyTo >= removedEnd) {
            structureDirtyTo += delta;
        } else if (structureDirtyTo >= startLine) {
            structureDirtyTo = touchedEnd;
        }
        structureDirtyTo = Math.max(structureDirtyTo, touchedEnd);
        structureDirtyFrom = Math.min(structureDirtyFrom, startLine);
    }

    /**
     * Keeps {@link #indentLevels} exactly the document's length across an edit.
     *
     * <p>Inserted slots are left at zero rather than guessed. They are inside the dirty span, so the next
     * pass overwrites them before anything reads them.
     */
    private void spliceIndentLevels(int startLine, int removedCount, int insertedCount) {
        if (startLine < 0 || startLine > indentLevels.length) {
            invalidateStructureCache();
            return;
        }
        int removable = Math.min(removedCount, indentLevels.length - startLine);
        if (removable == insertedCount) {
            return;
        }
        int newLength = indentLevels.length - removable + insertedCount;
        int[] resized = new int[newLength];
        System.arraycopy(indentLevels, 0, resized, 0, startLine);
        int tailFrom = startLine + removable;
        int tailTo = startLine + insertedCount;
        System.arraycopy(indentLevels, tailFrom, resized, tailTo, indentLevels.length - tailFrom);
        indentLevels = resized;
    }

    /** Drops the incremental structure cache, so the next pass scans the whole document. */
    private void invalidateStructureCache() {
        structureCacheValid = false;
        structureCheckpoints.clear();
        structureScanner = null;
        structureDirtyFrom = Integer.MAX_VALUE;
        structureDirtyTo = -1;
    }


    /**
     * Rebuilds the derived fold structures from {@link #structureRegions}, whichever pass produced it.
     */
    private void applyStructureResults(int lineCount, Array<String> lines) {
        if (indentLevels.length != lineCount) {
            int[] resized = new int[lineCount];
            System.arraycopy(indentLevels, 0, resized, 0, Math.min(indentLevels.length, lineCount));
            indentLevels = resized;
        }

        foldRegions.clear();
        foldRegionsByStart.clear();
        foldDepthsByEndLine.clear();
        // Symbols are deliberately left alone: they are on their own timer and clearing them here would
        // blank an outline on every keystroke. runSymbolAnalysis replaces them wholesale when it runs.
        for (CodeFoldRegion region : structureRegions) {
            // The end bound matters on the incremental path in a way it never did on the full one: a
            // spliced region can outlive the lines it described, and FoldRegion reads both line texts.
            if (region.endLine > region.startLine && region.endLine < lineCount) {
                foldRegions.add(new FoldRegion(region.startLine, region.endLine, region.depth, lines));
            }
        }
        for (FoldRegion region : foldRegions) {
            FoldRegion current = foldRegionsByStart.get(region.startLine);
            if (current == null || region.endLine > current.endLine) {
                foldRegionsByStart.put(region.startLine, region);
            }
            IntArray depths = foldDepthsByEndLine.get(region.endLine);
            if (depths == null) {
                depths = new IntArray(2);
                foldDepthsByEndLine.put(region.endLine, depths);
            }
            if (depths.indexOf(region.depth) < 0) {
                depths.add(region.depth);
            }
        }

        structureAnalyzedVersion = document.getVersion();
        structureAnalysisPending = false;
        structureAnalysisDelayRemaining = 0f;
        pruneStaleCollapsedLines();
        // Indent levels feed guide rendering, so cached layouts hold stale depths.
        discardAllLineLayouts();
        resizeLineArrays(lineCount);
    }

    /**
     * Drops collapsed start lines that no longer begin a foldable region.
     *
     * <p>An edit can delete the header a fold referred to, leaving an entry pointing at a line that is
     * no longer foldable. Such an entry hides nothing — {@code rebuildHiddenLines} only consults regions
     * the provider reported — but it would resurrect the fold if a region later happened to start there.
     * Only safe to call right after a structure analysis, when the region set describes the current text.
     */
    private void pruneStaleCollapsedLines() {
        for (int i = collapsedLines.size - 1; i >= 0; i--) {
            if (foldRegionsByStart.get(collapsedLines.get(i)) == null) {
                collapsedLines.removeIndex(i);
            }
        }
    }

    /** Runs the deferred structure pass once the document has been quiet briefly. */
    private void updateStructureAnalysisDebounce(float delta) {
        if (!structureAnalysisPending) {
            return;
        }
        structureAnalysisDelayRemaining -= delta;
        if (structureAnalysisDelayRemaining > 0f) {
            return;
        }
        runStructureAnalysis();
        rebuildHiddenLines(document.getLineCount());
        rebuildVisualRows(document.getLineCount());
        clampScroll();
    }

    /** Forces a pending structure pass to run now, for operations that need exact fold regions. */
    private void flushStructureAnalysis() {
        if (structureAnalysisPending || structureAnalyzedVersion != document.getVersion()) {
            runStructureAnalysis();
            rebuildHiddenLines(document.getLineCount());
            rebuildVisualRows(document.getLineCount());
        }
    }

    /**
     * Whether a structure analysis is waiting for the document to go quiet. While true, fold regions
     * and indent depths describe a slightly older version of the text.
     */
    public boolean isStructureAnalysisPending() {
        return structureAnalysisPending;
    }

    /**
     * First line whose cached lexer state still needs revalidating, or the line count when the cache is
     * fully valid. Exposed for diagnostics: if this sits far below the line count while you type, the
     * highlighter's state is not converging and every edit is re-scanning the tail of the document.
     */
    public int getHighlightResyncLine() {
        return Math.min(highlightResyncFrom, document.getLineCount());
    }

    /** Highest line whose lexer state has been computed at least once for this document. */
    public int getHighlightPopulatedLine() {
        return highlightStatesPopulatedThrough;
    }

    /** Number of lines whose layout is currently materialized, bounded by the internal cache cap. */
    public int getMaterializedLineCount() {
        return materializedLineCount;
    }

    /**
     * Whether a full wrap re-measure is waiting for the wrap width to settle. True while a window resize
     * is in progress on a large document; row counts are slightly stale until it clears.
     */
    public boolean isWrapRemeasurePending() {
        return wrapRemeasurePending;
    }

    /**
     * Whether visual row N is document line N. True when wrapping is off and nothing is collapsed, which
     * lets the editor skip its per-line row arrays entirely.
     */
    public boolean isRowMappingIdentity() {
        ensureLayout();
        return rowMappingIsIdentity;
    }

    /**
     * Total visual rows in the document: the line count unless wrapping splits lines or folding hides
     * them. This is what the vertical scroll extent is measured in.
     */
    public int getVisualRowCount() {
        ensureLayout();
        return totalVisualRows;
    }

    /**
     * How many rows have been drawn with a segment index their line's layout did not have, since
     * construction or the last {@link #resetRowSegmentClampCount()}.
     *
     * <p>Always zero in a healthy editor. Anything else means {@link #countWrapRows(int, int)} and
     * {@link #wrapLine(LineLayout, int)} disagreed about a line's row count — the condition that used to
     * throw {@code IndexOutOfBoundsException} out of {@code drawRows}. The row is now drawn with the
     * nearest segment the layout does have, so a non-zero value here is the same bug arriving as a
     * glitch, and is worth reporting rather than swallowing.
     *
     * <p>Only meaningful once the editor has been drawn at least once, since that is what populates the
     * visible rows.
     */
    public long getRowSegmentClampCount() {
        return rowSegmentClampCount;
    }

    /** Clears {@link #getRowSegmentClampCount()}, so a caller can check one interaction at a time. */
    public void resetRowSegmentClampCount() {
        rowSegmentClampCount = 0L;
    }

    /**
     * How many visual rows {@code line} occupies. Always 1 with wrapping off; more when the line wraps.
     * Returns 0 for a line hidden inside a collapsed fold.
     */
    public int getVisualRowsForLine(int line) {
        if (line < 0 || line >= document.getLineCount()) {
            return 0;
        }
        ensureLayout();
        if (isHiddenLine(line)) {
            return 0;
        }
        if (rowMappingIsIdentity) {
            return 1;
        }
        return line < visualRowsPerLine.length ? visualRowsPerLine[line] : 1;
    }

    /**
     * Lines the last structure analysis actually scanned.
     *
     * <p>The diagnostic that says whether structure analysis is staying incremental. After an ordinary edit
     * this should be a couple of hundred lines whatever the document's size; if it keeps coming back near
     * {@link #getLineCount()} while you type, the provider's state is not converging — most often because
     * it encodes something per-line in its state, so no two lines ever compare equal.
     *
     * <p>A whole-document pass reports the line count. A pass that had nothing to do reports 0.
     *
     * <p>Two shapes of edit legitimately re-scan a long way and are not a sign of anything wrong. One is a
     * change that really does alter every line below it, such as opening a block comment. The other is an
     * edit on the very line a still-open block opens on: the edit journal is line-granular, so whether that
     * block's opening token survived and where it moved to cannot be known, and every checkpoint below it
     * still describes the old start line, so none of them can match.
     */
    public int getStructureScanLineCount() {
        return lastStructureScanLines;
    }

    /**
     * Runs a deferred structure analysis immediately. Call this before code that depends on exact fold
     * regions or indent depths, such as programmatic folding right after an edit.
     */
    public void refreshStructureNow() {
        flushStructureAnalysis();
    }

    /**
     * Marks cached lexer state from {@code line} onward as needing revalidation.
     *
     * <p>Does not throw the later states away. Editing a line usually leaves the state at the start of
     * the next line unchanged, so revalidation stops as soon as a recomputed state matches what is
     * already stored — see {@link #resyncHighlightState(int, int)}. That is what makes jumping to line
     * 90,000 after an edit near the top cheap instead of a full re-scan.
     */
    private void invalidateHighlightStateFrom(int line) {
        int from = Math.max(0, line);
        highlightResyncFrom = Math.min(highlightResyncFrom, from);
        if (from == 0) {
            highlightStatesPopulatedThrough = 0;
        }
    }

    /** Grows {@link #lineStartStates} to hold a state for every line plus the end sentinel. */
    private void ensureHighlightStateCapacity(int lineCount) {
        if (lineStartStates.length >= lineCount + 1) {
            return;
        }
        int[] grown = new int[Math.max(lineCount + 1, lineStartStates.length * 2)];
        System.arraycopy(lineStartStates, 0, grown, 0, lineStartStates.length);
        lineStartStates = grown;
    }

    /** Lexer state at the start of {@code line}, revalidating only as far as necessary. */
    private int highlightStateAt(int line) {
        if (line <= 0) {
            return IncrementalCodeHighlighter.START_STATE;
        }
        IncrementalCodeHighlighter incremental = incrementalHighlighter();
        if (incremental == null) {
            return IncrementalCodeHighlighter.START_STATE;
        }
        ensureHighlightStateCapacity(document.getLineCount());
        int target = Math.min(line, document.getLineCount());
        resyncHighlightState(target, Integer.MAX_VALUE);
        return lineStartStates[target];
    }

    /**
     * Brings cached lexer state up to date as far as {@code target}, scanning at most {@code budget}
     * lines.
     *
     * <p>Two things bound the work. Lines below {@link #highlightResyncFrom} are already valid, so
     * nothing is redone. And from the resync point the scan stops early as soon as a recomputed state
     * matches the stored one for a line that was already populated, because from there on every later
     * state must be unchanged too. A normal edit converges within a line or two; only something that
     * genuinely changes every following line, such as opening a block comment, runs to {@code budget}
     * and resumes next frame.
     */
    private void resyncHighlightState(int target, int budget) {
        IncrementalCodeHighlighter incremental = incrementalHighlighter();
        if (incremental == null) {
            return;
        }
        int lineCount = document.getLineCount();
        int limit = Math.min(target, lineCount);
        if (highlightResyncFrom >= limit && highlightStatesPopulatedThrough >= limit) {
            return;
        }

        ensureHighlightStateCapacity(lineCount);
        int current = Math.min(highlightResyncFrom, highlightStatesPopulatedThrough);
        int state = lineStartStates[current];
        int scanned = 0;

        while (current < limit) {
            if (scanned >= budget) {
                // Out of budget: remember where to continue and leave the rest for a later frame.
                highlightResyncFrom = current;
                highlightStatesPopulatedThrough = Math.max(highlightStatesPopulatedThrough, current);
                return;
            }
            state = incremental.advanceState(document.getLineSequence(current), state, style);
            current++;
            scanned++;

            boolean wasPopulated = current <= highlightStatesPopulatedThrough;
            if (wasPopulated && lineStartStates[current] == state && current > highlightResyncFrom) {
                // Converged: the stored state for this line already matches, so everything after it is
                // still correct and needs no rescan.
                highlightResyncFrom = lineCount;
                return;
            }
            lineStartStates[current] = state;
        }

        highlightStatesPopulatedThrough = Math.max(highlightStatesPopulatedThrough, current);
        highlightResyncFrom = Math.max(highlightResyncFrom, current);
    }

    /**
     * Keeps lexer state current for the visible region, a bounded number of lines per frame, so a change
     * that invalidates every following line cannot stall a keystroke.
     */
    private void updateHighlightStateBudget() {
        int needed = Math.min(document.getLineCount(), getLastVisibleLine() + HIGHLIGHT_MARGIN_LINES);
        resyncHighlightState(needed, HIGHLIGHT_STATE_BUDGET_PER_FRAME);
    }

    private IncrementalCodeHighlighter incrementalHighlighter() {
        return highlighter instanceof IncrementalCodeHighlighter
            ? (IncrementalCodeHighlighter) highlighter
            : null;
    }

    /**
     * Builds the colour tokens for one line. Uses the incremental highlighter when available so only
     * this line is scanned; otherwise falls back to the whole-document API, which is why a
     * non-incremental highlighter stays correct but slow on large files.
     */
    /**
     * Fills in one line's colour tokens and bracket-ignore ranges from a single lexer pass.
     *
     * <p>With an {@link IncrementalCodeHighlighter} only this line is scanned. A plain
     * {@link CodeHighlighter} still forces a whole-document highlight per version, so it stays
     * correct but does not benefit from the viewport laziness.
     */
    private void buildLineContent(int line, String displayLine, LineLayout layout) {
        if (passwordMode || displayLine.isEmpty()) {
            layout.tokens = new Array<>(0);
            return;
        }

        Array<CodeHighlightSpan> syntaxSpans = scratchHighlightSpans;
        syntaxSpans.clear();

        IncrementalCodeHighlighter incremental = incrementalHighlighter();
        if (incremental != null) {
            // Null ignore-span sink: bracket matching reads its own cache, so collecting them here
            // would be work no one consumes.
            incremental.highlightLine(displayLine, highlightStateAt(line), style, syntaxSpans, null);
        } else {
            ensureLegacyHighlight();
            if (line < legacyHighlightLines.size) {
                syntaxSpans.addAll(legacyHighlightLines.get(line));
            }
        }

        Array<CodeHighlightSpan> semanticSpans = scratchSemanticSpans;
        semanticSpans.clear();
        if (semanticHighlightEnabled && semanticTokenCount > 0) {
            buildSemanticSpansForLine(line, displayLine.length(), semanticSpans);
        }

        Array<CodeHighlightSpan> rainbowSpans = scratchRainbowSpans;
        rainbowSpans.clear();
        if (rainbowBracketsEnabled) {
            buildRainbowBracketSpansForLine(line, displayLine, rainbowSpans);
        }
        layout.tokens = buildHighlightTokens(displayLine, syntaxSpans, semanticSpans, rainbowSpans);
    }

    /**
     * Turns one line's semantic tokens into colour spans, clamped to the line.
     *
     * <p>Clamping matters because a token can outlive the exact text it described: an edit that only
     * shortens a line without changing the line count leaves the tokens in place, and an unclamped span
     * would then index past the end while drawing. A token whose start is already past the end is
     * skipped rather than clamped to an empty range.
     *
     * <p>Tokens whose colour resolves to null are skipped, which is how a type nobody themed leaves the
     * lexical colour showing rather than painting the identifier in the default font colour.
     */
    private void buildSemanticSpansForLine(int line, int lineLength, Array<CodeHighlightSpan> target) {
        Array<CodeSemanticToken> lineTokens = getSemanticTokensAtLine(line);
        if (lineTokens == null || lineLength == 0) {
            return;
        }
        for (int i = 0; i < lineTokens.size; i++) {
            CodeSemanticToken token = lineTokens.get(i);
            int start = token.startColumn;
            if (start >= lineLength) {
                continue;
            }
            int end = Math.min(token.endColumn, lineLength);
            if (end <= start) {
                continue;
            }
            Color color = getSemanticTokenColor(token);
            if (color == null) {
                continue;
            }
            target.add(new CodeHighlightSpan(start, end, color));
        }
    }

    /** Runs the legacy whole-document highlighter once per document version, for non-incremental highlighters. */
    private void ensureLegacyHighlight() {
        if (legacyHighlightVersion == document.getVersion()) {
            return;
        }
        Array<String> lines = document.sharedLineSnapshot();
        legacyHighlightLines = passwordMode
            ? new Array<Array<CodeHighlightSpan>>()
            : highlighter.highlight(lines, style);
        bracketIgnoreLines = passwordMode
            ? normalizeBracketIgnoreLines(new Array<Array<CodeBracketIgnoreSpan>>(), lines.size)
            : normalizeBracketIgnoreLines(highlighter.getBracketIgnoreSpans(lines), lines.size);
        legacyHighlightVersion = document.getVersion();
    }

    /** Grows {@link #measuredMaxLineWidth} as lines are measured; it never shrinks within a version. */
    private void noteMeasuredLineWidth(LineLayout layout) {
        float width = layout.measureRange(0, layout.text.length());
        if (width > measuredMaxLineWidth) {
            measuredMaxLineWidth = width;
            maxLineWidth = Math.max(maxLineWidth, width);
        }
    }

    /**
     * Estimates the horizontal extent from the document's longest line by character count, measuring
     * only that one line, and takes the larger of that and everything measured so far. Exact for
     * monospaced fonts; for proportional fonts a different line could be wider in pixels, and the
     * estimate corrects itself as lines are drawn.
     */
    private void updateMaxLineWidth() {
        int longest = document.getLongestLineIndex();
        if (longest != estimatedWidestLine || estimatedWidestLineWidth <= 0f) {
            estimatedWidestLine = longest;
            estimatedWidestLineWidth = longest >= 0 && longest < document.getLineCount()
                ? measureLineWidth(longest)
                : 0f;
        }
        maxLineWidth = Math.max(estimatedWidestLineWidth, measuredMaxLineWidth);
    }

    /**
     * Width of one line in pixels, without building its layout.
     *
     * <p>Deliberately does not call {@link #layoutFor(int)}: that would highlight the line, which needs
     * the lexer state at that line, which for a line far from the caret means scanning everything in
     * between. The horizontal extent only needs glyph advances, so this walks them directly and
     * allocates nothing.
     */
    private float measureLineWidth(int line) {
        CharSequence text = document.getLineSequence(line);
        float width = 0f;
        for (int i = 0; i < text.length(); i++) {
            char current = text.charAt(i);
            if (current == '\t') {
                width += getTabAdvanceAtWidth(width);
            } else {
                char next = i + 1 < text.length() ? text.charAt(i + 1) : 0;
                width += glyphAdvance(current, next);
            }
        }
        return width;
    }

    /** First document line that could be on screen. */
    private int getFirstVisibleLine() {
        if (!rowMappingIsIdentity && visualRowStart.length == 0) {
            return 0;
        }
        int startRow = Math.max(0, (int) Math.floor(scrollY / lineHeight));
        return findLineByVisualRow(startRow);
    }

    /** Last document line that could be on screen. */
    private int getLastVisibleLine() {
        if (!rowMappingIsIdentity && visualRowStart.length == 0) {
            return 0;
        }
        int endRow = Math.min(totalVisualRows - 1, (int) Math.ceil((scrollY + getContentHeight()) / lineHeight));
        return findLineByVisualRow(Math.max(0, endRow));
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

        // Same value countWrapRows subtracts, from the same helper, so the two agree on the row count.
        // Cached on the layout because the draw and hit-test paths need the same number as an x offset.
        layout.continuationIndent = continuationIndentWidth(text, wrapWidth);
        int segmentStart = 0;
        int rows = 0;
        while (segmentStart < text.length()) {
            rows++;
            float available = rows > 1 ? Math.max(1f, wrapWidth - layout.continuationIndent) : wrapWidth;
            int bestBreak = -1;
            int index = segmentStart;

            while (index < text.length()) {
                float segmentWidth = layout.measureRange(segmentStart, index + 1);
                if (segmentWidth > available) {
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

    /**
     * Flattens overlapping colour spans into non-overlapping tokens, highest priority winning.
     *
     * <p>Still O(boundaries × spans), which is fine because both are small for one line, but the
     * scratch buffers mean drawing a line allocates only the token array.
     *
     * <p>The priority order is lexical, then semantic, then rainbow brackets. Semantic above lexical is
     * the whole point: it is the more informed answer about the same identifier. Rainbow above semantic
     * is not a claim that bracket colouring matters more, it is that the two do not compete — semantic
     * tokens cover identifiers and literals, brackets are punctuation — so on the rare overlap the
     * narrower, single-character span is the one the user asked for explicitly.
     */
    private Array<HighlightToken> buildHighlightTokens(
        String text,
        Array<CodeHighlightSpan> syntaxSpans,
        Array<CodeHighlightSpan> semanticSpans,
        Array<CodeHighlightSpan> overlaySpans
    ) {
        int length = text.length();
        if (length == 0) {
            return new Array<>(0);
        }

        Array<ColorSpan> spans = scratchColorSpans;
        spans.clear();
        collectColorSpans(spans, syntaxSpans, length, 0);
        collectColorSpans(spans, semanticSpans, length, 1);
        collectColorSpans(spans, overlaySpans, length, 2);
        if (spans.size == 0) {
            return new Array<>(0);
        }

        IntArray boundaries = scratchBoundaries;
        boundaries.clear();
        boundaries.add(0);
        boundaries.add(length);
        for (int i = 0; i < spans.size; i++) {
            boundaries.add(spans.get(i).start);
            boundaries.add(spans.get(i).end);
        }
        boundaries.sort();

        IntArray uniqueBoundaries = scratchUniqueBoundaries;
        uniqueBoundaries.clear();
        for (int i = 0; i < boundaries.size; i++) {
            int value = boundaries.get(i);
            if (uniqueBoundaries.size == 0 || uniqueBoundaries.get(uniqueBoundaries.size - 1) != value) {
                uniqueBoundaries.add(value);
            }
        }

        Array<HighlightToken> tokens = new Array<>(uniqueBoundaries.size);
        for (int i = 0; i < uniqueBoundaries.size - 1; i++) {
            int start = uniqueBoundaries.get(i);
            int end = uniqueBoundaries.get(i + 1);
            if (start >= end) {
                continue;
            }

            Color selectedColor = null;
            int selectedPriority = Integer.MIN_VALUE;
            for (int s = 0; s < spans.size; s++) {
                ColorSpan span = spans.get(s);
                // Spans are appended in ascending start order per source, but the two sources
                // interleave, so this cannot break early on start alone.
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

    /**
     * Rainbow bracket colours for one line. A closing bracket's colour depends on the whole bracket
     * stack above it, so the stack is checkpointed every
     * {@link #BRACKET_CHECKPOINT_INTERVAL} lines and replayed from the nearest checkpoint, bounding
     * the scan instead of walking the document.
     */
    private void buildRainbowBracketSpansForLine(int line, String displayLine, Array<CodeHighlightSpan> target) {
        Array<BracketFrame> stack = bracketStackAtLineStart(line);
        scanRainbowBracketsInLine(line, displayLine, stack, target);
    }

    /**
     * Replays bracket state from the nearest stored checkpoint up to the start of {@code line},
     * storing any checkpoints it passes so the next call starts closer. Checkpoints are truncated at
     * the edited line, so an edit invalidates only what follows it.
     */
    private Array<BracketFrame> bracketStackAtLineStart(int line) {
        scratchBracketStack.clear();
        if (bracketCheckpoints.isEmpty()) {
            bracketCheckpoints.add(new IntArray(0));
        }

        int checkpoint = Math.min(line / BRACKET_CHECKPOINT_INTERVAL, bracketCheckpoints.size - 1);
        unpackBracketStack(bracketCheckpoints.get(checkpoint), scratchBracketStack);
        int current = checkpoint * BRACKET_CHECKPOINT_INTERVAL;

        while (current < line) {
            scanRainbowBracketsInLine(
                current,
                getDisplayLineText(document.getLine(current)),
                scratchBracketStack,
                null
            );
            current++;
            if (current % BRACKET_CHECKPOINT_INTERVAL == 0) {
                int index = current / BRACKET_CHECKPOINT_INTERVAL;
                if (index == bracketCheckpoints.size) {
                    bracketCheckpoints.add(packBracketStack(scratchBracketStack));
                }
            }
        }
        return scratchBracketStack;
    }

    private static void unpackBracketStack(IntArray packed, Array<BracketFrame> target) {
        for (int i = 0; i < packed.size; i++) {
            int value = packed.get(i);
            target.add(new BracketFrame((char) (value >>> 16), value & 0xFFFF));
        }
    }

    private static IntArray packBracketStack(Array<BracketFrame> stack) {
        IntArray packed = new IntArray(stack.size);
        for (int i = 0; i < stack.size; i++) {
            BracketFrame frame = stack.get(i);
            packed.add((frame.open << 16) | (frame.depth & 0xFFFF));
        }
        return packed;
    }

    /** Drops bracket checkpoints that an edit at {@code line} could have invalidated. */
    private void invalidateBracketCheckpointsFrom(int line) {
        int keep = Math.max(1, line / BRACKET_CHECKPOINT_INTERVAL + 1);
        while (bracketCheckpoints.size > keep) {
            bracketCheckpoints.pop();
        }
    }

    /**
     * Scans one line for brackets, updating {@code stack} and optionally emitting spans.
     *
     * <p>Tests the ignore cache only for characters that are actually brackets, and reads it through
     * {@link #isIgnoredBracketPosition(int, int)} so no per-line span array is allocated.
     */
    private void scanRainbowBracketsInLine(
        int line,
        String text,
        Array<BracketFrame> stack,
        Array<CodeHighlightSpan> target
    ) {
        for (int column = 0; column < text.length(); column++) {
            char current = text.charAt(column);
            int bracketIndex = BRACKET_CHARACTERS.indexOf(current);
            if (bracketIndex < 0) {
                continue;
            }
            if (isIgnoredBracketPosition(line, column)) {
                continue;
            }

            if (bracketIndex % 2 == 0) {
                int depth = stack.size;
                if (target != null) {
                    target.add(new CodeHighlightSpan(column, column + 1, getRainbowBracketColor(depth)));
                }
                stack.add(new BracketFrame(current, depth));
                continue;
            }

            char expectedOpen = BRACKET_CHARACTERS.charAt(bracketIndex - 1);
            int matchIndex = findMatchingOpenBracketIndex(stack, expectedOpen);
            int depth = matchIndex >= 0 ? stack.get(matchIndex).depth : Math.max(0, stack.size - 1);
            if (target != null) {
                target.add(new CodeHighlightSpan(column, column + 1, getRainbowBracketColor(depth)));
            }
            if (matchIndex >= 0) {
                while (stack.size > matchIndex) {
                    stack.pop();
                }
            }
        }
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

    /**
     * Keeps {@link #searchMatches} in step with the document, rescanning only the lines an edit
     * touched. Search needs every match, not just visible ones, because find-next walks the whole
     * document; the saving is that untouched lines are not re-searched and that nothing at all
     * happens while no search is active.
     */
    private void refreshSearchMatches() {
        if (searchText == null || searchText.isEmpty()) {
            if (searchMatchCount != 0 || searchMatches.size != 0) {
                searchMatches.clear();
                flatSearchMatches.clear();
                searchMatchCount = 0;
                clearCurrentSearchMatch();
            }
            searchAnalyzedVersion = document.getVersion();
            return;
        }

        int lineCount = document.getLineCount();
        if (searchAnalyzedVersion == document.getVersion()
            && searchMatches.size == lineCount
            && searchDirtyFrom > searchDirtyTo) {
            return;
        }

        int previousCurrentLine = currentSearchMatchLine;
        int previousCurrentStart = currentSearchMatchStart;
        int previousCurrentEnd = currentSearchMatchEnd;

        // An invalid pattern yields no matches rather than an exception; findMatchesInLine checks this.
        ensureSearchPattern();

        // Splices keep searchMatches aligned and leave nulls for new lines; a size mismatch means
        // the alignment was lost and everything has to be re-searched.
        if (searchMatches.size != lineCount) {
            searchMatches.clear();
            for (int i = 0; i < lineCount; i++) {
                searchMatches.add(null);
            }
            searchDirtyFrom = Integer.MAX_VALUE;
            searchDirtyTo = -1;
        }

        int from = Math.max(0, Math.min(searchDirtyFrom, lineCount - 1));
        int to = Math.min(lineCount - 1, searchDirtyTo);
        for (int line = from; line <= to; line++) {
            searchMatches.set(line, findMatchesInLine(line, document.getLineSequence(line)));
        }
        for (int line = 0; line < lineCount; line++) {
            if (searchMatches.get(line) == null) {
                searchMatches.set(line, findMatchesInLine(line, document.getLineSequence(line)));
            }
        }

        flatSearchMatches.clear();
        searchMatchCount = 0;
        for (int line = 0; line < lineCount; line++) {
            Array<SearchMatch> lineMatches = searchMatches.get(line);
            for (int i = 0; i < lineMatches.size; i++) {
                flatSearchMatches.add(new SearchMatchRef(line, lineMatches.get(i)));
                searchMatchCount++;
            }
        }

        searchDirtyFrom = Integer.MAX_VALUE;
        searchDirtyTo = -1;
        searchAnalyzedVersion = document.getVersion();
        restoreCurrentSearchMatch(previousCurrentLine, previousCurrentStart, previousCurrentEnd);
    }

    /**
     * Finds every occurrence of the search text in one line. The literal path allocates no lowercased
     * copy; the regex path reuses one {@link Matcher} across all lines.
     */
    private Array<SearchMatch> findMatchesInLine(int line, CharSequence text) {
        Array<SearchMatch> matches = new Array<>(0);
        int from = searchRangeStartColumn(line);
        int to = searchRangeEndColumn(line, text.length());
        if (from > to) {
            return matches;
        }
        if (searchRegexEnabled) {
            findRegexMatchesInLine(text, from, to, matches);
            return matches;
        }
        int needleLength = searchText.length();
        if (needleLength == 0) {
            return matches;
        }
        int limit = to - needleLength;
        for (int start = from; start <= limit; start++) {
            if (!regionMatchesSearch(text, start)) {
                continue;
            }
            int end = start + needleLength;
            if (searchWholeWord && !isWholeWordMatch(text, start, end)) {
                // A longer identifier can still start one character later, so do not skip ahead.
                continue;
            }
            matches.add(new SearchMatch(start, end));
            // Matches never overlap; the loop's own increment covers the last character.
            start = end - 1;
        }
        return matches;
    }

    /**
     * Applies {@link #searchPattern} to a whole line, then keeps only the matches inside
     * {@code [from, to)}. The pattern runs over the full line even when a range is set, so {@code ^}
     * and {@code $} keep meaning "line start" and "line end" instead of shifting with the selection.
     */
    private void findRegexMatchesInLine(CharSequence text, int from, int to, Array<SearchMatch> matches) {
        if (searchPattern == null) {
            return;
        }
        if (searchMatcher == null) {
            searchMatcher = searchPattern.matcher(text);
        } else {
            searchMatcher.reset(text);
        }
        int search = 0;
        int length = text.length();
        while (search <= length && searchMatcher.find(search)) {
            int start = searchMatcher.start();
            int end = searchMatcher.end();
            if (end == start) {
                // A pattern such as "a*" can match nothing; without this the scan would never advance.
                search = start + 1;
            } else {
                search = end;
            }
            if (start < from || end > to) {
                continue;
            }
            if (end == start) {
                // Zero-length hits have nothing to highlight or replace.
                continue;
            }
            if (searchWholeWord && !isWholeWordMatch(text, start, end)) {
                continue;
            }
            matches.add(new SearchMatch(start, end));
        }
    }

    /** Whether {@code replacement} can contain group references worth resolving per match. */
    private boolean needsReplacementExpansion(String replacement) {
        return searchRegexEnabled
            && replacement != null
            && !replacement.isEmpty()
            && (replacement.indexOf('$') >= 0 || replacement.indexOf('\\') >= 0);
    }

    /** Whether the characters flanking {@code [start, end)} are non-word, so the hit stands alone. */
    private boolean isWholeWordMatch(CharSequence text, int start, int end) {
        if (start > 0 && isWordChar(text.charAt(start - 1))) {
            return false;
        }
        return end >= text.length() || !isWordChar(text.charAt(end));
    }

    /** First column of {@code line} that {@link #searchRange} allows a match to start at. */
    private int searchRangeStartColumn(int line) {
        if (searchRange == null) {
            return 0;
        }
        if (line < searchRange.startLine) {
            return Integer.MAX_VALUE;
        }
        return line == searchRange.startLine ? Math.max(0, searchRange.startColumn) : 0;
    }

    /** Column of {@code line} that {@link #searchRange} requires a match to end at or before. */
    private int searchRangeEndColumn(int line, int lineLength) {
        if (searchRange == null) {
            return lineLength;
        }
        if (line > searchRange.endLine) {
            return -1;
        }
        return line == searchRange.endLine ? Math.min(lineLength, searchRange.endColumn) : lineLength;
    }

    /** Compiles {@link #searchText} on demand. A failure leaves the pattern null and records why. */
    private void ensureSearchPattern() {
        if (!searchPatternDirty) {
            return;
        }
        searchPatternDirty = false;
        searchPattern = null;
        searchMatcher = null;
        searchRegexError = null;
        if (!searchRegexEnabled || searchText.isEmpty()) {
            return;
        }
        try {
            searchPattern = Pattern.compile(searchText,
                searchCaseSensitive ? 0 : Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
        } catch (PatternSyntaxException error) {
            searchRegexError = error.getDescription() == null ? error.getMessage() : error.getDescription();
            if (searchRegexError == null) {
                searchRegexError = "invalid pattern";
            }
        }
    }

    /**
     * Expands {@code $1} group references and backslash escapes in {@code replacement} against one
     * match, the way {@link Matcher#appendReplacement} does.
     *
     * <p>Only meaningful in regex mode. The pattern is re-run on the match's own line to recover the
     * groups, which costs one line scan per replacement but keeps the search itself from having to
     * retain a group array for every hit in the document. Falls back to the literal replacement if
     * the pattern no longer matches at that exact offset, or if the reference is malformed.
     */
    private String expandReplacement(String replacement, SearchMatchRef target) {
        if (target == null || !needsReplacementExpansion(replacement)) {
            return replacement;
        }
        ensureSearchPattern();
        if (searchPattern == null) {
            return replacement;
        }
        if (target.line < 0 || target.line >= document.getLineCount()) {
            return replacement;
        }
        CharSequence text = document.getLineSequence(target.line);
        if (target.match.start < 0 || target.match.end > text.length()) {
            return replacement;
        }
        Matcher matcher = searchPattern.matcher(text);
        if (!matcher.find(target.match.start)
            || matcher.start() != target.match.start
            || matcher.end() != target.match.end) {
            return replacement;
        }
        StringBuffer buffer = new StringBuffer(text.length() + replacement.length());
        try {
            matcher.appendReplacement(buffer, replacement);
        } catch (RuntimeException error) {
            // A dangling "$" or a reference to a group the pattern does not have; take it literally.
            return replacement;
        }
        // appendReplacement copied text before the match first, so the expansion is what follows it.
        return buffer.substring(target.match.start);
    }

    private boolean regionMatchesSearch(CharSequence line, int offset) {
        for (int i = 0; i < searchText.length(); i++) {
            char a = line.charAt(offset + i);
            char b = searchText.charAt(i);
            if (a != b) {
                if (searchCaseSensitive) {
                    return false;
                }
                if (Character.toLowerCase(a) != Character.toLowerCase(b)) {
                    return false;
                }
            }
        }
        return true;
    }

    /** Marks a line range as needing a search rescan. */
    private void markSearchDirty(int fromLine, int toLine) {
        searchDirtyFrom = Math.min(searchDirtyFrom, fromLine);
        searchDirtyTo = Math.max(searchDirtyTo, toLine);
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
        if (document.isCompoundEditInProgress()) {
            // Inside a group the closing endCompoundEdit() does this once. Refreshing per mutation
            // would rebuild the flat search match list and re-notify listeners on every edit, which
            // turns a long run of programmatic edits quadratic. Anything that needs a current layout
            // before the group closes calls ensureLayout() itself, and the query API already does.
            resetPreferredColumn();
            refreshBlink();
            return;
        }
        // Deliberately no invalidateLayout(): that drops every cached line, which would defeat the
        // incremental sync. ensureLayout() notices the new document version and patches the arrays
        // from the edit journal instead.
        ensureLayout();
        ensureCursorVisible();
        resetPreferredColumn();
        refreshBlink();
        notifyContentChanged();
    }

    private void onDocumentMutatedDeferred() {
        // No invalidateLayout() for the same reason as onDocumentMutated: the deferred
        // flushDeferredMutationProcessing() calls ensureLayout(), which patches incrementally.
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
            resetPendingContentRange();
            return;
        }
        CodeEditorContentChangeType type = pendingContentChangeType;
        pendingContentChangeType = CodeEditorContentChangeType.UNKNOWN;

        int lastLine = Math.max(0, document.getLineCount() - 1);
        int startLine;
        int endLine;
        if (pendingChangeStartLine > pendingChangeEndLine) {
            // No range was recorded, e.g. setText: report the whole document.
            startLine = 0;
            endLine = lastLine;
        } else {
            startLine = Math.max(0, Math.min(pendingChangeStartLine, lastLine));
            endLine = Math.max(startLine, Math.min(pendingChangeEndLine, lastLine));
        }

        CodeEditorContentChangeEvent event = new CodeEditorContentChangeEvent(
            this,
            type,
            document.getVersion(),
            document.getCursorLine(),
            document.getCursorColumn(),
            startLine,
            endLine,
            pendingChangeRemovedLines,
            pendingChangeInsertedLines
        );
        resetPendingContentRange();
        // Over a snapshot, because a listener is allowed to edit the document and to remove itself.
        // Both happen: a snippet session propagates mirrors from this callback, which re-enters this
        // method, and ends itself from the same callback, which unregisters mid-notification. A
        // for-each over the live array throws on the first and skips a listener on the second.
        Array<CodeEditorContentListener> targets = borrowContentListenerSnapshot();
        try {
            for (int i = 0; i < targets.size; i++) {
                targets.get(i).onContentChanged(this, event);
            }
        } finally {
            releaseContentListenerSnapshot(targets);
        }
    }

    /**
     * A copy of the content listeners to notify.
     *
     * <p>Reuses one scratch array for the common case and allocates only when a listener's own edit has
     * re-entered notification, so the ordinary keystroke path allocates nothing.
     */
    private Array<CodeEditorContentListener> borrowContentListenerSnapshot() {
        if (contentNotifyDepth++ == 0) {
            contentListenerScratch.clear();
            contentListenerScratch.addAll(contentListeners);
            return contentListenerScratch;
        }
        Array<CodeEditorContentListener> nested = new Array<CodeEditorContentListener>(contentListeners.size);
        nested.addAll(contentListeners);
        return nested;
    }

    private void releaseContentListenerSnapshot(Array<CodeEditorContentListener> snapshot) {
        contentNotifyDepth--;
        if (snapshot == contentListenerScratch) {
            contentListenerScratch.clear();
        }
    }

    /** Widens the range reported by the next content-change event. */
    private void notePendingContentRange(int startLine, int endLine, int removedCount, int insertedCount) {
        pendingChangeStartLine = Math.min(pendingChangeStartLine, startLine);
        pendingChangeEndLine = Math.max(pendingChangeEndLine, endLine);
        pendingChangeRemovedLines += removedCount;
        pendingChangeInsertedLines += insertedCount;
    }

    /** Reports the next content change as covering everything, when the range could not be tracked. */
    private void markWholeDocumentChanged() {
        pendingChangeStartLine = 0;
        pendingChangeEndLine = Math.max(0, document.getLineCount() - 1);
        pendingChangeRemovedLines = 0;
        pendingChangeInsertedLines = document.getLineCount();
    }

    private void resetPendingContentRange() {
        pendingChangeStartLine = Integer.MAX_VALUE;
        pendingChangeEndLine = -1;
        pendingChangeRemovedLines = 0;
        pendingChangeInsertedLines = 0;
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

    public void selectWordAtCursor() {
        int line = document.getCursorLine();
        int column = document.getCursorColumn();
        String text = document.getLine(line);
        if (text.isEmpty()) {
            clearSelection();
            refreshBlink();
            return;
        }

        int pivot = Math.min(column, Math.max(0, text.length() - 1));
        if (pivot > 0 && pivot == text.length()) {
            pivot--;
        }
        if (!isWordChar(text.charAt(pivot))) {
            clearSelection();
            refreshBlink();
            return;
        }

        int start = pivot;
        int end = pivot + 1;
        while (start > 0 && isWordChar(text.charAt(start - 1))) {
            start--;
        }
        while (end < text.length() && isWordChar(text.charAt(end))) {
            end++;
        }

        selectionAnchorLine = line;
        selectionAnchorColumn = start;
        document.moveCursorTo(line, end);
        ensureCursorVisible();
        showTransientCaretHandle();
        refreshBlink();
    }

    public void selectLineAtCursor() {
        int line = document.getCursorLine();
        int endColumn = document.getLineLength(line);
        selectionAnchorLine = line;
        selectionAnchorColumn = 0;
        document.moveCursorTo(line, endColumn);
        ensureCursorVisible();
        showTransientCaretHandle();
        refreshBlink();
    }

    private void clearSelection() {
        selectionAnchorLine = -1;
        selectionAnchorColumn = -1;
        draggingSelection = false;
        draggingCaretHandle = false;
        handleDragFixedLine = -1;
        handleDragFixedColumn = -1;
        handleDragPointerOffsetX = 0f;
        handleDragPointerOffsetY = 0f;
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
        expandCollapsedRegionContainingLine(ref.line);
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
        replaceSearchMatch(target, replacement, true);
    }

    /**
     * @param expandFolds whether to uncollapse a region hiding the match first. Replace All passes
     *     false: it expanded every affected region up front, and repeating the check per match costs
     *     a scan of all fold regions each time.
     */
    private void replaceSearchMatch(SearchMatchRef target, String replacement, boolean expandFolds) {
        if (target == null) {
            return;
        }
        if (expandFolds) {
            expandCollapsedRegionContainingLine(target.line);
        }
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

        scratchExpandLines.clear();
        for (SearchMatchRef match : matches) {
            if (match == null) {
                continue;
            }
            for (FoldRegion region : foldRegions) {
                if (region.collapsed && region.startLine < match.line && match.line <= region.endLine) {
                    scratchExpandLines.add(region.startLine);
                }
            }
        }

        if (scratchExpandLines.size == 0) {
            return;
        }

        float previousScroll = scrollY;
        removeCollapsedLines(scratchExpandLines);
        invalidateRowMapping();
        ensureLayout();
        scrollY = Math.max(getMinScroll(), Math.min(previousScroll, getMaxScroll()));
    }

    private boolean expandCollapsedRegionContainingLine(int line) {
        if (collapsedLines.size == 0) {
            // Cheap exit that matters: Replace All calls this once per match, and the loop below is
            // O(fold regions). Without it, a 100k line document with many folds turns Replace All
            // into a quadratic scan.
            return false;
        }
        scratchExpandLines.clear();
        for (FoldRegion region : foldRegions) {
            if (region.collapsed && region.startLine < line && line <= region.endLine) {
                scratchExpandLines.add(region.startLine);
            }
        }
        if (scratchExpandLines.size == 0) {
            return false;
        }

        float previousScroll = scrollY;
        removeCollapsedLines(scratchExpandLines);
        invalidateRowMapping();
        ensureLayout();
        scrollY = Math.max(getMinScroll(), Math.min(previousScroll, getMaxScroll()));
        return true;
    }

    private void removeCollapsedLines(IntArray startLines) {
        for (int i = 0; i < startLines.size; i++) {
            setCollapsed(startLines.get(i), false);
        }
    }

    private boolean isCollapsedStartLine(int line) {
        return collapsedLines.indexOf(line) >= 0;
    }

    /** Adds or removes a start line from the collapsed set, keeping it duplicate-free. */
    private void setCollapsed(int startLine, boolean collapsed) {
        int index = collapsedLines.indexOf(startLine);
        if (collapsed) {
            if (index < 0) {
                collapsedLines.add(startLine);
            }
        } else if (index >= 0) {
            collapsedLines.removeIndex(index);
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

    /**
     * Copies the selection to the clipboard.
     *
     * @return false when there is no selection, or when the platform refused the text — see
     *     {@link #writeToClipboard(String)}
     */
    public boolean copySelection() {
        String selected = getSelectedText();
        if (selected.isEmpty()) {
            return false;
        }
        return writeToClipboard(selected);
    }

    /**
     * Cuts the selection to the clipboard. The text is only deleted if the clipboard write succeeded,
     * so a refused copy cannot silently destroy the selection.
     */
    public boolean cutSelection() {
        if (disabled || readOnly) {
            return false;
        }
        String selected = getSelectedText();
        if (selected.isEmpty()) {
            return false;
        }
        if (!writeToClipboard(selected)) {
            return false;
        }
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
        String contents = readFromClipboard();
        String sanitized = sanitizeInputText(contents, true);
        if (sanitized.isEmpty()) {
            return false;
        }
        markPendingContentChange(CodeEditorContentChangeType.PASTE);
        replaceSelectionWith(sanitized, true);
        return true;
    }

    /**
     * Writes text to the system clipboard, reporting failure instead of propagating it.
     *
     * <p>Catching {@link Throwable} here is deliberate. The LWJGL3 backend encodes the string into a
     * thread-local {@code MemoryStack} whose default size is 64 KB, and throws
     * {@link OutOfMemoryError} — an {@code Error}, not an {@code Exception} — when the text does not
     * fit. Copying a large selection would otherwise kill the application from inside the frame loop,
     * even though nothing is actually wrong with the heap and the editor is perfectly able to carry on.
     * Raise {@code -Dorg.lwjgl.system.stackSize=<kilobytes>} on the desktop backend if you need to copy
     * multi-megabyte selections.
     *
     * @return true when the clipboard accepted the text
     */
    protected boolean writeToClipboard(String text) {
        if (text == null) {
            return false;
        }
        try {
            Gdx.app.getClipboard().setContents(text);
            return true;
        } catch (Throwable failure) {
            Gdx.app.error(
                "CodeEditor",
                "Clipboard rejected " + text.length() + " characters ("
                    + failure + "); raise -Dorg.lwjgl.system.stackSize to copy selections this large"
            );
            return false;
        }
    }

    /** Reads the system clipboard, returning an empty string when it is unavailable or fails. */
    protected String readFromClipboard() {
        try {
            String contents = Gdx.app.getClipboard().getContents();
            return contents == null ? "" : contents;
        } catch (Throwable failure) {
            Gdx.app.error("CodeEditor", "Clipboard read failed (" + failure + ")");
            return "";
        }
    }

    public boolean canUndo() {
        return document.canUndo();
    }

    public boolean canRedo() {
        return document.canRedo();
    }

    /**
     * Caps how many undo steps are kept, default {@link CodeDocument#DEFAULT_MAX_UNDO_ENTRIES}. A
     * compound group counts as one. Values below 1 are raised to 1; lowering the cap trims immediately.
     */
    public void setMaxUndoEntries(int entries) {
        document.setMaxUndoEntries(entries);
    }

    public int getMaxUndoEntries() {
        return document.getMaxUndoEntries();
    }

    /**
     * Caps the text the undo stack retains, default {@link CodeDocument#DEFAULT_MAX_UNDO_CHARS}. Both
     * this and {@link #setMaxUndoEntries(int)} apply; whichever binds first drops the oldest step.
     *
     * <p>The newest step is never dropped, so one edit larger than the whole budget still undoes once.
     */
    public void setMaxUndoChars(int chars) {
        document.setMaxUndoChars(chars);
    }

    public int getMaxUndoChars() {
        return document.getMaxUndoChars();
    }

    /** Undo steps on the stack. A compound group counts as one. */
    public int getUndoEntryCount() {
        return document.getUndoEntryCount();
    }

    public int getRedoEntryCount() {
        return document.getRedoEntryCount();
    }

    /** Approximate characters retained by the undo stack — the figure {@link #setMaxUndoChars(int)} bounds. */
    public int getUndoChars() {
        return document.getUndoChars();
    }

    /**
     * Name of the next undo step, or null when it has none. Set by
     * {@link #beginCompoundEdit(String)}, {@link #runAsSingleUndoStep(String, Runnable)} or
     * {@link #applyEdits(Array, String)}. Survives an undo/redo cycle.
     */
    public String getUndoLabel() {
        return document.getUndoLabel();
    }

    /** Name of the next redo step, or null when it has none. */
    public String getRedoLabel() {
        return document.getRedoLabel();
    }

    /**
     * Drops undo and redo history, leaving the text alone. For a host that has just saved, or that
     * loaded a document through the mutation API rather than {@code setText}.
     */
    public void clearUndoHistory() {
        document.clearUndoHistory();
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

    private void startDeleteKeyRepeat(int keycode, CodeEditorAction action) {
        repeatingDeleteKey = keycode;
        repeatingDeleteAction = action;
        repeatDeleteDelayRemaining = KEY_REPEAT_INITIAL_DELAY;
        repeatDeleteIntervalRemaining = KEY_REPEAT_INTERVAL;
    }

    private void stopDeleteKeyRepeat(int keycode) {
        if (repeatingDeleteKey == keycode) {
            repeatingDeleteKey = -1;
            repeatingDeleteAction = null;
        }
    }

    private void updateKeyRepeat(float delta) {
        if (repeatingDeleteKey == -1 || repeatingDeleteAction == null) {
            return;
        }
        if (disabled || readOnly || !isFocused()) {
            repeatingDeleteKey = -1;
            repeatingDeleteAction = null;
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
            if (!performDeleteAction(repeatingDeleteAction)) {
                repeatingDeleteKey = -1;
                repeatingDeleteAction = null;
                return;
            }
            repeatDeleteIntervalRemaining += KEY_REPEAT_INTERVAL;
        }
    }

    private boolean performDeleteAction(CodeEditorAction action) {
        if (action == CodeEditorAction.BACKSPACE) {
            expandCollapsedRegionsForEdit(EditIntent.BACKSPACE);
            markPendingContentChange(CodeEditorContentChangeType.DELETE);
            if (getSelectionRange() != null) {
                deleteSelectionIfPresent();
                onDocumentMutated();
                return true;
            }

            if (dispatchAutoEditBackspace()) {
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

        if (action == CodeEditorAction.DELETE) {
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

    /**
     * Segment index for a visual row, pinned to a segment the layout actually has, or {@code -1} for a
     * layout with no segments at all.
     *
     * <p>{@code drawRows} takes the index from the row mapping and the segments from the layout, so any
     * disagreement between {@link #countWrapRows(int, int)} and {@link #wrapLine(LineLayout, int)} about
     * one line's row count used to surface as an {@link IndexOutOfBoundsException} out of
     * {@code IntArray.get} — fatal to the frame, and to the application when nothing above catches it,
     * for what is only one row drawn wrong.
     *
     * <p>Clamping keeps the frame alive and counts the event instead, so the underlying disagreement
     * stays observable through {@link #getRowSegmentClampCount()} rather than becoming silent. It is a
     * backstop, not the fix: the width the mapping and the layouts are built from is now shared, and a
     * healthy editor never reaches this.
     */
    private int clampedSegment(LineLayout layout, int segment) {
        int count = layout.segmentEnds.size;
        if (count <= 0) {
            rowSegmentClampCount++;
            return -1;
        }
        if (segment < 0) {
            rowSegmentClampCount++;
            return 0;
        }
        if (segment >= count) {
            rowSegmentClampCount++;
            return count - 1;
        }
        return segment;
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
            if (line < 0 || line >= document.getLineCount()) {
                continue;
            }

            LineLayout layout = layoutFor(line);
            int segment = clampedSegment(layout, row - visualRowStartOf(line));
            if (segment < 0) {
                continue;
            }
            int start = layout.segmentStarts.get(segment);
            int end = layout.segmentEnds.get(segment);
            float rowBottom = getY() + rowBottom(row);
            float baseline = getTextBaseline(rowBottom);
            // Computed once and handed to every drawer on this row, so the text and its decorations
            // cannot end up at different offsets on a wrapped continuation row.
            float rowX = getX() + getTextRenderX() + segmentIndentOffset(layout, segment);

            drawSearchHighlights(batch, layout, line, start, end, rowBottom, rowX);
            drawSelection(batch, selection, layout, line, start, end, rowBottom, rowX);
            drawBracketHighlight(batch, bracketMatch, layout, line, segment, start, end, rowBottom, rowX);

            drawStyledRange(batch, layout, start, end, rowX, baseline);
            drawDiagnosticsForRow(batch, layout, line, start, end, rowBottom, rowX);

            FoldRegion region = foldRegionsByStart.get(line);
            if (segment == 0 && region != null && region.collapsed) {
                CollapsedFoldDisplay collapsedDisplay = getCollapsedFoldDisplay(region);
                String collapsedText = collapsedDisplay.placeholderText;
                float badgeX = getX() + getCollapsedFoldDisplayX(layout, start, end);
                if (style.foldBadge != null && !collapsedText.isEmpty()) {
                    float badgeWidth = getCollapsedFoldDisplayWidth(collapsedText);
                    float badgeHorizontalInset = getCollapsedFoldDisplayHorizontalInset();
                    style.foldBadge.draw(
                        batch,
                        badgeX - badgeHorizontalInset,
                        rowBottom + style.foldBadgeVerticalPadding,
                        badgeWidth,
                        lineHeight - style.foldBadgeVerticalPadding * 2f
                    );
                }
                drawCollapsedFoldPlaceholderSelection(batch, selection, region, collapsedDisplay, badgeX, rowBottom);
                if (!collapsedText.isEmpty()) {
                    style.font.setColor(style.gutterFontColor);
                    style.font.draw(batch, collapsedText, badgeX, baseline);
                }
                if (collapsedDisplay.hasSuffix()) {
                    float suffixX = badgeX + measureText(collapsedText);
                    drawCollapsedFoldSuffixDecorations(batch, selection, bracketMatch, collapsedDisplay, suffixX, rowBottom);
                    drawCollapsedFoldSuffix(batch, collapsedDisplay, suffixX, baseline);
                }
            }
        }

        drawGutterOverlay(batch, startRow, endRow);
        drawScrollbar(batch);
    }

    /**
     * Draws squiggles for any diagnostics intersecting one visual row.
     *
     * <p>Scans the diagnostic list per row, which is fine for the tens-to-hundreds a linter produces.
     * If you push tens of thousands, index them by line first.
     */
    private void drawDiagnosticsForRow(
        Batch batch,
        LineLayout layout,
        int line,
        int segmentStart,
        int segmentEnd,
        float rowBottom,
        float renderX
    ) {
        if (diagnostics.size == 0 || style.whitePixelTexture == null) {
            return;
        }
        for (int i = 0; i < diagnostics.size; i++) {
            CodeDiagnostic diagnostic = diagnostics.get(i);
            if (!diagnostic.overlapsLine(line)) {
                continue;
            }
            int from = diagnostic.startLine == line ? diagnostic.startColumn : 0;
            int to = diagnostic.endLine == line ? diagnostic.endColumn : layout.text.length();
            from = Math.max(from, segmentStart);
            to = Math.min(to, segmentEnd);
            if (to <= from) {
                // A zero-width diagnostic still deserves a mark, so widen it by one character.
                if (from >= segmentStart && from <= segmentEnd && diagnostic.startColumn == diagnostic.endColumn) {
                    to = Math.min(segmentEnd, from + 1);
                    if (to <= from) {
                        continue;
                    }
                } else {
                    continue;
                }
            }
            float x = renderX + layout.measureRange(segmentStart, from);
            float width = layout.measureRange(from, to);
            drawSquiggle(batch, x, rowBottom + style.diagnosticSquiggleOffset, width, getDiagnosticColor(diagnostic.severity));
        }
    }

    /**
     * Draws a zig-zag underline from a run of small quads. Uses the style's white pixel, the same
     * texture the theme builder already creates for the other decorations.
     */
    private void drawSquiggle(Batch batch, float x, float y, float width, Color color) {
        if (width <= 0f || color == null) {
            return;
        }
        float step = Math.max(1f, style.diagnosticSquiggleStep);
        float amplitude = Math.max(1f, style.diagnosticSquiggleAmplitude);
        float thickness = Math.max(1f, style.diagnosticSquiggleThickness);
        Color previous = batch.getColor();
        float previousR = previous.r;
        float previousG = previous.g;
        float previousB = previous.b;
        float previousA = previous.a;
        batch.setColor(color);
        int segments = (int) Math.ceil(width / step);
        for (int i = 0; i < segments; i++) {
            float segmentX = x + i * step;
            float segmentWidth = Math.min(step, x + width - segmentX);
            if (segmentWidth <= 0f) {
                break;
            }
            float segmentY = (i & 1) == 0 ? y : y + amplitude;
            batch.draw(style.whitePixelTexture, segmentX, segmentY, segmentWidth, thickness);
        }
        batch.setColor(previousR, previousG, previousB, previousA);
    }

    private void drawCollapsedFoldPlaceholderSelection(
        Batch batch,
        SelectionRange selection,
        FoldRegion region,
        CollapsedFoldDisplay display,
        float badgeX,
        float rowBottom
    ) {
        if (selection == null || style.selection == null || region == null || display == null || display.placeholderText.isEmpty()) {
            return;
        }
        if (!selectionOverlapsCollapsedPlaceholder(selection, region, display)) {
            return;
        }

        float badgeHorizontalInset = getCollapsedFoldDisplayHorizontalInset();
        float badgeWidth = getCollapsedFoldDisplayWidth(display.placeholderText);
        style.selection.draw(
            batch,
            badgeX - badgeHorizontalInset,
            rowBottom + 2f,
            Math.max(1f, badgeWidth),
            lineHeight - 4f
        );
    }

    private void drawCollapsedFoldSuffixDecorations(
        Batch batch,
        SelectionRange selection,
        BracketMatch bracketMatch,
        CollapsedFoldDisplay display,
        float suffixX,
        float rowBottom
    ) {
        if (display == null || !display.hasSuffix() || display.suffixLine < 0 || display.suffixLine >= document.getLineCount()) {
            return;
        }

        LineLayout endLayout = layoutFor(display.suffixLine);
        drawSearchHighlights(batch, endLayout, display.suffixLine, display.suffixStart, display.suffixEnd, rowBottom, suffixX);
        drawSelection(batch, selection, endLayout, display.suffixLine, display.suffixStart, display.suffixEnd, rowBottom, suffixX);
        drawBracketHighlight(batch, bracketMatch, endLayout, display.suffixLine, 0, display.suffixStart, display.suffixEnd, rowBottom, suffixX);
    }

    private boolean selectionOverlapsCollapsedPlaceholder(SelectionRange selection, FoldRegion region, CollapsedFoldDisplay display) {
        if (selection == null || region == null || region.endLine <= region.startLine) {
            return false;
        }

        int hiddenStartLine = region.startLine;
        int hiddenStartColumn = document.getLineLength(region.startLine);
        int hiddenEndLine = region.endLine;
        int hiddenEndColumn = display.hasSuffix() ? Math.max(0, display.suffixStart) : document.getLineLength(region.endLine);

        return comparePosition(selection.endLine, selection.endColumn, hiddenStartLine, hiddenStartColumn) > 0
            && comparePosition(selection.startLine, selection.startColumn, hiddenEndLine, hiddenEndColumn) < 0;
    }

    private void drawGutterOverlay(Batch batch, int startRow, int endRow) {
        if (getGutterWidth() <= 0f) {
            return;
        }
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
            if (line < 0 || line >= document.getLineCount()) {
                continue;
            }

            int segment = row - visualRowStartOf(line);
            float rowBottom = getY() + rowBottom(row);
            float baseline = getTextBaseline(rowBottom);
            style.font.setColor(style.gutterFontColor);
            if (segment == 0) {
                drawGutterMarks(batch, line, rowBottom);
                drawFoldIndicator(batch, line, rowBottom, baseline);
                if (lineNumbersVisible) {
                    String lineNumber = Integer.toString(line + 1);
                    float numberWidth = measureText(lineNumber);
                    float lineNumberRight = getGutterLineNumberRightX();
                    style.font.draw(
                        batch,
                        lineNumber,
                        getX() + getGutterRenderX() + lineNumberRight - numberWidth,
                        baseline
                    );
                }
            } else if (lineNumbersVisible) {
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

    /**
     * Draws the gutter indicators for one line: a caller-supplied {@link CodeLineMark}, or failing that
     * the worst diagnostic on the line.
     *
     * <p>A mark takes precedence, so a breakpoint is not hidden by a warning on the same line.
     */
    private void drawGutterMarks(Batch batch, int line, float rowBottom) {
        CodeLineMark mark = lineMarksByLine.get(line);
        if (mark != null) {
            drawLineMark(batch, mark, rowBottom);
            return;
        }
        drawDiagnosticGutterMark(batch, line, rowBottom);
    }

    private void drawLineMark(Batch batch, CodeLineMark mark, float rowBottom) {
        if (mark.icon != null) {
            float size = Math.min(style.lineMarkIconSize, Math.max(1f, lineHeight));
            float iconY = rowBottom + (lineHeight - size) * 0.5f;
            mark.icon.draw(batch, getX() + getGutterRenderX() + style.gutterLeftPadding, iconY, size, size);
            return;
        }
        if (mark.color != null && style.lineMarkBarWidth > 0f) {
            drawColoredBar(
                batch,
                getX() + getGutterRenderX(),
                rowBottom + 1f,
                style.lineMarkBarWidth,
                Math.max(1f, lineHeight - 2f),
                mark.color
            );
        }
    }

    /** Draws a severity-coloured tick at the gutter's left edge for a line carrying a diagnostic. */
    private void drawDiagnosticGutterMark(Batch batch, int line, float rowBottom) {
        if (diagnostics.size == 0 || style.diagnosticGutterMarkWidth <= 0f) {
            return;
        }
        CodeDiagnosticSeverity worst = null;
        for (int i = 0; i < diagnostics.size; i++) {
            CodeDiagnostic diagnostic = diagnostics.get(i);
            if (!diagnostic.overlapsLine(line)) {
                continue;
            }
            if (worst == null || diagnostic.severity.ordinal() < worst.ordinal()) {
                worst = diagnostic.severity;
            }
        }
        if (worst == null) {
            return;
        }
        drawColoredBar(
            batch,
            getX() + getGutterRenderX(),
            rowBottom + 1f,
            style.diagnosticGutterMarkWidth,
            Math.max(1f, lineHeight - 2f),
            getDiagnosticColor(worst)
        );
    }

    private CollapsedFoldDisplay getCollapsedFoldDisplay(FoldRegion region) {
        if (region == null) {
            return new CollapsedFoldDisplay("", "", -1, -1, -1);
        }
        String placeholderText;
        if (foldDisplayProvider == null) {
            placeholderText = DEFAULT_FOLD_DISPLAY_PROVIDER.getCollapsedText(this, null);
        } else {
            FoldDisplayContext context = new FoldDisplayContext(
                region.startLine,
                region.endLine,
                region.depth,
                region.startText,
                region.endText
            );
            placeholderText = foldDisplayProvider.getCollapsedText(this, context);
        }
        return buildCollapsedFoldDisplay(region, placeholderText == null ? "" : placeholderText);
    }

    private float getCollapsedFoldDisplayX(LineLayout layout, int start, int end) {
        return getTextRenderX() + layout.measureRange(start, end) + style.foldBadgeGap;
    }

    private float getCollapsedFoldDisplayHorizontalInset() {
        return style.foldBadgeHorizontalPadding * 0.5f;
    }

    private float getCollapsedFoldDisplayWidth(String collapsedText) {
        if (collapsedText == null || collapsedText.isEmpty()) {
            return 0f;
        }
        return measureText(collapsedText) + style.foldBadgeHorizontalPadding;
    }

    private void drawCollapsedFoldSuffix(Batch batch, CollapsedFoldDisplay display, float x, float baseline) {
        if (display == null || !display.hasSuffix()) {
            return;
        }
        if (display.suffixLine < 0 || display.suffixLine >= document.getLineCount()) {
            style.font.setColor(style.fontColor);
            style.font.draw(batch, display.suffixText, x, baseline);
            return;
        }
        LineLayout endLayout = layoutFor(display.suffixLine);
        drawStyledRange(
            batch,
            endLayout,
            display.suffixStart,
            display.suffixEnd,
            x,
            baseline
        );
    }

    private boolean isInsideCollapsedFoldDisplayHitArea(float x, float y, int row, LineLayout layout, int start, int end, FoldRegion region) {
        if (region == null || !region.collapsed) {
            return false;
        }
        String collapsedText = getCollapsedFoldDisplay(region).placeholderText;
        if (collapsedText.isEmpty()) {
            return false;
        }
        float rowBottom = rowBottom(row);
        if (y < rowBottom || y > rowBottom + lineHeight) {
            return false;
        }
        float left = getCollapsedFoldDisplayX(layout, start, end) - getCollapsedFoldDisplayHorizontalInset();
        float right = left + getCollapsedFoldDisplayWidth(collapsedText);
        return x >= left && x <= right;
    }

    private boolean isCollapsedSuffixCursorVisible(FoldRegion region, int line, int column) {
        if (region == null || !region.collapsed || line != region.endLine) {
            return false;
        }
        CollapsedFoldDisplay display = getCollapsedFoldDisplay(region);
        return display.hasSuffix() && column >= display.suffixStart;
    }

    private void drawVisibleRowBackgrounds(Batch batch, int startRow, int endRow, FoldRegion activeBlock) {
        for (int row = startRow; row <= endRow; row++) {
            int line = findLineByVisualRow(row);
            if (line < 0 || line >= document.getLineCount()) {
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

            CodeLineMark mark = lineMarksByLine.get(line);
            if (mark != null && mark.highlightLine && mark.color != null) {
                drawColoredBar(batch, rowContentX, rowBottom, rowContentWidth, lineHeight,
                    tintColor(mark.color, style.lineMarkHighlightAlpha));
            }
        }
    }

    /** A copy of {@code color} at {@code alpha}, for tints that must not obscure the text. */
    private Color tintColor(Color color, float alpha) {
        scratchTintColor.set(color);
        scratchTintColor.a = color.a * alpha;
        return scratchTintColor;
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
                drawText(batch, layout, cursor, plainEnd, x, baseline, baseColor);
                x += layout.measureRange(cursor, plainEnd);
            }

            int tokenStart = Math.max(token.start, start);
            int tokenEnd = Math.min(token.end, end);
            drawText(batch, layout, tokenStart, tokenEnd, x, baseline, disabled ? baseColor : token.color);
            x += layout.measureRange(tokenStart, tokenEnd);
            cursor = tokenEnd;
        }

        if (cursor < end) {
            drawText(batch, layout, cursor, end, x, baseline, baseColor);
        }
    }

    private void drawText(Batch batch, LineLayout layout, int start, int end, float x, float y, Color color) {
        String text = layout.text;
        if (start >= end) {
            return;
        }
        style.font.setColor(color);
        int runStart = start;
        float runX = x;
        for (int i = start; i < end; i++) {
            if (text.charAt(i) != '\t') {
                continue;
            }
            if (runStart < i) {
                style.font.draw(batch, text, runX, y, runStart, i, 0f, Align.left, false);
                runX += layout.measureRange(runStart, i);
            }
            runX += layout.measureRange(i, i + 1);
            runStart = i + 1;
        }
        if (runStart < end) {
            style.font.draw(batch, text, runX, y, runStart, end, 0f, Align.left, false);
        }
    }

    private float getTextBaseline(float rowBottom) {
        return rowBottom + lineHeight + style.textBaselineOffset;
    }

    private String getDisplayLineText(String actualLine) {
        if (!passwordMode || actualLine == null || actualLine.isEmpty()) {
            return actualLine == null ? "" : actualLine;
        }
        StringBuilder builder = new StringBuilder(actualLine.length());
        for (int i = 0; i < actualLine.length(); i++) {
            builder.append(passwordCharacter);
        }
        return builder.toString();
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
        int start,
        int end,
        float rowBottom,
        float renderX
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

        float x = renderX + layout.measureRange(start, selectedStart);
        float width = layout.measureRange(selectedStart, selectedEnd);
        style.selection.draw(batch, x, rowBottom + 2f, Math.max(1f, width), lineHeight - 4f);
    }

    private void drawSearchHighlights(
        Batch batch,
        LineLayout layout,
        int line,
        int start,
        int end,
        float rowBottom,
        float renderX
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
            float x = renderX + layout.measureRange(start, highlightStart);
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
        float rowBottom,
        float renderX
    ) {
        if (bracketMatch == null || style.bracketMatch == null) {
            return;
        }
        drawBracketHighlightAt(batch, bracketMatch.anchorLine, bracketMatch.anchorColumn, layout, line, segment, start, end, rowBottom, renderX);
        drawBracketHighlightAt(batch, bracketMatch.matchLine, bracketMatch.matchColumn, layout, line, segment, start, end, rowBottom, renderX);
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
        float rowBottom,
        float renderX
    ) {
        if (line != targetLine || targetColumn < start || targetColumn >= end || targetColumn >= layout.text.length()) {
            return;
        }
        float x = renderX + layout.measureRange(start, targetColumn);
        float width = Math.max(1f, layout.measureRange(targetColumn, targetColumn + 1));
        style.bracketMatch.draw(batch, x, rowBottom + 2f, width, lineHeight - 4f);
    }

    private void drawVisibleIndentGuides(Batch batch, int startRow, int endRow, FoldRegion activeBlock) {
        if (startRow > endRow) {
            return;
        }

        int maxVisibleIndent = 0;
        for (int row = startRow; row <= endRow; row++) {
            int line = findLineByVisualRow(row);
            if (line >= 0 && line < document.getLineCount()) {
                // Read the depth array directly: materializing a layout just to read its indent level
                // would measure every glyph on the line.
                maxVisibleIndent = Math.max(maxVisibleIndent, safeIndentLevel(indentLevels, line));
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
                    if (line >= 0 && line < document.getLineCount()) {
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

    /**
     * Whether an indent guide should be drawn for this line at this depth.
     *
     * <p>Called for every visible row at every depth, once per frame, so the fold-region test uses the
     * {@link #foldDepthsByEndLine} index. Scanning the region list here cost roughly
     * {@code rows × depths × regions} per frame — millions of iterations on a large source file.
     */
    private boolean shouldDrawGuideForLineDepth(int line, int depth) {
        if (line < 0 || line >= document.getLineCount()) {
            return false;
        }
        if (safeIndentLevel(indentLevels, line) <= depth) {
            return false;
        }
        IntArray depthsEndingHere = foldDepthsByEndLine.get(line);
        if (depthsEndingHere != null && depthsEndingHere.indexOf(depth) >= 0) {
            return false;
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
        if (!scrollbarsVisible || style.scrollbarTrack == null || style.scrollbarKnob == null) {
            return;
        }

        if (hasVisibleVerticalScrollbar()) {
            float x = getX() + getWidth() - style.scrollbarWidth - style.scrollbarMargin;
            float y = getY() + getScrollbarTrackY();
            float trackHeight = getScrollbarTrackHeight();
            style.scrollbarTrack.draw(batch, x, y, style.scrollbarWidth, trackHeight);

            float thumbHeight = getScrollbarThumbHeight();
            float thumbY = getY() + getScrollbarThumbY();
            style.scrollbarKnob.draw(batch, x, thumbY, style.scrollbarWidth, thumbHeight);
        }

        if (hasVisibleHorizontalScrollbar()) {
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
        drawSelectionHandles(batch, getX(), getY());
    }

    private void drawSelectionHandles(Batch batch, float baseX, float baseY) {
        if (!hasSelectionHandleDrawable() || !shouldShowTouchHandles()) {
            return;
        }
        SelectionRange selection = getSelectionRange();
        if (selection == null) {
            drawCaretHandle(batch, baseX, baseY);
            drawHandleMagnifier(batch, baseX, baseY);
            return;
        }
        HandlePlacement start = getHandlePlacement(selection.startLine, selection.startColumn, false);
        HandlePlacement end = getHandlePlacement(selection.endLine, selection.endColumn, true);
        if (isHandleVisibleWithinEditorBounds(start)) {
            Drawable startHandle = getSelectionHandleDrawable(false);
            startHandle.draw(
                batch,
                baseX + start.drawX,
                baseY + start.drawY,
                style.selectionHandleRadius * 2f,
                style.selectionHandleRadius * 2f
            );
        }
        if (isHandleVisibleWithinEditorBounds(end)) {
            Drawable endHandle = getSelectionHandleDrawable(true);
            endHandle.draw(
                batch,
                baseX + end.drawX,
                baseY + end.drawY,
                style.selectionHandleRadius * 2f,
                style.selectionHandleRadius * 2f
            );
        }
        drawHandleMagnifier(batch, baseX, baseY);
    }

    private void drawCaretHandle(Batch batch, float baseX, float baseY) {
        if (!shouldShowTransientCaretHandle()) {
            return;
        }
        Drawable caretHandle = getCaretHandleDrawable();
        if (caretHandle == null) {
            return;
        }
        HandlePlacement caret = getCaretHandlePlacement();
        if (!isHandleVisibleWithinEditorBounds(caret)) {
            return;
        }
        caretHandle.draw(
            batch,
            baseX + caret.drawX,
            baseY + caret.drawY,
            style.selectionHandleRadius * 2f,
            style.selectionHandleRadius * 2f
        );
    }

    private void drawHandleMagnifier(Batch batch, float baseX, float baseY) {
        if (!shouldShowHandleMagnifier() || getStage() == null) {
            return;
        }

        Stage stage = getStage();
        float popupWidth = getMagnifierWidth();
        float popupHeight = getMagnifierHeight();
        float popupInset = Math.max(0f, style.magnifierContentPadding);
        float popupX = baseX + lastDragX - popupWidth * 0.5f;
        float popupY = baseY + lastDragY + getMagnifierVerticalOffset();
        if (popupY + popupHeight > stage.getHeight() - 6f) {
            popupY = baseY + lastDragY - popupHeight - getMagnifierVerticalOffset();
        }
        popupX = clamp(popupX, 6f, stage.getWidth() - popupWidth - 6f);
        popupY = clamp(popupY, 6f, stage.getHeight() - popupHeight - 6f);

        int backBufferWidth = Gdx.graphics.getBackBufferWidth();
        int backBufferHeight = Gdx.graphics.getBackBufferHeight();
        float scaleX = backBufferWidth / Math.max(1f, (float) Gdx.graphics.getWidth());
        float scaleY = backBufferHeight / Math.max(1f, (float) Gdx.graphics.getHeight());
        float contentWidth = Math.max(8f, popupWidth - popupInset * 2f);
        float contentHeight = Math.max(8f, popupHeight - popupInset * 2f);
        float sampleStageWidth = Math.max(lineHeight, contentWidth * 0.5f);
        float sampleStageHeight = Math.max(lineHeight * 0.8f, contentHeight * 0.5f);
        int sampleWidth = Math.max(24, Math.round(sampleStageWidth * scaleX));
        int sampleHeight = Math.max(24, Math.round(sampleStageHeight * scaleY));

        scratchVector3.set(baseX + lastDragX, baseY + lastDragY + style.selectionHandleRadius * 2f, 0f);
        stage.getViewport().project(scratchVector3);
        int sourceX = clamp(Math.round(scratchVector3.x * scaleX) - sampleWidth / 2, 0, Math.max(0, backBufferWidth - sampleWidth));
        int sourceY = clamp(Math.round(scratchVector3.y * scaleY) - sampleHeight / 2, 0, Math.max(0, backBufferHeight - sampleHeight));

        batch.flush();
        TextureRegion captured = ScreenUtils.getFrameBufferTexture(sourceX, sourceY, sampleWidth, sampleHeight);
        try {
            if (style.magnifierBackground != null) {
                style.magnifierBackground.draw(batch, popupX, popupY, popupWidth, popupHeight);
            }
            batch.draw(
                captured,
                popupX + popupInset,
                popupY + popupInset,
                contentWidth,
                contentHeight
            );
            batch.flush();
        } finally {
            if (captured != null && captured.getTexture() != null) {
                captured.getTexture().dispose();
            }
        }
    }

    private boolean shouldShowTouchHandles() {
        return useTouchInteractions() && isFocused();
    }

    private boolean shouldShowHandleMagnifier() {
        return magnifierEnabled
            && useTouchInteractions()
            && (draggingStartHandle || draggingEndHandle || draggingCaretHandle)
            && isHandleVisibleWithinEditorBounds(getActiveHandlePlacement());
    }

    private float getMagnifierWidth() {
        return style.magnifierWidth > 0f ? style.magnifierWidth : stageUnitsFromCentimetersX(4f);
    }

    private float getMagnifierHeight() {
        return style.magnifierHeight > 0f ? style.magnifierHeight : stageUnitsFromCentimetersY(1.5f);
    }

    private float getMagnifierVerticalOffset() {
        return stageUnitsFromCentimetersY(1f);
    }

    private void showTransientCaretHandle() {
        if (!transientCaretHandleEnabled) {
            transientCaretHandleUntilNanos = 0L;
            return;
        }
        transientCaretHandleUntilNanos = TimeUtils.nanoTime() + CARET_HANDLE_VISIBLE_NS;
    }

    private void hideTransientCaretHandle() {
        transientCaretHandleUntilNanos = 0L;
    }

    private boolean acceptsInputCharacter(char character) {
        return inputFilter == null || inputFilter.acceptChar(this, character);
    }

    private String sanitizeInputText(String text, boolean normalizeLineBreaks) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        String source = normalizeLineBreaks ? text.replace("\r\n", "\n").replace('\r', '\n') : text;
        StringBuilder builder = new StringBuilder(source.length());
        for (int i = 0; i < source.length(); i++) {
            char current = source.charAt(i);
            if (acceptsInputCharacter(current)) {
                builder.append(current);
            }
        }
        return builder.toString();
    }

    private void clearHandleDragState() {
        draggingStartHandle = false;
        draggingEndHandle = false;
        draggingCaretHandle = false;
        handleDragFixedLine = -1;
        handleDragFixedColumn = -1;
        handleDragPointerOffsetX = 0f;
        handleDragPointerOffsetY = 0f;
    }

    private void hideTouchHandlesImmediately() {
        clearHandleDragState();
        hideTransientCaretHandle();
        syncSelectionHandleOverlay();
    }

    private boolean shouldShowTransientCaretHandle() {
        return shouldShowTouchHandles()
            && transientCaretHandleEnabled
            && getSelectionRange() == null
            && !disabled
            && (draggingCaretHandle || TimeUtils.nanoTime() < transientCaretHandleUntilNanos);
    }

    private boolean hasSelectionHandleDrawable() {
        return style.selectionHandleStart != null || style.selectionHandleEnd != null || style.selectionHandle != null;
    }

    private Drawable getSelectionHandleDrawable(boolean endHandle) {
        Drawable handle = endHandle ? style.selectionHandleEnd : style.selectionHandleStart;
        if (handle == null) {
            handle = style.selectionHandle;
        }
        return handle;
    }

    private Drawable getCaretHandleDrawable() {
        Drawable handle = style.selectionHandleCaret;
        return handle != null ? handle : getSelectionHandleDrawable(true);
    }

    private HandlePlacement getActiveHandlePlacement() {
        SelectionRange selection = getSelectionRange();
        if (draggingStartHandle && selection != null) {
            return getHandlePlacement(selection.startLine, selection.startColumn, false);
        }
        if (draggingEndHandle && selection != null) {
            return getHandlePlacement(selection.endLine, selection.endColumn, true);
        }
        if (draggingCaretHandle) {
            return getCaretHandlePlacement();
        }
        return null;
    }

    private boolean isHandleVisibleWithinEditorBounds(HandlePlacement handle) {
        if (handle == null) {
            return false;
        }
        float marginX = stageUnitsFromCentimetersX(0.3f);
        float marginY = stageUnitsFromCentimetersY(0.3f);
        float handleSize = style.selectionHandleRadius * 2f;
        float touchRadius = getSelectionHandleTouchRadius();
        float left = handle.drawX;
        float right = handle.drawX + handleSize;
        float bottom = handle.drawY;
        float top = handle.drawY + handleSize;
        // Full actor bounds + margin. Handles hang below the line so expand bottom by handleSize.
        return right >= -marginX - touchRadius
            && left <= getWidth() + marginX + touchRadius
            && top >= -marginY - handleSize
            && bottom <= getHeight() + marginY + touchRadius;
    }

    private void attachSelectionHandleOverlay(Stage stage) {
        if (stage == null) {
            return;
        }
        if (selectionHandleOverlay.getStage() != null && selectionHandleOverlay.getStage() != stage) {
            selectionHandleOverlay.remove();
        }
        if (selectionHandleOverlay.getStage() != stage) {
            stage.addActor(selectionHandleOverlay);
        }
        syncSelectionHandleOverlay();
    }

    private void detachSelectionHandleOverlay(Stage stage) {
        if (stage == null) {
            return;
        }
        if (selectionHandleOverlay.getStage() == stage) {
            selectionHandleOverlay.remove();
        }
    }

    private void syncSelectionHandleOverlay() {
        Stage stage = getStage();
        if (stage == null) {
            return;
        }
        if (selectionHandleOverlay.getStage() != stage) {
            attachSelectionHandleOverlay(stage);
            return;
        }
        selectionHandleOverlay.setBounds(0f, 0f, stage.getWidth(), stage.getHeight());
        selectionHandleOverlay.setVisible(shouldShowTouchHandles()
            && hasSelectionHandleDrawable()
            && (getSelectionRange() != null || shouldShowTransientCaretHandle())
            && isVisible());
        if (selectionHandleOverlay.isVisible()) {
            selectionHandleOverlay.toFront();
        }
    }

    private Vector2 getStageOrigin() {
        scratchVector.set(0f, 0f);
        return localToStageCoordinates(scratchVector);
    }

    private boolean beginSelectionHandleOverlayDrag(float stageX, float stageY) {
        Vector2 local = stageToLocalHandleCoordinates(stageX, stageY);
        lastDragX = local.x;
        lastDragY = local.y;
        return beginAnyHandleDrag(local.x, local.y);
    }

    private void updateSelectionHandleOverlayDrag(float stageX, float stageY) {
        Vector2 local = stageToLocalHandleCoordinates(stageX, stageY);
        lastDragX = local.x;
        lastDragY = local.y;
        updateAnyHandleDrag(local.x, local.y);
    }

    private void endSelectionHandleOverlayDrag() {
        boolean draggedHandle = draggingStartHandle || draggingEndHandle || draggingCaretHandle;
        clearHandleDragState();
        if (draggedHandle) {
            refreshBlink();
        }
    }

    private Vector2 stageToLocalHandleCoordinates(float stageX, float stageY) {
        scratchVector.set(stageX, stageY);
        return stageToLocalCoordinates(scratchVector);
    }

    /**
     * @param overlayLocalX Y coordinates in SelectionHandleOverlay local space
     *                      (overlay is full-stage at 0,0 so this equals stage space).
     */
    private boolean isStagePositionNearSelectionHandle(float overlayLocalX, float overlayLocalY) {
        if (!shouldShowTouchHandles()) {
            return false;
        }
        Vector2 local = stageToLocalHandleCoordinates(overlayLocalX, overlayLocalY);
        return isNearAnyHandle(local.x, local.y);
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
        CodePoint point = getCodePointAtRowX(row, Math.max(0f, x + scrollX - getTextStartX()));
        if (point == null) {
            return;
        }
        document.moveCursorTo(point.line, point.column);
        resetPreferredColumn();
        ensureCursorVisible();
    }

    private CodePoint getCodePointAt(float x, float y, boolean clampY) {
        int row = clampY ? rowAtClamped(y) : rowAt(y);
        if (row < 0 || row >= totalVisualRows) {
            return null;
        }
        return getCodePointAtRowX(row, Math.max(0f, x + scrollX - getTextStartX()));
    }

    private CodePoint getCodePointAtRowX(int row, float localX) {
        if (row < 0 || row >= totalVisualRows) {
            return null;
        }
        int line = findLineByVisualRow(row);
        if (line < 0 || line >= document.getLineCount()) {
            return null;
        }

        LineLayout layout = layoutFor(line);
        int segment = clamp(row - visualRowStartOf(line), 0, layout.getVisualRowCount() - 1);
        int start = layout.segmentStarts.get(segment);
        int end = layout.segmentEnds.get(segment);
        FoldRegion region = segment == 0 ? foldRegionsByStart.get(line) : null;
        if (region != null && region.collapsed) {
            CollapsedFoldDisplay display = getCollapsedFoldDisplay(region);
            if (display.hasSuffix()) {
                float suffixX = layout.measureRange(start, end) + style.foldBadgeGap + measureText(display.placeholderText);
                float suffixWidth = layoutFor(region.endLine).measureRange(display.suffixStart, display.suffixEnd);
                if (localX >= suffixX) {
                    float suffixLocalX = Math.max(0f, Math.min(localX - suffixX, suffixWidth));
                    int suffixColumn = findColumnForX(layoutFor(region.endLine), display.suffixStart, display.suffixEnd, suffixLocalX);
                    return new CodePoint(region.endLine, suffixColumn);
                }
            }
        }

        // Undo the continuation indent before asking which character this x falls on. A negative result
        // means the click landed in the indent gutter, and findColumnForX clamps it to the segment start,
        // which is what clicking left of the text should do.
        int column = findColumnForX(layout, start, end, localX - segmentIndentOffset(layout, segment));
        return new CodePoint(line, column);
    }

    private HandlePlacement getHandlePlacement(int line, int column, boolean endHandle) {
        CursorPlacement placement = getCursorPlacement(line, column);
        if (placement == null) {
            return null;
        }
        float radius = style.selectionHandleRadius;
        float handleSize = radius * 2f;
        // Tip of handle attaches just under the caret baseline.
        float anchorX = placement.x;
        float anchorY = rowBottom(placement.row) + 2f;
        // Start: square left of caret (TOP_RIGHT stem). End: square right of caret (TOP_LEFT stem).
        float drawX = endHandle ? anchorX : anchorX - handleSize;
        float drawY = anchorY - handleSize;
        // Must match drawCornerBubbleHandle circle center: (drawX + size/2, drawY + size/2).
        float hitX = drawX + radius;
        float hitY = drawY + radius;
        return new HandlePlacement(anchorX, anchorY, drawX, drawY, hitX, hitY);
    }

    private HandlePlacement getCaretHandlePlacement() {
        CursorPlacement placement = getCursorPlacement(document.getCursorLine(), document.getCursorColumn());
        if (placement == null) {
            return null;
        }
        float radius = style.selectionHandleRadius;
        float handleSize = radius * 2f;
        float anchorX = placement.x;
        float anchorY = rowBottom(placement.row) + 2f;
        float drawX = anchorX - radius;
        float drawY = anchorY - handleSize;
        float hitX = drawX + radius;
        float hitY = drawY + radius;
        return new HandlePlacement(anchorX, anchorY, drawX, drawY, hitX, hitY);
    }

    private boolean beginAnyHandleDrag(float x, float y) {
        return beginHandleDrag(x, y) || beginCaretHandleDrag(x, y);
    }

    private boolean beginHandleDrag(float x, float y) {
        SelectionRange selection = getSelectionRange();
        if (selection == null) {
            return false;
        }

        HandlePlacement start = getHandlePlacement(selection.startLine, selection.startColumn, false);
        HandlePlacement end = getHandlePlacement(selection.endLine, selection.endColumn, true);
        boolean nearStart = isHandleVisibleWithinEditorBounds(start) && isNearHandle(x, y, start);
        boolean nearEnd = isHandleVisibleWithinEditorBounds(end) && isNearHandle(x, y, end);
        if (nearStart && nearEnd) {
            nearStart = distanceSquaredToHandle(x, y, start) <= distanceSquaredToHandle(x, y, end);
            nearEnd = !nearStart;
        }
        if (nearStart) {
            draggingStartHandle = true;
            draggingEndHandle = false;
            pendingTouchPress = false;
            longPressTriggered = false;
            handleDragFixedLine = selection.endLine;
            handleDragFixedColumn = selection.endColumn;
            // Map finger -> selection tip (anchor), so the caret edge follows under the tip.
            handleDragPointerOffsetX = x - start.x;
            handleDragPointerOffsetY = y - start.y;
            touchScrollVelocityX = 0f;
            touchScrollVelocityY = 0f;
            return true;
        }
        if (nearEnd) {
            draggingStartHandle = false;
            draggingEndHandle = true;
            pendingTouchPress = false;
            longPressTriggered = false;
            handleDragFixedLine = selection.startLine;
            handleDragFixedColumn = selection.startColumn;
            handleDragPointerOffsetX = x - end.x;
            handleDragPointerOffsetY = y - end.y;
            touchScrollVelocityX = 0f;
            touchScrollVelocityY = 0f;
            return true;
        }
        return false;
    }

    private boolean beginCaretHandleDrag(float x, float y) {
        if (!shouldShowTransientCaretHandle()) {
            return false;
        }
        HandlePlacement caret = getCaretHandlePlacement();
        if (!isHandleVisibleWithinEditorBounds(caret) || !isNearHandle(x, y, caret)) {
            return false;
        }
        draggingCaretHandle = true;
        draggingStartHandle = false;
        draggingEndHandle = false;
        pendingTouchPress = false;
        longPressTriggered = false;
        handleDragFixedLine = -1;
        handleDragFixedColumn = -1;
        handleDragPointerOffsetX = x - caret.x;
        handleDragPointerOffsetY = y - caret.y;
        touchScrollVelocityX = 0f;
        touchScrollVelocityY = 0f;
        showTransientCaretHandle();
        return true;
    }

    private boolean isNearHandle(float x, float y, HandlePlacement handle) {
        if (handle == null) {
            return false;
        }
        // Only the visual bulb (and a small pad). Do NOT include the caret tip —
        // a capsule to the tip made large areas of text steal handle drags.
        float touchRadius = getSelectionHandleTouchRadius();
        float dx = x - handle.hitX;
        float dy = y - handle.hitY;
        return dx * dx + dy * dy <= touchRadius * touchRadius;
    }

    private float getSelectionHandleTouchRadius() {
        // Visual radius + modest pad. Cap multiplier so hit stays on the bulb, not half the line.
        float visual = Math.max(1f, style.selectionHandleRadius);
        float multiplier = Math.max(
            1f,
            Math.min(style.selectionHandleTouchRadiusMultiplier, SELECTION_HANDLE_TOUCH_RADIUS_MULTIPLIER_MAX)
        );
        return visual * multiplier;
    }

    private float distanceSquaredToHandle(float x, float y, HandlePlacement handle) {
        float dx = x - handle.hitX;
        float dy = y - handle.hitY;
        return dx * dx + dy * dy;
    }

    private boolean isNearAnyHandle(float x, float y) {
        SelectionRange selection = getSelectionRange();
        if (selection != null) {
            HandlePlacement start = getHandlePlacement(selection.startLine, selection.startColumn, false);
            HandlePlacement end = getHandlePlacement(selection.endLine, selection.endColumn, true);
            return (isHandleVisibleWithinEditorBounds(start) && isNearHandle(x, y, start))
                || (isHandleVisibleWithinEditorBounds(end) && isNearHandle(x, y, end));
        }
        HandlePlacement caret = getCaretHandlePlacement();
        return isHandleVisibleWithinEditorBounds(caret) && isNearHandle(x, y, caret);
    }

    private void updateAnyHandleDrag(float x, float y) {
        if (draggingCaretHandle) {
            updateCaretHandleDrag(x, y);
            return;
        }
        updateSelectionHandleDrag(x, y);
    }

    private void updateSelectionHandleDrag(float x, float y) {
        if (handleDragFixedLine < 0) {
            return;
        }
        CodePoint point = getCodePointAt(x - handleDragPointerOffsetX, y - handleDragPointerOffsetY, true);
        if (point == null) {
            return;
        }

        selectionAnchorLine = handleDragFixedLine;
        selectionAnchorColumn = handleDragFixedColumn;
        document.moveCursorTo(point.line, point.column);
        ensureCursorVisible();
        refreshBlink();
    }

    private void updateCaretHandleDrag(float x, float y) {
        CodePoint point = getCodePointAt(x - handleDragPointerOffsetX, y - handleDragPointerOffsetY, true);
        if (point == null) {
            return;
        }

        selectionAnchorLine = -1;
        selectionAnchorColumn = -1;
        document.moveCursorTo(point.line, point.column);
        ensureCursorVisible();
        showTransientCaretHandle();
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
        if (line < 0 || line >= document.getLineCount()) {
            return null;
        }
        if (isHiddenLine(line)) {
            FoldRegion containingRegion = findContainingRegion(line);
            if (isCollapsedSuffixCursorVisible(containingRegion, line, column)) {
                return getCollapsedSuffixCursorPlacement(containingRegion, column);
            }
            return null;
        }

        LineLayout layout = layoutFor(line);
        int safeColumn = Math.max(0, Math.min(column, layout.text.length()));
        int segment = layout.findSegmentForColumn(safeColumn);
        int start = layout.segmentStarts.get(segment);
        // The caret has to sit where the glyph is drawn, so it carries the continuation indent too.
        // moveCursorByVisualRows inverts this to get its preferred x, which is why Up/Down keeps a
        // screen column rather than a text column across an indented wrap.
        float x = getTextStartX() + segmentIndentOffset(layout, segment) + layout.measureRange(start, safeColumn) - scrollX;
        int row = visualRowStartOf(line) + segment;
        return new CursorPlacement(row, x);
    }

    private CursorPlacement getCollapsedSuffixCursorPlacement(FoldRegion region, int column) {
        if (region == null || !region.collapsed || region.startLine < 0 || region.startLine >= document.getLineCount()) {
            return null;
        }
        CollapsedFoldDisplay display = getCollapsedFoldDisplay(region);
        if (!display.hasSuffix()) {
            return null;
        }
        LineLayout startLayout = layoutFor(region.startLine);
        if (startLayout.getVisualRowCount() <= 0) {
            return null;
        }
        int start = startLayout.segmentStarts.get(0);
        int end = startLayout.segmentEnds.get(0);
        int safeColumn = Math.max(display.suffixStart, Math.min(column, layoutFor(region.endLine).text.length()));
        float x = getCollapsedFoldDisplayX(startLayout, start, end)
            + measureText(display.placeholderText)
            + layoutFor(region.endLine).measureRange(display.suffixStart, safeColumn);
        return new CursorPlacement(visualRowStartOf(region.startLine), x);
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
        // Folding acts on a region's end line, so a deferred structure pass must land first: after
        // edits the cached region could still describe where the block used to end.
        flushStructureAnalysis();

        float previousScroll = scrollY;
        setCollapsed(region.startLine, !isCollapsedStartLine(region.startLine));

        invalidateRowMapping();
        ensureLayout();
        scrollY = Math.max(getMinScroll(), Math.min(previousScroll, getMaxScroll()));
        refreshBlink();
    }

    private boolean expandCollapsedRegion(FoldRegion region) {
        if (region == null || !isCollapsedStartLine(region.startLine)) {
            return false;
        }
        float previousScroll = scrollY;
        setCollapsed(region.startLine, false);
        invalidateRowMapping();
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
                    setCollapsed(region.startLine, false);
                    changed = true;
                }
            }
        }

        int cursorLine = document.getCursorLine();
        if (intent == EditIntent.BACKSPACE && document.getCursorColumn() == 0) {
            for (FoldRegion region : foldRegions) {
                if (region.collapsed && region.endLine + 1 == cursorLine) {
                    setCollapsed(region.startLine, false);
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
            invalidateRowMapping();
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
            } else if (draggingCaretHandle) {
                updateCaretHandleDrag(lastDragX, lastDragY);
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

        float deltaX = touchScrollVelocityX * delta;
        float deltaY = touchScrollVelocityY * delta;
        if (touchScrollAxisLock == TOUCH_SCROLL_AXIS_HORIZONTAL) {
            deltaY = 0f;
            touchScrollVelocityY = 0f;
        } else if (touchScrollAxisLock == TOUCH_SCROLL_AXIS_VERTICAL) {
            deltaX = 0f;
            touchScrollVelocityX = 0f;
        }
        applyTouchScrollDelta(deltaX, deltaY);
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
        if (!overscrollEnabled) {
            clampScroll();
            return;
        }
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
        if (!overscrollEnabled) {
            return Math.max(min, Math.min(max, current + delta));
        }
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

    private void drawTintedRect(Batch batch, float x, float y, float width, float height, Color color) {
        if (width <= 0f || height <= 0f || style.whitePixelTexture == null || color == null) {
            return;
        }
        Color previous = batch.getColor();
        float previousR = previous.r;
        float previousG = previous.g;
        float previousB = previous.b;
        float previousA = previous.a;
        batch.setColor(color);
        batch.draw(style.whitePixelTexture, x, y, width, height);
        batch.setColor(previousR, previousG, previousB, previousA);
    }

    private float stageUnitsFromCentimetersX(float centimeters) {
        Stage stage = getStage();
        if (stage == null) {
            return centimeters * 38f;
        }
        float ppiX = getSafePpiX();
        float pixels = centimeters * ppiX / 2.54f;
        float stageUnitsPerPixel = stage.getWidth() / Math.max(1f, (float) Gdx.graphics.getWidth());
        return pixels * stageUnitsPerPixel;
    }

    private float stageUnitsFromCentimetersY(float centimeters) {
        Stage stage = getStage();
        if (stage == null) {
            return centimeters * 38f;
        }
        float ppiY = getSafePpiY();
        float pixels = centimeters * ppiY / 2.54f;
        float stageUnitsPerPixel = stage.getHeight() / Math.max(1f, (float) Gdx.graphics.getHeight());
        return pixels * stageUnitsPerPixel;
    }

    private float getSafePpiX() {
        float ppi = Gdx.graphics.getPpiX();
        if (ppi > 0f) {
            return ppi;
        }
        float density = Gdx.graphics.getDensity();
        return density > 0f ? density * 160f : 96f;
    }

    private float getSafePpiY() {
        float ppi = Gdx.graphics.getPpiY();
        if (ppi > 0f) {
            return ppi;
        }
        float density = Gdx.graphics.getDensity();
        return density > 0f ? density * 160f : 96f;
    }

    private void resetTouchScrollAxisLock() {
        touchScrollAxisLock = TOUCH_SCROLL_AXIS_NONE;
    }

    private void updateTouchScrollAxisLock(float x, float y) {
        if (touchScrollAxisLock != TOUCH_SCROLL_AXIS_NONE) {
            return;
        }
        float dx = x - touchDownX;
        float dy = y - touchDownY;
        if (dx * dx + dy * dy <= TOUCH_SLOP * TOUCH_SLOP) {
            return;
        }
        touchScrollAxisLock = Math.abs(dx) >= Math.abs(dy)
            ? TOUCH_SCROLL_AXIS_HORIZONTAL
            : TOUCH_SCROLL_AXIS_VERTICAL;
    }

    private boolean isInVerticalScrollbarHitArea(float x, float y) {
        return hasVisibleVerticalScrollbar()
            && x >= getWidth() - style.scrollbarHitWidth
            && x <= getWidth()
            && y >= style.statusBarHeight
            && y <= contentTopY();
    }

    private boolean isInHorizontalScrollbarHitArea(float x, float y) {
        return hasVisibleHorizontalScrollbar()
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

    private boolean hasVisibleVerticalScrollbar() {
        return scrollbarsVisible && hasVerticalScrollbar();
    }

    private boolean hasVisibleHorizontalScrollbar() {
        return scrollbarsVisible && hasHorizontalScrollbar();
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
        if (!hasVisibleVerticalScrollbar()) {
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
        if (!hasVisibleHorizontalScrollbar()) {
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

    /**
     * The block containing the caret, highlighted as the active block. Cached because
     * {@link #findContainingRegion(int)} scans every fold region and this runs once per frame.
     */
    private FoldRegion getActiveBlockRegion() {
        int cursorLine = document.getCursorLine();
        if (activeBlockCacheVersion == structureAnalyzedVersion && activeBlockCacheLine == cursorLine) {
            return activeBlockCache;
        }

        FoldRegion resolved = foldRegionsByStart.get(cursorLine);
        if (resolved == null) {
            resolved = findContainingRegion(cursorLine);
        }
        activeBlockCache = resolved;
        activeBlockCacheVersion = structureAnalyzedVersion;
        activeBlockCacheLine = cursorLine;
        return resolved;
    }

    /**
     * The bracket pair to highlight, cached until the caret or the document changes.
     *
     * <p>Called once per frame from {@code drawRows}, so it must not rescan: an unmatched bracket makes
     * every call walk the full scan window.
     */
    private BracketMatch getBracketMatch() {
        int line = document.getCursorLine();
        int column = document.getCursorColumn();
        if (bracketMatchCacheVersion == document.getVersion()
            && bracketMatchCacheLine == line
            && bracketMatchCacheColumn == column) {
            return bracketMatchCache;
        }

        BracketMatch computed = computeBracketMatch(line, column);
        bracketMatchCache = computed;
        bracketMatchCacheVersion = document.getVersion();
        bracketMatchCacheLine = line;
        bracketMatchCacheColumn = column;
        return computed;
    }

    private BracketMatch computeBracketMatch(int line, int column) {
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

    /**
     * Finds the bracket matching the one at {@code line}/{@code column}.
     *
     * <p>Scans outward from that bracket, not from the start of the document, and gives up after
     * {@link #BRACKET_MATCH_SCAN_LINES}. The previous version walked every line from zero on every
     * call, which is per-frame work proportional to the document.
     */
    private BracketMatch findMatchingBracket(int line, int column, char candidate) {
        int bracketIndex = BRACKET_CHARACTERS.indexOf(candidate);
        if (bracketIndex < 0) {
            return null;
        }

        // A bracket that is itself inside a string or comment has no match. getBracketMatch already
        // filters this, but the scan must not depend on its caller doing so.
        if (isIgnoredBracketPosition(line, column)) {
            return null;
        }

        boolean forward = bracketIndex % 2 == 0;
        char open = forward ? candidate : BRACKET_CHARACTERS.charAt(bracketIndex - 1);
        char close = forward ? BRACKET_CHARACTERS.charAt(bracketIndex + 1) : candidate;
        if (!forward) {
            return searchBackwardForBracket(line, column, open, close);
        }

        int lastLine = Math.min(document.getLineCount() - 1, line + BRACKET_MATCH_SCAN_LINES);
        int depth = 0;
        for (int currentLine = line; currentLine <= lastLine; currentLine++) {
            String text = document.getLine(currentLine);
            int from = currentLine == line ? column : 0;
            for (int currentColumn = from; currentColumn < text.length(); currentColumn++) {
                if (currentLine == line && currentColumn == column) {
                    depth = 1;
                    continue;
                }
                char current = text.charAt(currentColumn);
                if (current != open && current != close) {
                    continue;
                }
                // Only consult the ignore cache for actual brackets: it is far cheaper than testing
                // every character, and non-brackets cannot change the depth anyway.
                if (isIgnoredBracketPosition(currentLine, currentColumn)) {
                    continue;
                }
                if (current == open) {
                    depth++;
                } else {
                    depth--;
                    if (depth == 0) {
                        return new BracketMatch(line, column, currentLine, currentColumn);
                    }
                }
            }
        }
        return null;
    }

    /**
     * Walks backwards from a closing bracket to its opener.
     *
     * <p>Scans backwards directly rather than collecting every bracket from line 0 into a list and then
     * walking it in reverse, which allocated one object per bracket in the document.
     */
    private BracketMatch searchBackwardForBracket(int line, int column, char open, char close) {
        int firstLine = Math.max(0, line - BRACKET_MATCH_SCAN_LINES);
        int depth = 0;
        for (int currentLine = line; currentLine >= firstLine; currentLine--) {
            String text = document.getLine(currentLine);
            int from = currentLine == line ? Math.min(column, text.length() - 1) : text.length() - 1;
            for (int currentColumn = from; currentColumn >= 0; currentColumn--) {
                if (currentLine == line && currentColumn == column) {
                    depth = 1;
                    continue;
                }
                char current = text.charAt(currentColumn);
                if (current != open && current != close) {
                    continue;
                }
                if (isIgnoredBracketPosition(currentLine, currentColumn)) {
                    continue;
                }
                if (current == close) {
                    depth++;
                } else {
                    depth--;
                    if (depth == 0) {
                        return new BracketMatch(line, column, currentLine, currentColumn);
                    }
                }
            }
        }
        return null;
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

    /**
     * First visual row of a line. Reads the identity mapping directly when
     * {@link #rowMappingIsIdentity} holds, because the arrays are not maintained in that case.
     */
    private int visualRowStartOf(int line) {
        if (rowMappingIsIdentity) {
            return Math.max(0, line);
        }
        return line >= 0 && line < visualRowStart.length ? visualRowStart[line] : 0;
    }

    private int findLineByVisualRow(int row) {
        if (rowMappingIsIdentity) {
            return clamp(row, 0, Math.max(0, document.getLineCount() - 1));
        }
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
        if (line < 0 || line >= document.getLineCount()) {
            return;
        }

        LineLayout layout = layoutFor(line);
        int segment = clamp(row - visualRowStartOf(line), 0, layout.getVisualRowCount() - 1);
        int start = layout.segmentStarts.get(segment);
        int end = layout.segmentEnds.get(segment);
        // targetX is a distance from the text origin, so the indent comes off before the lookup — same
        // adjustment getCodePointAtRowX makes, and the reason Up/Down keeps its column across a wrap.
        int column = findColumnForX(layout, start, end, targetX - segmentIndentOffset(layout, segment));
        document.moveCursorTo(line, column);
    }

    /**
     * Extra x offset for a segment of this layout: the continuation indent on every row but the first.
     *
     * <p>Every path that turns a column into an x, and the hit test that goes back the other way, adds
     * this. Missing it on one of them is not a cosmetic bug — the caret lands on a different character
     * than the one clicked.
     */
    private float segmentIndentOffset(LineLayout layout, int segment) {
        return segment > 0 ? layout.continuationIndent : 0f;
    }

    private boolean isWrapOpportunity(String text, int index) {
        return isWrapOpportunity(text.charAt(index));
    }

    /** Whether a line may be broken immediately after this character. */
    private boolean isWrapOpportunity(char current) {
        if (Character.isWhitespace(current)) {
            return true;
        }
        return WRAP_OPPORTUNITY_CHARACTERS.indexOf(current) >= 0;
    }

    private int trimWrappedIndent(CharSequence text, int index) {
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
            return getTabWidth();
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
        char next = index + 1 < text.length() ? text.charAt(index + 1) : 0;
        return glyphAdvance(current, next);
    }

    /** Advance for a character pair, cached. A {@code next} of 0 means no following character. */
    private float glyphAdvance(char current, char next) {
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

    private float getTabWidth() {
        return Math.max(1f, glyphWidth(' ') * document.getIndentStrategy().indentWidth);
    }

    private float getTabAdvanceAtWidth(float currentWidth) {
        float tabWidth = getTabWidth();
        if (tabWidth <= 0f) {
            return 0f;
        }
        float remainder = currentWidth % tabWidth;
        if (remainder < 0f) {
            remainder += tabWidth;
        }
        float advance = tabWidth - remainder;
        if (advance < 0.5f) {
            advance = tabWidth;
        }
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

    private boolean beginEditorClip() {
        Stage stage = getStage();
        if (stage == null) {
            return false;
        }
        float x;
        float y;
        float width;
        float height;
        if (customClipArea) {
            if (clipAreaWidth <= 0f || clipAreaHeight <= 0f) {
                return false;
            }
            x = getX() + clipAreaX;
            y = getY() + clipAreaY;
            width = clipAreaWidth;
            height = clipAreaHeight;
        } else {
            // Full actor bounds in current draw space (Group may promote child x/y).
            x = getX();
            y = getY();
            width = getWidth();
            height = getHeight();
        }
        if (width <= 0f || height <= 0f) {
            return false;
        }

        // Expand in draw space first (glyph padding / antialias), then expand again after
        // projection with floor/ceil so ScissorStack Math.round + f2i cannot shave edges.
        float drawPad = Math.max(4f, lineHeight > 0f ? lineHeight * 0.2f : 4f);
        clipLocalBounds.set(x - drawPad, y - drawPad, width + drawPad * 2f, height + drawPad * 2f);

        Rectangle scissorBounds = Actor.POOLS.obtain(Rectangle.class);
        stage.calculateScissors(clipLocalBounds, scissorBounds);
        float left = (float) Math.floor(scissorBounds.x) - 2f;
        float bottom = (float) Math.floor(scissorBounds.y) - 2f;
        float right = (float) Math.ceil(scissorBounds.x + scissorBounds.width) + 2f;
        float top = (float) Math.ceil(scissorBounds.y + scissorBounds.height) + 2f;
        scissorBounds.set(left, bottom, Math.max(1f, right - left), Math.max(1f, top - bottom));
        if (ScissorStack.pushScissors(scissorBounds)) {
            return true;
        }
        Actor.POOLS.free(scissorBounds);
        return false;
    }

    private void endEditorClip() {
        Actor.POOLS.free(ScissorStack.popScissors());
    }

    private float getGutterWidth() {
        if (!lineNumbersVisible) {
            return 0f;
        }

        float foldIndicatorWidth = !foldRegionsByStart.isEmpty() ? getFoldIndicatorWidth() : 0f;
        float numberWidth = measureText(Integer.toString(Math.max(1, document.getLineCount())));
        float foldGap = foldIndicatorWidth > 0f ? style.gutterFoldIndicatorGap : 0f;
        return Math.max(
            style.gutterMinWidth,
            style.gutterLeftPadding + getLineMarkColumnWidth() + numberWidth + foldGap
                + foldIndicatorWidth + style.foldIndicatorRightPadding
        );
    }

    /** Width reserved for mark icons, zero when no mark has an icon. */
    private float getLineMarkColumnWidth() {
        return anyLineMarkHasIcon ? style.lineMarkIconSize + style.lineMarkIconGap : 0f;
    }

    private float getGutterRenderX() {
        return lineNumbersFixed ? 0f : -scrollX;
    }

    /**
     * Right edge of the line-number column. Derived from the gutter's right side, so widening the gutter
     * for a mark-icon column shifts the numbers right automatically: the numbers end up drawn at
     * {@code gutterLeftPadding + markColumnWidth}, immediately after the icons.
     */
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
        if (hasVisibleVerticalScrollbar()) {
            rightInset += style.scrollbarWidth + style.scrollbarMargin;
        }
        return Math.max(getHorizontalViewportLeft() + 1f, getWidth() - rightInset);
    }

    private float getHorizontalViewportWidth() {
        return Math.max(1f, getHorizontalViewportRight() - getHorizontalViewportLeft());
    }

    private float getWrapWidth() {
        float rightInset = style.textRightPadding;
        if (hasVisibleVerticalScrollbar()) {
            rightInset += style.scrollbarWidth + style.scrollbarGap;
        }
        return Math.max(
            1f,
            getWidth() - getTextStartX() - rightInset
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

    /**
     * Reports scroll movement and the touch gesture edges since the previous frame.
     *
     * <p>Comparing offsets rather than instrumenting the places that move them is the same trade
     * {@link #notifyCaretMovedIfNeeded()} makes, and it is what lets one callback cover wheel, scrollbar
     * thumb, keyboard caret movement, {@link #setScroll(float, float)}, pinch zoom and pane
     * synchronization without wiring up each of them. The cost is a frame of latency, and a change that
     * is undone within one frame going unreported.
     *
     * <p>Runs at the end of {@link #act(float)}, after the fling and bounce steps, so the offset it reads
     * is the one the frame settles on.
     */
    private void notifyScrollListenersIfNeeded() {
        if (Float.isNaN(lastNotifiedScrollX) || Float.isNaN(lastNotifiedScrollY)) {
            // First frame after construction, or after the first listener was added. Record where the
            // offset is instead of announcing a scroll from an origin nobody saw.
            lastNotifiedScrollX = scrollX;
            lastNotifiedScrollY = scrollY;
        }

        boolean dragging = draggingTouchScroll;
        boolean flinging = isFlinging();
        boolean dragChanged = dragging != touchScrollDragNotified;
        boolean flingChanged = flinging != flingNotified;

        // One snapshot for the whole update. None of the callbacks below can re-enter this method:
        // setScroll only moves the offset, which the next frame picks up.
        scrollListenerScratch.clear();
        scrollListenerScratch.addAll(scrollListeners);

        if (dragChanged || flingChanged) {
            touchScrollDragNotified = dragging;
            flingNotified = flinging;
            // Pan first, then fling, so a release that coasts arrives as finished-dragging followed by
            // started-coasting.
            if (dragChanged) {
                for (int i = 0; i < scrollListenerScratch.size; i++) {
                    CodeEditorScrollListener listener = scrollListenerScratch.get(i);
                    if (dragging) {
                        listener.onTouchScrollStarted(this);
                    } else {
                        listener.onTouchScrollFinished(this, flinging);
                    }
                }
            }
            if (flingChanged) {
                for (int i = 0; i < scrollListenerScratch.size; i++) {
                    CodeEditorScrollListener listener = scrollListenerScratch.get(i);
                    if (flinging) {
                        listener.onFlingStarted(this, touchScrollVelocityX, touchScrollVelocityY);
                    } else {
                        listener.onFlingFinished(this);
                    }
                }
            }
        }

        if (scrollX != lastNotifiedScrollX || scrollY != lastNotifiedScrollY) {
            float deltaX = scrollX - lastNotifiedScrollX;
            float deltaY = scrollY - lastNotifiedScrollY;
            lastNotifiedScrollX = scrollX;
            lastNotifiedScrollY = scrollY;
            boolean starting = !scrollInProgress;
            scrollInProgress = true;
            // Two passes rather than one, so every listener sees the start of the scroll before any of
            // them sees the movement itself.
            if (starting) {
                for (int i = 0; i < scrollListenerScratch.size; i++) {
                    scrollListenerScratch.get(i).onScrollStarted(this, scrollX, scrollY);
                }
            }
            for (int i = 0; i < scrollListenerScratch.size; i++) {
                scrollListenerScratch.get(i).onScrollChanged(this, scrollX, scrollY, deltaX, deltaY);
            }
        } else if (scrollInProgress && !dragging && !flinging) {
            // A finger held still mid-pan pauses the scroll; it does not end it. Waiting for the drag and
            // the fling to be over keeps one gesture to one start and end pair.
            scrollInProgress = false;
            for (int i = 0; i < scrollListenerScratch.size; i++) {
                scrollListenerScratch.get(i).onScrollFinished(this, scrollX, scrollY);
            }
        }
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
        if (!zoomEnabled) {
            return;
        }
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
        resetTouchScrollAxisLock();
        pendingTouchPress = false;
        longPressTriggered = false;
        draggingTouchScroll = false;
        draggingSelection = false;
        draggingStartHandle = false;
        draggingEndHandle = false;
        draggingCaretHandle = false;
        draggingScrollbar = false;
        draggingHorizontalScrollbar = false;
        touchScrollVelocityX = 0f;
        touchScrollVelocityY = 0f;
        handleDragFixedLine = -1;
        handleDragFixedColumn = -1;
        handleDragPointerOffsetX = 0f;
        handleDragPointerOffsetY = 0f;
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

    /**
     * Whether a bracket at this position is inside a string or comment.
     *
     * <p>Must stay cheap: bracket matching calls this for every character it scans. It therefore uses
     * a dedicated ignore-range cache rather than {@link #layoutFor(int)}, which would additionally
     * measure every glyph on the line and build its colour tokens — that turned a full-document
     * bracket scan into a freeze.
     */
    private boolean isIgnoredBracketPosition(int line, int column) {
        if (line < 0 || line >= document.getLineCount() || column < 0) {
            return false;
        }
        IntArray ranges = bracketIgnoreRangesFor(line);
        for (int i = 0; i + 1 < ranges.size; i += 2) {
            if (column >= ranges.get(i) && column < ranges.get(i + 1)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Bracket-ignore ranges for one line, as start/end pairs, cached per document version.
     *
     * <p>Runs the highlighter's lexer only — no style, so no colour lookups, and no glyph measurement.
     * The cache is a small ring keyed by line, sized for a bracket scan's working set rather than the
     * document.
     */
    private IntArray bracketIgnoreRangesFor(int line) {
        if (ignoreCacheVersion != document.getVersion()) {
            ignoreCacheLines.clear();
            ignoreCacheRanges.clear();
            ignoreCacheLastLine = -1;
            ignoreCacheLastRanges = null;
            ignoreCacheVersion = document.getVersion();
        }

        // Scans walk line by line, so a one-entry memo in front of the ring absorbs nearly every
        // lookup and keeps the ring's linear search off the hot path.
        if (line == ignoreCacheLastLine && ignoreCacheLastRanges != null) {
            return ignoreCacheLastRanges;
        }

        int cached = ignoreCacheLines.indexOf(line);
        if (cached >= 0) {
            IntArray hit = ignoreCacheRanges.get(cached);
            ignoreCacheLastLine = line;
            ignoreCacheLastRanges = hit;
            return hit;
        }

        IntArray ranges = new IntArray(8);
        if (!passwordMode) {
            IncrementalCodeHighlighter incremental = incrementalHighlighter();
            if (incremental != null) {
                scratchIgnoreCacheSpans.clear();
                incremental.highlightLine(
                    document.getLineSequence(line),
                    highlightStateAt(line),
                    null,
                    null,
                    scratchIgnoreCacheSpans
                );
                for (int i = 0; i < scratchIgnoreCacheSpans.size; i++) {
                    CodeBracketIgnoreSpan span = scratchIgnoreCacheSpans.get(i);
                    if (span != null && span.end > span.start) {
                        ranges.add(span.start);
                        ranges.add(span.end);
                    }
                }
            } else {
                ensureLegacyHighlight();
                if (line < bracketIgnoreLines.size) {
                    Array<CodeBracketIgnoreSpan> spans = bracketIgnoreLines.get(line);
                    for (int i = 0; i < spans.size; i++) {
                        CodeBracketIgnoreSpan span = spans.get(i);
                        if (span != null && span.end > span.start) {
                            ranges.add(span.start);
                            ranges.add(span.end);
                        }
                    }
                }
            }
        }

        if (ignoreCacheLines.size >= IGNORE_CACHE_SIZE) {
            ignoreCacheLines.removeIndex(0);
            ignoreCacheRanges.removeIndex(0);
        }
        ignoreCacheLines.add(line);
        ignoreCacheRanges.add(ranges);
        ignoreCacheLastLine = line;
        ignoreCacheLastRanges = ranges;
        return ranges;
    }

    private static boolean sameColor(Color first, Color second) {
        return first == second || (first != null && second != null && first.toIntBits() == second.toIntBits());
    }

    private CollapsedFoldDisplay buildCollapsedFoldDisplay(FoldRegion region, String placeholderText) {
        if (region == null) {
            return new CollapsedFoldDisplay(placeholderText, "", -1, -1, -1);
        }
        int trimStart = leadingTrimIndex(region.endText);
        if (trimStart >= region.endText.length()) {
            return new CollapsedFoldDisplay(placeholderText, "", -1, -1, -1);
        }
        String trimmedEnd = region.endText.substring(trimStart).trim();
        if (!looksLikeFoldClosingSuffix(trimmedEnd)) {
            return new CollapsedFoldDisplay(placeholderText, "", -1, -1, -1);
        }

        int sourceStart = trimStart;
        int maxSuffixLength = 48;
        int sourceEnd = Math.min(region.endText.length(), sourceStart + maxSuffixLength);
        String shownSuffix = region.endText.substring(sourceStart, sourceEnd).trim();
        if (shownSuffix.isEmpty()) {
            return new CollapsedFoldDisplay(placeholderText, "", -1, -1, -1);
        }

        return new CollapsedFoldDisplay(placeholderText, shownSuffix, region.endLine, sourceStart, sourceStart + shownSuffix.length());
    }

    private static boolean looksLikeFoldClosingSuffix(String text) {
        if (text == null || text.isEmpty()) {
            return false;
        }
        return text.startsWith("}")
            || text.startsWith("]")
            || text.startsWith(")")
            || text.startsWith("</")
            || text.startsWith("*/")
            || text.startsWith("?>");
    }

    private static int leadingTrimIndex(String text) {
        if (text == null) {
            return 0;
        }
        int index = 0;
        while (index < text.length() && Character.isWhitespace(text.charAt(index))) {
            index++;
        }
        return index;
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static float clamp(float value, float min, float max) {
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
        final String startText;
        final String endText;
        boolean collapsed;

        // startText/endText are references into the line snapshot, not copies, so building one of these is
        // two bounds checks and nothing else. It used to also compute a `key` string — two trim() calls, two
        // substrings and a five-part concat — that nothing ever read. One of these is constructed per region
        // per structure pass, so on a 100k-line file with 28k regions that dead field was the single largest
        // cost in the pass. Keep this constructor allocation-free.
        FoldRegion(int startLine, int endLine, int depth, Array<String> lines) {
            this.startLine = startLine;
            this.endLine = endLine;
            this.depth = depth;
            this.startText = startLine >= 0 && startLine < lines.size ? lines.get(startLine) : "";
            this.endText = endLine >= 0 && endLine < lines.size ? lines.get(endLine) : "";
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
        /** Assigned after construction by {@code buildLineContent}, which fills the ignore ranges too. */
        Array<HighlightToken> tokens;
        final IntArray segmentStarts = new IntArray();
        final IntArray segmentEnds = new IntArray();
        float[] prefixWidths;
        /**
         * Pixels every segment after the first is pushed right by, or 0 when off or not wrapped.
         *
         * <p>Cached here rather than recomputed per draw because it is read once per visible row and
         * again per hit test, and because reading the one number the wrap used rules out the draw and
         * the wrap disagreeing.
         */
        float continuationIndent;

        LineLayout(String text, int indentLevel, Array<HighlightToken> tokens) {
            this.text = text;
            this.indentLevel = indentLevel;
            this.tokens = tokens;
        }

        /** Placeholder returned for out-of-range lines so callers never see null. */
        static LineLayout empty() {
            LineLayout layout = new LineLayout("", 0, new Array<HighlightToken>(0));
            layout.prefixWidths = new float[] {0f};
            layout.segmentStarts.add(0);
            layout.segmentEnds.add(0);
            return layout;
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
                char current = text.charAt(i);
                float advance = current == '\t'
                    ? editor.getTabAdvanceAtWidth(prefixWidths[i])
                    : editor.glyphAdvance(text, i);
                prefixWidths[i + 1] = prefixWidths[i] + advance;
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

    private static final class CollapsedFoldDisplay {
        final String placeholderText;
        final String suffixText;
        final int suffixLine;
        final int suffixStart;
        final int suffixEnd;

        CollapsedFoldDisplay(String placeholderText, String suffixText, int suffixLine, int suffixStart, int suffixEnd) {
            this.placeholderText = placeholderText == null ? "" : placeholderText;
            this.suffixText = suffixText == null ? "" : suffixText;
            this.suffixLine = suffixLine;
            this.suffixStart = suffixStart;
            this.suffixEnd = suffixEnd;
        }

        boolean hasSuffix() {
            return !suffixText.isEmpty() && suffixStart >= 0 && suffixEnd > suffixStart;
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
        /** Caret/selection tip (local). */
        final float x;
        final float y;
        /** Drawable bottom-left (local). */
        final float drawX;
        final float drawY;
        /** Visual bulb center (local); must match drawCornerBubbleHandle. */
        final float hitX;
        final float hitY;

        HandlePlacement(float x, float y, float drawX, float drawY, float hitX, float hitY) {
            this.x = x;
            this.y = y;
            this.drawX = drawX;
            this.drawY = drawY;
            this.hitX = hitX;
            this.hitY = hitY;
        }
    }

    private enum EditIntent {
        INSERT,
        DELETE,
        BACKSPACE,
        ENTER,
        TAB,
        TYPE
    }

    private final class SelectionHandleOverlay extends Actor {
        SelectionHandleOverlay() {
            setTouchable(com.badlogic.gdx.scenes.scene2d.Touchable.enabled);
            addListener(new InputListener() {
                @Override
                public boolean touchDown(InputEvent event, float x, float y, int pointer, int button) {
                    if (!shouldHandleOverlayTouch(button) || !isStagePositionNearSelectionHandle(x, y)) {
                        return false;
                    }
                    return beginSelectionHandleOverlayDrag(x, y);
                }

                @Override
                public void touchDragged(InputEvent event, float x, float y, int pointer) {
                    updateSelectionHandleOverlayDrag(x, y);
                }

                @Override
                public void touchUp(InputEvent event, float x, float y, int pointer, int button) {
                    endSelectionHandleOverlayDrag();
                }
            });
        }

        @Override
        public Actor hit(float x, float y, boolean touchable) {
            if (!isVisible() || (touchable && getTouchable() != com.badlogic.gdx.scenes.scene2d.Touchable.enabled)) {
                return null;
            }
            return isStagePositionNearSelectionHandle(x, y) ? this : null;
        }

        @Override
        public void draw(Batch batch, float parentAlpha) {
            if (!isVisible()) {
                return;
            }
            Vector2 stageOrigin = getStageOrigin();
            drawSelectionHandles(batch, stageOrigin.x, stageOrigin.y);
        }

        private boolean shouldHandleOverlayTouch(int button) {
            return useTouchInteractions() && button == Input.Buttons.LEFT;
        }
    }

    public static class CodeEditorStyle {
        private final Array<Texture> ownedTextures = new Array<>();
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
        public Color diagnosticErrorColor = new Color(0.94f, 0.33f, 0.31f, 1f);
        public Color diagnosticWarningColor = new Color(0.96f, 0.76f, 0.26f, 1f);
        public Color diagnosticInformationColor = new Color(0.37f, 0.71f, 0.99f, 1f);
        public Color diagnosticHintColor = new Color(0.55f, 0.63f, 0.71f, 1f);
        public Color[] rainbowBracketColors = copyPalette(null, DEFAULT_RAINBOW_BRACKET_PALETTE);
        public Color[] rainbowGuideColors = copyPalette(null, DEFAULT_RAINBOW_GUIDE_PALETTE);
        /**
         * Colours for semantic token types, overriding both the fallbacks below and the lexical colour.
         *
         * <p>Empty by default, and a missing entry is not an error: see
         * {@link #getSemanticTokenColor(CodeSemanticTokenType)} for what happens instead. A map rather
         * than two dozen fields because most themes will want to set two or three of them.
         */
        public final ObjectMap<CodeSemanticTokenType, Color> semanticTokenColors = new ObjectMap<>();
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
        public Drawable selectionHandleStart;
        public Drawable selectionHandleEnd;
        public Drawable selectionHandleCaret;
        public Drawable magnifierBackground;
        public Drawable bracketMatch;
        public Drawable guide;
        public Drawable foldExpanded;
        public Drawable foldCollapsed;
        public Drawable scrollbarTrack;
        public Drawable scrollbarKnob;
        public Drawable foldBadge;
        public Texture whitePixelTexture;
        /** Vertical offset of a diagnostic squiggle from the row bottom. */
        public float diagnosticSquiggleOffset = 1f;
        /** Width of one zig or zag. */
        public float diagnosticSquiggleStep = 2f;
        /** Height difference between a zig and a zag. */
        public float diagnosticSquiggleAmplitude = 2f;
        public float diagnosticSquiggleThickness = 1f;
        /** Width of the severity tick drawn in the gutter; 0 disables it. */
        public float diagnosticGutterMarkWidth = 3f;
        /** Size of a {@link CodeLineMark} icon in the gutter. */
        public float lineMarkIconSize = 12f;
        /** Gap between the mark icon column and the line numbers. */
        public float lineMarkIconGap = 4f;
        /** Width of a {@link CodeLineMark} colour bar; 0 disables it. */
        public float lineMarkBarWidth = 3f;
        /** Alpha applied to a mark's colour when it tints a whole line. */
        public float lineMarkHighlightAlpha = 0.18f;
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
        public float magnifierWidth = 0f;
        public float magnifierHeight = 0f;
        public float magnifierContentPadding = 0f;

        public CodeEditorStyle() {
        }

        public static ThemeBuilder theme(BitmapFont font) {
            return new ThemeBuilder(font);
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
            this.diagnosticErrorColor = new Color(style.diagnosticErrorColor);
            this.diagnosticWarningColor = new Color(style.diagnosticWarningColor);
            this.diagnosticInformationColor = new Color(style.diagnosticInformationColor);
            this.diagnosticHintColor = new Color(style.diagnosticHintColor);
            this.rainbowBracketColors = copyPalette(style.rainbowBracketColors, DEFAULT_RAINBOW_BRACKET_PALETTE);
            this.rainbowGuideColors = copyPalette(style.rainbowGuideColors, DEFAULT_RAINBOW_GUIDE_PALETTE);
            // Copied by value: a theme derived from another must be recolourable without reaching back
            // into the original, which is the same reason every Color above is copied rather than shared.
            for (ObjectMap.Entry<CodeSemanticTokenType, Color> entry : style.semanticTokenColors) {
                this.semanticTokenColors.put(entry.key, new Color(entry.value));
            }
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
            this.selectionHandleStart = style.selectionHandleStart;
            this.selectionHandleEnd = style.selectionHandleEnd;
            this.selectionHandleCaret = style.selectionHandleCaret;
            this.magnifierBackground = style.magnifierBackground;
            this.bracketMatch = style.bracketMatch;
            this.guide = style.guide;
            this.foldExpanded = style.foldExpanded;
            this.foldCollapsed = style.foldCollapsed;
            this.scrollbarTrack = style.scrollbarTrack;
            this.scrollbarKnob = style.scrollbarKnob;
            this.foldBadge = style.foldBadge;
            this.whitePixelTexture = style.whitePixelTexture;
            this.diagnosticSquiggleOffset = style.diagnosticSquiggleOffset;
            this.diagnosticSquiggleStep = style.diagnosticSquiggleStep;
            this.diagnosticSquiggleAmplitude = style.diagnosticSquiggleAmplitude;
            this.diagnosticSquiggleThickness = style.diagnosticSquiggleThickness;
            this.diagnosticGutterMarkWidth = style.diagnosticGutterMarkWidth;
            this.lineMarkIconSize = style.lineMarkIconSize;
            this.lineMarkIconGap = style.lineMarkIconGap;
            this.lineMarkBarWidth = style.lineMarkBarWidth;
            this.lineMarkHighlightAlpha = style.lineMarkHighlightAlpha;
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
            this.magnifierWidth = style.magnifierWidth;
            this.magnifierHeight = style.magnifierHeight;
            this.magnifierContentPadding = style.magnifierContentPadding;
        }

        /**
         * Colour for a semantic token type: an explicit {@link #semanticTokenColors} entry, else a
         * fallback derived from the lexical palette, else null.
         *
         * <p>Null means "leave the lexical colour alone", and it is the deliberate answer for the types
         * a lexer cannot produce at all — {@link CodeSemanticTokenType#VARIABLE},
         * {@link CodeSemanticTokenType#PARAMETER}, {@link CodeSemanticTokenType#PROPERTY} and the
         * callable kinds. Those are exactly the distinctions semantic highlighting exists to draw, so
         * inventing colours for them here would mean this library, not the theme, deciding what a field
         * looks like — and it would change the appearance of every existing theme the moment tokens were
         * pushed. Set them and they apply; leave them and identifiers keep the colour they have today.
         *
         * <p>The types that <em>do</em> get a fallback are the ones with an exact lexical counterpart, so
         * the fallback cannot disagree with the theme: a semantic {@code STRING} is the same colour the
         * highlighter would already have used. Their value is in the cases a lexer gets wrong, such as
         * an embedded language or a contextual keyword.
         *
         * <p>Call {@link #applyDefaultSemanticTokenColors()} for a ready-made set covering the rest.
         */
        public Color getSemanticTokenColor(CodeSemanticTokenType type) {
            if (type == null) {
                return null;
            }
            Color explicit = semanticTokenColors.get(type);
            if (explicit != null) {
                return explicit;
            }
            switch (type) {
                case TYPE:
                case CLASS:
                case ENUM:
                case INTERFACE:
                case STRUCT:
                case TYPE_PARAMETER:
                    return typeColor;
                case KEYWORD:
                case MODIFIER:
                    return keywordColor;
                case COMMENT:
                    return commentColor;
                case STRING:
                case REGEXP:
                    return stringColor;
                case NUMBER:
                    return numberColor;
                case DECORATOR:
                    return annotationColor;
                case ENUM_MEMBER:
                    return literalColor;
                default:
                    // NAMESPACE, PARAMETER, VARIABLE, PROPERTY, EVENT, FUNCTION, METHOD, MACRO, OPERATOR.
                    return null;
            }
        }

        /**
         * Fills in {@link #semanticTokenColors} for the types that otherwise resolve to null, so
         * pushing tokens has a visible effect without the caller picking colours first.
         *
         * <p>Derived from the existing palette rather than being a new set of constants: fields lean
         * towards the annotation colour, locals and parameters towards the body text colour with
         * parameters lightened, callables towards the type colour. That keeps a custom theme's character
         * instead of dropping a fixed dark-theme palette on top of it. Existing entries are left alone.
         *
         * <p>These are a starting point, not a recommendation. A theme with a considered opinion should
         * put its own colours in the map.
         */
        public CodeEditorStyle applyDefaultSemanticTokenColors() {
            putSemanticTokenColorIfAbsent(CodeSemanticTokenType.NAMESPACE, shade(typeColor, 0.82f));
            putSemanticTokenColorIfAbsent(CodeSemanticTokenType.PROPERTY, shade(annotationColor, 0.9f));
            putSemanticTokenColorIfAbsent(CodeSemanticTokenType.EVENT, shade(annotationColor, 0.9f));
            putSemanticTokenColorIfAbsent(CodeSemanticTokenType.VARIABLE, new Color(fontColor));
            putSemanticTokenColorIfAbsent(CodeSemanticTokenType.PARAMETER, shade(fontColor, 1.08f));
            putSemanticTokenColorIfAbsent(CodeSemanticTokenType.FUNCTION, shade(typeColor, 1.12f));
            putSemanticTokenColorIfAbsent(CodeSemanticTokenType.METHOD, shade(typeColor, 1.12f));
            putSemanticTokenColorIfAbsent(CodeSemanticTokenType.MACRO, shade(keywordColor, 1.1f));
            putSemanticTokenColorIfAbsent(CodeSemanticTokenType.OPERATOR, new Color(fontColor));
            return this;
        }

        /** Sets one semantic token colour, copying it so a later change to the argument cannot leak in. */
        public CodeEditorStyle setSemanticTokenColor(CodeSemanticTokenType type, Color color) {
            if (type == null) {
                return this;
            }
            if (color == null) {
                semanticTokenColors.remove(type);
            } else {
                semanticTokenColors.put(type, new Color(color));
            }
            return this;
        }

        private void putSemanticTokenColorIfAbsent(CodeSemanticTokenType type, Color color) {
            if (!semanticTokenColors.containsKey(type)) {
                semanticTokenColors.put(type, color);
            }
        }

        /** {@code source} scaled towards white or black, alpha kept. */
        private static Color shade(Color source, float factor) {
            return new Color(
                Math.min(1f, source.r * factor),
                Math.min(1f, source.g * factor),
                Math.min(1f, source.b * factor),
                source.a);
        }

        public void disposeGeneratedResources() {
            for (Texture texture : ownedTextures) {
                if (texture != null) {
                    texture.dispose();
                }
            }
            ownedTextures.clear();
        }

        private void addOwnedTexture(Texture texture) {
            if (texture != null && !ownedTextures.contains(texture, true)) {
                ownedTextures.add(texture);
            }
        }

        public static final class ThemeBuilder {
            private final BitmapFont font;
            private Texture whitePixelTexture;
            private boolean ownsWhitePixelTexture;
            private Drawable magnifierBackground;
            private final Color accentColor = new Color(0.274f, 0.561f, 0.898f, 1f);
            private final Color backgroundColor = new Color(0.055f, 0.078f, 0.109f, 1f);
            private final Color gutterColor = new Color(0.028f, 0.039f, 0.051f, 1f);
            private final Color gutterDividerColor = new Color(0.204f, 0.294f, 0.392f, 1f);
            private final Color textColor = new Color(0.93f, 0.96f, 0.99f, 1f);
            private final Color gutterTextColor = new Color(0.64f, 0.72f, 0.8f, 1f);
            private final Color messageTextColor = new Color(0.54f, 0.64f, 0.74f, 1f);
            private final Color disabledTextColor = new Color(0.55f, 0.61f, 0.68f, 1f);
            private final Color magnifierBackgroundColor = new Color(0.96f, 0.98f, 1f, 0.98f);
            private float scrollbarWidth = -1f;
            private float selectionHandleRadius = -1f;
            private float textBaselineOffset = -6f;
            private float magnifierWidth = -1f;
            private float magnifierHeight = -1f;

            ThemeBuilder(BitmapFont font) {
                if (font == null) {
                    throw new IllegalArgumentException("font cannot be null");
                }
                this.font = font;
            }

            public ThemeBuilder themeColor(Color color) {
                if (color == null) {
                    return this;
                }
                accentColor.set(color);
                return this;
            }

            public ThemeBuilder backgroundColor(Color color) {
                if (color == null) {
                    return this;
                }
                backgroundColor.set(color);
                return this;
            }

            public ThemeBuilder gutterColor(Color color) {
                if (color == null) {
                    return this;
                }
                gutterColor.set(color);
                return this;
            }

            public ThemeBuilder textColor(Color color) {
                if (color == null) {
                    return this;
                }
                textColor.set(color);
                return this;
            }

            public ThemeBuilder gutterTextColor(Color color) {
                if (color == null) {
                    return this;
                }
                gutterTextColor.set(color);
                return this;
            }

            public ThemeBuilder scrollbarWidth(float scrollbarWidth) {
                this.scrollbarWidth = Math.max(4f, scrollbarWidth);
                return this;
            }

            public ThemeBuilder selectionHandleRadius(float selectionHandleRadius) {
                this.selectionHandleRadius = Math.max(6f, selectionHandleRadius);
                return this;
            }

            public ThemeBuilder textBaselineOffset(float textBaselineOffset) {
                this.textBaselineOffset = textBaselineOffset;
                return this;
            }

            public ThemeBuilder whitePixelTexture(Texture whitePixelTexture) {
                this.whitePixelTexture = whitePixelTexture;
                this.ownsWhitePixelTexture = false;
                return this;
            }

            public ThemeBuilder magnifierWidth(float magnifierWidth) {
                this.magnifierWidth = Math.max(24f, magnifierWidth);
                return this;
            }

            public ThemeBuilder magnifierHeight(float magnifierHeight) {
                this.magnifierHeight = Math.max(24f, magnifierHeight);
                return this;
            }

            public ThemeBuilder magnifierBackgroundColor(Color color) {
                if (color != null) {
                    this.magnifierBackgroundColor.set(color);
                }
                return this;
            }

            public ThemeBuilder magnifierBackground(Drawable magnifierBackground) {
                this.magnifierBackground = magnifierBackground;
                return this;
            }

            public CodeEditorStyle build() {
                CodeEditorStyle style = new CodeEditorStyle();
                style.font = font;

                Texture pixel = whitePixelTexture;
                if (pixel == null) {
                    pixel = createWhitePixelTexture();
                    ownsWhitePixelTexture = true;
                }

                style.whitePixelTexture = pixel;
                if (ownsWhitePixelTexture) {
                    style.addOwnedTexture(pixel);
                }

                float fontLineHeight = Math.max(12f, font.getLineHeight());
                float derivedScrollbarWidth = scrollbarWidth > 0f
                    ? scrollbarWidth
                    : Math.max(6f, Math.round(fontLineHeight * 0.26f));
                float derivedHandleRadius = selectionHandleRadius > 0f
                    ? selectionHandleRadius
                    : Math.max(6f, centimetersToUiUnits(0.15f));
                float derivedMagnifierHeight = magnifierHeight > 0f ? magnifierHeight : 0f;
                float derivedMagnifierWidth = magnifierWidth > 0f ? magnifierWidth : 0f;
                float derivedMagnifierPadding = Math.max(4f, fontLineHeight * 0.18f);
                float derivedFoldIndicatorSize = Math.max(10f, fontLineHeight * 0.42f);
                float derivedRowPadding = Math.max(5f, fontLineHeight * 0.22f);
                float derivedTextPadding = Math.max(12f, fontLineHeight * 0.7f);
                float derivedGutterPadding = Math.max(6f, fontLineHeight * 0.32f);
                float derivedFoldGap = Math.max(8f, fontLineHeight * 0.36f);
                float derivedFoldRightPadding = Math.max(7f, fontLineHeight * 0.3f);
                float derivedGutterMinWidth = Math.max(40f, fontLineHeight * 2.4f);

                Color focusedBackground = mix(backgroundColor, accentColor, 0.045f, 1f);
                Color disabledBackground = mix(backgroundColor, Color.BLACK, 0.22f, 1f);
                Color currentLineColor = mix(backgroundColor, accentColor, 0.14f, 1f);
                Color currentBlockColor = mix(backgroundColor, accentColor, 0.15f, 0.72f);
                Color selectionColor = mix(accentColor, Color.WHITE, 0.08f, 0.58f);
                Color searchColor = mix(accentColor, new Color(1f, 0.82f, 0.33f, 1f), 0.72f, 0.26f);
                Color currentSearchColor = mix(accentColor, new Color(1f, 0.86f, 0.42f, 1f), 0.72f, 0.54f);
                Color guideColor = mix(accentColor, backgroundColor, 0.42f, 0.18f);
                Color bracketMatchColor = mix(accentColor, new Color(1f, 0.82f, 0.36f, 1f), 0.45f, 0.34f);
                Color scrollbarTrackColor = mix(backgroundColor, Color.BLACK, 0.12f, 1f);
                Color scrollbarKnobColor = mix(accentColor, backgroundColor, 0.36f, 1f);
                Color foldBadgeColor = mix(backgroundColor, accentColor, 0.24f, 1f);
                Color gutterDivider = mix(gutterDividerColor, accentColor, 0.18f, 1f);

                style.background = createSolidDrawable(pixel, backgroundColor);
                style.focusedBackground = createSolidDrawable(pixel, focusedBackground);
                style.disabledBackground = createSolidDrawable(pixel, disabledBackground);
                style.gutterBackground = createGutterDrawable(pixel, gutterColor, gutterDivider, 2f);
                style.currentBlock = createSolidDrawable(pixel, currentBlockColor);
                style.currentLine = createSolidDrawable(pixel, currentLineColor);
                style.cursor = createSolidDrawable(pixel, accentColor);
                style.selection = createSolidDrawable(pixel, selectionColor);
                style.searchHighlight = createSolidDrawable(pixel, searchColor);
                style.currentSearchHighlight = createSolidDrawable(pixel, currentSearchColor);
                style.selectionHandleStart = createCornerSelectionHandleDrawable(
                    pixel,
                    HANDLE_CORNER_TOP_RIGHT,
                    accentColor
                );
                style.selectionHandleEnd = createCornerSelectionHandleDrawable(
                    pixel,
                    HANDLE_CORNER_TOP_LEFT,
                    accentColor
                );
                style.selectionHandleCaret = createCornerSelectionHandleDrawable(pixel,HANDLE_CORNER_TOP_CENTER, accentColor);
                style.selectionHandle = style.selectionHandleEnd;
                style.magnifierBackground = magnifierBackground != null
                    ? magnifierBackground
                    : createRoundedRectDrawable(pixel, magnifierBackgroundColor, Math.max(8f, fontLineHeight * 0.45f));
                style.bracketMatch = createSolidDrawable(pixel, bracketMatchColor);
                style.guide = createSolidDrawable(pixel, guideColor);
                style.foldExpanded = createChevronDrawable(pixel, false, mix(textColor, accentColor, 0.18f, 1f));
                style.foldCollapsed = createChevronDrawable(pixel, true, mix(textColor, accentColor, 0.18f, 1f));
                style.scrollbarTrack = createSolidDrawable(pixel, scrollbarTrackColor);
                style.scrollbarKnob = createSolidDrawable(pixel, scrollbarKnobColor);
                style.foldBadge = createSolidDrawable(pixel, foldBadgeColor);

                style.fontColor = new Color(textColor);
                style.gutterFontColor = new Color(gutterTextColor);
                style.messageFontColor = new Color(messageTextColor);
                style.disabledFontColor = new Color(disabledTextColor);
                style.keywordColor = mix(accentColor, new Color(0.29f, 0.75f, 0.98f, 1f), 0.58f, 1f);
                style.typeColor = mix(accentColor, new Color(0.52f, 0.86f, 0.64f, 1f), 0.28f, 1f);
                style.stringColor = new Color(0.984f, 0.63f, 0.396f, 1f);
                style.commentColor = new Color(0.486f, 0.588f, 0.486f, 1f);
                style.numberColor = new Color(0.914f, 0.761f, 0.384f, 1f);
                style.annotationColor = mix(accentColor, new Color(0.965f, 0.522f, 0.722f, 1f), 0.32f, 1f);
                style.literalColor = mix(accentColor, new Color(0.875f, 0.506f, 0.506f, 1f), 0.26f, 1f);

                style.scrollbarWidth = derivedScrollbarWidth;
                style.scrollbarHitWidth = Math.max(style.scrollbarWidth * 2.5f, DEFAULT_SCROLLBAR_HIT_WIDTH);
                style.scrollbarMinThumbSize = Math.max(24f, derivedScrollbarWidth * 3f);
                style.selectionHandleRadius = derivedHandleRadius;
                // Keep touch slightly larger than the visual bulb only (not radius-scaled huge).
                style.selectionHandleTouchRadiusMultiplier = 1.25f;
                style.magnifierWidth = derivedMagnifierWidth;
                style.magnifierHeight = derivedMagnifierHeight;
                style.magnifierContentPadding = derivedMagnifierPadding;
                style.foldIndicatorSize = derivedFoldIndicatorSize;
                style.textBaselineOffset = textBaselineOffset;
                style.gutterMinWidth = derivedGutterMinWidth;
                style.gutterLeftPadding = derivedGutterPadding;
                style.gutterFoldIndicatorGap = derivedFoldGap;
                style.foldIndicatorRightPadding = derivedFoldRightPadding;
                style.textLeftPadding = derivedTextPadding;
                style.textRightPadding = Math.max(derivedTextPadding + 6f, fontLineHeight * 1.02f);
                style.rowPadding = derivedRowPadding;
                style.guideSpacing = DEFAULT_GUIDE_SPACING;
                style.guideOffsetX = DEFAULT_GUIDE_OFFSET_X;
                return style;
            }

            private static Texture createWhitePixelTexture() {
                Pixmap pixmap = new Pixmap(1, 1, Pixmap.Format.RGBA8888);
                pixmap.setColor(Color.WHITE);
                pixmap.fill();
                Texture texture = new Texture(pixmap);
                pixmap.dispose();
                return texture;
            }

            private static float centimetersToUiUnits(float centimeters) {
                float ppiX = Gdx.graphics.getPpiX();
                float ppiY = Gdx.graphics.getPpiY();
                float ppi;
                if (ppiX > 0f && ppiY > 0f) {
                    ppi = (ppiX + ppiY) * 0.5f;
                } else {
                    float density = Gdx.graphics.getDensity();
                    ppi = density > 0f ? density * 160f : 96f;
                }
                return centimeters * ppi / 2.54f;
            }

            private static Color mix(Color first, Color second, float secondAmount, float alpha) {
                float clamped = Math.max(0f, Math.min(1f, secondAmount));
                return new Color(
                    first.r + (second.r - first.r) * clamped,
                    first.g + (second.g - first.g) * clamped,
                    first.b + (second.b - first.b) * clamped,
                    alpha
                );
            }

            private static Drawable createSolidDrawable(final Texture pixel, final Color color) {
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
                        batch.draw(pixel, x, y, width, height);
                        batch.setColor(previousR, previousG, previousB, previousA);
                    }
                };
            }

            private static Drawable createRoundedRectDrawable(final Texture pixel, final Color color, final float radius) {
                final Color tint = new Color(color);
                return new BaseDrawable() {
                    @Override
                    public void draw(Batch batch, float x, float y, float width, float height) {
                        float clampedRadius = Math.max(1f, Math.min(Math.min(width, height) * 0.5f, radius));
                        float centerWidth = Math.max(0f, width - clampedRadius * 2f);
                        float centerHeight = Math.max(0f, height - clampedRadius * 2f);
                        drawRect(batch, pixel, x + clampedRadius, y, centerWidth, height, tint);
                        drawRect(batch, pixel, x, y + clampedRadius, clampedRadius, centerHeight, tint);
                        drawRect(batch, pixel, x + width - clampedRadius, y + clampedRadius, clampedRadius, centerHeight, tint);
                        drawCircle(batch, pixel, x + clampedRadius, y + clampedRadius, clampedRadius, tint);
                        drawCircle(batch, pixel, x + width - clampedRadius, y + clampedRadius, clampedRadius, tint);
                        drawCircle(batch, pixel, x + clampedRadius, y + height - clampedRadius, clampedRadius, tint);
                        drawCircle(batch, pixel, x + width - clampedRadius, y + height - clampedRadius, clampedRadius, tint);
                    }
                };
            }

            private static Drawable createGutterDrawable(final Texture pixel, final Color fillColor, final Color borderColor, final float borderWidth) {
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
                        batch.draw(pixel, x, y, width, height);
                        float actualBorder = Math.max(1f, Math.min(borderWidth, width));
                        batch.setColor(border);
                        batch.draw(pixel, x + width - actualBorder, y, actualBorder, height);
                        batch.setColor(previousR, previousG, previousB, previousA);
                    }
                };
            }

            private static Drawable createChevronDrawable(final Texture pixel, final boolean collapsed, final Color color) {
                final Color tint = new Color(color);
                return new BaseDrawable() {
                    {
                        setMinWidth(12f);
                        setMinHeight(12f);
                    }

                    @Override
                    public void draw(Batch batch, float x, float y, float width, float height) {
                        float thickness = Math.max(1.35f, Math.min(width, height) * 0.14f);
                        if (collapsed) {
                            drawSegment(batch, pixel, x + width * 0.34f, y + height * 0.22f, x + width * 0.7f, y + height * 0.5f, thickness, tint);
                            drawSegment(batch, pixel, x + width * 0.34f, y + height * 0.78f, x + width * 0.7f, y + height * 0.5f, thickness, tint);
                        } else {
                            drawSegment(batch, pixel, x + width * 0.22f, y + height * 0.64f, x + width * 0.5f, y + height * 0.3f, thickness, tint);
                            drawSegment(batch, pixel, x + width * 0.78f, y + height * 0.64f, x + width * 0.5f, y + height * 0.3f, thickness, tint);
                        }
                    }
                };
            }

            private static Drawable createCornerSelectionHandleDrawable(
                final Texture pixel,
                final int cornerMode,
                final Color outerColor
            ) {
                final Color outer = new Color(outerColor);
                return new BaseDrawable() {
                    @Override
                    public void draw(Batch batch, float x, float y, float width, float height) {
                        drawCornerBubbleHandle(batch, pixel, x, y, width, height, cornerMode, outer);
                    }
                };
            }


            private static void drawCornerBubbleHandle(
                Batch batch,
                Texture pixel,
                float x,
                float y,
                float width,
                float height,
                int cornerMode,
                Color color
            ) {
                // Square drawable: circle center = (x + w/2, y + h/2) == HandlePlacement.hitX/hitY.
                float size = Math.min(width, height);
                float radius = size * 0.5f;
                float centerX = x + width * 0.5f;
                float centerY = y + height * 0.5f;
                float top = y + height;
                if (cornerMode == HANDLE_CORNER_TOP_LEFT) {
                    // End: stem fills top-left quadrant toward caret.
                    drawRect(batch, pixel, x, centerY, radius, top - centerY, color);
                    drawCircle(batch, pixel, centerX, centerY, radius, color);
                } else if (cornerMode == HANDLE_CORNER_TOP_RIGHT) {
                    // Start: stem fills top-right quadrant toward caret.
                    drawRect(batch, pixel, centerX, centerY, radius, top - centerY, color);
                    drawCircle(batch, pixel, centerX, centerY, radius, color);
                } else {
                    // Caret: teardrop tip at top-center, then circle.
                    float halfBase = radius * 0.85f;
                    drawTriangle(
                        batch,
                        pixel,
                        centerX,
                        top,
                        centerX - halfBase,
                        centerY,
                        centerX + halfBase,
                        centerY,
                        color
                    );
                    drawCircle(batch, pixel, centerX, centerY, radius, color);
                }
            }

            private static void drawRect(Batch batch, Texture pixel, float x, float y, float width, float height, Color color) {
                Color previous = batch.getColor();
                float previousR = previous.r;
                float previousG = previous.g;
                float previousB = previous.b;
                float previousA = previous.a;
                batch.setColor(color);
                batch.draw(pixel, x, y, width, height);
                batch.setColor(previousR, previousG, previousB, previousA);
            }

            static float[] triangle = new float[4*5];
            private static void drawTriangle(Batch batch, Texture pixel, float x1, float y1, float x2, float y2,float x3,float y3, Color color) {
                Color previous = batch.getColor();
                float previousR = previous.r;
                float previousG = previous.g;
                float previousB = previous.b;
                float previousA = previous.a;
                float colorFloatBits = color.toFloatBits();
                float x4=(x2+x3)/2;
                float y4=(y2+y3)/2;
                batch.setColor(color);
                triangle[0] = x1;
                triangle[1] = y1;
                triangle[2] = colorFloatBits;
                triangle[3] = 0;
                triangle[4] = 0;
                triangle[5] = x2;
                triangle[6] = y2;
                triangle[7] = colorFloatBits;
                triangle[8] = 0;
                triangle[9] = 0;
                triangle[10] = x4;
                triangle[11] = y4;
                triangle[12] = colorFloatBits;
                triangle[13] = 0;
                triangle[14] = 0;
                triangle[15] = x3;
                triangle[16] = y3;
                triangle[17] = colorFloatBits;
                triangle[18] = 0;
                triangle[19] = 0;
                batch.draw(pixel, triangle, 0, triangle.length);
                batch.setColor(previousR, previousG, previousB, previousA);
            }

            private static void drawCircle(Batch batch, Texture pixel, float centerX, float centerY, float radius, Color color) {
                if (radius <= 0f) {
                    return;
                }
                Color previous = batch.getColor();
                float previousR = previous.r;
                float previousG = previous.g;
                float previousB = previous.b;
                float previousA = previous.a;
                batch.setColor(color);
                int steps = Math.max(8, Math.round(radius * 2.4f));
                float diameter = radius * 2f;
                float stripHeight = diameter / steps;
                for (int i = 0; i < steps; i++) {
                    float stripCenterY = centerY - radius + stripHeight * (i + 0.5f);
                    float dy = stripCenterY - centerY;
                    float halfWidth = (float) Math.sqrt(Math.max(0f, radius * radius - dy * dy));
                    batch.draw(
                        pixel,
                        centerX - halfWidth,
                        stripCenterY - stripHeight * 0.5f,
                        halfWidth * 2f,
                        stripHeight + 0.5f
                    );
                }
                batch.setColor(previousR, previousG, previousB, previousA);
            }

            private static void drawSegment(Batch batch, Texture pixel, float x1, float y1, float x2, float y2, float thickness, Color color) {
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
                    pixel,
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
        }
    }

    public interface FoldDisplayProvider {
        String getCollapsedText(CodeEditor editor, FoldDisplayContext context);
    }

    public interface InputFilter {
        boolean acceptChar(CodeEditor editor, char character);
    }

    public static class AllowedCharactersFilter implements InputFilter {
        private final String allowedCharacters;

        public AllowedCharactersFilter(String allowedCharacters) {
            this.allowedCharacters = allowedCharacters == null ? "" : allowedCharacters;
        }

        @Override
        public boolean acceptChar(CodeEditor editor, char character) {
            return allowedCharacters.indexOf(character) >= 0;
        }
    }

    public static final class FoldDisplayContext {
        public final int startLine;
        public final int endLine;
        public final int depth;
        public final int hiddenLineCount;
        public final String startLineText;
        public final String endLineText;

        public FoldDisplayContext(int startLine, int endLine, int depth, String startLineText, String endLineText) {
            this.startLine = startLine;
            this.endLine = endLine;
            this.depth = depth;
            this.hiddenLineCount = Math.max(0, endLine - startLine);
            this.startLineText = startLineText == null ? "" : startLineText;
            this.endLineText = endLineText == null ? "" : endLineText;
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
                    if (zoomEnabled) {
                        beginPinchZoom();
                    }
                    return true;
                }
            }

            ensureLayout();
            lastDragX = x;
            lastDragY = y;
            touchDownX = x;
            touchDownY = y;
            resetTouchScrollAxisLock();
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
            draggingCaretHandle = false;
            draggingHorizontalScrollbar = false;
            handleDragFixedLine = -1;
            handleDragFixedColumn = -1;
            handleDragPointerOffsetX = 0f;
            handleDragPointerOffsetY = 0f;

            if (!touchInteraction && button == Input.Buttons.RIGHT) {
                return notifySecondaryClick(x, y);
            }

            if (touchInteraction && beginAnyHandleDrag(x, y)) {
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
                    resetTouchScrollAxisLock();
                }
                return true;
            }

            int line = findLineByVisualRow(row);
            int segment = row - visualRowStartOf(line);
            FoldRegion region = foldRegionsByStart.get(line);
            if (segment == 0 && region != null && isInsideGutter(x)) {
                toggleFold(region);
                return true;
            }
            if (segment == 0 && region != null && region.collapsed
                && isInsideCollapsedFoldDisplayHitArea(x, y, row, layoutFor(line), layoutFor(line).segmentStarts.get(segment), layoutFor(line).segmentEnds.get(segment), region)) {
                expandCollapsedRegion(region);
                refreshBlink();
                return true;
            }

            if (touchInteraction) {
                pendingTouchPress = true;
                draggingTouchScroll = false;
                return true;
            }

            if (isInsideGutter(x)) {
                clearSelection();
                draggingTouchScroll = true;
                resetTouchScrollAxisLock();
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
            // Overlays such as an open completion popup claim navigation and Enter first.
            if (dispatchInterceptorKeyDown(keycode)) {
                return true;
            }

            CodeEditorAction action = keymap.actionForCurrentKeyDown(keycode);
            if (action == null) {
                return false;
            }
            // Shift only extends a selection over a caret move; on an edit it is part of the chord.
            boolean shiftHeld = keymap.shiftPressed() && action.isMovement();
            return performAction(action, shiftHeld, keycode);
        }

        @Override
        public boolean keyUp(InputEvent event, int keycode) {
            // Keyed by keycode, not action, so whichever key armed the repeat is the one that cancels it.
            stopDeleteKeyRepeat(keycode);
            return false;
        }

        @Override
        public boolean keyTyped(InputEvent event, char character) {
            if (disabled) {
                return false;
            }
            // Via the keymap so a map with commandActsAsControl also suppresses Cmd-chord characters.
            if (keymap.controlPressed()
                || character == 0 || character == 8 || character == 9 || character == 13 || character == 127) {
                return false;
            }

            if (readOnly) {
                return true;
            }

            if (!Character.isISOControl(character)) {
                if (!acceptsInputCharacter(character)) {
                    return true;
                }
                if (dispatchInterceptorKeyTyped(character)) {
                    return true;
                }
                expandCollapsedRegionsForEdit(EditIntent.TYPE);
                if (dispatchAutoEditCharacter(character)) {
                    // The strategy performed its own edit through the public mutators, which already
                    // notified listeners; only the after-typed hook is still owed, so a completion
                    // provider still sees the character.
                    dispatchInterceptorAfterKeyTyped(character);
                    return true;
                }
                markPendingContentChange(CodeEditorContentChangeType.INSERT);
                // Grouped only when there is genuinely more than one mutation to group: a closing brace
                // that also dedents, or a keystroke that replaces a selection. A plain character is a
                // single insertChar, and wrapping that in a group suppressed undo merging entirely --
                // CodeDocument.recordUndo only consults the merge rule at compound depth 0, and
                // endCompoundEdit resets the merge state on the way out. Grouping every keystroke made
                // typing one undo step per character while Backspace, which is not grouped, merged.
                if (character == '}' || hasSelection()) {
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
                } else {
                    document.insertChar(character);
                }
                onDocumentMutatedDeferred();
                // After insertion, so a trigger character sees the document as the user left it.
                dispatchInterceptorAfterKeyTyped(character);
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
                if (pinchZooming || (zoomEnabled && countActiveTouchPointers() >= 2)) {
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
            if (draggingStartHandle || draggingEndHandle || draggingCaretHandle) {
                updateAnyHandleDrag(x, y);
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
                    resetTouchScrollAxisLock();
                    touchScrollVelocityX = 0f;
                    touchScrollVelocityY = 0f;
                }
            }
            if (draggingTouchScroll) {
                updateTouchScrollAxisLock(x, y);
                float scrollDeltaX = -(x - previousDragX);
                float scrollDeltaY = y - previousDragY;
                if (touchScrollAxisLock == TOUCH_SCROLL_AXIS_HORIZONTAL) {
                    scrollDeltaY = 0f;
                } else if (touchScrollAxisLock == TOUCH_SCROLL_AXIS_VERTICAL) {
                    scrollDeltaX = 0f;
                }
                float scrollBeforeX = scrollX;
                float scrollBeforeY = scrollY;
                applyTouchScrollDelta(scrollDeltaX, scrollDeltaY);
                float elapsedSeconds = Math.max(0.001f, (nowNanos - previousTimeNanos) / 1_000_000_000f);
                float sampledVelocityX = (scrollX - scrollBeforeX) / elapsedSeconds;
                float sampledVelocityY = (scrollY - scrollBeforeY) / elapsedSeconds;
                if (touchScrollAxisLock == TOUCH_SCROLL_AXIS_HORIZONTAL) {
                    sampledVelocityY = 0f;
                } else if (touchScrollAxisLock == TOUCH_SCROLL_AXIS_VERTICAL) {
                    sampledVelocityX = 0f;
                }
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
                resetTouchScrollAxisLock();
                draggingSelection = false;
                draggingStartHandle = false;
                draggingEndHandle = false;
                draggingCaretHandle = false;
                handleDragPointerOffsetX = 0f;
                handleDragPointerOffsetY = 0f;
                clearSelectedTextDragState();
                draggingScrollbar = false;
                draggingHorizontalScrollbar = false;
                touchScrollVelocityX = 0f;
                touchScrollVelocityY = 0f;
                return;
            }
            boolean handleDrag = draggingStartHandle || draggingEndHandle || draggingCaretHandle;
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
            resetTouchScrollAxisLock();
            draggingSelection = false;
            draggingStartHandle = false;
            draggingEndHandle = false;
            draggingCaretHandle = false;
            handleDragFixedLine = -1;
            handleDragFixedColumn = -1;
            handleDragPointerOffsetX = 0f;
            handleDragPointerOffsetY = 0f;
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
                    showTransientCaretHandle();
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
            cancelHover();
            return true;
        }

        @Override
        public boolean mouseMoved(InputEvent event, float x, float y) {
            updateHoverTarget(x, y);
            return false;
        }

        @Override
        public void exit(InputEvent event, float x, float y, int pointer, Actor toActor) {
            if (pointer == -1) {
                cancelHover();
            }
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
