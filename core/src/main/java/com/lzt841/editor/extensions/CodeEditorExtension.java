package com.lzt841.editor.extensions;

/** Optional functionality with resources owned by one editor. Prefer a new instance per editor. */
public interface CodeEditorExtension {
    String getId();

    /** Register listeners/commands and cleanup callbacks through this scope for automatic rollback. */
    void install(CodeExtensionContext context);

    /** Called after scoped resources have been released, only if installation completed successfully. */
    default void uninstall() {
    }
}
