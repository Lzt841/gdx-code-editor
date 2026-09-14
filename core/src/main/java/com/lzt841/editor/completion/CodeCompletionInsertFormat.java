package com.lzt841.editor.completion;

/**
 * How {@link CodeCompletionItem#insertText} is interpreted when the item is accepted.
 *
 * <p>LSP's {@code InsertTextFormat}. The default is {@link #PLAIN_TEXT} so existing providers keep
 * inserting literally, including any {@code $} they happened to put in a label. Set
 * {@link #SNIPPET} — or {@link CodeCompletionItemKind#SNIPPET} — to opt into placeholder tab stops.
 */
public enum CodeCompletionInsertFormat {
    /** Insert the text as-is. */
    PLAIN_TEXT,
    /** Parse as a snippet: {@code $1}, {@code ${1:default}}, {@code $0}, variables, mirrors. */
    SNIPPET
}
