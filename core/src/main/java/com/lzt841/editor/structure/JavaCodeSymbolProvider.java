package com.lzt841.editor.structure;

import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.BooleanArray;
import com.badlogic.gdx.utils.IntArray;

import java.util.Locale;

/**
 * Symbol extraction for Java and other C-family sources, driven by the brace regions
 * {@link BraceCodeStructureProvider} already found.
 *
 * <p>This is a heuristic, not a parser, and it is written to say nothing rather than to guess. A brace
 * region becomes a symbol only when its header line matches a shape it recognises: a type declaration
 * ({@code class}, {@code interface}, {@code enum}, {@code @interface}, {@code record}), or a
 * method-like declaration, meaning a name followed by a parameter list. Anything else — a control-flow
 * block, an initializer, an array literal spanning lines — is skipped, and its children are attached to
 * the nearest recognised ancestor so the tree does not grow anonymous levels.
 *
 * <p>Fields and one-line members are reported too, but only outside any nested block, so a local
 * variable inside a method body is not mistaken for a field. A declaration whose body opens and closes
 * on one line, as a compact getter does, has no fold region at all, so it is found by that same per-line
 * scan and is reported even when field reporting is switched off.
 *
 * <p>Where it will be wrong: a method whose parameter list spans lines is named from its first line, so
 * a declaration split immediately after the opening parenthesis loses its parameter detail. Generic
 * parameters containing a {@code >} inside a nested type argument are handled, but a comparison operator
 * inside a default parameter value is not. Treat the output as good enough for an outline and a
 * breadcrumb, not as a source of truth for refactoring.
 */
public class JavaCodeSymbolProvider implements CodeSymbolProvider {
    // "@interface" comes first: "interface" matches inside it as a whole word, since '@' is not an
    // identifier character, so testing it earlier would classify an annotation as an interface.
    private static final String[] TYPE_KEYWORDS = {"@interface", "class", "interface", "enum", "record"};
    private static final String[] MODIFIERS = {
        "public", "protected", "private", "static", "final", "abstract", "native", "synchronized",
        "transient", "volatile", "strictfp", "default", "sealed", "non-sealed"
    };

    /** How far back {@link #continuedHeaderSymbol} will look for a wrapped parameter list. */
    private static final int MAX_SIGNATURE_LINES = 24;

    private boolean includeFields = true;
    private boolean includeSectionComments = true;

    /** Whether to report fields and other one-line members. Default true. */
    public JavaCodeSymbolProvider setIncludeFields(boolean includeFields) {
        this.includeFields = includeFields;
        return this;
    }

    /** Whether {@code // MARK:} / {@code // region} comments become {@link CodeSymbolKind#SECTION} entries. Default true. */
    public JavaCodeSymbolProvider setIncludeSectionComments(boolean includeSectionComments) {
        this.includeSectionComments = includeSectionComments;
        return this;
    }

    @Override
    public Array<CodeSymbol> extract(Array<String> lines, Array<CodeFoldRegion> foldRegions) {
        Array<CodeSymbol> roots = new Array<CodeSymbol>();
        if (lines == null || lines.size == 0) {
            return roots;
        }

        // Regions arrive innermost-first, because a brace region closes when its '}' is reached. The
        // tree has to be built outermost-first, so sort by start line with the wider region winning a
        // tie: an outer region shares its start line with an inner one on "class X { void y() {".
        Array<CodeFoldRegion> ordered = new Array<CodeFoldRegion>(foldRegions == null ? 0 : foldRegions.size);
        if (foldRegions != null) {
            ordered.addAll(foldRegions);
        }
        sortByStartThenWidest(ordered);

        Array<CodeFoldRegion> openRegions = new Array<CodeFoldRegion>();
        Array<CodeSymbol> openSymbols = new Array<CodeSymbol>();
        // Whether declarations found inside each open region should be reported. False for a method body
        // and for an unrecognised block, so locals and anonymous class members stay out of the outline.
        BooleanArray openAcceptsMembers = new BooleanArray();
        // The effective last line of each open region: its own, or its parent's when the input overlapped.
        // The member scan has to use this too, or a trailing member lands inside a symbol that ends first.
        IntArray openEndLines = new IntArray();

        int nextMemberScanLine = 0;
        for (int i = 0; i < ordered.size; i++) {
            CodeFoldRegion region = ordered.get(i);
            if (region.startLine < 0 || region.startLine >= lines.size) {
                continue;
            }
            // Close any region the new one is not inside. Lines still unscanned when a region closes
            // belong to that region's symbol, so each one is flushed against its own owner before the
            // stack is popped. Scanning after the pops instead would hand a class's trailing fields to
            // whatever comes next, which at the top level means the root.
            while (openRegions.size > 0 && region.startLine > openEndLines.peek()) {
                openRegions.pop();
                openAcceptsMembers.pop();
                nextMemberScanLine = flushMembers(lines, nextMemberScanLine, openEndLines.pop(),
                    openSymbols.pop(), roots);
            }

            CodeSymbol parent = openSymbols.size == 0 ? null : openSymbols.peek();
            boolean accepting = openAcceptsMembers.size == 0 || openAcceptsMembers.peek();
            // A region that starts where its parent does shares the header line, and the parent already
            // took the name from it. Leave this one unnamed rather than repeating the parent.
            boolean headerTaken = openRegions.size > 0 && openRegions.peek().startLine == region.startLine;
            CodeSymbol symbol = (!accepting || headerTaken) ? null : symbolForHeader(lines, region, parent);

            // This runs even when fields and section comments are suppressed, because a declaration whose
            // body opens and closes on one line has no fold region and is only found by this scan.
            nextMemberScanLine = flushMembers(lines, nextMemberScanLine, region.startLine - 1, parent, roots);

            if (symbol != null) {
                if (parent == null) {
                    roots.add(symbol);
                } else {
                    parent.addChild(symbol);
                }
                openRegions.add(region);
                openSymbols.add(symbol);
                openEndLines.add(symbol.endLine);
                boolean holdsMembers = bodyHoldsMembers(symbol.kind);
                openAcceptsMembers.add(holdsMembers);
                // A type's body is where its fields live; a method's body is where its locals live,
                // and those must not become fields of the enclosing type.
                nextMemberScanLine = Math.max(nextMemberScanLine, holdsMembers
                    ? region.startLine + 1
                    : symbol.endLine + 1);
            } else {
                // Unrecognised block: keep the region open so nesting is tracked, but let its children
                // attach to the nearest recognised ancestor instead of creating an anonymous level.
                openRegions.add(region);
                openSymbols.add(parent);
                int blockEnd = openEndLines.size == 0
                    ? region.endLine
                    : Math.min(region.endLine, openEndLines.peek());
                openEndLines.add(blockEnd);
                openAcceptsMembers.add(false);
                // Never move the cursor backwards: an unrecognised block nested inside a method must not
                // re-expose the lines after it, which the method body already claimed.
                nextMemberScanLine = Math.max(nextMemberScanLine, blockEnd + 1);
            }
        }

        // Unwind what is still open, innermost first, so a trailing member lands on the symbol whose
        // body it is in rather than on the outermost one.
        while (openRegions.size > 0) {
            openRegions.pop();
            openAcceptsMembers.pop();
            nextMemberScanLine = flushMembers(lines, nextMemberScanLine, openEndLines.pop(),
                openSymbols.pop(), roots);
        }
        flushMembers(lines, nextMemberScanLine, lines.size - 1, null, roots);
        return roots;
    }

