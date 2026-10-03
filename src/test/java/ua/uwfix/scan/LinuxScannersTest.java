package ua.uwfix.scan;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ua.uwfix.model.Game;
import ua.uwfix.model.GameSource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Пошук ігор на Linux: кілька копій Steam і Heroic (Epic + GOG) на штучній домашній папці. */
class LinuxScannersTest {

    @TempDir
    Path home;

    @Test
    void findsNativeAndFlatpakSteam() throws IOException {
        Path nativeSteam = Files.createDirectories(home.resolve(".local/share/Steam/steamapps")).getParent();
        Path flatpak = Files.createDirectories(
                home.resolve(".var/app/com.valvesoftware.Steam/.local/share/Steam/steamapps")).getParent();
        Files.createDirectories(home.resolve(".steam/root")); // без steamapps — не Steam

        assertEquals(List.of(nativeSteam, flatpak), SteamScanner.linuxSteamRoots(home));
    }

    @Test
    void noSteamOnLinux() {
        assertTrue(SteamScanner.linuxSteamRoots(home).isEmpty());
    }

    @Test
    void heroicReadsEpicAndGogGames() throws IOException {
        Path hades = Files.createDirectories(home.resolve("Games/Heroic/Hades"));
        Path witcher = Files.createDirectories(home.resolve("Games/Heroic/The Witcher 3 Wild Hunt"));
        Path removed = home.resolve("Games/Heroic/Removed");

        write(home.resolve(".config/heroic/legendaryConfig/legendary/installed.json"), """
                {
                  "Min": {"app_name": "Min", "title": "Hades", "install_path": "%s", "is_dlc": false},
                  "Gone": {"app_name": "Gone", "title": "Removed Game", "install_path": "%s"}
                }
                """.formatted(json(hades), json(removed)));
        write(home.resolve(".config/heroic/gog_store/installed.json"), """
                {"installed": [
                  {"appName": "1207664643", "install_path": "%s", "is_dlc": false, "platform": "windows"},
                  {"appName": "1640424747", "install_path": "%s", "is_dlc": true}
                ]}
                """.formatted(json(witcher), json(witcher)));
        // окремий Legendary з тією самою грою — дубля бути не повинно
        write(home.resolve(".config/legendary/installed.json"), """
                {"Min": {"title": "Hades", "install_path": "%s"}}
                """.formatted(json(hades)));

        List<Game> games = new HeroicScanner(home).scan();

        assertEquals(List.of("epic:Min", "gog:1207664643"), games.stream().map(Game::id).toList());
        assertEquals("Hades", games.get(0).name());
        assertEquals("The Witcher 3 Wild Hunt", games.get(1).name());
        assertEquals(GameSource.GOG, games.get(1).source());
    }

    @Test
    void heroicFlatpakAndBrokenFiles() throws IOException {
        Path game = Files.createDirectories(home.resolve("Games/Control"));
        write(home.resolve(".var/app/com.heroicgameslauncher.hgl/config/heroic/legendaryConfig/legendary/installed.json"),
                """
                {"Calluna": {"title": "Control", "install_path": "%s"}}
                """.formatted(json(game)));
        write(home.resolve(".config/heroic/gog_store/installed.json"), "{ зламаний json");

        List<Game> games = new HeroicScanner(home).scan();
        assertEquals(List.of("Control"), games.stream().map(Game::name).toList());
    }

    // ------------------------------------------------------------------

    private static void write(Path file, String text) throws IOException {
        Files.createDirectories(file.getParent());
        Files.writeString(file, text, StandardCharsets.UTF_8);
    }

    private static String json(Path p) {
        return p.toAbsolutePath().toString().replace("\\", "\\\\");
    }
}
