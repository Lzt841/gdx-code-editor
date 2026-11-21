package com.lzt841.editor.completion;

import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.IntArray;

/**
 * A snippet with its placeholders already expanded into the text that will be inserted.
 *
 * <p>The grammar is the Language Server Protocol / VS Code one:
 * <ul>
 *   <li>{@code $1} / {@code ${1}} — a tab stop, empty</li>
 *   <li>{@code ${1:default}} — a tab stop whose default can itself contain tab stops</li>
 *   <li>{@code ${1|one,two|}} — a choice; the first option is the default</li>
 *   <li>{@code $0} — the last stop, conventionally the exit</li>
 *   <li>{@code $NAME} / {@code ${NAME}} / {@code ${NAME:default}} — a variable, resolved against
 *       {@link CodeSnippetContext}</li>
 *   <li>{@code $$}, {@code \$}, {@code \\}, {@code \}}, {@code \n}, {@code \t} — escapes</li>
 * </ul>
 *
 * <p>Several stops can share an index; those are mirrors, and typing in the first copies to the rest.
 * Nested stops flatten: {@code ${1:foo ${2:bar}}} inserts {@code foo bar} with {@code $2} covering
 * {@code bar} and {@code $1} covering the whole thing.
 *
 * <p>If the source has no {@code $0}, one is implied at the end of the insert text. A source with no
 * numbered stops at all parses, but {@link #hasNavigableStops()} is false and inserting it is just
 * inserting text with the caret at the end — there is nothing to Tab between.
 *
 * <p>Transforms ({@code ${NAME/regex/format/}}) are recognised so they do not leak into the insert
 * text, then ignored: the variable resolves as if the transform were absent. Regex rewrite is a
 * language-server concern, not an editor one.
 */
public class CodeSnippetTemplate {
    public final String source;
    public final String insertText;
    public final Array<CodeSnippetPlaceholder> placeholders;
    /**
     * Unique tab-stop indices in visit order: {@code 1, 2, 3, \ldots} then {@code 0}.
     * Always contains at least {@code 0}.
     */
    public final IntArray tabOrder;

    public CodeSnippetTemplate(
        String source,
        String insertText,
        Array<CodeSnippetPlaceholder> placeholders,
        IntArray tabOrder
    ) {
        this.source = source == null ? "" : source;
        this.insertText = insertText == null ? "" : insertText;
        this.placeholders = placeholders == null ? new Array<CodeSnippetPlaceholder>(0) : placeholders;
        this.tabOrder = tabOrder == null ? new IntArray() : tabOrder;
        if (this.tabOrder.size == 0) {
            this.tabOrder.add(0);
        }
    }

    public static CodeSnippetTemplate parse(String source) {
        return parse(source, new CodeSnippetContext());
    }

    public static CodeSnippetTemplate parse(String source, CodeSnippetContext context) {
        if (source == null || source.isEmpty()) {
            return empty(source);
        }
        CodeSnippetContext resolved = context == null ? new CodeSnippetContext() : context;
        StringBuilder text = new StringBuilder(source.length());
        Array<CodeSnippetPlaceholder> placeholders = new Array<CodeSnippetPlaceholder>();
        parseBody(source, 0, source.length(), text, placeholders, resolved, (char) 0, -1);
        boolean hasZero = false;
        for (int i = 0; i < placeholders.size; i++) {
            if (placeholders.get(i).index == 0) {
                hasZero = true;
                break;
            }
        }
        if (!hasZero) {
            placeholders.add(new CodeSnippetPlaceholder(0, text.length(), text.length()));
        }
        fillEmptyMirrors(text, placeholders);
        return new CodeSnippetTemplate(source, text.toString(), placeholders, buildTabOrder(placeholders));
    }

