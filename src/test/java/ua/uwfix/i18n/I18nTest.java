package ua.uwfix.i18n;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import ua.uwfix.analysis.Engine;
import ua.uwfix.model.GameSource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Повнота й коректність перекладу. */
class I18nTest {

    private static final Pattern PLACEHOLDER = Pattern.compile("\\{(\\d+)");

    @AfterEach
    void resetLanguage() {
        I18n.setLanguage(Language.EN);
    }

    @Test
    void bothLanguagesHaveTheSameKeys() {
        Set<String> uk = withoutFewForms(I18n.dictionary(Language.UK).keySet());
        Set<String> en = withoutFewForms(I18n.dictionary(Language.EN).keySet());
        Set<String> missingInEn = new TreeSet<>(uk);
        missingInEn.removeAll(en);
        Set<String> missingInUk = new TreeSet<>(en);
        missingInUk.removeAll(uk);
        assertTrue(missingInEn.isEmpty(), "Немає англійського перекладу: " + missingInEn);
        assertTrue(missingInUk.isEmpty(), "Немає українського перекладу: " + missingInUk);
    }

    @Test
    void ukrainianPluralsHaveAllThreeForms() {
        Map<String, String> uk = I18n.dictionary(Language.UK);
        for (String key : uk.keySet()) {
            if (key.endsWith(".one")) {
                String base = key.substring(0, key.length() - 4);
                assertTrue(uk.containsKey(base + ".few") && uk.containsKey(base + ".many"),
                        "Неповні форми множини: " + base);
            }
        }
    }

    @Test
    void translationsUseTheSameParameters() {
        Map<String, String> uk = I18n.dictionary(Language.UK);
        Map<String, String> en = I18n.dictionary(Language.EN);
        for (Map.Entry<String, String> e : en.entrySet()) {
            String ukValue = uk.get(e.getKey());
            if (ukValue != null) {
                assertEquals(placeholders(e.getValue()), placeholders(ukValue), "Параметри не збігаються: " + e.getKey());
            }
        }
    }

    @Test
    void noPlainApostrophes() {
        for (Language language : Language.values()) {
            for (Map.Entry<String, String> e : I18n.dictionary(language).entrySet()) {
                assertFalse(e.getValue().contains("'"),
                        language + ": замість ' треба ’ у ключі " + e.getKey());
            }
        }
    }

    @Test
    void pluralFormsAreChosenPerLanguage() {
        I18n.setLanguage(Language.UK);
        assertEquals("1 файл", I18n.plural("count.files", 1));
        assertEquals("3 файли", I18n.plural("count.files", 3));
        assertEquals("11 файлів", I18n.plural("count.files", 11));
        I18n.setLanguage(Language.EN);
        assertEquals("1 file", I18n.plural("count.files", 1));
        assertEquals("3 files", I18n.plural("count.files", 3));
    }

    /** Кожен ключ, який використовують FXML і код, має бути у словнику. */
    @Test
    void everyUsedKeyExists() throws Exception {
        Map<String, String> en = I18n.dictionary(Language.EN);
        Set<String> missing = new TreeSet<>();

        String fxml = Files.readString(Path.of("src/main/resources/ua/uwfix/ui/main.fxml"));
        Matcher fx = Pattern.compile("=\"%([\\w.]+)\"").matcher(fxml);
        while (fx.find()) {
            if (!en.containsKey(fx.group(1))) {
                missing.add("main.fxml: " + fx.group(1));
            }
        }

        Pattern literal = Pattern.compile("\"([a-z][a-zA-Z]*(?:\\.[a-zA-Z]+)+)\"");
        try (Stream<Path> files = Files.walk(Path.of("src/main/java"))) {
            for (Path file : files.filter(p -> p.toString().endsWith(".java")).toList()) {
                for (String line : Files.readAllLines(file)) {
                    // ключі передаються в I18n.t/plural або вибираються у switch: case X -> "ключ"
                    if (!line.contains("I18n.") && !line.matches(".*case .*-> \".*")) {
                        continue;
                    }
                    Matcher m = literal.matcher(line);
                    while (m.find()) {
                        String key = m.group(1);
                        if (key.matches(".*\\.(fxml|css|png|ico|json|log|exe|dll|properties)")) {
                            continue; // ім'я файлу, а не ключ перекладу
                        }
                        if (!en.containsKey(key) && !en.containsKey(key + ".one")) {
                            missing.add(file.getFileName() + ": " + key);
                        }
                    }
                }
            }
        }
        assertTrue(missing.isEmpty(), "Ключів немає у словнику: " + missing);
    }

    /** Ключі рушіїв і джерел складаються динамічно, тому перевіряємо їх окремо. */
    @Test
    void enginesAndSourcesAreTranslated() {
        for (Language language : Language.values()) {
            I18n.setLanguage(language);
            for (Engine engine : Engine.values()) {
                assertFalse(engine.displayName().startsWith("engine."), language + ": " + engine);
                assertFalse(engine.hint().startsWith("engine."), language + ": " + engine);
            }
            for (GameSource source : GameSource.values()) {
                assertFalse(source.displayName().startsWith("source."), language + ": " + source);
            }
        }
    }

    @Test
    void missingKeyIsVisible() {
        assertEquals("no.such.key", I18n.t("no.such.key"));
    }

    @Test
    void bundleServesFxmlTexts() {
        I18n.setLanguage(Language.UK);
        assertEquals("UWFix — катсцени без чорних смуг", I18n.bundle().getString("app.title"));
    }

    @Test
    void languageDetection() {
        assertEquals(Language.UK, Language.detect("uk"));
        assertEquals(Language.EN, Language.detect("EN"));
        assertEquals(Language.UK, Language.fromCode("uk"));
        assertEquals(null, Language.fromCode("xx"));
    }

    private static Set<String> withoutFewForms(Set<String> keys) {
        Set<String> result = new TreeSet<>();
        for (String k : keys) {
            if (!k.endsWith(".few")) {
                result.add(k);
            }
        }
        return result;
    }

    private static Set<String> placeholders(String text) {
        Set<String> result = new TreeSet<>();
        Matcher m = PLACEHOLDER.matcher(text);
        while (m.find()) {
            result.add(m.group(1));
        }
        return result;
    }
}
