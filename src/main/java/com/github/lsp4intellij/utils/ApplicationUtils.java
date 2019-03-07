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
package com.github.lsp4intellij.utils;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.Computable;

public class ApplicationUtils {

    static public void invokeLater(Runnable runnable) {
        ApplicationManager.getApplication().invokeLater(runnable);
    }

    static public void pool(Runnable runnable) {
        ApplicationManager.getApplication().executeOnPooledThread(runnable);
    }

    static public <T> T computableReadAction(Computable<T> computable) {
        return ApplicationManager.getApplication().runReadAction(computable);
    }

    static public void writeAction(Runnable runnable) {
        ApplicationManager.getApplication().runWriteAction(runnable);
    }

    static public <T> T computableWriteAction(Computable<T> computable) {
        return ApplicationManager.getApplication().runWriteAction(computable);
    }
}
