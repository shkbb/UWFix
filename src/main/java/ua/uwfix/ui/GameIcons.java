package ua.uwfix.ui;

import javafx.application.Platform;
import javafx.scene.image.Image;
import javafx.scene.image.PixelFormat;
import javafx.scene.image.WritableImage;
import ua.uwfix.icon.GameIconLocator;
import ua.uwfix.icon.IconImage;
import ua.uwfix.icon.PeIconExtractor;
import ua.uwfix.model.Game;
import ua.uwfix.scan.SteamScanner;

import java.io.ByteArrayInputStream;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Іконки ігор для списку. Завантажуються у фоні й кешуються в пам'яті:
 * поки іконки немає, комірка показує першу літеру назви.
 */
final class GameIcons {

    /** Розмір, під який обирається зображення з .exe (із запасом для екранів з масштабуванням). */
    private static final int DESIRED_SIZE = 48;

    /** Обкладинка гри для шапки: широкий фон і логотип (кожне може бути відсутнім). */
    record Art(Image hero, Image logo) {
    }

    private final Map<String, Optional<Image>> cache = new ConcurrentHashMap<>();
    private final Set<String> loading = ConcurrentHashMap.newKeySet();
    private final Map<String, Optional<Art>> artCache = new ConcurrentHashMap<>();
    private final Set<String> artLoading = ConcurrentHashMap.newKeySet();
    private final ExecutorService pool = Executors.newFixedThreadPool(2, r -> {
        Thread t = new Thread(r, "uwfix-icons");
        t.setDaemon(true);
        return t;
    });
    private volatile GameIconLocator locator;
    private volatile Runnable onLoaded = () -> { };

    /** Що робити, коли довантажилась чергова іконка (оновити список). */
    void setOnLoaded(Runnable onLoaded) {
        this.onLoaded = onLoaded;
    }

    /**
     * Іконка гри, якщо вона вже завантажена; інакше запускає завантаження
     * і повертає {@code null}.
     */
    Image get(Game game) {
        Optional<Image> cached = cache.get(game.id());
        if (cached != null) {
            return cached.orElse(null);
        }
        if (loading.add(game.id())) {
            pool.submit(() -> load(game));
        }
        return null;
    }

    private void load(Game game) {
        Image image = null;
        try {
            Optional<Path> steamIcon = locator().steamIcon(game);
            if (steamIcon.isPresent()) {
                Image jpg = new Image(steamIcon.get().toUri().toString());
                image = jpg.isError() ? null : jpg;
            }
            if (image == null) {
                image = GameIconLocator.mainExecutable(game.installDir())
                        .flatMap(exe -> PeIconExtractor.extract(exe, DESIRED_SIZE))
                        .map(GameIcons::toImage)
                        .orElse(null);
            }
        } catch (RuntimeException e) {
            image = null; // без іконки — покажемо літеру
        }
        cache.put(game.id(), Optional.ofNullable(image));
        Platform.runLater(onLoaded);
    }

    /** Обкладинка гри, якщо вона вже завантажена; інакше запускає завантаження і повертає {@code null}. */
    Art art(Game game) {
        Optional<Art> cached = artCache.get(game.id());
        if (cached != null) {
            return cached.orElse(null);
        }
        if (artLoading.add(game.id())) {
            pool.submit(() -> loadArt(game));
        }
        return null;
    }

    private void loadArt(Game game) {
        Art art = null;
        try {
            art = locator().steamArt(game).map(found -> new Art(
                    // фон до 1600 px завширшки — досить для шапки й економить пам'ять (оригінал 3840 px)
                    found.hero() == null ? null : image(found.hero(), 1600, 0),
                    found.logo() == null ? null : image(found.logo(), 0, 160))).orElse(null);
            if (art != null && art.hero() == null && art.logo() == null) {
                art = null;
            }
        } catch (RuntimeException e) {
            art = null;
        }
        artCache.put(game.id(), Optional.ofNullable(art));
        Platform.runLater(onLoaded);
    }

    private static Image image(Path file, double width, double height) {
        Image image = new Image(file.toUri().toString(), width, height, true, true, false);
        return image.isError() ? null : image;
    }

    /** Папку Steam шукаємо один раз і лише у фоновому потоці (це виклик reg.exe). */
    private GameIconLocator locator() {
        GameIconLocator current = locator;
        if (current == null) {
            current = new GameIconLocator(SteamScanner.findSteamRoot());
            locator = current;
        }
        return current;
    }

    private static Image toImage(IconImage icon) {
        if (icon.isPng()) {
            Image png = new Image(new ByteArrayInputStream(icon.png()));
            return png.isError() ? null : png;
        }
        WritableImage image = new WritableImage(icon.width(), icon.height());
        image.getPixelWriter().setPixels(0, 0, icon.width(), icon.height(),
                PixelFormat.getIntArgbInstance(), icon.argb(), 0, icon.width());
        return image;
    }

    void shutdown() {
        pool.shutdownNow();
    }
}
