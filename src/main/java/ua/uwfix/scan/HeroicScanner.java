package ua.uwfix.scan;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import ua.uwfix.model.Game;
import ua.uwfix.model.GameSource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Ігри Epic Games і GOG на Linux — їх зазвичай встановлюють через Heroic Games Launcher.
 * <ul>
 *   <li>Epic (через вбудований Legendary): {@code ~/.config/heroic/legendaryConfig/legendary/installed.json}
 *       — об'єкт «AppName → {title, install_path, …}»; також окремий Legendary: {@code ~/.config/legendary/installed.json};</li>
 *   <li>GOG: {@code ~/.config/heroic/gog_store/installed.json} — масив «installed» з appName та install_path.</li>
 * </ul>
 * Heroic з Flatpak зберігає ті самі файли в {@code ~/.var/app/com.heroicgameslauncher.hgl/config/heroic}.
 */
public final class HeroicScanner implements GameScanner {

    private final List<Path> heroicConfigs;
    private final List<Path> legendaryConfigs;

    public HeroicScanner() {
        this(Path.of(System.getProperty("user.home")));
    }

    /** @param home домашня папка (для тестів — тимчасова) */
    public HeroicScanner(Path home) {
        this.heroicConfigs = List.of(
                home.resolve(".config/heroic"),
                home.resolve(".var/app/com.heroicgameslauncher.hgl/config/heroic"));
        this.legendaryConfigs = List.of(home.resolve(".config/legendary"));
    }

    @Override
    public String name() {
        return "Heroic";
    }

    @Override
    public List<Game> scan() {
        List<Game> games = new ArrayList<>();
        Set<String> seenDirs = new HashSet<>();
        for (Path heroic : heroicConfigs) {
            readEpic(heroic.resolve("legendaryConfig/legendary/installed.json"), games, seenDirs);
            readGog(heroic.resolve("gog_store/installed.json"), games, seenDirs);
        }
        for (Path legendary : legendaryConfigs) {
            readEpic(legendary.resolve("installed.json"), games, seenDirs);
        }
        return games;
    }

    static void readEpic(Path file, List<Game> games, Set<String> seenDirs) {
        JsonObject root = readJson(file);
        if (root == null) {
            return;
        }
        for (Map.Entry<String, JsonElement> e : root.entrySet()) {
            if (!e.getValue().isJsonObject()) {
                continue;
            }
            JsonObject app = e.getValue().getAsJsonObject();
            String title = string(app, "title");
            String path = string(app, "install_path");
            add(games, seenDirs, GameSource.EPIC, e.getKey(), title, path);
        }
    }

    static void readGog(Path file, List<Game> games, Set<String> seenDirs) {
        JsonObject root = readJson(file);
        if (root == null || root.get("installed") == null || !root.get("installed").isJsonArray()) {
            return;
        }
        for (JsonElement element : root.getAsJsonArray("installed")) {
            if (!element.isJsonObject()) {
                continue;
            }
            JsonObject app = element.getAsJsonObject();
            if (app.has("is_dlc") && app.get("is_dlc").getAsBoolean()) {
                continue;
            }
            String path = string(app, "install_path");
            // назви гри у цьому файлі немає — беремо назву папки встановлення
            String title = path == null ? null : Path.of(path).getFileName().toString();
            add(games, seenDirs, GameSource.GOG, string(app, "appName"), title, path);
        }
    }

    private static void add(List<Game> games, Set<String> seenDirs, GameSource source,
                            String id, String title, String path) {
        if (id == null || title == null || path == null) {
            return;
        }
        Path dir = Path.of(path);
        if (Files.isDirectory(dir) && seenDirs.add(dir.toAbsolutePath().normalize().toString())) {
            games.add(new Game(source, id, title, dir));
        }
    }

    private static JsonObject readJson(Path file) {
        if (!Files.isRegularFile(file)) {
            return null;
        }
        try {
            JsonElement json = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8));
            return json.isJsonObject() ? json.getAsJsonObject() : null;
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }

    private static String string(JsonObject o, String key) {
        JsonElement e = o.get(key);
        return e != null && e.isJsonPrimitive() ? e.getAsString() : null;
    }
}
