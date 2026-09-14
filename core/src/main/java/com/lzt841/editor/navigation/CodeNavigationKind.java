package com.lzt841.editor.navigation;

/**
 * What a navigation request is asking for. The LSP set, minus the ones that need no editor support.
 *
 * <p>A provider that cannot tell these apart is free to answer them all the same way — the word-based
 * {@link WordCodeNavigationProvider} does exactly that. The distinction exists so a real language
 * backend can be precise, and so a host can bind them to different keys.
 */
public enum CodeNavigationKind {
    /** Where the symbol is defined. The usual Ctrl-click / F12 target. */
    DEFINITION,
    /** Where the symbol is declared, which in some languages is not where it is defined. */
    DECLARATION,
    /** The definition of the symbol's type rather than of the symbol. */
    TYPE_DEFINITION,
    /** Concrete implementations of an interface or abstract member. */
    IMPLEMENTATION,
    /** Every use of the symbol. Normally many results, which a host shows as a list. */
    REFERENCES
}
