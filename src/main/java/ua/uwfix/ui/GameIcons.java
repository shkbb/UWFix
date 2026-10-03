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

    private final Map<String, Optional<Image>> cache = new ConcurrentHashMap<>();
    private final Set<String> loading = ConcurrentHashMap.newKeySet();
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
