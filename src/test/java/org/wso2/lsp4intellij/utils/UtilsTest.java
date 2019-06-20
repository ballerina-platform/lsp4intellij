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
            new Utils().concatenateArrays(
                    new String[]{"a", "b"}, new String[]{"c", "d"}));
  }

  @Test
  public void testParseArgsSingleQuote() {
    Assert.assertArrayEquals(new String[]{"\'\'"},
            Utils.parseArgs(new String[]{"\'\'"}));
  }

  @Test
  public void testParseArgsDoubleQuote() {
    Assert.assertArrayEquals(new String[]{"\"\""},
            Utils.parseArgs(new String[]{"\"\""}));
  }

  @Test
  public void testParseArgsSpace() {
    Assert.assertArrayEquals(new String[]{"", "\" \""},
            Utils.parseArgs(new String[]{" \" \""}));
  }

  @Test
  public void testParseArgsSlash() {
    Assert.assertArrayEquals(new String[]{"\\\\"},
            Utils.parseArgs(new String[]{"\\\\"}));
  }

  @Test
  public void testParseArgsC() {
    Assert.assertArrayEquals(new String[]{"c"},
            Utils.parseArgs(new String[]{"c"}));
  }

  @Test
  public void testStringToList() {
    final List<String> arrayList = new ArrayList<>();
    arrayList.add("foo");
    arrayList.add("Bar");

    Assert.assertEquals(arrayList,
            new Utils().stringToList("foo, Bar", ", "));
  }

  @Test
  public void testStringToListSepNull() {
    final List<String> arrayList = new ArrayList<>();
    arrayList.add("fooBar");

    Assert.assertEquals(arrayList,
            new Utils().stringToList("fooBar", null));
  }
}
