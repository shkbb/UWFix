package ua.uwfix.scan;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Парсер файлів .reg, які створює команда {@code reg export}.
 * <p>
 * Формат:
 * <pre>
 *   [HKEY_LOCAL_MACHINE\SOFTWARE\...\Games\1207664643]
 *   "gameName"="The Witcher 3: Wild Hunt"
 *   "path"="C:\\GOG Games\\The Witcher 3"
 *   "dependsOn"=dword:00000000
 * </pre>
 * Беремо лише рядкові значення (REG_SZ) — інші типи програмі не потрібні.
 */
public final class RegFileParser {

    private RegFileParser() {
    }

    /**
     * @return мапа «повний шлях розділу → (ім'я значення → рядок)»;
     *         значення за замовчуванням має ім'я «@»
     */
    public static Map<String, Map<String, String>> parse(String text) {
        Map<String, Map<String, String>> keys = new LinkedHashMap<>();
        Map<String, String> current = null;
        for (String rawLine : text.split("\r?\n")) {
            String line = rawLine.strip();
            if (line.startsWith("\uFEFF")) {
                line = line.substring(1);
            }
            if (line.startsWith("[") && line.endsWith("]")) {
                current = new LinkedHashMap<>();
                keys.put(line.substring(1, line.length() - 1), current);
            } else if (current != null && (line.startsWith("\"") || line.startsWith("@="))) {
                parseValueLine(line, current);
            }
        }
        return keys;
    }

    private static void parseValueLine(String line, Map<String, String> target) {
        int[] cursor = {0};
        String name;
        if (line.startsWith("@")) {
            name = "@";
            cursor[0] = 1;
        } else {
            name = readQuoted(line, cursor);
            if (name == null) {
                return;
            }
        }
        if (cursor[0] >= line.length() || line.charAt(cursor[0]) != '=') {
            return;
        }
        cursor[0]++;
        if (cursor[0] < line.length() && line.charAt(cursor[0]) == '"') {
            String value = readQuoted(line, cursor);
            if (value != null) {
                target.put(name, value);
            }
        }
    }

    /** Читає рядок у лапках, починаючи з line[cursor], з урахуванням \\ та \". */
    private static String readQuoted(String line, int[] cursor) {
        int i = cursor[0];
        if (i >= line.length() || line.charAt(i) != '"') {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        i++;
        while (i < line.length()) {
            char c = line.charAt(i++);
            if (c == '"') {
                cursor[0] = i;
                return sb.toString();
            }
            if (c == '\\' && i < line.length()) {
                sb.append(line.charAt(i++));
            } else {
                sb.append(c);
            }
        }
        return null;
    }
}
