package ua.uwfix.i18n;

import ua.uwfix.util.Plural;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.text.MessageFormat;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.ResourceBundle;
import java.util.Set;

/**
 * Переклад текстів інтерфейсу.
 * <p>
 * Тексти лежать у словниках {@code messages_<мова>.properties} (UTF-8) поруч із цим класом.
 * Якщо ключа немає в поточній мові, береться англійський текст; якщо немає й там —
 * повертається сам ключ, щоб пропуск було видно.
 * <p>
 * Параметри підставляються через {@link MessageFormat}: {@code "Знайдено {0}"}.
 * Тому в словниках апостроф пишеться як ’ (U+2019): звичайний ' MessageFormat
 * сприймає як службовий символ.
 */
public final class I18n {

    private static final Map<String, String> FALLBACK = load(Language.EN);

    private static volatile Language current = Language.EN;
    private static volatile Map<String, String> strings = FALLBACK;

    private I18n() {
    }

    /** Перемикає мову для всієї програми. */
    public static synchronized void setLanguage(Language language) {
        current = language;
        strings = language == Language.EN ? FALLBACK : load(language);
    }

    public static Language language() {
        return current;
    }

    public static Locale locale() {
        return current.locale();
    }

    /**
     * Текст за ключем з підставленими параметрами.
     *
     * @param key  ключ у словнику, наприклад {@code "status.fixed.title"}
     * @param args значення для {0}, {1}, …
     */
    public static String t(String key, Object... args) {
        String pattern = strings.get(key);
        if (pattern == null) {
            pattern = FALLBACK.get(key);
        }
        if (pattern == null) {
            return key;
        }
        return args.length == 0 ? pattern : new MessageFormat(pattern, locale()).format(args);
    }

    /**
     * Текст з правильною формою слова для числа {@code n}: шукає ключі
     * {@code key.one}, {@code key.few}, {@code key.many} і підставляє n як {0}.
     */
    public static String plural(String key, long n, Object... moreArgs) {
        Plural.Category category = Plural.category(current, n);
        String fullKey = key + "." + category.suffix();
        if (!strings.containsKey(fullKey) && !FALLBACK.containsKey(fullKey)) {
            fullKey = key + "." + Plural.Category.MANY.suffix(); // в англійській немає форми «few»
        }
        Object[] args = new Object[moreArgs.length + 1];
        args[0] = n;
        System.arraycopy(moreArgs, 0, args, 1, moreArgs.length);
        return t(fullKey, args);
    }

    /** Поточний словник як {@link ResourceBundle} — для FXMLLoader (тексти «%ключ» у main.fxml). */
    public static ResourceBundle bundle() {
        Map<String, String> snapshot = strings;
        return new ResourceBundle() {
            @Override
            protected Object handleGetObject(String key) {
                String value = snapshot.get(key);
                return value != null ? value : FALLBACK.get(key);
            }

            @Override
            public Enumeration<String> getKeys() {
                Set<String> keys = new LinkedHashSet<>(FALLBACK.keySet());
                keys.addAll(snapshot.keySet());
                return Collections.enumeration(keys);
            }
        };
    }

    /** Усі ключі словника мови (для тестів повноти перекладу). */
    public static Map<String, String> dictionary(Language language) {
        return Collections.unmodifiableMap(load(language));
    }

    private static Map<String, String> load(Language language) {
        String name = "messages_" + language.code() + ".properties";
        Properties properties = new Properties();
        try (InputStream in = I18n.class.getResourceAsStream(name)) {
            if (in == null) {
                throw new IllegalStateException("Словник не знайдено: " + name);
            }
            try (Reader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
                properties.load(reader);
            }
        } catch (IOException e) {
            throw new IllegalStateException("Не вдалося прочитати словник " + name, e);
        }
        Map<String, String> map = new HashMap<>();
        for (String key : properties.stringPropertyNames()) {
            map.put(key, properties.getProperty(key));
        }
        return map;
    }
}
