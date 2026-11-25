package com.lzt841.editor.render;

import com.badlogic.gdx.graphics.g2d.Batch;

/**
 * Adds host-defined backgrounds and overlays without replacing the editor's text renderer.
 *
 * <p>Called only for visible rows, in registration order, including wrapped rows, empty lines and
 * visible fold suffixes. Hidden lines and synthetic fold placeholders have no callbacks. All
 * callbacks are suppressed in password mode. The background pass precedes selection and text;
 * the foreground pass follows text, whitespace markers and diagnostics, before the caret/gutter.
 *
 * <p>Draw using the current batch and clip. Do not begin/end the batch, change its transform,
 * projection, shader or blending, or modify the document, layout or settings while drawing; defer
 * edits to the next act. Layer registration changes take effect on the next callback pass. Batch
 * tint and the editor font's color/scale are restored even if a callback throws.
 * Renderer and texture lifetimes belong to the host; remove renderers before disposing resources.
 */
public interface CodeEditorRowRenderer {
    default void drawBackground(Batch batch, CodeEditorRowRenderContext row) { }
    default void drawForeground(Batch batch, CodeEditorRowRenderContext row) { }
}
