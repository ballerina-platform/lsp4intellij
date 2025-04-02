/*
 * Copyright (c) 2025, WSO2 LLC. (http://www.wso2.org).
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
package org.wso2.lsp4intellij;

import com.intellij.DynamicBundle;
import org.jetbrains.annotations.NonNls;

import org.jetbrains.annotations.PropertyKey;

/**
 * {@code Lsp4IntellijBundle} is responsible for loading localized messages from resource bundles.
 * It extends {@link DynamicBundle} to facilitate message retrieval with parameter substitution.
 */
public class Lsp4IntellijBundle extends DynamicBundle {
    /**
     * The base name of the resource bundle.
     */
    public static final String BUNDLE_NAME = "messages.Lsp4IntellijBundle";

    /**
     * Singleton instance of {@code Lsp4IntellijBundle}.
     */
    public static final Lsp4IntellijBundle INSTANCE = new Lsp4IntellijBundle();

    /**
     * Constructs an instance of {@code Lsp4IntellijBundle} with the specified bundle name.
     */
    public Lsp4IntellijBundle() {
        super(BUNDLE_NAME);
    }

    /**
     * Retrieves a localized message from the resource bundle.
     *
     * @param key    the key of the message in the resource bundle
     * @param params optional parameters to format the message
     * @return the localized message corresponding to the key
     */
    public static String message(@NonNls @PropertyKey(resourceBundle = BUNDLE_NAME) String key, Object... params) {
        return INSTANCE.getMessage(key, params);
    }
}
