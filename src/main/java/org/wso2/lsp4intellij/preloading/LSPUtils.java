/*
 *  Copyright (c) 2020, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package org.wso2.lsp4intellij.preloading;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.ui.MessageType;
import com.intellij.openapi.util.Pair;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.wso2.lsp4intellij.IntellijLanguageClient;
import org.wso2.lsp4intellij.client.languageserver.serverdefinition.ProcessBuilderServerDefinition;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import static org.wso2.lsp4intellij.preloading.BallerinaConstants.BALLERINA_CMD;
import static org.wso2.lsp4intellij.preloading.BallerinaConstants.BALLERINA_HOME_CMD;
import static org.wso2.lsp4intellij.preloading.BallerinaConstants.BALLERINA_LS_CMD;
import static org.wso2.lsp4intellij.preloading.BallerinaConstants.BAL_FILE_EXT;
import static org.wso2.lsp4intellij.preloading.BallerinaSdkUtils.execBalHomeCmd;

/**
 * Language server protocol related utils.
 *
 * @since 1.1.4
 */
public class LSPUtils {

    private static final BallerinaAutoDetectNotifier autoDetectNotifier = new BallerinaAutoDetectNotifier();
    private static final Logger LOG = Logger.getInstance(LSPUtils.class);

    /**
     * Registered language server definition using currently opened ballerina projects.
     */
    static void registerServerDefinition() {
        Project[] openProjects = ProjectManager.getInstance().getOpenProjects();
        for (Project project : openProjects) {
            registerServerDefinition(project);
        }
    }

    static boolean registerServerDefinition(Project project) {

        Pair<String, Boolean> balSdk;
        try {
            balSdk = getOrDetectBalSdkHome(project, true);

            String balSdkPath = balSdk.first;
            boolean autoDetected = balSdk.second;

            if (!BallerinaSdkUtils.stringIsNullOrEmpty(balSdkPath)) {
                boolean success = doRegister(project, balSdkPath, autoDetected);
                if (success && autoDetected) {
                    BallerinaPreloadingActivity.LOG.info(String.format("Auto-detected Ballerina Home: %s for the " +
                            "project: %s", balSdkPath, project.getBasePath()));
                    showInIdeaEventLog(project, "Auto-Detected Ballerina Home: " + balSdkPath);
                }
                return success;
            }
            return false;
        } catch (BallerinaCmdException e) {
            showInIdeaEventLog(project, String.format("Auto-Detection Failed for project: %s, due to: %s",
                    project.getBasePath(), e.getMessage()));
            return false;
        }
    }

    private static boolean doRegister(@NotNull Project project, @NotNull String sdkPath, boolean autoDetected) {

        ProcessBuilder processBuilder = getLangServerProcessBuilder(project, sdkPath, autoDetected);
        if (processBuilder == null || project.getBasePath() == null) {
            return false;
        }
        processBuilder.directory(new File(project.getBasePath()));

        // Registers language server definition in the lsp4intellij lang-client library.
        IntellijLanguageClient.addServerDefinition(new ProcessBuilderServerDefinition(BAL_FILE_EXT, processBuilder),
                project);
        BallerinaPreloadingActivity.LOG.info("language server definition is registered using sdk path: " + sdkPath);
        return true;
    }

    /**
     * Returns ballerina sdk location for a given project. First checks for a user-configured ballerina SDK for the
     * project if nothing found, tries to auto detect the active ballerina distribution location.
     *
     * @param project Project instance.
     * @return SDK location path string and a flag which indicates whether the location is auto detected.
     */
    public static Pair<String, Boolean> getOrDetectBalSdkHome(Project project, boolean verboseMode)
            throws BallerinaCmdException {

        // If a ballerina SDK is not configured for the project, Plugin tries to auto detect the ballerina SDK.
        String balSdkPath = BallerinaSdkUtils.autoDetectSdk(project);
        return new Pair<>(balSdkPath, true);
    }

