package com.lzt841.editor;

import com.badlogic.gdx.utils.Array;

import java.util.ArrayDeque;

/** Mutable line-based document optimized for editor style operations. */
public class CodeDocument {
    public static final int INDENT_SIZE = 4;
    private static final int MAX_HISTORY_SIZE = 200;
    private static final long MERGE_WINDOW_NANOS = 1_000_000_000L;

    private final Array<StringBuilder> lines = new Array<>();
    private final ArrayDeque<DocumentState> undoStack = new ArrayDeque<>();
    private final ArrayDeque<DocumentState> redoStack = new ArrayDeque<>();
    private int cursorLine;
    private int cursorColumn;
    private int version;
    private EditKind lastEditKind = EditKind.NONE;
    private int lastEditCursorLine = -1;
    private int lastEditCursorColumn = -1;
    private long lastEditTimestampNanos;
    private int compoundEditDepth;
    private boolean compoundEditRecorded;

    public CodeDocument() {
        setText("");
    }

    public void setText(String text) {
        applyText(text, 0, 0);
        clearHistory();
    }

    public boolean canUndo() {
        return !undoStack.isEmpty();
    }

    public boolean canRedo() {
        return !redoStack.isEmpty();
    }

    public boolean undo() {
        if (undoStack.isEmpty()) {
            return false;
        }

        resetEditState();
        redoStack.push(captureState());
        restoreState(undoStack.pop());
        return true;
    }

    public boolean redo() {
        if (redoStack.isEmpty()) {
            return false;
        }

        resetEditState();
        undoStack.push(captureState());
        restoreState(redoStack.pop());
        return true;
    }

    public void beginCompoundEdit() {
        compoundEditDepth++;
    }

    public void endCompoundEdit() {
        if (compoundEditDepth <= 0) {
            return;
        }
        compoundEditDepth--;
        if (compoundEditDepth == 0) {
            if (compoundEditRecorded) {
                finishEdit(EditKind.COMPOUND);
            }
            compoundEditRecorded = false;
        }
    }

    private void applyText(String text, int targetCursorLine, int targetCursorColumn) {
        lines.clear();
        String normalized = text.replace("\r\n", "\n").replace('\r', '\n');
        String[] split = normalized.split("\n", -1);
        for (String line : split) {
            lines.add(new StringBuilder(line));
        }
        if (lines.isEmpty()) {
            lines.add(new StringBuilder());
        }
        cursorLine = clamp(targetCursorLine, 0, lines.size - 1);
        cursorColumn = clamp(targetCursorColumn, 0, lines.get(cursorLine).length());
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

    public String getText() {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < lines.size; i++) {
            if (i > 0) {
                builder.append('\n');
            }
            builder.append(lines.get(i));
        }
        return builder.toString();
    }

    public int getLineLength(int line) {
        return lines.get(line).length();
    }

    public int getCursorLine() {
        return cursorLine;
    }

    public int getCursorColumn() {
        return cursorColumn;
    }

    public void moveCursorTo(int line, int column) {
        cursorLine = clamp(line, 0, lines.size - 1);
        cursorColumn = clamp(column, 0, lines.get(cursorLine).length());
        resetMergeState();
    }

