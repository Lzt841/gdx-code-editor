package com.lzt841.editor.input;

import com.badlogic.gdx.Gdx;

/** Hook for platform-specific soft keyboard behavior. */
public interface CodeEditorOnscreenKeyboard {
    void show(boolean visible);

    CodeEditorOnscreenKeyboard DEFAULT = new CodeEditorOnscreenKeyboard() {
        @Override
        public void show(boolean visible) {
            Gdx.input.setOnscreenKeyboardVisible(visible);
        }
    };
}
