/*
 * Copyright (c) 2018, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
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

import com.intellij.ide.plugins.IdeaPluginDescriptor;
import com.intellij.ide.plugins.PluginManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.vfs.newvfs.impl.VfsRootAccess;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.wso2.lsp4intellij.preloading.BallerinaConstants.BALLERINA_CMD;
import static org.wso2.lsp4intellij.preloading.BallerinaConstants.BALLERINA_CONFIG_FILE_NAME;
import static org.wso2.lsp4intellij.preloading.BallerinaConstants.BALLERINA_HOME_CMD;
import static org.wso2.lsp4intellij.preloading.BallerinaConstants.BALLERINA_NEW_VERSION_FILE_PATH;
import static org.wso2.lsp4intellij.preloading.BallerinaConstants.BALLERINA_PLUGIN_ID;
import static org.wso2.lsp4intellij.preloading.BallerinaConstants.BALLERINA_VERSION_FILE_PATH;
import static org.wso2.lsp4intellij.preloading.OSUtils.MAC;
import static org.wso2.lsp4intellij.preloading.OSUtils.UNIX;
import static org.wso2.lsp4intellij.preloading.OSUtils.WINDOWS;

/**
 * Contains util classes related to Ballerina SDK.
 */
public class BallerinaSdkUtils {

    private static final Logger LOG = Logger.getInstance(BallerinaSdkUtils.class);
    private static final Key<String> VERSION_DATA_KEY = Key.create("BALLERINA_VERSION_KEY");

    // Sem-var compatible version pattern prior to Swan Lake release.
    public static final Pattern BALLERINA_OLD_VERSION_PATTERN = Pattern.compile("(\\d+\\.\\d+(\\.\\d+)?(-.+)?)");

    // Swan lake release version constants.
    public static final Pattern BALLERINA_SWANLAKE_VERSION_PATTERN = Pattern.compile("(swan[-_]?lake)");
    private static final String BALLERINA_SWAN_LAKE_VERSION_TEXT = "2.0.0-SwanLake";

    private static final String INSTALLER_PATH_UNIX = "/usr/bin/ballerina";
    private static final String INSTALLER_PATH_MAC = "/etc/paths.d/ballerina";
    private static final String HOMEBREW_PATH_MAC = "/usr/local/bin/ballerina";
    // Todo
    private static final String INSTALLER_PATH_WINDOWS = "C:\\Program Files\\Ballerina\\ballerina";

    private BallerinaSdkUtils() {
    }

    @Nullable
    public static String retrieveBallerinaVersion(@NotNull String sdkPath) {
        try {
            // need this hack to avoid an IDEA bug caused by "AssertionError: File accessed outside allowed roots"
            VfsRootAccess.allowRootAccess(sdkPath);

            VirtualFile sdkRoot = VirtualFileManager.getInstance().findFileByUrl(VfsUtilCore.pathToUrl(sdkPath));
            if (sdkRoot != null) {
                String cachedVersion = sdkRoot.getUserData(VERSION_DATA_KEY);
                if (!stringIsNullOrEmpty(cachedVersion)) {
                    return cachedVersion;
                }

                VirtualFile versionFile = sdkRoot.findFileByRelativePath(BALLERINA_VERSION_FILE_PATH);
                if (versionFile == null) {
                    VfsRootAccess.allowRootAccess();
                    versionFile = sdkRoot.findFileByRelativePath(BALLERINA_NEW_VERSION_FILE_PATH);
                }
                // Please note that if the above versionFile is null, we can check on other locations as well.
                if (versionFile != null) {
                    String text = VfsUtilCore.loadText(versionFile);
                    String version = parseBallerinaVersion(text);
                    if (version == null) {
                        LOG.debug("Cannot retrieve Ballerina version from version file: " + text);
                    }
                    sdkRoot.putUserData(VERSION_DATA_KEY, StringUtil.notNullize(version));
                    return version;
                } else {
                    LOG.debug("Cannot find Ballerina version file in sdk path: " + sdkPath);
                }
            }
        } catch (Exception e) {
            LOG.debug("Cannot retrieve Ballerina version from sdk path: " + sdkPath, e);
        }
        return null;
    }

    @Nullable
    public static String getBallerinaPluginVersion() {
        IdeaPluginDescriptor balPluginDescriptor = PluginManager.getPlugin(PluginId.getId(BALLERINA_PLUGIN_ID));
        if (balPluginDescriptor != null) {
            return balPluginDescriptor.getVersion();
        }
        return null;
    }

    @Nullable
    public static String parseBallerinaVersion(@NotNull String text) {
        // Checks for versions prior to Swan Lake release.
        Matcher matcher = BALLERINA_OLD_VERSION_PATTERN.matcher(text);
        if (matcher.find()) {
            return matcher.group(1);
        }
        // Checks for swan lake specific version patterns.
        Matcher swanLakeMatcher = BALLERINA_SWANLAKE_VERSION_PATTERN.matcher(text);
        if (swanLakeMatcher.find()) {
            return BALLERINA_SWAN_LAKE_VERSION_TEXT;
        }
        return null;
    }

