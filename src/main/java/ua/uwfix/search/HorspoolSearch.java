package ua.uwfix.search;

import java.util.Arrays;
import java.util.function.IntConsumer;

/**
 * Алгоритм Бойєра–Мура–Хорспула (1980).
 * <p>
 * Шаблон порівнюється з текстом справа наліво. Якщо не співпало, вікно
 * зсувається одразу на кілька байтів: величину зсуву визначає байт тексту
 * під останнім байтом вікна (таблиця «поганого символу»). Якщо цього байта
 * немає в шаблоні, вікно стрибає на всю довжину шаблону.
 * <p>
 * У середньому переглядається приблизно n/m байтів, тому на великих
 * .exe файлах цей алгоритм найшвидший із трьох.
 */
public final class HorspoolSearch implements ByteSearch {

    @Override
    public void search(byte[] text, int from, int to, byte[] pattern, IntConsumer onMatch) {
        int m = pattern.length;
        int[] shift = badCharacterTable(pattern);
        int last = m - 1;
        int i = from;
        while (i <= to - m) {
            int j = last;
            while (j >= 0 && text[i + j] == pattern[j]) {
                j--;
            }
            if (j < 0) {
                onMatch.accept(i);
            }
            i += shift[text[i + last] & 0xFF];
        }
    }

    /**
     * Таблиця зсувів: для кожного з 256 можливих значень байта — на скільки
     * можна зсунути вікно, якщо цей байт стоїть під останньою позицією вікна.
     */
    static int[] badCharacterTable(byte[] pattern) {
        int m = pattern.length;
        int[] shift = new int[256];
        Arrays.fill(shift, m);
        for (int k = 0; k < m - 1; k++) {
            shift[pattern[k] & 0xFF] = m - 1 - k;
        }
        return shift;
    }

    @Override
    public String name() {
        return "Бойєра–Мура–Хорспула";
    }
}
