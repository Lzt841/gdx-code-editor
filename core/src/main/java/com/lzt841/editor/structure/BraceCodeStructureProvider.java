package com.lzt841.editor.structure;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

/** Default brace-based structure provider. */
public class BraceCodeStructureProvider implements CodeStructureProvider {
    @Override
    public CodeStructureInfo analyze(List<String> lines) {
        int[] indentLevels = new int[lines.size()];
        ArrayList<CodeFoldRegion> regions = new ArrayList<>();
        ArrayDeque<BraceFrame> stack = new ArrayDeque<>();
        boolean inBlockComment = false;

        for (int lineIndex = 0; lineIndex < lines.size(); lineIndex++) {
            String line = lines.get(lineIndex);
            indentLevels[lineIndex] = stack.size();

            boolean inString = false;
            char stringQuote = 0;
            boolean escaped = false;

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
                    stack.push(new BraceFrame(lineIndex, stack.size()));
                    continue;
                }
                if (c == '}' && !stack.isEmpty()) {
                    BraceFrame frame = stack.pop();
                    if (lineIndex > frame.startLine) {
                        regions.add(new CodeFoldRegion(frame.startLine, lineIndex, frame.depth));
                    }
                }
            }
        }

        return new CodeStructureInfo(indentLevels, regions);
    }

    private static final class BraceFrame {
        final int startLine;
        final int depth;

        BraceFrame(int startLine, int depth) {
            this.startLine = startLine;
            this.depth = depth;
        }
    }
}
