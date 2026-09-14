package com.lzt841.editor.hints;

import com.badlogic.gdx.utils.Array;

/**
 * One function signature plus which parameter is active, as shown by a parameter hint.
 *
 * <p>{@link #label} is the whole signature, for example {@code max(int a, int b)}.
 * {@link #parameterRanges} holds start/end pairs into that label, one per parameter, so the panel can
 * highlight the active one without re-parsing. {@link #parseParenthesised(String, int)} builds those
 * ranges for the common {@code name(a, b, c)} shape.
 */
public class CodeSignatureHelp {
    public final String label;
    /** Start/end pairs into {@link #label}, two ints per parameter. */
    public final int[] parameterRanges;
    /** Index of the active parameter, or -1 for none. */
    public final int activeParameter;
    /** Optional documentation shown under the signature. */
    public final String documentation;

    public CodeSignatureHelp(String label, int[] parameterRanges, int activeParameter, String documentation) {
        this.label = label == null ? "" : label;
        this.parameterRanges = parameterRanges == null ? new int[0] : parameterRanges;
        this.activeParameter = activeParameter;
        this.documentation = documentation == null ? "" : documentation;
    }

    public int getParameterCount() {
        return parameterRanges.length / 2;
    }

    /** Start of the active parameter inside {@link #label}, or -1 when there is none. */
    public int activeParameterStart() {
        int index = activeParameter * 2;
        return isActiveParameterValid() ? parameterRanges[index] : -1;
    }

    /** End of the active parameter inside {@link #label}, or -1 when there is none. */
    public int activeParameterEnd() {
        int index = activeParameter * 2;
        return isActiveParameterValid() ? parameterRanges[index + 1] : -1;
    }

    private boolean isActiveParameterValid() {
        int index = activeParameter * 2;
        return activeParameter >= 0 && index + 1 < parameterRanges.length;
    }

    /** The same signature with a different active parameter. */
    public CodeSignatureHelp withActiveParameter(int index) {
        return new CodeSignatureHelp(label, parameterRanges, index, documentation);
    }

    /**
     * Builds signature help from a label of the form {@code name(a, b, c)}, splitting the parameters
     * on top-level commas so nested parentheses, brackets, angle brackets and quotes are respected.
     *
     * @param activeParameter index to mark active, or -1
     */
    public static CodeSignatureHelp parseParenthesised(String label, int activeParameter) {
        if (label == null || label.isEmpty()) {
            return new CodeSignatureHelp("", new int[0], -1, "");
        }
        int open = label.indexOf('(');
        int close = label.lastIndexOf(')');
        if (open < 0 || close <= open + 1) {
            return new CodeSignatureHelp(label, new int[0], -1, "");
        }

        Array<Integer> bounds = new Array<>();
        int depth = 0;
        char quote = 0;
        int segmentStart = open + 1;
        for (int i = open + 1; i < close; i++) {
            char c = label.charAt(i);
            if (quote != 0) {
                if (c == '\\') {
                    i++;
                } else if (c == quote) {
                    quote = 0;
                }
                continue;
            }
            if (c == '"' || c == '\'') {
                quote = c;
            } else if (c == '(' || c == '[' || c == '<') {
                depth++;
            } else if (c == ')' || c == ']' || c == '>') {
                depth--;
            } else if (c == ',' && depth == 0) {
                addTrimmed(bounds, label, segmentStart, i);
                segmentStart = i + 1;
            }
        }
        addTrimmed(bounds, label, segmentStart, close);

        int[] ranges = new int[bounds.size];
        for (int i = 0; i < bounds.size; i++) {
            ranges[i] = bounds.get(i);
        }
        return new CodeSignatureHelp(label, ranges, activeParameter, "");
    }

    /** Appends a parameter range with surrounding whitespace trimmed off. */
    private static void addTrimmed(Array<Integer> bounds, String label, int start, int end) {
        int from = start;
        int to = end;
        while (from < to && Character.isWhitespace(label.charAt(from))) {
            from++;
        }
        while (to > from && Character.isWhitespace(label.charAt(to - 1))) {
            to--;
        }
        if (to > from) {
            bounds.add(from);
            bounds.add(to);
        }
    }

    @Override
    public String toString() {
        return "CodeSignatureHelp{" + label + ", active=" + activeParameter + "}";
    }
}
