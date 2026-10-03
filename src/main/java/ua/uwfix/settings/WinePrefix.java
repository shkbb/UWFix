package ua.uwfix.settings;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import ua.uwfix.model.Game;
import ua.uwfix.model.GameSource;
import ua.uwfix.scan.HeroicScanner;
import ua.uwfix.scan.SteamScanner;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Префікс Wine — «віртуальний Windows», у якому гра працює на Linux. Налаштування гри лежать
 * не в домашній папці, а всередині префікса:
 * <ul>
 *   <li>{@code drive_c/users/<користувач>/AppData/Local} — замість %LOCALAPPDATA%;</li>
 *   <li>{@code drive_c/users/<користувач>/Documents} — замість «Документів»;</li>
 *   <li>{@code user.reg} — гілка реєстру HKEY_CURRENT_USER.</li>
 * </ul>
 * Steam (Proton) створює префікс для кожної гри: {@code <бібліотека>/steamapps/compatdata/<AppID>/pfx},
 * користувач там завжди «steamuser». Heroic зберігає шлях до префікса в налаштуваннях гри.
 *
 * @param root    папка префікса (у ній drive_c і user.reg)
 * @param userDir профіль користувача Windows всередині префікса
 */
public record WinePrefix(Path root, Path userDir) {

    /** Префікс у папці {@code root}; для Proton можна передати і папку на рівень вище (з «pfx» усередині). */
    public static WinePrefix at(Path root) {
        if (!Files.isDirectory(root.resolve("drive_c")) && Files.isDirectory(root.resolve("pfx").resolve("drive_c"))) {
            root = root.resolve("pfx");
        }
        Path users = root.resolve("drive_c").resolve("users");
        List<Path> candidates = new ArrayList<>(List.of(
                users.resolve("steamuser"),
                users.resolve(System.getProperty("user.name", "user"))));
        try (DirectoryStream<Path> dirs = Files.newDirectoryStream(users, Files::isDirectory)) {
            for (Path dir : dirs) {
                if (!dir.getFileName().toString().equalsIgnoreCase("Public")) {
                    candidates.add(dir);
                }
            }
        } catch (IOException e) {
            // папки users ще немає — гру не запускали
        }
        Path user = candidates.stream().filter(Files::isDirectory).findFirst().orElse(candidates.get(0));
        return new WinePrefix(root, user);
    }

    public boolean exists() {
        return Files.isRegularFile(userReg());
    }

    public Path userReg() {
        return root.resolve("user.reg");
    }

    /** %LOCALAPPDATA% (у старих версіях Wine — «Local Settings/Application Data»). */
    public Path localAppData() {
        return firstExisting(userDir.resolve("AppData").resolve("Local"),
                userDir.resolve("Local Settings").resolve("Application Data"));
    }

    /** «Документи» (у старих версіях Wine — «My Documents»). */
    public Path documents() {
        return firstExisting(userDir.resolve("Documents"), userDir.resolve("My Documents"));
    }

    private static Path firstExisting(Path preferred, Path legacy) {
        return !Files.isDirectory(preferred) && Files.isDirectory(legacy) ? legacy : preferred;
    }

    // ------------------------------------------------------------------ пошук префікса гри

    /** Префікс, у якому працює гра, якщо вона запускається через Wine/Proton. */
    public static Optional<WinePrefix> forGame(Game game) {
        Path home = Path.of(System.getProperty("user.home"));
        return forGame(game, SteamScanner.findSteamRoots(), HeroicScanner.configDirs(home), home);
    }

    static Optional<WinePrefix> forGame(Game game, List<Path> steamRoots, List<Path> heroicConfigs, Path home) {
        List<Path> candidates = new ArrayList<>();
        if (game.source() == GameSource.STEAM) {
            // зазвичай compatdata лежить у тій самій бібліотеці, що й гра: <бібліотека>/steamapps/common/<гра>
            Path common = game.installDir().getParent();
            if (common != null && common.getParent() != null
                    && common.getFileName().toString().equalsIgnoreCase("common")) {
                candidates.add(common.getParent().resolve("compatdata").resolve(game.sourceId()));
            }
            for (Path root : steamRoots) {
                candidates.add(root.resolve("steamapps").resolve("compatdata").resolve(game.sourceId()));
            }
        } else if (game.source() == GameSource.EPIC || game.source() == GameSource.GOG) {
            for (Path config : heroicConfigs) {
                Path prefix = heroicPrefix(config.resolve("GamesConfig").resolve(game.sourceId() + ".json"),
                        game.sourceId(), home);
                if (prefix != null) {
                    candidates.add(prefix);
                }
            }
            // типові місця, якщо в налаштуваннях гри префікс не вказано
            Path prefixes = home.resolve("Games").resolve("Heroic").resolve("Prefixes");
            try {
                candidates.add(prefixes.resolve("default").resolve(game.name()));
                candidates.add(prefixes.resolve(game.name()));
            } catch (InvalidPathException e) {
                // у назві гри символи, неможливі в імені папки
            }
        }
        return candidates.stream()
                .map(WinePrefix::at)
                .filter(WinePrefix::exists)
                .findFirst();
    }

    /** {@code GamesConfig/<appName>.json}: {"<appName>": {"winePrefix": "/home/…/Prefixes/default/Hades"}}. */
    static Path heroicPrefix(Path configFile, String appName, Path home) {
        if (!Files.isRegularFile(configFile)) {
            return null;
        }
        try {
            JsonElement json = JsonParser.parseString(Files.readString(configFile, StandardCharsets.UTF_8));
            if (!json.isJsonObject()) {
                return null;
            }
            JsonElement game = json.getAsJsonObject().get(appName);
            if (game == null || !game.isJsonObject()) {
                return null;
            }
            JsonObject settings = game.getAsJsonObject();
            JsonElement prefix = settings.get("winePrefix");
            if (prefix == null || !prefix.isJsonPrimitive() || prefix.getAsString().isBlank()) {
                return null;
            }
            String path = prefix.getAsString();
            return path.startsWith("~/") ? home.resolve(path.substring(2)) : Path.of(path);
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }
}
