package com.lzt841.editor.completion;

import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.IntArray;
import com.lzt841.editor.CodeEditor;
import com.lzt841.editor.CodeEditorCaretListener;
import com.lzt841.editor.CodeEditorContentChangeEvent;
import com.lzt841.editor.CodeEditorContentChangeType;
import com.lzt841.editor.CodeEditorContentListener;
import com.lzt841.editor.CodeEditorInputInterceptor;
import com.lzt841.editor.CodeEditorPosition;
import com.lzt841.editor.CodeEditorTextEdit;
import com.lzt841.editor.CodeEditorTextRange;

import java.util.Comparator;

/**
 * A live snippet: the placeholders are inserted, one is selected, and Tab moves between them.
 *
 * <p>Start one with {@link #start(CodeEditor, String)} or, from a completion, let
 * {@link CodeCompletionController} do it. The session installs itself as a content listener and an
 * input interceptor and removes them when it finishes, so nothing needs to be torn down by hand.
 *
 * <h2>How positions are tracked</h2>
 *
 * <p>Placeholders are held as line/column ranges, not document offsets. {@code CodeDocument.toOffset}
 * walks every line above its argument, so a session that converted offsets on each keystroke would
 * cost O(document) per character in a large file — the exact thing the rest of this editor is built to
 * avoid. Ranges are instead maintained by re-reading the snippet's own region, which is a few lines
 * long whatever the document's size.
 *
 * <p>After each edit the session reads its region and uses the event's exact pre-edit and post-edit
 * ranges to track the splice, retaining shared text in replaced defaults. Legacy events without exact
 * coordinates use a caret-constrained text diff. The edited occurrence grows or shrinks, adjacent
 * fields shift, and nested fields covered by a parent replacement leave the navigation order. Mirror
 * propagation is planned from children to affected parents before one exact batch changes the text.
 *
 * <h2>When it gives up</h2>
 *
 * <p>The session finishes on undo, redo, {@code setText}, an external batch rewrite, stale or reentrant
 * events, an edit crossing a tracked boundary, or the caret leaving the region. Independent edits above
 * or below the region are followed. Finishing leaves the text and caret as they are; only the Tab
 * behaviour stops.
 */
public class CodeSnippetSession {
    private final CodeEditor editor;
    private final CodeSnippetTemplate template;
    /** Parallel to {@link CodeSnippetTemplate#placeholders}; index i tracks placeholder i. */
    private final Array<LiveRange> ranges = new Array<LiveRange>();
    private final IntArray tabOrder;

    /** First line of the region the snippet occupies. */
    private int regionStartLine;
    /** Last line of the region, inclusive. Kept in step as the snippet's own lines change. */
    private int regionEndLine;
    /** Text and version last consumed by this session. */
    private String regionText = "";
    private int regionVersion;
    private int tabPosition = -1;
    private boolean active;
    private boolean applyingOwnEdit;
    private FinishListener finishListener;

    /** Notified once, when the session stops driving the editor. */
    public interface FinishListener {
        /**
         * @param completed true when the user Tabbed through to the end, false when the session was
         *     abandoned — Escape, an edit it could not follow, or the caret leaving
         */
        void onSnippetFinished(CodeSnippetSession session, boolean completed);
    }

    private final CodeEditorContentListener contentListener = new CodeEditorContentListener() {
        @Override
        public void onContentChanged(CodeEditor source, CodeEditorContentChangeEvent event) {
            handleContentChanged(event);
        }
    };

    private final CodeEditorInputInterceptor inputInterceptor = new CodeEditorInputInterceptor() {
        @Override
        public boolean onKeyDown(CodeEditor source, int keycode) {
            return handleKeyDown(keycode);
        }
    };

    private final CodeEditorCaretListener caretListener = new CodeEditorCaretListener() {
        @Override
        public void onCaretMoved(
            CodeEditor source,
            CodeEditorPosition position,
            CodeEditorTextRange selection,
            boolean causedByEdit
        ) {
            // Edits are the content listener's business; it has already moved the ranges by the time
            // this arrives, so re-checking here would test the caret against ranges that legitimately
            // moved with it.
            if (causedByEdit) {
                return;
            }
            handleCaretMoved(position);
        }
    };

    private CodeSnippetSession(CodeEditor editor, CodeSnippetTemplate template) {
        this.editor = editor;
        this.template = template;
        this.tabOrder = new IntArray(template.tabOrder);
    }

