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

import java.io.InputStream;
import java.util.Objects;

/** JavaFX-застосунок: створює головне вікно. Запускається з {@link Main}. */
public final class App extends Application {

    public static final String NAME = "UWFix";
    public static final String VERSION = "1.0.0";

    private AppContext context;

    @Override
    public void start(Stage stage) throws Exception {
        context = AppContext.create(getParameters().getRaw());
        I18n.setLanguage(Language.detect(context.store().state().language()));

        FXMLLoader loader = new FXMLLoader(MainController.class.getResource("main.fxml"));
        MainController controller = new MainController(context, stage);
        loader.setController(controller);
        Parent root = loader.load();

        Scene scene = new Scene(root, 1180, 760);
        scene.getStylesheets().add(
                Objects.requireNonNull(MainController.class.getResource("style.css")).toExternalForm());

        stage.setTitle(I18n.t("app.title"));
        stage.setMinWidth(960);
        stage.setMinHeight(620);
        try (InputStream icon = MainController.class.getResourceAsStream("icon.png")) {
            if (icon != null) {
                stage.getIcons().add(new Image(icon));
            }
        }
        stage.setScene(scene);
        stage.show();
        controller.onShown();
    }

    @Override
    public void stop() {
        if (context != null) {
            context.shutdown();
        }
    }
}
