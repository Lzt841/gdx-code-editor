/**
 * Go to definition, find references and rename.
 *
 * <p>{@link com.lzt841.editor.navigation.CodeNavigationController} is the entry point and follows the
 * same shape as the completion and hover controllers: it registers an input interceptor on
 * {@code install()}, so {@link com.lzt841.editor.CodeEditor} needs no knowledge of this package, and
 * every provider answers asynchronously with stale answers dropped.
 *
 * <p>Two provider interfaces, because they are separable in practice:
 * {@link com.lzt841.editor.navigation.CodeNavigationProvider} answers where a symbol is, and
 * {@link com.lzt841.editor.navigation.CodeRenameProvider} produces the edits to rewrite it.
 * {@link com.lzt841.editor.navigation.WordCodeNavigationProvider} implements both by matching
 * identifiers as text, which is what makes the controller useful with no language backend.
 *
 * <p>Renaming never edits the document from a provider. It returns a batch of
 * {@link com.lzt841.editor.CodeEditorTextEdit} and the controller applies it through
 * {@link com.lzt841.editor.CodeEditor#applyEdits}, which is what makes a rename one undo step and what
 * makes a bad batch fail whole instead of half-way.
 *
 * <p>Anything needing a UI — a results list, a rename prompt, opening another file — goes through
 * {@link com.lzt841.editor.navigation.CodeNavigationListener}. The editor draws no navigation UI of its
 * own.
 */
package com.lzt841.editor.navigation;
