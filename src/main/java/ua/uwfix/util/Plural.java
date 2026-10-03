package ua.uwfix.util;

/** Узгодження іменників з числівниками за правилами української мови: 1 файл, 2 файли, 5 файлів. */
public final class Plural {

    private Plural() {
    }

    /**
     * @param n    число
     * @param one  форма для 1, 21, 31… («файл»)
     * @param few  форма для 2–4, 22–24… («файли»)
     * @param many форма для 0, 5–20, 25–30… («файлів»)
     */
    public static String of(long n, String one, String few, String many) {
        long abs = Math.abs(n);
        long mod10 = abs % 10;
        long mod100 = abs % 100;
        String word;
        if (mod10 == 1 && mod100 != 11) {
            word = one;
        } else if (mod10 >= 2 && mod10 <= 4 && (mod100 < 12 || mod100 > 14)) {
            word = few;
        } else {
            word = many;
        }
        return n + " " + word;
    }
}
