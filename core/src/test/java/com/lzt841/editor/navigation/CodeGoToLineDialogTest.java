package com.lzt841.editor.navigation;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.backends.headless.HeadlessApplication;
import com.badlogic.gdx.backends.headless.HeadlessApplicationConfiguration;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.Group;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.ui.TextField;
import com.badlogic.gdx.scenes.scene2d.utils.BaseDrawable;
import com.badlogic.gdx.scenes.scene2d.utils.FocusListener;
import com.badlogic.gdx.utils.Disposable;
import com.badlogic.gdx.utils.viewport.ScreenViewport;
import com.lzt841.editor.CodeCommandRegistry;
import com.lzt841.editor.CodeEditor;
import com.lzt841.editor.CodeEditorCommand;
import com.lzt841.editor.CodeEditorPosition;
import com.lzt841.editor.CodeEditorTextRange;
import com.lzt841.editor.CodeKeyStroke;
import com.lzt841.editor.CodeKeymap;
import com.lzt841.editor.extensions.CodeGoToLineExtension;
import com.lzt841.editor.navigation.CodeGoToLineDialog.CodeGoToLineDialogStyle;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import java.lang.reflect.Proxy;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import static com.lzt841.editor.EditorTestSupport.createEditor;
import static org.junit.Assert.*;

public class CodeGoToLineDialogTest {
    private static HeadlessApplication application;
    private static GL20 previousGl;
    private static GL20 previousGl20;

    @BeforeClass public static void startLibgdx() {
        previousGl = Gdx.gl;
        previousGl20 = Gdx.gl20;
        application = new HeadlessApplication(new ApplicationAdapter() { }, new HeadlessApplicationConfiguration());
    }

    @AfterClass public static void stopLibgdx() {
        application.exit();
        Gdx.gl = previousGl;
        Gdx.gl20 = previousGl20;
    }

    @Test public void openingPrefillsAndSelectsCaretCoordinatesAndPreservesEditorState() {
        onGdx(f -> {
            f.editor.markSaved();
            assertTrue(f.editor.insertTextAtCursor("!"));
            assertTrue(f.editor.insertTextAtCursor("?"));
            assertTrue(f.editor.undo());
            f.editor.setSelection(2, 1, 1, 2);
            EditorState before = new EditorState(f.editor);
            assertTrue(before.dirty);
            assertEquals(1, before.undoEntries);
            assertEquals(1, before.redoEntries);
            int[] contentChanges = {0};
            f.editor.addContentListener((source, change) -> contentChanges[0]++);

            assertTrue(f.dialog.open());
            assertSame(f.stage, f.dialog.getStage());
            assertSame(f.dialog.getLocationField(), f.stage.getKeyboardFocus());
            assertEquals("2:3", f.dialog.getLocationField().getText());
            assertEquals("2:3", f.dialog.getLocationField().getSelection());
            before.assertUnchanged(f.editor);

            f.dialog.close();
            assertClosed(f.dialog);
            assertSame(f.editor, f.stage.getKeyboardFocus());
            before.assertUnchanged(f.editor);

            f.openAt("3:2");
            assertTrue(f.dialog.jump());
            assertCaret(f.editor, 2, 1);
            assertNull(f.editor.getSelection());
            before.assertContentUnchanged(f.editor);
            assertEquals(0, contentChanges[0]);
        });
    }

    @Test public void acceptsWhitespaceLeadingZerosAndAnOmittedColumn() {
        onGdx(f -> {
            f.openAt(" 002 : 005 ");
            assertTrue(f.dialog.jump());
            assertCaret(f.editor, 1, 4);
            f.openAt("3");
            assertTrue(f.dialog.jump());
            assertCaret(f.editor, 2, 0);
            assertClosed(f.dialog);
        });
    }

    @Test public void emptyAndTrailingEmptyLinesAllowOnlyColumnOne() {
        onGdx(f -> {
            f.editor.setText("");
            f.openAt("1:2");
            assertRejected(f.dialog);
            f.setLocation("1");
            assertTrue(f.dialog.jump());
            assertCaret(f.editor, 0, 0);
            f.editor.setText("a\n");
            f.openAt("2:2");
            assertRejected(f.dialog);
            f.setLocation("2:1");
            assertTrue(f.dialog.jump());
            assertCaret(f.editor, 1, 0);
        });
    }

