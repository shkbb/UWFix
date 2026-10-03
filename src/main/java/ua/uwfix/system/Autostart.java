package ua.uwfix.system;

import ua.uwfix.scan.WindowsRegistry;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * Автозапуск перевірки при вході в систему: програма тихо перевіряє, чи не оновились ігри,
 * і за потреби застосовує фікс знову. Права адміністратора не потрібні.
 * <ul>
 *   <li>Windows: команда {@code "UWFix.exe" --reapply} у розділі реєстру
 *       {@code HKCU\Software\Microsoft\Windows\CurrentVersion\Run};</li>
 *   <li>Linux: файл {@code ~/.config/autostart/uwfix-reapply.desktop} (стандарт XDG Autostart,
 *       його виконують GNOME, KDE, Xfce та інші робочі столи).</li>
 * </ul>
 */
public final class Autostart {

    private static final String RUN_KEY = "HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Run";
    private static final String PS_RUN_PATH = "HKCU:\\Software\\Microsoft\\Windows\\CurrentVersion\\Run";
    private static final String VALUE_NAME = "UWFix";
    private static final String DESKTOP_FILE = "uwfix-reapply.desktop";
    public static final String REAPPLY_ARG = "--reapply";

    private Autostart() {
    }

    public static boolean isEnabled() {
        if (Os.isWindows()) {
            return WindowsRegistry.readValues(RUN_KEY).containsKey(VALUE_NAME);
        }
        return Files.isRegularFile(desktopFile());
    }

    /** @return {@code true}, якщо вдалося увімкнути */
    public static boolean enable() {
        Optional<List<String>> command = currentLaunchCommand();
        if (command.isEmpty()) {
            return false;
        }
        if (!Os.isWindows()) {
            try {
                Path file = desktopFile();
                Files.createDirectories(file.getParent());
                Files.writeString(file, desktopEntry(command.get()), StandardCharsets.UTF_8);
                return true;
            } catch (IOException e) {
                return false;
            }
        }
        List<String> quoted = command.get().stream().map(Autostart::quote).toList();
        // Значення містить лапки, тому пишемо через PowerShell (див. WindowsShell.powershell)
        return WindowsShell.powershell("Set-ItemProperty -Path " + WindowsShell.psQuote(PS_RUN_PATH)
                + " -Name " + WindowsShell.psQuote(VALUE_NAME)
                + " -Value " + WindowsShell.psQuote(String.join(" ", quoted)));
    }

    public static boolean disable() {
        if (!Os.isWindows()) {
            try {
                Files.deleteIfExists(desktopFile());
                return true;
            } catch (IOException e) {
                return false;
            }
        }
        return run("reg", "delete", RUN_KEY, "/v", VALUE_NAME, "/f");
    }

    /**
     * Команда запуску поточної програми з аргументом {@code --reapply}.
     * Для встановленої програми це «UWFix.exe» (у Linux — «bin/UWFix»), під час розробки —
     * java з усіма аргументами JVM.
     */
    static Optional<List<String>> currentLaunchCommand() {
        ProcessHandle.Info info = ProcessHandle.current().info();
        if (info.command().isEmpty()) {
            return Optional.empty();
        }
        String exe = info.command().get();
        List<String> parts = new ArrayList<>();
        parts.add(exe);
        String name = Path.of(exe).getFileName().toString().toLowerCase(Locale.ROOT);
        if (name.equals("java.exe") || name.equals("javaw.exe") || name.equals("java")) {
            for (String arg : info.arguments().orElse(new String[0])) {
                if (arg.startsWith("--snapshot") || arg.startsWith("--select") || arg.equals(REAPPLY_ARG)) {
                    continue;
                }
                parts.add(arg);
            }
        }
        parts.add(REAPPLY_ARG);
        return Optional.of(parts);
    }

    // ------------------------------------------------------------------ Linux

    /** {@code $XDG_CONFIG_HOME/autostart} або {@code ~/.config/autostart}. */
    static Path desktopFile() {
        String xdg = System.getenv("XDG_CONFIG_HOME");
        Path config = xdg != null && !xdg.isBlank()
                ? Path.of(xdg)
                : Path.of(System.getProperty("user.home"), ".config");
        return config.resolve("autostart").resolve(DESKTOP_FILE);
    }

    static String desktopEntry(List<String> command) {
        List<String> args = command.stream().map(Autostart::desktopQuote).toList();
        return String.join("\n",
                "[Desktop Entry]",
                "Type=Application",
                "Name=UWFix",
                "Comment=Re-applies the ultrawide fix after game updates",
                "Exec=" + String.join(" ", args),
                "Terminal=false",
                "NoDisplay=true",
                "X-GNOME-Autostart-enabled=true",
                "");
    }

    /**
     * Аргумент для рядка Exec за специфікацією Desktop Entry: у подвійних лапках екрануються
     * {@code " ` $ \}, а потім (як у будь-якому рядку .desktop) ще раз подвоюється зворотна риска.
     */
    static String desktopQuote(String arg) {
        if (!arg.isEmpty() && arg.chars().noneMatch(c -> " \t\n\"'\\><~|&;$*?#()`%".indexOf(c) >= 0)) {
            return arg;
        }
        StringBuilder sb = new StringBuilder("\"");
        for (char c : arg.toCharArray()) {
            if (c == '"' || c == '`' || c == '$' || c == '\\') {
                sb.append('\\');
            }
            sb.append(c == '%' ? "%%" : String.valueOf(c));
        }
        return sb.append('"').toString().replace("\\", "\\\\");
    }

    // ------------------------------------------------------------------ Windows

    private static String quote(String s) {
        return s.contains(" ") ? "\"" + s + "\"" : s;
    }

    private static boolean run(String... command) {
        try {
            Process p = new ProcessBuilder(command).redirectErrorStream(true).start();
            p.getInputStream().readAllBytes();
            return p.waitFor(10, TimeUnit.SECONDS) && p.exitValue() == 0;
        } catch (IOException e) {
            return false;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }
}
