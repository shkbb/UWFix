package ua.uwfix.ui;

import javafx.geometry.Insets;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.DialogPane;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.control.TextFormatter;
import javafx.scene.layout.GridPane;
import javafx.stage.Window;
import ua.uwfix.model.AspectRatio;

import java.util.Objects;
import java.util.Optional;

/** Діалогові вікна в стилі програми. */
final class Dialogs {

    private Dialogs() {
    }

    static void info(Window owner, String header, String content) {
        Alert alert = create(Alert.AlertType.INFORMATION, owner, header, content);
        alert.showAndWait();
    }

    static void error(Window owner, String header, String content) {
        Alert alert = create(Alert.AlertType.ERROR, owner, header, content);
        alert.showAndWait();
    }

    /** Питання з кнопками «Так» / «Ні». За замовчуванням обрано «Ні». */
    static boolean confirm(Window owner, String header, String content, String yesText) {
        Alert alert = create(Alert.AlertType.CONFIRMATION, owner, header, content);
        ButtonType yes = new ButtonType(yesText, ButtonBar.ButtonData.OK_DONE);
        ButtonType no = new ButtonType("Скасувати", ButtonBar.ButtonData.CANCEL_CLOSE);
        alert.getButtonTypes().setAll(yes, no);
        ((javafx.scene.control.Button) alert.getDialogPane().lookupButton(yes)).setDefaultButton(false);
        ((javafx.scene.control.Button) alert.getDialogPane().lookupButton(no)).setDefaultButton(true);
        return alert.showAndWait().filter(b -> b == yes).isPresent();
    }

    /** Введення своєї роздільної здатності. */
    static Optional<AspectRatio> askResolution(Window owner, AspectRatio initial) {
        Dialog<AspectRatio> dialog = new Dialog<>();
        dialog.initOwner(owner);
        dialog.setTitle("Своя роздільна здатність");
        dialog.setHeaderText("Вкажи роздільну здатність монітора");
        style(dialog.getDialogPane());

        TextField width = numberField(initial == null ? "" : String.valueOf(initial.width()));
        TextField height = numberField(initial == null ? "" : String.valueOf(initial.height()));
        Label preview = new Label();
        preview.getStyleClass().add("hint");

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(6, 0, 0, 0));
        grid.addRow(0, new Label("Ширина"), width);
        grid.addRow(1, new Label("Висота"), height);
        grid.add(preview, 0, 2, 2, 1);
        dialog.getDialogPane().setContent(grid);

        ButtonType ok = new ButtonType("Обрати", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().setAll(ok, ButtonType.CANCEL);
        javafx.scene.Node okButton = dialog.getDialogPane().lookupButton(ok);

        Runnable validate = () -> {
            AspectRatio r = parse(width.getText(), height.getText());
            okButton.setDisable(r == null);
            preview.setText(r == null ? "Введи дві додатні цілі величини" : "Співвідношення: " + r.valueText()
                    + " (" + r.marketingName() + ")");
        };
        width.textProperty().addListener((o, a, b) -> validate.run());
        height.textProperty().addListener((o, a, b) -> validate.run());
        validate.run();

        dialog.setResultConverter(button -> button == ok ? parse(width.getText(), height.getText()) : null);
        return dialog.showAndWait();
    }

    private static AspectRatio parse(String w, String h) {
        try {
            int width = Integer.parseInt(w.trim());
            int height = Integer.parseInt(h.trim());
            return width > 0 && height > 0 && width <= 32768 && height <= 32768 ? new AspectRatio(width, height) : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static TextField numberField(String value) {
        TextField field = new TextField(value);
        field.setPrefColumnCount(8);
        field.setTextFormatter(new TextFormatter<String>(c -> c.getControlNewText().matches("\\d{0,5}") ? c : null));
        return field;
    }

    private static Alert create(Alert.AlertType type, Window owner, String header, String content) {
        Alert alert = new Alert(type);
        alert.initOwner(owner);
        alert.setTitle("UWFix");
        alert.setHeaderText(header);
        alert.setContentText(content);
        alert.getDialogPane().setMinWidth(480);
        alert.getDialogPane().setMinHeight(javafx.scene.layout.Region.USE_PREF_SIZE);
        style(alert.getDialogPane());
        return alert;
    }

    static void style(DialogPane pane) {
        pane.getStylesheets().add(Objects.requireNonNull(Dialogs.class.getResource("style.css")).toExternalForm());
        pane.getStyleClass().add("uw-dialog");
    }
}
