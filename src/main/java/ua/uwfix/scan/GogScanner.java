package ua.uwfix.scan;

import ua.uwfix.model.Game;
import ua.uwfix.model.GameSource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Ігри GOG. Інсталятори GOG записують кожну гру в реєстр:
 * {@code HKLM\SOFTWARE\WOW6432Node\GOG.com\Games\<id>} зі значеннями gameName і path.
 */
public final class GogScanner implements GameScanner {

    static final String GAMES_KEY = "HKLM\\SOFTWARE\\WOW6432Node\\GOG.com\\Games";

    @Override
    public String name() {
        return "GOG";
    }

    @Override
    public List<Game> scan() {
        return fromRegistry(WindowsRegistry.readSubkeys(GAMES_KEY));
    }

    /** Перетворює підрозділи реєстру на ігри (виділено окремо для тестів). */
    static List<Game> fromRegistry(Map<String, Map<String, String>> subkeys) {
        List<Game> games = new ArrayList<>();
        Set<String> seenDirs = new HashSet<>();
        for (Map.Entry<String, Map<String, String>> e : subkeys.entrySet()) {
            Map<String, String> values = e.getValue();
            String name = values.get("gameName");
            String path = values.get("path");
            String dependsOn = values.getOrDefault("dependsOn", "");
            if (name == null || path == null || !dependsOn.isBlank()) {
                continue; // DLC залежать від основної гри і мають ту саму папку
            }
            Path dir = Path.of(path);
            if (Files.isDirectory(dir) && seenDirs.add(dir.toString().toLowerCase(Locale.ROOT))) {
                games.add(new Game(GameSource.GOG, e.getKey(), name, dir));
            }
        }
        return games;
    }
}
