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
package org.wso2.lsp4intellij.requests;

/**
 * Enumeration for the timeouts
 */
public enum Timeouts {
    CODEACTION(2000), CODELENS(2000), COMPLETION(1000), DEFINITION(2000), DOC_HIGHLIGHT(1000), EXECUTE_COMMAND(
            2000), FORMATTING(2000), HOVER(2000), INIT(10000), REFERENCES(2000), SIGNATURE(1000), SHUTDOWN(
            5000), SYMBOLS(2000), WILLSAVE(2000);

    private final int defaultTimeout;

    Timeouts(final int defaultTimeout) {
        this.defaultTimeout = defaultTimeout;
    }

    public int getDefaultTimeout() {
        return defaultTimeout;
    }
}
