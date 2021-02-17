/*
 * Copyright (c) 2019, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
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

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.project.NoAccessDuringPsiEvents;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.Condition;
import org.wso2.lsp4intellij.IntellijLanguageClient;
import org.wso2.lsp4intellij.requests.Timeouts;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class ApplicationUtils {

    private static ExecutorService EXECUTOR_SERVICE;

    static {
        // Single threaded executor is used to simulate a behavior of async sequencial execution.
        // All runnables are executed asyncly but they are executed in the order of their submission.
        EXECUTOR_SERVICE = Executors.newSingleThreadExecutor();
        Runtime.getRuntime().addShutdownHook(new Thread() {
            @Override
            public void run() {
                EXECUTOR_SERVICE.shutdownNow();
            }
        });
    }


    static public void invokeLater(Runnable runnable) {
        ApplicationManager.getApplication().invokeLater(runnable);
    }

    static public void pool(Runnable runnable) {
        EXECUTOR_SERVICE.submit(runnable);
    }

    static public void restartPool() {
        EXECUTOR_SERVICE.shutdown();
        try {
            EXECUTOR_SERVICE.awaitTermination(IntellijLanguageClient.getTimeout(Timeouts.SHUTDOWN), TimeUnit.MILLISECONDS);
        } catch (InterruptedException ignored) {
        }
        EXECUTOR_SERVICE = Executors.newSingleThreadExecutor();
        Runtime.getRuntime().addShutdownHook(new Thread() {
            @Override
            public void run() {
                EXECUTOR_SERVICE.shutdownNow();
            }
        });    }

    static public <T> T computableReadAction(Computable<T> computable) {
        return ApplicationManager.getApplication().runReadAction(computable);
    }

    static public void writeAction(Runnable runnable) {
        ApplicationManager.getApplication().runWriteAction(runnable);
    }

    static public <T> T computableWriteAction(Computable<T> computable) {
        return ApplicationManager.getApplication().runWriteAction(computable);
    }

    static public void invokeAfterPsiEvents(Runnable runnable) {
        Runnable wrapper = () -> {
            if (NoAccessDuringPsiEvents.isInsideEventProcessing()) {
                invokeAfterPsiEvents(runnable);
            } else {
                runnable.run();
            }
        };

        ApplicationManager.getApplication().invokeLater(wrapper, (Condition<Void>) value -> false);
    }
}
