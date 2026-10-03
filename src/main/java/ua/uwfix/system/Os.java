package ua.uwfix.system;

import java.util.Locale;

/** Операційна система, на якій працює програма. */
public enum Os {
    WINDOWS, LINUX, OTHER;

    private static final Os CURRENT = detect(System.getProperty("os.name", ""));

    public static Os current() {
        return CURRENT;
    }

    public static boolean isWindows() {
        return CURRENT == WINDOWS;
    }

    public static boolean isLinux() {
        return CURRENT == LINUX;
    }

    static Os detect(String osName) {
        String name = osName.toLowerCase(Locale.ROOT);
        if (name.startsWith("windows")) {
            return WINDOWS;
        }
        if (name.contains("linux")) {
            return LINUX;
        }
        return OTHER;
    }
}
