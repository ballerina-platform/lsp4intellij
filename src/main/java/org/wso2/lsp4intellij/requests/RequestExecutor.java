/*
 * Copyright (c) 2026, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
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

import com.intellij.openapi.diagnostic.Logger;
import org.eclipse.lsp4j.jsonrpc.JsonRpcException;
import org.jetbrains.annotations.Nullable;
import org.wso2.lsp4intellij.client.languageserver.wrapper.LanguageServerWrapper;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.wso2.lsp4intellij.requests.Timeout.getTimeout;

/**
 * Executes blocking waits on language server request futures with a uniform policy: the timeout configured
 * for the request type is enforced, the server status widget is notified of the result, and protocol errors
 * are routed to the wrapper's crash handler.
 */
public class RequestExecutor {

    private static final Logger LOG = Logger.getInstance(RequestExecutor.class);

    private final LanguageServerWrapper wrapper;

    public RequestExecutor(LanguageServerWrapper wrapper) {
        this.wrapper = wrapper;
    }

    /**
     * Waits for the given request future using the timeout configured for the given timeout type.
     * Must not be called on the event dispatch thread.
     *
     * @return the request result, or null if the future is null, the request timed out, or the request failed
     */
    @Nullable
    public <T> T waitFor(@Nullable CompletableFuture<T> future, Timeouts timeoutType) {
        if (future == null) {
            return null;
        }
        try {
            T result = future.get(getTimeout(timeoutType), TimeUnit.MILLISECONDS);
            wrapper.notifySuccess(timeoutType);
            return result;
        } catch (TimeoutException e) {
            LOG.warn(e);
            wrapper.notifyFailure(timeoutType);
            return null;
        } catch (InterruptedException | JsonRpcException | ExecutionException e) {
            LOG.warn(e);
            wrapper.crashed(e);
            return null;
        }
    }
}
