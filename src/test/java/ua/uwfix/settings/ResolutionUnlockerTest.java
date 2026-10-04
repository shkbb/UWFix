package ua.uwfix.settings;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ua.uwfix.model.AspectRatio;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ResolutionUnlockerTest {

    @TempDir
    Path dir;

    private static final AspectRatio UW = new AspectRatio(3440, 1440);

    @Test
    void unityHashMatchesKnownRegistryNames() {
        assertEquals("Screenmanager Resolution Width_h182942802", UnityPrefs.valueName(UnityPrefs.WIDTH));
        assertEquals("Screenmanager Resolution Height_h2627697771", UnityPrefs.valueName(UnityPrefs.HEIGHT));
        assertEquals("Screenmanager Resolution Use Native_h1405027254", UnityPrefs.valueName(UnityPrefs.USE_NATIVE));
    }

    @Test
    void unreal5GameUserSettingsIsFoundAndUpdated() throws IOException {
        Path game = dir.resolve("Games/SILENT HILL 2");
        touch(game.resolve("SHProto/Binaries/Win64/SHProto-Win64-Shipping.exe"));
        Path ini = dir.resolve("Local/SHProto/Saved/Config/Windows/GameUserSettings.ini");
        Files.createDirectories(ini.getParent());
        Files.writeString(ini, String.join("\r\n",
                "[/Script/SHProto.SHGameUserSettings]",
                "bUseVSync=False",
                "ResolutionSizeX=1920",
                "ResolutionSizeY=1080",
                "DesiredScreenWidth=1920",
                "DesiredScreenHeight=1080",
                "",
                "[ScalabilityGroups]",
                "sg.ResolutionQuality=100",
                ""));

        ResolutionUnlocker unlocker = new ResolutionUnlocker(dir.resolve("Local"), dir.resolve("Docs"));
        ResolutionUnlocker.Lookup lookup = unlocker.lookup(game);
        assertTrue(lookup.supported());
        assertEquals(GameSettings.Kind.UNREAL_INI, lookup.settings().kind());
        assertEquals(new AspectRatio(1920, 1080), lookup.settings().current());

        unlocker.apply(lookup.settings(), UW);

        String text = Files.readString(ini);
        assertTrue(text.contains("ResolutionSizeX=3440\r\nResolutionSizeY=1440"));
        assertTrue(text.contains("DesiredScreenWidth=3440"));
        assertTrue(text.contains("LastUserConfirmedResolutionSizeX=3440"), "відсутні ключі дописуються в ту саму секцію");
        assertTrue(text.contains("sg.ResolutionQuality=100"), "інші секції не змінюються");
        assertTrue(text.indexOf("LastUserConfirmedResolutionSizeY") < text.indexOf("[ScalabilityGroups]"));
        assertEquals(UW, unlocker.lookup(game).settings().current());
        assertTrue(Files.exists(ini.resolveSibling("GameUserSettings.ini" + ResolutionUnlocker.BACKUP_SUFFIX)));
    }

    /** Life is Strange: Reunion — окрема папка збережень для кожного акаунта Steam, дві секції *GameUserSettings. */
    @Test
    void unrealSavedFolderPerSteamAccount() throws IOException {
        Path game = dir.resolve("Games/LifeisStrangeReunion");
        touch(game.resolve("Iris/Binaries/Win64/Iris-Win64-Shipping.exe"));
        touch(game.resolve("LifeIsStrangeReunion.exe"));
        Path ini = dir.resolve("Local/Iris/Saved_Steam_76561198000000000/Config/Windows/GameUserSettings.ini");
        Files.createDirectories(ini.getParent());
        Files.writeString(ini, String.join("\r\n",
                "[/Script/D9Runtime.D9GameUserSettings]",
                "ResolutionSizeX=1920",
                "ResolutionSizeY=1080",
                "DesiredScreenWidth=1280",
                "DesiredScreenHeight=720",
                "",
                "[/Script/Engine.GameUserSettings]",
                "bUseDesiredScreenHeight=False",
                ""));

        ResolutionUnlocker unlocker = new ResolutionUnlocker(dir.resolve("Local"), dir.resolve("Docs"));
        GameSettings settings = unlocker.lookup(game, "Life is Strange: Reunion").settings();
        assertEquals(ini, settings.file());
        assertEquals(new AspectRatio(1920, 1080), settings.current());

        unlocker.apply(settings, UW);
        String text = Files.readString(ini);
        assertTrue(text.contains("ResolutionSizeX=3440\r\nResolutionSizeY=1440\r\nDesiredScreenWidth=3440"));
        assertEquals(1, text.lines().filter(l -> l.startsWith("ResolutionSizeX=")).count(),
                "ключ не дублюється в іншій секції");
    }

    @Test
    void unreal3EngineIniOnlySystemSettingsSectionChanges() throws IOException {
        Path game = dir.resolve("Games/Life Is Strange");
        Files.createDirectories(game.resolve("LifeIsStrangeGame"));
        Files.createDirectories(game.resolve("Engine"));
        Path ini = dir.resolve("Docs/My Games/Life Is Strange/LifeIsStrangeGame/Config/LifeIsStrangeEngine.ini");
        Files.createDirectories(ini.getParent());
        Files.writeString(ini, String.join("\r\n",
                "[SystemSettings]", "Fullscreen=False", "ResX=1920", "ResY=1080", "",
                "[SystemSettingsMobile]", "ResX=1280", "ResY=720", ""));

        ResolutionUnlocker unlocker = new ResolutionUnlocker(dir.resolve("Local"), dir.resolve("Docs"));
        GameSettings settings = unlocker.lookup(game).settings();
        assertEquals(GameSettings.Kind.UNREAL3_INI, settings.kind());
        assertEquals(new AspectRatio(1920, 1080), settings.current());

        unlocker.apply(settings, UW);
        String text = Files.readString(ini);
        assertTrue(text.contains("[SystemSettings]\r\nFullscreen=False\r\nResX=3440\r\nResY=1440"));
        assertTrue(text.contains("[SystemSettingsMobile]\r\nResX=1280\r\nResY=720"), "інший профіль не чіпаємо");
    }

    @Test
    void utf16IniKeepsItsEncoding() throws IOException {
        Path game = dir.resolve("G");
        touch(game.resolve("Proj/Binaries/Win64/Proj-Win64-Shipping.exe"));
        Path ini = dir.resolve("Local/Proj/Saved/Config/WindowsNoEditor/GameUserSettings.ini");
        Files.createDirectories(ini.getParent());
        byte[] bom = {(byte) 0xFF, (byte) 0xFE};
        byte[] body = "[/Script/Engine.GameUserSettings]\r\nResolutionSizeX=1920\r\nResolutionSizeY=1080\r\n"
                .getBytes(StandardCharsets.UTF_16LE);
        Files.write(ini, concat(bom, body));

        ResolutionUnlocker unlocker = new ResolutionUnlocker(dir.resolve("Local"), dir.resolve("Docs"));
        unlocker.apply(unlocker.lookup(game).settings(), UW);

        byte[] after = Files.readAllBytes(ini);
        assertArrayEquals(bom, new byte[]{after[0], after[1]}, "BOM UTF-16 зберігся");
        String text = new String(after, 2, after.length - 2, StandardCharsets.UTF_16LE);
        assertTrue(text.contains("ResolutionSizeX=3440"));
    }

    @Test
    void settingsFolderNamedAfterTheGameIsFound() throws IOException {
        // SILENT HILL 2: файл SHProto-Win64-Shipping.exe, а налаштування в %LOCALAPPDATA%\SilentHill2
        Path game = dir.resolve("Games/SILENT HILL 2");
        touch(game.resolve("SHProto/Binaries/Win64/SHProto-Win64-Shipping.exe"));
        Path ini = dir.resolve("Local/SilentHill2/Saved/Config/Windows/GameUserSettings.ini");
        Files.createDirectories(ini.getParent());
        Files.writeString(ini, "[/Script/Engine.GameUserSettings]\nResolutionSizeX=2560\nResolutionSizeY=1440\n");

        GameSettings settings = new ResolutionUnlocker(dir.resolve("Local"), dir.resolve("Docs"))
                .lookup(game, "SILENT HILL 2").settings();
        assertEquals(ini, settings.file());
        assertEquals(new AspectRatio(2560, 1440), settings.current());
    }

    @Test
    void folderNameMatching() {
        assertTrue(ResolutionUnlocker.nameMatches("SilentHill2", List.of("SHProto", "SILENT HILL 2")));
        assertTrue(ResolutionUnlocker.nameMatches("Stalker2", List.of("S.T.A.L.K.E.R. 2: Heart of Chornobyl")));
        assertTrue(ResolutionUnlocker.nameMatches("ReadyOrNot", List.of("Ready Or Not")));
        assertFalse(ResolutionUnlocker.nameMatches("Impact", List.of("Red Dead Redemption 2")));
        assertFalse(ResolutionUnlocker.nameMatches("UE", List.of("UE game")), "надто коротка назва папки");
    }

    @Test
    void supportedEngineWithoutSettingsYet() throws IOException {
        Path game = dir.resolve("NewGame");
        touch(game.resolve("Fresh/Binaries/Win64/Fresh-Win64-Shipping.exe"));
        ResolutionUnlocker.Lookup lookup = new ResolutionUnlocker(dir.resolve("Local"), dir.resolve("Docs")).lookup(game);
        assertTrue(lookup.supported());
        assertNull(lookup.settings(), "гру ще не запускали — файлу налаштувань немає");
    }

    @Test
    void unknownEngineIsNotSupported() throws IOException {
        Path game = dir.resolve("Other");
        touch(game.resolve("other.exe"));
        assertFalse(new ResolutionUnlocker(dir.resolve("Local"), dir.resolve("Docs")).lookup(game).supported());
    }

    @Test
    void sourceVideoTxtIsFoundAndUpdated() throws IOException {
        Path game = dir.resolve("Left 4 Dead 2");
        touch(game.resolve("bin/engine.dll"));
        touch(game.resolve("left4dead2/gameinfo.txt"));
        Path video = game.resolve("left4dead2/cfg/video.txt");
        Files.createDirectories(video.getParent());
        Files.writeString(video, String.join("\r\n",
                "\"VideoConfig\"",
                "{",
                "\t\"setting.cpu_level\"\t\t\"2\"",
                "\t\"setting.defaultres\"\t\t\"1920\"",
                "\t\"setting.defaultresheight\"\t\t\"1080\"",
                "}",
                ""));

        ResolutionUnlocker unlocker = new ResolutionUnlocker(dir.resolve("Local"), dir.resolve("Docs"));
        GameSettings settings = unlocker.lookup(game, "Left 4 Dead 2").settings();
        assertEquals(GameSettings.Kind.SOURCE_VIDEO_TXT, settings.kind());
        assertEquals(new AspectRatio(1920, 1080), settings.current());

        unlocker.apply(settings, UW);
        String text = Files.readString(video);
        assertTrue(text.contains("\t\"setting.defaultres\"\t\t\"3440\"\r\n\t\"setting.defaultresheight\"\t\t\"1440\""),
                "формат рядків (табуляції, лапки) зберігся");
        assertTrue(text.contains("\"setting.cpu_level\"\t\t\"2\""));
    }

    @Test
    void videoTxtGetsMissingKeysBeforeClosingBrace() {
        String out = ResolutionUnlocker.updateVideoTxt("\"VideoConfig\"\n{\n\t\"setting.fullscreen\"\t\"1\"\n}\n", UW);
        assertTrue(out.contains("\t\"setting.defaultres\"\t\t\"3440\"\n\t\"setting.defaultresheight\"\t\t\"1440\"\n}"));
    }

    @Test
    void creationPrefsFoundByExecutableNameAndUpdated() throws IOException {
        // Fallout: New Vegas — налаштування в «My Games\FalloutNV», а не «Fallout New Vegas»
        Path game = dir.resolve("Games/Fallout New Vegas");
        touch(game.resolve("Data/FalloutNV.esm"));
        touch(game.resolve("FalloutNV.exe"));
        Path prefs = dir.resolve("Docs/My Games/FalloutNV/FalloutPrefs.ini");
        Files.createDirectories(prefs.getParent());
        Files.writeString(prefs, "[Display]\r\niSize W=1920\r\niSize H=1080\r\nbFull Screen=1\r\n[Audio]\r\nfMasterVolume=1.0\r\n");

        ResolutionUnlocker unlocker = new ResolutionUnlocker(dir.resolve("Local"), dir.resolve("Docs"));
        GameSettings settings = unlocker.lookup(game, "Fallout: New Vegas").settings();
        assertEquals(GameSettings.Kind.CREATION_INI, settings.kind());
        assertEquals(new AspectRatio(1920, 1080), settings.current());

        unlocker.apply(settings, UW);
        assertEquals("[Display]\r\niSize W=3440\r\niSize H=1440\r\nbFull Screen=1\r\n[Audio]\r\nfMasterVolume=1.0\r\n",
                Files.readString(prefs));
    }

    @Test
    void unityAppInfoGivesRegistryKey() throws IOException {
        Path game = dir.resolve("Unity Game");
        Files.createDirectories(game.resolve("Adventure_Data"));
        Files.writeString(game.resolve("Adventure_Data/app.info"), "Cool Studio\nAdventure\n");
        String[] info = ResolutionUnlocker.unityAppInfo(game).orElseThrow();
        assertEquals("HKCU\\Software\\Cool Studio\\Adventure", ResolutionUnlocker.unityRegistryKey(info[0], info[1]));
    }

    @Test
    void iniEditorCreatesSectionWhenMissing() {
        Map<String, String> values = new LinkedHashMap<>();
        values.put("ResX", "3440");
        String out = IniEditor.update("[Other]\nA=1\n", IniEditor.named("SystemSettings"), "SystemSettings",
                values, Map.of());
        assertEquals(List.of("[Other]", "A=1", "", "[SystemSettings]", "ResX=3440", ""), List.of(out.split("\n", -1)));
        assertEquals("3440", IniEditor.get(out, IniEditor.named("systemsettings"), "resx"));
    }

    private static void touch(Path file) throws IOException {
        Files.createDirectories(file.getParent());
        Files.write(file, new byte[0]);
    }

    private static byte[] concat(byte[] a, byte[] b) {
        byte[] r = new byte[a.length + b.length];
        System.arraycopy(a, 0, r, 0, a.length);
        System.arraycopy(b, 0, r, a.length, b.length);
        return r;
    }
}