    /** Parses {@code source}, replaces the selection (or inserts at the caret), and selects the first stop. */
    public static CodeSnippetSession start(CodeEditor editor, String source) {
        return start(editor, source, null);
    }

    /**
     * Parses and inserts a snippet over an explicit range.
     *
     * @param replaceRange range the snippet replaces, or null to replace the selection / insert at the caret
     * @return the live session, or null when there was nothing to navigate — in which case the text was
     *     still inserted and the caret placed at {@code $0}
     */
    public static CodeSnippetSession start(CodeEditor editor, String source, CodeEditorTextRange replaceRange) {
        CodeSnippetSession session = startForCompletion(editor, source, replaceRange);
        return session != null && session.isActive() ? session : null;
    }

    /** Returns a successful start even if its final stop immediately finishes the session. */
    static CodeSnippetSession startForCompletion(CodeEditor editor, String source, CodeEditorTextRange replaceRange) {
        if (editor == null || source == null) {
            return null;
        }
        // Captured before the edit, so $TM_SELECTED_TEXT is the text about to be replaced.
        CodeSnippetTemplate template = CodeSnippetTemplate.parse(source, CodeSnippetContext.from(editor)).normalizedCopy();
        if (template == null) return null;
        CodeSnippetSession session = new CodeSnippetSession(editor, template);
        return session.insertAndBegin(replaceRange) ? session : null;
    }

    /** As {@link #start(CodeEditor, String, CodeEditorTextRange)} for an already-parsed template. */
    public static CodeSnippetSession startParsed(
        CodeEditor editor,
        CodeSnippetTemplate template,
        CodeEditorTextRange replaceRange
    ) {
        if (editor == null || template == null) {
            return null;
        }
        CodeSnippetTemplate prepared = template.normalizedCopy();
        if (prepared == null) return null;
        CodeSnippetSession session = new CodeSnippetSession(editor, prepared);
        return session.insertAndBegin(replaceRange) && session.isActive() ? session : null;
    }

    private boolean insertAndBegin(CodeEditorTextRange replaceRange) {
        if (editor.isReadOnly() || editor.isDisabled() || editor.isPasswordMode()) return false;
        CodeEditorTextRange target = replaceRange != null ? replaceRange : editor.getSelection();
        if (target == null) {
            target = new CodeEditorTextRange(editor.getCursorLine(), editor.getCursorColumn(),
                editor.getCursorLine(), editor.getCursorColumn());
        }
        final CodeEditorTextRange insertionRange = target;
        final Array<CodeEditorTextEdit> edits = Array.with(new CodeEditorTextEdit(target, template.insertText));
        if (!editor.canApplyEdits(edits)) return false;
        final boolean unchanged = template.insertText.equals(editor.getTextRange(target));
        final int[] acceptedVersion = {-1};
        applyingOwnEdit = true;
        try {
            editor.runAsSingleUndoStep("Insert Snippet", new Runnable() {
                @Override public void run() {
                    // Exact insertion rejects the whole body if filtering would invalidate its stops.
                    // Existing text needs navigation only, preserving its history and redo entries.
                    if (!unchanged && !editor.applyEditsExact(edits, "Insert Snippet")) return;
                    acceptedVersion[0] = editor.getDocumentVersion();
                    buildRanges(insertionRange.startLine, insertionRange.startColumn);
                    if (!template.hasNavigableStops()) {
                        moveCaretToFinalStop();
                        return;
                    }
                    active = true;
                    editor.addContentListener(contentListener);
                    // Completion gets first refusal while its popup is open; otherwise Tab reaches us.
                    editor.addInputInterceptor(inputInterceptor);
                    editor.addCaretListener(caretListener);
                    tabPosition = -1;
                    next();
                }
            });
        } finally {
            applyingOwnEdit = false;
        }
        // Closing the group notifies host listeners, which can rewrite the document themselves.
        if (acceptedVersion[0] < 0 || editor.getDocumentVersion() != acceptedVersion[0]
            || editor.isReadOnly() || editor.isDisabled() || editor.isPasswordMode()) {
            finish(false);
            return false;
        }
        return true;
    }

