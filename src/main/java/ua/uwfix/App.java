package ua.uwfix;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.stage.Stage;
import ua.uwfix.i18n.I18n;
import ua.uwfix.i18n.Language;
import ua.uwfix.ui.AppContext;
import ua.uwfix.ui.MainController;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.Objects;
import java.util.Properties;

/** JavaFX-застосунок: створює головне вікно. Запускається з {@link Main}. */
public final class App extends Application {

    public static final String NAME = "UWFix";
    /** Версія з pom.xml (Maven підставляє її у version.properties під час збирання). */
    public static final String VERSION = readVersion();

    private AppContext context;
    private Stage stage;

    @Override
    public void start(Stage stage) throws Exception {
        this.stage = stage;
        context = AppContext.create(getParameters().getRaw());
        I18n.setLanguage(Language.detect(context.store().state().language()));

        stage.setMinWidth(960);
        stage.setMinHeight(620);
        try (InputStream icon = MainController.class.getResourceAsStream("icon.png")) {
            if (icon != null) {
                stage.getIcons().add(new Image(icon));
            }
        }
        MainController controller = loadUi();
        stage.show();
        controller.onShown();
    }

    /**
     * Завантажує розмітку з поточним словником і ставить її у вікно.
     * Викликається при запуску і після зміни мови.
     */
    private MainController loadUi() {
        FXMLLoader loader = new FXMLLoader(MainController.class.getResource("main.fxml"), I18n.bundle());
        MainController controller = new MainController(context, stage, this::reloadUi);
        loader.setController(controller);
        Parent root;
        try {
            root = loader.load();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        if (stage.getScene() == null) {
            Scene scene = new Scene(root, 1180, 760);
            scene.getStylesheets().add(
                    Objects.requireNonNull(MainController.class.getResource("style.css")).toExternalForm());
            stage.setScene(scene);
        } else {
            stage.getScene().setRoot(root); // розмір і положення вікна зберігаються
        }
        stage.setTitle(I18n.t("app.title"));
        return controller;
    }

    /** Перебудовує вікно після зміни мови й знову обирає гру {@code gameId}. */
    private void reloadUi(String gameId) {
        loadUi().onReloaded(gameId);
    }

    @Override
    public void stop() {
        if (context != null) {
            context.shutdown();
        }
    }

    private static String readVersion() {
        // -Duwfix.version=... дозволяє вдати стару версію, щоб перевірити оновлення
        String override = System.getProperty("uwfix.version");
        if (override != null && !override.isBlank()) {
            return override;
        }
        Properties properties = new Properties();
        try (InputStream in = App.class.getResourceAsStream("version.properties")) {
            if (in != null) {
                properties.load(in);
            }
        } catch (IOException ignored) {
            // без файлу версії програма працює, просто не покаже номер
        }
        return properties.getProperty("version", "dev");
    }
}
