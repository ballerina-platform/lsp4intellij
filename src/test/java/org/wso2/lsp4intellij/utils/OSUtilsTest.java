/*
 * Copyright (c) 2026, WSO2 LLC. (http://www.wso2.org) All Rights Reserved.
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

import java.util.Locale;

/**
 * Unit tests for {@link OSUtils} OS family detection methods.
 * 
 * Tests verify that the OS detection logic correctly identifies the host platform (Windows, Mac, or Unix)
 * and that all three family-check methods and getOperatingSystem() remain internally consistent.
 * Since OSUtils captures os.name at class-load time, tests infer the expected result from the same
 * source rather than mocking the OS.
 */
public class OSUtilsTest {

    // OSUtils captures System.getProperty("os.name") at class-load time, so we infer the expected
    // result from the same source and assert internal consistency rather than mocking the OS.

    private static String hostOs() {
        return System.getProperty("os.name").toLowerCase(Locale.getDefault());
    }

    /**
     * Verifies that exactly one of {@link OSUtils#isWindows()}, {@link OSUtils#isMac()},
     * and {@link OSUtils#isUnix()} returns true for the host operating system.
     */
    @Test
    public void exactlyOneFamilyReportsTrueForHostOs() {
        int trueCount = 0;
        if (OSUtils.isWindows()) {
            trueCount++;
        }
        if (OSUtils.isMac()) {
            trueCount++;
        }
        if (OSUtils.isUnix()) {
            trueCount++;
        }
        Assert.assertEquals("Exactly one OS family should match host (os.name=" + hostOs() + ")",
                1, trueCount);
    }

    /**
     * Verifies that {@link OSUtils#getOperatingSystem()} returns a non-null string and that
     * its value agrees with the corresponding family-check method.
     */
    @Test
    public void getOperatingSystemAgreesWithFamilyChecks() {
        String os = OSUtils.getOperatingSystem();
        Assert.assertNotNull("Unsupported host os.name=" + hostOs(), os);

        if (OSUtils.isWindows()) {
            Assert.assertEquals("windows", os);
        } else if (OSUtils.isUnix()) {
            Assert.assertEquals("unix", os);
        } else if (OSUtils.isMac()) {
            Assert.assertEquals("mac", os);
        }
    }

    /**
     * Verifies that {@link OSUtils#isWindows()} correctly detects Windows by checking for "win"
     * in the os.name system property.
     */
    @Test
    public void windowsCheckMatchesOsName() {
        Assert.assertEquals(hostOs().contains("win"), OSUtils.isWindows());
    }

    /**
     * Verifies that {@link OSUtils#isMac()} correctly detects macOS by checking for "mac"
     * in the os.name system property.
     */
    @Test
    public void macCheckMatchesOsName() {
        Assert.assertEquals(hostOs().contains("mac"), OSUtils.isMac());
    }

    /**
     * Verifies that {@link OSUtils#isUnix()} correctly detects Unix-like OSes by checking for
     * "nix", "nux", or "aix" in the os.name system property.
     */
    @Test
    public void unixCheckMatchesOsName() {
        boolean expected = hostOs().contains("nix") || hostOs().contains("nux") || hostOs().contains("aix");
        Assert.assertEquals(expected, OSUtils.isUnix());
    }
}
