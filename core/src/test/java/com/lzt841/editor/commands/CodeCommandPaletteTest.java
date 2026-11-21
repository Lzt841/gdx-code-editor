package com.lzt841.editor.commands;

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
import com.badlogic.gdx.utils.Clipboard;
import com.badlogic.gdx.utils.viewport.ScreenViewport;
import com.lzt841.editor.CodeEditor;
import com.lzt841.editor.CodeEditorCommand;
import com.lzt841.editor.CodeEditorTextRange;
import com.lzt841.editor.CodeKeyStroke;
import com.lzt841.editor.commands.CodeCommandPalette.CodeCommandPaletteStyle;
import com.lzt841.editor.extensions.CodeCommandPaletteExtension;
import com.lzt841.editor.extensions.CodeStandardCommandsExtension;
import com.lzt841.editor.extensions.CodeTextEditingExtension;
import com.lzt841.editor.extensions.CodeLineEditingExtension;
import com.lzt841.editor.extensions.CodeCommentEditingExtension;
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

public class CodeCommandPaletteTest {
    private static HeadlessApplication application;
    private static GL20 previousGl;
    private static GL20 previousGl20;

    @BeforeClass public static void startLibgdx() {
        previousGl = Gdx.gl;
        previousGl20 = Gdx.gl20;
        // The headless backend intentionally supplies no OS clipboard. Provide the platform service
        // in memory so the real editor copy/cut/paste path can run without desktop clipboard access.
        Clipboard clipboard = new Clipboard() {
            private String contents = "";
            @Override public String getContents() { return contents; }
            @Override public void setContents(String value) { contents = value == null ? "" : value; }
            @Override public boolean hasContents() { return !contents.isEmpty(); }
        };
        application = new HeadlessApplication(new ApplicationAdapter() { }, new HeadlessApplicationConfiguration()) {
            @Override public Clipboard getClipboard() { return clipboard; }
        };
    }

    @AfterClass public static void stopLibgdx() {
        application.exit();
        Gdx.gl = previousGl;
        Gdx.gl20 = previousGl20;
    }

    private static Object defaults(Class<?> type) {
        if (type == boolean.class) return false;
        if (type == int.class) return 0;
        if (type == long.class) return 0L;
        if (type == float.class) return 0f;
        if (type == double.class) return 0d;
        if (type == short.class) return (short) 0;
        if (type == byte.class) return (byte) 0;
        if (type == char.class) return '\0';
        return null;
    }

    private void onGdx(Consumer<Fixture> assertions) {
        CountDownLatch done = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        application.postRunnable(() -> {
            Fixture fixture = null;
            try {
                fixture = new Fixture();
                assertions.accept(fixture);
            } catch (Throwable throwable) {
                failure.set(throwable);
            } finally {
                try {
                    if (fixture != null) fixture.dispose();
                } catch (Throwable cleanup) {
                    if (failure.get() == null) failure.set(cleanup);
                    else failure.get().addSuppressed(cleanup);
                }
                done.countDown();
            }
        });
        try {
            assertTrue("headless palette test timed out", done.await(45L, TimeUnit.SECONDS));
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AssertionError(exception);
        }
        if (failure.get() != null) throw new AssertionError("Scene2D palette regression", failure.get());
    }

    private static final class Fixture {
        final CodeEditor editor;
        final Stage stage;
        final CodeCommandPaletteStyle style;
        final CodeCommandPalette palette;

        Fixture() {
            Gdx.gl = Gdx.gl20 = (GL20) Proxy.newProxyInstance(GL20.class.getClassLoader(), new Class<?>[] {GL20.class},
                (proxy, method, arguments) -> defaults(method.getReturnType()));
            stage = new Stage(new ScreenViewport(), (Batch) Proxy.newProxyInstance(Batch.class.getClassLoader(),
                new Class<?>[] {Batch.class}, (proxy, method, arguments) -> defaults(method.getReturnType())));
            stage.getViewport().update(900, 650, true);
            editor = createEditor("alpha beta");
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
            button.checked = new BaseDrawable();
            button.disabledFontColor = Color.GRAY;
            style = new CodeCommandPaletteStyle(field, button, new Label.LabelStyle(font, Color.WHITE));
            palette = new CodeCommandPalette(editor, style);
        }

        void register(String id, String label) {
            editor.getCommands().register(new CodeEditorCommand(id, label, "Host", false, null, source -> true));
        }

        void dispose() {
            palette.dispose();
            editor.getExtensions().dispose();
            stage.dispose();
        }
    }

