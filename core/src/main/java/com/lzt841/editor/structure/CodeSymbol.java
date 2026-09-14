package com.lzt841.editor.structure;

import com.badlogic.gdx.utils.Array;

/**
 * One entry in a document's symbol tree: a class, a method, a field, a section heading.
 *
 * <p>A symbol carries two ranges, and the distinction matters. {@link #selectionStartLine} /
 * {@link #selectionStartColumn} point at the <em>name</em>, which is where "go to symbol" should put
 * the caret. {@link #startLine} to {@link #endLine} is the whole construct including its body, which
 * is what a breadcrumb uses to decide which symbol the caret is inside.
 *
 * <p>Instances are immutable apart from {@link #children}, which a provider fills while building the
 * tree. Once the editor has the tree it only reads it, so a provider must not keep mutating a symbol
 * it has already returned.
 */
public class CodeSymbol {
    /** Display name, e.g. {@code "parseHeader"}. Never null; a provider that has no name should not emit a symbol. */
    public final String name;
    /** Optional extra shown after the name, e.g. a signature or type. Never null; empty when unused. */
    public final String detail;
    public final CodeSymbolKind kind;
    /** First line of the whole construct, including any body. */
    public final int startLine;
    /** Last line of the whole construct, inclusive. Equal to {@link #startLine} for a one-line symbol. */
    public final int endLine;
    /** Line the name appears on, which is where the caret should land. */
    public final int selectionStartLine;
    public final int selectionStartColumn;
    public final int selectionEndColumn;
    /** Nested symbols in document order. Never null; empty for a leaf. */
    public final Array<CodeSymbol> children = new Array<CodeSymbol>(0);

    public CodeSymbol(
        String name,
        String detail,
        CodeSymbolKind kind,
        int startLine,
        int endLine,
        int selectionStartLine,
        int selectionStartColumn,
        int selectionEndColumn
    ) {
        if (name == null || name.isEmpty()) {
            throw new IllegalArgumentException("name must not be empty");
        }
        if (kind == null) {
            throw new IllegalArgumentException("kind must not be null");
        }
        this.name = name;
        this.detail = detail == null ? "" : detail;
        this.kind = kind;
        this.startLine = Math.max(0, startLine);
        this.endLine = Math.max(this.startLine, endLine);
        this.selectionStartLine = Math.max(0, selectionStartLine);
        this.selectionStartColumn = Math.max(0, selectionStartColumn);
        this.selectionEndColumn = Math.max(this.selectionStartColumn, selectionEndColumn);
    }

    /** Convenience for a symbol whose name sits on its first line. */
    public CodeSymbol(String name, String detail, CodeSymbolKind kind, int startLine, int endLine, int nameColumn) {
        this(name, detail, kind, startLine, endLine, startLine, nameColumn, nameColumn + name.length());
    }

    /** Whether {@code line} falls inside this symbol's full range. */
    public boolean containsLine(int line) {
        return line >= startLine && line <= endLine;
    }

    /** Adds a child, keeping {@link #children} in document order. */
    public void addChild(CodeSymbol child) {
        if (child == null || child == this) {
            return;
        }
        children.add(child);
    }

    /** {@link #name} followed by {@link #detail}, for a list row that shows both. */
    public String label() {
        return detail.isEmpty() ? name : name + " " + detail;
    }

    @Override
    public String toString() {
        return "CodeSymbol{" + kind + " " + name + " " + startLine + ".." + endLine + "}";
    }
}
