package ua.uwfix.scan;

import ua.uwfix.model.Game;

import java.util.List;

/** Знаходить ігри, встановлені через певний лаунчер. */
public interface GameScanner {

    /** Назва лаунчера для журналу. */
    String name();

    /**
     * Повертає знайдені ігри. Помилки читання окремих ігор не мають зупиняти
     * сканування — такі ігри просто пропускаються.
     */
    List<Game> scan();
}
