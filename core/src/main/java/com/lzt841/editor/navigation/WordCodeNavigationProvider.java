package com.lzt841.editor.navigation;

import com.badlogic.gdx.utils.Array;
import com.lzt841.editor.CodeEditor;
import com.lzt841.editor.CodeEditorTextEdit;
import com.lzt841.editor.CodeEditorTextRange;
import com.lzt841.editor.structure.CodeSymbol;

/**
 * Navigation and rename with no language backend: it matches identifiers by text.
 *
 * <p>Useful on its own for a plain-text or config editor, useful as the starting point for a real
 * provider, and useful as the thing that makes {@link CodeNavigationController} work out of the box.
 * Its limits are the obvious ones and are not worth hiding: it cannot tell two same-named locals
 * apart, it matches inside strings and comments, and it never leaves the current document. Anything
 * that needs to be right about scope needs a provider that understands the language.
 *
 * <p>{@link CodeNavigationKind#DEFINITION} does slightly better than plain text search: if the
 * editor's symbol provider knows a symbol with that exact name, its declaration is the target. Failing
 * that, the first occurrence in the document is used.
 *
 * <p>Scanning is character-by-character through {@link CodeEditor#getCharAt}, which allocates nothing.
 * A full pass over a 100k-line document is a one-off cost on a key press, not something the frame loop
 * touches.
 */
public class WordCodeNavigationProvider implements CodeNavigationProvider, CodeRenameProvider {
    private boolean skipDeclarationLookup;

    /**
     * Whether {@link CodeNavigationKind#DEFINITION} should ignore the symbol tree and behave like a
     * plain text search. Off by default.
     */
    public WordCodeNavigationProvider setSkipDeclarationLookup(boolean skipDeclarationLookup) {
        this.skipDeclarationLookup = skipDeclarationLookup;
        return this;
    }

    @Override
    public void provideTargets(CodeNavigationRequest request, CodeNavigationResponse response) {
        if (!request.hasWord()) {
            response.complete(null);
            return;
        }
        CodeEditor editor = request.editor;
        if (request.kind == CodeNavigationKind.REFERENCES) {
            Array<CodeNavigationTarget> targets = new Array<CodeNavigationTarget>();
            Array<CodeEditorTextRange> occurrences = findWholeWordOccurrences(editor, request.word);
            for (int i = 0; i < occurrences.size; i++) {
                targets.add(targetFor(editor, occurrences.get(i)));
            }
            response.complete(targets);
            return;
        }

        if (!skipDeclarationLookup) {
            CodeSymbol symbol = findSymbolNamed(editor, request.word);
            if (symbol != null) {
                CodeEditorTextRange range = new CodeEditorTextRange(
                    symbol.selectionStartLine, symbol.selectionStartColumn,
                    symbol.selectionStartLine, symbol.selectionEndColumn);
                response.completeOne(new CodeNavigationTarget(null, range, symbol.name, symbol.detail));
                return;
            }
        }
        Array<CodeEditorTextRange> occurrences = findWholeWordOccurrences(editor, request.word);
        if (occurrences.size == 0) {
            response.complete(null);
            return;
        }
        // The first occurrence, and only when it is not the one the caret is already on: jumping to
        // where you already are reads as "nothing happened".
        for (int i = 0; i < occurrences.size; i++) {
            CodeEditorTextRange candidate = occurrences.get(i);
            if (!candidate.equals(request.wordRange)) {
                response.completeOne(targetFor(editor, candidate));
                return;
            }
        }
        response.complete(null);
    }

    @Override
    public void provideRenameEdits(CodeRenameRequest request, CodeRenameResponse response) {
        if (!isIdentifier(request.newName)) {
            response.fail("'" + request.newName + "' is not a valid identifier");
            return;
        }
        if (request.newName.equals(request.oldName)) {
            response.fail("the name is unchanged");
            return;
        }
        Array<CodeEditorTextRange> occurrences = findWholeWordOccurrences(request.editor, request.oldName);
        if (occurrences.size == 0) {
            response.fail("no occurrence of '" + request.oldName + "' found");
            return;
        }
        Array<CodeEditorTextEdit> edits = new Array<CodeEditorTextEdit>(occurrences.size);
        for (int i = 0; i < occurrences.size; i++) {
            edits.add(new CodeEditorTextEdit(occurrences.get(i), request.newName));
        }
        response.complete(edits);
    }

    /**
     * Every whole-word occurrence of {@code word}, in document order. Whole-word means the characters
     * on either side are not identifier characters, so {@code count} does not match inside
     * {@code counter}.
     */
    public static Array<CodeEditorTextRange> findWholeWordOccurrences(CodeEditor editor, String word) {
        Array<CodeEditorTextRange> found = new Array<CodeEditorTextRange>();
        if (editor == null || word == null || word.isEmpty()) {
            return found;
        }
        int length = word.length();
        int lineCount = editor.getLineCount();
        for (int line = 0; line < lineCount; line++) {
            int lineLength = editor.getLineLength(line);
            int column = 0;
            while (column + length <= lineLength) {
                if (matchesAt(editor, line, column, word, lineLength)) {
                    found.add(new CodeEditorTextRange(line, column, line, column + length));
                    column += length;
                } else {
                    column++;
                }
            }
        }
        return found;
    }

    private static boolean matchesAt(CodeEditor editor, int line, int column, String word, int lineLength) {
        if (column > 0 && isWordChar(editor.getCharAt(line, column - 1))) {
            return false;
        }
        int end = column + word.length();
        if (end < lineLength && isWordChar(editor.getCharAt(line, end))) {
            return false;
        }
        for (int i = 0; i < word.length(); i++) {
            if (editor.getCharAt(line, column + i) != word.charAt(i)) {
                return false;
            }
        }
        return true;
    }

    private static CodeNavigationTarget targetFor(CodeEditor editor, CodeEditorTextRange range) {
        CodeSymbol enclosing = editor.getSymbolAt(range.startLine);
        String label = enclosing != null ? enclosing.name : "";
        return new CodeNavigationTarget(null, range, label, editor.getLineText(range.startLine).trim());
    }

    private static CodeSymbol findSymbolNamed(CodeEditor editor, String name) {
        Array<CodeSymbol> candidates = editor.findSymbols(name);
        for (int i = 0; i < candidates.size; i++) {
            if (name.equals(candidates.get(i).name)) {
                return candidates.get(i);
            }
        }
        return null;
    }

    private static boolean isIdentifier(String text) {
        if (text == null || text.isEmpty()) {
            return false;
        }
        if (Character.isDigit(text.charAt(0))) {
            return false;
        }
        for (int i = 0; i < text.length(); i++) {
            if (!isWordChar(text.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    private static boolean isWordChar(char c) {
        return Character.isLetterOrDigit(c) || c == '_';
    }
}
