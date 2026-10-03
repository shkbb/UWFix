package ua.uwfix.system;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ua.uwfix.model.Game;
import ua.uwfix.model.GameSource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GameLauncherTest {

    @TempDir
    Path dir;

    @Test
    void launcherUris() {
        assertEquals("steam://rungameid/292030", GameLauncher.launcherUri(game(GameSource.STEAM, "292030")));
        assertEquals("com.epicgames.launcher://apps/fa4240e5%3Abd2?action=launch&silent=true",
                GameLauncher.launcherUri(game(GameSource.EPIC, "fa4240e5:bd2")));
        assertEquals("uplay://launch/635/0", GameLauncher.launcherUri(game(GameSource.UBISOFT, "635")));
        assertNull(GameLauncher.launcherUri(game(GameSource.GOG, "1207664643")), "GOG запускаємо напряму");
        assertNull(GameLauncher.launcherUri(game(GameSource.STEAM, "1; calc")), "некоректний AppID не передаємо");
        assertTrue(GameLauncher.usesLauncher(game(GameSource.STEAM, "10")));
        assertFalse(GameLauncher.usesLauncher(game(GameSource.MANUAL, "x")));
    }

    @Test
    void prefersStarterExeInTheRootFolder() throws IOException {
        write("Game/Binaries/Win64/Game-Win64-Shipping.exe", 90_000);
        Path starter = write("Game.exe", 3_000);
        write("UnityCrashHandler64.exe", 5_000);
        write("Launcher.exe", 8_000);
        assertEquals(Optional.of(starter), GameLauncher.launchExecutable(dir));
    }

    @Test
    void fallsBackToMainExecutableDeeper() throws IOException {
        Path witcher = write("bin/x64_dx12/witcher3.exe", 90_000);
        write("REDprelauncher.exe", 10_000);
        assertEquals(Optional.of(witcher), GameLauncher.launchExecutable(dir));
    }

    private Game game(GameSource source, String id) {
        return new Game(source, id, "Game", dir);
    }

    private Path write(String relative, int size) throws IOException {
        Path p = dir.resolve(relative);
        Files.createDirectories(p.getParent());
        Files.write(p, new byte[size]);
        return p;
    }
}
