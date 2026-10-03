package ua.uwfix.update;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VersionTest {

    @ParameterizedTest
    @CsvSource({
            "1.2.0, 1.2.0",
            "v1.2.0, 1.2.0",
            "V2.0, 2.0.0",
            "3, 3.0.0",
            "1.2.0-beta, 1.2.0",
            "1.2.0+build5, 1.2.0"
    })
    void parsesCommonForms(String text, String expected) {
        assertEquals(expected, Version.parse(text).toString());
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "dev", "1.2.3.4", "1.x.0", "v", "-1.0.0"})
    void rejectsInvalidText(String text) {
        assertNull(Version.parse(text));
    }

    @Test
    void comparesNumericallyNotAsText() {
        assertTrue(Version.parse("1.10.0").isNewerThan(Version.parse("1.9.9")));
        assertTrue(Version.parse("2.0.0").isNewerThan(Version.parse("1.99.99")));
        assertTrue(Version.parse("1.1.1").isNewerThan(Version.parse("1.1.0")));
        assertFalse(Version.parse("1.1.0").isNewerThan(Version.parse("1.1")));
        assertNull(Version.parse(null));
    }
}
