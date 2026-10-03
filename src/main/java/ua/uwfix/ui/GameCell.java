package ua.uwfix.ui;

import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.MenuItem;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import ua.uwfix.i18n.I18n;
import ua.uwfix.model.Game;
import ua.uwfix.model.GameSource;

import java.util.Locale;
import java.util.function.Consumer;
import java.util.function.Function;

/** Комірка списку ігор: іконка, назва, лаунчер і позначка стану. */
final class GameCell extends ListCell<Game> {

    /** Позначка біля гри у списку. */
    enum Badge { NONE, PATCHED, OUTDATED }

    private static final double ICON_SIZE = 32;

    private final StackPane iconBox = new StackPane();
    private final Label name = new Label();
    private final Label source = new Label();
    private final Label badge = new Label();
    private final HBox box;
    private final Function<Game, Badge> badgeProvider;
    private final Function<Game, Image> iconProvider;
    private final Consumer<Game> onOpenFolder;
    private final Consumer<Game> onRemove;

    GameCell(Function<Game, Badge> badgeProvider, Function<Game, Image> iconProvider,
             Consumer<Game> onOpenFolder, Consumer<Game> onRemove) {
        this.badgeProvider = badgeProvider;
        this.iconProvider = iconProvider;
        this.onOpenFolder = onOpenFolder;
        this.onRemove = onRemove;

        name.getStyleClass().add("game-name");
        source.getStyleClass().add("game-source");
        badge.getStyleClass().add("badge");
        iconBox.setMinSize(ICON_SIZE, ICON_SIZE);
        iconBox.setMaxSize(ICON_SIZE, ICON_SIZE);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox bottom = new HBox(8, source, spacer, badge);
        bottom.setAlignment(Pos.CENTER_LEFT);
        VBox text = new VBox(3, name, bottom);
        HBox.setHgrow(text, Priority.ALWAYS);
        box = new HBox(10, iconBox, text);
        box.setAlignment(Pos.CENTER_LEFT);
        box.getStyleClass().add("game-cell-box");
        setPrefWidth(0); // не розтягувати список під найдовшу назву
    }

    @Override
    protected void updateItem(Game game, boolean empty) {
        super.updateItem(game, empty);
        if (empty || game == null) {
            setGraphic(null);
            setText(null);
            setContextMenu(null);
            return;
        }
        name.setText(game.name());
        source.setText(game.source().displayName());
        iconBox.getChildren().setAll(iconNode(iconProvider.apply(game), game.name(), ICON_SIZE));

        badge.getStyleClass().removeAll("badge-ok", "badge-warn");
        switch (badgeProvider.apply(game)) {
            case PATCHED -> {
                badge.setText(I18n.t("badge.fixed"));
                badge.getStyleClass().add("badge-ok");
                badge.setVisible(true);
            }
            case OUTDATED -> {
                badge.setText(I18n.t("badge.updated"));
                badge.getStyleClass().add("badge-warn");
                badge.setVisible(true);
            }
            default -> badge.setVisible(false);
        }

        MenuItem open = new MenuItem(I18n.t("menu.openFolder"));
        open.setOnAction(e -> onOpenFolder.accept(game));
        ContextMenu menu = new ContextMenu(open);
        if (game.source() == GameSource.MANUAL) {
            MenuItem remove = new MenuItem(I18n.t("menu.remove"));
            remove.setOnAction(e -> onRemove.accept(game));
            menu.getItems().add(remove);
        }
        setContextMenu(menu);
        setText(null);
        setGraphic(box);
    }

    /**
     * Іконка гри із заокругленими кутами або, якщо її немає, кольоровий квадрат
     * з першою літерою назви (колір залежить від назви, тож у кожної гри свій).
     */
    static Node iconNode(Image image, String gameName, double size) {
        double arc = size * 0.3;
        if (image != null) {
            ImageView view = new ImageView(image);
            view.setFitWidth(size);
            view.setFitHeight(size);
            view.setPreserveRatio(true);
            view.setSmooth(true);
            Rectangle clip = new Rectangle(size, size);
            clip.setArcWidth(arc);
            clip.setArcHeight(arc);
            view.setClip(clip);
            return view;
        }
        String letter = gameName == null || gameName.isBlank()
                ? "?" : gameName.strip().substring(0, 1).toUpperCase(Locale.ROOT);
        Label label = new Label(letter);
        label.getStyleClass().add("game-icon-letter");
        label.setMinSize(size, size);
        label.setMaxSize(size, size);
        label.setAlignment(Pos.CENTER);
        double hue = Math.floorMod(gameName == null ? 0 : gameName.hashCode(), 360);
        Color color = Color.hsb(hue, 0.45, 0.55);
        label.setStyle(String.format(Locale.ROOT,
                "-fx-background-color: #%02x%02x%02x; -fx-background-radius: %.0f; -fx-font-size: %.0fpx;",
                (int) (color.getRed() * 255), (int) (color.getGreen() * 255), (int) (color.getBlue() * 255),
                arc / 2, size * 0.5));
        return label;
    }
}
