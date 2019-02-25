package com.github.lsp4intellij.utils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Object containing some useful methods for the plugin
 */
public class Utils {

    /**
     * Transforms an array into a string (using mkString, useful for Java)
     *
     * @param arr The array
     * @param sep A separator
     * @return The result of mkString
     */
    public String arrayToString(Object[] arr, String sep) {
        sep = (sep != null) ? sep : "";
        return String.join(sep, arr.toString());
    }

    /**
     * Concatenate multiple arrays
     *
     * @param arr The arrays
     * @return The concatenated arrays
     */
    public Object[] concatenateArrays(Object[]... arr) {
        List<Object> result = new ArrayList<>(Arrays.asList(arr));
        return result.toArray();
    }

    public List<String> stringToList(String str, String sep) {
        sep = (sep != null) ? sep : System.lineSeparator();
        return new ArrayList<>(Arrays.asList(str.split(sep)));
    }

    public static String[] parseArgs(String[] strArr) {
        List<String> buffer = new ArrayList<>();
        boolean isSingleQuote = false;
        boolean isDoubleQuote = false;
        boolean wasEscaped = false;

        StringBuilder curStr = new StringBuilder();
        for (String str : strArr) {
            for (int i = 0; i < str.length(); i++) {
                switch (str.charAt(i)) {
                case '\'':
                    if (!wasEscaped) {
                        isSingleQuote = !isSingleQuote;
                    }
                    wasEscaped = false;
                    curStr.append('\'');
                    break;
                case '\"':
                    if (!wasEscaped) {
                        isDoubleQuote = !isDoubleQuote;
                    }
                    wasEscaped = false;
                    curStr.append('\"');
                    break;
                case ' ':
                    if (isSingleQuote || isDoubleQuote) {
                        curStr.append(" ");
                    } else {
                        buffer.add(curStr.toString());
                        curStr.setLength(0);
                    }
                    wasEscaped = false;
                    break;
                case '\\':
                    if (wasEscaped) {
                        wasEscaped = false;
                    } else {
                        wasEscaped = true;
                    }
                    curStr.append('\\');
                    break;
                case 'c':
                    curStr.append('c');
                    wasEscaped = false;
                    break;
                }
            }

            if (curStr.length() != 0) {
                buffer.add(curStr.toString());
                curStr.setLength(0);
            }
        }
        String[] result = new String[buffer.size()];
        buffer.toArray(result);
        return result;
    }
}