    /** Independent insertion plan, with LF text and offsets mapped from the expanded body. */
    CodeSnippetTemplate normalizedCopy() {
        String body = insertText;
        int[] offsets = null;
        if (body.indexOf('\r') >= 0) {
            offsets = new int[body.length() + 1];
            StringBuilder normalized = new StringBuilder(body.length());
            for (int i = 0; i < body.length(); i++) {
                char character = body.charAt(i);
                normalized.append(character == '\r' ? '\n' : character);
                offsets[i + 1] = normalized.length();
                if (character == '\r' && i + 1 < body.length() && body.charAt(i + 1) == '\n') {
                    // A boundary between CR and LF belongs after the normalized newline.
                    i++;
                    offsets[i + 1] = normalized.length();
                }
            }
            body = normalized.toString();
        }

        Array<CodeSnippetPlaceholder> stops = new Array<CodeSnippetPlaceholder>(placeholders.size);
        IntArray order = new IntArray(tabOrder);
        if (order.size == 0 || order.peek() != 0) return null;
        for (int i = 0; i < order.size; i++) {
            int index = order.get(i);
            if (index < 0 || order.indexOf(index) != i) return null;
        }
        for (int i = 0; i < placeholders.size; i++) {
            CodeSnippetPlaceholder stop = placeholders.get(i);
            if (stop == null || stop.index < 0 || stop.start < 0 || stop.end < stop.start
                || stop.end > insertText.length() || !order.contains(stop.index) || stop.parent < -1 || stop.parent >= i
                || splitsSurrogatePair(insertText, stop.start) || splitsSurrogatePair(insertText, stop.end)) {
                return null;
            }
            if (stop.parent >= 0) {
                CodeSnippetPlaceholder parent = placeholders.get(stop.parent);
                if (parent.start > stop.start || parent.end < stop.end) return null;
                // An index cannot mirror itself through an enclosing default: the two copies overlap.
                for (int ancestor = stop.parent; ancestor >= 0; ancestor = placeholders.get(ancestor).parent) {
                    if (placeholders.get(ancestor).index == stop.index) return null;
                }
            }
            stops.add(new CodeSnippetPlaceholder(stop.index,
                offsets == null ? stop.start : offsets[stop.start],
                offsets == null ? stop.end : offsets[stop.end], stop.parent));
        }
        for (int i = 0; i < order.size; i++) {
            int index = order.get(i);
            boolean found = false;
            for (int p = 0; p < stops.size; p++) {
                if (stops.get(p).index == index) {
                    found = true;
                    break;
                }
            }
            if (!found) {
                if (index != 0) return null;
                stops.add(new CodeSnippetPlaceholder(0, body.length(), body.length()));
            }
        }
        if (hasUnsafeOverlaps(stops) || hasIndexDependencyCycle(stops, order)) return null;
        return new CodeSnippetTemplate(source, body, stops, order);
    }

    /** Mirroring may never replace part of another occurrence of the same field. */
    private static boolean hasUnsafeOverlaps(Array<CodeSnippetPlaceholder> stops) {
        for (int i = 0; i < stops.size; i++) {
            CodeSnippetPlaceholder first = stops.get(i);
            for (int j = i + 1; j < stops.size; j++) {
                CodeSnippetPlaceholder second = stops.get(j);
                if (first.index == second.index) {
                    if (Math.max(first.start, second.start) < Math.min(first.end, second.end)
                        || first.isEmpty() && second.start < first.start && first.start < second.end
                        || second.isEmpty() && first.start < second.start && second.start < first.end) {
                        return true;
                    }
                } else if (first.start < second.start && second.start < first.end && first.end < second.end
                    || second.start < first.start && first.start < second.end && second.end < first.end) {
                    return true;
                }
            }
        }
        // A hand-built template may omit parent metadata for fully contained fields. Keep accepting
        // those templates; only declared parent relationships add edges to the cycle check.
        return false;
    }