    @Test public void malformedAndOutOfRangeInputPreservesEditorState() {
        onGdx(f -> {
            f.editor.setSize(200, 64);
            f.editor.setText("head\n{\nsecret\n}\nbody\nmore\nlast");
            assertEquals(1, f.editor.collapseAllFolds());
            f.editor.setSelection(0, 3, 0, 1);
            f.editor.setScrollY(32f);
            EditorState before = new EditorState(f.editor);
            assertTrue(f.dialog.open());
            for (String invalid : new String[] {"", "  ", ":2", "2:", "1::2", "+2", "-2", "0", "1:0", "1:-1",
                "2.0", "1 2", "2e0", "\u0662", "2:\uFF12", "2147483648", "1:2147483648", "8", "1:6"}) {
                f.setLocation(invalid);
                assertTrue(invalid, f.dialog.getGoButton().isDisabled());
                assertRejected(f.dialog);
                before.assertUnchanged(f.editor);
            }
            assertSame(f.dialog.getLocationField(), f.stage.getKeyboardFocus());
        });
    }

    @Test public void utf16ColumnsCountATabAsOneAndSnapASurrogateMidpointForward() {
        onGdx(f -> {
            f.editor.setText("first\n\t\uD83D\uDE42x");
            f.openAt("2:3");
            assertTrue(f.dialog.jump());
            assertCaret(f.editor, 1, 3);
            f.openAt("2:5");
            assertTrue(f.dialog.jump());
            assertCaret(f.editor, 1, 4);
        });
    }

    @Test public void logicalJumpExpandsContainingFoldsAndRevealsWrappedCaret() {
        onGdx(f -> {
            String longLine = new String(new char[120]).replace('\0', 'x');
            f.editor.setSize(200, 88);
            f.editor.setWrapEnabled(true);
            f.editor.setText("start\n{\n  {\n    " + longLine + "\n  }\n}\n{\nunrelated\n}\nend");
            assertEquals(3, f.editor.collapseAllFolds());
            assertTrue(f.editor.isLineHidden(3));
            f.editor.setSelection(0, 0, 0, 2);
            EditorState before = new EditorState(f.editor);
            f.openAt("4:117");
            assertTrue(f.dialog.jump());
            assertCaret(f.editor, 3, 116);
            assertNull(f.editor.getSelection());
            assertFalse(f.editor.isFoldCollapsed(1));
            assertFalse(f.editor.isFoldCollapsed(2));
            assertTrue(f.editor.isFoldCollapsed(6));
            assertTrue(f.editor.getVisualRowCount() > f.editor.getLineCount());
            assertTrue(f.editor.getScrollY() > 0f);
            assertTrue(f.editor.getFirstVisibleDocumentLine() <= 3);
            assertTrue(f.editor.getLastVisibleDocumentLine() >= 3);

            f.editor.setWrapEnabled(false);
            f.editor.setCursorPosition(0, 0);
            f.openAt("4:117");
            assertTrue(f.dialog.jump());
            assertTrue(f.editor.getScrollX() > 0f);
            before.assertContentUnchanged(f.editor);
        });
    }

    @Test public void liveBoundsRefreshDuringActAndSubmission() {
        onGdx(f -> {
            f.openAt("2:5");
            assertFalse(f.dialog.getGoButton().isDisabled());
            f.editor.setText("alpha\nb");
            f.dialog.act(0f);
            assertTrue(f.dialog.getGoButton().isDisabled());
            f.editor.setText("alpha\nbeta");
            f.dialog.act(0f);
            assertFalse(f.dialog.getGoButton().isDisabled());
            assertEquals("2:5", f.dialog.getLocationField().getText());
            f.editor.setText("alpha");
            EditorState afterHostEdit = new EditorState(f.editor);
            assertRejected(f.dialog);
            afterHostEdit.assertUnchanged(f.editor);
        });
    }