    @Test public void openingPreservesSelectionAndClosingRestoresKeyboardAndScrollFocus() {
        onGdx(f -> {
            f.register("host.run", "Run");
            f.editor.setSelection(0, 2, 0, 7);
            CodeEditorTextRange selection = f.editor.getSelection();
            f.stage.setScrollFocus(f.editor);
            assertTrue(f.palette.open());
            assertSame(f.palette.getQueryField(), f.stage.getKeyboardFocus());
            assertEquals(selection, f.editor.getSelection());
            f.stage.setScrollFocus(f.palette.getResultsPane());
            f.palette.close();
            assertSame(f.editor, f.stage.getKeyboardFocus());
            assertSame(f.editor, f.stage.getScrollFocus());
            assertNull(f.palette.getStage());
            assertEquals(selection, f.editor.getSelection());
        });
    }

    @Test public void searchMatchesCategoryLabelAndIdAndReturnsIndependentSnapshots() {
        onGdx(f -> {
            f.register("host.format", "Format Selection");
            f.register("host.reload", "Reload File");
            assertTrue(f.palette.open("HOST format"));
            assertEquals(1, f.palette.getResultCount());
            assertEquals("host.format", f.palette.getSelectedCommand().id);
            f.palette.getMatches().clear();
            assertEquals(1, f.palette.getResultCount());
            f.palette.setQuery("file");
            assertEquals("host.reload", f.palette.getSelectedCommand().id);
            f.palette.setQuery("no such command");
            assertEquals(0, f.palette.getResultCount());
            assertNull(f.palette.getSelectedCommand());
            assertEquals("No matching commands", f.palette.getStatusText());
        });
    }

    @Test public void unicodeQueriesArePreservedEvenWhenTheUiFontLacksThoseGlyphs() {
        onGdx(f -> {
            f.register("host.format", "\u683c\u5f0f\u5316\u6587\u6863");
            f.register("host.reload", "Reload");
            assertTrue(f.palette.open("\u683c\u5f0f\u5316"));
            assertEquals("\u683c\u5f0f\u5316", f.palette.getQuery());
            assertEquals(1, f.palette.getResultCount());
            assertEquals("host.format", f.palette.getSelectedCommand().id);
        });
    }

    @Test public void arrowAndPageKeysNavigateWhileHomeAndEndRemainQueryEditingKeys() {
        onGdx(f -> {
            for (int index = 0; index < 15; index++) f.register("host." + index, "Action " + index);
            f.palette.open();
            assertTrue(f.stage.keyDown(Input.Keys.DOWN));
            assertEquals(1, f.palette.getSelectedIndex());
            assertTrue(f.stage.keyDown(Input.Keys.UP));
            assertTrue(f.stage.keyDown(Input.Keys.UP));
            assertEquals(14, f.palette.getSelectedIndex());
            f.stage.keyDown(Input.Keys.PAGE_UP);
            assertEquals(6, f.palette.getSelectedIndex());
            f.stage.keyDown(Input.Keys.HOME);
            assertEquals(6, f.palette.getSelectedIndex());
            f.stage.keyDown(Input.Keys.PAGE_DOWN);
            assertEquals(14, f.palette.getSelectedIndex());
            assertTrue(f.palette.getResultsPane().getScrollY() > 0f);
            assertTrue(f.stage.keyDown(Input.Keys.ESCAPE));
            assertFalse(f.palette.isOpen());
            assertSame(f.editor, f.stage.getKeyboardFocus());
        });
    }

    @Test public void enterExecutesExactlyOnceAfterFocusReturnsToEditor() {
        onGdx(f -> {
            int[] calls = {0};
            f.editor.getCommands().register(new CodeEditorCommand("host.run", "Run", source -> {
                assertSame(f.editor, f.stage.getKeyboardFocus());
                calls[0]++;
                return source.insertTextAtCursor("X");
            }));
            f.palette.open();
            assertTrue(f.stage.keyDown(Input.Keys.ENTER));
            f.stage.keyTyped('\r');
            assertEquals(1, calls[0]);
            assertEquals("Xalpha beta", f.editor.getText());
            assertFalse(f.palette.isOpen());
            assertEquals(1, f.editor.getUndoEntryCount());
        });
    }

