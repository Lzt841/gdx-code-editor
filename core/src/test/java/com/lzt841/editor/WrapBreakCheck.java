package com.lzt841.editor;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Standalone invariant check for the shared wrap-break helpers. Not a JUnit test (there is no test
 * runtime on the verifiable classpath here); it is a main() over pure static functions only and never
 * touches a display, a font, or the editor itself.
 *
 * <p>What it proves, in order: (1) the ASCII-pair rule is the old predicate verbatim, over every ASCII
 * adjacent pair; (2) no accepted boundary splits a surrogate pair; (3) a mirror of countWrapRows's loop
 * and a mirror of wrapLine's loop, both driven by the same helpers, agree on every segment boundary for
 * ASCII, CJK and mixed inputs; (4) kinsoku: a row never opens on a full-width non-starter, except in the
 * degenerate case where the row is a single forced column.
 */
public class WrapBreakCheck {

    static int failures = 0;

    static void check(String name, boolean cond) {
        if (!cond) {
            failures++;
            System.out.println("FAIL " + name);
        }
    }

    /** The pre-change predicate, kept here so the ASCII guarantee is measured rather than asserted. */
    static boolean oldPredicate(char c) {
        return Character.isWhitespace(c) || ",.;:+-*/=%&|!?)>]}".indexOf(c) >= 0;
    }

    /** Width model: full-width columns 10, narrow ASCII 6, an astral pair 20 (image-like, low = 0). */
    static final class Text {
        final String s;
        final double[] w;

        Text(String s) {
            this.s = s;
            this.w = new double[s.length()];
            for (int i = 0; i < s.length(); i++) {
                char c = s.charAt(i);
                if (Character.isHighSurrogate(c) && i + 1 < s.length()
                    && Character.isLowSurrogate(s.charAt(i + 1))) {
                    w[i] = 20.0;
                    w[i + 1] = 0.0;
                    i++;
                } else {
                    w[i] = (c > 0x7F) ? 10.0 : 6.0;
                }
            }
        }

        double rangeWidth(int from, int to) {
            double sum = 0;
            for (int i = from; i < to; i++) {
                sum += w[i];
            }
            return sum;
        }
    }

    static int trim(CharSequence text, int index) {
        int next = index;
        while (next < text.length() && Character.isWhitespace(text.charAt(next)) && text.charAt(next) != '\t') {
            next++;
        }
        return next;
    }

    /** Mirror of countWrapRows: absolute-width accumulation plus a re-walk after the break. */
    static List<int[]> countMirror(Text t, double wrapWidth, double indent) {
        List<int[]> segs = new ArrayList<>();
        int segmentStart = 0;
        double segStartWidth = 0;
        int rows = 0;
        while (segmentStart < t.s.length()) {
            rows++;
            double available = rows > 1 ? Math.max(1.0, wrapWidth - indent) : wrapWidth;
            int bestBreak = -1;
            int index = segmentStart;
            double absoluteWidth = segStartWidth;
            while (index < t.s.length()) {
                absoluteWidth += t.w[index];
                if (absoluteWidth - segStartWidth > available) {
                    break;
                }
                if (CodeEditor.breakAllowedBefore(t.s, index + 1)) {
                    bestBreak = index + 1;
                }
                index++;
            }
            if (index >= t.s.length()) {
                segs.add(new int[] {segmentStart, t.s.length()});
                return segs;
            }
            int breakIndex = CodeEditor.resolveWrapBreak(t.s, segmentStart, index, bestBreak);
            int nextStart = trim(t.s, breakIndex);
            segs.add(new int[] {segmentStart, breakIndex});
            segStartWidth += t.rangeWidth(segmentStart, nextStart);
            segmentStart = nextStart;
        }
        return segs;
    }

    /** Mirror of wrapLine: prefix widths, exactly as LineLayout.measureRange does. */
    static List<int[]> wrapMirror(Text t, double wrapWidth, double indent) {
        double[] prefix = new double[t.s.length() + 1];
        for (int i = 0; i < t.s.length(); i++) {
            prefix[i + 1] = prefix[i] + t.w[i];
        }
        List<int[]> segs = new ArrayList<>();
        int segmentStart = 0;
        int rows = 0;
        while (segmentStart < t.s.length()) {
            rows++;
            double available = rows > 1 ? Math.max(1.0, wrapWidth - indent) : wrapWidth;
            int bestBreak = -1;
            int index = segmentStart;
            while (index < t.s.length()) {
                double segmentWidth = prefix[index + 1] - prefix[segmentStart];
                if (segmentWidth > available) {
                    break;
                }
                if (CodeEditor.breakAllowedBefore(t.s, index + 1)) {
                    bestBreak = index + 1;
                }
                index++;
            }
            if (index >= t.s.length()) {
                segs.add(new int[] {segmentStart, t.s.length()});
                return segs;
            }
            int breakIndex = CodeEditor.resolveWrapBreak(t.s, segmentStart, index, bestBreak);
            segs.add(new int[] {segmentStart, breakIndex});
            segmentStart = trim(t.s, breakIndex);
        }
        return segs;
    }