    @Test public void enterNumpadEnterAndTypedOnlyEnterSubmitWithoutEditing() {
        onGdx(f -> {
            EditorState before = new EditorState(f.editor);
            for (int key : new int[] {Input.Keys.ENTER, Input.Keys.NUMPAD_ENTER}) {
                f.editor.setCursorPosition(0, 0);
                f.openAt("2:2");
                assertTrue(f.stage.keyDown(key));
                assertTrue(f.dialog.isOpen());
                assertCaret(f.editor, 0, 0);
                assertTrue(f.stage.keyTyped(key == Input.Keys.ENTER ? '\r' : '\n'));
                f.stage.keyUp(key);
                assertClosed(f.dialog);
                assertCaret(f.editor, 1, 1);
            }
            f.openAt("0");
            assertTrue(f.stage.keyTyped('\n'));
            assertTrue(f.dialog.isOpen());
            assertCaret(f.editor, 1, 1);
            f.setLocation("3:3");
            assertTrue(f.stage.keyTyped('\r'));
            assertClosed(f.dialog);
            assertCaret(f.editor, 2, 2);
            before.assertContentUnchanged(f.editor);
        });
    }

    @Test public void escapeAndDisposeRestoreEligibleFocusAndPreserveTheEditor() {
        onGdx(f -> {
            Actor keyboard = f.addActor();
            Actor scroll = f.addActor();
            f.stage.setKeyboardFocus(keyboard);
            f.stage.setScrollFocus(scroll);
            EditorState before = new EditorState(f.editor);
            f.openAt("2:2");
            f.stage.setScrollFocus(f.dialog);
            assertTrue(f.stage.keyDown(Input.Keys.ESCAPE));
            assertClosed(f.dialog);
            assertSame(keyboard, f.stage.getKeyboardFocus());
            assertSame(scroll, f.stage.getScrollFocus());

            assertTrue(f.dialog.open());
            assertTrue(f.dialog.remove());
            assertFalse(f.dialog.remove());
            assertSame(keyboard, f.stage.getKeyboardFocus());

            assertTrue(f.dialog.open());
            f.stage.setScrollFocus(f.dialog);
            f.dialog.dispose();
            f.dialog.dispose();
            assertClosed(f.dialog);
            assertFalse(f.dialog.open());
            assertSame(keyboard, f.stage.getKeyboardFocus());
            assertSame(scroll, f.stage.getScrollFocus());
            before.assertUnchanged(f.editor);
        });
    }

    @Test public void lostFocusClosesWithoutStealingNewFocus() {
        onGdx(f -> {
            Actor host = f.addActor();
            EditorState before = new EditorState(f.editor);
            f.openAt("2:2");
            f.stage.setKeyboardFocus(host);
            f.stage.setScrollFocus(host);
            assertFalse(f.dialog.jump());
            f.dialog.act(0f);
            assertClosed(f.dialog);
            assertSame(host, f.stage.getKeyboardFocus());
            assertSame(host, f.stage.getScrollFocus());
            before.assertUnchanged(f.editor);
        });
    }

    @Test public void aFocusVetoRollsBackOpening() {
        onGdx(f -> {
            f.stage.setScrollFocus(f.editor);
            f.editor.addListener(new FocusListener() {
                @Override public void keyboardFocusChanged(FocusEvent event, Actor actor, boolean focused) {
                    if (!focused && event.getRelatedActor() == f.dialog.getLocationField()) event.cancel();
                }
            });
            assertFalse(f.dialog.open());
            assertClosed(f.dialog);
            assertSame(f.editor, f.stage.getKeyboardFocus());
            assertSame(f.editor, f.stage.getScrollFocus());
        });
    }

    @Test public void focusRestorationThatEditsTheDocumentAbortsTheJump() {
        onGdx(f -> {
            f.openAt("2:2");
            f.editor.addListener(new FocusListener() {
                @Override public void keyboardFocusChanged(FocusEvent event, Actor actor, boolean focused) {
                    if (focused) f.editor.setText("new first\nnew second");
                }
            });
            assertFalse(f.dialog.jump());
            assertClosed(f.dialog);
            assertEquals("new first\nnew second", f.editor.getText());
            assertCaret(f.editor, 0, 0);
        });
    }

    @Test public void reopeningDuringFocusRestorationInvalidatesTheOldJump() {
        for (boolean closeAgain : new boolean[] {false, true}) {
            onGdx(f -> {
                f.editor.setSelection(0, 0, 0, 2);
                EditorState before = new EditorState(f.editor);
                f.openAt("2:2");
                f.editor.addListener(new FocusListener() {
                    private boolean reopened;
                    @Override public void keyboardFocusChanged(FocusEvent event, Actor actor, boolean focused) {
                        if (focused && !reopened) {
                            reopened = true;
                            assertTrue(f.dialog.open());
                            if (closeAgain) f.dialog.close();
                        }
                    }
                });
                assertFalse(f.dialog.jump());
                assertEquals(!closeAgain, f.dialog.isOpen());
                assertSame(closeAgain ? f.editor : f.dialog.getLocationField(), f.stage.getKeyboardFocus());
                assertEquals("1:3", f.dialog.getLocationField().getText());
                before.assertUnchanged(f.editor);
            });
        }
    }

