package com.lzt841.editor.structure;

/**
 * Default brace-based structure provider.
 *
 * <p>Incremental: its only cross-line state is whether a block comment is open, so an edit re-scans
 * forward only until that agrees with what the editor cached, which for a normal keystroke is one line.
 * String and escape state deliberately do not carry across lines, matching the languages this targets.
 */
public class BraceCodeStructureProvider extends AbstractIncrementalStructureProvider {

    /** Inside a {@code /* ... *}{@code /} comment at the start of the line. */
    private static final int STATE_BLOCK_COMMENT = 1;

    /** @return this, so it can be set inline where the provider is constructed */
    public BraceCodeStructureProvider setSymbolProvider(CodeSymbolProvider symbolProvider) {
        this.symbolProvider = symbolProvider;
        return this;
    }

    @Override
    public int analyzeLine(CharSequence line, int startState, CodeStructureLineContext context) {
        boolean inBlockComment = startState == STATE_BLOCK_COMMENT;
        boolean inString = false;
        char stringQuote = 0;
        boolean escaped = false;
        int lineIndex = context.getLineIndex();

        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            char next = i + 1 < line.length() ? line.charAt(i + 1) : 0;

            if (inBlockComment) {
                if (c == '*' && next == '/') {
                    inBlockComment = false;
                    i++;
                }
                continue;
            }
            if (inString) {
                if (escaped) {
                    escaped = false;
                } else if (c == '\\') {
                    escaped = true;
                } else if (c == stringQuote) {
                    inString = false;
                }
                continue;
            }
            if (c == '/' && next == '/') {
                break;
            }
            if (c == '/' && next == '*') {
                inBlockComment = true;
                i++;
                continue;
            }
            if (c == '"' || c == '\'') {
                inString = true;
                stringQuote = c;
                escaped = false;
                continue;
            }
            if (c == '{') {
                // Key unused: a brace block closes on the matching brace, not on a measurement.
                context.openBlock(0);
                continue;
            }
            if (c == '}') {
                // Closes on this line, unlike an indentation-based block.
                context.closeBlock(lineIndex);
            }
        }

        return inBlockComment ? STATE_BLOCK_COMMENT : START_STATE;
    }

    // No finish(): an unclosed brace is a syntax error, and folding from its opening line to the end of
    // the file would fold the wrong range, so the block is left open and produces no region.
}
