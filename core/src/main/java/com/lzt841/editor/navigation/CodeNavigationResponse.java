package com.lzt841.editor.navigation;

import com.badlogic.gdx.utils.Array;

/**
 * One-shot sink for a {@link CodeNavigationProvider}. Calling {@link #complete} more than once, or
 * after a newer request has superseded this one, is a no-op.
 */
public interface CodeNavigationResponse {
    /**
     * Delivers the results. Must be called from the GDX thread. {@code targets} may be null or empty,
     * which reports "nothing found". The array may be retained, so do not mutate it afterwards.
     */
    void complete(Array<CodeNavigationTarget> targets);

    /** Delivers a single result, or reports nothing found when {@code target} is null. */
    default void completeOne(CodeNavigationTarget target) {
        if (target == null) {
            complete(null);
            return;
        }
        Array<CodeNavigationTarget> targets = new Array<CodeNavigationTarget>(1);
        targets.add(target);
        complete(targets);
    }
}
