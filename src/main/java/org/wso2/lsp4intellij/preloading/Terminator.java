/*
 * Copyright (c) 2018, WSO2 Inc. (http://wso2.com) All Rights Reserved.
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

package org.wso2.lsp4intellij.preloading;

import com.intellij.openapi.diagnostic.Logger;

/**
 * Launcher terminator Interface.
 */
abstract class Terminator {

    static final Logger LOGGER = Logger.getInstance(Terminator.class);

    static final String LS_SCRIPT_PROCESS_ID = "org.ballerinalang.langserver.launchers.stdio.Main";
    static final String LS_CMD_PROCESS_ID = BallerinaConstants.BALLERINA_LS_CMD;
    static final String DEBUG_SCRIPT_PROCESS_ID = "org.ballerinalang.debugadapter.launcher.Launcher";

    abstract void terminate();
}