    /**
     * Turns the template's body-relative offsets into document line/column ranges by walking the
     * inserted text once, counting newlines.
     */
    private void buildRanges(int startLine, int startColumn) {
        ranges.clear();
        String body = template.insertText;
        int lines = 0;
        for (int i = 0; i < body.length(); i++) {
            if (body.charAt(i) == '\n') {
                lines++;
            }
        }
        regionStartLine = startLine;
        regionEndLine = startLine + lines;

        for (int p = 0; p < template.placeholders.size; p++) {
            CodeSnippetPlaceholder placeholder = template.placeholders.get(p);
            LiveRange range = new LiveRange();
            positionOf(body, placeholder.start, startLine, startColumn, range, true);
            positionOf(body, placeholder.end, startLine, startColumn, range, false);
            ranges.add(range);
        }
        regionText = readRegion();
        regionVersion = editor.getDocumentVersion();
    }

    private void positionOf(
        String body,
        int offset,
        int startLine,
        int startColumn,
        LiveRange target,
        boolean isStart
    ) {
        int line = startLine;
        int column = startColumn;
        for (int i = 0; i < offset && i < body.length(); i++) {
            if (body.charAt(i) == '\n') {
                line++;
                column = 0;
            } else {
                column++;
            }
        }
        if (isStart) {
            target.startLine = line;
            target.startColumn = column;
        } else {
            target.endLine = line;
            target.endColumn = column;
        }
    }

    private boolean syncBeforeNavigation() {
        if (!active) return false;
        if (regionVersion != editor.getDocumentVersion()) {
            editor.flushPendingContentChanges();
            if (regionVersion != editor.getDocumentVersion()) finish(false);
        }
        return active;
    }

    /** Moves to the next stop. Finishes the session after the last one. */
    public boolean next() {
        if (!syncBeforeNavigation()) return false;
        if (tabPosition + 1 >= tabOrder.size) {
            finish(true);
            return false;
        }
        tabPosition++;
        if (tabOrder.get(tabPosition) == 0) {
            // $0 is the exit: place the caret and stop, rather than leaving a session that Tab
            // would then have to decide about.
            selectStop(tabPosition);
            finish(true);
            return true;
        }
        selectStop(tabPosition);
        return true;
    }

    /** Moves to the previous stop. Stays on the first one rather than finishing. */
    public boolean previous() {
        if (!syncBeforeNavigation() || tabPosition <= 0) return false;
        tabPosition--;
        selectStop(tabPosition);
        return true;
    }

    /** Abandons the session, leaving the text as it is and the caret where it is. */
    public void cancel() {
        finish(false);
    }

    public boolean isActive() {
        return active;
    }

    /** Tab-stop index currently selected, or -1 before the first {@link #next()}. */
    public int getCurrentTabStop() {
        return tabPosition < 0 || tabPosition >= tabOrder.size ? -1 : tabOrder.get(tabPosition);
    }

    public int getTabStopCount() {
        return tabOrder.size;
    }

    /** Returns an independent template snapshot; its mutable arrays cannot change this session. */
    public CodeSnippetTemplate getTemplate() {
        return new CodeSnippetTemplate(template.source, template.insertText,
            new Array<CodeSnippetPlaceholder>(template.placeholders), new IntArray(template.tabOrder));
    }

    public CodeSnippetSession setFinishListener(FinishListener listener) {
        this.finishListener = listener;
        return this;
    }

    /** Range currently selected, or null when the session is not on a stop. */
    public CodeEditorTextRange getCurrentRange() {
        int primary = primaryRangeIndexFor(getCurrentTabStop());
        if (primary < 0) {
            return null;
        }
        LiveRange range = ranges.get(primary);
        return new CodeEditorTextRange(range.startLine, range.startColumn, range.endLine, range.endColumn);
    }

    /**
     * Called by {@link CodeCompletionController} for every key down while a session is live.
     *
     * @return true when the session consumed the key
     */
    public boolean handleKeyDown(int keycode) {
        if (!active) {
            return false;
        }
        if (keycode == com.badlogic.gdx.Input.Keys.TAB) {
            if (editor.getKeymap().shiftPressed()) {
                // Returning true even at the first stop, deliberately: Shift-Tab inside a snippet must
                // not fall through to Dedent and silently reindent the line the user is filling in.
                previous();
            } else {
                next();
            }
            return true;
        }
        if (keycode == com.badlogic.gdx.Input.Keys.ESCAPE) {
            cancel();
            return true;
        }
        return false;
    }

    private void selectStop(int position) {
        int index = tabOrder.get(position);
        int primary = primaryRangeIndexFor(index);
        if (primary < 0) {
            return;
        }
        LiveRange range = ranges.get(primary);
        boolean wasApplyingOwnEdit = applyingOwnEdit;
        applyingOwnEdit = true;
        try {
            if (range.isEmpty()) {
                editor.setCursorPosition(range.startLine, range.startColumn);
            } else {
                editor.setSelection(range.startLine, range.startColumn, range.endLine, range.endColumn);
            }
        } finally {
            applyingOwnEdit = wasApplyingOwnEdit;
        }
    }

