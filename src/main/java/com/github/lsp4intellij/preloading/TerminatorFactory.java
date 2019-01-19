package com.github.lsp4intellij.preloading;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Launcher Terminator factory.
 */
public class TerminatorFactory {

    private static final Logger LOGGER = LoggerFactory.getLogger(TerminatorFactory.class);

    /**
     * Get Terminator.
     *
     * @param os - Running operating system
     * @return Terminator instance
     */
    public Terminator getTerminator(String os) {
        if ("unix".equalsIgnoreCase(os)) {
            return new TerminatorUnix();
        } else if ("windows".equalsIgnoreCase(os)) {
            return new TerminatorWindows();
        } else if ("mac".equalsIgnoreCase(os)) {
            return new TerminatorMac();
        } else {
            LOGGER.error("Unknown Operating System");
            return null;
        }
    }
}
