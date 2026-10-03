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
 *   <li>решта ігор: іконка з головного .exe (див. {@link PeIconExtractor});</li>
 *   <li>нативні ігри Linux: у файлах ELF іконок немає, тож беремо картинку, яку гра кладе поруч
 *       ({@link #nativeIcon(Path)}).</li>
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
     * Обкладинка гри зі Steam.
     *
     * @param hero широке фонове зображення (library_hero) або {@code null}
     * @param logo логотип з прозорим тлом (logo.png) або {@code null}
     */
    public record SteamArt(Path hero, Path logo) {
    }

    /**
     * Обкладинка з кешу Steam: {@code librarycache\<AppID>\<hash>\library_hero.jpg} і {@code logo.png}
     * (бувають локалізовані варіанти: library_hero_ukrainian.jpg). Розмите library_hero_blur не беремо.
     */
    public Optional<SteamArt> steamArt(Game game) {
        if (game.source() != GameSource.STEAM || libraryCache == null) {
            return Optional.empty();
        }
        Path dir = libraryCache.resolve(game.sourceId());
        Path hero = findArt(dir, "library_hero", "jpg");
        Path logo = findArt(dir, "logo", "png");
        if (hero == null) {
            Path legacy = libraryCache.resolve(game.sourceId() + "_library_hero.jpg");
            hero = Files.isRegularFile(legacy) ? legacy : null;
        }
        if (logo == null) {
            Path legacy = libraryCache.resolve(game.sourceId() + "_logo.png");
            logo = Files.isRegularFile(legacy) ? legacy : null;
        }
        return hero == null && logo == null ? Optional.empty() : Optional.of(new SteamArt(hero, logo));
    }

    /** Файл «назва.розширення» або «назва_мова.розширення»; варіант без мови — у пріоритеті. */
    private static Path findArt(Path dir, String baseName, String extension) {
        if (!Files.isDirectory(dir)) {
            return null;
        }
        String exact = baseName + "." + extension;
        String pattern = baseName + "(_[a-z]+)?\\." + extension;
        try (Stream<Path> files = Files.walk(dir, 2)) {
            return files
                    .filter(Files::isRegularFile)
                    .filter(p -> {
                        String name = p.getFileName().toString().toLowerCase(Locale.ROOT);
                        return name.matches(pattern) && !name.contains("blur");
                    })
                    .min(Comparator.comparingInt(p -> p.getFileName().toString().equalsIgnoreCase(exact) ? 0 : 1))
                    .orElse(null);
        } catch (IOException e) {
            return null;
        }
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

    /**
     * Іконка нативної гри Linux:
     * <ul>
     *   <li>{@code Гра_Data/Resources/UnityPlayer.png} — іконка вікна ігор Unity;</li>
     *   <li>{@code support/icon.png} — так встановлює ігри GOG.</li>
     * </ul>
     */
    public static Optional<Path> nativeIcon(Path installDir) {
        try (Stream<Path> dirs = Files.list(installDir)) {
            Optional<Path> unity = dirs
                    .filter(p -> p.getFileName().toString().endsWith("_Data"))
                    .map(p -> p.resolve("Resources").resolve("UnityPlayer.png"))
                    .filter(Files::isRegularFile)
                    .findFirst();
            if (unity.isPresent()) {
                return unity;
            }
        } catch (IOException e) {
            return Optional.empty();
        }
        Path gog = installDir.resolve("support").resolve("icon.png");
        return Files.isRegularFile(gog) ? Optional.of(gog) : Optional.empty();
    }

    private static long size(Path p) {
        try {
            return Files.size(p);
        } catch (IOException e) {
            return -1;
        }
    }
}
