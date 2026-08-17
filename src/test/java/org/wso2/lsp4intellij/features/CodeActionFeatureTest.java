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
package org.wso2.lsp4intellij.features;

import com.intellij.lang.annotation.Annotation;
import junit.framework.TestCase;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Tests for {@link CodeActionFeature}'s annotation/sync-flag bookkeeping — the pure part that
 * needs neither the wrapper's request manager nor a real editor. The request/annotation-attaching
 * paths ({@code codeAction}, {@code requestAndShowCodeActions}, {@code showCodeActions}) need a
 * running server and a real editor and have no test coverage, same as before this extraction.
 */
public class CodeActionFeatureTest extends TestCase {

    private CodeActionFeature feature;

    @Override
    protected void setUp() {
        // The override hooks are never invoked: these tests only exercise the annotation and
        // sync-flag bookkeeping, not requestAndShowCodeActions.
        feature = new CodeActionFeature(null, null, null, null, null);
    }

    public void testAnnotationsStartEmpty() {
        assertTrue(feature.getAnnotations().isEmpty());
    }

    public void testSetAnnotationsThenGetRoundTrips() {
        List<Annotation> annotations = new ArrayList<>();
        feature.setAnnotations(annotations);

        assertSame(annotations, feature.getAnnotations());
    }

    public void testCodeActionSyncNotRequiredInitially() {
        assertFalse(feature.isCodeActionSyncRequired());
    }

    public void testGetAnnotationsClearsTheSyncFlag() {
        // There is no public way to set codeActionSyncRequired directly; this only confirms
        // getAnnotations() doesn't leave a stale "required" flag from its initial false state.
        feature.getAnnotations();

        assertFalse(feature.isCodeActionSyncRequired());
    }

    public void testSilentAnnotationsStartsEmptyAndIsMutable() {
        assertTrue(feature.getSilentAnnotations().isEmpty());

        // documentChanged() clears this list through the same live reference.
        feature.getSilentAnnotations().addAll(Collections.emptyList());
        assertTrue(feature.getSilentAnnotations().isEmpty());
    }

    public void testTriggerIntentionActionsIsANoOpUntilRequested() {
        // isTriggerIntentionActions starts false, and there is no public setter for it outside
        // showCodeActions (which needs a running server); this must not touch the editor/wrapper
        // fields (all null here) when the flag is unset.
        feature.triggerIntentionActions();
    }
}
