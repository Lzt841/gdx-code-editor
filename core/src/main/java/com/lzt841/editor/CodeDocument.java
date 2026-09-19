package com.lzt841.editor;

import com.badlogic.gdx.utils.Array;

/**
 * Mutable line-based document optimized for editor style operations.
 *
 * <p>Undo history stores only the lines an edit actually touched, so a keystroke in a 100k line
 * file costs one small record instead of a full text copy. Every mutation also appends a
 * {@link LineEdit} to an edit journal; a view can replay the journal from the version it last
 * synchronized to patch its own per-line caches instead of rebuilding them.
 */
public class CodeDocument {
    /**
     * @deprecated the indent width is configurable per document now; read
     *     {@code getIndentStrategy().indentWidth} instead. Kept as the default only.
     */
    @Deprecated
    public static final int INDENT_SIZE = 4;
    /** Default undo step cap; see {@link #setMaxUndoEntries(int)}. */
    public static final int DEFAULT_MAX_UNDO_ENTRIES = 400;
    /** Default retained-text cap in characters; see {@link #setMaxUndoChars(int)}. */
    public static final int DEFAULT_MAX_UNDO_CHARS = 8 << 20;
    private static final int MAX_JOURNAL_SIZE = 512;
    private static final long MERGE_WINDOW_NANOS = 1_000_000_000L;

    private final Array<StringBuilder> lines = new Array<>();
    private final Array<UndoEntry> undoStack = new Array<>();
    private final Array<UndoEntry> redoStack = new Array<>();
    private final Array<LineEdit> journal = new Array<>();
    private final Array<String> cachedSnapshot = new Array<>();

    private int cursorLine;
    private int cursorColumn;
    private int version;
    private int journalBaseVersion;
    private int snapshotVersion = -1;
    private int undoStackChars;
    private int longestLineIndex;
    private int longestLineLength;
    /** Set when the champion was overwritten and the replacements did not beat it. See {@link #recomputeLongestLineAfterSplice(int, int)}. */
    private boolean longestLineDirty;
    private EditKind lastEditKind = EditKind.NONE;
    private int lastEditCursorLine = -1;
    private int lastEditCursorColumn = -1;
    private long lastEditTimestampNanos;
    private CodeIndentStrategy indentStrategy = CodeIndentStrategy.defaultStrategy();
    private int compoundEditDepth;
    private Array<UndoEntry> compoundEntries;
    private int compoundCursorLine;
    private int compoundCursorColumn;
    /** Label for the group the outermost {@link #beginCompoundEdit(String)} opened, or null. */
    private String compoundLabel;
    private int maxUndoEntries = DEFAULT_MAX_UNDO_ENTRIES;
    private int maxUndoChars = DEFAULT_MAX_UNDO_CHARS;

    public CodeDocument() {
        setText("");
    }

    public void setText(String text) {
        applyText(text, 0, 0);
        clearHistory();
        journal.clear();
        journalBaseVersion = version;
        snapshotVersion = -1;
    }

    private void applyText(String text, int targetCursorLine, int targetCursorColumn) {
        lines.clear();
        String normalized = text == null ? "" : text.replace("\r\n", "\n").replace('\r', '\n');
        int start = 0;
        while (true) {
            int newline = normalized.indexOf('\n', start);
            if (newline < 0) {
                lines.add(new StringBuilder(normalized.substring(start)));
                break;
            }
            lines.add(new StringBuilder(normalized.substring(start, newline)));
            start = newline + 1;
        }
        if (lines.isEmpty()) {
            lines.add(new StringBuilder());
        }
        cursorLine = clamp(targetCursorLine, 0, lines.size - 1);
        cursorColumn = clamp(targetCursorColumn, 0, lines.get(cursorLine).length());
        recomputeLongestLine();
        touch();
    }

    public int getVersion() {
        return version;
    }

    public int getLineCount() {
        return lines.size;
    }

    public String getLine(int line) {
        return lines.get(line).toString();
    }

    /**
     * Returns the backing buffer for a line as a read-only sequence. Avoids the {@code String}
     * allocation of {@link #getLine(int)}; the returned sequence is only valid until the next
     * mutation.
     */
    public CharSequence getLineSequence(int line) {
        return lines.get(line);
    }

    public int getLineLength(int line) {
        return lines.get(line).length();
    }

    public char charAt(int line, int column) {
        return lines.get(line).charAt(column);
    }

    /** Index of the line with the most characters, a cheap upper bound for horizontal extent. */
    public int getLongestLineIndex() {
        ensureLongestLine();
        return longestLineIndex;
    }

    public int getLongestLineLength() {
        ensureLongestLine();
        return longestLineLength;
    }

    public String getText() {
        StringBuilder builder = new StringBuilder(estimateTextLength());
        for (int i = 0; i < lines.size; i++) {
            if (i > 0) {
                builder.append('\n');
            }
            builder.append(lines.get(i));
        }
        return builder.toString();
    }

    public int getCursorLine() {
        return cursorLine;
    }

    public int getCursorColumn() {
        return cursorColumn;
    }

    /** Total character count including the newlines between lines. */
    public int getTextLength() {
        return estimateTextLength();
    }

    /** Converts a line/column pair to a document character offset. */
    public int toOffset(int line, int column) {
        int safeLine = clamp(line, 0, lines.size - 1);
        int offset = 0;
        for (int i = 0; i < safeLine; i++) {
            offset += lines.get(i).length() + 1;
        }
        return offset + clamp(column, 0, lines.get(safeLine).length());
    }

    /** Converts a document character offset to a line index. */
    public int lineAtOffset(int offset) {
        int remaining = Math.max(0, offset);
        for (int i = 0; i < lines.size; i++) {
            int lineLength = lines.get(i).length();
            if (remaining <= lineLength) {
                return i;
            }
            remaining -= lineLength + 1;
        }
        return lines.size - 1;
    }

