package com.lzt841.editor;

import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class CodeEditorPositionTest {
    private CodeEditor editor;

    @Before
    public void createEditor() {
        BitmapFont.BitmapFontData data = new BitmapFont.BitmapFontData();
        data.lineHeight = 16f;
        data.spaceXadvance = 8f;
        CodeEditor.CodeEditorStyle style = new CodeEditor.CodeEditorStyle();
        style.font = new BitmapFont(data, new TextureRegion(), false);
        editor = new CodeEditor(style);
        editor.setText("ab\n\uD83D\uDE42x\nz");
    }

    @Test
    public void mapsOffsetsAndClampsAtEditorBoundary() {
        assertEquals(new CodeEditorPosition(0, 0, 0), editor.toPosition(-1));
        assertEquals(new CodeEditorPosition(0, 2, 2), editor.toPosition(2));
        assertEquals(new CodeEditorPosition(1, 0, 3), editor.toPosition(3));
        assertEquals(new CodeEditorPosition(1, 3, 6), editor.toPosition(6));
        assertEquals(new CodeEditorPosition(2, 1, 8), editor.toPosition(999));
    }

    @Test
    public void setsCursorFromOffsetAndSnapsInsideSurrogatePair() {
        editor.setCursorOffset(4);
        assertEquals(1, editor.getCursorLine());
        assertEquals(2, editor.getCursorColumn());
        assertEquals(5, editor.getCursorOffset());

        editor.setCursorOffset(-1);
        assertEquals(new CodeEditorPosition(0, 0, 0), editor.getCursorPosition());
        editor.setCursorOffset(999);
        assertEquals(new CodeEditorPosition(2, 1, 8), editor.getCursorPosition());
    }
}