    @Test public void unloadingDuringFocusRestorationAbortsTheJump() {
        onGdx(f -> {
            CodeGoToLineExtension extension = new CodeGoToLineExtension(f.style);
            f.editor.getExtensions().install(extension);
            assertTrue(f.editor.getCommands().execute(CodeGoToLineExtension.SHOW));
            CodeGoToLineDialog dialog = extension.getDialog();
            dialog.getLocationField().setText("2:2");
            EditorState before = new EditorState(f.editor);
            f.editor.addListener(new FocusListener() {
                @Override public void keyboardFocusChanged(FocusEvent event, Actor actor, boolean focused) {
                    if (focused) assertTrue(f.editor.getExtensions().uninstall(CodeGoToLineExtension.ID));
                }
            });
            assertFalse(dialog.jump());
            assertClosed(dialog);
            assertTrue(dialog.isDisposed());
            assertNull(f.editor.getCommands().getCommand(CodeGoToLineExtension.SHOW));
            before.assertUnchanged(f.editor);
        });
    }

    @Test public void exactShortcutWorksInReadOnlyModeAndIsReleasedAfterRebindingAndUnload() {
        onGdx(f -> {
            CodeGoToLineExtension extension = new CodeGoToLineExtension(f.style);
            f.editor.getExtensions().install(extension);
            CodeGoToLineDialog dialog = extension.getDialog();
            CodeCommandRegistry commands = f.editor.getCommands();
            assertFalse(commands.executeShortcut(CodeKeyStroke.of(Input.Keys.G)));
            assertFalse(commands.executeShortcut(CodeKeyStroke.ctrlShift(Input.Keys.G)));
            assertFalse(commands.executeShortcut(CodeKeyStroke.ctrlAlt(Input.Keys.G)));
            f.editor.setReadOnly(true);
            f.editor.setKeymap(new CodeKeymap() {
                @Override public boolean controlPressed() { return true; }
            });
            EditorState before = new EditorState(f.editor);
            assertTrue(f.stage.keyDown(Input.Keys.G));
            f.stage.keyUp(Input.Keys.G);
            assertTrue(dialog.isOpen());
            dialog.getLocationField().setText("2:4");
            assertTrue(f.stage.keyTyped('\n'));
            assertCaret(f.editor, 1, 3);
            before.assertContentUnchanged(f.editor);

            CodeKeyStroke rebound = CodeKeyStroke.of(Input.Keys.F6);
            assertTrue(commands.setShortcuts(CodeGoToLineExtension.SHOW, rebound));
            assertFalse(commands.executeShortcut(CodeKeyStroke.ctrl(Input.Keys.G)));
            assertTrue(commands.executeShortcut(rebound));
            assertTrue(f.editor.getExtensions().uninstall(CodeGoToLineExtension.ID));
            assertClosed(dialog);
            assertTrue(dialog.isDisposed());
            assertSame(f.editor, f.stage.getKeyboardFocus());
            assertNull(commands.getCommand(CodeGoToLineExtension.SHOW));
            assertFalse(commands.executeShortcut(rebound));
            commands.register(new CodeEditorCommand("host.reuse", "Reuse shortcut", source -> true), rebound);
            assertTrue(commands.executeShortcut(rebound));
        });
    }