    /** Converts a document character offset to a column within {@link #lineAtOffset(int)}. */
    public int columnAtOffset(int offset) {
        int remaining = Math.max(0, offset);
        for (int i = 0; i < lines.size; i++) {
            int lineLength = lines.get(i).length();
            if (remaining <= lineLength) {
                return remaining;
            }
            remaining -= lineLength + 1;
        }
        return lines.get(lines.size - 1).length();
    }

    public void moveCursorTo(int line, int column) {
        cursorLine = clamp(line, 0, lines.size - 1);
        cursorColumn = clamp(column, 0, lines.get(cursorLine).length());
        resetMergeState();
    }

    public void moveCursorLeft() {
        if (cursorColumn > 0) {
            // Step over a whole cluster, so the caret never lands between the two halves of a
            // surrogate pair -- a position that is neither before nor after a character.
            cursorColumn = graphemeStartBefore(lines.get(cursorLine), cursorColumn);
            resetMergeState();
            return;
        }
        if (cursorLine > 0) {
            cursorLine--;
            cursorColumn = lines.get(cursorLine).length();
            resetMergeState();
        }
    }

    public void moveCursorRight() {
        StringBuilder current = lines.get(cursorLine);
        if (cursorColumn < current.length()) {
            cursorColumn = graphemeEndAt(current, cursorColumn);
            resetMergeState();
            return;
        }
        if (cursorLine < lines.size - 1) {
            cursorLine++;
            cursorColumn = 0;
            resetMergeState();
        }
    }

    /** Toggles between column 0 and the first non-whitespace character. */
    public void moveCursorHome() {
        // Uses the strategy's definition of leading whitespace, which counts tabs; the old
        // spaces-only count sent Home to column 0 on a tab-indented line.
        int indent = indentStrategy.leadingWhitespaceLength(lines.get(cursorLine));
        cursorColumn = cursorColumn == indent ? 0 : indent;
        resetMergeState();
    }

    public void moveCursorEnd() {
        cursorColumn = lines.get(cursorLine).length();
        resetMergeState();
    }

    public void insertChar(char character) {
        int beforeLine = cursorLine;
        int beforeColumn = cursorColumn;
        recordUndo(EditKind.INSERT, beforeLine, beforeColumn, beforeLine, beforeLine, 1);
        lines.get(cursorLine).insert(cursorColumn, character);
        cursorColumn++;
        afterEdit(EditKind.INSERT, beforeLine, 1, 1);
    }

    public void insertText(String text) {
        if (text == null || text.isEmpty()) {
            return;
        }

        String normalized = text.replace("\r\n", "\n").replace('\r', '\n');
        EditKind kind = normalized.indexOf('\n') < 0 ? EditKind.INSERT : EditKind.BULK_INSERT;
        int beforeLine = cursorLine;
        int beforeColumn = cursorColumn;
        // The undo entry must know how many lines this insert will occupy, otherwise undoing it
        // replaces only the first line and orphans the rest.
        int newLineCount = 1;
        for (int i = 0; i < normalized.length(); i++) {
            if (normalized.charAt(i) == '\n') {
                newLineCount++;
            }
        }
        recordUndo(kind, beforeLine, beforeColumn, beforeLine, beforeLine, newLineCount);

        StringBuilder currentLine = lines.get(cursorLine);
        String suffix = currentLine.substring(cursorColumn);
        currentLine.setLength(cursorColumn);

        boolean firstSegment = true;
        int segmentStart = 0;
        int lineIndex = cursorLine;
        while (true) {
            int newline = normalized.indexOf('\n', segmentStart);
            int segmentEnd = newline < 0 ? normalized.length() : newline;
            if (firstSegment) {
                currentLine.append(normalized, segmentStart, segmentEnd);
                firstSegment = false;
            } else {
                lineIndex++;
                lines.insert(lineIndex, new StringBuilder(normalized.substring(segmentStart, segmentEnd)));
            }
            if (newline < 0) {
                break;
            }
            segmentStart = newline + 1;
        }

        cursorLine = lineIndex;
        cursorColumn = lines.get(lineIndex).length();
        lines.get(lineIndex).append(suffix);
        afterEdit(kind, beforeLine, 1, newLineCount);
    }

    public void deleteRange(int startLine, int startColumn, int endLine, int endColumn) {
        if (startLine > endLine || (startLine == endLine && startColumn > endColumn)) {
            int tempLine = startLine;
            int tempColumn = startColumn;
            startLine = endLine;
            startColumn = endColumn;
            endLine = tempLine;
            endColumn = tempColumn;
        }

        startLine = clamp(startLine, 0, lines.size - 1);
        endLine = clamp(endLine, 0, lines.size - 1);
        startColumn = clamp(startColumn, 0, lines.get(startLine).length());
        endColumn = clamp(endColumn, 0, lines.get(endLine).length());
        if (startLine == endLine && startColumn == endColumn) {
            return;
        }

        int beforeLine = cursorLine;
        int beforeColumn = cursorColumn;
        recordUndo(EditKind.DELETE_RANGE, beforeLine, beforeColumn, startLine, endLine, 1);

        if (startLine == endLine) {
            lines.get(startLine).delete(startColumn, endColumn);
        } else {
            StringBuilder head = lines.get(startLine);
            head.setLength(startColumn);
            head.append(lines.get(endLine), endColumn, lines.get(endLine).length());
            for (int line = endLine; line > startLine; line--) {
                lines.removeIndex(line);
            }
        }

        cursorLine = startLine;
        cursorColumn = startColumn;
        afterEdit(EditKind.DELETE_RANGE, startLine, endLine - startLine + 1, 1);
    }

    public String getTextRange(int startLine, int startColumn, int endLine, int endColumn) {
        if (startLine > endLine || (startLine == endLine && startColumn > endColumn)) {
            int tempLine = startLine;
            int tempColumn = startColumn;
            startLine = endLine;
            startColumn = endColumn;
            endLine = tempLine;
            endColumn = tempColumn;
        }

        startLine = clamp(startLine, 0, lines.size - 1);
        endLine = clamp(endLine, 0, lines.size - 1);
        startColumn = clamp(startColumn, 0, lines.get(startLine).length());
        endColumn = clamp(endColumn, 0, lines.get(endLine).length());

        if (startLine == endLine) {
            return lines.get(startLine).substring(startColumn, endColumn);
        }

        StringBuilder builder = new StringBuilder();
        builder.append(lines.get(startLine), startColumn, lines.get(startLine).length()).append('\n');
        for (int line = startLine + 1; line < endLine; line++) {
            builder.append(lines.get(line)).append('\n');
        }
        builder.append(lines.get(endLine), 0, endColumn);
        return builder.toString();
    }

