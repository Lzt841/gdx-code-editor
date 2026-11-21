package com.lzt841.editor;

import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.lzt841.editor.input.CodeEditorInteractionMode;

public final class EditorTestSupport {
    private EditorTestSupport() { }

    public static CodeEditor createEditor(String text) {
        BitmapFont.BitmapFontData data = new BitmapFont.BitmapFontData();
        data.lineHeight = 16f;
        data.spaceXadvance = 8f;
        CodeEditor.CodeEditorStyle style = new CodeEditor.CodeEditorStyle();
        style.font = new BitmapFont(data, new TextureRegion(), false);
        CodeEditor editor = new CodeEditor(style);
        editor.setInteractionMode(CodeEditorInteractionMode.MOUSE);
        editor.setOnscreenKeyboard(visible -> { });
        editor.setSize(640, 480);
        editor.setText(text);
        return editor;
    }

    public static boolean keyDown(CodeEditor editor, int keycode) {
        InputEvent event = new InputEvent();
        event.setType(InputEvent.Type.keyDown);
        event.setKeyCode(keycode);
        editor.fire(event);
        return event.isHandled();
    }

    public static boolean keyTyped(CodeEditor editor, char character) {
        InputEvent event = new InputEvent();
        event.setType(InputEvent.Type.keyTyped);
        event.setCharacter(character);
        editor.fire(event);
        return event.isHandled();
    }

    public static void keyUp(CodeEditor editor, int keycode) {
        InputEvent event = new InputEvent();
        event.setType(InputEvent.Type.keyUp);
        event.setKeyCode(keycode);
        editor.fire(event);
    }
}