    @Nullable
    private static ProcessBuilder getLangServerProcessBuilder(Project project, String sdkPath, boolean autoDetected) {

        String version = BallerinaSdkUtils.retrieveBallerinaVersion(sdkPath);
        if (version == null) {
            LOG.warn("unable to retrieve ballerina version from sdk path: " + sdkPath);
            return null;
        }

        return createCmdBasedProcess(project, sdkPath, autoDetected);
    }

    /**
     * Creates a process builder instance based on language server CLI command, introduced with ballerina
     * v1.2.0.
     */
    @Nullable
    private static ProcessBuilder createCmdBasedProcess(Project project, String balSdkPath, boolean autoDetected) {

        // Creates the args list to register the language server definition using the ballerina lang-server launcher
        // command.
        List<String> args = getLangServerCmdArgs(project, balSdkPath, autoDetected);
        if (args == null || args.isEmpty()) {
            LOG.warn("Couldn't find ballerina executable to execute language server launch command.");
            return null;
        }

        ProcessBuilder cmdProcessBuilder = new ProcessBuilder(args);
//        // Checks user-configurable setting for allowing ballerina experimental features and sets the flag accordingly.
//        if (BallerinaProjectSettings.getStoredSettings(project).isAllowExperimental()) {
//            cmdProcessBuilder.environment().put(ENV_EXPERIMENTAL, "true");
//        }
//
//        // Checks user-configurable setting for allowing language server debug logs and sets the flag accordingly.
//        if (BallerinaProjectSettings.getStoredSettings(project).isLsDebugLogs()) {
//            cmdProcessBuilder.environment().put(ENV_DEBUG_LOG, "true");
//        }
//
//        // Checks user-configurable setting for allowing language server trace logs and sets the flag accordingly.
//        if (BallerinaProjectSettings.getStoredSettings(project).isLsTraceLogs()) {
//            cmdProcessBuilder.environment().put(ENV_TRACE_LOG, "true");
//        }
//
//        if (BallerinaProjectSettings.getStoredSettings(project).isStdlibGotoDef()) {
//            cmdProcessBuilder.environment().put(ENV_DEF_STDLIBS, "true");
//        }

        return cmdProcessBuilder;
    }

    @Nullable
    private static List<String> getLangServerCmdArgs(Project project, String balSdkPath, boolean autoDetected) {

        List<String> cmdArgs = new ArrayList<>();
        try {

            // Checks if the ballerina command works.
            String ballerinaPath = "";
            try {
                ballerinaPath = execBalHomeCmd(String.format("%s %s", BALLERINA_CMD, BALLERINA_HOME_CMD));
            } catch (BallerinaCmdException ignored) {
                // We do nothing here as we need to fall back for default installer based locations, since
                // "ballerina" command might not work because of the IntelliJ issue of PATH variable might not
                // being identified by the IntelliJ java runtime.
            }

            if (!ballerinaPath.isEmpty()) {
                cmdArgs.add(BALLERINA_CMD);
                cmdArgs.add(BALLERINA_LS_CMD);
                return cmdArgs;
            }
            // Todo - Verify
            // Tries for default installer based locations since "ballerina" commands might not work
            // because of the IntelliJ issue of PATH variable might not being identified by the IntelliJ java
            // runtime.
            String routerScriptPath = BallerinaSdkUtils.getByDefaultPath();
            if (routerScriptPath.isEmpty()) {
                // Returns the empty list.
                return cmdArgs;
            }
            cmdArgs.add(OSUtils.isWindows() ? String.format("\"%s\"", routerScriptPath) : routerScriptPath);
            cmdArgs.add(BALLERINA_LS_CMD);
            return cmdArgs;
        } catch (Exception e) {
            showInIdeaEventLog(project, String.format("Failed to start language server for project: %s, due to: %s",
                    project.getBasePath(), e.getMessage()));
            return null;
        }
    }

    private static void showInIdeaEventLog(@NotNull Project project, String message) {
        ApplicationManager.getApplication().invokeLater(() -> autoDetectNotifier.showMessage(project, message,
                MessageType.INFO));
    }
}
