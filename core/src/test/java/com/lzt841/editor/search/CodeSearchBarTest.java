package com.lzt841.editor.search;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.backends.headless.HeadlessApplication;
import com.badlogic.gdx.backends.headless.HeadlessApplicationConfiguration;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.Group;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.ui.TextField;
import com.lzt841.editor.CodeEditor;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.viewport.ScreenViewport;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import java.lang.reflect.Proxy;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class CodeSearchBarTest {
    private static HeadlessApplication application;
    private static GL20 previousGl;
    private static GL20 previousGl20;

    @BeforeClass
    public static void startLibgdx() {
        previousGl = Gdx.gl;
        previousGl20 = Gdx.gl20;
        application = new HeadlessApplication(new ApplicationAdapter() { }, new HeadlessApplicationConfiguration());
    }

    @AfterClass
    public static void stopLibgdx() {
        application.exit();
        Gdx.gl = previousGl;
        Gdx.gl20 = previousGl20;
    }

    @Test
    public void queryStaysInSyncWithEditorAndInputField() {
        // HeadlessApplication installs libGDX's graphics, input and clipboard services on its
        // application thread. Run Scene2D construction and assertions there as well.
        CountDownLatch done = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        application.postRunnable(() -> {
            try {
                verifyQuerySynchronization();
            } catch (Throwable throwable) {
                failure.set(throwable);
            } finally {
                done.countDown();
            }
        });
        try {
            // Native startup and Scene2D class loading can exceed five seconds on a busy host.
            assertTrue("headless test timed out", done.await(30L, TimeUnit.SECONDS));
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AssertionError("interrupted while waiting for the headless test", exception);
        }
        Throwable throwable = failure.get();
        if (throwable != null) {
            throw throwable instanceof AssertionError
                ? (AssertionError) throwable
                : new AssertionError("headless query synchronization failed", throwable);
        }
    }

    private void verifyQuerySynchronization() {
        Gdx.gl = Gdx.gl20 = (GL20) Proxy.newProxyInstance(
            GL20.class.getClassLoader(), new Class<?>[]{GL20.class}, (proxy, method, arguments) -> {
                Class<?> type = method.getReturnType();
                if (type == boolean.class) return false;
                if (type == byte.class || type == short.class || type == int.class || type == long.class) return 0;
                if (type == float.class || type == double.class) return 0f;
                if (type == char.class) return '\0';
                return null;
            }
        );
        BitmapFont.BitmapFontData fontData = new BitmapFont.BitmapFontData();
        fontData.lineHeight = 16f;
        fontData.spaceXadvance = 8f;
        BitmapFont font = new BitmapFont(fontData, new TextureRegion(), false);
        CodeEditor.CodeEditorStyle editorStyle = new CodeEditor.CodeEditorStyle();
        editorStyle.font = font;
        CodeEditor editor = new CodeEditor(editorStyle);
        editor.setText("alpha beta alpha");
        editor.setSize(640f, 480f);

        TextField.TextFieldStyle fieldStyle = new TextField.TextFieldStyle();
        fieldStyle.font = font;
        fieldStyle.fontColor = Color.WHITE;
        TextButton.TextButtonStyle buttonStyle = new TextButton.TextButtonStyle();
        buttonStyle.font = font;
        Label.LabelStyle labelStyle = new Label.LabelStyle(font, Color.WHITE);
        CodeSearchBar searchBar = new CodeSearchBar(editor, fieldStyle, buttonStyle, labelStyle);
        Stage stage = new Stage(new ScreenViewport(), (Batch) Proxy.newProxyInstance(
            Batch.class.getClassLoader(), new Class<?>[]{Batch.class}, (proxy, method, arguments) -> {
                Class<?> type = method.getReturnType();
                if (type == boolean.class) return false;
                if (type == byte.class || type == short.class || type == int.class || type == long.class) return 0;
                if (type == float.class || type == double.class) return 0f;
                if (type == char.class) return '\0';
                return null;
            }
        ));
        stage.addActor(editor);
        stage.addActor(searchBar);
        stage.setKeyboardFocus(editor);

        assertTrue(searchBar.open(false));
        assertTrue("opening search focuses the query field", stage.getKeyboardFocus() instanceof TextField);
        TextField findField = findTextField(searchBar);
        assertNotNull(findField);
        findField.setProgrammaticChangeEvents(true);
        findField.setText("alpha");
        assertEquals("alpha", editor.getSearchText());
        assertEquals(2, editor.getSearchMatchCount());
        assertTrue(stage.keyTyped('\n'));
        assertEquals(1, editor.getCurrentSearchMatchOrdinal());

        editor.setSearchText("beta");
        searchBar.act(0f);

        assertEquals("beta", findField.getText());
        assertEquals(1, editor.getSearchMatchCount());

        searchBar.open(true);
        assertEquals(2, countTextFields(searchBar));
        Array<TextField> fields = new Array<>();
        collectTextFields(searchBar, fields);
        assertEquals(fields.get(1), stage.getKeyboardFocus());
        fields.get(1).setText("gamma");
        assertTrue(stage.keyTyped('\n'));
        assertEquals("alpha gamma alpha", editor.getText());
        editor.setReadOnly(true);
        searchBar.act(0f);
        assertEquals(1, countTextFields(searchBar));
        assertEquals(findField, stage.getKeyboardFocus());
        assertEquals("beta", editor.getSearchText());
        stage.dispose();
    }

    private static TextField findTextField(Actor actor) {
        if (actor instanceof TextField) {
            return (TextField) actor;
        }
        if (actor instanceof Group) {
            Array<Actor> children = ((Group) actor).getChildren();
            for (int i = 0; i < children.size; i++) {
                TextField field = findTextField(children.get(i));
                if (field != null) {
                    return field;
                }
            }
        }
        return null;
    }

    private static int countTextFields(Actor actor) {
        int count = actor instanceof TextField ? 1 : 0;
        if (actor instanceof Group) {
            Array<Actor> children = ((Group) actor).getChildren();
            for (int i = 0; i < children.size; i++) {
                count += countTextFields(children.get(i));
            }
        }
        return count;
    }

    private static void collectTextFields(Actor actor, Array<TextField> out) {
        if (actor instanceof TextField) {
            out.add((TextField) actor);
        }
        if (actor instanceof Group) {
            Array<Actor> children = ((Group) actor).getChildren();
            for (int i = 0; i < children.size; i++) {
                collectTextFields(children.get(i), out);
            }
        }
    }
}