    private void moveCaretToFinalStop() {
        int primary = primaryRangeIndexFor(0);
        if (primary < 0) {
            return;
        }
        LiveRange range = ranges.get(primary);
        editor.setCursorPosition(range.startLine, range.startColumn);
    }

    /** First occurrence of an index; later ones with the same index are mirrors. */
    private int primaryRangeIndexFor(int index) {
        for (int i = 0; i < template.placeholders.size && i < ranges.size; i++) {
            if (ranges.get(i).alive && template.placeholders.get(i).index == index) {
                return i;
            }
        }
        return -1;
    }

    /**
     * Finishes the session when the caret has been moved out of the snippet by something other than an
     * edit — an arrow key, a click, a jump to a search hit.
     *
     * <p>Checked against the whole region rather than the current stop. Moving between stops with the
     * arrow keys, or clicking from one placeholder to another, is ordinary editing of the snippet the
     * user is still filling in, and ending the session there would take Tab away exactly when it is
     * about to be useful. Leaving the region is the unambiguous signal.
     */
    private void handleCaretMoved(CodeEditorPosition position) {
        if (!active || applyingOwnEdit || position == null) {
            return;
        }
        if (position.line < regionStartLine || position.line > regionEndLine) {
            finish(false);
        }
    }

    private void handleContentChanged(CodeEditorContentChangeEvent event) {
        if (!active || applyingOwnEdit) {
            return;
        }
        if (event.documentVersion != editor.getDocumentVersion()
            || event.documentVersion == regionVersion
            || event.previousDocumentVersion >= 0 && event.previousDocumentVersion != regionVersion) {
            finish(false);
            return;
        }
        if (!isFollowable(event)) {
            finish(false);
            return;
        }
        // An edit that began above the region moves the whole thing; the diff below only covers the
        // region itself.
        //
        // Classified on startLine and the line *delta*, never on an absolute end line. The event's
        // counts accumulate across a compound edit — replaceRange contributes once for the delete and
        // again for the insert — so removedLineCount is a sum rather than the span of one splice, and
        // startLine + removedLineCount is not a line number at all. The difference of the two sums does
        // survive the accumulation, which is what the shift needs.
        if (event.startLine < regionStartLine) {
            if (event.replacedRange != null && event.replacedRange.endLine >= regionStartLine) {
                finish(false);
                return;
            }
            int delta = event.insertedLineCount - event.removedLineCount;
            if (delta != 0) shiftRegionLines(delta);
            if (!regionText.equals(readRegion()) || !rangesFitRegion()) {
                finish(false);
                return;
            }
            regionVersion = event.documentVersion;
            return;
        }
        if (event.startLine > regionEndLine) {
            if (!regionText.equals(readRegion())) {
                finish(false);
                return;
            }
            regionVersion = event.documentVersion;
            return;
        }

        int lineDelta = event.insertedLineCount - event.removedLineCount;
        regionEndLine = Math.max(regionStartLine, regionEndLine + lineDelta);
        String updated = readRegion();
        int sourceRangeIndex = applyDiff(regionText, updated, event);
        if (sourceRangeIndex == Integer.MIN_VALUE) {
            finish(false);
            return;
        }
        regionText = updated;
        // Publish the accepted user transition before mirror propagation. The mirror batch is a new
        // transition and advances this baseline again after its own notification is suppressed.
        regionVersion = event.documentVersion;
        mirrorCurrentStop(sourceRangeIndex);
    }

    /**
     * Rejects the changes a region diff cannot honestly describe.
     *
     * <p>Undo and redo are the important ones: they can move text the session has no record of, and a
     * session that kept going would then point its placeholders at whatever now occupies those columns.
     */
    private boolean isFollowable(CodeEditorContentChangeEvent event) {
        if (event.type == CodeEditorContentChangeType.UNDO
            || event.type == CodeEditorContentChangeType.REDO
            || event.type == CodeEditorContentChangeType.SET_TEXT
            || event.type == CodeEditorContentChangeType.REPLACE_ALL) {
            return false;
        }
        // isWholeDocument() is not consulted. On a one-line file every edit reports the whole
        // document, including a newline typed inside a placeholder, and treating that as a rewrite
        // would end the session the first time the snippet grew a line.
        return regionStartLine >= 0 && regionStartLine < editor.getLineCount();
    }

