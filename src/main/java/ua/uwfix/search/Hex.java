package ua.uwfix.search;

import java.util.HexFormat;

/** Перетворення байтів у текст «39 8E E3 3F» і назад. */
public final class Hex {

    private static final HexFormat SPACED = HexFormat.ofDelimiter(" ").withUpperCase();
    private static final HexFormat COMPACT = HexFormat.of().withUpperCase();

    private Hex() {
    }

    /** Байти у вигляді «39 8E E3 3F» — для показу користувачу. */
    public static String spaced(byte[] bytes) {
        return SPACED.formatHex(bytes);
    }

    /** Байти у вигляді «398EE33F» — для збереження у файлі стану. */
    public static String compact(byte[] bytes) {
        return COMPACT.formatHex(bytes);
    }

    /** Розбирає «39 8E E3 3F», «39:8E:E3:3F» або «398EE33F». */
    public static byte[] parse(String text) {
        String digits = text.replaceAll("[\\s:,-]", "");
        if (digits.isEmpty() || digits.length() % 2 != 0) {
            throw new IllegalArgumentException("Некоректний hex-рядок: " + text);
        }
        return COMPACT.parseHex(digits);
    }
}
