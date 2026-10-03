package ua.uwfix.util;

import ua.uwfix.i18n.Language;

/**
 * Правила множини для узгодження іменників з числівниками.
 * <ul>
 *   <li>українська: 1 файл, 2 файли, 5 файлів (три форми);</li>
 *   <li>англійська: 1 file, 2 files (дві форми).</li>
 * </ul>
 */
public final class Plural {

    /** Форма слова. Назви збігаються з категоріями Unicode CLDR. */
    public enum Category {
        ONE("one"), FEW("few"), MANY("many");

        private final String suffix;

        Category(String suffix) {
            this.suffix = suffix;
        }

        /** Суфікс ключа у словнику: «files.one», «files.few», «files.many». */
        public String suffix() {
            return suffix;
        }
    }

    private Plural() {
    }

    /** Яку форму слова вжити з числом {@code n} у мові {@code language}. */
    public static Category category(Language language, long n) {
        long abs = Math.abs(n);
        if (language == Language.EN) {
            return abs == 1 ? Category.ONE : Category.MANY;
        }
        long mod10 = abs % 10;
        long mod100 = abs % 100;
        if (mod10 == 1 && mod100 != 11) {
            return Category.ONE;
        }
        if (mod10 >= 2 && mod10 <= 4 && (mod100 < 12 || mod100 > 14)) {
            return Category.FEW;
        }
        return Category.MANY;
    }

    /**
     * Число з українською формою слова: {@code of(5, "файл", "файли", "файлів")} → «5 файлів».
     */
    public static String of(long n, String one, String few, String many) {
        String word = switch (category(Language.UK, n)) {
            case ONE -> one;
            case FEW -> few;
            case MANY -> many;
        };
        return n + " " + word;
    }
}
