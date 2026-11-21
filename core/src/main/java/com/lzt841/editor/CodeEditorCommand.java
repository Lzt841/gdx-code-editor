package com.lzt841.editor;

/** A host or extension command, independent of the built-in {@link CodeEditorAction} enum. */
public final class CodeEditorCommand {
    public interface Handler {
        /** Whether the command handled the request; false also covers an effective no-op. */
        boolean execute(CodeEditor editor);
    }

    public interface Condition {
        /** Evaluated at execution/query time. Keep this cheap and free of side effects. */
        boolean isEnabled(CodeEditor editor);
    }

    public final String id;
    public final String label;
    public final String category;
    public final boolean requiresEditable;
    /** Consumes a bound key even when unavailable or a no-op. Direct execute still returns false. */
    public final boolean consumeShortcutOnNoOp;
    private final Condition condition;
    private final Handler handler;

    public CodeEditorCommand(String id, String label, Handler handler) {
        this(id, label, "", false, null, handler);
    }

    public CodeEditorCommand(String id, String label, String category, boolean requiresEditable,
                             Condition condition, Handler handler) {
        this(id, label, category, requiresEditable, condition, handler, false);
    }

    public CodeEditorCommand(String id, String label, String category, boolean requiresEditable,
                             Condition condition, Handler handler, boolean consumeShortcutOnNoOp) {
        if (id == null || id.trim().isEmpty() || !id.equals(id.trim())) {
            throw new IllegalArgumentException("command id must be non-blank without surrounding whitespace");
        }
        if (label == null || label.trim().isEmpty() || handler == null) {
            throw new IllegalArgumentException("command label and handler are required");
        }
        this.id = id;
        this.label = label;
        this.category = category == null ? "" : category;
        this.requiresEditable = requiresEditable;
        this.consumeShortcutOnNoOp = consumeShortcutOnNoOp;
        this.condition = condition;
        this.handler = handler;
    }

    /** Disabled editors cannot execute commands; editing commands also require a writable editor. */
    public boolean isEnabled(CodeEditor editor) {
        return editor != null && !editor.isDisabled() && (!requiresEditable || !editor.isReadOnly())
            && (condition == null || condition.isEnabled(editor));
    }

    boolean execute(CodeEditor editor) {
        return handler.execute(editor);
    }
}
