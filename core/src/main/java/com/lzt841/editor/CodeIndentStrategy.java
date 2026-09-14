package com.lzt841.editor;

/**
 * Controls how the editor indents: tabs or spaces, how wide, and what happens on Enter.
 *
 * <p>Replaces the hard-coded four spaces the editor used to insert. Construct one of the factory
 * methods and hand it to {@link CodeEditor#setIndentStrategy(CodeIndentStrategy)}:
 *
 * <pre>{@code
 * editor.setIndentStrategy(CodeIndentStrategy.spaces(2));   // 2-space, e.g. YAML
 * editor.setIndentStrategy(CodeIndentStrategy.tabs(4));     // real tabs, displayed 4 wide
 * }</pre>
 *
 * <p>Subclass to change the smart-indent rules; {@link #indentAfter(CharSequence, int)} decides how
 * much a new line is indented, and {@link #shouldDedentBefore(CharSequence, int, char)} decides
 * whether typing a character pulls the line back out.
 */
public class CodeIndentStrategy {
    /** Columns one indent level occupies. Also the rendered width of a tab. */
    public final int indentWidth;
    /** Whether one indent level is a literal tab character rather than {@link #indentWidth} spaces. */
    public final boolean useTabs;

    protected CodeIndentStrategy(int indentWidth, boolean useTabs) {
        this.indentWidth = Math.max(1, indentWidth);
        this.useTabs = useTabs;
    }

    /** Indent with {@code width} spaces per level. */
    public static CodeIndentStrategy spaces(int width) {
        return new CodeIndentStrategy(width, false);
    }

    /** Indent with tab characters, rendered {@code width} columns wide. */
    public static CodeIndentStrategy tabs(int width) {
        return new CodeIndentStrategy(width, true);
    }

    /** The editor's default: four spaces. */
    public static CodeIndentStrategy defaultStrategy() {
        return spaces(4);
    }

    /** The text inserted for one indent level. */
    public String oneIndent() {
        if (useTabs) {
            return "\t";
        }
        StringBuilder builder = new StringBuilder(indentWidth);
        for (int i = 0; i < indentWidth; i++) {
            builder.append(' ');
        }
        return builder.toString();
    }

    /**
     * Visual column of {@code text[0, limit)}, counting a tab as advancing to the next multiple of
     * {@link #indentWidth}. Mixed tabs and spaces therefore measure correctly.
     */
    public int visualColumn(CharSequence text, int limit) {
        int column = 0;
        int stop = Math.min(limit, text.length());
        for (int i = 0; i < stop; i++) {
            if (text.charAt(i) == '\t') {
                column += indentWidth - (column % indentWidth);
            } else {
                column++;
            }
        }
        return column;
    }

    /** Number of leading whitespace characters on a line. */
    public int leadingWhitespaceLength(CharSequence text) {
        int count = 0;
        while (count < text.length()) {
            char c = text.charAt(count);
            if (c != ' ' && c != '\t') {
                break;
            }
            count++;
        }
        return count;
    }

    /** Visual indent column of a line, i.e. where its first non-whitespace character sits. */
    public int leadingIndentColumn(CharSequence text) {
        return visualColumn(text, leadingWhitespaceLength(text));
    }

    /** Whitespace producing a visual indent of {@code column}, honouring {@link #useTabs}. */
    public String indentForColumn(int column) {
        int safe = Math.max(0, column);
        StringBuilder builder = new StringBuilder();
        if (useTabs) {
            int tabs = safe / indentWidth;
            for (int i = 0; i < tabs; i++) {
                builder.append('\t');
            }
            for (int i = 0; i < safe % indentWidth; i++) {
                builder.append(' ');
            }
        } else {
            for (int i = 0; i < safe; i++) {
                builder.append(' ');
            }
        }
        return builder.toString();
    }

    /**
     * Indent column for the line created by pressing Enter at {@code caretColumn}.
     *
     * <p>The default keeps the current line's indent and adds one level after an opening brace. It
     * never returns more than the caret's own column, so splitting a line mid-indent does not gain
     * indentation.
     */
    public int indentAfter(CharSequence line, int caretColumn) {
        int limit = Math.min(caretColumn, line.length());
        int indent = Math.min(leadingIndentColumn(line), visualColumn(line, limit));
        if (endsWithOpenBrace(line, limit)) {
            indent += indentWidth;
        }
        return indent;
    }

    /**
     * Whether typing {@code character} at {@code caretColumn} should remove one indent level, which is
     * how {@code }} lines itself up with its opener. The default fires for a closing brace on a line
     * containing nothing but whitespace.
     */
    public boolean shouldDedentBefore(CharSequence line, int caretColumn, char character) {
        if (character != '}') {
            return false;
        }
        int limit = Math.min(caretColumn, line.length());
        if (limit <= 0) {
            return false;
        }
        for (int i = 0; i < limit; i++) {
            char c = line.charAt(i);
            if (c != ' ' && c != '\t') {
                return false;
            }
        }
        return visualColumn(line, limit) >= indentWidth;
    }

    /** Whether the text before {@code limit}, ignoring trailing whitespace, ends with '{'. */
    protected static boolean endsWithOpenBrace(CharSequence text, int limit) {
        int index = Math.min(limit, text.length()) - 1;
        while (index >= 0 && Character.isWhitespace(text.charAt(index))) {
            index--;
        }
        return index >= 0 && text.charAt(index) == '{';
    }

    @Override
    public String toString() {
        return "CodeIndentStrategy{" + (useTabs ? "tabs" : "spaces") + " width=" + indentWidth + "}";
    }
}
