package ua.uwfix.search;

import java.util.function.IntConsumer;

/**
 * Прямий перебір: шаблон прикладається до кожної позиції тексту.
 * <p>
 * Найгірший випадок O(n·m), де n — довжина тексту, m — довжина шаблону.
 * Для коротких шаблонів на реальних даних працює непогано, бо невдача
 * зазвичай виявляється вже на першому байті.
 */
public final class NaiveSearch implements ByteSearch {

    @Override
    public void search(byte[] text, int from, int to, byte[] pattern, IntConsumer onMatch) {
        int m = pattern.length;
        for (int i = from; i <= to - m; i++) {
            int j = 0;
            while (j < m && text[i + j] == pattern[j]) {
                j++;
            }
            if (j == m) {
                onMatch.accept(i);
            }
        }
    }

    @Override
    public String name() {
        return "Прямий перебір";
    }
}