    public static void main(String[] args) {
        // (1) ASCII-pair equivalence with the old predicate, over every printable ASCII pair.
        for (int a = 1; a < 0x80; a++) {
            for (int b = 1; b < 0x80; b++) {
                String s = "" + (char) a + (char) b;
                check("ascii-pair " + a + "/" + b,
                    CodeEditor.breakAllowedBefore(s, 1) == oldPredicate((char) a));
            }
        }

        // (2) Surrogate safety: no accepted boundary of any astral-containing string splits a pair.
        String[] astral = {"😀汉字𝄞", "a😀b", "😀😀", "汉字😀",
            "x😀", "😀y", "𝄞music𝄞"};
        for (String s : astral) {
            for (int k = 1; k < s.length(); k++) {
                if (CodeEditor.breakAllowedBefore(s, k)) {
                    char p = s.charAt(k - 1);
                    char n = s.charAt(k);
                    check("surrogate split k=" + k + " in " + s,
                        !(Character.isHighSurrogate(p) && Character.isLowSurrogate(n)));
                }
            }
        }

        // (3) count/wrap agreement and (4) kinsoku, across widths and indents.
        // The shelter directly: a row that fits exactly four full-width chars leaves bestBreak at the
        // column of the 。, so without the shelter the next row would open on it. bestBreak is what the
        // per-char veto already accepted, so only the shelter can rescue it.
        String sheltered = "汉字汉字。更多文字";
        check("kinsoku shelter pulls the 。 onto the current row",
            CodeEditor.resolveWrapBreak(sheltered, 0, 4, 4) == 5);
        check("kinsoku shelter is a no-op when the next row already starts cleanly",
            CodeEditor.resolveWrapBreak("汉字汉字更多文字", 0, 4, 4) == 4);
        String[] lines = {
            "public static void main(String[] args) { int x = a + b * c; }",
            "int[] arr = new int[]{1, 2, 3}; foo.bar(arr[0], arr[1]);",
            "这是一个纯粹的中文行没有任何空格所以它必须能够在任意两个汉字之间换行",
            "调用processDocument方法之后需要检查返回值是否为null",
            "中文 mixed with English words and 中文 again in the same line",
            "标点符号测试：中文，中文。中文、中文；中文：中文！中文？",
            "括号测试（中文括号）【方括号】「角括号」《书名号》",
            "foo(bar), baz[0] {qux}; a.b.c.d.e.f.g.h.i.j.k.l.m.n.o.p",
            "引号“测试”和‘单引号’以及波浪号～结束",
            "😀 emoji 在中文里也要换行得对才行这是一个很长的行",
            "café naïve résumé über", "ＡＢＣ１２３全角字母数字也应该保持完整",
            "a", " ", "　", "。", "（（（（", "。。。。", "x,）", "（y",
            "trim trailing spaces then break   。 here",
            "a长的英文单词后面跟着中文比如processDocument方法调用"
        };
        double[] widths = {7, 10, 12, 13, 16, 20, 23, 25, 30, 40, 60, 80, 100, 200};
        double[] indents = {0, 4, 10, 13};
        for (String line : lines) {
            Text t = new Text(line);
            for (double ww : widths) {
                for (double ind : indents) {
                    List<int[]> c = countMirror(t, ww, ind);
                    List<int[]> w = wrapMirror(t, ww, ind);
                    check("row-count [" + line + "] w=" + ww + " i=" + ind, c.size() == w.size());
                    for (int i = 0; i < Math.min(c.size(), w.size()); i++) {
                        check("segment [" + line + "] w=" + ww + " i=" + ind + " seg " + i,
                            c.get(i)[0] == w.get(i)[0] && c.get(i)[1] == w.get(i)[1]);
                    }
                    // (4) No row may open on a full-width non-starter after indent trimming, unless the
                    //     row is the single forced column of a degenerate row.
                    for (int[] seg : c) {
                        if (seg[1] - seg[0] <= 1) {
                            continue;
                        }
                        // 禁則 governs only the breaks the editor places: the first row of a line starts
                        // wherever the line itself starts, so a line opening on a 。 or a ） is the
                        // author's, not the wrapper's. Exempting seg[0] == 0 is what stops that from
                        // reading as a regression here.
                        if (seg[0] == 0) {
                            continue;
                        }
                        int start = trim(line, seg[0]);
                        if (start < line.length() && CodeEditor.isNoStartRowPunct(line.charAt(start))) {
                            check("kinsoku start [" + line + "] w=" + ww + " seg=" + seg[0] + "," + seg[1], false);
                        }
                    }
                }
            }
        }

        // (5) The class loaded, so the static guard passed: both literals are all above 0x7F and in-band.
        for (int i = 0; i < CodeEditor.NO_START_ROW_PUNCT.length(); i++) {
            char ch = CodeEditor.NO_START_ROW_PUNCT.charAt(i);
            check("no-start ascii/band " + i, ch > 0x7F && CodeEditor.kinsokuBand(ch));
        }
        for (int i = 0; i < CodeEditor.NO_END_ROW_PUNCT.length(); i++) {
            char ch = CodeEditor.NO_END_ROW_PUNCT.charAt(i);
            check("no-end ascii/band " + i, ch > 0x7F && CodeEditor.kinsokuBand(ch));
        }

        System.out.println(CodeEditor.NO_START_ROW_PUNCT.length() + " no-start chars, "
            + CodeEditor.NO_END_ROW_PUNCT.length() + " no-end chars; band guard verified");
        System.out.println(failures == 0 ? "ALL CHECKS PASSED" : failures + " FAILURES");
        if (failures != 0) {
            System.exit(1);
        }
    }
}