    /**
     * Whether a symbol of this kind has a body that declares members, as opposed to statements.
     *
     * <p>This is deliberately not {@link CodeSymbolKind#isContainer()}, which answers a different
     * question: whether an outline should expand the node by default. The two disagree in both
     * directions. {@code PACKAGE} and {@code SECTION} are containers for display but have no body at all,
     * and {@code ENUM_MEMBER} is not a container yet can carry a class body full of methods.
     */
    private static boolean bodyHoldsMembers(CodeSymbolKind kind) {
        switch (kind) {
            case CLASS:
            case INTERFACE:
            case ENUM:
            case STRUCT:
            case ANNOTATION:
            case ENUM_MEMBER:
                return true;
            default:
                return false;
        }
    }

    /**
     * Scans {@code [from, to]} for members and returns the next unscanned line. Never returns less than
     * {@code from}, so a caller cannot rewind the cursor.
     */
    private int flushMembers(Array<String> lines, int from, int to, CodeSymbol owner, Array<CodeSymbol> roots) {
        int start = Math.max(from, 0);
        scanMembers(lines, start, to, owner, roots);
        return Math.max(start, to + 1);
    }

    /** Insertion sort by start line, wider region first on a tie. Regions are few and nearly sorted. */
    private static void sortByStartThenWidest(Array<CodeFoldRegion> regions) {
        for (int i = 1; i < regions.size; i++) {
            CodeFoldRegion current = regions.get(i);
            int j = i - 1;
            while (j >= 0 && isAfter(regions.get(j), current)) {
                regions.set(j + 1, regions.get(j));
                j--;
            }
            regions.set(j + 1, current);
        }
    }

    private static boolean isAfter(CodeFoldRegion a, CodeFoldRegion b) {
        if (a.startLine != b.startLine) {
            return a.startLine > b.startLine;
        }
        return a.endLine < b.endLine;
    }

    /**
     * Classifies a fold region by its header line. Kept as an extension point; the work is in
     * {@link #classifyHeader(String, int, int, CodeSymbol)}.
     */
    protected CodeSymbol symbolForHeader(Array<String> lines, CodeFoldRegion region, CodeSymbol parent) {
        // A region is meant to nest inside the one holding it, but nothing enforces that: the fold regions
        // are an argument, and a custom CodeStructureProvider may overlap them. A child that reaches past
        // its parent is unreachable from CodeEditor.getSymbolPath, which stops descending once a range
        // fails to contain the line, so clamp instead of trusting the input.
        int endLine = parent == null ? region.endLine : Math.min(region.endLine, parent.endLine);
        CodeSymbol local = classifyHeader(lines.get(region.startLine), region.startLine, endLine, parent);
        if (local != null) {
            return local;
        }
        return continuedHeaderSymbol(lines, region, endLine, parent);
    }