    @Test public void duplicateCommandIdOrShortcutDisposesPartiallyInstalledDialog() {
        onGdx(f -> {
            CodeCommandRegistry commands = f.editor.getCommands();
            CodeKeyStroke shortcut = CodeKeyStroke.ctrl(Input.Keys.G);
            Disposable hostId = commands.register(new CodeEditorCommand(CodeGoToLineExtension.SHOW, "Host", source -> true));
            CodeGoToLineExtension duplicateId = new CodeGoToLineExtension(f.style);
            assertThrows(IllegalArgumentException.class, () -> f.editor.getExtensions().install(duplicateId));
            assertTrue(duplicateId.getDialog().isDisposed());
            assertFalse(f.editor.getExtensions().isInstalled(CodeGoToLineExtension.ID));
            assertTrue(commands.execute(CodeGoToLineExtension.SHOW));
            hostId.dispose();

            commands.register(new CodeEditorCommand("host.go", "Host shortcut", source -> true), shortcut);
            CodeGoToLineExtension duplicateChord = new CodeGoToLineExtension(f.style);
            assertThrows(IllegalArgumentException.class, () -> f.editor.getExtensions().install(duplicateChord));
            assertTrue(duplicateChord.getDialog().isDisposed());
            assertFalse(f.editor.getExtensions().isInstalled(CodeGoToLineExtension.ID));
            assertNull(commands.getCommand(CodeGoToLineExtension.SHOW));
            assertTrue(commands.executeShortcut(shortcut));
        });
    }

    @Test public void disablingHidingOrDetachingTheEditorClosesTheDialog() {
        onGdx(f -> {
            Group container = new Group();
            f.stage.addActor(container);
            container.addActor(f.editor);

            f.openAt("2");
            f.editor.setDisabled(true);
            assertUnavailable(f.dialog);
            assertNull(f.stage.getKeyboardFocus());
            f.editor.setDisabled(false);

            f.openAt("2");
            f.editor.setPasswordMode(true);
            assertUnavailable(f.dialog);
            f.editor.setPasswordMode(false);

            f.openAt("2");
            container.setVisible(false);
            assertUnavailable(f.dialog);
            container.setVisible(true);

            f.openAt("2");
            f.editor.remove();
            assertUnavailable(f.dialog);
            assertNull(f.stage.getKeyboardFocus());
        });
    }

    @Test public void touchButtonsCancelOrJumpAndOutsideTouchCannotReachTheEditor() {
        onGdx(f -> {
            Actor keyboard = f.addActor();
            f.stage.setKeyboardFocus(keyboard);
            f.editor.setSelection(0, 0, 0, 3);
            EditorState before = new EditorState(f.editor);
            assertSame(f.editor, f.stage.hit(1f, 1f, true));
            f.openAt("2:2");
            Actor outsideHit = f.stage.hit(1f, 1f, true);
            assertSame(f.dialog, outsideHit);
            assertTrue(fireTouch(outsideHit, InputEvent.Type.touchDown, new Vector2(1f, 1f)));
            fireTouch(outsideHit, InputEvent.Type.touchUp, new Vector2(1f, 1f));
            before.assertUnchanged(f.editor);

            click(f.dialog, f.dialog.getCancelButton());
            assertClosed(f.dialog);
            assertSame(keyboard, f.stage.getKeyboardFocus());
            before.assertUnchanged(f.editor);

            f.openAt("3:2");
            click(f.dialog, f.dialog.getGoButton());
            assertClosed(f.dialog);
            assertSame(f.editor, f.stage.getKeyboardFocus());
            assertCaret(f.editor, 2, 1);
            assertNull(f.editor.getSelection());
            before.assertContentUnchanged(f.editor);
        });
    }

    private static void assertUnavailable(CodeGoToLineDialog dialog) {
        assertFalse(dialog.jump());
        dialog.act(0f);
        assertClosed(dialog);
        assertFalse(dialog.open());
    }

    private static void click(CodeGoToLineDialog dialog, Actor actor) {
        dialog.validate();
        Vector2 center = actor.localToStageCoordinates(new Vector2(actor.getWidth() / 2f, actor.getHeight() / 2f));
        assertTrue(fireTouch(actor, InputEvent.Type.touchDown, center));
        fireTouch(actor, InputEvent.Type.touchUp, center);
    }

    private static boolean fireTouch(Actor actor, InputEvent.Type type, Vector2 position) {
        InputEvent event = new InputEvent();
        event.setType(type);
        event.setStage(actor.getStage());
        event.setStageX(position.x);
        event.setStageY(position.y);
        event.setPointer(0);
        event.setButton(Input.Buttons.LEFT);
        actor.fire(event);
        return event.isHandled();
    }

    private static void assertRejected(CodeGoToLineDialog dialog) {
        assertFalse(dialog.jump());
        assertTrue(dialog.isOpen());
        assertTrue(dialog.getGoButton().isDisabled());
        assertFalse(dialog.getStatusText().isEmpty());
    }

    private static void assertCaret(CodeEditor editor, int line, int column) {
        assertEquals("caret line", line, editor.getCursorLine());
        assertEquals("caret column", column, editor.getCursorColumn());
    }

