package com.lzt841.editor.completion;

import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.ObjectSet;

/**
 * A completion provider that offers a fixed set of keywords, filtered by the typed prefix.
 *
 * <p>Useful as a starting point, and as the default for the built-in highlighters: pass the same
 * keyword list they already know about. For anything that needs document-aware results, implement
 * {@link CodeCompletionProvider} yourself.
 */
public class KeywordCompletionProvider implements CodeCompletionProvider {
    private final Array<CodeCompletionItem> items = new Array<>();
    private final char[] triggerCharacters;

    public KeywordCompletionProvider(ObjectSet<String> keywords) {
        this(keywords, CodeCompletionItemKind.KEYWORD, '.');
    }

    public KeywordCompletionProvider(ObjectSet<String> keywords, CodeCompletionItemKind kind, char... triggers) {
        this.triggerCharacters = triggers == null ? new char[0] : triggers;
        if (keywords == null) {
            return;
        }
        for (String keyword : keywords) {
            items.add(CodeCompletionItem.of(keyword, kind));
        }
    }

    public KeywordCompletionProvider(String... keywords) {
        this.triggerCharacters = new char[] {'.'};
        if (keywords == null) {
            return;
        }
        for (String keyword : keywords) {
            items.add(CodeCompletionItem.of(keyword, CodeCompletionItemKind.KEYWORD));
        }
    }

    @Override
    public void provide(CodeCompletionRequest request, CodeCompletionResponse response) {
        Array<CodeCompletionItem> copy = new Array<>(items.size);
        copy.addAll(items);
        response.complete(copy);
    }

    @Override
    public boolean isTriggerCharacter(char character) {
        for (char trigger : triggerCharacters) {
            if (trigger == character) {
                return true;
            }
        }
        return false;
    }
}