    public void insertNewLine() {
        int beforeLine = cursorLine;
        int beforeColumn = cursorColumn;
        recordUndo(EditKind.INSERT_NEWLINE, beforeLine, beforeColumn, beforeLine, beforeLine, 2);

        StringBuilder currentLine = lines.get(cursorLine);
        String indentText = indentStrategy.indentForColumn(
            indentStrategy.indentAfter(currentLine, cursorColumn));

        StringBuilder next = new StringBuilder(indentText.length() + currentLine.length() - cursorColumn);
        next.append(indentText);
        next.append(currentLine, cursorColumn, currentLine.length());
        currentLine.setLength(cursorColumn);
        lines.insert(cursorLine + 1, next);
        cursorLine++;
        cursorColumn = indentText.length();
        afterEdit(EditKind.INSERT_NEWLINE, beforeLine, 1, 2);
    }

    // ---- grapheme clusters ----------------------------------------------------

    private static final int ZWJ = 0x200D;
    private static final int KEYCAP = 0x20E3;
    private static final int TAG_FIRST = 0xE0020;
    private static final int TAG_LAST = 0xE007F;
    private static final int VARIATION_SELECTOR_FIRST = 0xFE00;
    private static final int VARIATION_SELECTOR_LAST = 0xFE0F;
    private static final int EMOJI_MODIFIER_FIRST = 0x1F3FB;
    private static final int EMOJI_MODIFIER_LAST = 0x1F3FF;
    private static final int REGIONAL_INDICATOR_FIRST = 0x1F1E6;
    private static final int REGIONAL_INDICATOR_LAST = 0x1F1FF;

    /**
     * Where the grapheme cluster ending at {@code end} starts.
     *
     * <p>A cluster is one user-perceived character: a base code point plus whatever extends it, and a
     * code point above U+FFFF is two {@code char}s. This is what makes a 😀 disappear in one backspace
     * and a 👨‍👩‍👧‍👦 in one backspace too, instead of leaving a stranded surrogate behind that renders as a
     * second, separate box. It covers the forms an emoji actually takes: a surrogate pair, a skin-tone
     * modifier, a variation selector, a keycap, a flag's two regional indicators, a tag sequence, and
     * any chain of those glued together by zero-width joiners. Plain text is unaffected, because a
     * cluster of one {@code char} backs up over exactly one {@code char}.
     *
     * @see #graphemeEndAt(CharSequence, int)
     */
    static int graphemeStartBefore(CharSequence text, int end) {
        if (end <= 0) {
            return 0;
        }
        int stop = Math.min(end, text.length());
        // A flag is exactly two regional indicators and nothing extends it, and the pairs are counted
        // from the start of the run, so it takes its own count rather than the base-and-extender scan.
        if (isRegionalIndicator(text, stop - 2)) {
            int start = stop - 2;
            while (start >= 2 && isRegionalIndicator(text, start - 2)) {
                start -= 2;
            }
            int indicators = (stop - start) / 2;
            // An even run is whole flags, and the cluster is the trailing pair; an odd one ends on a lone
            // indicator, which is a cluster of its own.
            return stop - (indicators % 2 == 0 ? 4 : 2);
        }
        int index = stop;
        while (index > 0) {
            // What trails a base belongs to the base before it, so the extenders are absorbed first and
            // the base only then; absorbing them the other way round would take the diacritic off the
            // letter it modifies and put it on the one after.
            while (index > 0) {
                if (index >= 2 && isEmojiModifier(text, index - 2)) {
                    index -= 2;
                    continue;
                }
                int extended = extenderWidthBefore(text, index);
                if (extended > 0) {
                    index -= extended;
                    continue;
                }
                break;
            }
            if (index > 0) {
                index -= unitBackward(text, index);
            }
            // A joiner glues the base just consumed to the one behind it, so the cluster reaches back
            // over the joiner for another base and its own extenders.
            if (index > 0 && text.charAt(index - 1) == ZWJ) {
                index -= 1;
                continue;
            }
            break;
        }
        return index;
    }

    /**
     * Where the grapheme cluster starting at {@code index} ends, the forward twin of
     * {@link #graphemeStartBefore(CharSequence, int)}. Cursor-right and forward delete move by this, so
     * they and backspace agree about where every cluster's edges are.
     */
    static int graphemeEndAt(CharSequence text, int index) {
        int length = text.length();
        if (index >= length) {
            return length;
        }
        if (isRegionalIndicator(text, index)) {
            int end = index;
            while (end + 1 < length && isRegionalIndicator(text, end)) {
                end += 2;
            }
            int indicators = (end - index) / 2;
            return Math.min(length, index + (indicators >= 2 ? 4 : 2));
        }
        int at = index;
        boolean consumedBase = false;
        while (at < length) {
            if (!consumedBase) {
                at += unitForward(text, at);
                consumedBase = true;
                continue;
            }
            if (isEmojiModifier(text, at)) {
                at += 2;
                continue;
            }
            int extended = extenderWidthAt(text, at);
            if (extended > 0) {
                at += extended;
                continue;
            }
            if (text.charAt(at) == ZWJ) {
                at += 1;
                consumedBase = false;
                continue;
            }
            break;
        }
        return at;
    }

    /** The width in {@code char}s of the code unit ending at {@code end}: two for a surrogate pair. */
    private static int unitBackward(CharSequence text, int end) {
        if (end >= 2 && Character.isLowSurrogate(text.charAt(end - 1))
            && Character.isHighSurrogate(text.charAt(end - 2))) {
            return 2;
        }
        return 1;
    }

