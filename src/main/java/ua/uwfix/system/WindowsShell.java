package ua.uwfix.system;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/** Взаємодія з Windows: права адміністратора, Провідник, запущені процеси. */
public final class WindowsShell {

    /** SID «High Mandatory Level» — процес запущено з правами адміністратора. */
    private static final String HIGH_INTEGRITY_SID = "S-1-16-12288";

    private WindowsShell() {
    }

    /** Чи має програма права адміністратора. */
    public static boolean isElevated() {
        try {
            Process p = new ProcessBuilder("whoami", "/groups").redirectErrorStream(true).start();
            String out = new String(p.getInputStream().readAllBytes(), Charset.defaultCharset());
            p.waitFor(5, TimeUnit.SECONDS);
            return out.contains(HIGH_INTEGRITY_SID);
        } catch (IOException e) {
            return false;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    /**
     * Перезапускає програму з правами адміністратора (Windows покаже запит UAC).
     *
     * @return {@code true}, якщо новий процес запущено — тоді поточний треба закрити
     */
    public static boolean relaunchElevated() {
        ProcessHandle.Info info = ProcessHandle.current().info();
        Optional<String> command = info.command();
        if (command.isEmpty()) {
            return false;
        }
        List<String> args = new ArrayList<>();
        String exe = command.get();
        String name = Path.of(exe).getFileName().toString().toLowerCase(Locale.ROOT);
        if (name.equals("java.exe") || name.equals("javaw.exe")) {
            // запуск під час розробки — треба передати всі аргументи JVM
            info.arguments().ifPresent(a -> args.addAll(List.of(a)));
        }
        StringBuilder ps = new StringBuilder("Start-Process -FilePath ")
                .append(psQuote(exe)).append(" -Verb RunAs");
        if (!args.isEmpty()) {
            ps.append(" -ArgumentList @(");
            for (int i = 0; i < args.size(); i++) {
                if (i > 0) {
                    ps.append(',');
                }
                // аргументи з пробілами (шляхи) треба взяти в подвійні лапки для командного рядка
                String arg = args.get(i);
                ps.append(psQuote(arg.contains(" ") ? "\"" + arg + "\"" : arg));
            }
            ps.append(')');
        }
        return powershell(ps.toString());
    }

    /**
     * Виконує скрипт PowerShell. Скрипт передається через -EncodedCommand (Base64 від UTF-16LE),
     * тож лапки та пробіли всередині не ламають командний рядок.
     */
    public static boolean powershell(String script) {
        String encoded = java.util.Base64.getEncoder()
                .encodeToString(script.getBytes(java.nio.charset.StandardCharsets.UTF_16LE));
        try {
            Process p = new ProcessBuilder("powershell", "-NoProfile", "-NonInteractive", "-EncodedCommand", encoded)
                    .redirectErrorStream(true).start();
            p.getInputStream().readAllBytes();
            return p.waitFor(60, TimeUnit.SECONDS) && p.exitValue() == 0;
        } catch (IOException e) {
            return false;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    /** Рядок PowerShell в одинарних лапках (усередині вони подвоюються). */
    public static String psQuote(String s) {
        return "'" + s.replace("'", "''") + "'";
    }

    /** Відкриває Провідник і виділяє файл або відкриває папку. */
    public static void reveal(Path path) {
        try {
            if (path.toFile().isDirectory()) {
                new ProcessBuilder("explorer.exe", path.toString()).start();
            } else {
                new ProcessBuilder("explorer.exe", "/select," + path).start();
            }
        } catch (IOException ignored) {
            // Провідник недоступний — нічого страшного
        }
    }

    /** Чи запущено процес з цього виконуваного файлу. */
    public static boolean isRunning(Path exe) {
        String target = exe.toAbsolutePath().normalize().toString();
        return ProcessHandle.allProcesses()
                .map(ph -> ph.info().command().orElse(""))
                .anyMatch(cmd -> cmd.equalsIgnoreCase(target));
    }
}
