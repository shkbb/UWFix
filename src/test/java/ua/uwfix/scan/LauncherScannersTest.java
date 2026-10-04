package ua.uwfix.scan;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ua.uwfix.model.Game;
import ua.uwfix.model.GameSource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Сканери лаунчерів на штучних папках (справжні Steam/Epic/GOG не потрібні). */
class LauncherScannersTest {

    @TempDir
    Path dir;

    @Test
    void steamFindsGamesInAllLibrariesAndSkipsTools() throws IOException {
        Path steam = dir.resolve("Steam");
        Path second = dir.resolve("SteamLibrary");
        String vdf = """
                "libraryfolders"
                {
                	"0" { "path" "%s" }
                	"1" { "path" "%s" }
                	"2" { "path" "%s" }
                }
                """.formatted(escape(steam), escape(second), escape(second)); // дубль бібліотеки, як буває насправді
        write(steam.resolve("steamapps/libraryfolders.vdf"), vdf);

        manifest(steam, "292030", "The Witcher 3: Wild Hunt", "The Witcher 3", true);
        manifest(steam, "228980", "Steamworks Common Redistributables", "Steamworks Shared", true);
        manifest(second, "319630", "Life is Strange™", "Life Is Strange", true);
        manifest(second, "1091500", "Cyberpunk 2077", "Cyberpunk 2077", false); // папки немає — не встановлено

        List<Game> games = new SteamScanner(steam).scan();

        assertEquals(List.of("292030", "319630"), games.stream().map(Game::sourceId).toList());
        assertEquals("Life is Strange™", games.get(1).name());
        assertEquals(second.resolve("steamapps/common/Life Is Strange").toAbsolutePath().normalize(),
                games.get(1).installDir().toAbsolutePath().normalize());
        assertTrue(games.stream().allMatch(g -> g.source() == GameSource.STEAM));
    }

    @Test
    void steamReadsLegacyLibraryFormat() throws IOException {
        Path steam = dir.resolve("Steam");
        Path lib = dir.resolve("OldLib");
        write(steam.resolve("steamapps/libraryfolders.vdf"), """
                "LibraryFolders"
                {
                	"TimeNextStatsReport"		"1600000000"
                	"1"		"%s"
                }
                """.formatted(escape(lib)));
        manifest(lib, "10", "Old Game", "Old Game", true);
        assertEquals(List.of("Old Game"), new SteamScanner(steam).scan().stream().map(Game::name).toList());
    }

    @Test
    void epicReadsManifestsAndSkipsNonGames() throws IOException {
        Path manifests = dir.resolve("Manifests");
        Path game = Files.createDirectories(dir.resolve("Games/Hogwarts"));
        Path plugin = Files.createDirectories(dir.resolve("Plugins/Fab"));
        write(manifests.resolve("A.item"), """
                {"DisplayName": "Hogwarts Legacy", "InstallLocation": "%s", "AppName": "fa4240e5",
                 "AppCategories": ["public", "games", "applications"], "bIsIncompleteInstall": false}
                """.formatted(escape(game)));
        write(manifests.resolve("B.item"), """
                {"DisplayName": "Some Plugin", "InstallLocation": "%s", "AppName": "plug",
                 "AppCategories": ["plugins", "engines"]}
                """.formatted(escape(plugin)));
        write(manifests.resolve("C.item"), """
                {"DisplayName": "Half Downloaded", "InstallLocation": "%s", "AppName": "half",
                 "bIsIncompleteInstall": true}
                """.formatted(escape(game)));
        write(manifests.resolve("broken.item"), "{ це не json");

        List<Game> games = new EpicScanner(manifests).scan();
        assertEquals(1, games.size());
        assertEquals("Hogwarts Legacy", games.get(0).name());
        assertEquals("epic:fa4240e5", games.get(0).id());
    }

    @Test
    void gogSkipsDlcAndMissingFolders() throws IOException {
        Path witcher = Files.createDirectories(dir.resolve("GOG/Witcher 3"));
        Map<String, Map<String, String>> registry = new LinkedHashMap<>();
        registry.put("1207664643", Map.of("gameName", "The Witcher 3", "path", witcher.toString(), "dependsOn", ""));
        registry.put("1640424747", Map.of("gameName", "Blood and Wine", "path", witcher.toString(),
                "dependsOn", "1207664643"));
        registry.put("1", Map.of("gameName", "Uninstalled", "path", dir.resolve("nope").toString()));

        List<Game> games = GogScanner.fromRegistry(registry);
        assertEquals(1, games.size());
        assertEquals("gog:1207664643", games.get(0).id());
    }

    @Test
    void libraryMergesSourcesWithoutDuplicates() throws IOException {
        Path a = Files.createDirectories(dir.resolve("a"));
        Path b = Files.createDirectories(dir.resolve("b"));
        GameScanner first = scanner(new Game(GameSource.STEAM, "1", "Бета", a));
        GameScanner second = scanner(new Game(GameSource.EPIC, "x", "Same folder", a),
                new Game(GameSource.EPIC, "y", "Альфа", b));
        GameScanner failing = new GameScanner() {
            @Override
            public String name() {
                return "broken";
            }

            @Override
            public List<Game> scan() {
                throw new IllegalStateException("boom");
            }
        };

        List<Game> games = new GameLibrary(List.of(first, failing, second)).scanAll(List.of(), msg -> { });
        assertEquals(List.of("Альфа", "Бета"), games.stream().map(Game::name).toList());
    }

    @Test
    void nonGameDetection() {
        assertTrue(SteamScanner.isNonGame("228980", "Steamworks Common Redistributables"));
        assertTrue(SteamScanner.isNonGame("1493710", "Proton Experimental"));
        assertTrue(SteamScanner.isNonGame("2805730", "Proton 9.0 (Beta)"));
        assertTrue(SteamScanner.isNonGame("1826330", "Proton EasyAntiCheat Runtime"));
        assertEquals(false, SteamScanner.isNonGame("4343", "Proton Quest"), "гра, що починається з «Proton»");
        assertEquals(false, SteamScanner.isNonGame("292030", "The Witcher 3"));
        assertNull(EpicScanner.readItem(dir.resolve("missing.item")));
    }

    // ------------------------------------------------------------------

    private static GameScanner scanner(Game... games) {
        return new GameScanner() {
            @Override
            public String name() {
                return "test";
            }

            @Override
            public List<Game> scan() {
                return List.of(games);
            }
        };
    }

    private static void manifest(Path library, String appId, String name, String installDir, boolean installed)
            throws IOException {
        write(library.resolve("steamapps/appmanifest_" + appId + ".acf"), """
                "AppState"
                {
                	"appid"		"%s"
                	"name"		"%s"
                	"installdir"		"%s"
                }
                """.formatted(appId, name, installDir));
        if (installed) {
            Files.createDirectories(library.resolve("steamapps/common").resolve(installDir));
        }
    }

    private static void write(Path file, String text) throws IOException {
        Files.createDirectories(file.getParent());
        Files.writeString(file, text, StandardCharsets.UTF_8);
    }

    /** Шлях для VDF/JSON: зворотні скісні риски треба подвоїти. */
    private static String escape(Path p) {
        return p.toAbsolutePath().toString().replace("\\", "\\\\");
    }
}
