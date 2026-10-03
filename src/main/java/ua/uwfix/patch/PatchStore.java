package ua.uwfix.patch;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Зберігає стан програми у файлі {@code %APPDATA%\UWFix\state.json}.
 * <p>
 * Запис атомарний: спершу пишемо тимчасовий файл, потім перейменовуємо його,
 * тож при збої живлення старий стан не пошкодиться.
 */
public final class PatchStore {

    private final Path file;
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    private AppState state;

    public PatchStore(Path file) {
        this.file = file;
        this.state = load();
    }

    /**
     * Папка даних програми. Можна перевизначити властивістю {@code -Duwfix.home=...}
     * або змінною оточення {@code UWFIX_HOME} (для тестів, щоб не чіпати справжній стан).
     */
    public static Path defaultHome() {
        String override = System.getProperty("uwfix.home");
        if (override == null || override.isBlank()) {
            override = System.getenv("UWFIX_HOME");
        }
        if (override != null && !override.isBlank()) {
            return Path.of(override);
        }
        String appData = System.getenv("APPDATA");
        Path base = appData != null ? Path.of(appData) : Path.of(System.getProperty("user.home"));
        return base.resolve("UWFix");
    }

    public static PatchStore openDefault() {
        return new PatchStore(defaultHome().resolve("state.json"));
    }

    public synchronized AppState state() {
        return state;
    }

    // ---------- записи про патчі ----------

    public synchronized Optional<PatchRecord> find(Path target) {
        String key = key(target);
        return state.patches.stream().filter(r -> key(Path.of(r.file())).equals(key)).findFirst();
    }

    public synchronized List<PatchRecord> forGame(String gameId) {
        return state.patches.stream().filter(r -> r.gameId().equals(gameId)).toList();
    }

    public synchronized List<PatchRecord> all() {
        return List.copyOf(state.patches);
    }

    /** Додає запис або замінює наявний для того самого файлу. */
    public synchronized void put(PatchRecord record) {
        String key = key(Path.of(record.file()));
        state.patches.removeIf(r -> key(Path.of(r.file())).equals(key));
        state.patches.add(record);
    }

    public synchronized void remove(Path target) {
        String key = key(target);
        state.patches.removeIf(r -> key(Path.of(r.file())).equals(key));
    }

    // ---------- налаштування ----------

    public synchronized void setTarget(Integer width, Integer height) {
        state.targetWidth = width;
        state.targetHeight = height;
    }

    public synchronized void setIncludeDouble(boolean value) {
        state.includeDouble = value;
    }

    public synchronized void addManualGame(ManualGameEntry entry) {
        state.manualGames.removeIf(g -> key(Path.of(g.path())).equals(key(Path.of(entry.path()))));
        state.manualGames.add(entry);
    }

    public synchronized void removeManualGame(Path path) {
        state.manualGames.removeIf(g -> key(Path.of(g.path())).equals(key(path)));
    }

    // ---------- читання / запис ----------

    public synchronized void save() throws IOException {
        Files.createDirectories(file.getParent());
        Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
        Files.writeString(tmp, gson.toJson(state), StandardCharsets.UTF_8);
        try {
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private AppState load() {
        if (!Files.isRegularFile(file)) {
            return new AppState();
        }
        try {
            AppState loaded = gson.fromJson(Files.readString(file, StandardCharsets.UTF_8), AppState.class);
            if (loaded == null) {
                return new AppState();
            }
            loaded.fixNulls();
            loaded.patches = new ArrayList<>(loaded.patches);
            return loaded;
        } catch (IOException | JsonParseException e) {
            // Пошкоджений файл не видаляємо, а відкладаємо — раптом знадобиться
            try {
                Files.move(file, file.resolveSibling(file.getFileName() + ".broken"),
                        StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException ignored) {
                // нічого страшного — просто почнемо з чистого стану
            }
            return new AppState();
        }
    }

    /** Шляхи у Windows нечутливі до регістру — порівнюємо в нижньому регістрі. */
    private static String key(Path path) {
        return path.toAbsolutePath().normalize().toString().toLowerCase(Locale.ROOT);
    }
}
