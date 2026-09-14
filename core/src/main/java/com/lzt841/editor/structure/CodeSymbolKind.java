package com.lzt841.editor.structure;

/**
 * What a {@link CodeSymbol} is, so an outline can pick an icon and a go-to-symbol dialog can group or
 * filter results.
 *
 * <p>The set mirrors the Language Server Protocol's document symbol kinds, which is what most language
 * tooling already speaks. {@link #OTHER} is the escape hatch for anything a provider cannot classify;
 * it is never used by the built-in providers as a silent fallback, so seeing it means the provider
 * chose it.
 */
public enum CodeSymbolKind {
    FILE,
    MODULE,
    NAMESPACE,
    PACKAGE,
    CLASS,
    INTERFACE,
    ENUM,
    ENUM_MEMBER,
    STRUCT,
    ANNOTATION,
    METHOD,
    FUNCTION,
    CONSTRUCTOR,
    FIELD,
    PROPERTY,
    VARIABLE,
    CONSTANT,
    TYPE_PARAMETER,
    IMPORT,
    /** A heading or region marker rather than a language construct, e.g. a {@code // MARK:} comment. */
    SECTION,
    OTHER;

    /** Whether this kind normally contains other symbols, which an outline can use to default its expansion. */
    public boolean isContainer() {
        switch (this) {
            case FILE:
            case MODULE:
            case NAMESPACE:
            case PACKAGE:
            case CLASS:
            case INTERFACE:
            case ENUM:
            case STRUCT:
            case ANNOTATION:
            case SECTION:
                return true;
            default:
                return false;
        }
    }

    /** Whether this kind is callable, which a go-to-symbol filter can use to show only functions. */
    public boolean isCallable() {
        return this == METHOD || this == FUNCTION || this == CONSTRUCTOR;
    }
}
