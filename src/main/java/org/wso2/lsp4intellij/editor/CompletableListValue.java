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
package org.wso2.lsp4intellij.editor;

import com.intellij.openapi.diagnostic.Logger;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

class CompletableListValue<T> {
    private static final Logger LOGGER = Logger.getInstance(CompletableListValue.class);

    private CompletableFuture<List<T>> completableFuture = new CompletableFuture<>();

    public CompletableListValue() {
    }

    public List<T> getValue() {
        try {
            List<T> value = completableFuture.get(3000, TimeUnit.MILLISECONDS);
            return value;
        } catch (InterruptedException | ExecutionException e) {
            LOGGER.error("CompletableListValue failed", e);
        } catch (TimeoutException e) {
            LOGGER.warn("CompletableListValue timeed out");
        } finally {
            completableFuture = new CompletableFuture<>();
        }
        return Collections.emptyList();
    }

    public void complete(List<T> value) {
        completableFuture.complete(value);
    }
}
