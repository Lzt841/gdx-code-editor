package com.lzt841.editor.render;

import com.lzt841.editor.CodeEditor;

/**
 * Geometry for one visible text segment, valid only during a row renderer callback.
 *
 * <p>The editor reuses this object. Copy any values that need to outlive the callback. Coordinates
 * are in the batch's current draw space, including the editor's position, scroll and zoom. Lines,
 * visual rows, segments and UTF-16 columns are zero-based; the end column is exclusive.
 */
public interface CodeEditorRowRenderContext {
    CodeEditor getEditor();
    int getLine();
    int getVisualRow();
    /** Wrapped segment index, or -1 for the visible closing text of a collapsed fold. */
    int getSegmentIndex();
    int getStartColumn();
    int getEndColumn();
    /** The full logical line, not a substring of the visible segment. */
    String getLineText();
    boolean isFoldSuffix();
    float getTextX();
    float getTextEndX();
    float getRowBottom();
    float getLineHeight();
    float getBaseline();
    /** Content bounds exclude a fixed gutter. They do not include any custom scissor inset. */
    float getContentLeft();
    float getContentRight();
    /**
     * X of a column boundary, using the actual measured tabs, images and wrap indentation.
     * Out-of-segment columns are clamped; a boundary inside a surrogate pair snaps to its end.
     */
    float getColumnX(int column);
}
