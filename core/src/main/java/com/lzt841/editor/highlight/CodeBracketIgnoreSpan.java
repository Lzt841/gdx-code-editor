package com.lzt841.editor.highlight;

/** Marks a range where bracket features should be suppressed for language-specific syntax. */
public class CodeBracketIgnoreSpan {
    public final int start;
    public final int end;

    public CodeBracketIgnoreSpan(int start, int end) {
        this.start = start;
        this.end = end;
    }
}
