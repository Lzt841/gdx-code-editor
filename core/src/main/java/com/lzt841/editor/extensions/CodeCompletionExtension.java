package com.lzt841.editor.extensions;

import com.lzt841.editor.CodeEditorCommand;
import com.lzt841.editor.completion.CodeCompletionController;

/** Owns a controller for the shared completion sources and exposes it to the command palette. */
public final class CodeCompletionExtension implements CodeEditorExtension {
    public static final String ID = "editor.completion";
    public static final String TRIGGER = "editor.completion.trigger";
    private CodeCompletionController controller;
    private boolean active;

    @Override public String getId() { return ID; }

    @Override public void install(CodeExtensionContext context) {
        if (active) throw new IllegalStateException("completion extension is already active");
        CodeCompletionController created = new CodeCompletionController(context.getEditor());
        controller = created;
        active = true;
        context.onDispose(() -> { active = false; created.uninstall(); });
        created.install();
        context.registerCommand(new CodeEditorCommand(TRIGGER, "Show Completions", "Completion", true,
            editor -> !editor.isPasswordMode() && editor.getStage() != null,
            editor -> {
                if (editor.getStage() == null || !editor.getStage().setKeyboardFocus(editor)
                    || context.isDisposed() || controller != created) return false;
                created.triggerManually();
                return true;
            }));
    }

    /** Null before installation; an uninstalled controller after the scope is released. */
    public CodeCompletionController getController() { return controller; }
}
