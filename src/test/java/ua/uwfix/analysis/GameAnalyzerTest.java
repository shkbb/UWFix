package ua.uwfix.analysis;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ua.uwfix.model.AspectRatio;
import ua.uwfix.model.Game;
import ua.uwfix.model.GameSource;
import ua.uwfix.model.ValueFormat;
import ua.uwfix.util.ProgressListener;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GameAnalyzerTest {

    @TempDir
    Path dir;

    @Test
    void unrealGameRecommendsShippingExeOnly() throws IOException {
        binary("Game.exe", 100_000, 0);
        binary("MyGame/Binaries/Win64/MyGame-Win64-Shipping.exe", 900_000, 12);
        binary("MyGame/Binaries/Win64/MyGameLauncher.exe", 300_000, 2);
        binary("MyGame/Binaries/Win64/Plugin.dll", 200_000, 1);
        binary("Engine/Binaries/Win64/CrashReportClient.exe", 500_000, 3);       // відфільтровується за назвою
        binary("Engine/Binaries/Win64/steam_api64.dll", 200_000, 3);             // стороння бібліотека
        binary("_CommonRedist/vcredist/2019/vc_redist.x64.exe", 300_000, 3);     // службова папка
        binary("MyGame/Binaries/Win64/tiny.exe", 1_000, 3);                      // замалий

        GameAnalysis analysis = analyze();

        assertEquals(Engine.UNREAL_4_5, analysis.engine());
        assertEquals(List.of("MyGame-Win64-Shipping.exe"),
                analysis.recommended().stream().map(BinaryCandidate::fileName).toList());
        assertEquals(List.of("MyGame-Win64-Shipping.exe", "MyGameLauncher.exe", "Plugin.dll"),
                analysis.candidates().stream().map(BinaryCandidate::fileName).toList());
        assertEquals(12, analysis.candidates().get(0).matches(ValueFormat.FLOAT32));
        assertTrue(analysis.antiCheat().isEmpty());
    }

    @Test
    void witcherStyleRecommendsAllExecutablesWithSameName() throws IOException {
        Files.createDirectories(dir.resolve("content"));
        binary("bin/x64/witcher3.exe", 800_000, 20);
        binary("bin/x64_dx12/witcher3.exe", 900_000, 21);
        binary("REDprelauncher.exe", 400_000, 5);

        GameAnalysis analysis = analyze();

        assertEquals(Engine.RED_ENGINE, analysis.engine());
        assertEquals(2, analysis.recommended().size());
        assertTrue(analysis.recommended().stream().allMatch(c -> c.fileName().equals("witcher3.exe")));
    }

    @Test
    void unityRecommendsGameCodeAssembly() throws IOException {
        binary("UnityPlayer.dll", 900_000, 4);
        binary("GameAssembly.dll", 700_000, 6);
        binary("Game.exe", 100_000, 0);

        GameAnalysis analysis = analyze();

        assertEquals(Engine.UNITY_IL2CPP, analysis.engine());
        assertEquals(List.of("GameAssembly.dll"),
                analysis.recommended().stream().map(BinaryCandidate::fileName).toList());
    }

    @Test
    void fallsBackToLargestFileWhenNoExeMatches() throws IOException {
        binary("Launcher.exe", 300_000, 0);
        binary("core/GameLogic.dll", 600_000, 3);
        binary("core/Small.dll", 100_000, 1);

        GameAnalysis analysis = analyze();
        assertEquals(List.of("GameLogic.dll"), analysis.recommended().stream().map(BinaryCandidate::fileName).toList());
    }

    @Test
    void detectsAntiCheat() throws IOException {
        binary("Game-Win64-Shipping.exe", 300_000, 1);
        Files.createDirectories(dir.resolve("EasyAntiCheat"));
        GameAnalysis analysis = analyze();
        assertEquals("Easy Anti-Cheat", analysis.antiCheat().orElseThrow());
    }

    @Test
    void antiCheatNamesOnWindowsAndLinux() {
        assertEquals("Easy Anti-Cheat", AntiCheatDetector.classify("easyanticheat_x64.so"));
        assertEquals("BattlEye", AntiCheatDetector.classify("libbeclient_x64.so"));
        assertEquals("BattlEye", AntiCheatDetector.classify("beservice_x64.exe"));
        assertEquals(null, AntiCheatDetector.classify("libsteam_api.so"));
    }

    @Test
    void trackedFileIsShownEvenWithoutMatches() throws IOException {
        Path patched = binary("Game.exe", 300_000, 0);
        GameAnalysis analysis = new GameAnalyzer().analyze(game(), Set.of(patched), ProgressListener.NONE);
        assertEquals(1, analysis.candidates().size());
        assertTrue(analysis.candidates().get(0).recommended());
    }

    @Test
    void nativeLinuxUnityGame() throws IOException {
        elf("Game.x86_64", 200_000, 1);
        elf("UnityPlayer.so", 900_000, 4);
        elf("GameAssembly.so", 700_000, 6);
        elf("libsteam_api.so", 300_000, 2);                 // стороння бібліотека
        elf("lib/x86_64/libSDL2-2.0.so.0", 300_000, 2);     // і ця теж
        binary("Game_Data/resources", 400_000, 3);          // не ELF — файл даних без розширення

        GameAnalysis analysis = analyze();

        assertEquals(Engine.UNITY_IL2CPP, analysis.engine());
        assertEquals(List.of("GameAssembly.so", "Game.x86_64"),
                analysis.recommended().stream().map(BinaryCandidate::fileName).toList());
        assertEquals(List.of("GameAssembly.so", "Game.x86_64", "UnityPlayer.so"),
                analysis.candidates().stream().map(BinaryCandidate::fileName).toList());
        assertEquals(Optional.of(BinaryFormat.ELF), GameAnalyzer.mainProgramFormat(dir));
    }

    @Test
    void nativeLinuxProgramWithoutExtension() throws IOException {
        elf("bin/ShadowOfTheTombRaider", 900_000, 3);
        elf("bin/crashpad_handler", 500_000, 1);
        GameAnalysis analysis = analyze();
        assertEquals(List.of("ShadowOfTheTombRaider"),
                analysis.recommended().stream().map(BinaryCandidate::fileName).toList());
        assertTrue(analysis.recommended().get(0).isProgram());
    }

    @Test
    void mainProgramFormatTellsWindowsBuildFromNative() throws IOException {
        assertEquals(Optional.empty(), GameAnalyzer.mainProgramFormat(dir));
        binary("Game.exe", 300_000, 0);
        elf("tools/helper_linux", 100_000, 0);
        assertEquals(Optional.of(BinaryFormat.PE), GameAnalyzer.mainProgramFormat(dir));
    }

    @Test
    void fileFilters() {
        assertTrue(GameAnalyzer.isInteresting("witcher3.exe"));
        assertTrue(GameAnalyzer.isInteresting("GameAssembly.dll"));
        assertFalse(GameAnalyzer.isInteresting("readme.txt"));
        assertFalse(GameAnalyzer.isInteresting("d3dcompiler_47.dll"));
        assertFalse(GameAnalyzer.isInteresting("dxgi.dll"));
        assertTrue(GameAnalyzer.isAuxiliary("REDprelauncher.exe"));
        assertFalse(GameAnalyzer.isAuxiliary("witcher3.exe"));
        // Linux
        assertTrue(GameAnalyzer.isInteresting("portal2_linux"));
        assertTrue(GameAnalyzer.isInteresting("valheim.x86_64"));
        assertTrue(GameAnalyzer.isInteresting("GameAssembly.so"));
        assertFalse(GameAnalyzer.isInteresting("libsteam_api.so"));
        assertFalse(GameAnalyzer.isInteresting("libGL.so.1"));
        assertFalse(GameAnalyzer.isInteresting("libfmod.so.13"));
        assertFalse(GameAnalyzer.isInteresting("start.sh"));
        assertTrue(GameAnalyzer.isInteresting("Glitchspace.exe"), "префікси бібліотек Linux не чіпають ігри Windows");
    }

    @Test
    void binaryFormatByMagicBytes() {
        assertEquals(BinaryFormat.ELF, BinaryFormat.of(new byte[]{0x7F, 'E', 'L', 'F'}, 4));
        assertEquals(BinaryFormat.PE, BinaryFormat.of(new byte[]{'M', 'Z', (byte) 0x90, 0}, 4));
        assertEquals(BinaryFormat.OTHER, BinaryFormat.of(new byte[]{'#', '!', '/', 'b'}, 4));
        assertEquals(BinaryFormat.OTHER, BinaryFormat.of(new byte[0], 0));
        assertTrue(BinaryFormat.isSharedObjectName("libSDL2-2.0.so.0"));
        assertFalse(BinaryFormat.isSharedObjectName("Game.x86_64"));
        assertTrue(BinaryFormat.isLibraryName("UnityPlayer.so"));
        assertTrue(BinaryFormat.mayBeElfProgramName("Game-Linux-Shipping"));
        assertFalse(BinaryFormat.mayBeElfProgramName("level0.assets"));
    }

    // ------------------------------------------------------------------

    private GameAnalysis analyze() throws IOException {
        return new GameAnalyzer().analyze(game(), Set.of(), ProgressListener.NONE);
    }

    private Game game() {
        return new Game(GameSource.MANUAL, "test", "Test Game", dir);
    }

    /** Те саме, але з заголовком ELF — як програма чи бібліотека Linux. */
    private Path elf(String relative, int size, int matches) throws IOException {
        Path file = binary(relative, size, matches);
        try (var channel = java.nio.channels.FileChannel.open(file, java.nio.file.StandardOpenOption.WRITE)) {
            channel.write(java.nio.ByteBuffer.wrap(new byte[]{0x7F, 'E', 'L', 'F', 2, 1, 1}), 0);
        }
        return file;
    }

    /** Створює файл заданого розміру з {@code matches} входженнями 16:9. */
    private Path binary(String relative, int size, int matches) throws IOException {
        byte[] data = new byte[size];
        byte[] pattern = ValueFormat.FLOAT32.encode(AspectRatio.STANDARD);
        for (int k = 0; k < matches; k++) {
            int offset = 64 + k * 64;
            System.arraycopy(pattern, 0, data, offset, pattern.length);
        }
        Path file = dir.resolve(relative);
        Files.createDirectories(file.getParent());
        Files.write(file, data);
        return file;
    }
}
