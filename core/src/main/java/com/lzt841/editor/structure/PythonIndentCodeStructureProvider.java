package com.lzt841.editor.structure;

/**
 * Python-oriented structure provider based on indentation and ':' block starters.
 *
 * <p>Incremental: its only cross-line state is which triple-quoted string is open, if any. The indent
 * widths that decide where a block ends live in the block stack, which the caller owns, so an edit above
 * a block does not have to be understood by this class.
 */
public class PythonIndentCodeStructureProvider extends AbstractIncrementalStructureProvider {

    /** Inside a {@code """} string at the start of the line. */
    private static final int STATE_TRIPLE_DOUBLE = 1;
    /** Inside a {@code '''} string at the start of the line. */
    private static final int STATE_TRIPLE_SINGLE = 2;

    /** Reused per line; the scan is single-threaded and the object never escapes. */
    private final PythonLineInfo scratchInfo = new PythonLineInfo();

    /** @return this, so it can be set inline where the provider is constructed */
    public PythonIndentCodeStructureProvider setSymbolProvider(CodeSymbolProvider symbolProvider) {
        this.symbolProvider = symbolProvider;
        return this;
    }

    @Override
    public int analyzeLine(CharSequence line, int startState, CodeStructureLineContext context) {
        int indentWidth = countIndentWidth(line);
        PythonLineInfo info = scratchInfo;
        info.reset();
        int endState = scanLine(line, startState, info);

        if (info.meaningful) {
            // A line at or left of the block's own indent ends it, and it ends at the last line that had
            // content — not at this one, which belongs to the enclosing block.
            while (context.getBlockDepth() > 0
                && indentWidth <= context.getBlockKey(context.getBlockDepth() - 1)) {
                context.closeBlock(context.getLastContentLine());
            }
            // After the pops, so a de-denting line reports the depth it lands at rather than the one it
            // came from.
            context.setIndentLevel(context.getBlockDepth());
            if (info.blockStart) {
                context.openBlock(indentWidth);
            }
            // After the pops above, which need the previous content line as their end.
            context.markContentLine();
        }
        return endState;
    }

    @Override
    public void finish(int endState, CodeStructureLineContext context) {
        // Unlike a brace block, an indentation block genuinely ends when the file does.
        while (context.getBlockDepth() > 0) {
            context.closeBlock(context.getLastContentLine());
        }
    }

    private int countIndentWidth(CharSequence line) {
        int width = 0;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == ' ') {
                width++;
            } else if (c == '\t') {
                width += 4;
            } else {
                break;
            }
        }
        return width;
    }

    /** Classifies one line into {@code info} and returns the state the next line starts in. */
    private int scanLine(CharSequence line, int startState, PythonLineInfo info) {
        int state = startState;
        int index = 0;

        while (index < line.length()) {
            if (state != START_STATE) {
                char delimiter = state == STATE_TRIPLE_DOUBLE ? '"' : '\'';
                int end = indexOfTriple(line, delimiter, index);
                if (end < 0) {
                    return state;
                }
                index = end + 3;
                state = START_STATE;
                continue;
            }

            char current = line.charAt(index);
            if (Character.isWhitespace(current)) {
                index++;
                continue;
            }
            if (current == '#') {
                return state;
            }

            int prefixLength = getStringPrefixLength(line, index);
            if (prefixLength >= 0) {
                int quoteIndex = index + prefixLength;
                char quote = line.charAt(quoteIndex);
                info.meaningful = true;
                if (quoteIndex + 2 < line.length()
                    && line.charAt(quoteIndex + 1) == quote
                    && line.charAt(quoteIndex + 2) == quote) {
                    int end = indexOfTriple(line, quote, quoteIndex + 3);
                    if (end < 0) {
                        return quote == '"' ? STATE_TRIPLE_DOUBLE : STATE_TRIPLE_SINGLE;
                    }
                    index = end + 3;
                    continue;
                }
                index = readSingleQuotedString(line, quoteIndex, quote);
                continue;
            }

            info.meaningful = true;
            info.lastCodeChar = current;
            index++;
        }

        info.blockStart = info.meaningful && info.lastCodeChar == ':';
        return state;
    }

    /**
     * First index at or after {@code from} where three {@code quote} characters run consecutively, or -1.
     *
     * <p>Hand-written rather than {@code String.indexOf} so the scan accepts a {@link CharSequence} and
     * allocates no delimiter string per line.
     */
    private static int indexOfTriple(CharSequence line, char quote, int from) {
        for (int i = Math.max(0, from); i + 2 < line.length(); i++) {
            if (line.charAt(i) == quote && line.charAt(i + 1) == quote && line.charAt(i + 2) == quote) {
                return i;
            }
        }
        return -1;
    }

    private int getStringPrefixLength(CharSequence line, int start) {
        if (!isIdentifierBoundary(line, start)) {
            return -1;
        }
        int index = start;
        while (index < line.length()) {
            char c = line.charAt(index);
            if (c == '\'' || c == '"') {
                return index - start;
            }
            if ("rRuUbBfF".indexOf(c) < 0 || index - start >= 2) {
                return -1;
            }
            index++;
        }
        return -1;
    }

    private boolean isIdentifierBoundary(CharSequence line, int index) {
        if (index <= 0 || index > line.length()) {
            return true;
        }
        char previous = line.charAt(index - 1);
        return !Character.isLetterOrDigit(previous) && previous != '_';
    }

    private int readSingleQuotedString(CharSequence line, int start, char quote) {
        boolean escaped = false;
        int index = start + 1;
        while (index < line.length()) {
            char c = line.charAt(index);
            if (escaped) {
                escaped = false;
            } else if (c == '\\') {
                escaped = true;
            } else if (c == quote) {
                return index + 1;
            }
            index++;
        }
        return line.length();
    }

    private static final class PythonLineInfo {
        boolean meaningful;
        boolean blockStart;
        char lastCodeChar;

        void reset() {
            meaningful = false;
            blockStart = false;
            lastCodeChar = 0;
        }
    }
}
