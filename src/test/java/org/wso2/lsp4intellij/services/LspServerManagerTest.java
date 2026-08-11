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

import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import org.wso2.lsp4intellij.client.connection.StreamConnectionProvider;
import org.wso2.lsp4intellij.client.languageserver.serverdefinition.LanguageServerDefinition;
import org.wso2.lsp4intellij.client.languageserver.wrapper.LanguageServerWrapper;

/**
 * Tests for {@link LspServerManager}'s registry and disposal contract against a light test project.
 * No language server is started: {@code getOrCreateWrapper} only constructs and registers the
 * wrapper, so the stub definition below never has to connect to anything.
 *
 * <p>Each test builds its own {@code LspServerManager} rather than fetching the project's service.
 * {@code BasePlatformTestCase} reuses one light project across the methods of a class, so a test
 * that disposes the shared service would leave {@code disposed} set for every test after it. A
 * directly constructed instance is exactly what the move off static maps makes possible; the
 * service wiring itself is covered separately by {@link #testServiceIsRegisteredPerProject()}.
 */
public class LspServerManagerTest extends BasePlatformTestCase {

    private LspServerManager manager;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        manager = new LspServerManager(getProject());
    }

    @Override
    protected void tearDown() throws Exception {
        try {
            manager.dispose();
        } finally {
            super.tearDown();
        }
    }

    public void testServiceIsRegisteredPerProject() {
        LspServerManager service = LspServerManager.getInstance(getProject());

        assertNotNull(service);
        // Once created, the tolerant accessor must resolve to the very same instance.
        assertSame(service, LspServerManager.getInstanceIfCreated(getProject()));
    }

    public void testGetOrCreateWrapperRegistersEveryExtensionOfTheDefinition() {
        LanguageServerDefinition definition = new StubDefinition("sma,smb");

        LanguageServerWrapper wrapper = manager.getOrCreateWrapper("sma", definition, null);

        assertNotNull(wrapper);
        // The second extension of the same definition must resolve to the same wrapper without
        // creating another one.
        assertSame(wrapper, manager.getOrCreateWrapper("smb", definition, null));
        assertEquals(1, manager.allWrappers().size());
    }

    public void testGetOrCreateWrapperIsIdempotentForTheSameExtension() {
        LanguageServerDefinition definition = new StubDefinition("smc");

        LanguageServerWrapper first = manager.getOrCreateWrapper("smc", definition, null);
        LanguageServerWrapper second = manager.getOrCreateWrapper("smc", definition, null);

        assertSame(first, second);
        assertEquals(1, manager.allWrappers().size());
    }

    public void testAllWrappersReturnsASnapshot() {
        manager.getOrCreateWrapper("smd", new StubDefinition("smd"), null);

        manager.allWrappers().clear();

        assertEquals("mutating the returned set must not affect the registry", 1, manager.allWrappers().size());
    }

    public void testLastWrapperTracksTheMostRecentlyCreatedWrapper() {
        LanguageServerWrapper first = manager.getOrCreateWrapper("sme", new StubDefinition("sme"), null);
        assertSame(first, manager.lastWrapper());

        LanguageServerWrapper second = manager.getOrCreateWrapper("smf", new StubDefinition("smf"), null);
        assertSame(second, manager.lastWrapper());
    }

    public void testUriMappingRoundTrip() {
        LanguageServerWrapper wrapper = manager.getOrCreateWrapper("smg", new StubDefinition("smg"), null);
        assertNull(manager.wrapperForUri("file:///tmp/a.smg"));

        manager.mapUri("file:///tmp/a.smg", wrapper);
        assertSame(wrapper, manager.wrapperForUri("file:///tmp/a.smg"));

        manager.unmapUri("file:///tmp/a.smg");
        assertNull(manager.wrapperForUri("file:///tmp/a.smg"));
    }

    public void testUnregisterForgetsTheWrapperAndItsDefinition() {
        LanguageServerDefinition definition = new StubDefinition("smh,smi");
        manager.definitions().register("smh", definition);
        manager.definitions().register("smi", definition);
        LanguageServerWrapper wrapper = manager.getOrCreateWrapper("smh", definition, null);

        manager.unregister(wrapper, new String[]{"smh", "smi"});

        assertTrue(manager.allWrappers().isEmpty());
        assertNull(manager.lastWrapper());
        // Mirrors the old removeWrapper: the definition is forgotten along with the wrapper.
        assertNull(manager.definitions().definitionForExt("smh"));
        assertNull(manager.definitions().definitionForExt("smi"));
    }

    public void testUnregisterDoesNotClearLastWrapperForADifferentWrapper() {
        LanguageServerWrapper first = manager.getOrCreateWrapper("smj", new StubDefinition("smj"), null);
        LanguageServerWrapper second = manager.getOrCreateWrapper("smk", new StubDefinition("smk"), null);

        manager.unregister(first, new String[]{"smj"});

        assertSame(second, manager.lastWrapper());
    }

    public void testGetOrCreateWrapperReturnsNullAfterDisposal() {
        manager.dispose();

        assertNull("a wrapper must not be creatable once nothing is left to dispose it",
                manager.getOrCreateWrapper("sml", new StubDefinition("sml"), null));
    }

    public void testDisposeClearsTheRegistry() {
        manager.getOrCreateWrapper("smm", new StubDefinition("smm"), null);
        manager.mapUri("file:///tmp/a.smm", manager.lastWrapper());

        manager.dispose();

        assertTrue(manager.allWrappers().isEmpty());
        assertNull(manager.lastWrapper());
        assertNull(manager.wrapperForUri("file:///tmp/a.smm"));
    }

    public void testDisposeIsIdempotent() {
        manager.getOrCreateWrapper("smn", new StubDefinition("smn"), null);

        manager.dispose();
        manager.dispose();

        assertTrue(manager.allWrappers().isEmpty());
    }

    /**
     * A definition that never connects to anything. {@code getOrCreateWrapper} only constructs the
     * wrapper, and the wrapper's constructor does not start a server, so this is never called.
     */
    private static class StubDefinition extends LanguageServerDefinition {

        StubDefinition(String ext) {
            this.ext = ext;
        }

        @Override
        public StreamConnectionProvider createConnectionProvider(String workingDir) {
            throw new UnsupportedOperationException("no server is started in these tests");
        }
    }
}
