package ua.uwfix.scan;

import ua.uwfix.i18n.I18n;
import ua.uwfix.model.Game;

import java.text.Collator;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Consumer;

/** Збирає ігри з усіх лаунчерів в один відсортований список без дублікатів. */
public final class GameLibrary {

    private final List<GameScanner> scanners;

    public GameLibrary(List<GameScanner> scanners) {
        this.scanners = List.copyOf(scanners);
    }

    /** Бібліотека з усіма підтримуваними лаунчерами. */
    public static GameLibrary withDefaultScanners() {
        return new GameLibrary(List.of(
                new SteamScanner(),
                new EpicScanner(),
                new GogScanner(),
                new UbisoftScanner()));
    }

    /**
     * Опитує всі лаунчери та додає ігри, які користувач додав вручну.
     * Якщо одна папка знайдена кілька разів, лишається перший запис.
     *
     * @param manualGames ігри, додані вручну
     * @param log         куди писати повідомлення для журналу
     */
    public List<Game> scanAll(List<Game> manualGames, Consumer<String> log) {
        List<Game> all = new ArrayList<>();
        Set<String> seenDirs = new HashSet<>();
        for (GameScanner scanner : scanners) {
            try {
                List<Game> found = scanner.scan();
                int added = addUnique(found, all, seenDirs);
                if (added > 0) {
                    log.accept(I18n.t("log.scannerFound", scanner.name(), added));
                }
            } catch (RuntimeException e) {
                log.accept(I18n.t("log.scannerError", scanner.name(), e.getMessage()));
            }
        }
        addUnique(manualGames, all, seenDirs);

        Collator collator = Collator.getInstance(Locale.forLanguageTag("uk"));
        collator.setStrength(Collator.SECONDARY);
        all.sort((a, b) -> collator.compare(a.name(), b.name()));
        return all;
    }

    private static int addUnique(List<Game> source, List<Game> target, Set<String> seenDirs) {
        int added = 0;
        for (Game g : source) {
            String key = g.installDir().toAbsolutePath().normalize().toString().toLowerCase(Locale.ROOT);
            if (seenDirs.add(key)) {
                target.add(g);
                added++;
            }
        }
        return added;
    }
}
