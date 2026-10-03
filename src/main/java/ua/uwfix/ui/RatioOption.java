package ua.uwfix.ui;

import ua.uwfix.model.AspectRatio;

/**
 * Пункт списку «Цільова роздільна здатність».
 *
 * @param label  текст пункту
 * @param ratio  співвідношення (null для пункту «Інша…»)
 * @param custom чи це пункт для введення своєї роздільної здатності
 */
public record RatioOption(String label, AspectRatio ratio, boolean custom) {

    static RatioOption monitor(int index, AspectRatio ratio) {
        return new RatioOption("Монітор " + index + " · " + ratio, ratio, false);
    }

    static RatioOption preset(AspectRatio ratio) {
        return new RatioOption(ratio.toString(), ratio, false);
    }

    static RatioOption own(AspectRatio ratio) {
        return new RatioOption("Своя · " + ratio, ratio, false);
    }

    static RatioOption other() {
        return new RatioOption("Інша роздільна здатність…", null, true);
    }

    @Override
    public String toString() {
        return label;
    }
}
