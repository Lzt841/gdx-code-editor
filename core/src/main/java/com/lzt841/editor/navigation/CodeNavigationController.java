package com.lzt841.editor.navigation;

import com.badlogic.gdx.Input;
import com.badlogic.gdx.utils.Array;
import com.lzt841.editor.CodeEditor;
import com.lzt841.editor.CodeEditorAction;
import com.lzt841.editor.CodeEditorInputInterceptor;
import com.lzt841.editor.CodeEditorTextEdit;
import com.lzt841.editor.CodeEditorTextRange;
import com.lzt841.editor.CodeKeyStroke;
import com.lzt841.editor.CodeKeymap;

/**
 * Go to definition, find references and rename, wired to keys.
 *
 * <p>{@link #install()} is the whole setup:
 *
 * <pre>{@code
 * CodeNavigationController navigation = new CodeNavigationController(editor);
 * navigation.setListener(myListener);
 * navigation.install();
 * }</pre>
 *
 * <p>With no provider it uses {@link WordCodeNavigationProvider}, so identifier-level navigation and
 * rename work immediately; pass a real one for a language that has a backend.
 *
 * <p>Defaults are F12 for definition, Shift+F12 for references and Shift+F6 for rename, in this
 * controller's own keymap rather than the editor's — the same split the completion popup uses. Rename
 * is not on F2 because this editor has always folded with F2, and silently taking that away from
 * existing keymaps would be a worse default than an unfamiliar chord.
 *
 * <p>Results the controller cannot act on alone — several targets, a target in another file, the new
 * name for a rename — go to a {@link CodeNavigationListener}. Without one, a definition jumps to the
 * first target, references do nothing beyond a single hit, and rename does nothing at all.
 */
public class CodeNavigationController {
    private final CodeEditor editor;
    private final CodeNavigationProvider provider;
    private final CodeRenameProvider renameProvider;
    private final Array<CodeNavigationTarget> lastTargets = new Array<CodeNavigationTarget>();
    private final CodeEditorInputInterceptor inputInterceptor = new CodeEditorInputInterceptor() {
        @Override
        public boolean onKeyDown(CodeEditor source, int keycode) {
            return handleKeyDown(keycode);
        }
    };

    private CodeNavigationListener listener;
    private CodeKeymap keymap = navigationDefaults();
    private int requestSerial;
    private boolean installed;

    /** Uses a {@link WordCodeNavigationProvider} for both navigation and rename. */
    public CodeNavigationController(CodeEditor editor) {
        this(editor, null, null);
    }

    public CodeNavigationController(CodeEditor editor, CodeNavigationProvider provider) {
        this(editor, provider, provider instanceof CodeRenameProvider ? (CodeRenameProvider) provider : null);
    }

    /**
     * @param provider null for {@link WordCodeNavigationProvider}
     * @param renameProvider null to disable renaming, unless {@code provider} is itself a
     *     {@link CodeRenameProvider}, in which case that is used
     */
    public CodeNavigationController(
        CodeEditor editor,
        CodeNavigationProvider provider,
        CodeRenameProvider renameProvider
    ) {
        if (editor == null) {
            throw new IllegalArgumentException("editor must not be null");
        }
        this.editor = editor;
        if (provider == null) {
            WordCodeNavigationProvider fallback = new WordCodeNavigationProvider();
            this.provider = fallback;
            this.renameProvider = renameProvider != null ? renameProvider : fallback;
        } else {
            this.provider = provider;
            this.renameProvider = renameProvider;
        }
    }

    /** F12, Shift+F12, Shift+F6. Independent of the editor's keymap. */
    public static CodeKeymap navigationDefaults() {
        CodeKeymap map = new CodeKeymap();
        map.bind(Input.Keys.F12, CodeEditorAction.NAVIGATE_TO_DEFINITION);
        map.bind(CodeKeyStroke.shift(Input.Keys.F12), CodeEditorAction.NAVIGATE_FIND_REFERENCES);
        map.bind(CodeKeyStroke.shift(Input.Keys.F6), CodeEditorAction.NAVIGATE_RENAME);
        return map;
    }

    public void install() {
        if (installed) {
            return;
        }
        editor.addInputInterceptor(inputInterceptor);
        installed = true;
    }

    public void uninstall() {
        if (!installed) {
            return;
        }
        editor.removeInputInterceptor(inputInterceptor);
        // Anything still in flight is now stale.
        requestSerial++;
        lastTargets.clear();
        installed = false;
    }

    public boolean isInstalled() {
        return installed;
    }

