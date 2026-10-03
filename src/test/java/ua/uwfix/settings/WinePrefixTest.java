package ua.uwfix.settings;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ua.uwfix.model.AspectRatio;
import ua.uwfix.model.Game;
import ua.uwfix.model.GameSource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Налаштування ігор на Linux: реєстр Wine (user.reg) і пошук префікса Proton/Heroic. */
class WinePrefixTest {

    private static final AspectRatio UW = new AspectRatio(3440, 1440);

    private static final String USER_REG = """
            WINE REGISTRY Version 2
            ;; All keys relative to \\\\User\\\\S-1-5-21-0-0-0-1000

            #arch=win64

            [Software\\\\Cool Studio\\\\Adventure] 1700000000
            #time=1da1b2c3d4e5f60
            "Screenmanager Resolution Width_h182942802"=dword:00000780
            "Screenmanager Resolution Height_h2627697771"=dword:00000438
            "Screenmanager Fullscreen mode_h3630240806"=dword:00000001
            "UnityGraphicsQuality_h1669003810"=hex(4):05,00,00,00

            [Software\\\\Valve\\\\Steam] 1700000001
            "Language"="ukrainian"
            "Name"="\\x0428\\x0435\\x0432\\x0447\\x0435\\x043d\\x043a\\x043e"
            """;

    @TempDir
    Path dir;

    @Test
    void readsValuesFromUserReg() throws IOException {
        WineRegistry reg = registry(USER_REG);

        Map<String, String> unity = reg.readValues("HKCU\\Software\\Cool Studio\\Adventure");
        assertEquals("1920", unity.get("Screenmanager Resolution Width_h182942802"));
        assertEquals("1080", unity.get("Screenmanager Resolution Height_h2627697771"));
        assertFalse(unity.containsKey("UnityGraphicsQuality_h1669003810"), "двійкові значення не потрібні");

        // назви розділів у Wine не залежать від регістру, не-ASCII символи екрановані
        Map<String, String> steam = reg.readValues("HKEY_CURRENT_USER\\software\\valve\\STEAM");
        assertEquals("ukrainian", steam.get("Language"));
        assertEquals("Шевченко", steam.get("Name"));

        assertTrue(reg.readValues("HKCU\\Software\\Missing").isEmpty());
        assertTrue(reg.readValues("HKLM\\Software\\Valve\\Steam").isEmpty(), "HKLM у user.reg не зберігається");
    }

    @Test
    void writesDwordsInPlaceAndKeepsBackup() throws IOException {
        WineRegistry reg = registry(USER_REG);
        String key = "HKCU\\Software\\Cool Studio\\Adventure";

        assertTrue(reg.setDword(key, "Screenmanager Resolution Width_h182942802", 3440));
        assertTrue(reg.setDword(key, "New Value", 7));

        String text = Files.readString(reg.file());
        assertTrue(text.contains("\"Screenmanager Resolution Width_h182942802\"=dword:00000d70"));
        assertTrue(text.contains("#time=1da1b2c3d4e5f60\n\"New Value\"=dword:00000007"),
                "нове значення — одразу після службових рядків розділу");
        assertTrue(text.contains("\"UnityGraphicsQuality_h1669003810\"=hex(4):05,00,00,00"), "інше не чіпаємо");
        assertEquals("3440", reg.readValues(key).get("Screenmanager Resolution Width_h182942802"));
        assertEquals(USER_REG, Files.readString(dir.resolve("user.reg" + ResolutionUnlocker.BACKUP_SUFFIX)));
    }

    @Test
    void createsMissingSection() {
        String out = WineRegistry.withDword("WINE REGISTRY Version 2\n", "Software\\Valve\\Source\\hl2\\Settings",
                "ScreenWidth", 3440, 1700000000);
        assertEquals("""
                WINE REGISTRY Version 2

                [Software\\\\Valve\\\\Source\\\\hl2\\\\Settings] 1700000000
                "ScreenWidth"=dword:00000d70
                """, out);
    }

    @Test
    void escapingRoundTrips() {
        String original = "Ключ \"з\" лапками\\і ]дужкою 1";
        String escaped = WineRegistry.escape(original, '"');
        assertEquals(original, WineRegistry.unescapeUntil(escaped + "\"", 0, '"')[0]);
        // як у Wine: якщо далі йде шістнадцяткова цифра, код доповнюється до 4 знаків
        assertEquals("\\x04441", WineRegistry.escape("ф1", '"'));
        assertEquals("\\x444z", WineRegistry.escape("фz", '"'));
        assertEquals("ф1", WineRegistry.unescapeUntil("\\x04441]", 0, ']')[0]);
    }

