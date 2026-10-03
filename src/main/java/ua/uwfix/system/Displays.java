package ua.uwfix.system;

import javafx.geometry.Rectangle2D;
import javafx.stage.Screen;
import ua.uwfix.model.AspectRatio;

import java.util.ArrayList;
import java.util.List;

/** Визначає роздільну здатність підключених моніторів. */
public final class Displays {

    private Displays() {
    }

    /**
     * Фізична роздільна здатність кожного монітора, основний — першим.
     * <p>
     * JavaFX повертає розміри в логічних пікселях (з урахуванням масштабування
     * Windows, наприклад 125 %), тому множимо їх на коефіцієнт масштабу.
     * Викликати з потоку JavaFX.
     */
    public static List<AspectRatio> monitors() {
        List<AspectRatio> result = new ArrayList<>();
        Screen primary = Screen.getPrimary();
        List<Screen> screens = new ArrayList<>(Screen.getScreens());
        screens.remove(primary);
        screens.add(0, primary);
        for (Screen screen : screens) {
            Rectangle2D bounds = screen.getBounds();
            int width = (int) Math.round(bounds.getWidth() * screen.getOutputScaleX());
            int height = (int) Math.round(bounds.getHeight() * screen.getOutputScaleY());
            if (width > 0 && height > 0) {
                result.add(new AspectRatio(width, height));
            }
        }
        return result;
    }
}
