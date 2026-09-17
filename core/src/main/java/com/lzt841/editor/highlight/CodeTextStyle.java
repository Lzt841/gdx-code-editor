package com.lzt841.editor.highlight;

import com.badlogic.gdx.graphics.Color;

/**
 * How a span is drawn, beyond its colour: underlines, a background, a strike-through, a gradient, and
 * the two fake type styles this editor can render with a single {@link com.badlogic.gdx.graphics.g2d.BitmapFont}.
 *
 * <p><b>Why a bag of flags rather than a type per style.</b> The whole point is that decorations
 * <em>compose</em>: a deprecated symbol wants a strike-through <em>and</em> the error squiggle a linter
 * put under it, over a background another layer tinted. A single enum could not say that, and the
 * flatten pass that resolves overlapping spans already exists to pick one colour per segment — it
 * ORs these flags across every span covering a segment instead of choosing one.
 *
 * <p><b>Immutable, and deliberately cheap.</b> A large semantic token batch is hundreds of thousands
 * of these, so it is a final int plus up to three colour references and no allocation beyond itself.
 * A span with no decoration carries null rather than a NONE instance — {@link #PLAIN} is the non-null
 * stand-in the editor draws with when a span did not name a style — and the {@code CodeHighlightSpan}
 * constructors that predate styles mean exactly that.
 *
 * <p><b>Colours are optional, and each decoration kind has its own.</b> Leave {@link #underlineColor}
 * null and a plain underline is drawn in the span's text colour; leave {@link #backgroundColor} null
 * and the {@link #BACKGROUND} flag draws nothing rather than guessing one; leave
 * {@link #gradientEndColor} null and {@link #GRADIENT} falls back to a flat fill. The four
 * kind-specific colours — {@link #strikethroughColor}, {@link #wavyUnderlineColor},
 * {@link #dashedUnderlineColor}, {@link #dottedUnderlineColor} — fall back to {@link #underlineColor}
 * before the text colour, so one shared colour still styles every line a span draws while a specific
 * one overrides only its own decoration. Nothing here is a required pair with a flag — a flag without
 * its colour degrades gracefully instead of throwing.
 *
 * <p>The editor does not invent styles from token modifiers. Mapping {@code DEPRECATED} to a
 * strike-through is a theming choice the producer makes when it builds the token, exactly as it
 * already decides a token's colour.
 */
public final class CodeTextStyle {
    /** No decoration at all. */
    public static final int NONE = 0;
    /** Fake bold: the run is drawn a second time, offset right by the style's {@code fakeBoldOffset}. */
    public static final int BOLD = 1 << 0;
    /** Fake italic: the run is drawn under a sheared projection matrix. */
    public static final int ITALIC = 1 << 1;
    /** A single line under the run. */
    public static final int UNDERLINE = 1 << 2;
    /** A zig-zag line under the run, drawn with the same algorithm as a diagnostic squiggle. */
    public static final int UNDERLINE_WAVY = 1 << 3;
    /** Two parallel lines under the run. */
    public static final int UNDERLINE_DOUBLE = 1 << 4;
    /** A broken line under the run, alternating dashes and gaps. */
    public static final int UNDERLINE_DASHED = 1 << 8;
    /** A broken line under the run made of round dots. */
    public static final int UNDERLINE_DOTTED = 1 << 9;
    /** A line through the middle of the run. */
    public static final int STRIKETHROUGH = 1 << 5;
    /** Fill the run's row extent with {@link #backgroundColor}. Draws nothing when that is null. */
    public static final int BACKGROUND = 1 << 6;
    /** Interpolate from the span's text colour to {@link #gradientEndColor} across the run. */
    public static final int GRADIENT = 1 << 7;

    /** Shared, immutable, allocation-free instance for the common case. */
    public static final CodeTextStyle PLAIN = new CodeTextStyle(NONE, null, null, null);

    public final int flags;
    /** Colour for the plain and double underlines, or null for the run's text colour. */
    public final Color underlineColor;
    /** Fill colour for the {@link #BACKGROUND} flag. */
    public final Color backgroundColor;
    /** Second endpoint of a {@link #GRADIENT}; the span's own colour is the first. */
    public final Color gradientEndColor;
    /** Colour for the {@link #STRIKETHROUGH} line; null falls back to {@link #underlineColor}. */
    public final Color strikethroughColor;
    /** Colour for the {@link #UNDERLINE_WAVY} line; null falls back to {@link #underlineColor}. */
    public final Color wavyUnderlineColor;
    /** Colour for the {@link #UNDERLINE_DASHED} line; null falls back to {@link #underlineColor}. */
    public final Color dashedUnderlineColor;
    /** Colour for the {@link #UNDERLINE_DOTTED} line; null falls back to {@link #underlineColor}. */
    public final Color dottedUnderlineColor;

    public CodeTextStyle(int flags) {
        this(flags, null, null, null);
    }

    public CodeTextStyle(int flags, Color underlineColor) {
        this(flags, underlineColor, null, null);
    }

    public CodeTextStyle(int flags, Color underlineColor, Color backgroundColor, Color gradientEndColor) {
        this(flags, underlineColor, backgroundColor, gradientEndColor, null, null, null, null);
    }

