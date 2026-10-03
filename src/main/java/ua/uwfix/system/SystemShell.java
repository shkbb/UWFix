package ua.uwfix.system;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Optional;

/**
 * Дії, що залежать від операційної системи: відкрити папку чи посилання, перевірити,
 * чи запущена гра, права адміністратора. На Windows — Провідник і UAC,
 * на Linux — {@code xdg-open} і звичайні права на файли.
 */
public final class SystemShell {

    private SystemShell() {
    }

    /** Відкриває папку (або папку, де лежить файл) у файловому менеджері. */
    public static void reveal(Path path) {
        if (Os.isWindows()) {
            WindowsShell.reveal(path);
        } else {
            Path dir = Files.isDirectory(path) ? path : path.getParent();
            if (dir != null) {
                xdgOpen(dir.toString());
            }
        }
    }

    /**
     * Відкриває посилання програмою, зареєстрованою в системі: https — у браузері,
     * steam:// — у Steam тощо. Дозволені лише відомі схеми.
     */
    public static void openUri(String uri) {
        if (uri == null || !isAllowedScheme(uri)) {
            return;
        }
        try {
            if (Os.isWindows()) {
                if (uri.startsWith("https://")) {
                    new ProcessBuilder("rundll32", "url.dll,FileProtocolHandler", uri).start();
                } else {
                    // Провідник відкриває посилання-протокол програмою, зареєстрованою для нього (Steam, Epic…)
                    new ProcessBuilder("explorer.exe", uri).start();
                }
            } else {
                xdgOpen(uri);
            }
        } catch (IOException ignored) {
            // немає програми для посилання — нічого страшного
        }
    }

    static boolean isAllowedScheme(String uri) {
        String lower = uri.toLowerCase(Locale.ROOT);
        return lower.startsWith("https://") || lower.startsWith("steam://")
                || lower.startsWith("com.epicgames.launcher://") || lower.startsWith("uplay://");
    }

    /**
     * Чи запущена гра з цього файлу. На Windows порівнюється шлях процесу;
     * на Linux гра працює всередині Proton/Wine, тож шукаємо назву .exe у команді чи аргументах процесу.
     */
    public static boolean isRunning(Path exe) {
        if (Os.isWindows()) {
            return WindowsShell.isRunning(exe);
        }
        String name = exe.getFileName().toString().toLowerCase(Locale.ROOT);
        return ProcessHandle.allProcesses().anyMatch(ph -> {
            ProcessHandle.Info info = ph.info();
            if (info.command().map(c -> c.toLowerCase(Locale.ROOT).endsWith(name)).orElse(false)) {
                return true;
            }
            Optional<String[]> args = info.arguments();
            if (args.isEmpty()) {
                return false;
            }
            for (String arg : args.get()) {
                if (arg.toLowerCase(Locale.ROOT).endsWith(name)) {
                    return true;
                }
            }
            return false;
        });
    }

    /** Чи має програма права адміністратора (лише Windows). */
    public static boolean isElevated() {
        return Os.isWindows() && WindowsShell.isElevated();
    }

    /** Чи можна перезапустити програму з підвищеними правами (UAC є лише у Windows). */
    public static boolean canElevate() {
        return Os.isWindows();
    }

    private static void xdgOpen(String target) {
        try {
            new ProcessBuilder("xdg-open", target).start();
        } catch (IOException ignored) {
            // у системі немає xdg-open — нічого страшного
        }
    }
}
