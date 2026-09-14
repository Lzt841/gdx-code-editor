package com.lzt841.editor.completion;

/** Why a completion request was issued. */
public enum CodeCompletionTrigger {
    /** The user pressed the explicit trigger (Ctrl-Space by default). */
    MANUAL,
    /** The user typed a configured trigger character, typically {@code '.'} or {@code ':'}. */
    CHARACTER,
    /** The popup is already open and the prefix changed, so the list is being refreshed. */
    PREFIX
}
