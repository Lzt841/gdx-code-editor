package com.lzt841.editor;

import com.badlogic.gdx.Input;
import org.junit.Before;
import org.junit.Test;

import static com.lzt841.editor.EditorTestSupport.*;
import static org.junit.Assert.*;

public class CodeEditorInputInterceptorTest {
    private CodeEditor editor;
    private StringBuilder calls;

    @Before public void setup() {
        editor = createEditor("");
        calls = new StringBuilder();
    }

    @Test
    public void removingSelfDoesNotSkipFollowingKeyDownInterceptor() {
        editor.addInputInterceptor(new CodeEditorInputInterceptor() {
            @Override public boolean onKeyDown(CodeEditor source, int keycode) {
                calls.append('A');
                source.removeInputInterceptor(this);
                return false;
            }
        });
        editor.addInputInterceptor(new CodeEditorInputInterceptor() {
            @Override public boolean onKeyDown(CodeEditor source, int keycode) { calls.append('B'); return true; }
        });
        assertTrue(keyDown(editor, Input.Keys.F6));
        assertEquals("AB", calls.toString());
        assertTrue(keyDown(editor, Input.Keys.F6));
        assertEquals("ABB", calls.toString());
    }

    @Test
    public void newInterceptorStartsOnNextDispatch() {
        CodeEditorInputInterceptor added = new CodeEditorInputInterceptor() {
            @Override public boolean onKeyDown(CodeEditor source, int keycode) { calls.append('B'); return true; }
        };
        editor.addInputInterceptor(new CodeEditorInputInterceptor() {
            @Override public boolean onKeyDown(CodeEditor source, int keycode) {
                calls.append('A'); source.addInputInterceptor(added); return false;
            }
        });
        assertFalse(keyDown(editor, Input.Keys.F6));
        assertEquals("A", calls.toString());
        assertTrue(keyDown(editor, Input.Keys.F6));
        assertEquals("AAB", calls.toString());
    }

    @Test
    public void nestedDispatchDoesNotOverwriteOuterSnapshot() {
        editor.addInputInterceptor(new CodeEditorInputInterceptor() {
            @Override public boolean onKeyDown(CodeEditor source, int keycode) {
                calls.append('A');
                source.removeInputInterceptor(this);
                keyDown(source, Input.Keys.F7);
                return false;
            }
        });
        editor.addInputInterceptor(new CodeEditorInputInterceptor() {
            @Override public boolean onKeyDown(CodeEditor source, int keycode) { calls.append('B'); return true; }
        });
        assertTrue(keyDown(editor, Input.Keys.F6));
        assertEquals("ABB", calls.toString());
    }

    @Test
    public void keyTypedAndAfterTypedCanUnregisterWithoutSkippingPeers() {
        editor.addInputInterceptor(new CodeEditorInputInterceptor() {
            @Override public boolean onKeyTyped(CodeEditor source, char character) {
                calls.append('A'); source.removeInputInterceptor(this); return false;
            }
        });
        editor.addInputInterceptor(new CodeEditorInputInterceptor() {
            @Override public boolean onKeyTyped(CodeEditor source, char character) { calls.append('B'); return false; }
            @Override public void afterKeyTyped(CodeEditor source, char character) {
                calls.append('C'); source.removeInputInterceptor(this);
            }
        });
        editor.addInputInterceptor(new CodeEditorInputInterceptor() {
            @Override public void afterKeyTyped(CodeEditor source, char character) { calls.append('D'); }
        });
        assertTrue(keyTyped(editor, 'x'));
        assertEquals("ABCD", calls.toString());
        assertEquals("x", editor.getText());
        assertTrue(keyTyped(editor, 'y'));
        assertEquals("ABCDD", calls.toString());
    }

    @Test
    public void throwingCallbackReleasesSnapshotForNextDispatch() {
        editor.addInputInterceptor(new CodeEditorInputInterceptor() {
            @Override public boolean onKeyDown(CodeEditor source, int keycode) {
                source.removeInputInterceptor(this);
                throw new IllegalStateException("callback failed");
            }
        });
        assertThrows(RuntimeException.class, () -> keyDown(editor, Input.Keys.F6));
        editor.addInputInterceptor(new CodeEditorInputInterceptor() {
            @Override public boolean onKeyDown(CodeEditor source, int keycode) { calls.append('B'); return true; }
        });
        assertTrue(keyDown(editor, Input.Keys.F6));
        assertEquals("B", calls.toString());
    }

    @Test
    public void keyReleaseCanUnregisterWithoutSkippingFollowingInterceptor() {
        editor.addInputInterceptor(new CodeEditorInputInterceptor() {
            @Override public void onKeyUp(CodeEditor source, int keycode) {
                calls.append('A'); source.removeInputInterceptor(this);
            }
        });
        editor.addInputInterceptor(new CodeEditorInputInterceptor() {
            @Override public void onKeyUp(CodeEditor source, int keycode) { calls.append('B'); }
        });
        keyUp(editor, Input.Keys.F6);
        keyUp(editor, Input.Keys.F6);
        assertEquals("ABB", calls.toString());
    }
}