    /**
     * A declaration whose parameter list spans lines, so the region's header is only {@code ") {"} or
     * {@code ") throws E {"} and holds no name. Walks back to the line carrying the matching {@code (} and
     * names the symbol from there, which is also where the caret should land, so the symbol starts at the
     * declaration rather than at its closing parenthesis.
     *
     * <p>The walk is bounded and demands that everything before the name be modifiers and at most one
     * type-like token. That is what separates {@code public CodeSymbol(} from a call whose arguments wrap,
     * such as {@code register(new Handler()} or {@code list.forEach(}, which must stay unnamed.
     */
    private CodeSymbol continuedHeaderSymbol(Array<String> lines, CodeFoldRegion region, int endLine,
            CodeSymbol parent) {
        String startCode = codeOnly(lines.get(region.startLine));
        int brace = startCode.indexOf('{');
        int headerLimit = brace >= 0 ? brace : startCode.length();
        // The parameter list has to end on this line, and only a throws clause may follow it. The closing
        // parenthesis need not be the first character: "float y) {" is a more common style than a bare
        // ") {" on its own line, and both have to work. Everything here is a column in startCode.
        int closeColumn = lastCodeIndexOf(startCode, ')', headerLimit);
        if (closeColumn < 0) {
            return null;
        }
        String afterParen = strip(startCode.substring(closeColumn + 1, headerLimit));
        if (!afterParen.isEmpty() && !afterParen.startsWith("throws")) {
            return null;
        }

        int openLine = -1;
        int openColumn = -1;
        int depth = 0;
        String code = startCode;
        int line = region.startLine;
        int column = closeColumn;

        for (int scanned = 0; scanned <= MAX_SIGNATURE_LINES && openLine < 0; scanned++) {
            for (; column >= 0; column--) {
                char c = code.charAt(column);
                if (c == ')') {
                    depth++;
                } else if (c == '(') {
                    depth--;
                    if (depth == 0) {
                        openLine = line;
                        openColumn = column;
                        break;
                    }
                }
            }
            if (openLine >= 0) {
                break;
            }
            line--;
            if (line < 0) {
                return null;
            }
            code = codeOnly(lines.get(line));
            column = code.length() - 1;
        }
        if (openLine < 0 || openColumn <= 0) {
            return null;
        }
        // A declaration cannot begin at or above its own container's header.
        if (parent != null && openLine <= parent.startLine) {
            return null;
        }

        String openCode = codeOnly(lines.get(openLine));
        int nameEnd = openColumn;
        while (nameEnd > 0 && Character.isWhitespace(openCode.charAt(nameEnd - 1))) {
            nameEnd--;
        }
        int nameStart = nameEnd;
        while (nameStart > 0 && isIdentifierChar(openCode.charAt(nameStart - 1))) {
            nameStart--;
        }
        if (nameEnd <= nameStart) {
            return null;
        }
        String name = openCode.substring(nameStart, nameEnd);
        if (isControlKeyword(name)) {
            return null;
        }
        String beforeName = strip(openCode.substring(0, nameStart));
        if (!isDeclarationPrefix(beforeName)) {
            return null;
        }
        String returnType = lastToken(beforeName);
        boolean noReturnType = returnType.isEmpty() || isModifier(returnType);
        CodeSymbolKind kind;
        if (!noReturnType) {
            kind = CodeSymbolKind.METHOD;
        } else if (isEnumConstantContext(parent, name)) {
            kind = CodeSymbolKind.ENUM_MEMBER;
        } else {
            kind = CodeSymbolKind.CONSTRUCTOR;
        }
        // The parameters are spread over several lines; summarise rather than stitch them together.
        String detail = noReturnType ? "(...)" : "(...) : " + returnType;
        return new CodeSymbol(name, detail, kind, openLine, endLine,
            openLine, nameStart, nameEnd);
    }

    /** Last index of {@code target} before {@code limit}, or -1. */
    private static int lastCodeIndexOf(String text, char target, int limit) {
        for (int i = Math.min(limit, text.length()) - 1; i >= 0; i--) {
            if (text.charAt(i) == target) {
                return i;
            }
        }
        return -1;
    }

    /**
     * Whether {@code text} could precede a declaration's name: modifiers and annotations, with at most a
     * single type-like token at the end. An empty prefix is not one, since a bare {@code foo(} is a call.
     */
    private static boolean isDeclarationPrefix(String raw) {
        if (raw.isEmpty() || raw.endsWith(".")) {
            return false;
        }
        // "Map<String, Integer>" is one type but two whitespace-delimited words, so close the gap inside
        // the angle brackets before tokenising.
        String text = collapseGenericSpacing(raw);
        int i = 0;
        int tokens = 0;
        while (i < text.length()) {
            while (i < text.length() && Character.isWhitespace(text.charAt(i))) {
                i++;
            }
            int start = i;
            while (i < text.length() && !Character.isWhitespace(text.charAt(i))) {
                i++;
            }
            if (i <= start) {
                continue;
            }
            String token = text.substring(start, i);
            tokens++;
            if (isModifier(token)) {
                continue;
            }
            // Only the last token may be a type, and only if it looks like one.
            boolean last = strip(text.substring(i)).isEmpty();
            if (!last || !isTypeLike(token)) {
                return false;
            }
        }
        return tokens > 0;
    }

