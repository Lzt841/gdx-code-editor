package com.lzt841.editor.extensions;

import com.badlogic.gdx.Input;
import com.lzt841.editor.CodeEditorCommand;
import com.lzt841.editor.CodeKeyStroke;
import com.lzt841.editor.navigation.CodeGoToLineDialog;
import com.lzt841.editor.navigation.CodeGoToLineDialog.CodeGoToLineDialogStyle;

/** Owns a go-to-line dialog and its Ctrl/Command+G command for one editor. */
public final class CodeGoToLineExtension implements CodeEditorExtension {
    public static final String ID = "editor.goToLine";
    public static final String SHOW = "editor.navigation.goToLine";
    private final CodeGoToLineDialogStyle style;
    private CodeGoToLineDialog dialog;

    public CodeGoToLineExtension(CodeGoToLineDialogStyle style) {
        if (style == null) throw new IllegalArgumentException("go-to-line style is required");
        this.style = style;
    }

    @Override public String getId() { return ID; }

    @Override public void install(CodeExtensionContext context) {
        if (dialog != null && !dialog.isDisposed()) throw new IllegalStateException("extension already owns a dialog");
        CodeGoToLineDialog created = context.own(new CodeGoToLineDialog(context.getEditor(), style));
        dialog = created;
        context.registerCommand(new CodeEditorCommand(SHOW, "Go to Line / Column", "Navigation", false,
            editor -> !editor.isPasswordMode() && editor.getStage() != null && !created.isOpen(),
            editor -> created.open()), CodeKeyStroke.ctrl(Input.Keys.G));
    }

    /** Null before installation; disposed after unload or failed installation. */
    public CodeGoToLineDialog getDialog() { return dialog; }
}
