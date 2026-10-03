package ua.uwfix;

import javafx.application.Application;
import ua.uwfix.cli.ReapplyCommand;
import ua.uwfix.system.Autostart;

import java.util.Arrays;

/**
 * Точка входу програми.
 * <p>
 * Окремий клас (а не {@link App}) потрібен тому, що для класу-нащадка
 * {@code javafx.application.Application} лаунчер Java запускає JavaFX ще до виклику main,
 * і тихий режим {@code --reapply} не зміг би завершитися без вікна.
 */
public final class Main {

    private Main() {
    }

    public static void main(String[] args) {
        if (Arrays.asList(args).contains(Autostart.REAPPLY_ARG)) {
            // Тихий режим під час входу у Windows: вікно відкривається, лише якщо щось не вдалося
            ReapplyCommand.Result result = ReapplyCommand.createDefault().run();
            if (!result.needsAttention()) {
                System.exit(0);
            }
            args = Arrays.stream(args).filter(a -> !a.equals(Autostart.REAPPLY_ARG)).toArray(String[]::new);
        }
        Application.launch(App.class, args);
    }
}
