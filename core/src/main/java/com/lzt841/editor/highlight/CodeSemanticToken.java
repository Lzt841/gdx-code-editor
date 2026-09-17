package com.lzt841.editor.highlight;

import com.badlogic.gdx.graphics.Color;

/**
 * One semantically classified stretch of a single line, drawn on top of the lexical highlighting.
 *
 * <p>The lexical highlighters in this package are per-line state machines. That is what makes them
 * fast enough to run on the visible rows every frame, and it is also why they cannot tell a local
 * variable from a field from a parameter: answering that needs scope information the line does not
 * contain. Semantic tokens are how something that <em>does</em> have that information — a compiler, an
 * LSP server, a resolver of your own — puts the answer back on the screen.
 *
 * <p><b>Single line by construction.</b> There is no {@code endLine}. A semantic token classifies an
 * identifier or a literal, and neither spans a line break in any language this editor targets. The
 * restriction is what lets the editor store tokens in a per-line array and find the ones it needs for
 * a row with an index rather than a scan. A producer with a genuinely multi-line construct should emit
 * one token per line.
 *
 * <p><b>Columns are document columns</b> — indices into the line's text, {@code startColumn}
 * inclusive, {@code endColumn} exclusive, exactly like {@link CodeHighlightSpan}. They are clamped to
 * the line when drawn, so a token left over from a since-shortened line cannot paint past the end.
 *
 * <p><b>Colour.</b> Leave {@link #color} null and the editor resolves one from {@link #type} against
 * the style; set it and it wins. Null is the better default: it keeps the producer out of the theming
 * business, so switching themes recolours semantic tokens along with everything else. Pass a colour
 * when the meaning is not in the enum at all — an unused symbol dimmed, a test-only API tinted.
 *
 * <p>Instances are immutable and carry no document version of their own; the version applies to a
 * whole pushed batch, at {@code CodeEditor.setSemanticTokens}.
 */
public class CodeSemanticToken {
    /** No modifiers. */
    public static final int NO_MODIFIERS = 0;
    /** This occurrence is the declaration of the symbol. */
    public static final int MODIFIER_DECLARATION = 1 << 0;
    /** This occurrence is the definition of the symbol. */
    public static final int MODIFIER_DEFINITION = 1 << 1;
    /** The symbol cannot be reassigned: {@code final}, {@code const}, {@code val}. */
    public static final int MODIFIER_READONLY = 1 << 2;
    public static final int MODIFIER_STATIC = 1 << 3;
    public static final int MODIFIER_DEPRECATED = 1 << 4;
    public static final int MODIFIER_ABSTRACT = 1 << 5;
    /** The symbol is generated rather than written by hand. */
    public static final int MODIFIER_GENERATED = 1 << 6;
    /** The symbol comes from a dependency, not from this project. */
    public static final int MODIFIER_LIBRARY = 1 << 7;

    /**
     * Line the producer put this token on.
     *
     * <p>Read this only on a token you built. On one handed back by
     * {@code CodeEditor.getSemanticTokensAtLine}, the line you asked for is authoritative and this field
     * may be behind it: when an edit changes the line count the editor moves each surviving token's
     * <em>slot</em> and leaves the field alone, because rewriting it would allocate a fresh token for
     * every one below the edit — hundreds of thousands of them in a large file, on every keystroke.
     * Nothing in the editor reads this field after the push, so the drift costs nothing internally.
     */
    public final int line;
    /** Inclusive index into the line's text. */
    public final int startColumn;
    /** Exclusive index into the line's text. */
    public final int endColumn;
    public final CodeSemanticTokenType type;
    /** Explicit colour, or null to resolve one from {@link #type} against the editor's style. */
    public final Color color;
    /**
     * Bitmask of the {@code MODIFIER_*} constants.
     *
     * <p>The editor stores this and hands it back through
     * {@code CodeEditor.getSemanticTokensAtLine}, but never colours by it. Deciding that
     * {@link #MODIFIER_DEPRECATED} means struck through or that {@link #MODIFIER_READONLY} means
     * italic is a theming choice, and this library has one font per editor to render with. Callers
     * that want the distinction can read the mask and set {@link #color} accordingly.
     */
    public final int modifiers;
    /** Free slot for the producer; the editor never reads it. */
    public final Object userData;
    /** How this occurrence is drawn, or null for an undecorated run. */
    public final CodeTextStyle style;

    public CodeSemanticToken(int line, int startColumn, int endColumn, CodeSemanticTokenType type) {
        this(line, startColumn, endColumn, type, null, NO_MODIFIERS, null, null);
    }

    public CodeSemanticToken(
        int line,
        int startColumn,
        int endColumn,
        CodeSemanticTokenType type,
        Color color
    ) {
        this(line, startColumn, endColumn, type, color, NO_MODIFIERS, null, null);
    }

    public CodeSemanticToken(
        int line,
        int startColumn,
        int endColumn,
        CodeSemanticTokenType type,
        Color color,
        int modifiers,
        Object userData
    ) {
        this(line, startColumn, endColumn, type, color, modifiers, userData, null);
    }

    /**
     * With a {@link CodeTextStyle}. The editor never derives a style from {@link #modifiers} — mapping
     * {@link #MODIFIER_DEPRECATED} to a strike-through is a theming choice the producer makes, the same
     * way it decides the colour — so this is the only route to a decorated semantic token.
     */
    public CodeSemanticToken(
        int line,
        int startColumn,
        int endColumn,
        CodeSemanticTokenType type,
        Color color,
        int modifiers,
        Object userData,
        CodeTextStyle style
    ) {
        this.line = line;
        this.startColumn = startColumn;
        this.endColumn = endColumn;
        this.type = type;
        this.color = color;
        this.modifiers = modifiers;
        this.userData = userData;
        this.style = style;
    }

    /** This token on a different line, for callers shifting a batch after an edit of their own. */
    public CodeSemanticToken movedTo(int newLine) {
        return new CodeSemanticToken(newLine, startColumn, endColumn, type, color, modifiers, userData, style);
    }

    /** Whether every bit in {@code modifierMask} is set. */
    public boolean hasModifiers(int modifierMask) {
        return (modifiers & modifierMask) == modifierMask;
    }

    /** Whether {@code column} falls inside this token. */
    public boolean covers(int column) {
        return column >= startColumn && column < endColumn;
    }

    /** Whether the token describes a non-empty, non-inverted range. */
    public boolean isValid() {
        return line >= 0 && startColumn >= 0 && endColumn > startColumn;
    }

    @Override
    public String toString() {
        return "CodeSemanticToken{" + type + " " + line + ":" + startColumn + "-" + endColumn
            + (color != null ? " coloured" : "") + (modifiers != 0 ? " mods=" + modifiers : "") + "}";
    }
}
