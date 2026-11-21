package com.lzt841.editor.extensions;

import com.badlogic.gdx.Input;
import com.badlogic.gdx.utils.Array;
import com.lzt841.editor.CodeEditor;
import com.lzt841.editor.CodeEditorCommand;
import com.lzt841.editor.CodeEditorTextEdit;
import com.lzt841.editor.CodeEditorTextRange;
import com.lzt841.editor.CodeKeyStroke;

import java.util.Locale;

/** Optional, undoable text commands demonstrating scoped command registration. */
public final class CodeTextEditingExtension implements CodeEditorExtension {
    public static final String ID = "editor.textEditing";
    public static final String UPPERCASE_SELECTION = "editor.selection.uppercase";
    public static final String LOWERCASE_SELECTION = "editor.selection.lowercase";
    public static final String TRIM_TRAILING_WHITESPACE = "editor.whitespace.trimTrailing";

    @Override
    public String getId() {
        return ID;
    }

    @Override
    public void install(CodeExtensionContext context) {
        context.registerCommand(new CodeEditorCommand(UPPERCASE_SELECTION, "Uppercase Selection", "Text", true,
            editor -> editor.hasSelection() && !editor.isPasswordMode(),
            editor -> changeCase(editor, true)), CodeKeyStroke.ctrlShift(Input.Keys.U));
        context.registerCommand(new CodeEditorCommand(LOWERCASE_SELECTION, "Lowercase Selection", "Text", true,
            editor -> editor.hasSelection() && !editor.isPasswordMode(),
            editor -> changeCase(editor, false)), CodeKeyStroke.ctrlAlt(Input.Keys.U));
        context.registerCommand(new CodeEditorCommand(TRIM_TRAILING_WHITESPACE, "Trim Trailing Whitespace", "Text", true,
            editor -> !editor.isPasswordMode(), CodeTextEditingExtension::trimTrailingWhitespace));
    }

    private static boolean changeCase(CodeEditor editor, boolean uppercase) {
        CodeEditorTextRange selection = editor.getSelection();
        if (selection == null || selection.isEmpty()) return false;
        String original = editor.getTextRange(selection);
        String replacement = uppercase ? original.toUpperCase(Locale.ROOT) : original.toLowerCase(Locale.ROOT);
        if (original.equals(replacement)) return false;
        boolean[] changed = {false};
        editor.runAsSingleUndoStep(uppercase ? "Uppercase Selection" : "Lowercase Selection", () -> {
            changed[0] = editor.replaceRange(selection, replacement);
            if (changed[0]) {
                // Filters and Unicode case mappings can change the length. Use the actual endpoint.
                editor.setSelection(selection.startLine, selection.startColumn,
                    editor.getCursorLine(), editor.getCursorColumn());
            }
        });
        return changed[0];
    }

    private static boolean trimTrailingWhitespace(CodeEditor editor) {
        Array<CodeEditorTextEdit> edits = new Array<>();
        for (int line = 0; line < editor.getLineCount(); line++) {
            int length = editor.getLineLength(line);
            int end = length;
            while (end > 0) {
                char character = editor.getCharAt(line, end - 1);
                if (character != ' ' && character != '\t') break;
                end--;
            }
            if (end < length) edits.add(new CodeEditorTextEdit(line, end, line, length, ""));
        }
        return editor.applyEdits(edits, "Trim Trailing Whitespace");
    }
}