    public CodeEditor getEditor() {
        return editor;
    }

    public void setListener(CodeNavigationListener listener) {
        this.listener = listener;
    }

    public CodeNavigationListener getListener() {
        return listener;
    }

    /** The controller's own chords. Mutate it to rebind; never null. */
    public CodeKeymap getKeymap() {
        return keymap;
    }

    /** Replaces the keymap; null restores {@link #navigationDefaults()}. */
    public void setKeymap(CodeKeymap keymap) {
        this.keymap = keymap != null ? keymap : navigationDefaults();
    }

    /** Whether renaming is available, i.e. a rename provider was supplied or defaulted. */
    public boolean isRenameSupported() {
        return renameProvider != null;
    }

    /**
     * Targets from the most recent request that produced any, in document order. Not a live view: it is
     * replaced on the next request and cleared by {@link #uninstall()}. Handy for a host that shows a
     * results panel and wants to redraw it later without asking again.
     */
    public Array<CodeNavigationTarget> getLastTargets() {
        return lastTargets;
    }

    /** Asks for the definition of the symbol under the caret. */
    public void goToDefinition() {
        request(CodeNavigationKind.DEFINITION);
    }

    /** Asks for every use of the symbol under the caret. */
    public void findReferences() {
        request(CodeNavigationKind.REFERENCES);
    }

    /**
     * Asks the provider for {@code kind} at the caret and routes the answer.
     *
     * <p>Returns immediately: the provider may answer on a later frame. Only the newest request's
     * answer is used, so pressing F12 twice quickly cannot navigate twice.
     */
    public void request(CodeNavigationKind kind) {
        int line = editor.getCursorLine();
        int column = editor.getCursorColumn();
        CodeEditorTextRange wordRange = editor.getWordRangeAt(line, column);
        String word = wordRange == null ? "" : editor.getTextRange(wordRange);
        final CodeNavigationRequest request = new CodeNavigationRequest(
            editor, kind, line, column, editor.getDocumentVersion(), wordRange, word);
        final int serial = ++requestSerial;
        provider.provideTargets(request, new CodeNavigationResponse() {
            private boolean delivered;

            @Override
            public void complete(Array<CodeNavigationTarget> targets) {
                if (delivered || serial != requestSerial) {
                    return;
                }
                delivered = true;
                deliverTargets(request, targets);
            }
        });
    }

    /**
     * Moves the caret to {@code target}, or hands it to the listener when it is in another document.
     *
     * <p>A host calls this with whatever the user picked out of a results list. {@code request} may be
     * null, and is only passed through to the listener.
     *
     * @return whether the caret moved, or the host reported it opened the other document
     */
    public boolean navigateTo(CodeNavigationTarget target, CodeNavigationRequest request) {
        if (target == null) {
            return false;
        }
        if (!target.isCurrentDocument()) {
            return listener != null && listener.onNavigateToOtherDocument(this, request, target);
        }
        if (target.getLine() < 0 || target.getLine() >= editor.getLineCount()) {
            // A stale target: something deleted the lines it pointed at. Rejected rather than clamped,
            // the same way goToSymbol treats a stale symbol, so a caller can tell the difference.
            return false;
        }
        editor.setCursorPosition(target.getLine(), target.getColumn());
        editor.scrollLineToCenter(target.getLine());
        if (listener != null) {
            listener.onNavigated(this, request, target);
        }
        return true;
    }

    /** {@link #navigateTo(CodeNavigationTarget, CodeNavigationRequest)} with no request context. */
    public boolean navigateTo(CodeNavigationTarget target) {
        return navigateTo(target, null);
    }

    /**
     * Starts a rename of the symbol under the caret: vets the position with
     * {@link CodeRenameProvider#prepareRename} and then asks the listener for a name.
     *
     * @return false when renaming is unsupported, the editor is read-only, the caret is not on a
     *     renameable position, or no listener took the request
     */
    public boolean requestRename() {
        if (renameProvider == null || editor.isReadOnly() || editor.isDisabled()) {
            return false;
        }
        int line = editor.getCursorLine();
        int column = editor.getCursorColumn();
        CodeEditorTextRange range = renameProvider.prepareRename(editor, line, column);
        if (range == null) {
            if (listener != null) {
                listener.onRenameFailed(this, "nothing to rename here");
            }
            return false;
        }
        String oldName = editor.getTextRange(range);
        if (oldName.isEmpty()) {
            if (listener != null) {
                listener.onRenameFailed(this, "nothing to rename here");
            }
            return false;
        }
        return listener != null && listener.onRenameRequested(this, range, oldName);
    }

