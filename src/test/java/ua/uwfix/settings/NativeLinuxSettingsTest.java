package ua.uwfix.settings;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ua.uwfix.model.AspectRatio;
import ua.uwfix.model.Game;
import ua.uwfix.model.GameSource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/** Налаштування нативних ігор Linux: Unity (XML у ~/.config/unity3d) і Unreal (~/.config/Epic). */
class NativeLinuxSettingsTest {

    private static final AspectRatio UW = new AspectRatio(3440, 1440);

    private static final String PREFS = """
            <unity_prefs version_major="1" version_minor="1">
            	<pref name="Screenmanager Resolution Width" type="int">1920</pref>
            	<pref name="Screenmanager Resolution Height" type="int">1080</pref>
            	<pref name="Screenmanager Resolution Use Native" type="int">1</pref>
            	<pref name="PlayerName" type="string">0KjQtdCy0YfQtdC90LrQvg==</pref>
            </unity_prefs>
            """;

    @TempDir
    Path dir;

    @Test
    void unityPrefsXmlInsideConfig() throws IOException {
        Path game = dir.resolve("games/Adventure");
        elf(game.resolve("Adventure.x86_64"));
        Files.createDirectories(game.resolve("Adventure_Data"));
        Files.writeString(game.resolve("Adventure_Data/app.info"), "Cool Studio\nAdventure\n");
        Path config = dir.resolve("config");
        Path prefs = config.resolve("unity3d/Cool Studio/Adventure/prefs");
        Files.createDirectories(prefs.getParent());
        Files.writeString(prefs, PREFS);

        ResolutionUnlocker unlocker = ResolutionUnlocker.forLinuxGame(steamGame(game),
                () -> fail("нативній грі префікс Wine не потрібен"), config);
        assertNotNull(unlocker);
        GameSettings settings = unlocker.lookup(game).settings();
        assertEquals(GameSettings.Kind.UNITY_PREFS_XML, settings.kind());
        assertEquals("Cool Studio/Adventure/prefs", settings.location());
        assertEquals(new AspectRatio(1920, 1080), settings.current());

        unlocker.apply(settings, UW);
        String xml = Files.readString(prefs);
        assertTrue(xml.contains("<pref name=\"Screenmanager Resolution Width\" type=\"int\">3440</pref>"));
        assertTrue(xml.contains("<pref name=\"Screenmanager Resolution Use Native\" type=\"int\">0</pref>"));
        assertTrue(xml.contains("0KjQtdCy0YfQtdC90LrQvg=="), "інші налаштування не чіпаємо");
        assertEquals(UW, unlocker.lookup(game).settings().current());
        assertEquals(PREFS, Files.readString(prefs.resolveSibling("prefs" + ResolutionUnlocker.BACKUP_SUFFIX)));
    }

    @Test
    void unityNotLaunchedYet() throws IOException {
        Path game = dir.resolve("games/Fresh");
        elf(game.resolve("Fresh.x86_64"));
        Files.createDirectories(game.resolve("Fresh_Data"));
        Files.writeString(game.resolve("Fresh_Data/app.info"), "Studio\nFresh\n");

        ResolutionUnlocker.Lookup lookup = ResolutionUnlocker.forLinuxNative(dir.resolve("config")).lookup(game);
        assertTrue(lookup.supported());
        assertNull(lookup.settings());
    }

    @Test
    void unrealLinuxConfigUnderEpic() throws IOException {
        Path game = dir.resolve("games/Odyssey");
        elf(game.resolve("Odyssey/Binaries/Linux/Odyssey-Linux-Shipping"));
        Files.writeString(game.resolve("Odyssey.sh"), "#!/bin/sh\n");
        Path ini = dir.resolve("config/Epic/Odyssey/Saved/Config/LinuxNoEditor/GameUserSettings.ini");
        Files.createDirectories(ini.getParent());
        Files.writeString(ini, "[/Script/Engine.GameUserSettings]\nResolutionSizeX=2560\nResolutionSizeY=1080\n");

        ResolutionUnlocker unlocker = ResolutionUnlocker.forLinuxGame(steamGame(game), Optional::empty,
                dir.resolve("config"));
        GameSettings settings = unlocker.lookup(game).settings();
        assertEquals(ini, settings.file());
        assertEquals(new AspectRatio(2560, 1080), settings.current());
        unlocker.apply(settings, UW);
        assertTrue(Files.readString(ini).contains("ResolutionSizeX=3440"));
    }

    @Test
    void windowsBuildOnLinuxNeedsWinePrefix() throws IOException {
        Path game = dir.resolve("games/Win");
        Files.createDirectories(game);
        Files.write(game.resolve("Game.exe"), new byte[100_000]);
        assertNull(ResolutionUnlocker.forLinuxGame(steamGame(game), Optional::empty, dir.resolve("config")),
                "префікса ще немає — налаштування невідомі");
    }

    @Test
    void prefsXmlInsertsMissingValues() {
        String empty = "<unity_prefs version_major=\"1\" version_minor=\"1\">\n</unity_prefs>\n";
        String out = UnityPrefsXml.update(empty, Map.of("Screenmanager Resolution Width", 3440), Map.of("Absent", 0));
        assertEquals("<unity_prefs version_major=\"1\" version_minor=\"1\">\n"
                + "\t<pref name=\"Screenmanager Resolution Width\" type=\"int\">3440</pref>\n</unity_prefs>\n", out);
        assertEquals(3440, UnityPrefsXml.getInt(out, "Screenmanager Resolution Width"));
        assertNull(UnityPrefsXml.getInt(out, "Absent"), "значення «лише якщо є» не додається");
        assertTrue(UnityPrefsXml.update("", Map.of("A", 1), Map.of()).contains("<pref name=\"A\" type=\"int\">1</pref>"));
    }

    // ------------------------------------------------------------------

    private static Game steamGame(Path dir) {
        return new Game(GameSource.STEAM, "4242", dir.getFileName().toString(), dir);
    }

    /** Файл із заголовком ELF, достатньо великий, щоб аналізатор вважав його програмою. */
    private static void elf(Path file) throws IOException {
        byte[] data = new byte[100_000];
        System.arraycopy(new byte[]{0x7F, 'E', 'L', 'F'}, 0, data, 0, 4);
        Files.createDirectories(file.getParent());
        Files.write(file, data);
    }
}
