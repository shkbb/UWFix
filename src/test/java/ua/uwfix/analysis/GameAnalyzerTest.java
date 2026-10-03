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
    void trackedFileIsShownEvenWithoutMatches() throws IOException {
        Path patched = binary("Game.exe", 300_000, 0);
        GameAnalysis analysis = new GameAnalyzer().analyze(game(), Set.of(patched), ProgressListener.NONE);
        assertEquals(1, analysis.candidates().size());
        assertTrue(analysis.candidates().get(0).recommended());
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
    }

    // ------------------------------------------------------------------

    private GameAnalysis analyze() throws IOException {
        return new GameAnalyzer().analyze(game(), Set.of(), ProgressListener.NONE);
    }

    private Game game() {
        return new Game(GameSource.MANUAL, "test", "Test Game", dir);
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
