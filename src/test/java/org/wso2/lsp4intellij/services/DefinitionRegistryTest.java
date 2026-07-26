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
package org.wso2.lsp4intellij.services;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.wso2.lsp4intellij.client.connection.StreamConnectionProvider;
import org.wso2.lsp4intellij.client.languageserver.serverdefinition.LanguageServerDefinition;

import java.util.Map;

/**
 * Unit tests for {@link DefinitionRegistry}: registration and replacement, lookup by exact
 * extension, lookup by matching a file name against the keys as regex patterns, and the
 * live-view contract of {@link DefinitionRegistry#asMap()}.
 */
public class DefinitionRegistryTest {

    private DefinitionRegistry registry;
    private LanguageServerDefinition javaDef;
    private LanguageServerDefinition gradleDef;

    @Before
    public void setUp() {
        registry = new DefinitionRegistry();
        javaDef = new StubDefinition("java");
        gradleDef = new StubDefinition(".*\\.gradle\\.kts");
    }

    @Test
    public void registerThenLookupByExtension() {
        registry.register("java", javaDef);
        Assert.assertSame(javaDef, registry.definitionForExt("java"));
    }

    @Test
    public void definitionForUnknownExtensionIsNull() {
        registry.register("java", javaDef);
        Assert.assertNull(registry.definitionForExt("kt"));
    }

    @Test
    public void definitionForNullExtensionIsNull() {
        registry.register("java", javaDef);
        // VirtualFile.getExtension() returns null for an extensionless file; lookup must not throw.
        Assert.assertNull(registry.definitionForExt(null));
    }

    @Test
    public void registeringSameExtensionReplacesPreviousDefinition() {
        LanguageServerDefinition replacement = new StubDefinition("java");
        registry.register("java", javaDef);
        registry.register("java", replacement);
        Assert.assertSame(replacement, registry.definitionForExt("java"));
    }

    @Test
    public void removeForgetsTheDefinition() {
        registry.register("java", javaDef);
        registry.remove("java");
        Assert.assertNull(registry.definitionForExt("java"));
        Assert.assertTrue(registry.asMap().isEmpty());
    }

    @Test
    public void removingUnregisteredExtensionIsANoOp() {
        registry.register("java", javaDef);
        registry.remove("kt");
        Assert.assertSame(javaDef, registry.definitionForExt("java"));
    }

    @Test
    public void matchByFileNameReturnsTheMatchingKeyAndDefinition() {
        registry.register(".*\\.gradle\\.kts", gradleDef);
        Map.Entry<String, LanguageServerDefinition> matched = registry.matchByFileName("build.gradle.kts");
        Assert.assertNotNull(matched);
        // The caller uses the key as the ext when in file-name mode, so it must be the regex itself.
        Assert.assertEquals(".*\\.gradle\\.kts", matched.getKey());
        Assert.assertSame(gradleDef, matched.getValue());
    }

    @Test
    public void matchByFileNameReturnsNullWhenNothingMatches() {
        registry.register(".*\\.gradle\\.kts", gradleDef);
        Assert.assertNull(registry.matchByFileName("build.gradle"));
    }

    @Test
    public void matchByFileNameDoesNotMatchAPlainExtensionKey() {
        // A plain "java" key is a valid regex, but it must not match the whole file name "Foo.java".
        registry.register("java", javaDef);
        Assert.assertNull(registry.matchByFileName("Foo.java"));
    }

    @Test
    public void hasDefinitionMatchingOnExactExtension() {
        registry.register("java", javaDef);
        Assert.assertTrue(registry.hasDefinitionMatching("java", "Foo.java"));
    }

    @Test
    public void hasDefinitionMatchingOnFileNamePattern() {
        registry.register(".*\\.gradle\\.kts", gradleDef);
        // The extension ("kts") matches no key; the file name matches the regex key.
        Assert.assertTrue(registry.hasDefinitionMatching("kts", "build.gradle.kts"));
    }

    @Test
    public void hasDefinitionMatchingIsFalseWhenNeitherMatches() {
        registry.register("java", javaDef);
        Assert.assertFalse(registry.hasDefinitionMatching("kt", "Foo.kt"));
    }

    @Test
    public void hasDefinitionMatchingToleratesNullExtension() {
        registry.register("java", javaDef);
        // Extensionless files reach isExtensionSupported with a null ext; only the name is matched.
        Assert.assertFalse(registry.hasDefinitionMatching(null, "Makefile"));
    }

    @Test
    public void hasDefinitionMatchingIsFalseOnAnEmptyRegistry() {
        Assert.assertFalse(registry.hasDefinitionMatching("java", "Foo.java"));
    }

    @Test(expected = UnsupportedOperationException.class)
    public void asMapIsUnmodifiable() {
        registry.asMap().put("kt", javaDef);
    }

    @Test
    public void asMapIsALiveViewNotASnapshot() {
        Map<String, LanguageServerDefinition> view = registry.asMap();
        Assert.assertTrue(view.isEmpty());
        registry.register("java", javaDef);
        // Documented contract: the returned map tracks later registrations, unlike
        // IntellijLanguageClient.getProjectToLanguageWrappers(), which returns a snapshot.
        Assert.assertSame(javaDef, view.get("java"));
    }

    /** A definition that never connects to anything; only its {@code ext} field matters here. */
    private static class StubDefinition extends LanguageServerDefinition {

        StubDefinition(String ext) {
            this.ext = ext;
        }

        @Override
        public StreamConnectionProvider createConnectionProvider(String workingDir) {
            throw new UnsupportedOperationException("not needed for registry tests");
        }
    }
}
