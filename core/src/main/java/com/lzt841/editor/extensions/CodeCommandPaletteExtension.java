package com.lzt841.editor.extensions;

import com.badlogic.gdx.Input;
import com.lzt841.editor.CodeEditorCommand;
import com.lzt841.editor.CodeKeyStroke;
import com.lzt841.editor.commands.CodeCommandPalette;
import com.lzt841.editor.commands.CodeCommandPalette.CodeCommandPaletteStyle;

/** Owns a command palette and its Ctrl/Command+Shift+P registration for one editor. */
public final class CodeCommandPaletteExtension implements CodeEditorExtension {
    public static final String ID = "editor.commandPalette";
    public static final String SHOW = "editor.commands.showPalette";
    private final CodeCommandPaletteStyle style;
    private CodeCommandPalette palette;

    public CodeCommandPaletteExtension(CodeCommandPaletteStyle style) {
        if (style == null) throw new IllegalArgumentException("palette style is required");
        this.style = style;
    }

    @Override public String getId() { return ID; }

    @Override public void install(CodeExtensionContext context) {
        if (palette != null && !palette.isDisposed()) throw new IllegalStateException("extension already owns a palette");
        CodeCommandPalette created = context.own(new CodeCommandPalette(context.getEditor(), style));
        palette = created;
        context.registerCommand(new CodeEditorCommand(SHOW, "Show Command Palette", "Commands", false,
            editor -> !editor.isPasswordMode() && editor.getStage() != null && !created.isOpen(),
            editor -> created.open()), CodeKeyStroke.ctrlShift(Input.Keys.P));
    }

    /** Null before first installation; a disposed palette after unload or failed installation. */
    public CodeCommandPalette getPalette() { return palette; }
}
