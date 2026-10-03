package ua.uwfix.settings;

import java.nio.file.Path;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Налаштування Unity-ігор у Linux (PlayerPrefs). Реєстру там немає, тож Unity пише їх у XML-файл
 * {@code ~/.config/unity3d/<компанія>/<гра>/prefs}:
 * <pre>
 * &lt;unity_prefs version_major="1" version_minor="1"&gt;
 *     &lt;pref name="Screenmanager Resolution Width" type="int"&gt;1920&lt;/pref&gt;
 *     &lt;pref name="Screenmanager Resolution Height" type="int"&gt;1080&lt;/pref&gt;
 * &lt;/unity_prefs&gt;
 * </pre>
 * Назви ті самі, що в реєстрі Windows, але без хешу «_h182942802».
 * Файл змінюється точково (як і ini), щоб решта налаштувань і форматування лишились як були.
 */
public final class UnityPrefsXml {

    private static final String END_TAG = "</unity_prefs>";

    private UnityPrefsXml() {
    }

    /** {@code ~/.config/unity3d/<компанія>/<гра>/prefs}. */
    public static Path file(Path unity3dDir, String company, String product) {
        return unity3dDir.resolve(company).resolve(product).resolve("prefs");
    }

    /** Ціле значення або {@code null}, якщо його немає. */
    public static Integer getInt(String xml, String name) {
        Matcher m = intPref(name).matcher(xml);
        if (!m.find()) {
            return null;
        }
        try {
            return Integer.parseInt(m.group(2).strip());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * Записує цілі значення: наявні замінює, відсутні дописує перед {@code </unity_prefs>}.
     *
     * @param setIfPresent значення, які змінюються лише якщо вже є у файлі
     */
    public static String update(String xml, Map<String, Integer> setAlways, Map<String, Integer> setIfPresent) {
        String result = xml;
        for (Map.Entry<String, Integer> e : setIfPresent.entrySet()) {
            if (getInt(result, e.getKey()) != null) {
                result = replace(result, e.getKey(), e.getValue());
            }
        }
        for (Map.Entry<String, Integer> e : setAlways.entrySet()) {
            if (getInt(result, e.getKey()) != null) {
                result = replace(result, e.getKey(), e.getValue());
            } else {
                result = insert(result, e.getKey(), e.getValue());
            }
        }
        return result;
    }

    private static String replace(String xml, String name, int value) {
        Matcher m = intPref(name).matcher(xml);
        m.find();
        return xml.substring(0, m.start(2)) + value + xml.substring(m.end(2));
    }

    private static String insert(String xml, String name, int value) {
        String newline = xml.contains("\r\n") ? "\r\n" : "\n";
        String line = "\t<pref name=\"" + escape(name) + "\" type=\"int\">" + value + "</pref>" + newline;
        int end = xml.lastIndexOf(END_TAG);
        if (end < 0) {
            // файл порожній або пошкоджений — створюємо мінімальний правильний
            return "<unity_prefs version_major=\"1\" version_minor=\"1\">" + newline + line + END_TAG + newline;
        }
        return xml.substring(0, end) + line + xml.substring(end);
    }

    /** {@code <pref name="…" type="int">число</pref>}; лапки в атрибутах — подвійні, як пише Unity. */
    private static Pattern intPref(String name) {
        return Pattern.compile("(<pref\\s+name=\"" + Pattern.quote(escape(name)) + "\"\\s+type=\"int\"\\s*>)([^<]*)</pref>");
    }

    private static String escape(String text) {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }
}
