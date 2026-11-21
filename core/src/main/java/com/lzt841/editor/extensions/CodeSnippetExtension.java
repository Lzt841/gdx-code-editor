package com.lzt841.editor.extensions;

import com.lzt841.editor.completion.CodeCompletionItem;
import com.lzt841.editor.completion.CodeCompletionRegistry;
import com.lzt841.editor.completion.CodeSnippetProvider;

/** A named snippet pack, contributed to the editor's shared completion registry for this scope. */
public final class CodeSnippetExtension implements CodeEditorExtension {
    private final String id;
    private final CodeSnippetProvider provider;
    private final CodeCompletionRegistry.Condition condition;

    public CodeSnippetExtension(String id, CodeCompletionItem... snippets) {
        this(id, null, snippets);
    }

    public CodeSnippetExtension(String id, CodeCompletionRegistry.Condition condition, CodeCompletionItem... snippets) {
        if (id == null || id.trim().isEmpty() || !id.equals(id.trim())) throw new IllegalArgumentException("valid id is required");
        this.id = id;
        this.condition = condition;
        provider = new CodeSnippetProvider(snippets);
    }

    @Override public String getId() { return id; }

    @Override public void install(CodeExtensionContext context) {
        context.registerCompletionProvider(id, provider, condition);
    }
}
