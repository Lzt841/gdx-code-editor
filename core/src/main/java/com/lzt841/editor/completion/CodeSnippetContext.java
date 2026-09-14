package com.lzt841.editor.completion;

import com.lzt841.editor.CodeEditor;
import com.lzt841.editor.CodeEditorTextRange;
import java.util.Calendar;
import java.util.Locale;
import java.util.UUID;

/**
 * Values substituted for {@code $TM_*} / {@code $CURRENT_*} variables when a snippet is parsed.
 *
 * <p>{@link #from(CodeEditor)} fills in everything the editor can know: the selection, the current
 * line and word, the line number. Filename variables stay empty unless you set them — this editor
 * has no concept of a buffer path. Unknown names resolve to an empty string, matching LSP.
 *
 * <p>Captured once, before the snippet is inserted, so {@code $TM_SELECTED_TEXT} is the selection
 * that the snippet is about to replace rather than whatever is left afterwards.
 */
public class CodeSnippetContext {
    public String selectedText = "";
    public String currentLine = "";
    public String currentWord = "";
    public int lineIndex;
    public int lineNumber = 1;
    public String filename = "";
    public String filenameBase = "";
    public String directory = "";
    public String filepath = "";
    public String clipboard = "";
    public String lineComment = "//";
    public String blockCommentStart = "/*";
    public String blockCommentEnd = "*/";

    public static CodeSnippetContext from(CodeEditor editor) {
        CodeSnippetContext context = new CodeSnippetContext();
        if (editor == null) {
            return context;
        }
        context.selectedText = editor.getSelectionText();
        int line = editor.getCursorLine();
        context.lineIndex = line;
        context.lineNumber = line + 1;
        context.currentLine = editor.getLineText(line);
        CodeEditorTextRange word = editor.getWordRangeAtCursor();
        if (word != null) {
            context.currentWord = editor.getTextRange(word);
        }
        return context;
    }

    /**
     * Resolves a variable name. Unknown names return {@code ""}. Date fields use the local calendar
     * at the moment of the call, so a snippet parsed twice a day apart can differ in {@code $CURRENT_DATE}.
     */
    public String resolve(String name) {
        if (name == null || name.isEmpty()) {
            return "";
        }
        if ("TM_SELECTED_TEXT".equals(name)) {
            return selectedText;
        }
        if ("TM_CURRENT_LINE".equals(name)) {
            return currentLine;
        }
        if ("TM_CURRENT_WORD".equals(name)) {
            return currentWord;
        }
        if ("TM_LINE_INDEX".equals(name)) {
            return Integer.toString(lineIndex);
        }
        if ("TM_LINE_NUMBER".equals(name)) {
            return Integer.toString(lineNumber);
        }
        if ("TM_FILENAME".equals(name)) {
            return filename;
        }
        if ("TM_FILENAME_BASE".equals(name)) {
            return filenameBase;
        }
        if ("TM_DIRECTORY".equals(name)) {
            return directory;
        }
        if ("TM_FILEPATH".equals(name)) {
            return filepath;
        }
        if ("CLIPBOARD".equals(name)) {
            return clipboard;
        }
        if ("LINE_COMMENT".equals(name)) {
            return lineComment;
        }
        if ("BLOCK_COMMENT_START".equals(name)) {
            return blockCommentStart;
        }
        if ("BLOCK_COMMENT_END".equals(name)) {
            return blockCommentEnd;
        }
        Calendar calendar = Calendar.getInstance();
        if ("CURRENT_YEAR".equals(name)) {
            return Integer.toString(calendar.get(Calendar.YEAR));
        }
        if ("CURRENT_YEAR_SHORT".equals(name)) {
            int year = calendar.get(Calendar.YEAR) % 100;
            return year < 10 ? "0" + year : Integer.toString(year);
        }
        if ("CURRENT_MONTH".equals(name)) {
            return twoDigit(calendar.get(Calendar.MONTH) + 1);
        }
        if ("CURRENT_DATE".equals(name)) {
            return twoDigit(calendar.get(Calendar.DAY_OF_MONTH));
        }
        if ("CURRENT_HOUR".equals(name)) {
            return twoDigit(calendar.get(Calendar.HOUR_OF_DAY));
        }
        if ("CURRENT_MINUTE".equals(name)) {
            return twoDigit(calendar.get(Calendar.MINUTE));
        }
        if ("CURRENT_SECOND".equals(name)) {
            return twoDigit(calendar.get(Calendar.SECOND));
        }
        if ("CURRENT_DAY_NAME".equals(name)) {
            return calendar.getDisplayName(Calendar.DAY_OF_WEEK, Calendar.LONG, Locale.getDefault());
        }
        if ("CURRENT_DAY_NAME_SHORT".equals(name)) {
            return calendar.getDisplayName(Calendar.DAY_OF_WEEK, Calendar.SHORT, Locale.getDefault());
        }
        if ("CURRENT_MONTH_NAME".equals(name)) {
            return calendar.getDisplayName(Calendar.MONTH, Calendar.LONG, Locale.getDefault());
        }
        if ("CURRENT_MONTH_NAME_SHORT".equals(name)) {
            return calendar.getDisplayName(Calendar.MONTH, Calendar.SHORT, Locale.getDefault());
        }
        if ("RANDOM".equals(name)) {
            return twoDigit((int) (Math.random() * 100))
                + twoDigit((int) (Math.random() * 100))
                + twoDigit((int) (Math.random() * 100));
        }
        if ("RANDOM_HEX".equals(name)) {
            return Integer.toHexString((int) (Math.random() * 0x1000000) | 0x1000000).substring(1);
        }
        if ("UUID".equals(name)) {
            return UUID.randomUUID().toString();
        }
        return "";
    }

    private static String twoDigit(int value) {
        return value < 10 ? "0" + value : Integer.toString(value);
    }
}
