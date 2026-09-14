package com.lzt841.editor.highlight;

/**
 * What a semantic token is, as far as colouring is concerned.
 *
 * <p>The names and the set mirror the standard token types in the Language Server Protocol, so a
 * caller driving the editor from an LSP server can map {@code SemanticTokenTypes} onto this enum
 * one-for-one without a translation table. Nothing here is Java-specific.
 *
 * <p>The editor attaches no behaviour to these values. They exist only so a colour can be looked up
 * once per token instead of the producer having to resolve a {@link com.badlogic.gdx.graphics.Color}
 * itself for every identifier in the file. A producer that would rather pick the colour directly can
 * pass one on the token and the type is then only informational.
 *
 * <p>Why an enum rather than a string, given LSP allows custom type names: a fixed set keeps the
 * per-line token arrays free of string comparisons on the draw path, and an unknown name from a
 * server is better mapped to the nearest standard type by the caller — which knows the server — than
 * silently coloured by the editor, which does not.
 */
public enum CodeSemanticTokenType {
    /** A package, module or namespace name. */
    NAMESPACE,
    /** A type whose more specific kind is not known or does not matter. */
    TYPE,
    CLASS,
    ENUM,
    INTERFACE,
    /** A record, or a struct in languages that have them. */
    STRUCT,
    /** A generic type parameter, e.g. the {@code T} in {@code List<T>}. */
    TYPE_PARAMETER,
    /** A method or function parameter, at its declaration or at a use. */
    PARAMETER,
    /** A local variable. The distinction from {@link #PROPERTY} is the usual reason to want this. */
    VARIABLE,
    /** A field or property of a type. */
    PROPERTY,
    /** An enum constant. */
    ENUM_MEMBER,
    /** An event, in languages that have them as a declaration form. */
    EVENT,
    /** A free function, not attached to a type. */
    FUNCTION,
    /** A method on a type. */
    METHOD,
    /** A macro, or a preprocessor definition. */
    MACRO,
    /** A language keyword the lexer could not classify on its own, e.g. a contextual keyword. */
    KEYWORD,
    /** A modifier such as {@code public} or {@code static}. */
    MODIFIER,
    COMMENT,
    STRING,
    NUMBER,
    /** A regular expression literal. */
    REGEXP,
    OPERATOR,
    /** An annotation or attribute use, e.g. {@code @Override}. */
    DECORATOR
}
