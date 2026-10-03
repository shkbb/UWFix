package ua.uwfix.settings;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Predicate;

/**
 * Редагування ini-файлів з мінімальними змінами: замінюються лише значення потрібних ключів
 * у потрібних секціях, решта файлу (коментарі, порядок, інші секції) лишається як була.
 * <p>
 * Секції ігор Unreal Engine називаються по-різному ([/Script/Engine.GameUserSettings],
 * [/Script/SHProto.SHGameUserSettings] тощо), тому секція задається умовою, а не точною назвою.
 */
public final class IniEditor {

    private IniEditor() {
    }

    /**
     * @param text            вміст файлу
     * @param section         які секції редагувати (умова на назву без дужок)
     * @param defaultSection  куди дописати відсутні ключі, якщо підходящої секції немає
     * @param setAlways       ключі, які мають з'явитися у файлі (замінюються або дописуються)
     * @param setIfPresent    ключі, які замінюються лише якщо вже є
     * @return новий вміст файлу
     */
    public static String update(String text, Predicate<String> section, String defaultSection,
                                Map<String, String> setAlways, Map<String, String> setIfPresent) {
        String newline = text.contains("\r\n") ? "\r\n" : "\n";
        List<String> lines = new ArrayList<>(List.of(text.split("\r?\n", -1)));
        Map<String, String> pending = new LinkedHashMap<>(setAlways);

        int lastMatchingSectionEnd = -1;
        boolean inMatching = false;
        for (int i = 0; i < lines.size(); i++) {
            String trimmed = lines.get(i).strip();
            String name = sectionName(trimmed);
            if (name != null) {
                inMatching = section.test(name);
                continue;
            }
            if (!inMatching) {
                continue;
            }
            if (!trimmed.isEmpty()) {
                lastMatchingSectionEnd = i; // нові ключі дописуємо після останнього непорожнього рядка секції
            }
            int eq = trimmed.indexOf('=');
            if (eq <= 0 || trimmed.startsWith(";")) {
                continue;
            }
            String key = trimmed.substring(0, eq).strip();
            String value = find(setAlways, key);
            if (value == null) {
                value = find(setIfPresent, key);
            }
            if (value != null) {
                lines.set(i, key + "=" + value);
                pending.keySet().removeIf(k -> k.equalsIgnoreCase(key));
            }
        }

        if (!pending.isEmpty()) {
            List<String> additions = new ArrayList<>();
            pending.forEach((k, v) -> additions.add(k + "=" + v));
            if (lastMatchingSectionEnd >= 0) {
                lines.addAll(lastMatchingSectionEnd + 1, additions);
            } else {
                // підходящої секції немає — створюємо нову в кінці файлу
                while (!lines.isEmpty() && lines.get(lines.size() - 1).isBlank()) {
                    lines.remove(lines.size() - 1);
                }
                if (!lines.isEmpty()) {
                    lines.add("");
                }
                lines.add("[" + defaultSection + "]");
                lines.addAll(additions);
                lines.add("");
            }
        }
        return String.join(newline, lines);
    }

    /** Значення ключа в першій підходящій секції або {@code null}. */
    public static String get(String text, Predicate<String> section, String key) {
        boolean inMatching = false;
        for (String line : text.split("\r?\n")) {
            String trimmed = line.strip();
            String name = sectionName(trimmed);
            if (name != null) {
                inMatching = section.test(name);
            } else if (inMatching) {
                int eq = trimmed.indexOf('=');
                if (eq > 0 && trimmed.substring(0, eq).strip().equalsIgnoreCase(key)) {
                    return trimmed.substring(eq + 1).strip();
                }
            }
        }
        return null;
    }

    /** Умова «секція називається саме так» (без урахування регістру). */
    public static Predicate<String> named(String name) {
        return s -> s.equalsIgnoreCase(name);
    }

    /** Умова «назва секції закінчується на …», наприклад «GameUserSettings». */
    public static Predicate<String> endingWith(String suffix) {
        String lower = suffix.toLowerCase(Locale.ROOT);
        return s -> s.toLowerCase(Locale.ROOT).endsWith(lower);
    }

    private static String sectionName(String trimmed) {
        return trimmed.startsWith("[") && trimmed.endsWith("]") ? trimmed.substring(1, trimmed.length() - 1) : null;
    }

    private static String find(Map<String, String> map, String key) {
        for (Map.Entry<String, String> e : map.entrySet()) {
            if (e.getKey().equalsIgnoreCase(key)) {
                return e.getValue();
            }
        }
        return null;
    }
}
