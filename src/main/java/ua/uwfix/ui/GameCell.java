package ua.uwfix.ui;

import javafx.geometry.Pos;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.MenuItem;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import ua.uwfix.model.Game;
import ua.uwfix.model.GameSource;

import java.util.function.Consumer;
import java.util.function.Function;

/** Комірка списку ігор: назва, лаунчер і позначка стану. */
final class GameCell extends ListCell<Game> {

    /** Позначка біля гри у списку. */
    enum Badge { NONE, PATCHED, OUTDATED }

    private final Label name = new Label();
    private final Label source = new Label();
    private final Label badge = new Label();
    private final VBox box;
    private final Function<Game, Badge> badgeProvider;
    private final Consumer<Game> onOpenFolder;
    private final Consumer<Game> onRemove;

    GameCell(Function<Game, Badge> badgeProvider, Consumer<Game> onOpenFolder, Consumer<Game> onRemove) {
        this.badgeProvider = badgeProvider;
        this.onOpenFolder = onOpenFolder;
        this.onRemove = onRemove;

        name.getStyleClass().add("game-name");
        source.getStyleClass().add("game-source");
        badge.getStyleClass().add("badge");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox bottom = new HBox(8, source, spacer, badge);
        bottom.setAlignment(Pos.CENTER_LEFT);
        box = new VBox(3, name, bottom);
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

        badge.getStyleClass().removeAll("badge-ok", "badge-warn");
        switch (badgeProvider.apply(game)) {
            case PATCHED -> {
                badge.setText("✓ Виправлено");
                badge.getStyleClass().add("badge-ok");
                badge.setVisible(true);
            }
            case OUTDATED -> {
                badge.setText("⟳ Оновилась");
                badge.getStyleClass().add("badge-warn");
                badge.setVisible(true);
            }
            default -> badge.setVisible(false);
        }

        MenuItem open = new MenuItem("Відкрити папку гри");
        open.setOnAction(e -> onOpenFolder.accept(game));
        ContextMenu menu = new ContextMenu(open);
        if (game.source() == GameSource.MANUAL) {
            MenuItem remove = new MenuItem("Прибрати зі списку");
            remove.setOnAction(e -> onRemove.accept(game));
            menu.getItems().add(remove);
        }
        setContextMenu(menu);
        setText(null);
        setGraphic(box);
    }
}
