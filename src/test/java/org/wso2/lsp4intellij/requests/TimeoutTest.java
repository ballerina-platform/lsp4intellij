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
package org.wso2.lsp4intellij.requests;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;

/**
 * Unit tests for {@link Timeout} and the {@link Timeouts} enum. Each test takes a snapshot of the
 * static timeout map before running and restores it after, so order-dependence between tests is
 * not a concern.
 */
public class TimeoutTest {

    private Map<Timeouts, Integer> originalSnapshot;

    @Before
    public void snapshotTimeouts() {
        originalSnapshot = new EnumMap<>(Timeouts.class);
        originalSnapshot.putAll(Timeout.getTimeouts());
    }

    @After
    public void restoreTimeouts() {
        Timeout.setTimeouts(originalSnapshot);
    }

    /**
     * Every Timeouts enum value resolves to its declared default when Timeout has not been mutated.
     */
    @Test
    public void getTimeoutReturnsDefaultForEveryEnumValue() {
        for (Timeouts type : Timeouts.values()) {
            Assert.assertEquals("Default mismatch for " + type, type.getDefaultTimeout(), Timeout.getTimeout(type));
        }
    }

    /**
     * The map returned by getTimeouts has an entry for every Timeouts enum value.
     */
    @Test
    public void getTimeoutsContainsAllEnumValues() {
        Map<Timeouts, Integer> map = Timeout.getTimeouts();
        Assert.assertEquals(Timeouts.values().length, map.size());
        for (Timeouts type : Timeouts.values()) {
            Assert.assertTrue("Missing entry for " + type, map.containsKey(type));
        }
    }

    /**
     * setTimeouts applies overrides from the supplied map to the corresponding enum keys.
     */
    @Test
    public void setTimeoutsOverridesProvidedEntries() {
        Map<Timeouts, Integer> override = new HashMap<>();
        override.put(Timeouts.COMPLETION, 12345);
        override.put(Timeouts.HOVER, 67890);

        Timeout.setTimeouts(override);

        Assert.assertEquals(12345, Timeout.getTimeout(Timeouts.COMPLETION));
        Assert.assertEquals(67890, Timeout.getTimeout(Timeouts.HOVER));
    }

    /**
     * setTimeouts only touches keys present in the override map; absent keys retain their previous value.
     */
    @Test
    public void setTimeoutsLeavesUnspecifiedEntriesUntouched() {
        int originalInit = Timeout.getTimeout(Timeouts.INIT);
        Map<Timeouts, Integer> override = new HashMap<>();
        override.put(Timeouts.SHUTDOWN, 1);

        Timeout.setTimeouts(override);

        Assert.assertEquals(originalInit, Timeout.getTimeout(Timeouts.INIT));
        Assert.assertEquals(1, Timeout.getTimeout(Timeouts.SHUTDOWN));
    }

    /**
     * Passing an empty map to setTimeouts is a no-op and leaves every entry in place.
     */
    @Test
    public void setTimeoutsWithEmptyMapPreservesAllEntries() {
        Timeout.setTimeouts(new HashMap<>());
        Assert.assertEquals(Timeouts.values().length, Timeout.getTimeouts().size());
    }

    /**
     * Every Timeouts enum value declares a positive default timeout. Guards against an accidental
     * zero/negative default sneaking in during future edits.
     */
    @Test
    public void timeoutsEnumDefaultsArePositive() {
        for (Timeouts type : Timeouts.values()) {
            Assert.assertTrue("Default timeout for " + type + " should be > 0",
                    type.getDefaultTimeout() > 0);
        }
    }
}
