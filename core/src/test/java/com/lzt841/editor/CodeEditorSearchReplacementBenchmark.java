package com.lzt841.editor;

import java.util.Arrays;
import java.util.Locale;

import static com.lzt841.editor.EditorTestSupport.createEditor;

/** Manual benchmark for regex replacement expansion across dense matches on one long line. */
public final class CodeEditorSearchReplacementBenchmark {
    private CodeEditorSearchReplacementBenchmark() {
    }

    public static void main(String[] args) {
        int[] matchCounts = {1000, 2000, 4000};
        String replacement = "v=$1;";
        for (int matchCount : matchCounts) {
            StringBuilder text = new StringBuilder(matchCount * 6);
            for (int i = 0; i < matchCount; i++) {
                text.append("v=123;");
            }

            String source = text.toString();
            CodeEditor editor = createEditor(source);
            editor.setSearchRegexEnabled(true);
            editor.setSearchText("v=(\\d+);");
            if (editor.getSearchMatchCount() != matchCount) {
                throw new IllegalStateException("Search did not find all benchmark matches");
            }

            if (editor.replaceAllSearchMatches(replacement) != 0) {
                throw new IllegalStateException("Benchmark replacement unexpectedly changed text");
            }
            long[] samples = new long[5];
            for (int i = 0; i < samples.length; i++) {
                long start = System.nanoTime();
                int changed = editor.replaceAllSearchMatches(replacement);
                samples[i] = System.nanoTime() - start;
                if (changed != 0) {
                    throw new IllegalStateException("Benchmark replacement unexpectedly changed text");
                }
            }
            Arrays.sort(samples);
            System.out.printf(Locale.ROOT, "matches=%d medianMs=%.3f%n",
                matchCount, samples[samples.length / 2] / 1_000_000d);
        }
    }
}