    @Test public void imeEnterAndClickBothActivateCommands() {
        onGdx(f -> {
            int[] calls = {0};
            f.editor.getCommands().register(new CodeEditorCommand("host.run", "Run", source -> { calls[0]++; return true; }));
            f.palette.open();
            assertTrue(f.stage.keyTyped('\n'));
            assertEquals(1, calls[0]);
            f.palette.open();
            TextButton row = findButton(f.palette, "Run");
            assertNotNull(row);
            click(f.stage, row);
            assertEquals(2, calls[0]);
            assertFalse(f.palette.isOpen());
        });
    }

    @Test public void unavailableCommandStaysOpenAndIsRecheckedBeforeExecution() {
        onGdx(f -> {
            boolean[] enabled = {true};
            int[] calls = {0};
            f.editor.getCommands().register(new CodeEditorCommand("host.run", "Run", "", false,
                source -> enabled[0], source -> { calls[0]++; return true; }));
            f.palette.open();
            enabled[0] = false;
            assertFalse(f.palette.executeSelected());
            assertTrue(f.palette.isOpen());
            assertTrue(f.palette.getStatusText().contains("unavailable"));
            assertEquals(0, calls[0]);
            f.palette.setEnabledOnly(true);
            assertEquals(0, f.palette.getResultCount());
            enabled[0] = true;
            f.palette.act(0.2f);
            assertEquals(1, f.palette.getResultCount());
            assertTrue(f.palette.executeSelected());
            assertEquals(1, calls[0]);
        });
    }

    @Test public void registrationChangesRefreshImmediatelyAndRetainSelectionByIdentity() {
        onGdx(f -> {
            f.register("one", "First");
            Disposable second = f.editor.getCommands().register(new CodeEditorCommand("two", "Second", source -> true));
            f.palette.open();
            f.palette.setSelectedIndex(1);
            f.register("three", "Third");
            f.palette.act(0f);
            assertEquals(3, f.palette.getResultCount());
            assertEquals("two", f.palette.getSelectedCommand().id);
            second.dispose();
            f.palette.act(0f);
            assertEquals(2, f.palette.getResultCount());
            assertNotEquals("two", f.palette.getSelectedCommand().id);
        });
    }

    @Test public void removedAndReusedIdNeverExecutesTheReplacementFromAStaleRow() {
        onGdx(f -> {
            Disposable old = f.editor.getCommands().register(new CodeEditorCommand("host.run", "Old", source -> {
                fail("old command should be gone"); return true;
            }));
            f.palette.open();
            old.dispose();
            int[] calls = {0};
            f.editor.getCommands().register(new CodeEditorCommand("host.run", "New", source -> { calls[0]++; return true; }));
            assertFalse(f.palette.executeSelected());
            assertEquals(0, calls[0]);
            assertTrue(f.palette.isOpen());
            assertEquals("New", f.palette.getSelectedCommand().label);
            assertTrue(f.palette.executeSelected());
            assertEquals(1, calls[0]);
        });
    }

    @Test public void focusRestorationCannotSubstituteAnotherCommandWithTheSameId() {
        onGdx(f -> {
            Disposable old = f.editor.getCommands().register(new CodeEditorCommand("host.run", "Old", source -> {
                fail("command was replaced during focus restoration"); return true;
            }));
            f.palette.open();
            int[] replacements = {0};
            f.editor.addListener(new FocusListener() {
                @Override public void keyboardFocusChanged(FocusEvent event, Actor actor, boolean focused) {
                    if (focused) {
                        old.dispose();
                        f.editor.getCommands().register(new CodeEditorCommand("host.run", "New", source -> {
                            replacements[0]++; return true;
                        }));
                    }
                }
            });
            assertFalse(f.palette.executeSelected());
            assertFalse(f.palette.isOpen());
            assertEquals(0, replacements[0]);
        });
    }

    @Test public void commandThatFocusesAnotherWidgetKeepsThatFocusAfterExecution() {
        onGdx(f -> {
            TextField other = new TextField("", f.style.query);
            f.stage.addActor(other);
            f.editor.getCommands().register(new CodeEditorCommand("host.focus", "Focus", source -> {
                f.stage.setKeyboardFocus(other); return true;
            }));
            f.palette.open();
            assertTrue(f.palette.executeSelected());
            assertSame(other, f.stage.getKeyboardFocus());
        });
    }

    @Test public void externalFocusChangesCloseThePaletteWithoutTakingFocusBack() {
        onGdx(f -> {
            f.palette.open();
            TextField other = new TextField("", f.style.query);
            f.stage.addActor(other);
            f.stage.setKeyboardFocus(other);
            f.palette.act(0f);
            assertFalse(f.palette.isOpen());
            assertSame(other, f.stage.getKeyboardFocus());
        });
    }

