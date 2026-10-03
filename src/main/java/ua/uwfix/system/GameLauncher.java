package ua.uwfix.system;

import ua.uwfix.analysis.BinaryFormat;
import ua.uwfix.analysis.GameAnalyzer;
import ua.uwfix.icon.GameIconLocator;
import ua.uwfix.model.Game;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Запуск гри. Ігри з лаунчерів запускаються через сам лаунчер (його посилання-протокол),
 * щоб працювали досягнення, хмарні збереження й захист від копіювання.
 * <ul>
 *   <li>Steam: {@code steam://rungameid/<AppID>}</li>
 *   <li>Epic Games: {@code com.epicgames.launcher://apps/<AppName>?action=launch&silent=true}</li>
 *   <li>Ubisoft Connect: {@code uplay://launch/<id>/0}</li>
 *   <li>GOG і додані вручну: напряму .exe гри</li>
 * </ul>
 * У Linux напряму запускаються лише нативні ігри (див. {@link #nativeLaunchTarget(Path)});
 * версії для Windows там працюють через Proton/Wine — тобто через Steam чи Heroic.
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
     * Чи вміє програма запустити гру: через лаунчер, у Windows — напряму .exe, у Linux — напряму
     * нативну версію. У Linux переглядає файли гри, тож викликається у фоновому потоці.
     */
    public static boolean canLaunch(Game game) {
        return usesLauncher(game) || Os.isWindows() || nativeLaunchTarget(game.installDir()).isPresent();
    }

    public static void launch(Game game) throws IOException {
        String uri = launcherUri(game);
        if (uri != null) {
            SystemShell.openUri(uri);
            return;
        }
        if (!Os.isWindows()) {
            Path target = nativeLaunchTarget(game.installDir())
                    .orElseThrow(() -> new IOException("No Linux executable found in " + game.installDir()));
            new ProcessBuilder(nativeCommand(target)).directory(target.getParent().toFile()).start();
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

    /**
     * Що запускати в Linux, за пріоритетом:
     * <ol>
     *   <li>{@code start.sh} — так встановлюються ігри GOG (скрипт налаштовує бібліотеки й запускає гру);</li>
     *   <li>інший скрипт у корені, наприклад «Гра.sh» в Unreal Engine;</li>
     *   <li>програма ELF у корені (Unity: «Гра.x86_64»);</li>
     *   <li>найбільша програма ELF глибше («Гра/Binaries/Linux/Гра-Linux-Shipping»).</li>
     * </ol>
     */
    static Optional<Path> nativeLaunchTarget(Path installDir) {
        Path start = installDir.resolve("start.sh");
        if (Files.isRegularFile(start)) {
            return Optional.of(start);
        }
        try (Stream<Path> files = Files.list(installDir)) {
            Optional<Path> script = files
                    .filter(p -> {
                        String name = p.getFileName().toString();
                        return name.toLowerCase(Locale.ROOT).endsWith(".sh") && !GameAnalyzer.isAuxiliary(name);
                    })
                    .filter(Files::isRegularFile)
                    .min(Comparator.comparing(Path::getFileName));
            if (script.isPresent()) {
                return script;
            }
        } catch (IOException e) {
            return Optional.empty();
        }
        Optional<Path> root = largestElfProgram(installDir, 1);
        return root.isPresent() ? root : largestElfProgram(installDir, 4);
    }

    private static Optional<Path> largestElfProgram(Path dir, int depth) {
        try (Stream<Path> files = Files.walk(dir, depth)) {
            return files
                    .filter(p -> p.getFileName() != null)
                    .filter(p -> {
                        String name = p.getFileName().toString();
                        return !BinaryFormat.isLibraryName(name) && BinaryFormat.mayBeElfProgramName(name)
                                && GameAnalyzer.isInteresting(name) && !GameAnalyzer.isAuxiliary(name);
                    })
                    .filter(Files::isRegularFile)
                    .filter(p -> BinaryFormat.of(p) == BinaryFormat.ELF)
                    .max(Comparator.comparingLong(GameLauncher::size));
        } catch (IOException | RuntimeException e) {
            return Optional.empty();
        }
    }

    /** Скрипт без права на виконання запускаємо через sh, решту — напряму. */
    static List<String> nativeCommand(Path target) {
        boolean script = target.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".sh");
        return script && !Files.isExecutable(target)
                ? List.of("sh", target.toString())
                : List.of(target.toString());
    }

    private static long size(Path p) {
        try {
            return Files.size(p);
        } catch (IOException e) {
            return -1;
        }
    }
}
