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

public class UtilsTest {

    @Test
    public void testConcatenateArrays() {
        Assert.assertArrayEquals(
                new Object[][]{{"a", "b"}, {"c", "d"}},
                new Utils().concatenateArrays(new String[]{"a", "b"}, new String[]{"c", "d"}));
    }

    @Test
    public void testParseArgsSingleQuote() {
        Assert.assertArrayEquals(new String[]{"''"}, Utils.parseArgs(new String[]{"''"}));
    }

    @Test
    public void testParseArgsDoubleQuote() {
        Assert.assertArrayEquals(new String[]{"\"\""}, Utils.parseArgs(new String[]{"\"\""}));
    }

    @Test
    public void testParseArgsSpace() {
        Assert.assertArrayEquals(new String[]{"", "\" \""}, Utils.parseArgs(new String[]{" \" \""}));
    }

    @Test
    public void testParseArgsSlash() {
        Assert.assertArrayEquals(new String[]{"\\\\"}, Utils.parseArgs(new String[]{"\\\\"}));
    }

    @Test
    public void testParseArgsC() {
        Assert.assertArrayEquals(new String[]{"c"}, Utils.parseArgs(new String[]{"c"}));
    }

    @Test
    public void testStringToList() {
        final List<String> arrayList = new ArrayList<>();
        arrayList.add("foo");
        arrayList.add("Bar");

        Assert.assertEquals(arrayList, new Utils().stringToList("foo, Bar", ", "));
    }

    @Test
    public void testStringToListSepNull() {
        final List<String> arrayList = new ArrayList<>();
        arrayList.add("fooBar");

        Assert.assertEquals(arrayList, new Utils().stringToList("fooBar", null));
    }
}
