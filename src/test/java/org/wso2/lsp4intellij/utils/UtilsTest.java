/*
 * Copyright (c) 2019, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.wso2.lsp4intellij.utils;

import org.junit.Assert;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

/**
 * Unit tests for the {@link Utils} utility class.
 * 
 * Tests cover argument parsing with various quote and escape scenarios,
 * array-to-string conversion with configurable separators,
 * and string-to-list conversion with different delimiters.
 */
public class UtilsTest {

    /**
     * Verifies that {@link Utils#concatenateArrays(Object[], Object[])} joins two arrays into a
     * two-element Object[][] where each inner array is one of the inputs.
     */
    @Test
    public void testConcatenateArrays() {
        Assert.assertArrayEquals(
                new Object[][]{{"a", "b"}, {"c", "d"}},
                new Utils().concatenateArrays(new String[]{"a", "b"}, new String[]{"c", "d"}));
    }

    /**
     * Verifies that {@link Utils#parseArgs(String[])} handles adjacent single-quote pairs,
     * preserving them as an empty single-quoted token.
     */
    @Test
    public void testParseArgsSingleQuote() {
        Assert.assertArrayEquals(new String[]{"''"}, Utils.parseArgs(new String[]{"''"}));
    }

    /**
     * Verifies that {@link Utils#parseArgs(String[])} handles adjacent double-quote pairs,
     * preserving them as an empty double-quoted token.
     */
    @Test
    public void testParseArgsDoubleQuote() {
        Assert.assertArrayEquals(new String[]{"\"\""}, Utils.parseArgs(new String[]{"\"\""}));
    }

    /**
     * Verifies that {@link Utils#parseArgs(String[])} splits on an unquoted space and preserves
     * a subsequent space that appears inside a double-quoted section.
     */
    @Test
    public void testParseArgsSpace() {
        Assert.assertArrayEquals(new String[]{"", "\" \""}, Utils.parseArgs(new String[]{" \" \""}));
    }

    /**
     * Verifies that {@link Utils#parseArgs(String[])} treats a backslash as an escape character,
     * preserving adjacent backslash pairs as a literal double-backslash token.
     */
    @Test
    public void testParseArgsSlash() {
        Assert.assertArrayEquals(new String[]{"\\\\"}, Utils.parseArgs(new String[]{"\\\\"}));
    }

    /**
     * Verifies that {@link Utils#parseArgs(String[])} passes ordinary characters through unchanged.
     */
    @Test
    public void testParseArgsC() {
        Assert.assertArrayEquals(new String[]{"abc"}, Utils.parseArgs(new String[]{"abc"}));
    }

    /**
     * Verifies that {@link Utils#stringToList(String, String)} splits a string on the supplied
     * separator and returns the resulting tokens as a list.
     */
    @Test
    public void testStringToList() {
        final List<String> arrayList = new ArrayList<>();
        arrayList.add("foo");
        arrayList.add("Bar");

        Assert.assertEquals(arrayList, new Utils().stringToList("foo, Bar", ", "));
    }

    /**
     * Verifies that {@link Utils#stringToList(String, String)} treats a null separator as
     * "no split", returning the entire input string as a single-element list.
     */
    @Test
    public void testStringToListSepNull() {
        final List<String> arrayList = new ArrayList<>();
        arrayList.add("fooBar");

        Assert.assertEquals(arrayList, new Utils().stringToList("fooBar", null));
    }

    /**
     * Verifies that {@link Utils#parseArgs(String[])} handles an empty array input correctly.
     */
    @Test
    public void testParseArgsEmptyArray() {
        Assert.assertArrayEquals(new String[0], Utils.parseArgs(new String[0]));
    }

    /**
     * Verifies that {@link Utils#parseArgs(String[])} splits tokens on unquoted spaces.
     */
    @Test
    public void testParseArgsSplitsOnUnquotedSpace() {
        Assert.assertArrayEquals(new String[]{"foo", "bar"}, Utils.parseArgs(new String[]{"foo bar"}));
    }

    /**
     * Verifies that {@link Utils#parseArgs(String[])} preserves spaces inside double-quoted strings.
     */
    @Test
    public void testParseArgsKeepsSpaceInsideDoubleQuotes() {
        Assert.assertArrayEquals(new String[]{"\"c c\""}, Utils.parseArgs(new String[]{"\"c c\""}));
    }

    /**
     * Verifies that {@link Utils#parseArgs(String[])} preserves spaces inside single-quoted strings.
     */
    @Test
    public void testParseArgsKeepsSpaceInsideSingleQuotes() {
        Assert.assertArrayEquals(new String[]{"'c c'"}, Utils.parseArgs(new String[]{"'c c'"}));
    }

    /**
     * Verifies that escaped quotes do not toggle the quote state in {@link Utils#parseArgs(String[])}.
     * An escaped quote should not be treated as a closing delimiter.
     */
    @Test
    public void testParseArgsEscapedQuoteDoesNotToggle() {
        // Input: " \" "  (open quote, escaped quote, space). The escape prevents the inner quote
        // from closing the pair, so the trailing space stays inside and the whole thing is one token.
        Assert.assertArrayEquals(new String[]{"\"\\\" "}, Utils.parseArgs(new String[]{"\"\\\" "}));
    }

    /**
     * Verifies that {@link Utils#parseArgs(String[])} correctly processes multiple input tokens.
     */
    @Test
    public void testParseArgsFlushesMultipleInputs() {
        Assert.assertArrayEquals(new String[]{"c", "c"}, Utils.parseArgs(new String[]{"c", "c"}));
    }

    /**
     * Verifies that {@link Utils#arrayToString(Object[], String)} uses a default separator
     * when {@code null} is provided as the separator.
     */
    @Test
    public void testArrayToStringDefaultSep() {
        Utils utils = new Utils();
        Assert.assertEquals("ab", utils.arrayToString(new String[]{"a", "b"}, null));
    }

    /**
     * Verifies that {@link Utils#arrayToString(Object[], String)} joins elements with the
     * supplied separator.
     */
    @Test
    public void testArrayToStringWithSep() {
        Utils utils = new Utils();
        Assert.assertEquals("a,b", utils.arrayToString(new String[]{"a", "b"}, ","));
    }

    /**
     * Verifies that {@link Utils#arrayToString(Object[], String)} correctly formats an empty array.
     */
    @Test
    public void testArrayToStringEmpty() {
        Utils utils = new Utils();
        Assert.assertEquals("", utils.arrayToString(new Object[0], ","));
    }

    /**
     * Verifies that {@link Utils#stringToList(String, String)} correctly handles empty string input.
     * String#split returns a single empty element for empty input, which should be preserved.
     */
    @Test
    public void testStringToListEmptyString() {
        // String#split on an empty input returns a single empty element.
        List<String> result = new Utils().stringToList("", ",");
        Assert.assertEquals(1, result.size());
        Assert.assertEquals("", result.get(0));
    }
}