    /** The width in {@code char}s of the code unit at {@code index}: two for a surrogate pair. */
    private static int unitForward(CharSequence text, int index) {
        if (index + 1 < text.length() && Character.isHighSurrogate(text.charAt(index))
            && Character.isLowSurrogate(text.charAt(index + 1))) {
            return 2;
        }
        return 1;
    }

    /**
     * Whether a code unit starting here is a supplementary code point in the emoji-modifier range, the
     * skin tones that turn 👍 into 👍🏽. Modifiers extend the base before them.
     */
    private static boolean isEmojiModifier(CharSequence text, int index) {
        return codePointAt(text, index, EMOJI_MODIFIER_FIRST, EMOJI_MODIFIER_LAST);
    }

    /** Whether a surrogate pair starting here is one regional indicator, half of a flag. */
    private static boolean isRegionalIndicator(CharSequence text, int index) {
        return codePointAt(text, index, REGIONAL_INDICATOR_FIRST, REGIONAL_INDICATOR_LAST);
    }

    private static boolean codePointAt(CharSequence text, int index, int first, int last) {
        if (index < 0 || index + 1 >= text.length()) {
            return false;
        }
        char high = text.charAt(index);
        char low = text.charAt(index + 1);
        if (!Character.isHighSurrogate(high) || !Character.isLowSurrogate(low)) {
            return false;
        }
        int codePoint = Character.toCodePoint(high, low);
        return codePoint >= first && codePoint <= last;
    }

    /**
     * The width in {@code char}s of the extender code unit at {@code index}, or 0 if the code unit there
     * does not extend the base before it. A tag character is a supplementary code point, so this is not
     * just {@link #isExtender(char)}: the tag characters of a flag's tag sequence occupy plane 14 and
     * each takes two {@code char}s.
     */
    private static int extenderWidthAt(CharSequence text, int index) {
        if (index < 0 || index >= text.length()) {
            return 0;
        }
        char first = text.charAt(index);
        if (Character.isHighSurrogate(first) && index + 1 < text.length()
            && Character.isLowSurrogate(text.charAt(index + 1))) {
            return codePointAt(text, index, TAG_FIRST, TAG_LAST) ? 2 : 0;
        }
        return isExtender(first) ? 1 : 0;
    }

    /** The backward twin of {@link #extenderWidthAt(CharSequence, int)}. */
    private static int extenderWidthBefore(CharSequence text, int end) {
        if (end <= 0 || end > text.length()) {
            return 0;
        }
        int width = unitBackward(text, end);
        return extenderWidthAt(text, end - width);
    }

    /**
     * Whether a BMP code point extends the base before it: a variation selector, a combining diacritic,
     * or a keycap. Tag characters are deliberately excluded, because they are supplementary and belong to
     * {@link #extenderWidthAt(CharSequence, int)}; the zero-width joiner is excluded too, because it is
     * not an extension of its base but a glue between two bases, and each direction treats it explicitly.
     */
    private static boolean isExtender(char character) {
        if (character >= VARIATION_SELECTOR_FIRST && character <= VARIATION_SELECTOR_LAST) {
            return true;
        }
        if (character == KEYCAP) {
            return true;
        }
        int type = Character.getType(character);
        return type == Character.NON_SPACING_MARK || type == Character.ENCLOSING_MARK;
    }

    public void backspace() {
        if (cursorColumn > 0) {
            int beforeLine = cursorLine;
            int beforeColumn = cursorColumn;
            recordUndo(EditKind.BACKSPACE, beforeLine, beforeColumn, beforeLine, beforeLine, 1);
            // A whole grapheme cluster goes at once. Removing one char of a surrogate pair would leave a
            // stray half behind, and a 😀 that takes two presses is the bug this fixes.
            int start = graphemeStartBefore(lines.get(cursorLine), cursorColumn);
            lines.get(cursorLine).delete(start, cursorColumn);
            cursorColumn = start;
            afterEdit(EditKind.BACKSPACE, beforeLine, 1, 1);
            return;
        }

        if (cursorLine == 0) {
            return;
        }

        int beforeLine = cursorLine;
        int beforeColumn = cursorColumn;
        int targetLine = cursorLine - 1;
        recordUndo(EditKind.BACKSPACE, beforeLine, beforeColumn, targetLine, cursorLine, 1);
        int previousLength = lines.get(targetLine).length();
        lines.get(targetLine).append(lines.get(cursorLine));
        lines.removeIndex(cursorLine);
        cursorLine = targetLine;
        cursorColumn = previousLength;
        afterEdit(EditKind.BACKSPACE, targetLine, 2, 1);
    }

    public void deleteForward() {
        StringBuilder current = lines.get(cursorLine);
        if (cursorColumn < current.length()) {
            int beforeLine = cursorLine;
            int beforeColumn = cursorColumn;
            recordUndo(EditKind.DELETE_FORWARD, beforeLine, beforeColumn, beforeLine, beforeLine, 1);
            int end = graphemeEndAt(current, cursorColumn);
            current.delete(cursorColumn, end);
            afterEdit(EditKind.DELETE_FORWARD, beforeLine, 1, 1);
            return;
        }

        if (cursorLine >= lines.size - 1) {
            return;
        }

        int beforeLine = cursorLine;
        int beforeColumn = cursorColumn;
        recordUndo(EditKind.DELETE_FORWARD, beforeLine, beforeColumn, beforeLine, beforeLine + 1, 1);
        current.append(lines.get(cursorLine + 1));
        lines.removeIndex(cursorLine + 1);
        afterEdit(EditKind.DELETE_FORWARD, beforeLine, 2, 1);
    }

