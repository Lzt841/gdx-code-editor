package com.lzt841.editor;

/**
 * The built-in auto-edit strategy: auto-closing brackets and quotes, wrapping a selection, typing
 * through a closer, deleting a pair with one Backspace, and splitting a brace block on Enter.
 *
 * <pre>{@code
 * editor.setAutoEditStrategy(new CodeBracketAutoEditStrategy());
 * }</pre>
 *
 * <p>Defaults to the C-family pairs {@code () [] {} "" ''}. Pass your own to
 * {@link #CodeBracketAutoEditStrategy(String, String, String)} for a different language — for example
 * no single-quote pair in a language where {@code '} is an apostrophe.
 *
 * <p>Each behaviour can be turned off individually. The decisions are deliberately local — the
 * character before the caret, the character at the caret, and whether the caret is inside a string or
 * comment — because anything more needs a parser, and a wrong guess here is far more annoying than a
 * missing convenience.
 */
public class CodeBracketAutoEditStrategy implements CodeAutoEditStrategy {
    /** Openers of the default pairs, in the same order as {@link #DEFAULT_CLOSERS}. */
    public static final String DEFAULT_OPENERS = "([{\"'";
    /** Closers of the default pairs. */
    public static final String DEFAULT_CLOSERS = ")]}\"'";
    /** Pairs whose two characters are identical, so "is this an opener?" needs context. */
    public static final String DEFAULT_SYMMETRIC = "\"'";

    private final String openers;
    private final String closers;
    private final String symmetric;

    private boolean autoClose = true;
    private boolean wrapSelection = true;
    private boolean overtypeClosers = true;
    private boolean deletePairs = true;
    private boolean smartEnter = true;
    private boolean skipInsideStringsAndComments = true;

    public CodeBracketAutoEditStrategy() {
        this(DEFAULT_OPENERS, DEFAULT_CLOSERS, DEFAULT_SYMMETRIC);
    }

    /**
     * @param openers one character per pair, the character that opens it
     * @param closers the matching closers, same length and order as {@code openers}
     * @param symmetric the subset of openers whose closer is the same character, typically quotes
     */
    public CodeBracketAutoEditStrategy(String openers, String closers, String symmetric) {
        if (openers == null || closers == null || openers.length() != closers.length()) {
            throw new IllegalArgumentException("openers and closers must be non-null and the same length");
        }
        this.openers = openers;
        this.closers = closers;
        this.symmetric = symmetric == null ? "" : symmetric;
    }

    /** Insert the closer when an opener is typed. */
    public CodeBracketAutoEditStrategy setAutoClose(boolean autoClose) {
        this.autoClose = autoClose;
        return this;
    }

    /** Typing an opener with text selected surrounds the selection instead of replacing it. */
    public CodeBracketAutoEditStrategy setWrapSelection(boolean wrapSelection) {
        this.wrapSelection = wrapSelection;
        return this;
    }

    /** Typing a closer that is already at the caret steps over it rather than inserting a second one. */
    public CodeBracketAutoEditStrategy setOvertypeClosers(boolean overtypeClosers) {
        this.overtypeClosers = overtypeClosers;
        return this;
    }

    /** Backspace between an empty pair removes both characters. */
    public CodeBracketAutoEditStrategy setDeletePairs(boolean deletePairs) {
        this.deletePairs = deletePairs;
        return this;
    }

    /** Enter between {@code {} } opens an indented blank line and pushes the closer down. */
    public CodeBracketAutoEditStrategy setSmartEnter(boolean smartEnter) {
        this.smartEnter = smartEnter;
        return this;
    }

    /**
     * Skip auto-closing inside strings and comments, where a bracket is usually literal text. Relies on
     * {@link CodeEditor#isInStringOrComment(int, int)}, so it is only as accurate as the highlighter.
     */
    public CodeBracketAutoEditStrategy setSkipInsideStringsAndComments(boolean skip) {
        this.skipInsideStringsAndComments = skip;
        return this;
    }

    public boolean isAutoClose() {
        return autoClose;
    }

    public boolean isWrapSelection() {
        return wrapSelection;
    }

    public boolean isOvertypeClosers() {
        return overtypeClosers;
    }

    public boolean isDeletePairs() {
        return deletePairs;
    }

    public boolean isSmartEnter() {
        return smartEnter;
    }

    public boolean isSkipInsideStringsAndComments() {
        return skipInsideStringsAndComments;
    }

    /** The closer paired with {@code opener}, or 0 when it is not an opener. */
    public char closerFor(char opener) {
        int index = openers.indexOf(opener);
        return index < 0 ? 0 : closers.charAt(index);
    }

    /** The opener paired with {@code closer}, or 0 when it is not a closer. */
    public char openerFor(char closer) {
        int index = closers.indexOf(closer);
        return index < 0 ? 0 : openers.charAt(index);
    }

    /** Whether the pair's two characters are the same, as they are for quotes. */
    public boolean isSymmetric(char character) {
        return symmetric.indexOf(character) >= 0;
    }

    @Override
    public boolean onCharacterTyped(CodeEditor editor, char character) {
        if (wrapSelection && editor.hasSelection()) {
            return wrapSelectionWith(editor, character);
        }
        if (editor.hasSelection()) {
            // A selection is about to be replaced; auto-closing on top of that is more surprising
            // than helpful, so let the editor do its normal thing.
            return false;
        }
        if (overtypeClosers && overtype(editor, character)) {
            return true;
        }
        return autoClose && autoCloseAfter(editor, character);
    }

