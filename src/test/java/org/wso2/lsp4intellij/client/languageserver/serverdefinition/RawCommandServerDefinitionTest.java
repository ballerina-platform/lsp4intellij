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

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Unit tests for {@link RawCommandServerDefinition}: constructor variants, language-id resolution,
 * the equals/hashCode/toString contract, and the type of connection provider produced.
 */
public class RawCommandServerDefinitionTest {

    private static final String[] CMD = new String[]{"server", "--stdio"};

    /**
     * The two-arg constructor (no explicit language ids) leaves languageIdFor falling back to the
     * extension itself.
     */
    @Test
    public void simpleConstructorDefaultsLanguageIdsToEmpty() {
        RawCommandServerDefinition def = new RawCommandServerDefinition("go", CMD);
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
        RawCommandServerDefinition def = new RawCommandServerDefinition("ts", ids, CMD);

        Assert.assertEquals("typescript", def.languageIdFor("ts"));
        Assert.assertEquals("unmapped", def.languageIdFor("unmapped"));
    }

    /**
     * toString surfaces the full command joined by spaces — useful for log diagnostics.
     */
    @Test
    public void toStringJoinsCommandArgsWithSpaces() {
        RawCommandServerDefinition def = new RawCommandServerDefinition("go", new String[]{"a", "b", "c"});
        Assert.assertEquals("RawCommandServerDefinition : a b c", def.toString());
    }

    /**
     * equals/hashCode are keyed on (ext, command[]). Mismatches in either field make the
     * definitions unequal; matches imply hash equality.
     */
    @Test
    public void equalsRequiresMatchingExtAndCommand() {
        RawCommandServerDefinition a = new RawCommandServerDefinition("go", new String[]{"server"});
        RawCommandServerDefinition b = new RawCommandServerDefinition("go", new String[]{"server"});
        RawCommandServerDefinition diffExt = new RawCommandServerDefinition("py", new String[]{"server"});
        RawCommandServerDefinition diffCmd = new RawCommandServerDefinition("go", new String[]{"server", "--x"});

        Assert.assertEquals(a, b);
        Assert.assertEquals(a.hashCode(), b.hashCode());
        Assert.assertNotEquals(a, diffExt);
        Assert.assertNotEquals(a, diffCmd);
    }

    /**
     * equals returns false for non-RawCommandServerDefinition arguments, including null.
     */
    @Test
    public void equalsReturnsFalseForUnrelatedTypes() {
        RawCommandServerDefinition def = new RawCommandServerDefinition("go", CMD);
        Assert.assertNotEquals(def, "not a definition");
        Assert.assertNotEquals(def, null);
    }

    /**
     * createConnectionProvider returns a {@link ProcessStreamConnectionProvider} bound to the
     * command — confirms the wiring without actually starting a process.
     */
    @Test
    public void createConnectionProviderReturnsProcessStreamProvider() {
        RawCommandServerDefinition def = new RawCommandServerDefinition("go", Collections.emptyMap(), CMD);
        StreamConnectionProvider provider = def.createConnectionProvider(System.getProperty("user.dir"));
        Assert.assertTrue(provider instanceof ProcessStreamConnectionProvider);
    }
}