    /**
     * Removes one indent level before a just-typed closing brace, so it lines up with its opener.
     *
     * <p>Rewrites the whole leading whitespace rather than deleting a fixed number of characters,
     * which is what makes it correct for tabs and for mixed tab/space indentation.
     */
    public void dedentBeforeClosingBrace() {
        StringBuilder current = lines.get(cursorLine);
        if (!indentStrategy.shouldDedentBefore(current, cursorColumn, '}')) {
            return;
        }

        int targetColumn = Math.max(0,
            indentStrategy.visualColumn(current, cursorColumn) - indentStrategy.indentWidth);
        String replacement = indentStrategy.indentForColumn(targetColumn);

        int beforeLine = cursorLine;
        int beforeColumn = cursorColumn;
        recordUndo(EditKind.AUTO_DEDENT, beforeLine, beforeColumn, beforeLine, beforeLine, 1);
        current.delete(0, cursorColumn);
        current.insert(0, replacement);
        cursorColumn = replacement.length();
        afterEdit(EditKind.AUTO_DEDENT, beforeLine, 1, 1);
    }

    public CodeIndentStrategy getIndentStrategy() {
        return indentStrategy;
    }

    public void setIndentStrategy(CodeIndentStrategy indentStrategy) {
        this.indentStrategy = indentStrategy == null
            ? CodeIndentStrategy.defaultStrategy()
            : indentStrategy;
    }

    /**
     * Indents or dedents whole lines by one level, as one undo step.
     *
     * <p>This is what Tab and Shift-Tab do with a multi-line selection. Blank lines are left alone when
     * indenting so no trailing whitespace is introduced.
     *
     * @return true when any line changed
     */
    public boolean shiftLinesIndent(int fromLine, int toLine, boolean increase) {
        int start = clamp(Math.min(fromLine, toLine), 0, lines.size - 1);
        int end = clamp(Math.max(fromLine, toLine), 0, lines.size - 1);

        boolean changed = false;
        beginCompoundEdit();
        try {
            for (int line = start; line <= end; line++) {
                StringBuilder text = lines.get(line);
                // Indenting an empty line would only add trailing whitespace.
                if (increase && text.length() == 0) {
                    continue;
                }
                int whitespace = indentStrategy.leadingWhitespaceLength(text);
                int column = indentStrategy.visualColumn(text, whitespace);
                int target = increase
                    ? column + indentStrategy.indentWidth
                    : Math.max(0, column - indentStrategy.indentWidth);
                if (target == column) {
                    continue;
                }
                String replacement = indentStrategy.indentForColumn(target);
                if (replacement.length() == whitespace && matchesPrefix(text, replacement)) {
                    continue;
                }

                // Keep the caret the same distance into the text, not the same absolute column.
                int caretOffsetInText = line == cursorLine ? Math.max(0, cursorColumn - whitespace) : -1;
                recordUndo(EditKind.INDENT_LINES, cursorLine, cursorColumn, line, line, 1);
                text.delete(0, whitespace);
                text.insert(0, replacement);
                if (caretOffsetInText >= 0) {
                    cursorColumn = Math.min(text.length(), replacement.length() + caretOffsetInText);
                }
                afterEdit(EditKind.INDENT_LINES, line, 1, 1);
                changed = true;
            }
        } finally {
            endCompoundEdit();
        }
        if (changed) {
            cursorLine = clamp(cursorLine, 0, lines.size - 1);
            cursorColumn = clamp(cursorColumn, 0, lines.get(cursorLine).length());
        }
        return changed;
    }

    private static boolean matchesPrefix(CharSequence text, String prefix) {
        if (text.length() < prefix.length()) {
            return false;
        }
        for (int i = 0; i < prefix.length(); i++) {
            if (text.charAt(i) != prefix.charAt(i)) {
                return false;
            }
        }
        return true;
    }

    public boolean canUndo() {
        return undoStack.size > 0;
    }

    public boolean canRedo() {
        return redoStack.size > 0;
    }

    /**
     * Caps how many undo steps are kept. Values below 1 are raised to 1, because the newest entry is
     * never dropped. Lowering the cap trims the history immediately rather than at the next edit.
     */
    public void setMaxUndoEntries(int entries) {
        maxUndoEntries = Math.max(1, entries);
        trimUndoStack();
    }

    public int getMaxUndoEntries() {
        return maxUndoEntries;
    }

    /**
     * Caps the retained text, counted the same way {@link #getUndoChars()} reports it. Values below 1
     * are raised to 1. A single edit larger than the whole budget is still kept, since the newest entry
     * is never dropped; the cap bounds the history, not one edit.
     */
    public void setMaxUndoChars(int chars) {
        maxUndoChars = Math.max(1, chars);
        trimUndoStack();
    }

    public int getMaxUndoChars() {
        return maxUndoChars;
    }

    /** Undo steps currently on the stack. A compound group counts as one. */
    public int getUndoEntryCount() {
        return undoStack.size;
    }

    public int getRedoEntryCount() {
        return redoStack.size;
    }

    /**
     * Approximate characters retained by the undo stack, including per-entry overhead. This is the
     * number {@link #setMaxUndoChars(int)} bounds, not an exact heap figure.
     */
    public int getUndoChars() {
        return undoStackChars;
    }

    /** Label of the next undo step, or null when it has none or there is nothing to undo. */
    public String getUndoLabel() {
        return undoStack.size == 0 ? null : undoStack.peek().label;
    }

    /** Label of the next redo step, or null when it has none or there is nothing to redo. */
    public String getRedoLabel() {
        return redoStack.size == 0 ? null : redoStack.peek().label;
    }

    /** Drops undo and redo history without touching the text. */
    public void clearUndoHistory() {
        clearHistory();
    }

    public boolean undo() {
        if (undoStack.size == 0) {
            return false;
        }
        UndoEntry entry = undoStack.pop();
        undoStackChars -= entry.chars();
        UndoEntry inverse = applyEntry(entry, true);
        redoStack.add(inverse);
        resetEditState();
        return true;
    }

    public boolean redo() {
        if (redoStack.size == 0) {
            return false;
        }
        UndoEntry entry = redoStack.pop();
        UndoEntry inverse = applyEntry(entry, false);
        // Deliberately not pushToUndoStack: that clears the redo stack, which would make one redo
        // discard every remaining one. trimUndoStack is still needed, or lowering a budget and then
        // redoing grows the stack past it with nothing to pull it back until the next edit.
        undoStack.add(inverse);
        undoStackChars += inverse.chars();
        trimUndoStack();
        resetEditState();
        return true;
    }

