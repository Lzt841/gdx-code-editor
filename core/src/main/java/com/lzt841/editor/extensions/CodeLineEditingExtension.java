package com.lzt841.editor.extensions;

import com.badlogic.gdx.Input;
import com.badlogic.gdx.utils.Array;
import com.lzt841.editor.CodeEditor;
import com.lzt841.editor.CodeEditorCommand;
import com.lzt841.editor.CodeEditorTextEdit;
import com.lzt841.editor.CodeEditorTextRange;
import com.lzt841.editor.CodeKeyStroke;

/** Optional logical-line editing. Each content change is exact and one named undo step. */
public final class CodeLineEditingExtension implements CodeEditorExtension {
    public static final String ID = "editor.lineEditing";
    public static final String MOVE_UP = "editor.lines.moveUp";
    public static final String MOVE_DOWN = "editor.lines.moveDown";
    public static final String DUPLICATE_UP = "editor.lines.duplicateUp";
    public static final String DUPLICATE_DOWN = "editor.lines.duplicateDown";
    public static final String DELETE = "editor.lines.delete";
    private final boolean defaultShortcuts;

    public CodeLineEditingExtension() { this(true); }

    /** False registers menu/palette commands without assigning any default shortcuts. */
    public CodeLineEditingExtension(boolean defaultShortcuts) { this.defaultShortcuts = defaultShortcuts; }

    @Override public String getId() { return ID; }

    @Override public void install(CodeExtensionContext context) {
        register(context, MOVE_UP, "Move Lines Up", editor -> new LineBlock(editor).first > 0,
            editor -> move(editor, -1), CodeKeyStroke.alt(Input.Keys.UP));
        register(context, MOVE_DOWN, "Move Lines Down", editor -> new LineBlock(editor).last + 1 < editor.getLineCount(),
            editor -> move(editor, 1), CodeKeyStroke.alt(Input.Keys.DOWN));
        register(context, DUPLICATE_UP, "Duplicate Lines Up", null, editor -> duplicate(editor, false),
            new CodeKeyStroke(Input.Keys.UP, false, true, true));
        register(context, DUPLICATE_DOWN, "Duplicate Lines Down", null, editor -> duplicate(editor, true),
            new CodeKeyStroke(Input.Keys.DOWN, false, true, true));
        register(context, DELETE, "Delete Lines", editor -> editor.getLineCount() > 1 || editor.getLineLength(0) > 0,
            CodeLineEditingExtension::delete, CodeKeyStroke.ctrlShift(Input.Keys.K));
    }

    private void register(CodeExtensionContext context, String id, String label, CodeEditorCommand.Condition condition,
                          CodeEditorCommand.Handler handler, CodeKeyStroke shortcut) {
        // Reserve registered chords at boundaries and on filter rejection. Otherwise Alt+Up/Down
        // would fall back to ordinary cursor movement and silently collapse the user's selection.
        context.registerCommand(new CodeEditorCommand(id, label, "Lines", true,
            editor -> !editor.isPasswordMode() && (condition == null || condition.isEnabled(editor)), handler, true),
            defaultShortcuts ? new CodeKeyStroke[] {shortcut} : new CodeKeyStroke[0]);
    }

    private static boolean move(CodeEditor editor, int direction) {
        LineBlock block = new LineBlock(editor);
        if ((direction < 0 && block.first == 0)
            || (direction > 0 && block.last + 1 >= editor.getLineCount())) return false;
        int first = direction < 0 ? block.first - 1 : block.first;
        int last = direction < 0 ? block.last : block.last + 1;
        String selected = block.text(editor);
        String replacement = direction < 0 ? selected + "\n" + editor.getLineText(first)
            : editor.getLineText(last) + "\n" + selected;
        CodeEditorTextRange range = new CodeEditorTextRange(first, 0, last, editor.getLineLength(last));
        if (replacement.equals(editor.getTextRange(range))) {
            block.restore(editor, direction); // Equal adjacent lines still allow the caret to move.
            return true;
        }
        return apply(editor, range, replacement, direction < 0 ? "Move Lines Up" : "Move Lines Down",
            () -> block.restore(editor, direction));
    }

