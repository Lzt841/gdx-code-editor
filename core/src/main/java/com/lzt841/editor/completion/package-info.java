/**
 * Completion: a {@link com.lzt841.editor.completion.CodeCompletionProvider} supplies candidates,
 * {@link com.lzt841.editor.completion.CodeCompletionController} drives the popup, and
 * {@link com.lzt841.editor.completion.CodeCompletionPopup} draws it.
 *
 * <p>The editor itself does not depend on this package. The controller registers as a
 * {@link com.lzt841.editor.CodeEditorInputInterceptor} and a
 * {@link com.lzt841.editor.CodeEditorCaretListener}, so installing it is one call:
 *
 * <pre>{@code
 * new CodeCompletionController(editor, new KeywordCompletionProvider("if", "else", "for")).install();
 * }</pre>
 */
package com.lzt841.editor.completion;
