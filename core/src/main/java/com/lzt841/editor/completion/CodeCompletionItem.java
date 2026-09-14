package com.lzt841.editor.completion;

/**
 * One completion candidate.
 *
 * <p>{@link #label} is what the list shows, {@link #insertText} is what replaces the range being
 * completed. They differ for entries like {@code method(…) : int} whose insert text is
 * {@code method(}.
 *
 * <p>Build with {@link #of(String)} for the simple case, or the constructor when you want a detail
 * column, documentation for the side panel, a kind icon, or a custom sort weight.
 */
public class CodeCompletionItem {
    /** Text shown in the list. */
    public final String label;
    /** Text inserted when the item is accepted. */
    public final String insertText;
    /** Short right-aligned annotation, typically a type or a signature. */
    public final String detail;
    /** Longer text for a documentation panel or tooltip. */
    public final String documentation;
    public final CodeCompletionItemKind kind;
    /**
     * Higher sorts earlier among items that match the prefix equally well. Use it to float
     * locals above globals, or recently used entries to the top.
     */
    public final int priority;
    /**
     * Text matched against the typed prefix instead of {@link #label}, for entries whose label
     * carries decoration. Null means match on the label.
     */
    public final String filterText;
    /** Free slot for the provider to attach its own object; the editor never reads it. */
    public final Object userData;
    /**
     * Whether {@link #insertText} is literal text or a snippet body with placeholders.
     *
     * <p>Independent of {@link #kind}, as in LSP: the kind picks the icon, this decides how the text is
     * inserted. A provider that sets {@code kind = SNIPPET} but leaves this at
     * {@link CodeCompletionInsertFormat#PLAIN_TEXT} gets the snippet icon and literal insertion, which
     * is a legitimate combination for an entry whose body happens to contain a {@code $}.
     */
    public final CodeCompletionInsertFormat insertFormat;

    public CodeCompletionItem(
        String label,
        String insertText,
        String detail,
        String documentation,
        CodeCompletionItemKind kind,
        int priority,
        String filterText,
        Object userData
    ) {
        this(label, insertText, detail, documentation, kind, priority, filterText, userData,
            CodeCompletionInsertFormat.PLAIN_TEXT);
    }

    public CodeCompletionItem(
        String label,
        String insertText,
        String detail,
        String documentation,
        CodeCompletionItemKind kind,
        int priority,
        String filterText,
        Object userData,
        CodeCompletionInsertFormat insertFormat
    ) {
        this.label = label == null ? "" : label;
        this.insertText = insertText == null ? this.label : insertText;
        this.detail = detail == null ? "" : detail;
        this.documentation = documentation == null ? "" : documentation;
        this.kind = kind == null ? CodeCompletionItemKind.TEXT : kind;
        this.priority = priority;
        this.filterText = filterText;
        this.userData = userData;
        this.insertFormat = insertFormat == null ? CodeCompletionInsertFormat.PLAIN_TEXT : insertFormat;
    }

    /** A candidate whose label, insert text and filter text are all the same. */
    public static CodeCompletionItem of(String label) {
        return new CodeCompletionItem(label, label, "", "", CodeCompletionItemKind.TEXT, 0, null, null);
    }

    public static CodeCompletionItem of(String label, CodeCompletionItemKind kind) {
        return new CodeCompletionItem(label, label, "", "", kind, 0, null, null);
    }

    public static CodeCompletionItem of(String label, CodeCompletionItemKind kind, String detail) {
        return new CodeCompletionItem(label, label, detail, "", kind, 0, null, null);
    }

    /**
     * A snippet candidate: {@code body} is parsed for placeholders and drives a
     * {@link CodeSnippetSession} when accepted.
     *
     * <p>For example {@code snippet("for", "for (int ${1:i} = 0; $1 < ${2:n}; $1++) {\n\t$0\n}")}.
     */
    public static CodeCompletionItem snippet(String label, String body) {
        return snippet(label, body, "");
    }

    public static CodeCompletionItem snippet(String label, String body, String detail) {
        return new CodeCompletionItem(label, body, detail, "", CodeCompletionItemKind.SNIPPET, 0, null,
            null, CodeCompletionInsertFormat.SNIPPET);
    }

    /** Whether accepting this item should start a snippet session. */
    public boolean isSnippet() {
        return insertFormat == CodeCompletionInsertFormat.SNIPPET;
    }

    /** The text the typed prefix is matched against. */
    public String matchText() {
        return filterText != null ? filterText : label;
    }

    @Override
    public String toString() {
        return "CodeCompletionItem{" + label + "}";
    }
}