    private static void assertClosed(CodeGoToLineDialog dialog) {
        assertFalse(dialog.isOpen());
        assertNull(dialog.getStage());
    }

    private static void onGdx(Consumer<Fixture> assertions) {
        CountDownLatch done = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        application.postRunnable(() -> {
            try (Fixture fixture = new Fixture()) {
                assertions.accept(fixture);
            } catch (Throwable error) {
                failure.set(error);
            } finally {
                done.countDown();
            }
        });
        try {
            assertTrue("headless go-to-line test timed out", done.await(45L, TimeUnit.SECONDS));
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new AssertionError(error);
        }
        Throwable error = failure.get();
        if (error instanceof AssertionError) throw (AssertionError) error;
        if (error != null) throw new AssertionError("Scene2D go-to-line regression", error);
    }

    private static final class Fixture implements AutoCloseable {
        final Stage stage;
        final CodeEditor editor;
        final CodeGoToLineDialogStyle style;
        final CodeGoToLineDialog dialog;

        Fixture() {
            Gdx.gl = Gdx.gl20 = (GL20) Proxy.newProxyInstance(GL20.class.getClassLoader(), new Class<?>[] {GL20.class},
                (proxy, method, arguments) -> null);
            Batch batch = (Batch) Proxy.newProxyInstance(Batch.class.getClassLoader(), new Class<?>[] {Batch.class},
                (proxy, method, arguments) -> null);
            stage = new Stage(new ScreenViewport(), batch);
            stage.getViewport().update(900, 650, true);
            editor = createEditor("alpha\nbeta\ngamma");
            stage.addActor(editor);
            stage.setKeyboardFocus(editor);

            BitmapFont font = editor.getStyle().font;
            font.getData().capHeight = 12f;
            font.getData().descent = -4f;
            TextField.TextFieldStyle field = new TextField.TextFieldStyle();
            field.font = font;
            field.fontColor = Color.WHITE;
            field.background = new BaseDrawable();
            TextButton.TextButtonStyle button = new TextButton.TextButtonStyle();
            button.font = font;
            button.up = new BaseDrawable();
            button.disabledFontColor = Color.GRAY;
            style = new CodeGoToLineDialogStyle(field, button, new Label.LabelStyle(font, Color.WHITE));
            dialog = new CodeGoToLineDialog(editor, style);
        }

        Actor addActor() {
            Actor actor = new Actor();
            stage.addActor(actor);
            return actor;
        }

        void openAt(String location) {
            assertTrue(dialog.open());
            setLocation(location);
        }

        void setLocation(String location) {
            dialog.getLocationField().setText(location);
        }

        @Override public void close() {
            dialog.dispose();
            editor.getExtensions().dispose();
            stage.dispose();
        }
    }

    private static final class EditorState {
        final String text;
        final int version;
        final boolean dirty;
        final int undoEntries;
        final int redoEntries;
        final CodeEditorPosition caret;
        final CodeEditorTextRange selection;
        final float scrollX;
        final float scrollY;
        final int collapsedFolds;

        EditorState(CodeEditor editor) {
            text = editor.getText();
            version = editor.getDocumentVersion();
            dirty = editor.isDirty();
            undoEntries = editor.getUndoEntryCount();
            redoEntries = editor.getRedoEntryCount();
            caret = editor.getCursorPosition();
            selection = editor.getSelection();
            scrollX = editor.getScrollX();
            scrollY = editor.getScrollY();
            collapsedFolds = editor.getCollapsedFoldCount();
        }

        void assertContentUnchanged(CodeEditor editor) {
            assertEquals(text, editor.getText());
            assertEquals(version, editor.getDocumentVersion());
            assertEquals(dirty, editor.isDirty());
            assertEquals(undoEntries, editor.getUndoEntryCount());
            assertEquals(redoEntries, editor.getRedoEntryCount());
        }

        void assertUnchanged(CodeEditor editor) {
            assertContentUnchanged(editor);
            assertEquals(caret, editor.getCursorPosition());
            assertEquals(selection, editor.getSelection());
            assertEquals(scrollX, editor.getScrollX(), 0f);
            assertEquals(scrollY, editor.getScrollY(), 0f);
            assertEquals(collapsedFolds, editor.getCollapsedFoldCount());
        }
    }
}