    /** Whether every placeholder still resolves to a position inside the region text. */
    private boolean rangesFitRegion() {
        for (int i = 0; i < ranges.size; i++) {
            LiveRange range = ranges.get(i);
            if (!range.alive) continue;
            if (offsetOf(regionText, range.startLine, range.startColumn) < 0
                || offsetOf(regionText, range.endLine, range.endColumn) < 0) {
                return false;
            }
        }
        return true;
    }

    private void shiftRegionLines(int delta) {
        regionStartLine += delta;
        regionEndLine += delta;
        for (int i = 0; i < ranges.size; i++) {
            LiveRange range = ranges.get(i);
            range.startLine += delta;
            range.endLine += delta;
        }
    }

    /**
     * Tracks the event's original splice, retaining text shared by the old and new defaults. Legacy
     * events without exact coordinates fall back to a caret-constrained prefix/suffix diff.
     *
     * @return the edited occurrence, -1 for surrounding text, or MIN_VALUE for an unsafe change
     */
    private int applyDiff(String before, String after, CodeEditorContentChangeEvent event) {
        int changeStart;
        int changeEnd;
        if (event.replacedRange != null && event.insertedRange != null) {
            CodeEditorTextRange replaced = event.replacedRange;
            CodeEditorTextRange inserted = event.insertedRange;
            changeStart = offsetOf(before, replaced.startLine, replaced.startColumn);
            changeEnd = offsetOf(before, replaced.endLine, replaced.endColumn);
            int insertedStart = offsetOf(after, inserted.startLine, inserted.startColumn);
            int insertedEnd = offsetOf(after, inserted.endLine, inserted.endColumn);
            if (changeStart < 0 || changeEnd < changeStart || insertedStart != changeStart
                || insertedEnd < insertedStart
                || !before.regionMatches(0, after, 0, changeStart)
                || before.length() - changeEnd != after.length() - insertedEnd
                || !before.regionMatches(changeEnd, after, insertedEnd, before.length() - changeEnd)) {
                return Integer.MIN_VALUE;
            }
        } else {
            if (before.equals(after)) return -1;
            int caret = offsetOf(after, event.cursorLine, event.cursorColumn);
            int oldEnd = caret - (after.length() - before.length());
            if (caret >= 0 && oldEnd >= 0 && oldEnd <= before.length()
                && before.length() - oldEnd == after.length() - caret
                && before.regionMatches(oldEnd, after, caret, before.length() - oldEnd)) {
                changeStart = commonPrefix(before, after, Math.min(oldEnd, caret));
                changeEnd = oldEnd;
            } else {
                changeStart = commonPrefix(before, after, Math.min(before.length(), after.length()));
                int suffix = 0;
                int maxSuffix = Math.min(before.length() - changeStart, after.length() - changeStart);
                while (suffix < maxSuffix
                    && before.charAt(before.length() - 1 - suffix) == after.charAt(after.length() - 1 - suffix)) {
                    suffix++;
                }
                changeEnd = before.length() - suffix;
            }
            // A text diff may stop between the shared high surrogate and the changed low surrogate.
            if (splitsSurrogatePair(before, changeStart) || splitsSurrogatePair(after, changeStart)) changeStart--;
            int insertedEnd = changeEnd + after.length() - before.length();
            if (splitsSurrogatePair(before, changeEnd) || splitsSurrogatePair(after, insertedEnd)) changeEnd++;
        }
        int source = editedRangeIndex(before, changeStart, changeEnd);
        return applyRangeChange(before, after, changeStart, changeEnd, source) ? source : Integer.MIN_VALUE;
    }

    private static int commonPrefix(String before, String after, int limit) {
        int prefix = 0;
        while (prefix < limit && before.charAt(prefix) == after.charAt(prefix)) prefix++;
        return prefix;
    }

    /** Prefer the selected index at shared boundaries, then the smallest enclosing field. */
    private int editedRangeIndex(String before, int changeStart, int changeEnd) {
        int candidate = -1;
        int candidateLength = Integer.MAX_VALUE;
        boolean candidateIsCurrent = false;
        for (int i = 0; i < ranges.size; i++) {
            LiveRange range = ranges.get(i);
            if (!range.alive || template.placeholders.get(i).index <= 0) continue;
            int start = offsetOf(before, range.startLine, range.startColumn);
            int end = offsetOf(before, range.endLine, range.endColumn);
            if (start < 0 || changeStart < start || changeEnd > end) continue;
            boolean isCurrent = template.placeholders.get(i).index == getCurrentTabStop();
            if (candidate < 0 || isCurrent && !candidateIsCurrent
                || isCurrent == candidateIsCurrent && end - start < candidateLength) {
                candidate = i;
                candidateLength = end - start;
                candidateIsCurrent = isCurrent;
            }
        }
        return candidate;
    }

