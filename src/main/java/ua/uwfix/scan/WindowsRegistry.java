package ua.uwfix.scan;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Читання реєстру Windows без нативних бібліотек.
 * <p>
 * У стандартній Java немає API для реєстру, тому викликаємо системну утиліту
 * {@code reg export}: вона записує розділ у файл .reg у кодуванні UTF-16,
 * тож назви ігор будь-якою мовою читаються без спотворень.
 */
public final class WindowsRegistry {

    private static final Map<String, String> ROOT_ALIASES = Map.of(
            "HKLM", "HKEY_LOCAL_MACHINE",
            "HKCU", "HKEY_CURRENT_USER",
            "HKCR", "HKEY_CLASSES_ROOT",
            "HKU", "HKEY_USERS");

    private WindowsRegistry() {
    }

    /** Чи працює програма у Windows (реєстр є лише там). */
    public static boolean isWindows() {
        return ua.uwfix.system.Os.isWindows();
    }

    /**
     * Читає розділ разом з усіма підрозділами.
     *
     * @param key наприклад {@code HKLM\SOFTWARE\WOW6432Node\GOG.com\Games}
     * @return мапа «повний шлях розділу → значення»; порожня, якщо розділу немає
     */
    public static Map<String, Map<String, String>> readTree(String key) {
        if (!isWindows()) {
            return Map.of();
        }
        Path tmp = null;
        try {
            tmp = Files.createTempFile("uwfix-reg-", ".reg");
            Process process = new ProcessBuilder("reg", "export", key, tmp.toString(), "/y")
                    .redirectErrorStream(true)
                    .start();
            process.getInputStream().readAllBytes(); // вичитуємо вивід, щоб процес не завис
            if (!process.waitFor(15, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                return Map.of();
            }
            if (process.exitValue() != 0) {
                return Map.of(); // розділу не існує
            }
            String text = Files.readString(tmp, StandardCharsets.UTF_16);
            return RegFileParser.parse(text);
        } catch (IOException e) {
            return Map.of();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Map.of();
        } finally {
            if (tmp != null) {
                try {
                    Files.deleteIfExists(tmp);
                } catch (IOException ignored) {
                    // тимчасовий файл видалить система
                }
            }
        }
    }

    /** Значення безпосередньо в розділі {@code key} (без підрозділів). */
    public static Map<String, String> readValues(String key) {
        Map<String, Map<String, String>> tree = readTree(key);
        Map<String, String> values = tree.get(expandRoot(key));
        if (values == null) {
            // назви розділів у .reg можуть відрізнятися регістром
            for (Map.Entry<String, Map<String, String>> e : tree.entrySet()) {
                if (e.getKey().equalsIgnoreCase(expandRoot(key))) {
                    return e.getValue();
                }
            }
            return Map.of();
        }
        return values;
    }

    /** Безпосередні підрозділи {@code key}: «ім'я підрозділу → його значення». */
    public static Map<String, Map<String, String>> readSubkeys(String key) {
        String prefix = expandRoot(key).toLowerCase(Locale.ROOT) + "\\";
        Map<String, Map<String, String>> result = new LinkedHashMap<>();
        for (Map.Entry<String, Map<String, String>> e : readTree(key).entrySet()) {
            String full = e.getKey();
            if (full.toLowerCase(Locale.ROOT).startsWith(prefix)) {
                String rest = full.substring(prefix.length());
                if (!rest.isEmpty() && !rest.contains("\\")) {
                    result.put(rest, e.getValue());
                }
            }
        }
        return result;
    }

    /** Записує 32-бітне число (REG_DWORD). Розділ створюється, якщо його немає. */
    public static boolean setDword(String key, String name, long value) {
        if (!isWindows()) {
            return false;
        }
        try {
            Process process = new ProcessBuilder("reg", "add", key, "/v", name, "/t", "REG_DWORD",
                    "/d", String.valueOf(value), "/f").redirectErrorStream(true).start();
            process.getInputStream().readAllBytes();
            return process.waitFor(15, TimeUnit.SECONDS) && process.exitValue() == 0;
        } catch (IOException e) {
            return false;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    /** HKLM\... → HKEY_LOCAL_MACHINE\... */
    static String expandRoot(String key) {
        int slash = key.indexOf('\\');
        String root = slash < 0 ? key : key.substring(0, slash);
        String full = ROOT_ALIASES.get(root.toUpperCase(Locale.ROOT));
        if (full == null) {
            return key;
        }
        return slash < 0 ? full : full + key.substring(slash);
    }
}
