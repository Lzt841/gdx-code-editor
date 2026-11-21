package com.lzt841.editor;

import java.util.Locale;

/** Manual, non-gating comparison of the previous linear mappings and the indexed mappings. */
public final class CodeDocumentPositionBenchmark {
    private static final int LINE_COUNT = 20_000;
    private static final int QUERY_COUNT = 1_000;
    private static final int MEASUREMENT_ROUNDS = 3;

    private CodeDocumentPositionBenchmark() {
    }

    public static void main(String[] args) {
        CodeDocument document = createDocument();
        int[] offsets = createOffsets(document.getTextLength());

        long linearWarmup = runLinear(document, offsets);
        long indexedWarmup = runIndexed(document, offsets);
        if (linearWarmup != indexedWarmup) {
            throw new AssertionError("Position mapping checksums differ");
        }

        long linearNanos = 0;
        long indexedNanos = 0;
        for (int round = 0; round < MEASUREMENT_ROUNDS; round++) {
            long started = System.nanoTime();
            long linearChecksum = runLinear(document, offsets);
            linearNanos += System.nanoTime() - started;

            started = System.nanoTime();
            long indexedChecksum = runIndexed(document, offsets);
            indexedNanos += System.nanoTime() - started;
            if (linearChecksum != indexedChecksum) {
                throw new AssertionError("Position mapping checksums differ");
            }
        }

        System.out.printf(Locale.ROOT, "Document: %,d lines, %,d offsets, %d rounds%n",
            LINE_COUNT, QUERY_COUNT, MEASUREMENT_ROUNDS);
        System.out.printf(Locale.ROOT, "Previous linear path: %.2f ms total%n", linearNanos / 1_000_000.0);
        System.out.printf(Locale.ROOT, "Fenwick indexed path: %.2f ms total%n", indexedNanos / 1_000_000.0);
        System.out.println("Timing is informational; compare on the same machine and JVM.");
    }

    private static CodeDocument createDocument() {
        StringBuilder text = new StringBuilder(LINE_COUNT * 20);
        for (int line = 0; line < LINE_COUNT; line++) {
            if (line > 0) {
                text.append('\n');
            }
            text.append("line-").append(line).append("-data");
        }
        CodeDocument document = new CodeDocument();
        document.setText(text.toString());
        return document;
    }

    private static int[] createOffsets(int textLength) {
        int[] offsets = new int[QUERY_COUNT];
        for (int index = 0; index < offsets.length; index++) {
            offsets[index] = (int) ((long) (index + 1) * textLength / (offsets.length + 1));
        }
        return offsets;
    }

    private static long runIndexed(CodeDocument document, int[] offsets) {
        long checksum = 0;
        for (int offset : offsets) {
            int line = document.lineAtOffset(offset);
            int column = document.columnAtOffset(offset, line);
            int roundTrip = document.toOffset(line, column);
            checksum += line * 31L + column * 17L + roundTrip;
        }
        return checksum;
    }

    private static long runLinear(CodeDocument document, int[] offsets) {
        long checksum = 0;
        for (int offset : offsets) {
            int line = linearLineAtOffset(document, offset);
            int column = linearColumnAtOffset(document, offset);
            int roundTrip = linearToOffset(document, line, column);
            checksum += line * 31L + column * 17L + roundTrip;
        }
        return checksum;
    }

    private static int linearLineAtOffset(CodeDocument document, int offset) {
        int remaining = Math.max(0, offset);
        for (int line = 0; line < document.getLineCount(); line++) {
            int length = document.getLineLength(line);
            if (remaining <= length) {
                return line;
            }
            remaining -= length + 1;
        }
        return document.getLineCount() - 1;
    }

    private static int linearColumnAtOffset(CodeDocument document, int offset) {
        int remaining = Math.max(0, offset);
        for (int line = 0; line < document.getLineCount(); line++) {
            int length = document.getLineLength(line);
            if (remaining <= length) {
                return remaining;
            }
            remaining -= length + 1;
        }
        return document.getLineLength(document.getLineCount() - 1);
    }

    private static int linearToOffset(CodeDocument document, int line, int column) {
        int safeLine = Math.max(0, Math.min(line, document.getLineCount() - 1));
        int offset = 0;
        for (int index = 0; index < safeLine; index++) {
            offset += document.getLineLength(index) + 1;
        }
        return offset + Math.max(0, Math.min(column, document.getLineLength(safeLine)));
    }
}