    /**
     * @return an empty string if it fails to auto-detect the ballerina home automatically.
     */
    public static String autoDetectSdk(Project project) throws BallerinaCmdException {
        String ballerinaPath = "";
        try {
            ballerinaPath = execBalHomeCmd(String.format("%s %s", BALLERINA_CMD, BALLERINA_HOME_CMD));
        } catch (BallerinaCmdException ignored) {
            // We don nothing here as we need to fallback for default installer specific locations, since "ballerina"
            // commands might not work because of the IntelliJ issue of PATH variable might not being identified by
            // the IntelliJ java runtime.
        }
        if (ballerinaPath.isEmpty()) {
            // Todo - Verify
            // Tries for default installer based locations since "ballerina" commands might not work
            // because of the IntelliJ issue of PATH variable might not being identified by the IntelliJ java
            // runtime.
            String routerScriptPath = getByDefaultPath();
            if (OSUtils.isWindows()) {
                ballerinaPath = execBalHomeCmd(String.format("\"%s\" %s", routerScriptPath, BALLERINA_HOME_CMD));
            } else {
                ballerinaPath = execBalHomeCmd(String.format("%s %s", routerScriptPath, BALLERINA_HOME_CMD));
            }
        }

        return ballerinaPath;
    }

    /**
     * Executes ballerina home command and returns the result.
     *
     * @param cmd command to be executed.
     * @return ballerina distribution location.
     */
    public static String execBalHomeCmd(String cmd) throws BallerinaCmdException {
        java.util.Scanner s;
        // This may returns a symlink which links to the real path.
        try {
            s = new java.util.Scanner(Runtime.getRuntime().exec(cmd).getInputStream()).useDelimiter("\\A");

            String path = s.hasNext() ? s.next().trim().replace(System.lineSeparator(), "").trim() : "";
            LOG.info(String.format("%s command returned: %s", cmd, path));

            // Todo - verify
            // Since errors might be coming from the input stream when retrieving ballerina distribution path, we need
            // an additional check.
            if (!new File(path).exists()) {
                throw new BallerinaCmdException(String.format("unexpected output received by \"%s\" command. " +
                        "received output: %s", cmd, path));
            }
            return path;
        } catch (IOException e) {
            throw new BallerinaCmdException("Unexpected error occurred when executing ballerina command: " + cmd, e);
        }
    }

    /**
     * Tries for default installer based locations since "ballerina" commands might not work
     * because of the IntelliJ issue of PATH variable might not being identified by the IntelliJ java
     * runtime.
     */
    public static String getByDefaultPath() {
        try {
            String path = getDefaultPath();
            if (path.isEmpty()) {
                return path;
            }
            // Resolves actual path using "toRealPath()".
            return new File(path).toPath().toRealPath().toString();
        } catch (Exception e) {
            LOG.warn("Error occurred when resolving symlinks to auto detect ballerina home.");
            return "";
        }
    }

    private static String getDefaultPath() {
        String os = OSUtils.getOperatingSystem();
        switch (os) {
            case UNIX:
                return INSTALLER_PATH_UNIX;
            case MAC:
                return getMacDefaultPath();
            case WINDOWS:
                return getWinDefaultPath();
            default:
                return "";
        }
    }

    private static String getWinDefaultPath() {
        try {
            String pluginVersion = getBallerinaPluginVersion();
            if (pluginVersion == null) {
                return "";
            }
            String routerPath = String.join("-", INSTALLER_PATH_WINDOWS, pluginVersion);
            return Paths.get(routerPath, "bin", "ballerina.bat").toString();
        } catch (Exception e) {
            LOG.warn("Error occurred when trying to auto detect using default installation path.", e);
            return "";
        }
    }

    private static String getMacDefaultPath() {
        try {
            Stream<String> stream = Files.lines(Paths.get(INSTALLER_PATH_MAC), StandardCharsets.UTF_8);
            StringBuilder contentBuilder = new StringBuilder();
            stream.forEach(s -> contentBuilder.append(s).append("\n"));
            String balHomePath = contentBuilder.toString().trim();
            balHomePath = !stringIsNullOrEmpty(balHomePath) && balHomePath.endsWith("/bin") ?
                    balHomePath.replace("/bin", "/bin/ballerina") : "";

            // If a ballerina router script does not exist in this directory, falls-back to homebrew specific symlinks.
            if (balHomePath.isEmpty() || !new File(balHomePath).exists()) {
                return HOMEBREW_PATH_MAC;
            }
            return balHomePath;
        } catch (Exception ignored) {
            return "";
        }
    }

    @NotNull
    private static String getSrcLocation(@NotNull String version) {
        return "src";
    }

    /**
     * Searches for a ballerina project root using outward recursion starting from the file directory, until the given
     * root directory is found. Returns and empty string if unable to detect any ballerina project under the current
     * intellij project source root.
     */
    public static String searchForBallerinaProjectRoot(String currentPath, String root) {

        if (currentPath.isEmpty() || root.isEmpty()) {
            return "";
        }

        File currentDir = new File(currentPath);
        File[] files = currentDir.listFiles();
        if (files != null) {
            for (File f : files) {
                // Searches for the ballerina config file (Ballerina.toml).
                if (f.isFile() && f.getName().equals(BALLERINA_CONFIG_FILE_NAME)) {
                    return currentDir.getAbsolutePath();
                }
            }
        }

        if (currentPath.equals(root) || currentDir.getParentFile() == null) {
            return "";
        }
        return searchForBallerinaProjectRoot(currentDir.getParentFile().getAbsolutePath(), root);
    }

    public static boolean stringIsNullOrEmpty(@Nullable String string) {
        return string == null || string.isEmpty();
    }
}
