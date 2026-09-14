package com.lzt841.editor.navigation;

/**
 * Resolves "where is this symbol" questions for a {@link CodeNavigationController}.
 *
 * <p>Async by design, like the completion and hover providers: answer through
 * {@link CodeNavigationResponse#complete} whenever the answer is ready, from the GDX thread. A
 * response that arrives after the user has asked something else is dropped by the controller, so an
 * out-of-date language server reply is harmless.
 *
 * <p>Renaming is a separate interface, {@link CodeRenameProvider}, because plenty of backends can
 * answer where a symbol lives without being able to rewrite it safely.
 */
public interface CodeNavigationProvider {
    /**
     * Resolves {@code request} and delivers the result to {@code response}.
     *
     * <p>A provider that does not handle {@link CodeNavigationRequest#kind} should complete with null
     * rather than ignoring the call: the controller reports "nothing found" to its listener, and a
     * request never answered would look like a hang.
     */
    void provideTargets(CodeNavigationRequest request, CodeNavigationResponse response);
}
