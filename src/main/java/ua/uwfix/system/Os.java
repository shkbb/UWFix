package ua.uwfix.system;

import java.nio.file.Path;
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

    /** Домашня папка користувача. */
    public static Path home() {
        return Path.of(System.getProperty("user.home"));
    }

    /**
     * Папка налаштувань програм у Linux за стандартом XDG: {@code $XDG_CONFIG_HOME}
     * або {@code ~/.config}. Там лежать налаштування UWFix, автозапуск, налаштування ігор Unity і Unreal.
     */
    public static Path configHome() {
        String xdg = System.getenv("XDG_CONFIG_HOME");
        return xdg != null && !xdg.isBlank() ? Path.of(xdg) : home().resolve(".config");
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
