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
package org.wso2.lsp4intellij.client.connection;

import com.intellij.openapi.diagnostic.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;

/**
 * A class symbolizing a stream to a process
 * <p>
 * commands - The commands to start the process
 * workingDir - The working directory of the process
 */
public class ProcessStreamConnectionProvider implements StreamConnectionProvider {

    private Logger LOG = Logger.getInstance(ProcessStreamConnectionProvider.class);

    @Nullable
    private ProcessBuilder builder;
    @Nullable
    private Process process = null;
    private List<String> commands;
    private String workingDir;

    public ProcessStreamConnectionProvider(List<String> commands, String workingDir) {
        this.commands = commands;
        this.workingDir = workingDir;
        this.builder = null;
    }

    public ProcessStreamConnectionProvider(@NotNull ProcessBuilder processBuilder) {
        this.builder = processBuilder;
    }

    public void start() throws IOException {
        if ((workingDir == null || commands == null || commands.isEmpty() || commands.contains(null)) && builder == null) {
            throw new IOException("Unable to start language server: " + this.toString());
        }
        ProcessBuilder builder = createProcessBuilder();
        LOG.info("Starting server process with commands " + commands + " and workingDir " + workingDir);
        process = builder.start();
        if (!process.isAlive()) {
            throw new IOException("Unable to start language server: " + this.toString());
        } else {
            LOG.info("Server process started " + process);
        }
    }

    private ProcessBuilder createProcessBuilder() {
        if (builder != null) {
            return builder;
        } else {
            commands.forEach(c -> c = c.replace("\'", ""));
            ProcessBuilder builder = new ProcessBuilder(commands);
            builder.directory(new File(workingDir));
            builder.redirectError(ProcessBuilder.Redirect.INHERIT);
            return builder;
        }
    }

    @Nullable
    @Override
    public InputStream getInputStream() {
        return process != null ? process.getInputStream() : null;
    }

    @Nullable
    @Override
    public OutputStream getOutputStream() {
        return process != null ? process.getOutputStream() : null;
    }

    public void stop() {
        if (process != null) {
            process.destroy();
        }
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof ProcessStreamConnectionProvider) {
            ProcessStreamConnectionProvider other = (ProcessStreamConnectionProvider) obj;
            return commands.size() == other.commands.size() && new HashSet<>(commands).equals(new HashSet<>(other.commands))
                    && workingDir.equals(other.workingDir);
        }
        return false;
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(commands) ^ Objects.hashCode(workingDir);
    }
}
