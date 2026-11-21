package com.lzt841.editor.completion;

/**
 * One occurrence of a tab stop inside a parsed snippet.
 *
 * <p>{@link #index} is the LSP tab-stop number: {@code 1}, {@code 2}, \ldots visit first, {@code 0}
 * last. Several occurrences can share an index; those are <em>mirrors</em>, and typing in one copies
 * to the others. {@link #start} / {@link #end} are offsets into
 * {@link CodeSnippetTemplate#insertText}, {@code end} exclusive. An empty stop (bare {@code $1}) has
 * {@code start == end}.
 *
 * <p>Offsets are relative to the snippet body, not the document. The session rebases them onto the
 * document at insert time.
 */
public class CodeSnippetPlaceholder {
    public final int index;
    /** Inclusive offset into the parsed insert text. */
    public final int start;
    /** Exclusive offset into the parsed insert text. */
    public final int end;
    /** Enclosing placeholder's array index, or -1 for a top-level/manual stop. */
    final int parent;

    public CodeSnippetPlaceholder(int index, int start, int end) {
        this(index, start, end, -1);
    }

    CodeSnippetPlaceholder(int index, int start, int end, int parent) {
        this.index = index;
        this.start = start;
        this.end = end;
        this.parent = parent;
    }

    public int length() {
        return end - start;
    }

    public boolean isEmpty() {
        return end <= start;
    }

    @Override
    public String toString() {
        return "CodeSnippetPlaceholder{$" + index + " " + start + ".." + end + "}";
    }
}
