package com.lzt841.editor.hints;

import com.badlogic.gdx.Input;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.lzt841.editor.CodeEditor;
import com.lzt841.editor.CodeEditorCaretListener;
import com.lzt841.editor.CodeEditorInputInterceptor;
import com.lzt841.editor.CodeEditorPosition;
import com.lzt841.editor.CodeEditorTextRange;

/**
 * Shows a parameter hint while the caret sits inside a call's argument list.
 *
 * <p>Finds the enclosing call by scanning back for an unmatched {@code (}, counts top-level commas to
 * get the argument index, and re-asks the provider whenever that index changes. Typing {@code (}
 * opens the hint, {@code )} or leaving the call closes it, and Escape dismisses it.
 *
 * <pre>{@code
 * new CodeSignatureHelpController(editor, myProvider).install();
 * }</pre>
 */
public class CodeSignatureHelpController {
    /** How far back the scan for an enclosing call will look, in lines. */
    private static final int MAX_SCAN_LINES = 200;

    private final CodeEditor editor;
    private final CodeSignatureHelpProvider provider;
    private final CodeHintPanel panel;
    private final Vector2 scratch = new Vector2();
    private final CaretWatcher caretWatcher = new CaretWatcher();
    private final CodeEditorInputInterceptor interceptor = new CodeEditorInputInterceptor() {
        @Override
        public boolean onKeyDown(CodeEditor source, int keycode) {
            if (visible && keycode == Input.Keys.ESCAPE) {
                hide();
                return true;
            }
            return false;
        }

        @Override
        public void afterKeyTyped(CodeEditor source, char character) {
            if (character == '(') {
                refresh();
            } else if (character == ')') {
                refresh();
            }
        }
    };

    private int requestSerial;
    private boolean installed;
    private boolean visible;
    private int shownCallLine = -1;
    private int shownCallColumn = -1;
    private int shownParameter = -1;

    public CodeSignatureHelpController(CodeEditor editor, CodeSignatureHelpProvider provider) {
        this(editor, provider, null);
    }

    public CodeSignatureHelpController(
        CodeEditor editor,
        CodeSignatureHelpProvider provider,
        CodeHintPanel panel
    ) {
        if (editor == null || provider == null) {
            throw new IllegalArgumentException("editor and provider must not be null");
        }
        this.editor = editor;
        this.provider = provider;
        this.panel = panel != null ? panel : new CodeHintPanel(defaultPanelStyle(editor));
    }

    public void install() {
        if (installed) {
            return;
        }
        Stage stage = editor.getStage();
        if (stage != null && panel.getStage() != stage) {
            stage.addActor(panel);
        }
        editor.addCaretListener(caretWatcher);
        editor.addInputInterceptor(interceptor);
        installed = true;
    }

    public void uninstall() {
        if (!installed) {
            return;
        }
        editor.removeCaretListener(caretWatcher);
        editor.removeInputInterceptor(interceptor);
        panel.remove();
        hide();
        installed = false;
    }

    public CodeHintPanel getPanel() {
        return panel;
    }

    public boolean isVisible() {
        return visible;
    }

    public void hide() {
        requestSerial++;
        visible = false;
        shownCallLine = -1;
        shownCallColumn = -1;
        shownParameter = -1;
        panel.setVisible(false);
        panel.setText("");
    }

    /** Re-evaluates the enclosing call at the caret and shows, updates or hides the hint. */
    public void refresh() {
        CallSite site = findEnclosingCall();
        if (site == null) {
            hide();
            return;
        }
        // Same call and same argument: the panel already shows the right thing.
        if (visible
            && site.line == shownCallLine
            && site.column == shownCallColumn
            && site.parameterIndex == shownParameter) {
            reposition(site);
            return;
        }

        final int serial = ++requestSerial;
        final CallSite target = site;
        CodeEditorPosition position = editor.getCursorPosition();
        provider.provideSignatureHelp(
            editor,
            position,
            site.functionName,
            site.parameterIndex,
            new CodeSignatureHelpResponse() {
                private boolean delivered;

                @Override
                public void complete(CodeSignatureHelp help) {
                    if (delivered || serial != requestSerial) {
                        return;
                    }
                    delivered = true;
                    show(help, target);
                }
            }
        );
    }