    /** Tracks an exact splice; mirror edits use their known spans instead of guessing a diff. */
    private boolean applyRangeChange(String before, String after, int changeStart, int changeEnd, int expandingIndex) {
        int removedLength = changeEnd - changeStart;
        int insertedLength = after.length() - before.length() + removedLength;
        int delta = insertedLength - removedLength;
        if (changeStart < 0 || changeEnd > before.length() || removedLength < 0 || insertedLength < 0) return false;

        for (int i = 0; i < ranges.size; i++) {
            LiveRange range = ranges.get(i);
            if (!range.alive) continue;
            int start = offsetOf(before, range.startLine, range.startColumn);
            int end = offsetOf(before, range.endLine, range.endColumn);
            if (start < 0 || end < start) return false;
            int newStart = start;
            int newEnd = end;
            boolean isExpandingStop = i == expandingIndex;
            boolean isDescendant = template.isAncestor(expandingIndex, i);

            if (removedLength > 0 && isDescendant && changeStart <= start && changeEnd >= end) {
                // Replacing a default removes its nested fields, even if a minimal diff would leave
                // their old text untouched. The nested exit survives at the replacement's endpoint.
                if (template.placeholders.get(i).index != 0) {
                    range.alive = false;
                    continue;
                }
                newStart = newEnd = changeStart + insertedLength;
            } else if (removedLength == 0 && !isExpandingStop && changeStart >= start && changeStart <= end) {
                // Insertions at a shared edge belong to the edited field. Actual ancestors grow with
                // their child, including when both started empty; adjacent empty mirrors stay apart.
                if (changeStart > start && changeStart < end || template.isAncestor(i, expandingIndex)) {
                    newEnd += delta;
                } else if (changeStart == start && (start < end || i > expandingIndex)) {
                    newStart += delta;
                    newEnd += delta;
                }
            } else if (changeStart >= start && changeEnd <= end && (start < end || isExpandingStop)) {
                newEnd += delta;
            } else if (changeEnd <= start) {
                newStart += delta;
                newEnd += delta;
            } else if (changeStart >= end) {
                // The change is after this field.
            } else if (isDescendant) {
                // A partially overwritten child cannot describe the remaining text either.
                if (template.placeholders.get(i).index != 0) {
                    range.alive = false;
                    continue;
                }
                newStart = newEnd = changeStart + insertedLength;
            } else {
                return false;
            }
            if (!setFromOffsets(after, range, newStart, newEnd)) return false;
        }
        pruneTabOrder();
        return true;
    }

    private void pruneTabOrder() {
        for (int i = tabOrder.size - 1; i >= 0; i--) {
            if (primaryRangeIndexFor(tabOrder.get(i)) >= 0) continue;
            tabOrder.removeIndex(i);
            if (i <= tabPosition) tabPosition--;
        }
    }

    private static boolean splitsSurrogatePair(String text, int offset) {
        return offset > 0 && offset < text.length()
            && Character.isHighSurrogate(text.charAt(offset - 1)) && Character.isLowSurrogate(text.charAt(offset));
    }

