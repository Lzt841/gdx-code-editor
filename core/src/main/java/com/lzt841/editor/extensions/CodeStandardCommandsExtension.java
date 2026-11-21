package com.lzt841.editor.extensions;

import com.lzt841.editor.CodeEditor;
import com.lzt841.editor.CodeEditorCommand;

/** Common editor operations exposed to command pickers and host menus without rebinding native keys. */
public final class CodeStandardCommandsExtension implements CodeEditorExtension {
    public static final String ID = "editor.standardCommands";
    public static final String UNDO = "editor.history.undo";
    public static final String REDO = "editor.history.redo";
    public static final String SELECT_ALL = "editor.selection.selectAll";
    public static final String COPY = "editor.clipboard.copy";
    public static final String CUT = "editor.clipboard.cut";
    public static final String PASTE = "editor.clipboard.paste";
    public static final String TOGGLE_WRAP = "editor.view.toggleWrap";
    public static final String TOGGLE_LINE_NUMBERS = "editor.view.toggleLineNumbers";
    public static final String NEXT_DIAGNOSTIC = "editor.diagnostic.next";
    public static final String PREVIOUS_DIAGNOSTIC = "editor.diagnostic.previous";

    @Override public String getId() {
        return ID;
    }

    @Override public void install(CodeExtensionContext context) {
        context.registerCommand(new CodeEditorCommand(UNDO, "Undo", "History", true,
            editor -> !editor.isPasswordMode() && editor.canUndo(), CodeEditor::undo));
        context.registerCommand(new CodeEditorCommand(REDO, "Redo", "History", true,
            editor -> !editor.isPasswordMode() && editor.canRedo(), CodeEditor::redo));
        context.registerCommand(new CodeEditorCommand(SELECT_ALL, "Select All", "Selection", false,
            editor -> !editor.isPasswordMode() && editor.getTextLength() > 0,
            editor -> { editor.selectAllText(); return true; }));
        context.registerCommand(new CodeEditorCommand(COPY, "Copy Selection", "Clipboard", false,
            editor -> !editor.isPasswordMode() && editor.hasSelection(), CodeEditor::copySelection));
        context.registerCommand(new CodeEditorCommand(CUT, "Cut Selection", "Clipboard", true,
            editor -> !editor.isPasswordMode() && editor.hasSelection(), CodeEditor::cutSelection));
        context.registerCommand(new CodeEditorCommand(PASTE, "Paste", "Clipboard", true,
            editor -> !editor.isPasswordMode(), CodeEditor::pasteClipboard));
        context.registerCommand(new CodeEditorCommand(TOGGLE_WRAP, "Toggle Word Wrap", "View", false, null,
            editor -> { editor.setWrapEnabled(!editor.isWrapEnabled()); return true; }));
        context.registerCommand(new CodeEditorCommand(TOGGLE_LINE_NUMBERS, "Toggle Line Numbers", "View", false, null,
            editor -> { editor.setLineNumbersVisible(!editor.isLineNumbersVisible()); return true; }));
        context.registerCommand(new CodeEditorCommand(NEXT_DIAGNOSTIC, "Next Diagnostic", "Diagnostics", false,
            CodeStandardCommandsExtension::hasDiagnostics, editor -> editor.navigateToNextDiagnostic() != null));
        context.registerCommand(new CodeEditorCommand(PREVIOUS_DIAGNOSTIC, "Previous Diagnostic", "Diagnostics", false,
            CodeStandardCommandsExtension::hasDiagnostics, editor -> editor.navigateToPreviousDiagnostic() != null));
    }

    private static boolean hasDiagnostics(CodeEditor editor) {
        return !editor.isPasswordMode() && !editor.isDiagnosticsStale() && editor.getDiagnostics().size > 0;
    }
}