    /** An edit of a child changes its parent; mirrors can link that dependency across occurrences. */
    private static boolean hasIndexDependencyCycle(Array<CodeSnippetPlaceholder> stops, IntArray order) {
        IntArray[] parents = new IntArray[order.size];
        int[] incoming = new int[order.size];
        for (int i = 0; i < stops.size; i++) {
            CodeSnippetPlaceholder child = stops.get(i);
            if (child.parent < 0) continue;
            int childIndex = order.indexOf(child.index);
            int parentIndex = order.indexOf(stops.get(child.parent).index);
            if (parents[childIndex] == null) parents[childIndex] = new IntArray();
            if (!parents[childIndex].contains(parentIndex)) {
                parents[childIndex].add(parentIndex);
                incoming[parentIndex]++;
            }
        }
        int[] pending = new int[order.size];
        int head = 0;
        int tail = 0;
        for (int i = 0; i < incoming.length; i++) {
            if (incoming[i] == 0) pending[tail++] = i;
        }
        while (head < tail) {
            IntArray next = parents[pending[head++]];
            if (next == null) continue;
            for (int i = 0; i < next.size; i++) {
                int parent = next.get(i);
                if (--incoming[parent] == 0) pending[tail++] = parent;
            }
        }
        return head != order.size;
    }

    boolean isAncestor(int ancestor, int child) {
        return isAncestor(placeholders, ancestor, child);
    }

    private static boolean isAncestor(Array<CodeSnippetPlaceholder> placeholders, int ancestor, int child) {
        for (int parent = child < 0 ? -1 : placeholders.get(child).parent; parent >= 0;
             parent = placeholders.get(parent).parent) {
            if (parent == ancestor) return true;
        }
        return false;
    }

    private static boolean splitsSurrogatePair(String text, int offset) {
        return offset > 0 && offset < text.length()
            && Character.isHighSurrogate(text.charAt(offset - 1)) && Character.isLowSurrogate(text.charAt(offset));
    }

    /** Nothing to Tab between: either no stops, or only the implied {@code $0} at the end. */
    public boolean hasNavigableStops() {
        if (tabOrder.size > 1) {
            return true;
        }
        for (int i = 0; i < placeholders.size; i++) {
            CodeSnippetPlaceholder placeholder = placeholders.get(i);
            if (placeholder.index != 0 || placeholder.start != insertText.length()
                || placeholder.end != insertText.length()) {
                return true;
            }
        }
        return false;
    }

    public Array<CodeSnippetPlaceholder> placeholdersWithIndex(int index) {
        Array<CodeSnippetPlaceholder> found = new Array<CodeSnippetPlaceholder>();
        for (int i = 0; i < placeholders.size; i++) {
            if (placeholders.get(i).index == index) {
                found.add(placeholders.get(i));
            }
        }
        return found;
    }

    private static CodeSnippetTemplate empty(String source) {
        Array<CodeSnippetPlaceholder> placeholders = new Array<CodeSnippetPlaceholder>(1);
        placeholders.add(new CodeSnippetPlaceholder(0, 0, 0));
        IntArray order = new IntArray(1);
        order.add(0);
        return new CodeSnippetTemplate(source, "", placeholders, order);
    }

    /**
     * Walks {@code source[from..to)} appending to {@code text}. Stops (without consuming) at
     * {@code stop} when {@code stop != 0}. Returns the index it stopped at.
     */
    private static int parseBody(
        String source,
        int from,
        int to,
        StringBuilder text,
        Array<CodeSnippetPlaceholder> placeholders,
        CodeSnippetContext context,
        char stop,
        int parent
    ) {
        int i = from;
        while (i < to) {
            char c = source.charAt(i);
            if (stop != 0 && c == stop) {
                return i;
            }
            if (c == '\\' && i + 1 < to) {
                text.append(unescape(source.charAt(i + 1)));
                i += 2;
                continue;
            }
            if (c == '$') {
                i = parseDollar(source, i, to, text, placeholders, context, parent);
                continue;
            }
            text.append(c);
            i++;
        }
        return i;
    }