    @Test public void outsideClickDismissesModalWithoutChangingEditorSelection() {
        onGdx(f -> {
            f.editor.setSelection(0, 1, 0, 4);
            CodeEditorTextRange selection = f.editor.getSelection();
            f.palette.open();
            Actor target = f.stage.hit(1f, 1f, true);
            assertSame(f.palette, target);
            InputEvent event = touchEvent(f.stage, InputEvent.Type.touchDown, 1f, 1f);
            target.fire(event);
            assertTrue(event.isHandled());
            assertFalse(f.palette.isOpen());
            assertEquals(selection, f.editor.getSelection());
            assertSame(f.editor, f.stage.getKeyboardFocus());
        });
    }

    @Test public void modesAndStageRemovalCloseThePaletteAndDisposalIsTerminal() {
        onGdx(f -> {
            f.palette.open();
            f.editor.setReadOnly(true);
            f.palette.act(0f);
            assertTrue(f.palette.isOpen());
            f.editor.setPasswordMode(true);
            f.palette.act(0f);
            assertFalse(f.palette.isOpen());
            assertFalse(f.palette.open());
            f.editor.setPasswordMode(false);
            f.palette.open();
            f.editor.setDisabled(true);
            f.palette.act(0f);
            assertFalse(f.palette.isOpen());
            f.editor.setDisabled(false);
            f.palette.open();
            f.editor.remove();
            f.palette.act(0f);
            assertFalse(f.palette.isOpen());
            assertFalse(f.palette.open());
            f.stage.addActor(f.editor);
            f.palette.dispose();
            f.palette.dispose();
            assertTrue(f.palette.isDisposed());
            assertFalse(f.palette.open());
        });
    }

    @Test public void resultsScrollAndPaletteFitsNarrowResizedStage() {
        onGdx(f -> {
            for (int index = 0; index < 40; index++) f.register("host.action." + index, "A lengthy action label " + index);
            f.palette.open();
            f.stage.getViewport().update(280, 240, true);
            f.palette.act(0f);
            f.palette.validate();
            assertTrue(f.palette.getX() >= 0f);
            assertTrue(f.palette.getY() >= 0f);
            assertTrue(f.palette.getX() + f.palette.getWidth() <= 280f);
            assertTrue(f.palette.getY() + f.palette.getHeight() <= 240f);
            assertTrue(f.palette.getQueryField().getWidth() <= f.palette.getWidth());
            Actor run = findButton(f.palette, "Run (Enter)");
            assertNotNull(run);
            Vector2 footerBottom = f.palette.stageToLocalCoordinates(run.localToStageCoordinates(new Vector2()));
            assertTrue("footer must stay inside a short window", footerBottom.y >= 0f);
            Vector2 queryTop = f.palette.stageToLocalCoordinates(f.palette.getQueryField().localToStageCoordinates(
                new Vector2(0f, f.palette.getQueryField().getHeight())));
            assertTrue("query must stay inside a short window", queryTop.y <= f.palette.getHeight());
            assertTrue("scrolling results must leave room for controls", f.palette.getResultsPane().getHeight() > 0f);
            f.palette.setSelectedIndex(39);
            assertTrue(f.palette.getResultsPane().getScrollY() > 0f);
        });
    }

    @Test public void rebindingsUpdateShortcutHintsWhileOpen() {
        onGdx(f -> {
            f.editor.getCommands().register(new CodeEditorCommand("host.run", "Run", source -> true),
                CodeKeyStroke.ctrl(Input.Keys.K));
            f.palette.open();
            assertTrue(hasLabel(f.palette, "Ctrl+K"));
            f.editor.getCommands().setShortcuts("host.run", CodeKeyStroke.of(Input.Keys.F6));
            f.palette.act(0f);
            assertTrue(hasLabel(f.palette, "F6"));
            assertFalse(hasLabel(f.palette, "Ctrl+K"));
        });
    }

    @Test public void executionListenerReceivesNoOpResultAfterThePaletteCloses() {
        onGdx(f -> {
            f.editor.getCommands().register(new CodeEditorCommand("host.noop", "No Change", source -> false));
            int[] calls = {0};
            f.palette.setExecutionListener((command, handled) -> {
                assertFalse(f.palette.isOpen());
                assertEquals("host.noop", command.id);
                assertFalse(handled);
                calls[0]++;
            });
            f.palette.open();
            assertFalse(f.palette.executeSelected());
            assertEquals(1, calls[0]);
            assertFalse(f.palette.isOpen());
        });
    }

