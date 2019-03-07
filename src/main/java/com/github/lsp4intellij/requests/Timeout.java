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
package com.github.lsp4intellij.requests;

import com.intellij.history.core.StreamUtil;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import static com.github.lsp4intellij.requests.Timeouts.CODEACTION;
import static com.github.lsp4intellij.requests.Timeouts.CODELENS;
import static com.github.lsp4intellij.requests.Timeouts.COMPLETION;
import static com.github.lsp4intellij.requests.Timeouts.DEFINITION;
import static com.github.lsp4intellij.requests.Timeouts.DOC_HIGHLIGHT;
import static com.github.lsp4intellij.requests.Timeouts.EXECUTE_COMMAND;
import static com.github.lsp4intellij.requests.Timeouts.FORMATTING;
import static com.github.lsp4intellij.requests.Timeouts.HOVER;
import static com.github.lsp4intellij.requests.Timeouts.INIT;
import static com.github.lsp4intellij.requests.Timeouts.REFERENCES;
import static com.github.lsp4intellij.requests.Timeouts.SHUTDOWN;
import static com.github.lsp4intellij.requests.Timeouts.SIGNATURE;
import static com.github.lsp4intellij.requests.Timeouts.SYMBOLS;
import static com.github.lsp4intellij.requests.Timeouts.WILLSAVE;

/**
 * An object containing the Timeout for the various requests
 */
public class Timeout {

    private static Map<Timeouts, Integer> timeouts = new HashMap<>();

    static {
        Arrays.stream(Timeouts.values()).forEach(t -> timeouts.put(t, t.getDefaultTimeout()));
    }

    public static int CODEACTION_TIMEOUT = timeouts.get(CODEACTION);

    public static int CODELENS_TIMEOUT = timeouts.get(CODELENS);

    public static int COMPLETION_TIMEOUT = timeouts.get(COMPLETION);

    public static int DEFINITION_TIMEOUT = timeouts.get(DEFINITION);

    public static int DOC_HIGHLIGHT_TIMEOUT = timeouts.get(DOC_HIGHLIGHT);

    public static int EXECUTE_COMMAND_TIMEOUT = timeouts.get(EXECUTE_COMMAND);

    public static int FORMATTING_TIMEOUT = timeouts.get(FORMATTING);

    public static int HOVER_TIMEOUT = timeouts.get(HOVER);

    public static int INIT_TIMEOUT = timeouts.get(INIT);

    public static int REFERENCES_TIMEOUT = timeouts.get(REFERENCES);

    public static int SIGNATURE_TIMEOUT = timeouts.get(SIGNATURE);

    public static int SHUTDOWN_TIMEOUT = timeouts.get(SHUTDOWN);

    public static int SYMBOLS_TIMEOUT = timeouts.get(SYMBOLS);

    public static int WILLSAVE_TIMEOUT = timeouts.get(WILLSAVE);

    public static void setTimeouts(Map<Timeouts, Integer> loaded) {
        timeouts = loaded;
    }
}
