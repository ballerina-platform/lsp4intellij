/*
 * Copyright (c) 2018-2019, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
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
package org.wso2.lsp4intellij.listeners;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.event.CaretEvent;
import com.intellij.openapi.editor.event.CaretListener;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public class LSPCaretListenerImpl extends LSPListener implements CaretListener {

    private Logger LOG = Logger.getInstance(LSPCaretListenerImpl.class);
    private ScheduledExecutorService scheduler;
    private ScheduledFuture<?> scheduledFuture;
    private static final long DEBOUNCE_INTERVAL_MS = 500;

    public LSPCaretListenerImpl() {
        scheduler = Executors.newScheduledThreadPool(1);
        scheduledFuture = null;
    }

    @Override
    public void caretPositionChanged(CaretEvent e) {
        try {
            if (scheduledFuture != null && !scheduledFuture.isCancelled()) {
                scheduledFuture.cancel(false);
            }
            scheduledFuture = scheduler.schedule(this::debouncedCaretPositionChanged, DEBOUNCE_INTERVAL_MS, TimeUnit.MILLISECONDS);
        } catch (Exception err) {
            LOG.warn("Error occurred when trying to update code actions", err);
        }
    }

    private void debouncedCaretPositionChanged() {
        if (checkEnabled()) {
            manager.requestAndShowCodeActions();
        }
    }
}