    /**
     * Consumes a {@code $} construct starting at {@code i}. Always advances at least one character,
     * so a stray {@code $} cannot loop.
     */
    private static int parseDollar(
        String source,
        int i,
        int to,
        StringBuilder text,
        Array<CodeSnippetPlaceholder> placeholders,
        CodeSnippetContext context,
        int parent
    ) {
        int next = i + 1;
        if (next >= to) {
            text.append('$');
            return to;
        }
        char c = source.charAt(next);
        if (c == '$') {
            text.append('$');
            return next + 1;
        }
        if (c == '{') {
            return parseBrace(source, next + 1, to, text, placeholders, context, parent);
        }
        if (c >= '0' && c <= '9') {
            int[] consumed = {next};
            int index = readIndex(source, to, consumed);
            int pos = text.length();
            placeholders.add(new CodeSnippetPlaceholder(index, pos, pos, parent));
            return consumed[0];
        }
        if (isIdentStart(c)) {
            int[] consumed = {next};
            String name = readIdent(source, to, consumed);
            text.append(context.resolve(name));
            return consumed[0];
        }
        text.append('$');
        return next;
    }

    /**
     * Parses the inside of {@code ${...}}, with {@code i} already past the opening brace.
     * Returns the index past the matching {@code }}, or {@code to} if it was never closed — in which
     * case the construct is treated as literal text, because silently dropping the rest of the
     * snippet is worse than showing a leftover {@code ${}.
     */
    private static int parseBrace(
        String source,
        int i,
        int to,
        StringBuilder text,
        Array<CodeSnippetPlaceholder> placeholders,
        CodeSnippetContext context,
        int parent
    ) {
        if (i >= to) {
            text.append("${");
            return to;
        }
        char c = source.charAt(i);
        if (c >= '0' && c <= '9') {
            return parseTabStopBrace(source, i, to, text, placeholders, context, parent);
        }
        if (isIdentStart(c)) {
            return parseVariableBrace(source, i, to, text, placeholders, context, parent);
        }
        // `${` not followed by a name or a number: emit literally, including the brace, and let the
        // rest of the body carry on. Consuming a matching `}` here would eat a later tab stop's closer.
        text.append("${");
        return i;
    }

    private static int parseTabStopBrace(
        String source,
        int i,
        int to,
        StringBuilder text,
        Array<CodeSnippetPlaceholder> placeholders,
        CodeSnippetContext context,
        int parent
    ) {
        int[] consumed = {i};
        int index = readIndex(source, to, consumed);
        i = consumed[0];
        if (i >= to) {
            int pos = text.length();
            placeholders.add(new CodeSnippetPlaceholder(index, pos, pos, parent));
            return to;
        }
        char c = source.charAt(i);
        if (c == '}') {
            int pos = text.length();
            placeholders.add(new CodeSnippetPlaceholder(index, pos, pos, parent));
            return i + 1;
        }
        if (c == '|') {
            int[] choiceAt = {i + 1};
            String first = readFirstChoice(source, to, choiceAt);
            i = choiceAt[0];
            int start = text.length();
            text.append(first);
            placeholders.add(new CodeSnippetPlaceholder(index, start, text.length(), parent));
            // readFirstChoice stops *on* the closing `|` rather than past it, so skip it here before
            // looking for the brace. Without this the `|}` tail leaks into the insert text.
            if (i < to && source.charAt(i) == '|') {
                i++;
            }
            if (i < to && source.charAt(i) == '}') {
                i++;
            }
            return i;
        }
        if (c == ':') {
            int start = text.length();
            // The slot is reserved before recursing so that a nested stop lands after its enclosing
            // one, keeping the array in document order. parseBody appends to placeholders as it goes,
            // so adding the outer stop afterwards would order `${1:a ${2:b}}` as [2, 1] — and both
            // primaryRangeIndexFor and the mirror walk in CodeSnippetSession read the array as
            // document-ordered.
            int slot = placeholders.size;
            placeholders.add(null);
            i = parseBody(source, i + 1, to, text, placeholders, context, '}', slot);
            placeholders.set(slot, new CodeSnippetPlaceholder(index, start, text.length(), parent));
            if (i < to && source.charAt(i) == '}') {
                i++;
            }
            return i;
        }
        // `${1foo` — not a valid stop. Keep the number as a stop and leave `foo` to the body.
        int pos = text.length();
        placeholders.add(new CodeSnippetPlaceholder(index, pos, pos, parent));
        return i;
    }

