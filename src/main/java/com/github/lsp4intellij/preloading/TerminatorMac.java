package com.github.lsp4intellij.preloading;

import org.apache.commons.compress.utils.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.Charset;

/**
 * Launcher Terminator Implementation for Mac.
 */
public class TerminatorMac extends TerminatorUnix {
    private final String processIdentifier = "org.ballerinalang.langserver.launchers.stdio.Main";
    private static final Logger LOGGER = LoggerFactory.getLogger(TerminatorMac.class);

    /**
     * Get find process command.
     *
     * @param script absolute path of ballerina file running
     * @return find process command
     */
    private String[] getFindProcessCommand(String script) {
        String[] cmd = {
                "/bin/sh", "-c", "ps ax | grep " + script + " | grep -v 'grep' | awk '{print $1}'"
        };
        return cmd;
    }

    /**
     * Terminate running ballerina program.
     */
    public void terminate() {
        int processID;
        String[] findProcessCommand = getFindProcessCommand(processIdentifier);
        BufferedReader reader = null;
        try {
            Process findProcess = Runtime.getRuntime().exec(findProcessCommand);
            findProcess.waitFor();
            reader = new BufferedReader(new InputStreamReader(findProcess.getInputStream(), Charset.defaultCharset()));

            String line;
            while ((line = reader.readLine()) != null) {
                try {
                    processID = Integer.parseInt(line);
                    killChildProcesses(processID);
                    kill(processID);
                } catch (Throwable e) {
                    LOGGER.error("Launcher was unable to kill process " + line + ".");
                }
            }
        } catch (Throwable e) {
            LOGGER.error("Launcher was unable to find the process ID for " + processIdentifier + ".");
        } finally {
            if (reader != null) {
                IOUtils.closeQuietly(reader);
            }
        }
    }
}