    @Test public void extensionUnloadsAnOpenPaletteAndReleasesItsReboundShortcut() {
        onGdx(f -> {
            CodeCommandPaletteExtension extension = new CodeCommandPaletteExtension(f.style);
            f.editor.getExtensions().install(extension);
            f.editor.getCommands().setShortcuts(CodeCommandPaletteExtension.SHOW, CodeKeyStroke.of(Input.Keys.F6));
            assertTrue(f.stage.keyDown(Input.Keys.F6));
            assertTrue(extension.getPalette().isOpen());
            f.editor.getExtensions().uninstall(CodeCommandPaletteExtension.ID);
            assertTrue(extension.getPalette().isDisposed());
            assertNull(extension.getPalette().getStage());
            assertSame(f.editor, f.stage.getKeyboardFocus());
            assertFalse(f.editor.getCommands().executeShortcut(CodeKeyStroke.of(Input.Keys.F6)));
        });
    }

    @Test public void commandThatOpensPaletteDoesNotLeaveCharacterSuppressionAfterFocusReturns() {
        onGdx(f -> {
            CodeCommandPaletteExtension extension = new CodeCommandPaletteExtension(f.style);
            f.editor.getExtensions().install(extension);
            f.editor.getCommands().setShortcuts(CodeCommandPaletteExtension.SHOW, CodeKeyStroke.of(Input.Keys.F6));
            assertTrue(f.stage.keyDown(Input.Keys.F6));
            f.stage.keyUp(Input.Keys.F6); // Goes to the query, not the editor's registry.
            extension.getPalette().close();
            assertTrue(f.stage.keyTyped('x')); // Mobile/IME input need not have a keyDown.
            assertEquals("xalpha beta", f.editor.getText());
        });
    }

    @Test public void focusLossAfterAHandledShortcutAlsoClearsCharacterSuppression() {
        onGdx(f -> {
            f.editor.getCommands().register(new CodeEditorCommand("host.run", "Run", source -> true),
                CodeKeyStroke.of(Input.Keys.F6));
            assertTrue(f.stage.keyDown(Input.Keys.F6));
            TextField other = new TextField("", f.style.query);
            f.stage.addActor(other);
            f.stage.setKeyboardFocus(other);
            f.stage.keyUp(Input.Keys.F6);
            f.stage.setKeyboardFocus(f.editor);
            assertTrue(f.stage.keyTyped('x'));
            assertEquals("xalpha beta", f.editor.getText());
        });
    }

    @Test public void failedExtensionInstallRollsBackItsOwnedPalette() {
        onGdx(f -> {
            f.register(CodeCommandPaletteExtension.SHOW, "Host palette command");
            CodeCommandPaletteExtension extension = new CodeCommandPaletteExtension(f.style);
            assertThrows(IllegalArgumentException.class, () -> f.editor.getExtensions().install(extension));
            assertTrue(extension.getPalette().isDisposed());
            assertFalse(extension.getPalette().isOpen());
            assertFalse(f.editor.getExtensions().isInstalled(CodeCommandPaletteExtension.ID));
            assertEquals(1, f.editor.getCommands().size());
        });
    }

    @Test public void conditionThatUnloadsPaletteCannotLeaveFocusOnItsDetachedQuery() {
        onGdx(f -> {
            CodeCommandPaletteExtension extension = new CodeCommandPaletteExtension(f.style);
            f.editor.getExtensions().install(extension);
            f.editor.getCommands().register(new CodeEditorCommand("host.unload", "Unload", "", false,
                source -> { f.editor.getExtensions().uninstall(CodeCommandPaletteExtension.ID); return true; },
                source -> { fail("only availability was queried"); return true; }));
            assertFalse(extension.getPalette().open());
            assertTrue(extension.getPalette().isDisposed());
            assertNull(extension.getPalette().getStage());
            assertSame(f.editor, f.stage.getKeyboardFocus());
        });
    }

    @Test public void cancelledFocusTransferRollsBackOpeningWithoutChangingEditorFocus() {
        onGdx(f -> {
            f.editor.addListener(new FocusListener() {
                @Override public void keyboardFocusChanged(FocusEvent event, Actor actor, boolean focused) {
                    if (!focused) event.cancel();
                }
            });
            assertFalse(f.palette.open());
            assertFalse(f.palette.isOpen());
            assertNull(f.palette.getStage());
            assertSame(f.editor, f.stage.getKeyboardFocus());
        });
    }