    public void beginCompoundEdit() {
        beginCompoundEdit(null);
    }

    /**
     * Opens a compound edit that carries {@code label}, readable afterwards through
     * {@link #getUndoLabel()} so a host can show "Undo Rename" rather than plain "Undo".
     *
     * <p>Only the outermost call's label is kept. An inner group cannot rename the step it is part of,
     * which matters because the editor's own mutators open unlabelled groups of their own and would
     * otherwise clear a label the caller set.
     */
    public void beginCompoundEdit(String label) {
        if (compoundEditDepth == 0) {
            compoundEntries = new Array<>();
            compoundCursorLine = cursorLine;
            compoundCursorColumn = cursorColumn;
            compoundLabel = label;
        }
        compoundEditDepth++;
    }

    /** Whether a {@link #beginCompoundEdit()} group is open, at any nesting depth. */
    public boolean isCompoundEditInProgress() {
        return compoundEditDepth > 0;
    }

    public void endCompoundEdit() {
        if (compoundEditDepth <= 0) {
            return;
        }
        compoundEditDepth--;
        if (compoundEditDepth > 0) {
            return;
        }

        Array<UndoEntry> collected = compoundEntries;
        String label = compoundLabel;
        compoundEntries = null;
        compoundLabel = null;
        if (collected == null || collected.size == 0) {
            return;
        }
        if (collected.size == 1) {
            UndoEntry only = collected.first();
            only.label = label;
            pushToUndoStack(only);
        } else {
            UndoEntry composite = UndoEntry.composite(collected, compoundCursorLine, compoundCursorColumn);
            composite.inverseCursorLine = cursorLine;
            composite.inverseCursorColumn = cursorColumn;
            composite.label = label;
            pushToUndoStack(composite);
        }
        resetMergeState();
    }

    /**
     * Applies {@code entry} and returns the entry that reverses this application, so undo and redo
     * are the same operation with the stacks swapped.
     */
    private UndoEntry applyEntry(UndoEntry entry, boolean reverseOrder) {
        if (entry.children != null) {
            Array<UndoEntry> inverses = new Array<>(entry.children.size);
            if (reverseOrder) {
                for (int i = entry.children.size - 1; i >= 0; i--) {
                    inverses.add(applyEntry(entry.children.get(i), true));
                }
            } else {
                for (int i = 0; i < entry.children.size; i++) {
                    inverses.add(applyEntry(entry.children.get(i), false));
                }
            }
            cursorLine = clamp(entry.cursorLine, 0, lines.size - 1);
            cursorColumn = clamp(entry.cursorColumn, 0, lines.get(cursorLine).length());
            UndoEntry inverse = UndoEntry.composite(inverses, entry.inverseCursorLine, entry.inverseCursorColumn);
            // Carried across, or the label would survive undo but vanish on redo.
            inverse.label = entry.label;
            return inverse;
        }

        int startLine = entry.startLine;
        int replacedCount = Math.max(1, entry.insertedCount);
        int endLine = Math.min(lines.size - 1, startLine + replacedCount - 1);

        Array<String> displaced = new Array<>(endLine - startLine + 1);
        for (int line = startLine; line <= endLine; line++) {
            displaced.add(lines.get(line).toString());
        }

        splice(startLine, endLine, entry.removedLines);

        cursorLine = clamp(entry.cursorLine, 0, lines.size - 1);
        cursorColumn = clamp(entry.cursorColumn, 0, lines.get(cursorLine).length());

        recomputeLongestLineAfterSplice(startLine, entry.removedLines.size);
        journalReplace(startLine, displaced.size, entry.removedLines.size);
        touch();

        UndoEntry inverse = new UndoEntry(
            startLine,
            displaced,
            entry.removedLines.size,
            entry.inverseCursorLine,
            entry.inverseCursorColumn,
            entry.cursorLine,
            entry.cursorColumn
        );
        inverse.label = entry.label;
        return inverse;
    }

    /** Replaces lines {@code startLine..endLine} (inclusive) with {@code replacement}. */
    private void splice(int startLine, int endLine, Array<String> replacement) {
        int removeCount = endLine - startLine + 1;
        int reuse = Math.min(removeCount, replacement.size);
        for (int i = 0; i < reuse; i++) {
            StringBuilder buffer = lines.get(startLine + i);
            buffer.setLength(0);
            buffer.append(replacement.get(i));
        }
        if (removeCount > replacement.size) {
            for (int line = startLine + removeCount - 1; line >= startLine + replacement.size; line--) {
                lines.removeIndex(line);
            }
        } else {
            for (int i = reuse; i < replacement.size; i++) {
                lines.insert(startLine + i, new StringBuilder(replacement.get(i)));
            }
        }
        if (lines.isEmpty()) {
            lines.add(new StringBuilder());
        }
    }

    private void recordUndo(
        EditKind kind,
        int cursorLineBefore,
        int cursorColumnBefore,
        int startLine,
        int endLine,
        int insertedCount
    ) {
        if (compoundEditDepth == 0
            && canMergeWithPreviousEdit(kind, cursorLineBefore, cursorColumnBefore, startLine, endLine, insertedCount)) {
            return;
        }

        Array<String> removed = new Array<>(endLine - startLine + 1);
        for (int line = startLine; line <= endLine; line++) {
            removed.add(lines.get(line).toString());
        }
        UndoEntry entry = new UndoEntry(
            startLine,
            removed,
            insertedCount,
            cursorLineBefore,
            cursorColumnBefore,
            cursorLineBefore,
            cursorColumnBefore
        );

        if (compoundEditDepth > 0) {
            compoundEntries.add(entry);
        } else {
            pushToUndoStack(entry);
            redoStack.clear();
        }
    }

    private void pushToUndoStack(UndoEntry entry) {
        undoStack.add(entry);
        undoStackChars += entry.chars();
        redoStack.clear();
        trimUndoStack();
    }

