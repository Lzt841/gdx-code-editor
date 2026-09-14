package com.lzt841.editor.completion;

/**
 * Category of a completion candidate, used to pick an icon or colour. Deliberately close to the
 * Language Server Protocol's set so LSP-backed providers can map across directly.
 */
public enum CodeCompletionItemKind {
    TEXT,
    KEYWORD,
    SNIPPET,
    VARIABLE,
    FIELD,
    PROPERTY,
    CONSTANT,
    METHOD,
    FUNCTION,
    CONSTRUCTOR,
    CLASS,
    INTERFACE,
    ENUM,
    ENUM_MEMBER,
    STRUCT,
    MODULE,
    PACKAGE,
    FILE,
    FOLDER,
    TYPE_PARAMETER,
    OPERATOR,
    EVENT,
    REFERENCE
}
