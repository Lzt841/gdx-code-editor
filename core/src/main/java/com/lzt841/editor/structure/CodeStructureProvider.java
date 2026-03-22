package com.lzt841.editor.structure;

import java.util.List;

/** Analyzes code structure for block guides and folding. */
public interface CodeStructureProvider {
    CodeStructureInfo analyze(List<String> lines);
}
