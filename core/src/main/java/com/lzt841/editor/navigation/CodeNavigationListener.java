package com.lzt841.editor.navigation;

import com.badlogic.gdx.utils.Array;
import com.lzt841.editor.CodeEditorTextRange;

/**
 * How a host hooks into a {@link CodeNavigationController}: everything the controller cannot decide on
 * its own goes through here.
 *
 * <p>Three of these methods return a boolean, meaning "I handled it". The controller has a usable
 * default for each, so a host can implement only the parts it cares about — but the defaults are
 * deliberately modest, because opening a second file, showing a results list and asking the user for a
 * name are all things only the application can do.
 *
 * <p>Every method is called on the GDX thread.
 */
public interface CodeNavigationListener {
    /** Nothing was found. A host usually shows a status message. */
    default void onNoTarget(CodeNavigationController controller, CodeNavigationRequest request) {
    }

    /**
     * The request resolved to more than one place, so a host should show a list and call
     * {@link CodeNavigationController#navigateTo} with whatever the user picks.
     *
     * @return true when the host took over. Returning false lets the controller navigate to the first
     *     target, which is the right fallback for a definition and a poor one for references.
     */
    default boolean onMultipleTargets(
        CodeNavigationController controller,
        CodeNavigationRequest request,
        Array<CodeNavigationTarget> targets
    ) {
        return false;
    }

    /**
     * A target in a different document. The controller cannot open one, so this is the only way such a
     * target gets used.
     *
     * @return true when the host opened it. Returning false makes the navigation fail quietly.
     */
    default boolean onNavigateToOtherDocument(
        CodeNavigationController controller,
        CodeNavigationRequest request,
        CodeNavigationTarget target
    ) {
        return false;
    }

    /** The caret has moved to a target in this editor's document. */
    default void onNavigated(
        CodeNavigationController controller,
        CodeNavigationRequest request,
        CodeNavigationTarget target
    ) {
    }

    /**
     * Rename was triggered and needs a new name. The host shows its own input and calls
     * {@link CodeNavigationController#renameTo} when the user confirms.
     *
     * <p>{@code range} has already been vetted by {@link CodeRenameProvider#prepareRename}, so it is a
     * position that can be renamed.
     *
     * @return true when the host took over. Returning false makes the rename do nothing: there is no
     *     sensible default for "invent a name".
     */
    default boolean onRenameRequested(
        CodeNavigationController controller,
        CodeEditorTextRange range,
        String oldName
    ) {
        return false;
    }

    /** A rename was applied as one undo step. {@code editCount} is how many occurrences changed. */
    default void onRenameApplied(CodeNavigationController controller, CodeRenameRequest request, int editCount) {
    }

    /**
     * A rename did not happen. {@code message} is the provider's reason where there is one, or the
     * controller's own for a stale, out-of-range or overlapping edit batch. May be null.
     */
    default void onRenameFailed(CodeNavigationController controller, String message) {
    }
}
