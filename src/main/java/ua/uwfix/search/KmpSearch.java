package ua.uwfix.search;

import java.util.function.IntConsumer;

/**
 * Алгоритм Кнута–Морріса–Пратта (1977).
 * <p>
 * Спершу будується префікс-функція шаблону: для кожної позиції j — довжина
 * найдовшого власного префікса p[0..j], який одночасно є його суфіксом.
 * Завдяки їй при неспівпадінні не треба повертатися назад у тексті:
 * кожен байт тексту читається один раз, складність O(n + m).
 */
public final class KmpSearch implements ByteSearch {

    @Override
    public void search(byte[] text, int from, int to, byte[] pattern, IntConsumer onMatch) {
        int m = pattern.length;
        int[] prefix = prefixFunction(pattern);
        int j = 0; // скільки байтів шаблону вже співпало
        for (int i = from; i < to; i++) {
            while (j > 0 && text[i] != pattern[j]) {
                j = prefix[j - 1];
            }
            if (text[i] == pattern[j]) {
                j++;
            }
            if (j == m) {
                onMatch.accept(i - m + 1);
                j = prefix[j - 1];
            }
        }
    }

    /** Префікс-функція: prefix[j] — довжина найдовшої межі рядка pattern[0..j]. */
    static int[] prefixFunction(byte[] pattern) {
        int[] prefix = new int[pattern.length];
        int k = 0;
        for (int j = 1; j < pattern.length; j++) {
            while (k > 0 && pattern[j] != pattern[k]) {
                k = prefix[k - 1];
            }
            if (pattern[j] == pattern[k]) {
                k++;
            }
            prefix[j] = k;
        }
        return prefix;
    }

    @Override
    public String name() {
        return "Кнута–Морріса–Пратта";
    }
}
