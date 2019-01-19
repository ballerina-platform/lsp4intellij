package com.github.lsp4intellij.preloading;

import java.util.Locale;

/**
 * Utility functions used by launcher.
 */
public class OperatingSystemUtils {

    public static final String WINDOWS = "windows";
    public static final String UNIX = "unix";
    public static final String MAC = "mac";

    private static final String OS = System.getProperty("os.name").toLowerCase(Locale.getDefault());

    /**
     * Returns name of the operating system running. null if not a unsupported operating system.
     * @return operating system
     */
    static String getOperatingSystem() {
        if (OperatingSystemUtils.isWindows()) {
            return WINDOWS;
        } else if (OperatingSystemUtils.isUnix() || OperatingSystemUtils.isSolaris()) {
            return UNIX;
        } else if (OperatingSystemUtils.isMac()) {
            return MAC;
        }
        return null;
    }

    private static boolean isWindows() {
        return (OS.contains("win"));
    }

    private static boolean isMac() {
        return (OS.contains("mac"));
    }

    private static boolean isUnix() {
        return (OS.contains("nix") || OS.contains("nux") || OS.contains("aix"));
    }

    private static boolean isSolaris() {
        return (OS.contains("sunos"));
    }
}
