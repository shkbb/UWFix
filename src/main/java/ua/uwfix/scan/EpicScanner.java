package ua.uwfix.scan;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import ua.uwfix.model.Game;
import ua.uwfix.model.GameSource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Ігри Epic Games Store.
 * <p>
 * Лаунчер Epic зберігає для кожної встановленої гри JSON-маніфест
 * {@code *.item} у папці {@code C:\ProgramData\Epic\EpicGamesLauncher\Data\Manifests}.
 */
public final class EpicScanner implements GameScanner {

    private final Path manifestsOverride;

    public EpicScanner() {
        this(null);
    }

    /** @param manifestsDir папка з маніфестами (для тестів); {@code null} — визначити автоматично */
    public EpicScanner(Path manifestsDir) {
        this.manifestsOverride = manifestsDir;
    }

    @Override
    public String name() {
        return "Epic Games";
    }

    @Override
    public List<Game> scan() {
        Path dir = manifestsOverride != null ? manifestsOverride : findManifestsDir();
        if (dir == null || !Files.isDirectory(dir)) {
            return List.of();
        }
        List<Game> games = new ArrayList<>();
        Set<String> seenDirs = new HashSet<>();
        try (DirectoryStream<Path> items = Files.newDirectoryStream(dir, "*.item")) {
            for (Path item : items) {
                Game game = readItem(item);
                // DLC мають ту саму папку, що й основна гра — лишаємо одну
                if (game != null && seenDirs.add(game.installDir().toString().toLowerCase(Locale.ROOT))) {
                    games.add(game);
                }
            }
        } catch (IOException e) {
            return games;
        }
        return games;
    }

    private static Path findManifestsDir() {
        String appData = WindowsRegistry
                .readValues("HKLM\\SOFTWARE\\WOW6432Node\\Epic Games\\EpicGamesLauncher")
                .get("AppDataPath");
        if (appData != null) {
            return Path.of(appData).resolve("Manifests");
        }
        String programData = System.getenv().getOrDefault("ProgramData", "C:\\ProgramData");
        return Path.of(programData, "Epic", "EpicGamesLauncher", "Data", "Manifests");
    }

    static Game readItem(Path item) {
        try {
            JsonObject json = JsonParser.parseString(Files.readString(item, StandardCharsets.UTF_8)).getAsJsonObject();
            if (getBoolean(json, "bIsIncompleteInstall")) {
                return null;
            }
            if (!isGame(json)) {
                return null; // плагіни для Unreal Engine, сам рушій тощо
            }
            String name = getString(json, "DisplayName");
            String location = getString(json, "InstallLocation");
            String appName = getString(json, "AppName");
            if (name == null || location == null || appName == null) {
                return null;
            }
            Path dir = Path.of(location);
            if (!Files.isDirectory(dir)) {
                return null;
            }
            return new Game(GameSource.EPIC, appName, name, dir);
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }

    private static boolean isGame(JsonObject json) {
        JsonElement categories = json.get("AppCategories");
        if (categories == null || !categories.isJsonArray()) {
            return true;
        }
        JsonArray array = categories.getAsJsonArray();
        for (JsonElement c : array) {
            if (c.isJsonPrimitive() && c.getAsString().equalsIgnoreCase("games")) {
                return true;
            }
        }
        return array.isEmpty();
    }

    private static String getString(JsonObject json, String key) {
        JsonElement e = json.get(key);
        return e != null && e.isJsonPrimitive() ? e.getAsString() : null;
    }

    private static boolean getBoolean(JsonObject json, String key) {
        JsonElement e = json.get(key);
        return e != null && e.isJsonPrimitive() && e.getAsBoolean();
    }
}
