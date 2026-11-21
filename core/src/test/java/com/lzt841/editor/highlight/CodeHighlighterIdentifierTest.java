package com.lzt841.editor.highlight;

import com.badlogic.gdx.utils.Array;
import com.lzt841.editor.CodeEditor;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.Arrays;
import java.util.Collection;

import static org.junit.Assert.*;

@RunWith(Parameterized.class)
public class CodeHighlighterIdentifierTest {
    private static final String IDENTIFIERS = "$return \u20ACreturn \u203Freturn "
        + new String(Character.toChars(0x10400)) + "return return\u0301 return$";
    private final IncrementalCodeHighlighter highlighter;
    private final CodeEditor.CodeEditorStyle style = new CodeEditor.CodeEditorStyle();

    @Parameterized.Parameters(name = "{0}")
    public static Collection<Object[]> highlighters() {
        return Arrays.asList(new Object[][] {
            {"Java", new JavaCodeHighlighter()},
            {"JavaScript", new JavaScriptCodeHighlighter()},
            {"Kotlin", new KotlinCodeHighlighter()},
            {"Python", new PythonCodeHighlighter()}
        });
    }

    public CodeHighlighterIdentifierTest(String name, IncrementalCodeHighlighter highlighter) {
        this.highlighter = highlighter;
    }

    @Test(timeout = 10000L)
    public void identifierPrefixesAndSuffixesNeverFreezeOrBecomeStandaloneKeywords() {
        String line = IDENTIFIERS + " return";
        Array<CodeHighlightSpan> spans = new Array<CodeHighlightSpan>();
        Array<CodeBracketIgnoreSpan> ignored = new Array<CodeBracketIgnoreSpan>();
        assertEquals(0, highlighter.highlightLine(line, 0, style, spans, ignored));
        assertEquals(1, spans.size);
        assertEquals(line.length() - 6, spans.first().start);
        assertEquals(line.length(), spans.first().end);
        assertSame(style.keywordColor, spans.first().color);
        assertEquals(0, ignored.size);
    }

    @Test(timeout = 10000L)
    public void stateOnlyPassAlsoConsumesEveryIdentifier() {
        assertEquals(0, highlighter.advanceState(IDENTIFIERS, 0, null));
    }

    @Test(timeout = 10000L)
    public void ordinaryKeywordsAndStringIgnoreRangesArePreserved() {
        String line = "return \"(text)\"";
        Array<CodeHighlightSpan> spans = new Array<CodeHighlightSpan>();
        Array<CodeBracketIgnoreSpan> ignored = new Array<CodeBracketIgnoreSpan>();
        assertEquals(0, highlighter.highlightLine(line, 0, style, spans, ignored));
        assertEquals(2, spans.size);
        assertSame(style.keywordColor, spans.first().color);
        assertSame(style.stringColor, spans.peek().color);
        assertEquals(1, ignored.size);
        assertEquals(7, ignored.first().start);
        assertEquals(line.length(), ignored.first().end);
    }
}