    private static int parseVariableBrace(
        String source,
        int i,
        int to,
        StringBuilder text,
        Array<CodeSnippetPlaceholder> placeholders,
        CodeSnippetContext context,
        int parent
    ) {
        int[] consumed = {i};
        String name = readIdent(source, to, consumed);
        i = consumed[0];
        String resolved = context.resolve(name);
        if (i < to && source.charAt(i) == ':') {
            StringBuilder fallback = new StringBuilder();
            Array<CodeSnippetPlaceholder> nested = new Array<CodeSnippetPlaceholder>();
            i = parseBody(source, i + 1, to, fallback, nested, context, '}', -1);
            if (resolved == null || resolved.isEmpty()) {
                int base = text.length();
                int baseIndex = placeholders.size;
                text.append(fallback);
                for (int n = 0; n < nested.size; n++) {
                    CodeSnippetPlaceholder nestedPlaceholder = nested.get(n);
                    placeholders.add(new CodeSnippetPlaceholder(
                        nestedPlaceholder.index,
                        nestedPlaceholder.start + base,
                        nestedPlaceholder.end + base,
                        nestedPlaceholder.parent < 0 ? parent : baseIndex + nestedPlaceholder.parent
                    ));
                }
            } else {
                text.append(resolved);
            }
            if (i < to && source.charAt(i) == '}') {
                i++;
            }
            return i;
        }
        if (i < to && source.charAt(i) == '/') {
            // ${NAME/regex/format/options} — skip to the closer, resolve as a bare variable.
            i = skipTransform(source, i + 1, to);
            text.append(resolved);
            if (i < to && source.charAt(i) == '}') {
                i++;
            }
            return i;
        }
        text.append(resolved);
        if (i < to && source.charAt(i) == '}') {
            i++;
        }
        return i;
    }

    /** First choice of {@code a,b,c}, with {@code \,} as a literal comma. Leaves {@code i} on the closing {@code |}. */
    private static String readFirstChoice(String source, int to, int[] i) {
        StringBuilder choice = new StringBuilder();
        while (i[0] < to) {
            char c = source.charAt(i[0]);
            if (c == '|') {
                return choice.toString();
            }
            if (c == ',') {
                skipRestOfChoices(source, to, i);
                return choice.toString();
            }
            if (c == '\\' && i[0] + 1 < to) {
                choice.append(unescape(source.charAt(i[0] + 1)));
                i[0] += 2;
                continue;
            }
            choice.append(c);
            i[0]++;
        }
        return choice.toString();
    }

    private static void skipRestOfChoices(String source, int to, int[] i) {
        while (i[0] < to) {
            char c = source.charAt(i[0]);
            if (c == '|') {
                return;
            }
            if (c == '\\' && i[0] + 1 < to) {
                i[0] += 2;
                continue;
            }
            i[0]++;
        }
    }

    /**
     * Skips a {@code /regex/format/options} transform. Slashes inside {@code \Q...\E} or escaped with
     * {@code \} do not count; we only need to find the closing {@code }}, so a simpler rule is used:
     * unescaped {@code }} ends it, escaped characters are skipped. A transform is allowed to contain
     * {@code /} freely.
     */
    private static int skipTransform(String source, int from, int to) {
        int i = from;
        while (i < to) {
            char c = source.charAt(i);
            if (c == '}') {
                return i;
            }
            if (c == '\\' && i + 1 < to) {
                i += 2;
                continue;
            }
            i++;
        }
        return i;
    }

