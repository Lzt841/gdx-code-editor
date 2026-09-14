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
import com.lzt841.editor.CodeEditorTextRange;

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
 * <p>After each edit the session re-reads its region and compares it with the copy it kept, finding the
 * common prefix and suffix. That yields the changed span and its length delta without a per-line
 * journal of columns, which the document does not record. Every placeholder at or after the change is
 * shifted; the active one grows or shrinks around it.
 *
 * <h2>When it gives up</h2>
 *
 * <p>Deliberately eager, because a snippet that mis-tracks silently corrupts text the user is looking
 * at. It finishes on undo, redo, {@code setText}, a whole-document change, an edit outside its region,
 * or the caret leaving the region. Finishing is not failure: the text stays exactly as it is and only
 * the Tab behaviour stops.
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
    /** The region's text as the session last saw it, for diffing after an edit. */
    private String regionText = "";
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
        if (editor == null || source == null) {
            return null;
        }
        // Captured before the edit, so $TM_SELECTED_TEXT is the text about to be replaced.
        CodeSnippetTemplate template = CodeSnippetTemplate.parse(source, CodeSnippetContext.from(editor));
        return startParsed(editor, template, replaceRange);
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
        CodeSnippetSession session = new CodeSnippetSession(editor, template);
        return session.insertAndBegin(replaceRange) ? session : null;
    }

    private boolean insertAndBegin(CodeEditorTextRange replaceRange) {
        int startLine;
        int startColumn;
        if (replaceRange != null) {
            startLine = replaceRange.startLine;
            startColumn = replaceRange.startColumn;
        } else {
            CodeEditorTextRange selection = editor.getSelection();
            if (selection != null) {
                startLine = selection.startLine;
                startColumn = selection.startColumn;
            } else {
                startLine = editor.getCursorLine();
                startColumn = editor.getCursorColumn();
            }
        }

        boolean inserted;
        if (replaceRange != null) {
            inserted = editor.replaceRange(replaceRange, template.insertText);
        } else {
            inserted = editor.insertTextAtCursor(template.insertText);
        }
        if (!inserted) {
            return false;
        }

        buildRanges(startLine, startColumn);
        if (!template.hasNavigableStops()) {
            // Nothing to Tab between. Put the caret at $0 and do not install anything: a session that
            // only intercepts Tab in order to pass it through would break indentation for no gain.
            moveCaretToFinalStop();
            return false;
        }

        active = true;
        editor.addContentListener(contentListener);
        // Registered after any completion controller, which is what makes the ordering work: with the
        // popup closed the controller declines every key except its own trigger chord, so Tab reaches
        // the session; with the popup open the controller takes Tab first, as it should.
        editor.addInputInterceptor(inputInterceptor);
        editor.addCaretListener(caretListener);
        tabPosition = -1;
        next();
        return active;
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

    /** Moves to the next stop. Finishes the session after the last one. */
    public boolean next() {
        if (!active) {
            return false;
        }
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
        if (!active || tabPosition <= 0) {
            return false;
        }
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

    public CodeSnippetTemplate getTemplate() {
        return template;
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
        applyingOwnEdit = true;
        try {
            if (range.isEmpty()) {
                editor.setCursorPosition(range.startLine, range.startColumn);
            } else {
                editor.setSelection(range.startLine, range.startColumn, range.endLine, range.endColumn);
            }
        } finally {
            applyingOwnEdit = false;
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
            if (template.placeholders.get(i).index == index) {
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
            int delta = event.insertedLineCount - event.removedLineCount;
            if (delta != 0) {
                shiftRegionLines(delta);
            }
            regionText = readRegion();
            // An edit starting above may also have reached into the region and taken part of the
            // snippet with it. Cheaply detected by checking the placeholders still land inside the
            // region text; a shift alone leaves them all valid.
            if (!rangesFitRegion()) {
                finish(false);
            }
            return;
        }
        if (event.startLine > regionEndLine) {
            regionText = readRegion();
            return;
        }

        int lineDelta = event.insertedLineCount - event.removedLineCount;
        regionEndLine = Math.max(regionStartLine, regionEndLine + lineDelta);
        String updated = readRegion();
        if (!applyDiff(regionText, updated)) {
            finish(false);
            return;
        }
        regionText = updated;
        mirrorCurrentStop();
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
     * Finds what changed between two versions of the region and moves the placeholders accordingly.
     *
     * <p>A common-prefix / common-suffix diff, which is exact for the single contiguous edit a
     * keystroke, paste or delete produces. It cannot distinguish two separate edits in one event, so a
     * change that does not reduce to one span returns false and the session stops.
     *
     * @return false when the session can no longer trust its ranges
     */
    private boolean applyDiff(String before, String after) {
        if (before.equals(after)) {
            return true;
        }
        int prefix = 0;
        int maxPrefix = Math.min(before.length(), after.length());
        while (prefix < maxPrefix && before.charAt(prefix) == after.charAt(prefix)) {
            prefix++;
        }
        int suffix = 0;
        int maxSuffix = Math.min(before.length() - prefix, after.length() - prefix);
        while (suffix < maxSuffix
            && before.charAt(before.length() - 1 - suffix) == after.charAt(after.length() - 1 - suffix)) {
            suffix++;
        }
        int removedLength = before.length() - prefix - suffix;
        int insertedLength = after.length() - prefix - suffix;
        if (removedLength < 0 || insertedLength < 0) {
            return false;
        }

        int activeIndex = primaryRangeIndexFor(getCurrentTabStop());
        int changeStart = prefix;
        int changeEnd = prefix + removedLength;
        int delta = insertedLength - removedLength;

        for (int i = 0; i < ranges.size; i++) {
            LiveRange range = ranges.get(i);
            int start = offsetOf(before, range.startLine, range.startColumn);
            int end = offsetOf(before, range.endLine, range.endColumn);
            if (start < 0 || end < 0) {
                return false;
            }
            int newStart = start;
            int newEnd = end;

            boolean isActiveStop = i == activeIndex;
            if (changeStart >= start && changeEnd <= end) {
                // Inside this placeholder: it grows or shrinks with the edit.
                newEnd = end + delta;
            } else if (isActiveStop && changeStart == end && removedLength == 0) {
                // Typing at the very end of the active stop extends it, which is what makes a
                // placeholder feel like a field rather than a fixed-width box.
                newEnd = end + delta;
            } else if (changeEnd <= start) {
                newStart = start + delta;
                newEnd = end + delta;
            } else if (changeStart >= end) {
                // Entirely after this placeholder: unaffected.
                newStart = start;
                newEnd = end;
            } else {
                // The edit straddles a boundary, so this placeholder no longer describes a coherent
                // span. Better to stop than to guess.
                return false;
            }
            if (newEnd < newStart) {
                newEnd = newStart;
            }
            if (!setFromOffsets(after, range, newStart, newEnd)) {
                return false;
            }
        }
        return true;
    }

    /** Copies the active stop's text into the other stops that share its index. */
    private void mirrorCurrentStop() {
        int index = getCurrentTabStop();
        if (index <= 0) {
            return;
        }
        int primary = primaryRangeIndexFor(index);
        if (primary < 0) {
            return;
        }
        LiveRange source = ranges.get(primary);
        String value = editor.getTextRange(
            source.startLine, source.startColumn, source.endLine, source.endColumn);

        // The caret has to be put back afterwards. replaceRange clears the selection and leaves the
        // caret at the end of what it inserted, which is correct for the completion popup that method
        // was written for and wrong here: the user is mid-word in a different placeholder. Tracked as an
        // offset into the region rather than a line/column, because a mirror earlier in the region
        // changes the columns of everything after it.
        int caretOffset = offsetOf(regionText, editor.getCursorLine(), editor.getCursorColumn());

        applyingOwnEdit = true;
        editor.beginCompoundEdit();
        try {
            // Backwards, so replacing one mirror cannot move the ones not yet visited.
            for (int i = ranges.size - 1; i >= 0; i--) {
                if (i == primary || template.placeholders.get(i).index != index) {
                    continue;
                }
                LiveRange target = ranges.get(i);
                String current = editor.getTextRange(
                    target.startLine, target.startColumn, target.endLine, target.endColumn);
                if (value.equals(current)) {
                    continue;
                }
                if (caretOffset >= 0) {
                    int at = offsetOf(regionText, target.startLine, target.startColumn);
                    if (at >= 0 && at + current.length() <= caretOffset) {
                        caretOffset += value.length() - current.length();
                    }
                }
                editor.replaceRange(
                    target.startLine, target.startColumn, target.endLine, target.endColumn, value);
                advanceRangeEnd(target, value);
            }
        } finally {
            editor.endCompoundEdit();
            applyingOwnEdit = false;
        }
        regionEndLine = Math.max(regionEndLine, lastRangeLine());
        regionText = readRegion();
        restoreCaret(caretOffset);
    }

    /** Puts the caret back at a region offset after the mirror edits moved it. */
    private void restoreCaret(int caretOffset) {
        if (caretOffset < 0) {
            return;
        }
        int line = regionStartLine;
        int column = 0;
        for (int i = 0; i < caretOffset && i < regionText.length(); i++) {
            if (regionText.charAt(i) == '\n') {
                line++;
                column = 0;
            } else {
                column++;
            }
        }
        applyingOwnEdit = true;
        try {
            editor.setCursorPosition(line, column);
        } finally {
            applyingOwnEdit = false;
        }
    }

    /** Sets a range's end so it covers {@code value} starting from the range's own start. */
    private void advanceRangeEnd(LiveRange range, String value) {
        int line = range.startLine;
        int column = range.startColumn;
        for (int i = 0; i < value.length(); i++) {
            if (value.charAt(i) == '\n') {
                line++;
                column = 0;
            } else {
                column++;
            }
        }
        range.endLine = line;
        range.endColumn = column;
    }

    private int lastRangeLine() {
        int last = regionStartLine;
        for (int i = 0; i < ranges.size; i++) {
            last = Math.max(last, ranges.get(i).endLine);
        }
        return last;
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
        if (relativeLine < 0) {
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

    /** Mutable line/column pair, so tracking an edit does not allocate a range per placeholder per key. */
    private static final class LiveRange {
        int startLine;
        int startColumn;
        int endLine;
        int endColumn;

        boolean isEmpty() {
            return startLine == endLine && startColumn == endColumn;
        }
    }
}