    /** Plans linked fields from children to parents, then applies all copies as one exact batch. */
    private void mirrorCurrentStop(int sourceIndex) {
        if (sourceIndex < 0 || template.placeholders.get(sourceIndex).index <= 0) return;
        IntArray order = mirrorOrder();
        if (order == null) {
            finish(false);
            return;
        }
        int[] sources = new int[order.size];
        for (int i = 0; i < sources.length; i++) sources[i] = -1;
        sources[order.indexOf(template.placeholders.get(sourceIndex).index)] = sourceIndex;
        markAncestorSources(sourceIndex, order, sources);

        String beforeMirrors = regionText;
        CodeEditorTextRange[] originalRanges = new CodeEditorTextRange[ranges.size];
        boolean[] changed = new boolean[ranges.size];
        for (int i = 0; i < ranges.size; i++) {
            LiveRange range = ranges.get(i);
            if (range.alive) originalRanges[i] = new CodeEditorTextRange(
                range.startLine, range.startColumn, range.endLine, range.endColumn);
        }

        for (int step = 0; step < order.size; step++) {
            int source = sources[step];
            if (source < 0 || !ranges.get(source).alive) continue;
            LiveRange sourceRange = ranges.get(source);
            int sourceStart = offsetOf(regionText, sourceRange.startLine, sourceRange.startColumn);
            int sourceEnd = offsetOf(regionText, sourceRange.endLine, sourceRange.endColumn);
            if (sourceStart < 0 || sourceEnd < sourceStart) {
                finish(false);
                return;
            }
            String value = regionText.substring(sourceStart, sourceEnd);
            Array<MirrorEdit> mirrors = new Array<MirrorEdit>();
            for (int i = 0; i < ranges.size; i++) {
                if (i == source || !ranges.get(i).alive || template.placeholders.get(i).index != order.get(step)) continue;
                LiveRange target = ranges.get(i);
                int start = offsetOf(regionText, target.startLine, target.startColumn);
                int end = offsetOf(regionText, target.endLine, target.endColumn);
                if (start < 0 || end < start) {
                    finish(false);
                    return;
                }
                if (!value.equals(regionText.substring(start, end))) mirrors.add(new MirrorEdit(i, start, end));
            }
            mirrors.sort(MIRROR_ORDER);
            for (int i = 0; i < mirrors.size; i++) {
                MirrorEdit edit = mirrors.get(i);
                markAncestorSources(edit.rangeIndex, order, sources);
                String updated = regionText.substring(0, edit.start) + value + regionText.substring(edit.end);
                if (!applyRangeChange(regionText, updated, edit.start, edit.end, edit.rangeIndex)) {
                    finish(false);
                    return;
                }
                changed[edit.rangeIndex] = true;
                regionEndLine += newlineCount(value) - newlineCount(regionText.substring(edit.start, edit.end));
                regionText = updated;
            }
        }

        final Array<CodeEditorTextEdit> edits = new Array<CodeEditorTextEdit>();
        for (int i = 0; i < ranges.size; i++) {
            if (!changed[i] || !ranges.get(i).alive) continue;
            // A mirrored parent already includes its children's final text. Replacing both would
            // overlap and would filter those characters twice, so retain only the enclosing edit.
            boolean covered = false;
            for (int j = 0; j < ranges.size; j++) {
                if (changed[j] && ranges.get(j).alive && template.isAncestor(j, i)) {
                    covered = true;
                    break;
                }
            }
            if (covered) continue;
            LiveRange range = ranges.get(i);
            int start = offsetOf(regionText, range.startLine, range.startColumn);
            int end = offsetOf(regionText, range.endLine, range.endColumn);
            CodeEditorTextRange original = originalRanges[i];
            String replacement = regionText.substring(start, end);
            int oldStart = offsetOf(beforeMirrors, original.startLine, original.startColumn);
            int oldEnd = offsetOf(beforeMirrors, original.endLine, original.endColumn);
            if (!replacement.equals(beforeMirrors.substring(oldStart, oldEnd))) {
                edits.add(new CodeEditorTextEdit(original, replacement));
            }
        }
        if (edits.size == 0) return;
        if (!editor.canApplyEdits(edits)) {
            finish(false);
            return;
        }

        final int[] acceptedVersion = {-1};
        applyingOwnEdit = true;
        try {
            editor.runAsSingleUndoStep("Mirror Snippet", new Runnable() {
                @Override public void run() {
                    if (!editor.applyEditsExact(edits, "Mirror Snippet")) return;
                    acceptedVersion[0] = editor.getDocumentVersion();
                    regionVersion = acceptedVersion[0];
                    if (!regionText.equals(readRegion()) || !rangesFitRegion()) finish(false);
                }
            });
        } finally {
            applyingOwnEdit = false;
        }
        if (acceptedVersion[0] < 0 || acceptedVersion[0] != editor.getDocumentVersion()) finish(false);
    }