    /**
     * Drops the oldest entries until both budgets are met. The {@code size > 1} guard is deliberate: the
     * newest entry is never dropped, so a single edit larger than the whole character budget still
     * undoes once instead of leaving the user with no way back.
     */
    private void trimUndoStack() {
        while (undoStack.size > 1 && (undoStack.size > maxUndoEntries || undoStackChars > maxUndoChars)) {
            UndoEntry dropped = undoStack.removeIndex(0);
            undoStackChars -= dropped.chars();
        }
    }

    private void afterEdit(EditKind kind, int startLine, int removedCount, int insertedCount) {
        UndoEntry pending = pendingEntryForCursor();
        if (pending != null) {
            pending.inverseCursorLine = cursorLine;
            pending.inverseCursorColumn = cursorColumn;
        }
        recomputeLongestLineAfterSplice(startLine, insertedCount);
        journalReplace(startLine, removedCount, insertedCount);
        touch();
        if (compoundEditDepth == 0) {
            lastEditKind = kind;
            lastEditCursorLine = cursorLine;
            lastEditCursorColumn = cursorColumn;
            lastEditTimestampNanos = System.nanoTime();
        }
    }

    /** The entry whose post-edit cursor should track the newest mutation, if any. */
    private UndoEntry pendingEntryForCursor() {
        if (compoundEditDepth > 0) {
            return compoundEntries == null || compoundEntries.size == 0 ? null : compoundEntries.peek();
        }
        return undoStack.size == 0 ? null : undoStack.peek();
    }

    private boolean canMergeWithPreviousEdit(
        EditKind kind,
        int cursorLineBefore,
        int cursorColumnBefore,
        int startLine,
        int endLine,
        int insertedCount
    ) {
        if (!kind.mergeable || lastEditKind != kind || undoStack.size == 0) {
            return false;
        }
        // Only same-line, line-count-preserving edits may merge: the surviving entry keeps its own
        // removedLines, so a merged edit that adds or removes lines would invert to the wrong shape.
        if (startLine != endLine || insertedCount != 1) {
            return false;
        }
        if (cursorLineBefore != lastEditCursorLine || cursorColumnBefore != lastEditCursorColumn) {
            return false;
        }
        UndoEntry previous = undoStack.peek();
        if (previous.children != null || previous.insertedCount != 1 || previous.removedLines.size != 1) {
            return false;
        }
        // A named step is closed. Merging a later keystroke into it would silently put that keystroke
        // under someone else's label, so "Undo Rename" would take back a character the rename never
        // touched. endCompoundEdit already resets the merge state, so this only guards a labelled
        // single-entry group; keeping it explicit means a future caller cannot reopen the hole.
        if (previous.label != null) {
            return false;
        }
        if (previous.startLine != startLine) {
            return false;
        }
        return System.nanoTime() - lastEditTimestampNanos <= MERGE_WINDOW_NANOS;
    }

    /**
     * Feeds every line replacement applied since {@code sinceVersion} to {@code visitor}, oldest
     * first.
     *
     * @return {@code false} when the journal no longer reaches that far back, in which case the
     *     caller must rebuild from scratch.
     */
    public boolean replayEditsSince(int sinceVersion, LineEditVisitor visitor) {
        if (sinceVersion < journalBaseVersion || sinceVersion > version) {
            return false;
        }
        if (sinceVersion == version) {
            return true;
        }
        for (int i = 0; i < journal.size; i++) {
            LineEdit edit = journal.get(i);
            if (edit.version > sinceVersion) {
                visitor.onLineEdit(edit.startLine, edit.removedCount, edit.insertedCount);
            }
        }
        return true;
    }

    /** Oldest version the journal can still replay from. */
    public int getJournalBaseVersion() {
        return journalBaseVersion;
    }

    private void journalReplace(int startLine, int removedCount, int insertedCount) {
        journal.add(new LineEdit(version + 1, startLine, removedCount, insertedCount));
        while (journal.size > MAX_JOURNAL_SIZE) {
            LineEdit dropped = journal.removeIndex(0);
            journalBaseVersion = dropped.version;
        }
    }

    /**
     * Returns every line as a {@code String}, reusing a cached array that is patched from the edit
     * journal instead of rebuilt. The returned array is owned by the document and is invalidated by
     * the next mutation; callers that retain it must copy.
     */
    public Array<String> sharedLineSnapshot() {
        if (snapshotVersion == version) {
            return cachedSnapshot;
        }
        if (snapshotVersion < 0 || !patchSnapshot()) {
            cachedSnapshot.clear();
            cachedSnapshot.ensureCapacity(lines.size);
            for (int i = 0; i < lines.size; i++) {
                cachedSnapshot.add(lines.get(i).toString());
            }
        }
        snapshotVersion = version;
        return cachedSnapshot;
    }

    private boolean patchSnapshot() {
        final int[] touched = {0};
        boolean replayed = replayEditsSince(snapshotVersion, new LineEditVisitor() {
            @Override
            public void onLineEdit(int startLine, int removedCount, int insertedCount) {
                touched[0] += Math.max(removedCount, insertedCount);
                for (int line = startLine + removedCount - 1; line >= startLine + insertedCount; line--) {
                    if (line < cachedSnapshot.size) {
                        cachedSnapshot.removeIndex(line);
                    }
                }
                for (int i = 0; i < insertedCount; i++) {
                    int line = startLine + i;
                    if (i < removedCount && line < cachedSnapshot.size) {
                        continue;
                    }
                    if (line <= cachedSnapshot.size) {
                        cachedSnapshot.insert(line, "");
                    }
                }
                for (int i = 0; i < insertedCount; i++) {
                    int line = startLine + i;
                    if (line < cachedSnapshot.size && line < lines.size) {
                        cachedSnapshot.set(line, lines.get(line).toString());
                    }
                }
            }
        });
        if (!replayed) {
            return false;
        }
        // A rebuild is cheaper than thousands of shifting inserts.
        if (touched[0] > 4096) {
            return false;
        }
        return cachedSnapshot.size == lines.size;
    }