    public void moveCursorLeft() {
        if (cursorColumn > 0) {
            cursorColumn--;
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
        if (cursorColumn < lines.get(cursorLine).length()) {
            cursorColumn++;
            resetMergeState();
            return;
        }
        if (cursorLine < lines.size - 1) {
            cursorLine++;
            cursorColumn = 0;
            resetMergeState();
        }
    }

    public void moveCursorHome() {
        int indent = countLeadingWhitespace(lines.get(cursorLine));
        cursorColumn = cursorColumn == indent ? 0 : indent;
        resetMergeState();
    }

    public void moveCursorEnd() {
        cursorColumn = lines.get(cursorLine).length();
        resetMergeState();
    }

    public void insertChar(char character) {
        recordUndoState(EditKind.INSERT);
        lines.get(cursorLine).insert(cursorColumn, character);
        cursorColumn++;
        touch();
        finishEdit(EditKind.INSERT);
    }

    public void insertText(String text) {
        if (text == null || text.isEmpty()) {
            return;
        }

        EditKind kind = isMergeableInsert(text) ? EditKind.INSERT : EditKind.BULK_INSERT;
        recordUndoState(kind);
        String normalized = text.replace("\r\n", "\n").replace('\r', '\n');
        StringBuilder currentLine = lines.get(cursorLine);
        String suffix = currentLine.substring(cursorColumn);
        currentLine.setLength(cursorColumn);

        String[] parts = normalized.split("\n", -1);
        currentLine.append(parts[0]);
        int lineIndex = cursorLine;

        for (int i = 1; i < parts.length; i++) {
            lineIndex++;
            lines.insert(lineIndex, new StringBuilder(parts[i]));
        }

        cursorLine = lineIndex;
        cursorColumn = lines.get(cursorLine).length();
        lines.get(cursorLine).append(suffix);
        touch();
        finishEdit(kind);
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

        recordUndoState(EditKind.DELETE_RANGE);
        if (startLine == endLine) {
            lines.get(startLine).delete(startColumn, endColumn);
        } else {
            String prefix = lines.get(startLine).substring(0, startColumn);
            String suffix = lines.get(endLine).substring(endColumn);
            lines.get(startLine).setLength(0);
            lines.get(startLine).append(prefix).append(suffix);

            for (int line = endLine; line > startLine; line--) {
                lines.removeIndex(line);
            }
        }

        cursorLine = startLine;
        cursorColumn = startColumn;
        touch();
        finishEdit(EditKind.DELETE_RANGE);
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
        builder.append(lines.get(startLine).substring(startColumn)).append('\n');
        for (int line = startLine + 1; line < endLine; line++) {
            builder.append(lines.get(line)).append('\n');
        }
        builder.append(lines.get(endLine).substring(0, endColumn));
        return builder.toString();
    }

    public void insertNewLine() {
        recordUndoState(EditKind.INSERT_NEWLINE);
        StringBuilder currentLine = lines.get(cursorLine);
        String left = currentLine.substring(0, cursorColumn);
        String right = currentLine.substring(cursorColumn);
        int indent = countLeadingWhitespace(left);

        if (left.trim().endsWith("{")) {
            indent += INDENT_SIZE;
        }

        currentLine.setLength(cursorColumn);
        lines.insert(cursorLine + 1, new StringBuilder(spaces(indent)).append(right));
        cursorLine++;
        cursorColumn = indent;
        touch();
        finishEdit(EditKind.INSERT_NEWLINE);
    }

    public void backspace() {
        if (cursorColumn > 0) {
            recordUndoState(EditKind.BACKSPACE);
            lines.get(cursorLine).deleteCharAt(cursorColumn - 1);
            cursorColumn--;
            touch();
            finishEdit(EditKind.BACKSPACE);
            return;
        }

        if (cursorLine == 0) {
            return;
        }

        recordUndoState(EditKind.BACKSPACE);
        int previousLength = lines.get(cursorLine - 1).length();
        lines.get(cursorLine - 1).append(lines.get(cursorLine));
        lines.removeIndex(cursorLine);
        cursorLine--;
        cursorColumn = previousLength;
        touch();
        finishEdit(EditKind.BACKSPACE);
    }

    public void deleteForward() {
        StringBuilder current = lines.get(cursorLine);
        if (cursorColumn < current.length()) {
            recordUndoState(EditKind.DELETE_FORWARD);
            current.deleteCharAt(cursorColumn);
            touch();
            finishEdit(EditKind.DELETE_FORWARD);
            return;
        }

        if (cursorLine >= lines.size - 1) {
            return;
        }

        recordUndoState(EditKind.DELETE_FORWARD);
        current.append(lines.get(cursorLine + 1));
        lines.removeIndex(cursorLine + 1);
        touch();
        finishEdit(EditKind.DELETE_FORWARD);
    }

    public void dedentBeforeClosingBrace() {
        StringBuilder current = lines.get(cursorLine);
        if (cursorColumn < INDENT_SIZE) {
            return;
        }

        for (int i = cursorColumn - INDENT_SIZE; i < cursorColumn; i++) {
            if (current.charAt(i) != ' ') {
                return;
            }
        }

        for (int i = 0; i < cursorColumn - INDENT_SIZE; i++) {
            if (!Character.isWhitespace(current.charAt(i))) {
                return;
            }
        }

        recordUndoState(EditKind.AUTO_DEDENT);
        current.delete(cursorColumn - INDENT_SIZE, cursorColumn);
        cursorColumn -= INDENT_SIZE;
        touch();
        finishEdit(EditKind.AUTO_DEDENT);
    }

    public Array<String> snapshotLines() {
        Array<String> copy = new Array<>(lines.size);
        for (StringBuilder line : lines) {
            copy.add(line.toString());
        }
        return copy;
    }

    private int countLeadingWhitespace(CharSequence text) {
        int count = 0;
        while (count < text.length() && text.charAt(count) == ' ') {
            count++;
        }
        return count;
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static String spaces(int count) {
        StringBuilder builder = new StringBuilder(count);
        for (int i = 0; i < count; i++) {
            builder.append(' ');
        }
        return builder.toString();
    }

    private void clearHistory() {
        undoStack.clear();
        redoStack.clear();
        resetEditState();
    }

    private void recordUndoState(EditKind kind) {
        if (compoundEditDepth > 0) {
            if (!compoundEditRecorded) {
                pushUndoState();
                compoundEditRecorded = true;
            }
            return;
        }
        if (canMergeWithPreviousEdit(kind)) {
            return;
        }
        pushUndoState();
    }

    private void pushUndoState() {
        undoStack.push(captureState());
        while (undoStack.size() > MAX_HISTORY_SIZE) {
            undoStack.removeLast();
        }
        redoStack.clear();
    }

    private boolean canMergeWithPreviousEdit(EditKind kind) {
        if (!kind.mergeable || lastEditKind != kind) {
            return false;
        }
        if (cursorLine != lastEditCursorLine || cursorColumn != lastEditCursorColumn) {
            return false;
        }
        return System.nanoTime() - lastEditTimestampNanos <= MERGE_WINDOW_NANOS;
    }

    private void finishEdit(EditKind kind) {
        if (compoundEditDepth > 0) {
            return;
        }
        lastEditKind = kind;
        lastEditCursorLine = cursorLine;
        lastEditCursorColumn = cursorColumn;
        lastEditTimestampNanos = System.nanoTime();
    }

    private DocumentState captureState() {
        return new DocumentState(getText(), cursorLine, cursorColumn);
    }

    private void restoreState(DocumentState state) {
        applyText(state.text, state.cursorLine, state.cursorColumn);
        resetEditState();
    }

    private void touch() {
        version++;
    }

    private boolean isMergeableInsert(String text) {
        return text.indexOf('\n') < 0 && text.indexOf('\r') < 0;
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
        compoundEditRecorded = false;
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
        COMPOUND(false);

        final boolean mergeable;

        EditKind(boolean mergeable) {
            this.mergeable = mergeable;
        }
    }

    private static final class DocumentState {
        final String text;
        final int cursorLine;
        final int cursorColumn;

        DocumentState(String text, int cursorLine, int cursorColumn) {
            this.text = text;
            this.cursorLine = cursorLine;
            this.cursorColumn = cursorColumn;
        }
    }
}
