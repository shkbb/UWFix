package ua.uwfix.util;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PluralTest {

    @ParameterizedTest
    @CsvSource({
            "0, 0 файлів",
            "1, 1 файл",
            "2, 2 файли",
            "4, 4 файли",
            "5, 5 файлів",
            "11, 11 файлів",
            "12, 12 файлів",
            "14, 14 файлів",
            "21, 21 файл",
            "22, 22 файли",
            "25, 25 файлів",
            "101, 101 файл",
            "111, 111 файлів"
    })
    void ukrainianPluralForms(long n, String expected) {
        assertEquals(expected, Plural.of(n, "файл", "файли", "файлів"));
    }

    @ParameterizedTest
    @CsvSource({"0, MANY", "1, ONE", "2, MANY", "21, MANY", "101, MANY"})
    void englishHasOnlyOneAndOther(long n, Plural.Category expected) {
        assertEquals(expected, Plural.category(ua.uwfix.i18n.Language.EN, n));
    }
}
