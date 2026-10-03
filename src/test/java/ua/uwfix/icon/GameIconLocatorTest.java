package ua.uwfix.icon;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ua.uwfix.model.Game;
import ua.uwfix.model.GameSource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GameIconLocatorTest {

    @TempDir
    Path dir;

    @Test
    void findsSteamIconInCurrentCacheLayout() throws IOException {
        Path cache = Files.createDirectories(dir.resolve("Steam/appcache/librarycache/292030"));
        Path icon = cache.resolve("7b52d11edee478d652ef4e7103e00644bed2c74c.jpg");
        Files.write(icon, new byte[]{1});
        // поруч лежать обкладинки в підпапках — їх не плутаємо з іконкою
        Files.createDirectories(cache.resolve("5cf71be55957e5a887b2ca9356fd35e2e052dbf5"));
        Files.write(cache.resolve("5cf71be55957e5a887b2ca9356fd35e2e052dbf5/library_header.jpg"), new byte[]{2});

        GameIconLocator locator = new GameIconLocator(dir.resolve("Steam"));
        assertEquals(Optional.of(icon), locator.steamIcon(steam("292030")));
    }

    @Test
    void findsSteamIconInLegacyLayout() throws IOException {
        Path cache = Files.createDirectories(dir.resolve("Steam/appcache/librarycache"));
        Path icon = cache.resolve("570_icon.jpg");
        Files.write(icon, new byte[]{1});
        assertEquals(Optional.of(icon), new GameIconLocator(dir.resolve("Steam")).steamIcon(steam("570")));
    }

    @Test
    void noSteamIconForOtherSourcesOrWithoutSteam() {
        Game manual = new Game(GameSource.MANUAL, "x", "X", dir);
        assertEquals(Optional.empty(), new GameIconLocator(dir).steamIcon(manual));
        assertEquals(Optional.empty(), new GameIconLocator(null).steamIcon(steam("1")));
    }

    @Test
    void mainExecutableIsTheLargestNonAuxiliaryExe() throws IOException {
        write("Game.exe", 1_000);
        Path shipping = write("Game/Binaries/Win64/Game-Win64-Shipping.exe", 50_000);
        write("Engine/Binaries/Win64/CrashReportClient.exe", 90_000);  // допоміжна, хоч і більша
        write("Launcher.exe", 70_000);                                 // лаунчер
        write("Game/Content/data.pak", 200_000);                       // не .exe

        assertEquals(Optional.of(shipping), GameIconLocator.mainExecutable(dir));
    }

    private Game steam(String appId) {
        return new Game(GameSource.STEAM, appId, "Game", dir);
    }

    private Path write(String relative, int size) throws IOException {
        Path p = dir.resolve(relative);
        Files.createDirectories(p.getParent());
        Files.write(p, new byte[size]);
        return p;
    }
}
