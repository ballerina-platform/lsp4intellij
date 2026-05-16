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
package org.wso2.lsp4intellij.client.languageserver.serverdefinition;

import org.junit.Assert;
import org.junit.Test;
import org.wso2.lsp4intellij.client.connection.ProcessStreamConnectionProvider;
import org.wso2.lsp4intellij.client.connection.StreamConnectionProvider;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/**
 * Unit tests for {@link ProcessBuilderServerDefinition}: constructor variants, language-id
 * resolution, the equals/hashCode/toString contract, and the type of connection provider produced.
 */
public class ProcessBuilderServerDefinitionTest {

    private static ProcessBuilder builder(String... command) {
        return new ProcessBuilder(Arrays.asList(command));
    }

    /**
     * The two-arg constructor (no explicit language ids) leaves languageIdFor falling back to the
     * extension itself.
     */
    @Test
    public void simpleConstructorDefaultsLanguageIdsToEmpty() {
        ProcessBuilderServerDefinition def = new ProcessBuilderServerDefinition("go", builder("server"));
        Assert.assertEquals("go", def.ext);
        Assert.assertEquals("go", def.languageIdFor("go"));
    }

    /**
     * The three-arg constructor wires the languageIds map so languageIdFor returns the mapped id,
     * and unmapped extensions fall back to themselves.
     */
    @Test
    public void languageIdsConstructorIsHonored() {
        Map<String, String> ids = new HashMap<>();
        ids.put("ts", "typescript");
        ProcessBuilderServerDefinition def = new ProcessBuilderServerDefinition("ts", ids, builder("server"));

        Assert.assertEquals("typescript", def.languageIdFor("ts"));
        Assert.assertEquals("js", def.languageIdFor("js"));
    }

    /**
     * toString surfaces the ProcessBuilder command joined by spaces — useful for log diagnostics.
     */
    @Test
    public void toStringJoinsProcessCommandWithSpaces() {
        ProcessBuilderServerDefinition def = new ProcessBuilderServerDefinition("go", builder("a", "b", "c"));
        Assert.assertEquals("ProcessBuilderServerDefinition : a b c", def.toString());
    }

    /**
     * equals/hashCode are keyed on (ext, ProcessBuilder). Mismatches in either field make the
     * definitions unequal; matches imply hash equality.
     */
    @Test
    public void equalsRequiresMatchingExtAndProcessBuilder() {
        ProcessBuilder pb = builder("server");
        ProcessBuilderServerDefinition a = new ProcessBuilderServerDefinition("go", pb);
        ProcessBuilderServerDefinition b = new ProcessBuilderServerDefinition("go", pb);
        ProcessBuilderServerDefinition diffExt = new ProcessBuilderServerDefinition("py", pb);
        ProcessBuilderServerDefinition diffPb = new ProcessBuilderServerDefinition("go", builder("other"));

        Assert.assertEquals(a, b);
        Assert.assertEquals(a.hashCode(), b.hashCode());
        Assert.assertNotEquals(a, diffExt);
        Assert.assertNotEquals(a, diffPb);
    }

    /**
     * equals returns false for non-ProcessBuilderServerDefinition arguments, including null.
     */
    @Test
    public void equalsReturnsFalseForUnrelatedTypes() {
        ProcessBuilderServerDefinition def = new ProcessBuilderServerDefinition("go", builder("server"));
        Assert.assertNotEquals(def, "not a definition");
        Assert.assertNotEquals(def, null);
    }

    /**
     * createConnectionProvider returns a {@link ProcessStreamConnectionProvider} bound to the
     * supplied ProcessBuilder — confirms the wiring without actually starting a process.
     */
    @Test
    public void createConnectionProviderReturnsProcessStreamProvider() {
        ProcessBuilderServerDefinition def = new ProcessBuilderServerDefinition("go", builder("server"));
        StreamConnectionProvider provider = def.createConnectionProvider(System.getProperty("user.dir"));
        Assert.assertTrue(provider instanceof ProcessStreamConnectionProvider);
    }
}
