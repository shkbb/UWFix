package ua.uwfix.system;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ua.uwfix.model.Game;
import ua.uwfix.model.GameSource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GameLauncherTest {

    @TempDir
    Path dir;

    @Test
    void launcherUrisOnWindows() {
        assertEquals("steam://rungameid/292030", GameLauncher.launcherUri(game(GameSource.STEAM, "292030"), Os.WINDOWS));
        assertEquals("com.epicgames.launcher://apps/fa4240e5%3Abd2?action=launch&silent=true",
                GameLauncher.launcherUri(game(GameSource.EPIC, "fa4240e5:bd2"), Os.WINDOWS));
        assertEquals("uplay://launch/635/0", GameLauncher.launcherUri(game(GameSource.UBISOFT, "635"), Os.WINDOWS));
        assertNull(GameLauncher.launcherUri(game(GameSource.GOG, "1207664643"), Os.WINDOWS), "GOG запускаємо напряму");
        assertNull(GameLauncher.launcherUri(game(GameSource.STEAM, "1; calc"), Os.WINDOWS),
                "некоректний AppID не передаємо");
        assertTrue(GameLauncher.usesLauncher(game(GameSource.STEAM, "10")));
        assertFalse(GameLauncher.usesLauncher(game(GameSource.MANUAL, "x")));
    }

    @Test
    void onLinuxOnlySteamGamesHaveALauncher() {
        assertEquals("steam://rungameid/292030", GameLauncher.launcherUri(game(GameSource.STEAM, "292030"), Os.LINUX));
        assertNull(GameLauncher.launcherUri(game(GameSource.EPIC, "fa4240e5"), Os.LINUX));
        assertNull(GameLauncher.launcherUri(game(GameSource.UBISOFT, "635"), Os.LINUX));
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

    @Test
    void linuxPrefersGogStartScript() throws IOException {
        elf("game/Celeste.bin.x86_64", 90_000);
        Path start = write("start.sh", 500);
        write("support/postinst.sh", 500);
        assertEquals(Optional.of(start), GameLauncher.nativeLaunchTarget(dir));
    }

    @Test
    void linuxUsesUnrealScriptThenRootElfThenDeeperElf() throws IOException {
        Path deep = elf("Game/Binaries/Linux/Game-Linux-Shipping", 900_000);
        assertEquals(Optional.of(deep), GameLauncher.nativeLaunchTarget(dir));

        Path root = elf("Game.x86_64", 20_000);              // Unity: маленька програма в корені
        elf("UnityPlayer.so", 900_000);                      // бібліотека — не запускається
        assertEquals(Optional.of(root), GameLauncher.nativeLaunchTarget(dir));

        Path script = write("Game.sh", 300);
        write("uninstall.sh", 300);                          // допоміжний скрипт
        assertEquals(Optional.of(script), GameLauncher.nativeLaunchTarget(dir));
    }

    @Test
    void windowsBuildHasNoNativeTarget() throws IOException {
        write("Game.exe", 90_000);
        write("readme", 70_000);                             // без розширення, але не ELF
        assertTrue(GameLauncher.nativeLaunchTarget(dir).isEmpty());
    }

    @Test
    void scriptWithoutExecuteBitRunsThroughSh() throws IOException {
        Path script = write("start.sh", 100);
        if (!Files.isExecutable(script)) {
            assertEquals(List.of("sh", script.toString()), GameLauncher.nativeCommand(script));
        }
        Path elf = elf("Game.x86_64", 1_000);
        assertEquals(List.of(elf.toString()), GameLauncher.nativeCommand(elf));
    }

    private Path elf(String relative, int size) throws IOException {
        Path p = write(relative, size);
        byte[] data = Files.readAllBytes(p);
        System.arraycopy(new byte[]{0x7F, 'E', 'L', 'F'}, 0, data, 0, 4);
        Files.write(p, data);
        return p;
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
