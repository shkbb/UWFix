package ua.uwfix.model;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import ua.uwfix.search.Hex;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AspectRatioTest {

    /** Ці значення спільнота використовує для ручного hex-редагування (див. UltraAspect). */
    @ParameterizedTest(name = "{0}×{1} → {2}")
    @CsvSource({
            "16, 9, 39 8E E3 3F",
            "1920, 1080, 39 8E E3 3F",
            "2560, 1080, 26 B4 17 40",
            "3440, 1440, 8E E3 18 40",
            "3840, 1600, 9A 99 19 40",
            "5120, 1440, 39 8E 63 40",
            "1920, 1200, CD CC CC 3F",
            "3840, 1200, CD CC 4C 40"
    })
    void float32BytesMatchKnownHexValues(int width, int height, String expectedHex) {
        assertEquals(expectedHex, Hex.spaced(ValueFormat.FLOAT32.encode(new AspectRatio(width, height))));
    }

    @Test
    void float64IsLittleEndianDouble() {
        // 16/9 = 0x3FFC71C71C71C71C
        assertEquals("1C C7 71 1C C7 71 FC 3F", Hex.spaced(ValueFormat.FLOAT64.encode(AspectRatio.STANDARD)));
    }

    @Test
    void standardDetection() {
        assertTrue(new AspectRatio(1920, 1080).isStandard());
        assertTrue(new AspectRatio(3840, 2160).isStandard());
        assertFalse(new AspectRatio(3440, 1440).isStandard());
    }

    @Test
    void marketingNames() {
        assertEquals("21:9", new AspectRatio(3440, 1440).marketingName());
        assertEquals("21:9", new AspectRatio(2560, 1080).marketingName());
        assertEquals("32:9", new AspectRatio(5120, 1440).marketingName());
        assertEquals("16:9", new AspectRatio(2560, 1440).marketingName());
        assertEquals("2.389", new AspectRatio(3440, 1440).valueText());
    }

    @Test
    void rejectsNonPositiveSizes() {
        assertThrows(IllegalArgumentException.class, () -> new AspectRatio(0, 1080));
        assertThrows(IllegalArgumentException.class, () -> new AspectRatio(1920, -1));
    }
}
