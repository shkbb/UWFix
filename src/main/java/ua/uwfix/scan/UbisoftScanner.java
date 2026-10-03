package ua.uwfix.scan;

import ua.uwfix.model.Game;
import ua.uwfix.model.GameSource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Ігри Ubisoft Connect. Лаунчер зберігає лише папку встановлення
 * ({@code HKLM\SOFTWARE\WOW6432Node\Ubisoft\Launcher\Installs\<id> → InstallDir}),
 * тому назвою гри слугує назва папки.
 */
public final class UbisoftScanner implements GameScanner {

    private static final String INSTALLS_KEY = "HKLM\\SOFTWARE\\WOW6432Node\\Ubisoft\\Launcher\\Installs";

    @Override
    public String name() {
        return "Ubisoft Connect";
    }

    @Override
    public List<Game> scan() {
        List<Game> games = new ArrayList<>();
        for (Map.Entry<String, Map<String, String>> e : WindowsRegistry.readSubkeys(INSTALLS_KEY).entrySet()) {
            String installDir = e.getValue().get("InstallDir");
            if (installDir == null || installDir.isBlank()) {
                continue;
            }
            Path dir = Path.of(installDir.replace('/', '\\'));
            if (Files.isDirectory(dir) && dir.getFileName() != null) {
                games.add(new Game(GameSource.UBISOFT, e.getKey(), dir.getFileName().toString(), dir));
            }
        }
        return games;
    }
}