    private static boolean duplicate(CodeEditor editor, boolean below) {
        LineBlock block = new LineBlock(editor);
        int line;
        int column = 0;
        String replacement;
        if (!below) {
            line = block.first;
            replacement = block.text(editor) + "\n";
        } else if (block.last + 1 < editor.getLineCount()) {
            line = block.last + 1;
            replacement = block.text(editor) + "\n";
        } else {
            line = block.last;
            column = editor.getLineLength(line);
            replacement = "\n" + block.text(editor);
        }
        return apply(editor, new CodeEditorTextRange(line, column, line, column), replacement,
            below ? "Duplicate Lines Down" : "Duplicate Lines Up",
            () -> block.restore(editor, below ? block.last - block.first + 1 : 0));
    }

    private static boolean delete(CodeEditor editor) {
        LineBlock block = new LineBlock(editor);
        CodeEditorTextRange range;
        if (block.last + 1 < editor.getLineCount()) {
            range = new CodeEditorTextRange(block.first, 0, block.last + 1, 0);
        } else if (block.first > 0) {
            range = new CodeEditorTextRange(block.first - 1, editor.getLineLength(block.first - 1),
                block.last, editor.getLineLength(block.last));
        } else {
            range = new CodeEditorTextRange(0, 0, block.last, editor.getLineLength(block.last));
        }
        return apply(editor, range, "", "Delete Lines", () -> editor.setCursorPosition(
            Math.min(block.first, editor.getLineCount() - 1), block.selection == null ? block.cursorColumn : 0));
    }

    private static boolean apply(CodeEditor editor, CodeEditorTextRange range, String replacement, String label,
                                 Runnable restore) {
        boolean[] changed = {false};
        editor.runAsSingleUndoStep(label, () -> {
            changed[0] = editor.applyEditsExact(Array.with(new CodeEditorTextEdit(range, replacement)), label);
            if (changed[0]) restore.run();
        });
        return changed[0];
    }

    private static final class LineBlock {
        final CodeEditorTextRange selection;
        final int first;
        final int last;
        final int cursorLine;
        final int cursorColumn;
        final boolean backward;

        LineBlock(CodeEditor editor) {
            selection = editor.getSelection();
            cursorLine = editor.getCursorLine();
            cursorColumn = editor.getCursorColumn();
            first = selection == null ? cursorLine : selection.startLine;
            // The end is exclusive: selecting to the next line's column zero does not include it.
            last = selection == null ? cursorLine : selection.endLine
                - (selection.endLine > selection.startLine && selection.endColumn == 0 ? 1 : 0);
            backward = selection != null && cursorLine == selection.startLine && cursorColumn == selection.startColumn;
        }

        String text(CodeEditor editor) {
            return editor.getTextRange(first, 0, last, editor.getLineLength(last));
        }

        void restore(CodeEditor editor, int delta) {
            if (selection == null) {
                Position cursor = shifted(editor, cursorLine, cursorColumn, delta);
                editor.setCursorPosition(cursor.line, cursor.column);
                return;
            }
            Position start = shifted(editor, selection.startLine, selection.startColumn, delta);
            Position end = shifted(editor, selection.endLine, selection.endColumn, delta);
            editor.revealLine(backward ? start.line : end.line);
            if (backward) editor.setSelection(end.line, end.column, start.line, start.column);
            else editor.setSelection(start.line, start.column, end.line, end.column);
        }

        private Position shifted(CodeEditor editor, int line, int column, int delta) {
            int shiftedLine = line + delta;
            // An exclusive line-start endpoint can move past the final line. Represent EOF at the
            // last line's end instead of clamping its original column zero onto that line.
            if (shiftedLine >= editor.getLineCount()) {
                int finalLine = editor.getLineCount() - 1;
                return new Position(finalLine, editor.getLineLength(finalLine));
            }
            return new Position(shiftedLine, column);
        }
    }

    private static final class Position {
        final int line;
        final int column;
        Position(int line, int column) { this.line = line; this.column = column; }
    }
}
