package com.github.lsp4intellij.preloading;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Launcher Terminator Implementation for Windows. ( Xp professional SP2++).
 */
public class TerminatorWindows implements Terminator {
    private final String processIdentifier = "org.ballerinalang.langserver.launchers.stdio.Main";
    private static final Logger LOGGER = LoggerFactory.getLogger(TerminatorWindows.class);

    /**
     * @return file process command.
     */
    private String getFindProcessCommand() {
        // Escapes forward slashes.
        return "cmd /c wmic.exe Process where \"Commandline like '%" + processIdentifier + "%'\" CALL TERMINATE";
    }

    /**
     * Terminate running ballerina program.
     */
    public void terminate() {
        String findProcessCommand = getFindProcessCommand();
        try {
            Process findProcess = Runtime.getRuntime().exec(findProcessCommand);
            findProcess.waitFor();
        } catch (Throwable e) {
            LOGGER.error("Launcher was unable to find the process ID for " + processIdentifier + ".");
        }
    }
}
