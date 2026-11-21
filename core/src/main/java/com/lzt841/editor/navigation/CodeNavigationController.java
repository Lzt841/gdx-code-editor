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
 * <p>Defaults are F12 for definition, Shift+F12 for references, F4/Shift+F4 for browsing retained
 * results and Shift+F6 for rename, in this
 * controller's own keymap rather than the editor's — the same split the completion popup uses. Rename
 * is not on F2 because this editor has always folded with F2, and silently taking that away from
 * existing keymaps would be a worse default than an unfamiliar chord.
 *
 * <p>Results the controller cannot act on alone — several targets, a target in another file, the new
 * name for a rename — go to a {@link CodeNavigationListener}. Without one, navigation jumps to the
 * first local target and F4/Shift+F4 browse the rest. Opening another document or asking for a new name
 * still needs the host.
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
    private CodeNavigationRequest lastTargetRequest;
    private int lastTargetIndex = -1;
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
            this.renameProvider = renameProvider != null ? renameProvider
                : provider instanceof CodeRenameProvider ? (CodeRenameProvider) provider : null;
        }
    }

    /** F12, Shift+F12, Shift+F6, F4 and Shift+F4. Independent of the editor's keymap. */
    public static CodeKeymap navigationDefaults() {
        CodeKeymap map = new CodeKeymap();
        map.bind(Input.Keys.F12, CodeEditorAction.NAVIGATE_TO_DEFINITION);
        map.bind(CodeKeyStroke.shift(Input.Keys.F12), CodeEditorAction.NAVIGATE_FIND_REFERENCES);
        map.bind(CodeKeyStroke.shift(Input.Keys.F6), CodeEditorAction.NAVIGATE_RENAME);
        map.bind(Input.Keys.F4, CodeEditorAction.NAVIGATE_NEXT_TARGET);
        map.bind(CodeKeyStroke.shift(Input.Keys.F4), CodeEditorAction.NAVIGATE_PREVIOUS_TARGET);
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
        cancel();
        clearTargets();
        if (installed) {
            editor.removeInputInterceptor(inputInterceptor);
            installed = false;
        }
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
     * Snapshot of the latest navigation results, in provider order. Cleared by the next navigation
     * request, a document edit, or {@link #uninstall()}. Mutating this array does not affect the
     * retained results. Handy for a host that shows a results list without asking the provider again.
     */
    public Array<CodeNavigationTarget> getLastTargets() {
        invalidateStaleTargets();
        return new Array<CodeNavigationTarget>(lastTargets);
    }

    /** Number of retained results; unlike {@link #getLastTargets()}, does not allocate a snapshot. */
    public int getTargetCount() {
        invalidateStaleTargets();
        return lastTargets.size;
    }

    /** Index of the last successfully visited result, or -1 if none was visited or results expired. */
    public int getLastTargetIndex() {
        invalidateStaleTargets();
        return lastTargetIndex;
    }

    /** Visits the next retained result, wrapping at the end. Does not ask the provider again. */
    public boolean navigateToNextTarget() {
        return navigateAdjacentTarget(true);
    }

    /** Visits the preceding retained result, wrapping at the beginning. */
    public boolean navigateToPreviousTarget() {
        return navigateAdjacentTarget(false);
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
     * answer is used, and only while the document version, caret and selection still match. Disabled
     * and password-mode editors do not request navigation.
     */
    public void request(CodeNavigationKind kind) {
        final int serial = ++requestSerial;
        clearTargets();
        if (!canNavigate()) {
            return;
        }
        int line = editor.getCursorLine();
        int column = editor.getCursorColumn();
        final CodeEditorTextRange selection = editor.getSelection();
        CodeEditorTextRange wordRange = editor.getWordRangeAt(line, column);
        String word = wordRange == null ? "" : editor.getTextRange(wordRange);
        final CodeNavigationRequest request = new CodeNavigationRequest(
            editor, kind, line, column, editor.getDocumentVersion(), wordRange, word);
        provider.provideTargets(request, new CodeNavigationResponse() {
            private boolean delivered;

            @Override
            public void complete(Array<CodeNavigationTarget> targets) {
                if (delivered || serial != requestSerial) {
                    return;
                }
                delivered = true;
                if (!canNavigate() || editor.getDocumentVersion() != request.documentVersion
                    || editor.getCursorLine() != request.line || editor.getCursorColumn() != request.column
                    || (selection == null ? editor.getSelection() != null : !selection.equals(editor.getSelection()))) {
                    return;
                }
                deliverTargets(request, targets);
            }
        });
    }

    /**
     * Moves the caret to {@code target}, or hands it to the listener when it is in another document.
     *
     * <p>A host calls this with whatever the user picked out of a results list. {@code request} may be
     * null. When supplied, its editor and document version must still match, so a results list cannot
     * navigate using positions from an earlier document. Read-only navigation is allowed; disabled
     * and password-mode editors cannot navigate.
     *
     * @return whether the caret moved, or the host reported it opened the other document
     */
    public boolean navigateTo(CodeNavigationTarget target, CodeNavigationRequest request) {
        return navigateTo(target, request, -1);
    }

    private boolean navigateTo(CodeNavigationTarget target, CodeNavigationRequest request, int resultIndex) {
        if (target == null || !canNavigate()
            || (request != null && (request.editor != editor
                || request.documentVersion != editor.getDocumentVersion()))) {
            return false;
        }
        if (!target.isCurrentDocument()) {
            if (listener == null) {
                return false;
            }
            cancel();
            CodeNavigationRequest retainedRequest = lastTargetRequest;
            boolean navigated = listener.onNavigateToOtherDocument(this, request, target);
            if (navigated && lastTargetRequest == retainedRequest) {
                updateTargetIndex(target, resultIndex);
            }
            return navigated;
        }
        if (!isValidRange(target.range)) {
            return false;
        }
        cancel();
        editor.setCursorPosition(target.getLine(), target.getColumn());
        editor.scrollLineToCenter(target.getLine());
        updateTargetIndex(target, resultIndex);
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
        cancel();
        if (renameProvider == null || !canRename()) {
            return false;
        }
        int line = editor.getCursorLine();
        int column = editor.getCursorColumn();
        CodeEditorTextRange range = renameProvider.prepareRename(editor, line, column);
        if (!isValidRange(range)) {
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
        final int serial = ++requestSerial;
        if (renameProvider == null) {
            failRename("renaming is not supported here");
            return;
        }
        if (!canRename()) {
            failRename("renaming is unavailable in the current editor mode");
            return;
        }
        int line = editor.getCursorLine();
        int column = editor.getCursorColumn();
        CodeEditorTextRange range = renameProvider.prepareRename(editor, line, column);
        if (!isValidRange(range)) {
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
        if (action == null || !canNavigate()) {
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
            case NAVIGATE_NEXT_TARGET:
                navigateToNextTarget();
                return true;
            case NAVIGATE_PREVIOUS_TARGET:
                navigateToPreviousTarget();
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
        clearTargets();
        if (targets != null) {
            for (int i = 0; i < targets.size; i++) {
                CodeNavigationTarget target = targets.get(i);
                if (target != null && (!target.isCurrentDocument() || isValidRange(target.range))) {
                    lastTargets.add(target);
                }
            }
        }
        if (lastTargets.size == 0) {
            if (listener != null) {
                listener.onNoTarget(this, request);
            }
            return;
        }
        lastTargetRequest = request;
        if (lastTargets.size == 1) {
            navigateTo(lastTargets.first(), request);
            return;
        }
        if (listener != null && listener.onMultipleTargets(this, request, getLastTargets())) {
            return;
        }
        if (lastTargetRequest != request || lastTargets.size == 0) {
            return;
        }
        // No host list to show. Going to the first target is right for a definition that resolved
        // ambiguously; for references it is a poor substitute for a list, which is why the listener
        // gets first refusal.
        navigateToNextTarget();
    }

    private void applyRename(CodeRenameRequest request, Array<CodeEditorTextEdit> edits) {
        if (!canRename()) {
            failRename("renaming is unavailable in the current editor mode");
            return;
        }
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

    private boolean navigateAdjacentTarget(boolean forward) {
        invalidateStaleTargets();
        if (!canNavigate() || lastTargets.size == 0) {
            return false;
        }
        CodeNavigationRequest request = lastTargetRequest;
        int count = lastTargets.size;
        int index = lastTargetIndex;
        for (int visited = 0; visited < count; visited++) {
            index = forward ? (index + 1) % count : (index <= 0 ? count - 1 : index - 1);
            if (navigateTo(lastTargets.get(index), request, index)) {
                return true;
            }
            // A host can open another file, clear results or start a new request in its callback.
            invalidateStaleTargets();
            if (lastTargetRequest != request || !canNavigate()) {
                return false;
            }
        }
        return false;
    }

    private void updateTargetIndex(CodeNavigationTarget target, int resultIndex) {
        invalidateStaleTargets();
        if (resultIndex >= 0 && resultIndex < lastTargets.size && lastTargets.get(resultIndex) == target) {
            lastTargetIndex = resultIndex;
            return;
        }
        for (int i = 0; i < lastTargets.size; i++) {
            CodeNavigationTarget candidate = lastTargets.get(i);
            if (candidate.range.equals(target.range)
                && (candidate.documentId == null ? target.documentId == null
                    : candidate.documentId.equals(target.documentId))) {
                lastTargetIndex = i;
                return;
            }
        }
    }

    private void invalidateStaleTargets() {
        if (lastTargetRequest != null && lastTargetRequest.documentVersion != editor.getDocumentVersion()) {
            clearTargets();
        }
    }

    private void clearTargets() {
        lastTargets.clear();
        lastTargetRequest = null;
        lastTargetIndex = -1;
    }

    private boolean canNavigate() {
        return !editor.isDisabled() && !editor.isPasswordMode();
    }

    private boolean canRename() {
        return canNavigate() && !editor.isReadOnly();
    }

    private boolean isValidRange(CodeEditorTextRange range) {
        if (range == null || range.startLine < 0 || range.endLine < range.startLine
            || range.endLine >= editor.getLineCount() || range.startColumn < 0 || range.endColumn < 0
            || range.startColumn > editor.getLineLength(range.startLine)
            || range.endColumn > editor.getLineLength(range.endLine)
            || (range.startLine == range.endLine && range.startColumn > range.endColumn)) {
            return false;
        }
        return isCodePointBoundary(range.startLine, range.startColumn)
            && isCodePointBoundary(range.endLine, range.endColumn);
    }

    private boolean isCodePointBoundary(int line, int column) {
        return column == 0 || column == editor.getLineLength(line)
            || !Character.isHighSurrogate(editor.getCharAt(line, column - 1))
            || !Character.isLowSurrogate(editor.getCharAt(line, column));
    }
}