    @Test
    void unityResolutionInsideProtonPrefix() throws IOException {
        Path game = dir.resolve("steamapps/common/Adventure");
        Files.createDirectories(game.resolve("Adventure_Data"));
        Files.writeString(game.resolve("Adventure_Data/app.info"), "Cool Studio\nAdventure\n");
        Path pfx = dir.resolve("steamapps/compatdata/4242/pfx");
        Files.createDirectories(pfx.resolve("drive_c/users/steamuser/AppData/Local"));
        Files.writeString(pfx.resolve("user.reg"), USER_REG, StandardCharsets.ISO_8859_1);

        WinePrefix prefix = WinePrefix.forGame(new Game(GameSource.STEAM, "4242", "Adventure", game),
                List.of(), List.of(), dir).orElseThrow();
        assertEquals(pfx, prefix.root());
        assertEquals(pfx.resolve("drive_c/users/steamuser/AppData/Local"), prefix.localAppData());

        ResolutionUnlocker unlocker = ResolutionUnlocker.forPrefix(prefix);
        ResolutionUnlocker.Lookup lookup = unlocker.lookup(game);
        assertTrue(lookup.supported());
        assertNotNull(lookup.settings());
        assertEquals(new AspectRatio(1920, 1080), lookup.settings().current());

        unlocker.apply(lookup.settings(), UW);
        assertEquals(UW, unlocker.lookup(game).settings().current());
    }

    @Test
    void unrealSettingsInsideProtonPrefix() throws IOException {
        Path game = dir.resolve("lib/steamapps/common/Stellar");
        Files.createDirectories(game.resolve("SB/Binaries/Win64"));
        Files.write(game.resolve("SB/Binaries/Win64/SB-Win64-Shipping.exe"), new byte[0]);
        Path pfx = dir.resolve("lib/steamapps/compatdata/3489700/pfx");
        Path ini = pfx.resolve("drive_c/users/steamuser/AppData/Local/SB/Saved/Config/Windows/GameUserSettings.ini");
        Files.createDirectories(ini.getParent());
        Files.writeString(ini, "[/Script/SB.SBGameUserSettings]\nResolutionSizeX=2560\nResolutionSizeY=1440\n");
        Files.writeString(pfx.resolve("user.reg"), "WINE REGISTRY Version 2\n");

        WinePrefix prefix = WinePrefix.forGame(new Game(GameSource.STEAM, "3489700", "Stellar Blade", game),
                List.of(), List.of(), dir).orElseThrow();
        GameSettings settings = ResolutionUnlocker.forPrefix(prefix).lookup(game).settings();
        assertEquals(ini, settings.file());
        assertEquals(new AspectRatio(2560, 1440), settings.current());
    }

    @Test
    void heroicPrefixFromGameConfig() throws IOException {
        Path game = Files.createDirectories(dir.resolve("Games/Heroic/Hades"));
        Path prefix = dir.resolve("Games/Heroic/Prefixes/default/Hades");
        Files.createDirectories(prefix.resolve("drive_c/users/someone/Documents"));
        Files.createDirectories(prefix.resolve("drive_c/users/Public"));
        Files.writeString(prefix.resolve("user.reg"), "WINE REGISTRY Version 2\n");
        Path heroic = dir.resolve(".config/heroic");
        Files.createDirectories(heroic.resolve("GamesConfig"));
        Files.writeString(heroic.resolve("GamesConfig/Min.json"),
                "{\"Min\": {\"winePrefix\": \"~/Games/Heroic/Prefixes/default/Hades\"}, \"version\": \"v0\"}");

        WinePrefix found = WinePrefix.forGame(new Game(GameSource.EPIC, "Min", "Hades", game),
                List.of(), List.of(heroic), dir).orElseThrow();
        assertEquals(prefix, found.root());
        assertEquals(prefix.resolve("drive_c/users/someone/Documents"), found.documents());
    }

    @Test
    void noPrefixForNativeGame() throws IOException {
        Path game = Files.createDirectories(dir.resolve("steamapps/common/Native"));
        assertTrue(WinePrefix.forGame(new Game(GameSource.STEAM, "1", "Native", game),
                List.of(), List.of(), dir).isEmpty());
    }

    private WineRegistry registry(String text) throws IOException {
        Path file = dir.resolve("user.reg");
        Files.writeString(file, text, StandardCharsets.ISO_8859_1);
        return new WineRegistry(file);
    }
}
