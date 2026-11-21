package com.lzt841.editor.extensions;

import com.badlogic.gdx.Input;
import com.lzt841.editor.CodeEditorCommand;
import com.lzt841.editor.CodeKeyStroke;
import com.lzt841.editor.codeactions.CodeActionController;
import com.lzt841.editor.codeactions.CodeActionProvider;

/** Optional code action commands. Unloading cancels requests; the provider stays host-owned. */
public final class CodeActionExtension implements CodeEditorExtension {
    public static final String ID = "editor.codeActions";
    public static final String REQUEST_ACTIONS = "editor.requestCodeActions";
    public static final String APPLY_PREFERRED = "editor.applyPreferredCodeAction";
    private final CodeActionProvider provider;
    private CodeActionController controller;

    public CodeActionExtension(CodeActionProvider provider) {
        if (provider == null) throw new IllegalArgumentException("provider is required");
        this.provider = provider;
    }

    @Override public String getId() { return ID; }

    @Override public void install(CodeExtensionContext context) {
        if (controller != null && !controller.isDisposed()) {
            throw new IllegalStateException("code action extension is already active");
        }
        CodeActionController created = context.own(new CodeActionController(context.getEditor(), provider));
        controller = created;
        context.registerCommand(new CodeEditorCommand(REQUEST_ACTIONS, "Show Code Actions", "Code Actions", true,
            editor -> !created.isDisposed() && !editor.isPasswordMode() && !editor.isCompoundEditInProgress(),
            editor -> created.requestActions(), true), CodeKeyStroke.alt(Input.Keys.ENTER));
        context.registerCommand(new CodeEditorCommand(APPLY_PREFERRED, "Apply Preferred Code Action", "Code Actions", true,
            editor -> created.canApplyPreferred(), editor -> created.applyPreferred(), true));
    }

    /** Null before installation; disposed after the extension is unloaded or installation rolls back. */
    public CodeActionController getController() { return controller; }
}
