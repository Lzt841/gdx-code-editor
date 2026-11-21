package com.lzt841.editor.extensions;

import com.badlogic.gdx.Input;
import com.lzt841.editor.CodeEditorCommand;
import com.lzt841.editor.CodeKeyStroke;
import com.lzt841.editor.formatting.CodeFormattingController;
import com.lzt841.editor.formatting.CodeFormattingProvider;

/** Optional formatting commands. Unloading cancels pending requests; the provider stays host-owned. */
public final class CodeFormattingExtension implements CodeEditorExtension {
    public static final String ID = "editor.formatting";
    public static final String FORMAT_DOCUMENT = "editor.formatDocument";
    public static final String FORMAT_SELECTION = "editor.formatSelection";
    private final CodeFormattingProvider provider;
    private CodeFormattingController controller;

    public CodeFormattingExtension(CodeFormattingProvider provider) {
        if (provider == null) throw new IllegalArgumentException("provider is required");
        this.provider = provider;
    }

    @Override public String getId() { return ID; }

    @Override public void install(CodeExtensionContext context) {
        if (controller != null && !controller.isDisposed()) {
            throw new IllegalStateException("formatting extension is already active");
        }
        CodeFormattingController created = context.own(new CodeFormattingController(context.getEditor(), provider));
        controller = created;
        context.registerCommand(new CodeEditorCommand(FORMAT_DOCUMENT, "Format Document", "Formatting", true,
            editor -> !created.isDisposed() && !editor.isPasswordMode() && !editor.isCompoundEditInProgress(),
            editor -> created.formatDocument(), true), CodeKeyStroke.ctrlAlt(Input.Keys.L));
        context.registerCommand(new CodeEditorCommand(FORMAT_SELECTION, "Format Selection", "Formatting", true,
            editor -> !created.isDisposed() && !editor.isPasswordMode() && !editor.isCompoundEditInProgress()
                && editor.hasSelection(), editor -> created.formatSelection(), true));
    }

    /** Null before installation; disposed after the extension is unloaded or installation rolls back. */
    public CodeFormattingController getController() { return controller; }
}
