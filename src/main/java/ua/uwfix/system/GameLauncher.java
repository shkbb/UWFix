package ua.uwfix.system;

import ua.uwfix.analysis.GameAnalyzer;
import ua.uwfix.icon.GameIconLocator;
import ua.uwfix.model.Game;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Optional;

/**
 * Запуск гри. Ігри з лаунчерів запускаються через сам лаунчер (його посилання-протокол),
 * щоб працювали досягнення, хмарні збереження й захист від копіювання.
 * <ul>
 *   <li>Steam: {@code steam://rungameid/<AppID>}</li>
 *   <li>Epic Games: {@code com.epicgames.launcher://apps/<AppName>?action=launch&silent=true}</li>
 *   <li>Ubisoft Connect: {@code uplay://launch/<id>/0}</li>
 *   <li>GOG і додані вручну: напряму .exe гри</li>
 * </ul>
 */
public final class GameLauncher {

    private GameLauncher() {
    }

    /** Адреса для запуску через лаунчер або {@code null}, якщо гру запускаємо напряму. */
    static String launcherUri(Game game) {
        return launcherUri(game, Os.current());
    }

    static String launcherUri(Game game, Os os) {
        String id = game.sourceId();
        return switch (game.source()) {
            case STEAM -> id.matches("\\d+") ? "steam://rungameid/" + id : null;
            // Лаунчери Epic і Ubisoft існують лише для Windows
            case EPIC -> os == Os.WINDOWS ? "com.epicgames.launcher://apps/" + URLEncoder.encode(id, StandardCharsets.UTF_8)
                    + "?action=launch&silent=true" : null;
            case UBISOFT -> os == Os.WINDOWS && id.matches("\\d+") ? "uplay://launch/" + id + "/0" : null;
            default -> null;
        };
    }

    /** Чи запускається гра через лаунчер (а не напряму .exe). */
    public static boolean usesLauncher(Game game) {
        return launcherUri(game) != null;
    }

    /**
     * Чи вміє програма запустити гру. На Linux ігри для Windows працюють лише через Proton,
     * тож запуск можливий тільки через Steam.
     */
    public static boolean canLaunch(Game game) {
        return usesLauncher(game) || Os.isWindows();
    }

    public static void launch(Game game) throws IOException {
        String uri = launcherUri(game);
        if (uri != null) {
            SystemShell.openUri(uri);
            return;
        }
        Path exe = launchExecutable(game.installDir())
                .orElseThrow(() -> new IOException("No executable found in " + game.installDir()));
        new ProcessBuilder(exe.toString()).directory(exe.getParent().toFile()).start();
    }

    /**
     * Файл для запуску: .exe у корені папки гри (там зазвичай стартовий файл, який
     * передає грі потрібні параметри), інакше — головний .exe глибше.
     */
    static Optional<Path> launchExecutable(Path installDir) {
        Path best = null;
        long bestSize = -1;
        try (DirectoryStream<Path> files = Files.newDirectoryStream(installDir, "*.exe")) {
            for (Path p : files) {
                String name = p.getFileName().toString();
                if (GameAnalyzer.isInteresting(name) && !GameAnalyzer.isAuxiliary(name)
                        && !name.toLowerCase(Locale.ROOT).startsWith("unity")) {
                    long size = Files.size(p);
                    if (size > bestSize) {
                        best = p;
                        bestSize = size;
                    }
                }
            }
        } catch (IOException e) {
            // перейдемо до пошуку глибше
        }
        return best != null ? Optional.of(best) : GameIconLocator.mainExecutable(installDir);
    }
}
