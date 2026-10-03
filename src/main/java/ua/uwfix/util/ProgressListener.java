package ua.uwfix.util;

/**
 * Отримує повідомлення про хід тривалої операції (сканування, патчинг).
 * Через нього ж операцію можна скасувати.
 */
@FunctionalInterface
public interface ProgressListener {

    /** Слухач, який нічого не робить. */
    ProgressListener NONE = (fraction, message) -> { };

    /**
     * @param fraction частка виконаної роботи від 0 до 1, або -1, якщо невідомо
     * @param message  що зараз відбувається
     */
    void update(double fraction, String message);

    /** Чи попросив користувач зупинити операцію. */
    default boolean isCancelled() {
        return false;
    }

    /** Кидає {@link java.util.concurrent.CancellationException}, якщо операцію скасовано. */
    default void checkCancelled() {
        if (isCancelled()) {
            throw new java.util.concurrent.CancellationException("Операцію скасовано");
        }
    }
}