    /** Surrounds the selection with the pair, leaving the original text selected. */
    private boolean wrapSelectionWith(CodeEditor editor, char character) {
        char closer = closerFor(character);
        if (closer == 0) {
            return false;
        }
        CodeEditorTextRange selection = editor.getSelection();
        String text = editor.getSelectionText();
        if (selection == null || text.isEmpty()) {
            return false;
        }
        if (!editor.replaceRange(selection, character + text + closer)) {
            return false;
        }
        // Re-select the original text, now shifted one character right on its first line. A
        // single-line selection also shifts its end; a multi-line one does not, because only the
        // first line gained the opener.
        int endColumn = selection.startLine == selection.endLine
            ? selection.endColumn + 1
            : selection.endColumn;
        editor.setSelection(
            selection.startLine,
            selection.startColumn + 1,
            selection.endLine,
            endColumn
        );
        return true;
    }

    /** Steps over a closer the caret is already sitting in front of. */
    private boolean overtype(CodeEditor editor, char character) {
        if (openerFor(character) == 0) {
            return false;
        }
        int line = editor.getCursorLine();
        int column = editor.getCursorColumn();
        if (editor.getCharAt(line, column) != character) {
            return false;
        }
        if (isSymmetric(character)) {
            // For a quote, the character at the caret is as likely to be an opener the user is about
            // to type into as a closer to step over. Only step over it when the caret is directly
            // after content, which is where an auto-inserted closer would have been left.
            if (column == 0) {
                return false;
            }
            char before = editor.getCharAt(line, column - 1);
            if (before == character || before == '\\') {
                return false;
            }
        }
        editor.setCursorPosition(line, column + 1);
        return true;
    }

    /** Inserts the pair and leaves the caret between the two characters. */
    private boolean autoCloseAfter(CodeEditor editor, char character) {
        char closer = closerFor(character);
        if (closer == 0) {
            return false;
        }
        int line = editor.getCursorLine();
        int column = editor.getCursorColumn();
        if (skipInsideStringsAndComments && editor.isInStringOrComment(line, column)) {
            return false;
        }
        if (!shouldCloseAt(editor, line, column, character)) {
            return false;
        }
        if (!editor.insertTextAtCursor(String.valueOf(character) + closer)) {
            return false;
        }
        // insertTextAtCursor leaves the caret after both characters; step back between them.
        editor.setCursorPosition(editor.getCursorLine(), editor.getCursorColumn() - 1);
        return true;
    }

    /**
     * Whether a closer should be added for an opener typed at this position.
     *
     * <p>Two rules, both about not getting in the way. A quote is only closed when it reads as an
     * opening quote, so {@code don't} does not become {@code don''t}. Any pair is only closed when the
     * caret is at the end of the line or in front of whitespace or another closer, so typing
     * {@code (} in front of an existing word does not orphan a {@code )} after it.
     *
     * <p>Override to change the policy; the default is intentionally conservative.
     */
    protected boolean shouldCloseAt(CodeEditor editor, int line, int column, char opener) {
        char after = editor.getCharAt(line, column);
        if (isSymmetric(opener)) {
            if (column > 0) {
                char before = editor.getCharAt(line, column - 1);
                if (before == '\\' || isWordCharacter(before) || before == opener) {
                    return false;
                }
            }
            if (isWordCharacter(after)) {
                return false;
            }
        }
        // getCharAt returns 0 past the end of the line.
        return after == 0
            || after == ' '
            || after == '\t'
            || closers.indexOf(after) >= 0
            || after == ',' || after == ';' || after == ':';
    }

    private static boolean isWordCharacter(char character) {
        return Character.isLetterOrDigit(character) || character == '_' || character == '$';
    }

    @Override
    public boolean onBackspace(CodeEditor editor) {
        if (!deletePairs) {
            return false;
        }
        int line = editor.getCursorLine();
        int column = editor.getCursorColumn();
        if (column == 0) {
            return false;
        }
        char before = editor.getCharAt(line, column - 1);
        char closer = closerFor(before);
        if (closer == 0 || editor.getCharAt(line, column) != closer) {
            return false;
        }
        return editor.deleteRange(line, column - 1, line, column + 1);
    }

    @Override
    public boolean onEnter(CodeEditor editor) {
        if (!smartEnter || editor.hasSelection()) {
            return false;
        }
        int line = editor.getCursorLine();
        int column = editor.getCursorColumn();
        if (column == 0) {
            return false;
        }
        // Only the brace pair gets the two-line treatment; splitting a quote pair across lines would
        // usually be a syntax error.
        if (editor.getCharAt(line, column - 1) != '{' || editor.getCharAt(line, column) != '}') {
            return false;
        }

        CodeIndentStrategy indent = editor.getIndentStrategy();
        String lineText = editor.getLineText(line);
        int outerColumn = Math.min(
            indent.leadingIndentColumn(lineText),
            indent.visualColumn(lineText, column)
        );
        String outer = indent.indentForColumn(outerColumn);
        String inner = indent.indentForColumn(outerColumn + indent.indentWidth);
        if (!editor.insertTextAtCursor("\n" + inner + "\n" + outer)) {
            return false;
        }
        // The caret is now on the last inserted line; put it at the end of the indented middle one.
        editor.setCursorPosition(line + 1, inner.length());
        return true;
    }
}
