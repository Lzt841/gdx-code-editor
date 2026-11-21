package com.lzt841.editor;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class CodeDocumentUnicodeTest {
    @Test
    public void reversedRangeWithInteriorEndpointDeletesWholeCharacter() {
        CodeDocument document = new CodeDocument();
        String original = "a\uD83D\uDE42b\nc";
        document.setText(original);
        document.deleteRange(0, 2, 0, 1);
        assertEquals("ab\nc", document.getText());
        assertTrue(document.undo());
        assertEquals(original, document.getText());
    }

    @Test
    public void textRangeNeverReturnsHalfOfSupplementaryCharacter() {
        CodeDocument document = new CodeDocument();
        String face = "\uD83D\uDE42";
        document.setText("a" + face + "b");

        assertEquals(face, document.getTextRange(0, 1, 0, 2));
        assertEquals("", document.getTextRange(0, 2, 0, 2));
    }

    @Test
    public void cursorPlacementKeepsSupplementaryCharacterIntactOnInsertion() {
        CodeDocument document = new CodeDocument();
        document.setText("\uD83D\uDE42");
        document.moveCursorTo(0, 1);
        document.insertChar('!');
        assertEquals("\uD83D\uDE42!", document.getText());
    }
}
