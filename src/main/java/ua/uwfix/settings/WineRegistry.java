package ua.uwfix.settings;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Реєстр Wine/Proton. Розділ HKEY_CURRENT_USER зберігається у текстовому файлі {@code user.reg}:
 * <pre>
 * WINE REGISTRY Version 2
 * ;; All keys relative to \\User\\S-1-5-21-0-0-0-1000
 *
 * [Software\\Valve\\Source\\hl2\\Settings] 1700000000
 * #time=1da1b2c3d4e5f60
 * "ScreenWidth"=dword:00000d70
 * </pre>
 * Зворотні скісні риски в назвах подвоєні, а символи поза ASCII записані як {@code \x0444}.
 * Wine перезаписує файл, коли префікс закривається, тому змінювати його треба, поки гра не запущена.
 */
public final class WineRegistry implements RegistryAccess {

    private static final String BACKUP_SUFFIX = ResolutionUnlocker.BACKUP_SUFFIX;

    private final Path userReg;

    public WineRegistry(Path userReg) {
        this.userReg = userReg;
    }

    public Path file() {
        return userReg;
    }

    @Override
    public Map<String, String> readValues(String key) {
        String relative = hkcuRelative(key);
        List<String> lines = readLines();
        int header = relative == null ? -1 : findSection(lines, relative);
        if (header < 0) {
            return Map.of();
        }
        Map<String, String> values = new LinkedHashMap<>();
        for (int i = header + 1; i < lines.size() && !lines.get(i).startsWith("["); i++) {
            String[] value = parseValue(lines.get(i));
            if (value != null) {
                values.put(value[0], value[1]);
            }
        }
        return values;
    }

