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

import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * An object containing the Timeout for the various requests
 */
public class Timeout {

    private static Map<Timeouts, Integer> timeouts = new ConcurrentHashMap<>();

    static {
        Arrays.stream(Timeouts.values()).forEach(t -> timeouts.put(t, t.getDefaultTimeout()));
    }

    public static int getTimeout(Timeouts type) {
        return timeouts.get(type);
    }

    public static Map<Timeouts, Integer> getTimeouts() {
        return timeouts;
    }

    public static void setTimeouts(Map<Timeouts, Integer> loaded) {
        loaded.forEach((t, v) -> timeouts.replace(t, v));
    }
}
