/**
 * Hover tooltips and parameter hints, drawn by {@link com.lzt841.editor.hints.CodeHintPanel}.
 *
 * <p>{@link com.lzt841.editor.hints.CodeHoverController} shows text when the pointer rests over a
 * position; with no provider it reports the diagnostics there.
 * {@link com.lzt841.editor.hints.CodeSignatureHelpController} shows a signature while the caret is
 * inside a call's argument list, working out the active argument itself.
 *
 * <p>Both attach with one call and need no changes to the editor:
 *
 * <pre>{@code
 * new CodeHoverController(editor).install();
 * new CodeSignatureHelpController(editor, myProvider).install();
 * }</pre>
 */
package com.lzt841.editor.hints;