    @Override
    public boolean setDword(String key, String name, long value) {
        String relative = hkcuRelative(key);
        if (relative == null || !Files.isRegularFile(userReg)) {
            return false;
        }
        try {
            String text = Files.readString(userReg, StandardCharsets.ISO_8859_1);
            String updated = withDword(text, relative, name, value, System.currentTimeMillis() / 1000);
            Path backup = userReg.resolveSibling(userReg.getFileName() + BACKUP_SUFFIX);
            if (!Files.exists(backup)) {
                Files.copy(userReg, backup, StandardCopyOption.COPY_ATTRIBUTES);
            }
            Files.writeString(userReg, updated, StandardCharsets.ISO_8859_1);
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    /** Новий текст user.reg: значення замінене, дописане в розділ або разом з новим розділом. */
    static String withDword(String text, String relativeKey, String name, long value, long now) {
        String newline = text.contains("\r\n") ? "\r\n" : "\n";
        List<String> lines = new ArrayList<>(Arrays.asList(text.split("\r?\n", -1)));
        String valueLine = "\"" + escape(name, '"') + "\"=dword:"
                + String.format(Locale.ROOT, "%08x", value & 0xFFFFFFFFL);

        int header = findSection(lines, relativeKey);
        if (header < 0) {
            int end = lines.size();
            if (end > 0 && lines.get(end - 1).isEmpty()) {
                end--; // вставляємо перед завершальним порожнім рядком
            }
            lines.addAll(end, List.of("", "[" + escape(relativeKey, ']') + "] " + now, valueLine));
            return String.join(newline, lines);
        }
        int insertAt = header + 1;
        while (insertAt < lines.size() && lines.get(insertAt).startsWith("#")) {
            insertAt++; // службові рядки на кшталт #time= лишаються одразу після заголовка
        }
        for (int i = header + 1; i < lines.size() && !lines.get(i).startsWith("["); i++) {
            String[] existing = parseValue(lines.get(i));
            if (existing != null && existing[0].equalsIgnoreCase(name)) {
                lines.set(i, valueLine);
                return String.join(newline, lines);
            }
        }
        lines.add(insertAt, valueLine);
        return String.join(newline, lines);
    }

    // ------------------------------------------------------------------ формат файлу

    /** HKCU\Software\X → Software\X; інші гілки реєстру в user.reg не зберігаються. */
    static String hkcuRelative(String key) {
        for (String prefix : List.of("HKCU\\", "HKEY_CURRENT_USER\\")) {
            if (key.regionMatches(true, 0, prefix, 0, prefix.length())) {
                return key.substring(prefix.length());
            }
        }
        return null;
    }

    private List<String> readLines() {
        try {
            return Files.isRegularFile(userReg)
                    ? Arrays.asList(Files.readString(userReg, StandardCharsets.ISO_8859_1).split("\r?\n"))
                    : List.of();
        } catch (IOException e) {
            return List.of();
        }
    }

    /** Номер рядка-заголовка {@code [розділ] час} або -1. Назви розділів у Wine не залежать від регістру. */
    static int findSection(List<String> lines, String relativeKey) {
        String wanted = relativeKey.toLowerCase(Locale.ROOT);
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            if (line.startsWith("[")) {
                String[] name = unescapeUntil(line, 1, ']');
                if (name != null && name[0].toLowerCase(Locale.ROOT).equals(wanted)) {
                    return i;
                }
            }
        }
        return -1;
    }

    /**
     * Рядок значення {@code "Назва"=dword:00000780} або {@code "Назва"="текст"} → {назва, дані}.
     * Числа повертаються в десятковому вигляді, як у {@code reg export}. Інші типи пропускаються.
     */
    static String[] parseValue(String line) {
        String name;
        int pos;
        if (line.startsWith("@=")) {
            name = "";
            pos = 2;
        } else if (line.startsWith("\"")) {
            String[] parsed = unescapeUntil(line, 1, '"');
            if (parsed == null) {
                return null;
            }
            name = parsed[0];
            pos = Integer.parseInt(parsed[1]);
            if (pos >= line.length() || line.charAt(pos) != '=') {
                return null;
            }
            pos++;
        } else {
            return null;
        }
        String data = line.substring(pos);
        if (data.startsWith("dword:")) {
            try {
                return new String[]{name, String.valueOf(Long.parseLong(data.substring(6).strip(), 16))};
            } catch (NumberFormatException e) {
                return null;
            }
        }
        if (data.startsWith("\"")) {
            String[] text = unescapeUntil(data, 1, '"');
            return text == null ? null : new String[]{name, text[0]};
        }
        return null;
    }

    /**
     * Розбирає екранований текст від позиції {@code start} до символу {@code end}.
     *
     * @return {текст, позиція після закривного символу} або {@code null}, якщо його немає
     */
    static String[] unescapeUntil(String s, int start, char end) {
        StringBuilder sb = new StringBuilder();
        int i = start;
        while (i < s.length()) {
            char c = s.charAt(i);
            if (c == end) {
                return new String[]{sb.toString(), String.valueOf(i + 1)};
            }
            if (c != '\\' || i + 1 >= s.length()) {
                sb.append(c);
                i++;
                continue;
            }
            char e = s.charAt(i + 1);
            i += 2;
            switch (e) {
                case 'a' -> sb.append('\u0007');
                case 'b' -> sb.append('\b');
                case 'e' -> sb.append('\u001b');
                case 'f' -> sb.append('\f');
                case 'n' -> sb.append('\n');
                case 'r' -> sb.append('\r');
                case 't' -> sb.append('\t');
                case 'v' -> sb.append('\u000b');
                case 'x' -> {
                    int digits = 0;
                    int code = 0;
                    while (digits < 4 && i < s.length() && Character.digit(s.charAt(i), 16) >= 0) {
                        code = code * 16 + Character.digit(s.charAt(i), 16);
                        i++;
                        digits++;
                    }
                    sb.append(digits == 0 ? 'x' : (char) code);
                }
                default -> {
                    if (e >= '0' && e <= '7') {
                        int code = e - '0';
                        for (int d = 0; d < 2 && i < s.length() && s.charAt(i) >= '0' && s.charAt(i) <= '7'; d++) {
                            code = code * 8 + (s.charAt(i++) - '0');
                        }
                        sb.append((char) code);
                    } else {
                        sb.append(e); // \\ \" \] — сам символ
                    }
                }
            }
        }
        return null;
    }

    /** Екранування так само, як це робить Wine (dump_strW). */
    static String escape(String s, char delimiter) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c > 127) {
                boolean hexFollows = i + 1 < s.length() && s.charAt(i + 1) < 128
                        && Character.digit(s.charAt(i + 1), 16) >= 0;
                sb.append(String.format(Locale.ROOT, hexFollows ? "\\x%04x" : "\\x%x", (int) c));
            } else if (c < 32) {
                sb.append(String.format(Locale.ROOT, "\\%03o", (int) c));
            } else {
                if (c == '\\' || c == delimiter) {
                    sb.append('\\');
                }
                sb.append(c);
            }
        }
        return sb.toString();
    }
}