    /**
     * Renames the symbol at the caret to {@code newName}, as one undo step.
     *
     * <p>The host calls this once the user has confirmed a name. Applies through
     * {@link CodeEditor#applyEdits}, so an out-of-range or overlapping batch is rejected whole rather
     * than applied half-way, and one Undo puts everything back.
     *
     * <p>Returns immediately; the provider may answer later. Success or failure is reported to the
     * listener.
     */
    public void renameTo(String newName) {
        if (renameProvider == null) {
            failRename("renaming is not supported here");
            return;
        }
        if (editor.isReadOnly() || editor.isDisabled()) {
            failRename("the editor is read-only");
            return;
        }
        int line = editor.getCursorLine();
        int column = editor.getCursorColumn();
        CodeEditorTextRange range = renameProvider.prepareRename(editor, line, column);
        if (range == null) {
            failRename("nothing to rename here");
            return;
        }
        String oldName = editor.getTextRange(range);
        if (oldName.isEmpty() || newName == null || newName.isEmpty()) {
            failRename("nothing to rename here");
            return;
        }
        final CodeRenameRequest request = new CodeRenameRequest(
            editor, line, column, editor.getDocumentVersion(), range, oldName, newName);
        final int serial = ++requestSerial;
        renameProvider.provideRenameEdits(request, new CodeRenameResponse() {
            private boolean delivered;

            @Override
            public void complete(Array<CodeEditorTextEdit> edits) {
                if (delivered || serial != requestSerial) {
                    return;
                }
                delivered = true;
                applyRename(request, edits);
            }

            @Override
            public void fail(String message) {
                if (delivered || serial != requestSerial) {
                    return;
                }
                delivered = true;
                failRename(message);
            }
        });
    }

    /** Cancels anything in flight, so a late provider answer is ignored. */
    public void cancel() {
        requestSerial++;
    }

    /** Runs an action from this controller's keymap. Public so a menu item can reuse it. */
    public boolean performAction(CodeEditorAction action) {
        if (action == null) {
            return false;
        }
        switch (action) {
            case NAVIGATE_TO_DEFINITION:
                goToDefinition();
                return true;
            case NAVIGATE_FIND_REFERENCES:
                findReferences();
                return true;
            case NAVIGATE_RENAME:
                // Consumed either way. A rename chord that fell through to the editor would type
                // nothing useful, and swallowing it keeps the key from folding a region instead.
                requestRename();
                return true;
            default:
                return false;
        }
    }

    /** Key entry point, also called directly by the tests. */
    public boolean handleKeyDown(int keycode) {
        return performAction(keymap.actionForCurrentKeyDown(keycode));
    }

    private void deliverTargets(CodeNavigationRequest request, Array<CodeNavigationTarget> targets) {
        lastTargets.clear();
        if (targets == null || targets.size == 0) {
            if (listener != null) {
                listener.onNoTarget(this, request);
            }
            return;
        }
        lastTargets.addAll(targets);
        if (targets.size == 1) {
            navigateTo(targets.first(), request);
            return;
        }
        if (listener != null && listener.onMultipleTargets(this, request, targets)) {
            return;
        }
        // No host list to show. Going to the first target is right for a definition that resolved
        // ambiguously; for references it is a poor substitute for a list, which is why the listener
        // gets first refusal.
        navigateTo(targets.first(), request);
    }

    private void applyRename(CodeRenameRequest request, Array<CodeEditorTextEdit> edits) {
        if (edits == null || edits.size == 0) {
            failRename("no occurrences to rename");
            return;
        }
        if (editor.getDocumentVersion() != request.documentVersion) {
            // The document moved while the provider was thinking, so its positions describe text that
            // no longer exists. Applying them would corrupt the file in a way that looks like a
            // successful rename.
            failRename("the document changed while the rename was being prepared");
            return;
        }
        if (!editor.canApplyEdits(edits)) {
            failRename("the rename produced edits that do not fit the document");
            return;
        }
        // Named, so a host menu can offer "Undo Rename" rather than a bare "Undo" for an edit that
        // touched lines all over the file.
        if (!editor.applyEdits(edits, "Rename " + request.oldName + " to " + request.newName)) {
            failRename("the edits could not be applied");
            return;
        }
        if (listener != null) {
            listener.onRenameApplied(this, request, edits.size);
        }
    }

    private void failRename(String message) {
        if (listener != null) {
            listener.onRenameFailed(this, message);
        }
    }
}