    /** Orders index dependencies across all occurrences, including parents of mirror targets. */
    private IntArray mirrorOrder() {
        IntArray ordered = new IntArray(tabOrder.size);
        int count = tabOrder.size - 1; // $0 never mirrors.
        while (ordered.size < count) {
            int previousSize = ordered.size;
            for (int position = 0; position < tabOrder.size; position++) {
                int index = tabOrder.get(position);
                if (index <= 0 || ordered.contains(index)) continue;
                boolean blocked = false;
                for (int i = 0; i < ranges.size; i++) {
                    CodeSnippetPlaceholder child = template.placeholders.get(i);
                    if (!ranges.get(i).alive || child.index <= 0 || ordered.contains(child.index)) continue;
                    for (int parent = child.parent; parent >= 0;
                         parent = template.placeholders.get(parent).parent) {
                        if (ranges.get(parent).alive && template.placeholders.get(parent).index == index) {
                            blocked = true;
                            break;
                        }
                    }
                    if (blocked) break;
                }
                if (!blocked) ordered.add(index);
            }
            if (ordered.size == previousSize) return null;
        }
        return ordered;
    }

    private void markAncestorSources(int child, IntArray order, int[] sources) {
        for (int parent = template.placeholders.get(child).parent; parent >= 0;
             parent = template.placeholders.get(parent).parent) {
            if (!ranges.get(parent).alive) continue;
            int position = order.indexOf(template.placeholders.get(parent).index);
            if (position >= 0 && sources[position] < 0) sources[position] = parent;
        }
    }

    private static final Comparator<MirrorEdit> MIRROR_ORDER = new Comparator<MirrorEdit>() {
        @Override public int compare(MirrorEdit first, MirrorEdit second) {
            int byStart = Integer.compare(second.start, first.start);
            if (byStart != 0) return byStart;
            int byEnd = Integer.compare(second.end, first.end);
            return byEnd != 0 ? byEnd : Integer.compare(second.rangeIndex, first.rangeIndex);
        }
    };

    private static int newlineCount(String text) {
        int count = 0;
        for (int i = 0; i < text.length(); i++) {
            if (text.charAt(i) == '\n') count++;
        }
        return count;
    }

    private String readRegion() {
        int lastLine = Math.min(regionEndLine, editor.getLineCount() - 1);
        if (regionStartLine > lastLine) {
            return "";
        }
        return editor.getTextRange(regionStartLine, 0, lastLine, editor.getLineLength(lastLine));
    }

    /** Offset of a document position within the region text, or -1 when it falls outside. */
    private int offsetOf(String region, int line, int column) {
        int relativeLine = line - regionStartLine;
        if (relativeLine < 0 || column < 0) {
            return -1;
        }
        int offset = 0;
        int seen = 0;
        while (seen < relativeLine) {
            int next = region.indexOf('\n', offset);
            if (next < 0) {
                return -1;
            }
            offset = next + 1;
            seen++;
        }
        int lineEnd = region.indexOf('\n', offset);
        int limit = lineEnd < 0 ? region.length() : lineEnd;
        if (offset + column > limit) {
            return -1;
        }
        return offset + column;
    }

    private boolean setFromOffsets(String region, LiveRange range, int start, int end) {
        if (start < 0 || end < start || end > region.length()
            || splitsSurrogatePair(region, start) || splitsSurrogatePair(region, end)) return false;
        int line = regionStartLine;
        int column = 0;
        int i = 0;
        while (i < start) {
            if (i >= region.length()) {
                return false;
            }
            if (region.charAt(i) == '\n') {
                line++;
                column = 0;
            } else {
                column++;
            }
            i++;
        }
        range.startLine = line;
        range.startColumn = column;
        while (i < end) {
            if (i >= region.length()) {
                return false;
            }
            if (region.charAt(i) == '\n') {
                line++;
                column = 0;
            } else {
                column++;
            }
            i++;
        }
        range.endLine = line;
        range.endColumn = column;
        return true;
    }

    private void finish(boolean completed) {
        if (!active) {
            return;
        }
        active = false;
        editor.removeContentListener(contentListener);
        editor.removeInputInterceptor(inputInterceptor);
        editor.removeCaretListener(caretListener);
        if (finishListener != null) {
            finishListener.onSnippetFinished(this, completed);
        }
    }

    /** One mirror's original span, before any mirror in the batch moves it. */
    private static final class MirrorEdit {
        final int rangeIndex;
        final int start;
        final int end;

        MirrorEdit(int rangeIndex, int start, int end) {
            this.rangeIndex = rangeIndex;
            this.start = start;
            this.end = end;
        }
    }

    /** Mutable line/column pair, so tracking an edit does not allocate a range per placeholder per key. */
    private static final class LiveRange {
        int startLine;
        int startColumn;
        int endLine;
        int endColumn;
        boolean alive = true;

        boolean isEmpty() {
            return startLine == endLine && startColumn == endColumn;
        }
    }
}
