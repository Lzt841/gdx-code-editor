package com.lzt841.editor.structure;

import com.badlogic.gdx.utils.Array;

/** Analyzes code structure for block guides and folding. */
public interface CodeStructureProvider {
    CodeStructureInfo analyze(Array<String> lines);
}
