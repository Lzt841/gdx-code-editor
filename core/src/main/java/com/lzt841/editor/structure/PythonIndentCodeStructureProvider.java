package com.lzt841.editor.structure;

import com.badlogic.gdx.utils.Array;

/** Python-oriented structure provider based on indentation and ':' block starters. */
public class PythonIndentCodeStructureProvider implements CodeStructureProvider {
    @Override
    public CodeStructureInfo analyze(Array<String> lines) {
        int[] indentLevels = new int[lines.size];
        Array<CodeFoldRegion> regions = new Array<>();
        Array<IndentFrame> stack = new Array<>();
        PythonScanState state = new PythonScanState();
        int lastContentLine = -1;

        for (int lineIndex = 0; lineIndex < lines.size; lineIndex++) {
            String line = lines.get(lineIndex);
            int indentWidth = countIndentWidth(line);
            PythonLineInfo info = analyzeLine(line, state);

            if (info.meaningful) {
                while (stack.size > 0 && indentWidth <= stack.peek().indentWidth) {
                    closeFrame(stack.pop(), lastContentLine, regions);
                }
                indentLevels[lineIndex] = stack.size;
                if (info.blockStart) {
                    stack.add(new IndentFrame(lineIndex, indentWidth, stack.size));
                }
                lastContentLine = lineIndex;
            } else {
                indentLevels[lineIndex] = stack.size;
            }
        }

        while (stack.size > 0) {
            closeFrame(stack.pop(), lastContentLine, regions);
        }

        return new CodeStructureInfo(indentLevels, regions);
    }

    private void closeFrame(IndentFrame frame, int endLine, Array<CodeFoldRegion> regions) {
        if (endLine > frame.startLine) {
            regions.add(new CodeFoldRegion(frame.startLine, endLine, frame.depth));
        }
    }

    private int countIndentWidth(String line) {
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

    private PythonLineInfo analyzeLine(String line, PythonScanState state) {
        PythonLineInfo info = new PythonLineInfo();
        int index = 0;

        while (index < line.length()) {
            if (state.inTripleString) {
                int end = line.indexOf(state.tripleDelimiter, index);
                if (end < 0) {
                    return info;
                }
                index = end + state.tripleDelimiter.length();
                state.inTripleString = false;
                state.tripleDelimiter = null;
                continue;
            }

            char current = line.charAt(index);
            if (Character.isWhitespace(current)) {
                index++;
                continue;
            }
            if (current == '#') {
                return info;
            }

            int prefixLength = getStringPrefixLength(line, index);
            if (prefixLength >= 0) {
                int quoteIndex = index + prefixLength;
                char quote = line.charAt(quoteIndex);
                info.meaningful = true;
                if (quoteIndex + 2 < line.length()
                    && line.charAt(quoteIndex + 1) == quote
                    && line.charAt(quoteIndex + 2) == quote) {
                    String delimiter = new String(new char[] {quote, quote, quote});
                    int end = line.indexOf(delimiter, quoteIndex + 3);
                    if (end < 0) {
                        state.inTripleString = true;
                        state.tripleDelimiter = delimiter;
                        return info;
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
        return info;
    }

    private int getStringPrefixLength(String line, int start) {
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

    private boolean isIdentifierBoundary(String line, int index) {
        if (index <= 0 || index > line.length()) {
            return true;
        }
        char previous = line.charAt(index - 1);
        return !Character.isLetterOrDigit(previous) && previous != '_';
    }

    private int readSingleQuotedString(String line, int start, char quote) {
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

    private static final class IndentFrame {
        final int startLine;
        final int indentWidth;
        final int depth;

        IndentFrame(int startLine, int indentWidth, int depth) {
            this.startLine = startLine;
            this.indentWidth = indentWidth;
            this.depth = depth;
        }
    }

    private static final class PythonScanState {
        boolean inTripleString;
        String tripleDelimiter;
    }

    private static final class PythonLineInfo {
        boolean meaningful;
        boolean blockStart;
        char lastCodeChar;
    }
}
