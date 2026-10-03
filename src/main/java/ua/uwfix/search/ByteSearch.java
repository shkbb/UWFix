package ua.uwfix.search;

import java.util.function.IntConsumer;

/**
 * Алгоритм пошуку послідовності байтів (шаблону) у масиві байтів (тексті).
 * <p>
 * Реалізовано три алгоритми, щоб порівняти їх у курсовій:
 * {@link NaiveSearch} (прямий перебір), {@link KmpSearch} (Кнута–Морріса–Пратта)
 * і {@link HorspoolSearch} (Бойєра–Мура–Хорспула, використовується у програмі).
 */
public interface ByteSearch {

    /**
     * Знаходить усі входження шаблону, які повністю лежать у {@code text[from, to)}.
     * Входження можуть перекриватися — фільтрує їх той, хто викликає.
     *
     * @param text    масив, у якому шукаємо
     * @param from    перший індекс області пошуку (включно)
     * @param to      кінець області пошуку (не включно)
     * @param pattern шаблон, довжина ≥ 1
     * @param onMatch викликається з індексом початку кожного входження, по зростанню
     */
    void search(byte[] text, int from, int to, byte[] pattern, IntConsumer onMatch);

    /** Назва алгоритму для звітів і бенчмарку. */
    String name();
}
