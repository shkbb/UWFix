package ua.uwfix.icon;

import ua.uwfix.analysis.GameAnalyzer;
import ua.uwfix.model.Game;
import ua.uwfix.model.GameSource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Знаходить, звідки взяти іконку гри.
 * <ol>
 *   <li>Steam-ігри: Steam сам зберігає іконку кожної гри у
 *       {@code Steam\appcache\librarycache\<AppID>\<40 hex-символів>.jpg}
 *       (у старих версіях Steam — {@code <AppID>_icon.jpg});</li>
 *   <li>решта ігор: іконка з головного .exe (див. {@link PeIconExtractor}).</li>
 * </ol>
 */
public final class GameIconLocator {

    private final Path libraryCache;

    /** @param steamRoot папка Steam або {@code null}, якщо Steam не встановлено */
    public GameIconLocator(Path steamRoot) {
        this.libraryCache = steamRoot == null ? null : steamRoot.resolve("appcache").resolve("librarycache");
    }

    /** Іконка гри з кешу Steam. */
    public Optional<Path> steamIcon(Game game) {
        if (game.source() != GameSource.STEAM || libraryCache == null) {
            return Optional.empty();
        }
        Path dir = libraryCache.resolve(game.sourceId());
        if (Files.isDirectory(dir)) {
            try (Stream<Path> files = Files.list(dir)) {
                Optional<Path> icon = files
                        .filter(Files::isRegularFile)
                        .filter(p -> p.getFileName().toString().toLowerCase(Locale.ROOT).matches("[0-9a-f]{40}\\.jpg"))
                        .findFirst();
                if (icon.isPresent()) {
                    return icon;
                }
            } catch (IOException e) {
                // немає доступу — спробуємо інші варіанти
            }
        }
        Path legacy = libraryCache.resolve(game.sourceId() + "_icon.jpg");
        return Files.isRegularFile(legacy) ? Optional.of(legacy) : Optional.empty();
    }

    /**
     * Головний .exe гри — найбільший виконуваний файл, що не є допоміжною програмою
     * (лаунчером, звітом про збої тощо). Саме в ньому зазвичай лежить іконка гри.
     */
    public static Optional<Path> mainExecutable(Path installDir) {
        try (Stream<Path> files = Files.walk(installDir, 4)) {
            return files
                    .filter(p -> p.getFileName() != null)
                    .filter(p -> {
                        String name = p.getFileName().toString();
                        return name.toLowerCase(Locale.ROOT).endsWith(".exe")
                                && GameAnalyzer.isInteresting(name)
                                && !GameAnalyzer.isAuxiliary(name);
                    })
                    .filter(Files::isRegularFile)
                    .max(Comparator.comparingLong(GameIconLocator::size));
        } catch (IOException | RuntimeException e) {
            return Optional.empty();
        }
    }

    private static long size(Path p) {
        try {
            return Files.size(p);
        } catch (IOException e) {
            return -1;
        }
    }
}