    private void show(CodeSignatureHelp help, CallSite site) {
        if (help == null || help.label.isEmpty()) {
            hide();
            return;
        }
        Stage stage = editor.getStage();
        if (stage == null) {
            return;
        }
        if (panel.getStage() != stage) {
            stage.addActor(panel);
        }

        String text = help.documentation.isEmpty() ? help.label : help.label + "\n" + help.documentation;
        panel.setText(text, help.activeParameterStart(), help.activeParameterEnd());
        panel.setVisible(true);
        panel.toFront();
        visible = true;
        shownCallLine = site.line;
        shownCallColumn = site.column;
        shownParameter = site.parameterIndex;
        reposition(site);
    }

    /** Anchors the panel above the call's opening parenthesis. */
    private void reposition(CallSite site) {
        Stage stage = editor.getStage();
        if (stage == null) {
            return;
        }
        if (editor.getStagePositionAt(site.line, site.column, scratch) == null) {
            panel.setVisible(false);
            return;
        }
        panel.positionAbove(stage, scratch.x, scratch.y + editor.getLineHeight(), editor.getLineHeight());
    }

    /**
     * Scans back from the caret for an unmatched {@code (}, skipping balanced brackets and anything the
     * highlighter marked as string or comment, and counts top-level commas after it.
     *
     * @return the call site, or null when the caret is not inside a call
     */
    private CallSite findEnclosingCall() {
        int caretLine = editor.getCursorLine();
        int caretColumn = editor.getCursorColumn();
        int depth = 0;
        int commas = 0;
        int firstLine = Math.max(0, caretLine - MAX_SCAN_LINES);

        for (int line = caretLine; line >= firstLine; line--) {
            String text = editor.getLineText(line);
            int from = line == caretLine ? Math.min(caretColumn, text.length()) - 1 : text.length() - 1;
            for (int column = from; column >= 0; column--) {
                if (editor.isInStringOrComment(line, column)) {
                    continue;
                }
                char c = text.charAt(column);
                if (c == ')' || c == ']' || c == '}') {
                    depth++;
                } else if (c == '[' || c == '{') {
                    if (depth == 0) {
                        // A bracket or block opened but never closed: not a call argument list.
                        return null;
                    }
                    depth--;
                } else if (c == '(') {
                    if (depth == 0) {
                        return new CallSite(line, column, commas, identifierBefore(text, column));
                    }
                    depth--;
                } else if (c == ',' && depth == 0) {
                    commas++;
                } else if (c == ';' && depth == 0) {
                    // A statement boundary: stop rather than pairing across statements.
                    return null;
                }
            }
        }
        return null;
    }

    /** The identifier ending at {@code end}, ignoring whitespace between it and the parenthesis. */
    private static String identifierBefore(String text, int end) {
        int index = end - 1;
        while (index >= 0 && Character.isWhitespace(text.charAt(index))) {
            index--;
        }
        int stop = index + 1;
        while (index >= 0 && isIdentifierChar(text.charAt(index))) {
            index--;
        }
        int start = index + 1;
        return start >= stop ? "" : text.substring(start, stop);
    }

    private static boolean isIdentifierChar(char c) {
        return Character.isLetterOrDigit(c) || c == '_' || c == '$' || c == '.';
    }

    private static CodeHintPanel.CodeHintPanelStyle defaultPanelStyle(CodeEditor editor) {
        CodeHintPanel.CodeHintPanelStyle style = CodeHintPanel.styleFrom(editor);
        // Parameter hints invert the emphasis: the signature is dim, the active argument is bright.
        style.textColor.set(editor.getStyle().gutterFontColor);
        style.highlightColor.set(editor.getStyle().fontColor);
        return style;
    }

    /** Re-evaluates the hint on every caret move, so the active argument tracks the caret. */
    private final class CaretWatcher implements CodeEditorCaretListener {
        @Override
        public void onCaretMoved(
            CodeEditor source,
            CodeEditorPosition position,
            CodeEditorTextRange selection,
            boolean causedByEdit
        ) {
            if (selection != null) {
                hide();
                return;
            }
            if (visible) {
                refresh();
            }
        }
    }

    /** An enclosing call: where its '(' is, which argument the caret is in, and the callee name. */
    private static final class CallSite {
        final int line;
        final int column;
        final int parameterIndex;
        final String functionName;

        CallSite(int line, int column, int parameterIndex, String functionName) {
            this.line = line;
            this.column = column;
            this.parameterIndex = parameterIndex;
            this.functionName = functionName;
        }
    }
}