    @Test public void textAndClipboardCommandsOperateOnThePreservedEditorSelection() {
        onGdx(f -> {
            f.editor.getExtensions().install(new CodeTextEditingExtension());
            f.editor.getExtensions().install(new CodeStandardCommandsExtension());
            f.editor.setSelection(0, 0, 0, 5);
            f.palette.open("uppercase");
            assertTrue(f.palette.executeSelected());
            assertEquals("ALPHA beta", f.editor.getText());
            assertEquals("ALPHA", f.editor.getSelectionText());
            f.palette.open("clipboard copy");
            assertTrue(f.palette.executeSelected());
            assertEquals("ALPHA", Gdx.app.getClipboard().getContents());
            f.palette.open("clipboard cut");
            assertTrue(f.palette.executeSelected());
            assertEquals(" beta", f.editor.getText());
            f.palette.open("clipboard paste");
            assertTrue(f.palette.executeSelected());
            assertEquals("ALPHA beta", f.editor.getText());
        });
    }

    @Test public void lineCommandExecutesOnThePreservedReversedSelectionAfterFocusReturns() {
        onGdx(f -> {
            f.editor.setText("a\nbeta\ngamma\nz");
            f.editor.setSelection(2, 3, 1, 1);
            f.editor.getExtensions().install(new CodeLineEditingExtension());
            assertTrue(f.palette.open("lines duplicate down"));
            assertEquals(1, f.palette.getResultCount());
            assertTrue(f.palette.executeSelected());
            assertFalse(f.palette.isOpen());
            assertSame(f.editor, f.stage.getKeyboardFocus());
            assertEquals("a\nbeta\ngamma\nbeta\ngamma\nz", f.editor.getText());
            assertEquals(new CodeEditorTextRange(3, 1, 4, 3), f.editor.getSelection());
            assertEquals(3, f.editor.getCursorLine());
            assertEquals(1, f.editor.getCursorColumn());
            assertEquals(1, f.editor.getUndoEntryCount());
            assertTrue(f.editor.undo());
            assertEquals("a\nbeta\ngamma\nz", f.editor.getText());
        });
    }

    @Test public void lineCommentCommandKeepsTheReversedSelectionAfterPaletteFocusRestoration() {
        onGdx(f -> {
            f.editor.setText("alpha\nbeta\ngamma");
            f.editor.setSelection(1, 3, 0, 1);
            f.editor.getExtensions().install(new CodeCommentEditingExtension());
            assertTrue(f.palette.open("comments toggle line"));
            assertEquals(1, f.palette.getResultCount());
            assertTrue(f.palette.executeSelected());
            assertSame(f.editor, f.stage.getKeyboardFocus());
            assertEquals("// alpha\n// beta\ngamma", f.editor.getText());
            assertEquals(new CodeEditorTextRange(0, 4, 1, 6), f.editor.getSelection());
            assertEquals(0, f.editor.getCursorLine());
            assertEquals(4, f.editor.getCursorColumn());
            assertEquals(1, f.editor.getUndoEntryCount());
            assertTrue(f.editor.undo());
            assertEquals("alpha\nbeta\ngamma", f.editor.getText());
        });
    }

    private static TextButton findButton(Actor actor, String text) {
        if (actor instanceof TextButton && ((TextButton) actor).getText().toString().equals(text)) return (TextButton) actor;
        if (actor instanceof Group) {
            for (Actor child : ((Group) actor).getChildren()) {
                TextButton found = findButton(child, text);
                if (found != null) return found;
            }
        }
        return null;
    }

    private static boolean hasLabel(Actor actor, String text) {
        if (actor instanceof Label && ((Label) actor).getText().toString().equals(text)) return true;
        if (actor instanceof Group) {
            for (Actor child : ((Group) actor).getChildren()) if (hasLabel(child, text)) return true;
        }
        return false;
    }

    private static InputEvent touchEvent(Stage stage, InputEvent.Type type, float x, float y) {
        InputEvent event = new InputEvent();
        event.setType(type);
        event.setStage(stage);
        event.setStageX(x);
        event.setStageY(y);
        event.setPointer(0);
        event.setButton(Input.Buttons.LEFT);
        return event;
    }

    private static void click(Stage stage, Actor actor) {
        Vector2 position = actor.localToStageCoordinates(new Vector2(actor.getWidth() / 2f, actor.getHeight() / 2f));
        actor.fire(touchEvent(stage, InputEvent.Type.touchDown, position.x, position.y));
        actor.fire(touchEvent(stage, InputEvent.Type.touchUp, position.x, position.y));
    }
}
