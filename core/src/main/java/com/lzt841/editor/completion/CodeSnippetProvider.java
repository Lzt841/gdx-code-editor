package com.lzt841.editor.completion;

import com.badlogic.gdx.utils.Array;

/** Reusable snippet pack. The controller filters by each item's filterText or label. */
public final class CodeSnippetProvider implements CodeCompletionProvider {
    private final Array<CodeCompletionItem> snippets = new Array<>();

    public CodeSnippetProvider(CodeCompletionItem... items) {
        if (items == null) throw new IllegalArgumentException("snippets are required");
        for (CodeCompletionItem item : items) {
            if (item == null || !item.isSnippet()) throw new IllegalArgumentException("every item must use snippet insertion");
            snippets.add(item);
        }
    }

    public Array<CodeCompletionItem> getSnippets() { return new Array<>(snippets); }

    @Override public void provide(CodeCompletionRequest request, CodeCompletionResponse response) {
        if (!response.isCancelled()) response.complete(getSnippets());
    }

    /** Snippets are offered by manual/prefix completion, not by member-access punctuation. */
    @Override public boolean isTriggerCharacter(char character) { return false; }
}
