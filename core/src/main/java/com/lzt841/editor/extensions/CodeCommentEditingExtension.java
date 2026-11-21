package com.lzt841.editor.extensions;

import com.badlogic.gdx.Input;
import com.badlogic.gdx.utils.Array;
import com.lzt841.editor.CodeEditor;
import com.lzt841.editor.CodeEditorCommand;
import com.lzt841.editor.CodeEditorTextEdit;
import com.lzt841.editor.CodeEditorTextRange;
import com.lzt841.editor.CodeKeyStroke;

/** Optional line-comment toggling with exact edits and preserved selection direction. */
public final class CodeCommentEditingExtension implements CodeEditorExtension {
    public static final String ID = "editor.commentEditing";
    public static final String TOGGLE_LINE_COMMENT = "editor.comments.toggleLine";
    private final boolean defaultShortcuts;
    private String lineCommentPrefix;

    public CodeCommentEditingExtension() { this("//", true); }

    public CodeCommentEditingExtension(String lineCommentPrefix) { this(lineCommentPrefix, true); }

    /** False registers the command for menus/palettes without a default Ctrl/Command+/ shortcut. */
    public CodeCommentEditingExtension(String lineCommentPrefix, boolean defaultShortcuts) {
        setLineCommentPrefix(lineCommentPrefix);
        this.defaultShortcuts = defaultShortcuts;
    }

    @Override public String getId() { return ID; }

    public String getLineCommentPrefix() { return lineCommentPrefix; }

    /**
     * Changes syntax without reinstalling the extension. Null disables the command, for languages
     * without line comments. A non-null prefix must be a nonempty token without whitespace/control
     * characters. For example: "//", "#", "--" or ";". One optional space is managed separately.
     */
    public CodeCommentEditingExtension setLineCommentPrefix(String prefix) {
        if (prefix != null) {
            if (prefix.isEmpty()) throw new IllegalArgumentException("line comment prefix must not be empty");
            for (int offset = 0; offset < prefix.length();) {
                int character = prefix.codePointAt(offset);
                if (Character.isWhitespace(character) || Character.isSpaceChar(character)
                    || Character.isISOControl(character)) {
                    throw new IllegalArgumentException("line comment prefix must not contain whitespace or controls");
                }
                offset += Character.charCount(character);
            }
        }
        lineCommentPrefix = prefix;
        return this;
    }

    @Override public void install(CodeExtensionContext context) {
        context.registerCommand(new CodeEditorCommand(TOGGLE_LINE_COMMENT, "Toggle Line Comment", "Comments", true,
            editor -> lineCommentPrefix != null && !editor.isPasswordMode(), this::toggle, true),
            defaultShortcuts ? new CodeKeyStroke[] {CodeKeyStroke.ctrl(Input.Keys.SLASH)} : new CodeKeyStroke[0]);
    }

    private boolean toggle(CodeEditor editor) {
        String prefix = lineCommentPrefix;
        if (prefix == null) return false;
        CodeEditorTextRange selection = editor.getSelection();
        int cursorLine = editor.getCursorLine();
        int cursorColumn = editor.getCursorColumn();
        boolean backward = selection != null && cursorLine == selection.startLine && cursorColumn == selection.startColumn;
        int first = selection == null ? cursorLine : selection.startLine;
        int last = selection == null ? cursorLine : selection.endLine
            - (selection.endLine > selection.startLine && selection.endColumn == 0 ? 1 : 0);
        boolean uncomment = true;
        for (int line = first; line <= last; line++) {
            String text = editor.getLineText(line);
            int indent = indentLength(text);
            if (indent < text.length() && !text.startsWith(prefix, indent)) {
                uncomment = false;
                break;
            }
        }
        Array<CodeEditorTextEdit> edits = new Array<>();
        for (int line = first; line <= last; line++) {
            String text = editor.getLineText(line);
            int indent = indentLength(text);
            if (indent == text.length()) {
                // Ignore blank lines in a selection. A caret on a blank line starts a new comment.
                if (selection == null) edits.add(CodeEditorTextEdit.insert(line, indent, prefix + " "));
            } else if (uncomment) {
                int end = indent + prefix.length();
                if (end < text.length() && text.charAt(end) == ' ') end++;
                edits.add(new CodeEditorTextEdit(line, indent, line, end, ""));
            } else {
                edits.add(CodeEditorTextEdit.insert(line, indent, prefix + " "));
            }
        }
        boolean[] changed = {false};
        editor.runAsSingleUndoStep("Toggle Line Comment", () -> {
            changed[0] = editor.applyEditsExact(edits, "Toggle Line Comment");
            if (!changed[0]) return;
            editor.revealLine(cursorLine);
            if (selection == null) {
                editor.setCursorPosition(cursorLine, shiftedColumn(edits, cursorLine, cursorColumn));
            } else {
                int start = shiftedColumn(edits, selection.startLine, selection.startColumn);
                int end = shiftedColumn(edits, selection.endLine, selection.endColumn);
                if (backward) editor.setSelection(selection.endLine, end, selection.startLine, start);
                else editor.setSelection(selection.startLine, start, selection.endLine, end);
            }
        });
        return changed[0];
    }

    private static int indentLength(String text) {
        int column = 0;
        while (column < text.length() && (text.charAt(column) == ' ' || text.charAt(column) == '\t')) column++;
        return column;
    }

    private static int shiftedColumn(Array<CodeEditorTextEdit> edits, int line, int column) {
        for (CodeEditorTextEdit edit : edits) {
            CodeEditorTextRange range = edit.range;
            if (range.startLine != line || column < range.startColumn) continue;
            if (range.isEmpty()) return column + edit.replacement.length();
            return Math.max(range.startColumn, column - (range.endColumn - range.startColumn));
        }
        return column;
    }
}
