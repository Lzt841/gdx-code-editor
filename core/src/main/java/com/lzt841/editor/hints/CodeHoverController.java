package com.lzt841.editor.hints;

import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.lzt841.editor.CodeDiagnostic;
import com.lzt841.editor.CodeEditor;
import com.lzt841.editor.CodeEditorHoverListener;
import com.lzt841.editor.CodeEditorPosition;
import com.badlogic.gdx.utils.Array;

/**
 * Shows a tooltip when the pointer rests over a document position.
 *
 * <p>{@link #install()} registers a {@link CodeEditorHoverListener} and adds the panel to the stage:
 *
 * <pre>{@code
 * new CodeHoverController(editor, CodeHoverController.diagnosticProvider()).install();
 * }</pre>
 *
 * <p>With no provider it falls back to {@link #diagnosticProvider()}, so diagnostics get tooltips for
 * free.
 */
public class CodeHoverController {
    private final CodeEditor editor;
    private final CodeHoverProvider provider;
    private final CodeHintPanel panel;
    private final Vector2 scratch = new Vector2();
    private final HoverWatcher watcher = new HoverWatcher();

    private int requestSerial;
    private boolean installed;

    public CodeHoverController(CodeEditor editor) {
        this(editor, diagnosticProvider(), null);
    }

    public CodeHoverController(CodeEditor editor, CodeHoverProvider provider) {
        this(editor, provider, null);
    }

    public CodeHoverController(CodeEditor editor, CodeHoverProvider provider, CodeHintPanel panel) {
        if (editor == null) {
            throw new IllegalArgumentException("editor must not be null");
        }
        this.editor = editor;
        this.provider = provider == null ? diagnosticProvider() : provider;
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
        editor.addHoverListener(watcher);
        installed = true;
    }

    public void uninstall() {
        if (!installed) {
            return;
        }
        editor.removeHoverListener(watcher);
        panel.remove();
        hide();
        installed = false;
    }

    public CodeHintPanel getPanel() {
        return panel;
    }

    public void hide() {
        requestSerial++;
        panel.setVisible(false);
        panel.setText("");
    }

    /**
     * A provider that reports the diagnostics under the pointer, one per line. Useful on its own and
     * as a starting point for a richer provider.
     */
    public static CodeHoverProvider diagnosticProvider() {
        return new CodeHoverProvider() {
            private final Array<CodeDiagnostic> scratchDiagnostics = new Array<>();

            @Override
            public void provideHover(CodeEditor editor, CodeEditorPosition position, CodeHoverResponse response) {
                scratchDiagnostics.clear();
                editor.getDiagnosticsAt(position.line, position.column, scratchDiagnostics);
                if (scratchDiagnostics.size == 0) {
                    response.complete(null);
                    return;
                }
                StringBuilder builder = new StringBuilder();
                for (int i = 0; i < scratchDiagnostics.size; i++) {
                    if (i > 0) {
                        builder.append('\n');
                    }
                    CodeDiagnostic diagnostic = scratchDiagnostics.get(i);
                    builder.append(diagnostic.severity).append(": ").append(diagnostic.message);
                }
                response.complete(builder.toString());
            }
        };
    }

    private static CodeHintPanel.CodeHintPanelStyle defaultPanelStyle(CodeEditor editor) {
        return CodeHintPanel.styleFrom(editor);
    }

    /** Requests hover text when a hover starts, and hides the panel when it ends. */
    private final class HoverWatcher implements CodeEditorHoverListener {
        @Override
        public void onHoverStart(CodeEditor source, CodeEditorPosition position, float localX, float localY) {
            final int serial = ++requestSerial;
            provider.provideHover(source, position, new CodeHoverResponse() {
                private boolean delivered;

                @Override
                public void complete(String text) {
                    if (delivered || serial != requestSerial) {
                        return;
                    }
                    delivered = true;
                    show(text, position);
                }
            });
        }

        @Override
        public void onHoverEnd(CodeEditor source) {
            hide();
        }
    }

    private void show(String text, CodeEditorPosition position) {
        if (text == null || text.isEmpty()) {
            panel.setVisible(false);
            return;
        }
        Stage stage = editor.getStage();
        if (stage == null) {
            return;
        }
        if (panel.getStage() != stage) {
            stage.addActor(panel);
        }
        if (editor.getStagePositionAt(position.line, position.column, scratch) == null) {
            panel.setVisible(false);
            return;
        }
        panel.setText(text);
        panel.setVisible(true);
        panel.toFront();
        // scratch.y is the row bottom, so the row top is one line up.
        panel.positionAbove(stage, scratch.x, scratch.y + editor.getLineHeight(), editor.getLineHeight());
    }
}
