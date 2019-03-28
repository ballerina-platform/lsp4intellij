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
package org.wso2.lsp4intellij.contributors.inspection;

import com.intellij.codeInspection.LocalInspectionTool;
import org.intellij.lang.annotations.Pattern;
import org.jetbrains.annotations.NotNull;

/*
 * This is used just to register a local inspection tool provider. `LSPInspection` contains the actual implementation
 * to handle inspections, after receiving diagnostics notification.
 */
public class DummyLSPInspection extends LocalInspectionTool {

    @NotNull
    @Override
    public String getDisplayName() {
        return getShortName();
    }

    @NotNull
    @Override
    public String getShortName() {
        return "LSP";
    }

    @NotNull
    @Override
    @Pattern("[a-zA-Z_0-9.-]+")
    public String getID() {
        return "LSP";
    }

    @NotNull
    @Override
    public String getGroupDisplayName() {
        return "LSP";
    }

    @Override
    public String getStaticDescription() {
        return "Reports errors by the LSP server";
    }

    @Override
    public boolean isEnabledByDefault() {
        return true;
    }
}