    /**
     * Removes whitespace that sits inside angle brackets, so a generic type is a single token. Unbalanced
     * brackets are left alone; such a prefix fails {@link #isTypeLike(String)} anyway.
     */
    private static String collapseGenericSpacing(String text) {
        if (text.indexOf('<') < 0) {
            return text;
        }
        StringBuilder builder = new StringBuilder(text.length());
        int depth = 0;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '<') {
                depth++;
            } else if (c == '>') {
                depth--;
            }
            if (depth > 0 && Character.isWhitespace(c)) {
                continue;
            }
            builder.append(c);
        }
        return builder.toString();
    }

    /** Whether a token has the shape of a type name: an identifier, possibly qualified or generic. */
    private static boolean isTypeLike(String token) {
        if (token.isEmpty() || isControlKeyword(token)) {
            return false;
        }
        char first = token.charAt(0);
        if (!Character.isLetter(first) && first != '_' && first != '$') {
            return false;
        }
        for (int i = 0; i < token.length(); i++) {
            char c = token.charAt(i);
            if (isIdentifierChar(c) || c == '.' || c == '<' || c == '>' || c == '[' || c == ']'
                || c == ',' || c == '?' || c == '@') {
                continue;
            }
            return false;
        }
        return true;
    }

    /**
     * A copy of {@code text} with the contents of literals and comments replaced by spaces, so a scan can
     * run backwards over it without tracking quoting state in reverse. Lengths match, so a column found in
     * the result is a column in the original.
     */
    private static String codeOnly(String text) {
        char[] out = text.toCharArray();
        boolean inString = false;
        char quote = 0;
        boolean escaped = false;
        for (int i = 0; i < out.length; i++) {
            char c = out[i];
            if (inString) {
                out[i] = ' ';
                if (escaped) {
                    escaped = false;
                } else if (c == '\\') {
                    escaped = true;
                } else if (c == quote) {
                    inString = false;
                }
                continue;
            }
            if (c == '"' || c == '\'') {
                inString = true;
                quote = c;
                escaped = false;
                out[i] = ' ';
                continue;
            }
            if (c == '/' && i + 1 < out.length && (out[i + 1] == '/' || out[i + 1] == '*')) {
                for (int j = i; j < out.length; j++) {
                    out[j] = ' ';
                }
                break;
            }
        }
        return new String(out);
    }

    /**
     * Classifies a block header, or returns null when it is not a declaration this class will name.
     *
     * <p>Only the text up to the opening brace is considered, so a body that begins on the same line
     * cannot contribute a false name. The enclosing symbol is needed because some shapes are only
     * decidable from context: a name and an argument list followed by a body is an enum constant inside
     * an enum, and a constructor anywhere else.
     */
    protected CodeSymbol classifyHeader(String raw, int startLine, int endLine, CodeSymbol parent) {
        int brace = indexOfCodeChar(raw, '{');
        String header = strip(brace >= 0 ? raw.substring(0, brace) : raw);
        if (header.isEmpty()) {
            return null;
        }
        CodeSymbolKind typeKind = typeKeywordKind(header);
        if (typeKind != null) {
            return typeSymbol(raw, header, typeKind, startLine, endLine);
        }
        CodeSymbol method = methodSymbol(raw, header, startLine, endLine, parent);
        return method != null ? method : bareNameBlockSymbol(raw, header, startLine, endLine, parent);
    }

    private CodeSymbol typeSymbol(String raw, String header, CodeSymbolKind kind, int startLine, int endLine) {
        String keyword = typeKeyword(kind);
        if (keyword == null) {
            return null;
        }
        int keywordEnd = indexOfWord(header, keyword);
        if (keywordEnd < 0) {
            return null;
        }
        keywordEnd += keyword.length();
        int nameStart = skipSpaces(header, keywordEnd);
        int nameEnd = endOfIdentifier(header, nameStart);
        if (nameEnd <= nameStart) {
            return null;
        }
        String name = header.substring(nameStart, nameEnd);
        // "extends Foo implements Bar" or a record's parameter list is useful context in an outline.
        String detail = strip(header.substring(nameEnd));
        return new CodeSymbol(name, detail, kind, startLine, endLine,
            startLine, columnInRaw(raw, header, nameStart), columnInRaw(raw, header, nameEnd));
    }

    /**
     * Recognises a method-like header: an identifier immediately followed by a parameter list, with the
     * closing parenthesis on the same line.
     */
    private CodeSymbol methodSymbol(String raw, String header, int startLine, int endLine, CodeSymbol parent) {
        int open = indexOfCodeChar(header, '(');
        if (open <= 0) {
            return null;
        }
        int close = matchingParen(header, open);
        if (close < 0) {
            return null;
        }
        // Whatever follows the parameter list must be a throws clause or nothing; "if (x) return" style
        // lines are excluded by requiring the name to be an identifier and not a keyword.
        int nameEnd = open;
        while (nameEnd > 0 && Character.isWhitespace(header.charAt(nameEnd - 1))) {
            nameEnd--;
        }
        int nameStart = nameEnd;
        while (nameStart > 0 && isIdentifierChar(header.charAt(nameStart - 1))) {
            nameStart--;
        }
        if (nameEnd <= nameStart) {
            return null;
        }
        String name = header.substring(nameStart, nameEnd);
        if (isControlKeyword(name)) {
            return null;
        }

        String beforeName = strip(header.substring(0, nameStart));
        String returnType = lastToken(beforeName);
        // "new Runnable() {" has the shape of a declaration but names a supertype, not a member. A
        // keyword can never be a return type, so this also rejects "case foo() {" and friends.
        if (isControlKeyword(returnType) && !isModifier(returnType)) {
            return null;
        }
        boolean noReturnType = returnType.isEmpty() || isModifier(returnType);
        CodeSymbolKind kind;
        if (!noReturnType) {
            kind = CodeSymbolKind.METHOD;
        } else if (isEnumConstantContext(parent, name)) {
            // A name and an argument list with a body, inside an enum: a constant, not a constructor.
            kind = CodeSymbolKind.ENUM_MEMBER;
        } else {
            kind = CodeSymbolKind.CONSTRUCTOR;
        }
        String params = header.substring(open, close + 1);
        String detail = noReturnType ? params : params + " : " + returnType;
        return new CodeSymbol(name, detail, kind, startLine, endLine,
            startLine, columnInRaw(raw, header, nameStart), columnInRaw(raw, header, nameEnd));
    }

    /** Whether a body-carrying declaration named {@code name} inside {@code parent} is an enum constant. */
    private static boolean isEnumConstantContext(CodeSymbol parent, String name) {
        return parent != null && parent.kind == CodeSymbolKind.ENUM && !name.equals(parent.name);
    }

    /**
     * A block whose header is nothing but modifiers and a name. Two shapes look like this and neither is
     * decidable alone: an enum constant with a body, and a record's compact constructor. The parent
     * settles it, and anything else is left unnamed.
     */
    private CodeSymbol bareNameBlockSymbol(String raw, String header, int startLine, int endLine, CodeSymbol parent) {
        if (parent == null || header.isEmpty() || !isIdentifierChar(header.charAt(header.length() - 1))) {
            return null;
        }
        int nameEnd = header.length();
        int nameStart = nameEnd;
        while (nameStart > 0 && isIdentifierChar(header.charAt(nameStart - 1))) {
            nameStart--;
        }
        String name = header.substring(nameStart, nameEnd);
        if (isControlKeyword(name) || isModifier(name)) {
            return null;
        }
        // Everything in front of the name must be a modifier, or this is some other statement.
        String beforeName = strip(header.substring(0, nameStart));
        if (!beforeName.isEmpty() && !isModifierList(beforeName)) {
            return null;
        }
        CodeSymbolKind kind;
        if (parent.kind == CodeSymbolKind.ENUM) {
            kind = CodeSymbolKind.ENUM_MEMBER;
        } else if (parent.kind == CodeSymbolKind.STRUCT && name.equals(parent.name)) {
            kind = CodeSymbolKind.CONSTRUCTOR;
        } else {
            return null;
        }
        return new CodeSymbol(name, "", kind, startLine, endLine,
            startLine, columnInRaw(raw, header, nameStart), columnInRaw(raw, header, nameEnd));
    }

    /** Whether every whitespace-delimited token in {@code text} is a modifier or an annotation. */
    private static boolean isModifierList(String text) {
        int i = 0;
        while (i < text.length()) {
            while (i < text.length() && Character.isWhitespace(text.charAt(i))) {
                i++;
            }
            int start = i;
            while (i < text.length() && !Character.isWhitespace(text.charAt(i))) {
                i++;
            }
            if (i > start && !isModifier(text.substring(start, i))) {
                return false;
            }
        }
        return true;
    }

    /**
     * Reports one-line members in {@code [from, to]}: fields, imports, enum constants, and section
     * comments. Attaches them to {@code parent}, or to {@code roots} when there is none.
     */
    private void scanMembers(Array<String> lines, int from, int to, CodeSymbol parent, Array<CodeSymbol> roots) {
        int last = Math.min(to, lines.size - 1);
        for (int line = Math.max(0, from); line <= last; line++) {
            String raw = lines.get(line);
            if (isBlockCommentProse(strip(raw))) {
                continue;
            }
            CodeSymbol symbol = includeSectionComments ? sectionSymbol(raw, line) : null;
            if (symbol == null) {
                // "int size() { return n; }" opens and closes on one line, so BraceCodeStructureProvider
                // produced no region for it and the region walk never saw it.
                symbol = selfContainedBlockSymbol(raw, line, parent);
            }
            if (symbol == null && includeFields) {
                symbol = memberSymbol(raw, line, parent);
            }
            if (symbol == null) {
                continue;
            }
            if (parent == null) {
                roots.add(symbol);
            } else {
                parent.addChild(symbol);
            }
        }
    }

    /**
     * A declaration whose body opens and closes on the same line, so it has no fold region. Requires the
     * line to end at brace depth zero, which rules out a header that merely starts a block.
     */
    private CodeSymbol selfContainedBlockSymbol(String raw, int line, CodeSymbol parent) {
        int comment = indexOfComment(raw);
        String text = strip(comment >= 0 ? raw.substring(0, comment) : raw);
        if (text.isEmpty() || text.charAt(text.length() - 1) != '}') {
            return null;
        }
        if (indexOfCodeChar(text, '{') < 0 || !closesEveryBrace(text)) {
            return null;
        }
        return classifyHeader(raw, line, line, parent);
    }

    /** Whether {@code text} opens and closes the same number of braces, ignoring literals. */
    private static boolean closesEveryBrace(String text) {
        int depth = 0;
        boolean inString = false;
        char quote = 0;
        boolean escaped = false;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (inString) {
                if (escaped) {
                    escaped = false;
                } else if (c == '\\') {
                    escaped = true;
                } else if (c == quote) {
                    inString = false;
                }
                continue;
            }
            if (c == '"' || c == '\'') {
                inString = true;
                quote = c;
                escaped = false;
                continue;
            }
            if (c == '{') {
                depth++;
            } else if (c == '}') {
                depth--;
                if (depth < 0) {
                    return false;
                }
            }
        }
        return depth == 0;
    }

    /** A {@code // MARK: name}, {@code // region name} or {@code //region name} heading. */
    protected CodeSymbol sectionSymbol(String raw, int line) {
        String text = strip(raw);
        if (!text.startsWith("//")) {
            return null;
        }
        String body = strip(text.substring(2));
        String lower = body.toLowerCase(Locale.ROOT);
        String name = null;
        if (lower.startsWith("mark:")) {
            name = strip(body.substring(5));
        } else if (lower.startsWith("region")) {
            name = strip(body.substring(6));
        }
        if (name == null) {
            return null;
        }
        // Strip a leading "- " that the MARK convention uses for a separator line.
        while (name.startsWith("-")) {
            name = strip(name.substring(1));
        }
        if (name.isEmpty()) {
            return null;
        }
        // Point at the heading text, not at the "//", so selecting the name range selects the name.
        int column = raw.indexOf(name);
        if (column < 0) {
            column = Math.max(0, raw.indexOf("//"));
        }
        return new CodeSymbol(name, "", CodeSymbolKind.SECTION, line, line, column);
    }

    /**
     * A declaration that ends on its own line: a field, an import, an enum constant, an abstract or
     * interface method. Requires a terminator, so a continuation line cannot be misread as a member.
     */
    protected CodeSymbol memberSymbol(String raw, int line, CodeSymbol parent) {
        int comment = indexOfComment(raw);
        String text = strip(comment >= 0 ? raw.substring(0, comment) : raw);
        if (text.isEmpty()) {
            return null;
        }
        boolean semicolon = text.endsWith(";");
        boolean comma = text.endsWith(",");
        if (!semicolon && !comma) {
            return null;
        }
        String body = strip(text.substring(0, text.length() - 1));
        if (body.isEmpty()) {
            return null;
        }

        if (body.startsWith("import ")) {
            String name = strip(body.substring(7));
            if (name.startsWith("static ")) {
                name = strip(name.substring(7));
            }
            if (name.isEmpty()) {
                return null;
            }
            return new CodeSymbol(name, "", CodeSymbolKind.IMPORT, line, line, columnOfName(raw, name));
        }
        if (body.startsWith("package ")) {
            String name = strip(body.substring(8));
            if (name.isEmpty()) {
                return null;
            }
            return new CodeSymbol(name, "", CodeSymbolKind.PACKAGE, line, line, columnOfName(raw, name));
        }
        if (comma) {
            return enumConstant(raw, body, line);
        }
        // An abstract or interface method: name followed by a parameter list, terminated by ';'.
        CodeSymbol method = abstractMethodSymbol(raw, body, line);
        if (method != null) {
            return method;
        }
        if (parent != null && parent.kind == CodeSymbolKind.ENUM) {
            // The last constant in an enum ends with ';' rather than ',', and has no type in front of it
            // to make it look like a field.
            CodeSymbol constant = enumConstant(raw, body, line);
            if (constant != null) {
                return constant;
            }
        }
        return fieldSymbol(raw, body, line);
    }

    /**
     * A declaration with no body: {@code void run();}. An initializer that happens to contain a call is
     * not one, so an assignment disqualifies the line, and something must precede the name — a bare
     * {@code foo();} is a statement.
     */
    private CodeSymbol abstractMethodSymbol(String raw, String body, int line) {
        int open = indexOfCodeChar(body, '(');
        if (open <= 0 || indexOfAssignment(body) >= 0) {
            return null;
        }
        int close = matchingParen(body, open);
        if (close < 0) {
            return null;
        }
        // Only a throws clause may follow the parameter list.
        String tail = strip(body.substring(close + 1));
        if (!tail.isEmpty() && !tail.startsWith("throws")) {
            return null;
        }
        int nameEnd = open;
        while (nameEnd > 0 && Character.isWhitespace(body.charAt(nameEnd - 1))) {
            nameEnd--;
        }
        int nameStart = nameEnd;
        while (nameStart > 0 && isIdentifierChar(body.charAt(nameStart - 1))) {
            nameStart--;
        }
        if (nameEnd <= nameStart || isControlKeyword(body.substring(nameStart, nameEnd))) {
            return null;
        }
        if (strip(body.substring(0, nameStart)).isEmpty()) {
            return null;
        }
        return new CodeSymbol(body.substring(nameStart, nameEnd), body.substring(open, close + 1),
            CodeSymbolKind.METHOD, line, line, columnInRaw(raw, body, nameStart));
    }

    /** A bare identifier, optionally with an argument list, followed by a comma: an enum constant. */
    private CodeSymbol enumConstant(String raw, String body, int line) {
        int nameEnd = endOfIdentifier(body, 0);
        if (nameEnd <= 0) {
            return null;
        }
        if (nameEnd != body.length()) {
            // Anything after the name has to be a complete argument list, or this is not a constant.
            int open = indexOfCodeChar(body, '(');
            if (open != nameEnd || matchingParen(body, open) != body.length() - 1) {
                return null;
            }
        }
        String name = body.substring(0, nameEnd);
        if (isControlKeyword(name) || isModifier(name)) {
            return null;
        }
        return new CodeSymbol(name, "", CodeSymbolKind.ENUM_MEMBER, line, line, columnInRaw(raw, body, 0));
    }

    /**
     * A field declaration: {@code [modifiers] Type name} optionally followed by {@code = value}. The
     * name is the last identifier before the {@code =}, or at the end when there is none.
     */
    private CodeSymbol fieldSymbol(String raw, String body, int line) {
        int assign = indexOfAssignment(body);
        String declaration = strip(assign >= 0 ? body.substring(0, assign) : body);
        if (declaration.isEmpty()) {
            return null;
        }
        int nameEnd = declaration.length();
        while (nameEnd > 0 && !isIdentifierChar(declaration.charAt(nameEnd - 1))) {
            nameEnd--;
        }
        int nameStart = nameEnd;
        while (nameStart > 0 && isIdentifierChar(declaration.charAt(nameStart - 1))) {
            nameStart--;
        }
        if (nameEnd <= nameStart) {
            return null;
        }
        String name = declaration.substring(nameStart, nameEnd);
        if (isControlKeyword(name) || isModifier(name) || !isIdentifierStart(name.charAt(0))) {
            return null;
        }
        String type = strip(declaration.substring(0, nameStart));
        // A declaration is a type followed by a name; one bare word is an expression statement.
        if (type.isEmpty()) {
            return null;
        }
        // "return n;" and "throw e;" have that shape but no type. "synchronized" is both a keyword and a
        // modifier, so a modifier is allowed through.
        String leading = firstToken(type);
        if (isControlKeyword(leading) && !isModifier(leading)) {
            return null;
        }
        boolean constant = containsWord(type, "final") && containsWord(type, "static");
        return new CodeSymbol(name, lastToken(type),
            constant ? CodeSymbolKind.CONSTANT : CodeSymbolKind.FIELD,
            line, line, columnInRaw(raw, declaration, nameStart));
    }

    // --- text helpers -----------------------------------------------------------------------------
    // All of these ignore string and character literals, so a brace or parenthesis inside one cannot
    // steer the classification.

    /**
     * The Java keyword that introduces {@code kind}, or null when no keyword does.
     *
     * <p>Spelled out rather than derived from {@link Enum#name()}: that needed a {@code toLowerCase},
     * and in a Turkish locale {@code "INTERFACE"} lowercases to {@code "\u0131nterface"} with a dotless
     * i, so every interface stopped being recognised. libGDX ships to Android, where the device locale
     * reaches this code.
     */
    private static String typeKeyword(CodeSymbolKind kind) {
        switch (kind) {
            case CLASS:
                return "class";
            case INTERFACE:
                return "interface";
            case ENUM:
                return "enum";
            case STRUCT:
                return "record";
            case ANNOTATION:
                return "@interface";
            default:
                return null;
        }
    }

    private static CodeSymbolKind typeKeywordKind(String header) {
        for (int i = 0; i < TYPE_KEYWORDS.length; i++) {
            String keyword = TYPE_KEYWORDS[i];
            if (indexOfWord(header, keyword) < 0) {
                continue;
            }
            if ("class".equals(keyword)) {
                return CodeSymbolKind.CLASS;
            }
            if ("interface".equals(keyword)) {
                return CodeSymbolKind.INTERFACE;
            }
            if ("enum".equals(keyword)) {
                return CodeSymbolKind.ENUM;
            }
            if ("record".equals(keyword)) {
                return CodeSymbolKind.STRUCT;
            }
            return CodeSymbolKind.ANNOTATION;
        }
        return null;
    }

    /** Index of {@code word} as a whole word, or -1. {@code @interface} is matched from its {@code @}. */
    private static int indexOfWord(String text, String word) {
        int from = 0;
        while (true) {
            int at = text.indexOf(word, from);
            if (at < 0) {
                return -1;
            }
            char before = at == 0 ? ' ' : text.charAt(at - 1);
            int afterIndex = at + word.length();
            char after = afterIndex >= text.length() ? ' ' : text.charAt(afterIndex);
            boolean startOk = word.charAt(0) == '@' ? true : !isIdentifierChar(before);
            if (startOk && !isIdentifierChar(after)) {
                return at;
            }
            from = at + 1;
        }
    }

    private static boolean containsWord(String text, String word) {
        return indexOfWord(text, word) >= 0;
    }

    /** First occurrence of {@code target} that is not inside a literal or a comment, or -1. */
    private static int indexOfCodeChar(String text, char target) {
        boolean inString = false;
        char quote = 0;
        boolean escaped = false;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (inString) {
                if (escaped) {
                    escaped = false;
                } else if (c == '\\') {
                    escaped = true;
                } else if (c == quote) {
                    inString = false;
                }
                continue;
            }
            if (c == '"' || c == '\'') {
                inString = true;
                quote = c;
                escaped = false;
                continue;
            }
            if (c == '/' && i + 1 < text.length() && (text.charAt(i + 1) == '/' || text.charAt(i + 1) == '*')) {
                return -1;
            }
            if (c == target) {
                return i;
            }
        }
        return -1;
    }

    /** Start of a {@code //} or {@code /*} comment outside any literal, or -1. */
    private static int indexOfComment(String text) {
        boolean inString = false;
        char quote = 0;
        boolean escaped = false;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (inString) {
                if (escaped) {
                    escaped = false;
                } else if (c == '\\') {
                    escaped = true;
                } else if (c == quote) {
                    inString = false;
                }
                continue;
            }
            if (c == '"' || c == '\'') {
                inString = true;
                quote = c;
                escaped = false;
                continue;
            }
            if (c == '/' && i + 1 < text.length()
                && (text.charAt(i + 1) == '/' || text.charAt(i + 1) == '*')) {
                return i;
            }
        }
        return -1;
    }

    /**
     * Whether a line is prose inside a block comment rather than code.
     *
     * <p>Whether a line sits inside a block comment is a property of the whole document, and resolving it
     * properly would mean another character pass over every line. Conventionally formatted block comments
     * and javadoc start each continuation line with an asterisk, so that is what this looks for. A block
     * comment written without them can still leak one line into the outline, which is the trade for not
     * walking the document a second time.
     */
    private static boolean isBlockCommentProse(String stripped) {
        return stripped.startsWith("*") || stripped.startsWith("/*");
    }

    /** Index of the {@code )} closing the {@code (} at {@code open}, or -1 when it is not on this line. */
    private static int matchingParen(String text, int open) {
        int depth = 0;
        boolean inString = false;
        char quote = 0;
        boolean escaped = false;
        for (int i = open; i < text.length(); i++) {
            char c = text.charAt(i);
            if (inString) {
                if (escaped) {
                    escaped = false;
                } else if (c == '\\') {
                    escaped = true;
                } else if (c == quote) {
                    inString = false;
                }
                continue;
            }
            if (c == '"' || c == '\'') {
                inString = true;
                quote = c;
                escaped = false;
                continue;
            }
            if (c == '(') {
                depth++;
            } else if (c == ')') {
                depth--;
                if (depth == 0) {
                    return i;
                }
            }
        }
        return -1;
    }

    /** First {@code =} that is an assignment, skipping {@code ==}, {@code <=}, {@code >=}, {@code !=}. */
    private static int indexOfAssignment(String text) {
        for (int i = 0; i < text.length(); i++) {
            if (text.charAt(i) != '=') {
                continue;
            }
            char before = i == 0 ? ' ' : text.charAt(i - 1);
            char after = i + 1 < text.length() ? text.charAt(i + 1) : ' ';
            if (after == '=' || before == '=' || before == '<' || before == '>' || before == '!') {
                continue;
            }
            return i;
        }
        return -1;
    }

    private static int skipSpaces(String text, int from) {
        int i = from;
        while (i < text.length() && Character.isWhitespace(text.charAt(i))) {
            i++;
        }
        return i;
    }

    private static int endOfIdentifier(String text, int from) {
        int i = from;
        while (i < text.length() && isIdentifierChar(text.charAt(i))) {
            i++;
        }
        return i;
    }

    /** Whether {@code c} may begin an identifier. Rules out a numeric literal read as a name. */
    private static boolean isIdentifierStart(char c) {
        return Character.isLetter(c) || c == '_' || c == '$';
    }

    private static boolean isIdentifierChar(char c) {
        return Character.isLetterOrDigit(c) || c == '_' || c == '$';
    }

    /** First whitespace-delimited token, or an empty string. */
    private static String firstToken(String text) {
        int start = 0;
        while (start < text.length() && Character.isWhitespace(text.charAt(start))) {
            start++;
        }
        int end = start;
        while (end < text.length() && !Character.isWhitespace(text.charAt(end))) {
            end++;
        }
        return start >= end ? "" : text.substring(start, end);
    }

    /** Last whitespace-delimited token, or an empty string. Used to recover a return type or field type. */
    private static String lastToken(String text) {
        int end = text.length();
        while (end > 0 && Character.isWhitespace(text.charAt(end - 1))) {
            end--;
        }
        int start = end;
        // A generic type is one token even though it can contain spaces, so walk back over a balanced
        // "<...>" before looking for whitespace.
        if (start > 0 && text.charAt(start - 1) == '>') {
            int depth = 0;
            int i = start - 1;
            for (; i >= 0; i--) {
                char c = text.charAt(i);
                if (c == '>') {
                    depth++;
                } else if (c == '<') {
                    depth--;
                    if (depth == 0) {
                        break;
                    }
                }
            }
            if (depth != 0) {
                return "";
            }
            start = i;
        }
        while (start > 0 && !Character.isWhitespace(text.charAt(start - 1))) {
            start--;
        }
        return start >= end ? "" : text.substring(start, end);
    }

    private static boolean isModifier(String token) {
        for (int i = 0; i < MODIFIERS.length; i++) {
            if (MODIFIERS[i].equals(token)) {
                return true;
            }
        }
        return token.startsWith("@");
    }

    private static boolean isControlKeyword(String token) {
        return "if".equals(token) || "for".equals(token) || "while".equals(token)
            || "switch".equals(token) || "catch".equals(token) || "synchronized".equals(token)
            || "try".equals(token) || "else".equals(token) || "do".equals(token)
            || "return".equals(token) || "new".equals(token) || "case".equals(token)
            || "finally".equals(token) || "throw".equals(token) || "assert".equals(token)
            || "yield".equals(token) || "instanceof".equals(token);
    }

    private static String strip(String text) {
        int start = 0;
        int end = text.length();
        while (start < end && Character.isWhitespace(text.charAt(start))) {
            start++;
        }
        while (end > start && Character.isWhitespace(text.charAt(end - 1))) {
            end--;
        }
        return text.substring(start, end);
    }

    /**
     * Column where {@code name} starts in {@code raw}, falling back to the first non-blank column.
     *
     * <p>The convenience {@link CodeSymbol} constructor derives the name range end from the start plus the
     * name's length, so passing the column of the whole statement would describe a range that does not
     * contain the name.
     */
    private static int columnOfName(String raw, String name) {
        int at = raw.indexOf(name);
        if (at >= 0) {
            return at;
        }
        int leading = 0;
        while (leading < raw.length() && Character.isWhitespace(raw.charAt(leading))) {
            leading++;
        }
        return leading;
    }

    /**
     * Translates an index in a stripped substring back to a column in the original line.
     *
     * <p>{@code fragment} is always a prefix of {@code raw} with leading whitespace removed, so the
     * offset is the amount of whitespace that was removed. Falls back to the index itself if the
     * fragment is not found, which cannot happen but would otherwise produce a negative column.
     */
    private static int columnInRaw(String raw, String fragment, int indexInFragment) {
        if (fragment.isEmpty()) {
            return indexInFragment;
        }
        int offset = raw.indexOf(fragment);
        if (offset < 0) {
            int leading = 0;
            while (leading < raw.length() && Character.isWhitespace(raw.charAt(leading))) {
                leading++;
            }
            return leading + indexInFragment;
        }
        return offset + indexInFragment;
    }
}