    /** @deprecated prefer {@link #sharedLineSnapshot()}, which does not allocate per call. */
    @Deprecated
    public Array<String> snapshotLines() {
        Array<String> copy = new Array<>(lines.size);
        for (int i = 0; i < lines.size; i++) {
            copy.add(lines.get(i).toString());
        }
        return copy;
    }

    /** Pays for a deferred rescan, at most once per read no matter how many edits preceded it. */
    private void ensureLongestLine() {
        if (longestLineDirty) {
            recomputeLongestLine();
        }
    }

    private void recomputeLongestLine() {
        longestLineDirty = false;
        longestLineIndex = 0;
        longestLineLength = 0;
        for (int i = 0; i < lines.size; i++) {
            int length = lines.get(i).length();
            if (length > longestLineLength) {
                longestLineLength = length;
                longestLineIndex = i;
            }
        }
    }

    /**
     * Keeps {@link #getLongestLineLength()} a valid upper bound without rescanning the document:
     * grows for the lines just written, and otherwise defers the rescan to the next read.
     *
     * <p>Deferring rather than rescanning here is what keeps a large compound edit linear. Replace All
     * walks its matches from the last line backwards, so once the champion sits above the cursor every
     * remaining edit would "disturb" it; rescanning on the spot made the whole operation
     * O(matches x lines), which is minutes on a 100k line document. Now the rescan happens once, when
     * something actually asks for the width.
     */
    private void recomputeLongestLineAfterSplice(int startLine, int insertedCount) {
        int endLine = Math.min(lines.size - 1, startLine + insertedCount - 1);
        boolean championDisturbed = longestLineDirty || longestLineIndex >= startLine;
        for (int line = startLine; line <= endLine; line++) {
            int length = lines.get(line).length();
            if (length > longestLineLength) {
                longestLineLength = length;
                longestLineIndex = line;
                // A line longer than the stale record is the true champion either way, so a pending
                // rescan is no longer needed.
                championDisturbed = false;
            }
        }
        longestLineDirty = championDisturbed;
    }

    private int estimateTextLength() {
        int total = Math.max(0, lines.size - 1);
        for (int i = 0; i < lines.size; i++) {
            total += lines.get(i).length();
        }
        return total;
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private void clearHistory() {
        undoStack.clear();
        redoStack.clear();
        undoStackChars = 0;
        resetEditState();
    }

    private void touch() {
        version++;
    }

    private void resetMergeState() {
        lastEditKind = EditKind.NONE;
        lastEditCursorLine = -1;
        lastEditCursorColumn = -1;
        lastEditTimestampNanos = 0L;
    }

    private void resetEditState() {
        resetMergeState();
        compoundEditDepth = 0;
        compoundEntries = null;
        compoundLabel = null;
    }

    private enum EditKind {
        NONE(false),
        INSERT(true),
        BACKSPACE(true),
        DELETE_FORWARD(true),
        DELETE_RANGE(false),
        INSERT_NEWLINE(false),
        AUTO_DEDENT(false),
        BULK_INSERT(false),
        INDENT_LINES(false),
        COMPOUND(false);

        final boolean mergeable;

        EditKind(boolean mergeable) {
            this.mergeable = mergeable;
        }
    }

    /** One line replacement, enough for a view to patch per-line caches. */
    private static final class LineEdit {
        final int version;
        final int startLine;
        final int removedCount;
        final int insertedCount;

        LineEdit(int version, int startLine, int removedCount, int insertedCount) {
            this.version = version;
            this.startLine = startLine;
            this.removedCount = removedCount;
            this.insertedCount = insertedCount;
        }
    }

    /** Receives line replacements during {@link #replayEditsSince(int, LineEditVisitor)}. */
    public interface LineEditVisitor {
        /**
         * @param startLine first replaced line
         * @param removedCount lines that were present before the edit
         * @param insertedCount lines present after the edit
         */
        void onLineEdit(int startLine, int removedCount, int insertedCount);
    }

    /**
     * Stores only the lines an edit replaced. {@code insertedCount} says how many lines currently
     * occupy that slot, so the inverse can be derived at undo time; that keeps merged keystrokes
     * correct without rewriting the entry on every character.
     */
    private static final class UndoEntry {
        final int startLine;
        final Array<String> removedLines;
        final int insertedCount;
        final Array<UndoEntry> children;
        /** Where the caret goes when this entry is applied. */
        final int cursorLine;
        final int cursorColumn;
        /** Where the caret goes when the entry produced by applying this one is applied. */
        int inverseCursorLine;
        int inverseCursorColumn;
        /** Caller-supplied name for this step, or null. Carried onto the inverse so it survives redo. */
        String label;

        UndoEntry(
            int startLine,
            Array<String> removedLines,
            int insertedCount,
            int cursorLine,
            int cursorColumn,
            int inverseCursorLine,
            int inverseCursorColumn
        ) {
            this.startLine = startLine;
            this.removedLines = removedLines;
            this.insertedCount = insertedCount;
            this.children = null;
            this.cursorLine = cursorLine;
            this.cursorColumn = cursorColumn;
            this.inverseCursorLine = inverseCursorLine;
            this.inverseCursorColumn = inverseCursorColumn;
        }

        private UndoEntry(Array<UndoEntry> children, int cursorLine, int cursorColumn) {
            this.startLine = children.size == 0 ? 0 : children.first().startLine;
            this.removedLines = null;
            this.insertedCount = 0;
            this.children = children;
            this.cursorLine = cursorLine;
            this.cursorColumn = cursorColumn;
            this.inverseCursorLine = cursorLine;
            this.inverseCursorColumn = cursorColumn;
        }

        static UndoEntry composite(Array<UndoEntry> children, int cursorLine, int cursorColumn) {
            return new UndoEntry(children, cursorLine, cursorColumn);
        }

        int chars() {
            if (children != null) {
                int total = 0;
                for (int i = 0; i < children.size; i++) {
                    total += children.get(i).chars();
                }
                return total;
            }
            int total = 16;
            for (int i = 0; i < removedLines.size; i++) {
                total += removedLines.get(i).length() + 8;
            }
            return total;
        }
    }
}