    private static int readIndex(String source, int to, int[] i) {
        int start = i[0];
        int value = 0;
        while (i[0] < to) {
            char c = source.charAt(i[0]);
            if (c < '0' || c > '9') {
                break;
            }
            // Cap so a 100-digit "index" cannot overflow or allocate a huge tabOrder.
            if (value > 9999) {
                i[0]++;
                continue;
            }
            value = value * 10 + (c - '0');
            i[0]++;
        }
        if (i[0] == start) {
            return 0;
        }
        return value;
    }

    private static String readIdent(String source, int to, int[] i) {
        int start = i[0];
        if (start >= to || !isIdentStart(source.charAt(start))) {
            return "";
        }
        i[0]++;
        while (i[0] < to && isIdentPart(source.charAt(i[0]))) {
            i[0]++;
        }
        return source.substring(start, i[0]);
    }

    private static boolean isIdentStart(char c) {
        return c == '_' || (c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z');
    }

    private static boolean isIdentPart(char c) {
        return isIdentStart(c) || (c >= '0' && c <= '9');
    }

    private static char unescape(char c) {
        switch (c) {
            case 't':
                return '\t';
            case 'n':
                return '\n';
            case 'r':
                return '\r';
            default:
                return c;
        }
    }

    /**
     * Copies each stop's default text into the bare {@code $n} mirrors that share its index.
     *
     * <p>This is what makes the commonest snippet of all work. In
     * {@code for (int ${1:i} = 0; $1 < ${2:n}; $1++)} the two bare {@code $1}s parse as empty spans, so
     * without this pass the inserted text reads {@code for (int i = 0;  < n; ++)} and only becomes
     * correct once the user types — which they cannot do without first seeing the wrong text.
     *
     * <p>Runs once at parse time, so the O(placeholders²) offset fixup is paid per snippet rather than
     * per keystroke. A snippet has a handful of stops.
     */
    private static void fillEmptyMirrors(StringBuilder text, Array<CodeSnippetPlaceholder> placeholders) {
        for (int i = 0; i < placeholders.size; i++) {
            CodeSnippetPlaceholder mirror = placeholders.get(i);
            if (mirror.index == 0 || !mirror.isEmpty()) {
                continue;
            }
            String value = defaultTextFor(text, placeholders, mirror.index);
            if (value.isEmpty()) {
                continue;
            }
            int at = mirror.start;
            text.insert(at, value);
            int length = value.length();
            for (int j = 0; j < placeholders.size; j++) {
                CodeSnippetPlaceholder other = placeholders.get(j);
                if (j == i) {
                    placeholders.set(j, new CodeSnippetPlaceholder(other.index, at, at + length, other.parent));
                    continue;
                }
                int start = other.start;
                int end = other.end;
                if (isAncestor(placeholders, j, i) || start < at && end > at) {
                    // Actual nesting, including an empty parent, grows around the inserted mirror.
                    end += length;
                } else if (start > at || start == at && (start < end || j > i)) {
                    start += length;
                    end += length;
                }
                placeholders.set(j, new CodeSnippetPlaceholder(other.index, start, end, other.parent));
            }
        }
    }

    /** The first non-empty span carrying {@code index}, which is the default the mirrors copy. */
    private static String defaultTextFor(
        StringBuilder text,
        Array<CodeSnippetPlaceholder> placeholders,
        int index
    ) {
        for (int i = 0; i < placeholders.size; i++) {
            CodeSnippetPlaceholder candidate = placeholders.get(i);
            if (candidate.index != index || candidate.isEmpty()) {
                continue;
            }
            if (candidate.start < 0 || candidate.end > text.length()) {
                continue;
            }
            return text.substring(candidate.start, candidate.end);
        }
        return "";
    }

    private static IntArray buildTabOrder(Array<CodeSnippetPlaceholder> placeholders) {
        IntArray positive = new IntArray();
        boolean hasZero = false;
        for (int i = 0; i < placeholders.size; i++) {
            int index = placeholders.get(i).index;
            if (index == 0) {
                hasZero = true;
                continue;
            }
            if (!positive.contains(index)) {
                positive.add(index);
            }
        }
        positive.sort();
        if (hasZero) {
            positive.add(0);
        }
        return positive;
    }
}