    /**
     * Every colour at once. The flatten pass builds one composed style out of every span covering a
     * segment, so it is the caller here; a producer that only decorates wants one of the shorter
     * constructors plus a {@code with} method instead of counting nulls.
     */
    public CodeTextStyle(
        int flags,
        Color underlineColor,
        Color backgroundColor,
        Color gradientEndColor,
        Color strikethroughColor,
        Color wavyUnderlineColor,
        Color dashedUnderlineColor,
        Color dottedUnderlineColor
    ) {
        this.flags = flags;
        this.underlineColor = underlineColor;
        this.backgroundColor = backgroundColor;
        this.gradientEndColor = gradientEndColor;
        this.strikethroughColor = strikethroughColor;
        this.wavyUnderlineColor = wavyUnderlineColor;
        this.dashedUnderlineColor = dashedUnderlineColor;
        this.dottedUnderlineColor = dottedUnderlineColor;
    }

    /** This style with a colour only its strike-through draws in, keeping every other colour. */
    public CodeTextStyle withStrikethroughColor(Color color) {
        return new CodeTextStyle(
            flags, underlineColor, backgroundColor, gradientEndColor,
            color, wavyUnderlineColor, dashedUnderlineColor, dottedUnderlineColor);
    }

    /** This style with a colour only its wavy underline draws in, keeping every other colour. */
    public CodeTextStyle withWavyUnderlineColor(Color color) {
        return new CodeTextStyle(
            flags, underlineColor, backgroundColor, gradientEndColor,
            strikethroughColor, color, dashedUnderlineColor, dottedUnderlineColor);
    }

    /** This style with a colour only its dashed underline draws in, keeping every other colour. */
    public CodeTextStyle withDashedUnderlineColor(Color color) {
        return new CodeTextStyle(
            flags, underlineColor, backgroundColor, gradientEndColor,
            strikethroughColor, wavyUnderlineColor, color, dottedUnderlineColor);
    }

    /** This style with a colour only its dotted underline draws in, keeping every other colour. */
    public CodeTextStyle withDottedUnderlineColor(Color color) {
        return new CodeTextStyle(
            flags, underlineColor, backgroundColor, gradientEndColor,
            strikethroughColor, wavyUnderlineColor, dashedUnderlineColor, color);
    }

    /** Whether every bit in {@code mask} is set. */
    public boolean hasFlags(int mask) {
        return (flags & mask) == mask;
    }

    /** Whether any bit in {@code mask} is set. */
    public boolean hasAnyFlag(int mask) {
        return (flags & mask) != 0;
    }

    /**
     * Combines two styles a producer holds: flags OR together, and each colour is the first style that
     * actually supplies one, so a lower-priority span's background survives a higher-priority span that
     * only underlines.
     *
     * <p>This is a convenience for building one style from two, not a reimplementation of the editor's
     * flatten pass, and the two disagree on ties. The flatten pass races each colour independently by
     * span priority and awards a tie to the later span, while this method awards it to {@code this}; a
     * producer pre-merging styles to predict what will be drawn should give the higher-priority style
     * the colours it wants to win rather than relying on the tie either way.
     *
     * <p>Every colour keeps its own first-non-null race, so a lower-priority span's wavy colour survives
     * a higher-priority span that only supplies a strike-through colour, exactly as the flatten pass
     * would keep them apart.
     *
     * <p>{@code other} is the lower priority of the two: it fills the gaps this one leaves.
     */
    public CodeTextStyle merge(CodeTextStyle other) {
        if (other == null) {
            return this;
        }
        if (flags == 0 && other.flags == 0) {
            return PLAIN;
        }
        return new CodeTextStyle(
            flags | other.flags,
            underlineColor != null ? underlineColor : other.underlineColor,
            backgroundColor != null ? backgroundColor : other.backgroundColor,
            gradientEndColor != null ? gradientEndColor : other.gradientEndColor,
            strikethroughColor != null ? strikethroughColor : other.strikethroughColor,
            wavyUnderlineColor != null ? wavyUnderlineColor : other.wavyUnderlineColor,
            dashedUnderlineColor != null ? dashedUnderlineColor : other.dashedUnderlineColor,
            dottedUnderlineColor != null ? dottedUnderlineColor : other.dottedUnderlineColor
        );
    }

    /** This style with {@code extra} flags ORed in, keeping the colours. */
    public CodeTextStyle withFlags(int extra) {
        if (extra == 0) {
            return this;
        }
        return new CodeTextStyle(
            flags | extra, underlineColor, backgroundColor, gradientEndColor,
            strikethroughColor, wavyUnderlineColor, dashedUnderlineColor, dottedUnderlineColor);
    }

    @Override
    public String toString() {
        if (flags == 0) {
            return "CodeTextStyle{none}";
        }
        StringBuilder builder = new StringBuilder("CodeTextStyle{");
        int named = 0;
        named = appendFlag(builder, named, hasFlags(BOLD), "bold");
        named = appendFlag(builder, named, hasFlags(ITALIC), "italic");
        named = appendFlag(builder, named, hasFlags(UNDERLINE), "underline");
        named = appendFlag(builder, named, hasFlags(UNDERLINE_WAVY), "wavy");
        named = appendFlag(builder, named, hasFlags(UNDERLINE_DOUBLE), "double");
        named = appendFlag(builder, named, hasFlags(UNDERLINE_DASHED), "dashed");
        named = appendFlag(builder, named, hasFlags(UNDERLINE_DOTTED), "dotted");
        named = appendFlag(builder, named, hasFlags(STRIKETHROUGH), "strike");
        named = appendFlag(builder, named, hasFlags(BACKGROUND), "bg");
        appendFlag(builder, named, hasFlags(GRADIENT), "gradient");
        return builder.append("}").toString();
    }

    /** Appends {@code name} and the separator before it, so no flag leaves a trailing space. */
    private static int appendFlag(StringBuilder builder, int named, boolean set, String name) {
        if (!set) {
            return named;
        }
        if (named > 0) {
            builder.append(' ');
        }
        builder.append(name);
        return named + 1;
    }
}
