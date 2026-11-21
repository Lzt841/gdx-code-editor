package com.lzt841.editor.extensions;

import com.badlogic.gdx.Input;
import com.lzt841.editor.CodeEditor;
import com.lzt841.editor.CodeEditorCommand;
import com.lzt841.editor.CodeEditorPosition;
import com.lzt841.editor.CodeEditorTextRange;
import com.lzt841.editor.CodeKeyStroke;

/** Optional Unicode-aware word navigation and deletion, scoped to the editor extension lifecycle. */
public final class CodeWordEditingExtension implements CodeEditorExtension {
    public static final String ID = "editor.wordEditing";
    public static final String MOVE_WORD_LEFT = "editor.words.moveLeft";
    public static final String MOVE_WORD_RIGHT = "editor.words.moveRight";
    public static final String SELECT_WORD_LEFT = "editor.words.selectLeft";
    public static final String SELECT_WORD_RIGHT = "editor.words.selectRight";
    public static final String DELETE_PREVIOUS_WORD = "editor.words.deletePrevious";
    public static final String DELETE_NEXT_WORD = "editor.words.deleteNext";

    /** The built-in shortcuts for the platform convention the host wants to use. */
    public enum ShortcutProfile {
        /** Ctrl+Arrow / Ctrl+Backspace / Ctrl+Delete, common on Windows and Linux. */
        WINDOWS_LINUX,
        /** Option+Arrow / Option+Backspace / Option+Forward Delete, common on macOS. */
        MACOS,
        /** Register commands for menus and palettes, without reserving any keys. */
        NONE
    }

    private final ShortcutProfile shortcutProfile;

    /** Installs the extension with Windows/Linux-style Ctrl shortcuts. */
    public CodeWordEditingExtension() {
        this(ShortcutProfile.WINDOWS_LINUX);
    }

    public CodeWordEditingExtension(ShortcutProfile shortcutProfile) {
        if (shortcutProfile == null) throw new IllegalArgumentException("shortcut profile is required");
        this.shortcutProfile = shortcutProfile;
    }

    /** Convenience factory for macOS Option-based word editing shortcuts. */
    public static CodeWordEditingExtension macOSDefaults() {
        return new CodeWordEditingExtension(ShortcutProfile.MACOS);
    }

    @Override public String getId() {
        return ID;
    }

    @Override public void install(CodeExtensionContext context) {
        CodeKeyStroke left = shortcut(Input.Keys.LEFT, false);
        CodeKeyStroke right = shortcut(Input.Keys.RIGHT, false);
        CodeKeyStroke selectLeft = shortcut(Input.Keys.LEFT, true);
        CodeKeyStroke selectRight = shortcut(Input.Keys.RIGHT, true);
        CodeKeyStroke backspace = shortcut(Input.Keys.BACKSPACE, false);
        CodeKeyStroke delete = shortcut(Input.Keys.FORWARD_DEL, false);

        register(context, command(MOVE_WORD_LEFT, "Move Word Left", false,
            editor -> move(editor, false, false)), left);
        register(context, command(MOVE_WORD_RIGHT, "Move Word Right", false,
            editor -> move(editor, true, false)), right);
        register(context, command(SELECT_WORD_LEFT, "Select Word Left", false,
            editor -> move(editor, false, true)), selectLeft);
        register(context, command(SELECT_WORD_RIGHT, "Select Word Right", false,
            editor -> move(editor, true, true)), selectRight);
        register(context, command(DELETE_PREVIOUS_WORD, "Delete Previous Word", true,
            editor -> deleteWord(editor, false)), backspace);
        register(context, command(DELETE_NEXT_WORD, "Delete Next Word", true,
            editor -> deleteWord(editor, true)), delete);
    }

    private void register(CodeExtensionContext context, CodeEditorCommand command, CodeKeyStroke shortcut) {
        if (shortcutProfile == ShortcutProfile.NONE) context.registerCommand(command);
        else context.registerCommand(command, shortcut);
    }

    private CodeEditorCommand command(String id, String label, boolean editable,
                                      CodeEditorCommand.Handler handler) {
        return new CodeEditorCommand(id, label, "Words", editable,
            editor -> !editor.isPasswordMode(), handler, true);
    }

    private CodeKeyStroke shortcut(int keycode, boolean shift) {
        switch (shortcutProfile) {
            case WINDOWS_LINUX:
                return shift ? CodeKeyStroke.ctrlShift(keycode) : CodeKeyStroke.ctrl(keycode);
            case MACOS:
                return new CodeKeyStroke(keycode, false, shift, true);
            default:
                return null;
        }
    }

    private static boolean move(CodeEditor editor, boolean forward, boolean extendSelection) {
        CodeEditorTextRange selection = editor.getSelection();
        CodeEditorPosition caret = editor.getCursorPosition();
        if (!extendSelection && selection != null) {
            if (forward) editor.setCursorPosition(selection.endLine, selection.endColumn);
            else editor.setCursorPosition(selection.startLine, selection.startColumn);
            return true;
        }

        Position target = new Position(caret.line, caret.column);
        if (forward) moveToNextBoundary(editor, target);
        else moveToPreviousBoundary(editor, target);
        if (target.sameAs(caret.line, caret.column)) return false;

        if (!extendSelection) {
            editor.setCursorPosition(target.line, target.column);
            return true;
        }

        Position anchor = new Position(caret.line, caret.column);
        if (selection != null) {
            int startOffset = editor.toOffset(selection.startLine, selection.startColumn);
            int endOffset = editor.toOffset(selection.endLine, selection.endColumn);
            if (caret.offset == startOffset) anchor.set(selection.endLine, selection.endColumn);
            else anchor.set(selection.startLine, selection.startColumn);
        }
        editor.setSelection(anchor.line, anchor.column, target.line, target.column);
        return true;
    }

