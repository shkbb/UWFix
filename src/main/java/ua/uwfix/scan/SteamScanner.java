package ua.uwfix.scan;

import ua.uwfix.model.Game;
import ua.uwfix.model.GameSource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Ігри Steam.
 * <p>
 * Алгоритм:
 * <ol>
 *   <li>шлях до Steam береться з реєстру ({@code HKCU\Software\Valve\Steam → SteamPath});</li>
 *   <li>з {@code steamapps\libraryfolders.vdf} читаються всі бібліотеки (диски) Steam;</li>
 *   <li>у кожній бібліотеці файл {@code steamapps\appmanifest_<AppID>.acf} описує одну гру:
 *       назву та папку в {@code steamapps\common}.</li>
 * </ol>
 */
public final class SteamScanner implements GameScanner {

    /**
     * Встановлені через Steam програми, які не є іграми: бібліотеки, Proton,
     * SteamVR, утиліти. Їх немає сенсу показувати у списку.
     */
    private static final Set<String> NON_GAME_APP_IDS = Set.of(
            "228980",  // Steamworks Common Redistributables
            "250820",  // SteamVR
            "1070560", // Steam Linux Runtime
            "1391110", // Steam Linux Runtime - Soldier
            "1628350", // Steam Linux Runtime - Sniper
            "431960",  // Wallpaper Engine
            "365670",  // Blender
            "1905180", // OBS Studio
            "400040",  // ShareX
            "629520"); // Soundpad

    private final Path steamRootOverride;

    public SteamScanner() {
        this(null);
    }

    /** @param steamRoot папка Steam (для тестів); {@code null} — визначити автоматично */
    public SteamScanner(Path steamRoot) {
        this.steamRootOverride = steamRoot;
    }

    @Override
    public String name() {
        return "Steam";
    }

    @Override
    public List<Game> scan() {
        Path root = steamRootOverride != null ? steamRootOverride : findSteamRoot();
        if (root == null) {
            return List.of();
        }
        Map<String, Game> games = new LinkedHashMap<>();
        for (Path library : readLibraries(root)) {
            Path steamapps = library.resolve("steamapps");
            try (DirectoryStream<Path> manifests = Files.newDirectoryStream(steamapps, "appmanifest_*.acf")) {
                for (Path manifest : manifests) {
                    Game game = readManifest(steamapps, manifest);
                    if (game != null) {
                        games.putIfAbsent(game.sourceId(), game);
                    }
                }
            } catch (IOException e) {
                // бібліотека на відключеному диску — пропускаємо
            }
        }
        return new ArrayList<>(games.values());
    }

    /** Папка Steam з реєстру або стандартне розташування. */
    static Path findSteamRoot() {
        String path = WindowsRegistry.readValues("HKCU\\Software\\Valve\\Steam").get("SteamPath");
        if (path == null) {
            path = WindowsRegistry.readValues("HKLM\\SOFTWARE\\WOW6432Node\\Valve\\Steam").get("InstallPath");
        }
        if (path != null) {
            Path p = Path.of(path.replace('/', '\\'));
            if (Files.isDirectory(p)) {
                return p;
            }
        }
        Path fallback = Path.of("C:\\Program Files (x86)\\Steam");
        return Files.isDirectory(fallback) ? fallback : null;
    }

    /** Усі бібліотеки Steam (сама папка Steam — теж бібліотека). */
    static Set<Path> readLibraries(Path steamRoot) {
        Set<String> seen = new LinkedHashSet<>();
        Set<Path> libraries = new LinkedHashSet<>();
        addLibrary(steamRoot, seen, libraries);

        Path vdf = steamRoot.resolve("steamapps").resolve("libraryfolders.vdf");
        if (!Files.isRegularFile(vdf)) {
            return libraries;
        }
        try {
            VdfObject root = VdfParser.parse(Files.readString(vdf, StandardCharsets.UTF_8));
            VdfObject folders = root.getObject("libraryfolders");
            if (folders == null) {
                folders = root;
            }
            for (Map.Entry<String, Object> e : folders.entries().entrySet()) {
                if (!e.getKey().chars().allMatch(Character::isDigit)) {
                    continue; // службові поля на кшталт "contentstatsid"
                }
                String path = null;
                if (e.getValue() instanceof VdfObject folder) {
                    path = folder.getString("path");     // новий формат
                } else if (e.getValue() instanceof String s) {
                    path = s;                             // старий формат: "1" "D:\\SteamLibrary"
                }
                if (path != null && !path.isBlank()) {
                    addLibrary(Path.of(path), seen, libraries);
                }
            }
        } catch (IOException | IllegalArgumentException e) {
            // пошкоджений файл — працюємо лише з основною бібліотекою
        }
        return libraries;
    }

    private static void addLibrary(Path path, Set<String> seen, Set<Path> libraries) {
        Path normalized = path.toAbsolutePath().normalize();
        if (seen.add(normalized.toString().toLowerCase(Locale.ROOT))) {
            libraries.add(normalized);
        }
    }

    private static Game readManifest(Path steamapps, Path manifest) {
        try {
            VdfObject root = VdfParser.parse(Files.readString(manifest, StandardCharsets.UTF_8));
            VdfObject app = root.getObject("AppState");
            if (app == null) {
                return null;
            }
            String appId = app.getString("appid");
            String name = app.getString("name");
            String installDir = app.getString("installdir");
            if (appId == null || name == null || installDir == null || isNonGame(appId, name)) {
                return null;
            }
            Path dir = steamapps.resolve("common").resolve(installDir);
            if (!Files.isDirectory(dir)) {
                return null; // гра ще завантажується або видалена
            }
            return new Game(GameSource.STEAM, appId, name, dir);
        } catch (IOException | IllegalArgumentException e) {
            return null;
        }
    }

    static boolean isNonGame(String appId, String name) {
        if (NON_GAME_APP_IDS.contains(appId)) {
            return true;
        }
        String lower = name.toLowerCase(Locale.ROOT);
        return lower.startsWith("proton ") || lower.equals("proton experimental")
                || lower.startsWith("steam linux runtime") || lower.contains("redistributable");
    }
}
