package ua.uwfix.model;

import java.util.List;
import java.util.Locale;

/**
 * Співвідношення сторін, задане роздільною здатністю (наприклад 3440×1440).
 * <p>
 * Ігри зберігають співвідношення як дробове число: 16:9 = 1.7777778,
 * 3440×1440 = 2.3888888. Саме ці числа ми шукаємо і замінюємо у файлах гри.
 */
public record AspectRatio(int width, int height) {

    /** Стандартне співвідношення 16:9, під яке зроблені катсцени більшості ігор. */
    public static final AspectRatio STANDARD = new AspectRatio(16, 9);

    /** Типові роздільні здатності ультрашироких моніторів. */
    public static final List<AspectRatio> PRESETS = List.of(
            new AspectRatio(2560, 1080),
            new AspectRatio(3440, 1440),
            new AspectRatio(3840, 1600),
            new AspectRatio(5120, 2160),
            new AspectRatio(3840, 1080),
            new AspectRatio(5120, 1440),
            new AspectRatio(3840, 1200));

    public AspectRatio {
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException("Ширина і висота мають бути додатними числами");
        }
    }

    /** Співвідношення як число: ширина / висота. */
    public double value() {
        return (double) width / height;
    }

    /** Чи збігається співвідношення з 16:9 з точністю float (тоді змінювати нічого не треба). */
    public boolean isStandard() {
        return (float) value() == (float) STANDARD.value();
    }

    /** Наприклад «3440×1440». */
    public String resolutionText() {
        return width + "×" + height;
    }

    /** Наприклад «2.389». */
    public String valueText() {
        return String.format(Locale.ROOT, "%.3f", value());
    }

    /** Наприклад «21:9», «32:9» — назва, під якою такі монітори продають. */
    public String marketingName() {
        double v = value();
        if (Math.abs(v - 16.0 / 9) < 0.01) return "16:9";
        if (Math.abs(v - 16.0 / 10) < 0.01) return "16:10";
        if (v > 2.3 && v < 2.45) return "21:9";
        if (v > 3.15 && v < 3.25) return "32:10";
        if (v > 3.5 && v < 3.6) return "32:9";
        return valueText() + ":1";
    }

    @Override
    public String toString() {
        return resolutionText() + " (" + marketingName() + ")";
    }
}
