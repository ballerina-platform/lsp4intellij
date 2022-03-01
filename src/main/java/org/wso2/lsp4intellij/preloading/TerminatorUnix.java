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

import org.apache.commons.compress.utils.IOUtils;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.Charset;

/**
 * Launcher Terminator Implementation for Unix.
 */
public class TerminatorUnix extends Terminator {

    /**
     * Get find process command.
     *
     * @param script absolute path of ballerina file running
     * @return find process command
     */
    private String[] getFindProcessCommand(String script) {

        String[] cmd = {
                "/bin/sh", "-c", "ps ax | grep " + script + " |  grep -v 'grep' | awk '{print $1}'"
        };
        return cmd;
    }

    public void terminate() {
        terminate(LS_SCRIPT_PROCESS_ID);
        terminate(LS_CMD_PROCESS_ID);
        terminate(DEBUG_SCRIPT_PROCESS_ID);
    }

    /**
     * Terminates a given ballerina process.
     */
    private void terminate(String processName) {
        String[] findProcessCommand = getFindProcessCommand(processName);
        BufferedReader reader = null;
        try {
            Process findProcess = Runtime.getRuntime().exec(findProcessCommand);
            findProcess.waitFor();
            reader = new BufferedReader(new InputStreamReader(findProcess.getInputStream(), Charset.defaultCharset()));

            String line;
            while ((line = reader.readLine()) != null) {
                try {
                    int processID = Integer.parseInt(line);
                    killChildProcesses(processID);
                    kill(processID);
                } catch (Throwable e) {
                    LOGGER.error("Launcher was unable to kill process " + line + ".");
                }
            }
        } catch (Throwable e) {
            LOGGER.error("Launcher was unable to find the process ID for " + processName + ".");
        } finally {
            if (reader != null) {
                IOUtils.closeQuietly(reader);
            }
        }
    }

    /**
     * Terminate running ballerina program.
     *
     * @param pid - process id
     */
    void kill(int pid) {
        //Todo - need to put additional validation
        if (pid < 0) {
            return;
        }
        String killCommand = String.format("kill -9 %d", pid);
        try {
            Process kill = Runtime.getRuntime().exec(killCommand);
            kill.waitFor();
        } catch (Throwable e) {
            LOGGER.error("Launcher was unable to terminate process:" + pid + ".");
        }
    }

    /**
     * Terminate running all child processes for a given pid.
     *
     * @param pid - process id
     */
    void killChildProcesses(int pid) {
        BufferedReader reader = null;
        try {
            Process findChildProcess = Runtime.getRuntime().exec(String.format("pgrep -P %d", pid));
            findChildProcess.waitFor();
            reader = new BufferedReader(
                    new InputStreamReader(findChildProcess.getInputStream(), Charset.defaultCharset()));
            String line;
            int childProcessID;
            while ((line = reader.readLine()) != null) {
                childProcessID = Integer.parseInt(line);
                kill(childProcessID);
            }
        } catch (Throwable e) {
            LOGGER.error("Launcher was unable to find parent for process:" + pid + ".");
        } finally {
            if (reader != null) {
                IOUtils.closeQuietly(reader);
            }
        }
    }
}