    private static boolean deleteWord(CodeEditor editor, boolean forward) {
        CodeEditorTextRange selection = editor.getSelection();
        CodeEditorTextRange range;
        if (selection != null) {
            range = selection;
        } else {
            Position start = new Position(editor.getCursorLine(), editor.getCursorColumn());
            Position end = new Position(start.line, start.column);
            if (forward) moveToNextBoundary(editor, end);
            else moveToPreviousBoundary(editor, start);
            if (start.sameAs(end.line, end.column)) return false;
            range = new CodeEditorTextRange(start.line, start.column, end.line, end.column);
        }

        final boolean[] changed = {false};
        String label = forward ? "Delete Next Word" : "Delete Previous Word";
        editor.runAsSingleUndoStep(label, () -> changed[0] = editor.replaceRange(range, ""));
        return changed[0];
    }

    /** Move to the start of the preceding non-whitespace run, skipping whitespace first. */
    private static void moveToPreviousBoundary(CodeEditor editor, Position position) {
        int codePoint;
        while ((codePoint = previousCodePoint(editor, position)) >= 0 && isWhitespace(codePoint)) {
            // Whitespace, including line breaks, is skipped before the preceding run.
        }
        if (codePoint < 0) return;
        boolean word = isWord(codePoint);
        while ((codePoint = previousCodePoint(editor, position)) >= 0
            && !isWhitespace(codePoint) && isWord(codePoint) == word) {
            // Keep punctuation runs separate from identifier runs.
        }
        if (codePoint >= 0) nextCodePoint(editor, position); // Restore the first character of this run.
    }

    /** Move past the current run and trailing whitespace, stopping before the next run. */
    private static void moveToNextBoundary(CodeEditor editor, Position position) {
        int codePoint = nextCodePoint(editor, position);
        if (codePoint < 0) return;
        if (isWhitespace(codePoint)) {
            while ((codePoint = nextCodePoint(editor, position)) >= 0 && isWhitespace(codePoint)) {
                // Skip whitespace up to the next run.
            }
            if (codePoint >= 0) previousCodePoint(editor, position);
            return;
        }

        boolean word = isWord(codePoint);
        while ((codePoint = nextCodePoint(editor, position)) >= 0) {
            if (isWhitespace(codePoint)) {
                while ((codePoint = nextCodePoint(editor, position)) >= 0 && isWhitespace(codePoint)) {
                    // Include the whitespace after the current run.
                }
                if (codePoint >= 0) previousCodePoint(editor, position);
                return;
            }
            if (isWord(codePoint) != word) {
                previousCodePoint(editor, position);
                return;
            }
        }
    }

    /** Returns the previous Unicode code point and moves {@code position} before it. */
    private static int previousCodePoint(CodeEditor editor, Position position) {
        if (position.column > 0) {
            char last = editor.getCharAt(position.line, position.column - 1);
            if (Character.isLowSurrogate(last) && position.column > 1) {
                char first = editor.getCharAt(position.line, position.column - 2);
                if (Character.isHighSurrogate(first)) {
                    position.column -= 2;
                    return Character.toCodePoint(first, last);
                }
            }
            position.column--;
            return last;
        }
        if (position.line == 0) return -1;
        position.line--;
        position.column = editor.getLineLength(position.line);
        return '\n';
    }

    /** Returns the next Unicode code point and moves {@code position} after it. */
    private static int nextCodePoint(CodeEditor editor, Position position) {
        int lineLength = editor.getLineLength(position.line);
        if (position.column < lineLength) {
            char first = editor.getCharAt(position.line, position.column);
            if (Character.isHighSurrogate(first) && position.column + 1 < lineLength) {
                char second = editor.getCharAt(position.line, position.column + 1);
                if (Character.isLowSurrogate(second)) {
                    position.column += 2;
                    return Character.toCodePoint(first, second);
                }
            }
            position.column++;
            return first;
        }
        if (position.line + 1 >= editor.getLineCount()) return -1;
        position.line++;
        position.column = 0;
        return '\n';
    }

    private static boolean isWord(int codePoint) {
        return Character.isLetterOrDigit(codePoint) || codePoint == '_' || codePoint == '$';
    }

    private static boolean isWhitespace(int codePoint) {
        return codePoint == '\n' || Character.isWhitespace(codePoint) || Character.isSpaceChar(codePoint);
    }

    private static final class Position {
        int line;
        int column;

        Position(int line, int column) {
            set(line, column);
        }

        void set(int line, int column) {
            this.line = line;
            this.column = column;
        }

        boolean sameAs(int otherLine, int otherColumn) {
            return line == otherLine && column == otherColumn;
        }
    }
}
