package ua.uwfix.ui;

import ua.uwfix.i18n.I18n;
import ua.uwfix.model.AspectRatio;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Пункт списку «Цільова роздільна здатність».
 *
 * @param label  текст пункту
 * @param ratio  співвідношення (null для пункту «Інша…»)
 * @param custom чи це пункт для введення своєї роздільної здатності
 */
public record RatioOption(String label, AspectRatio ratio, boolean custom) {

    /** «Монітор 1 · 3440×1440» або, для кількох однакових моніторів, «Монітори 2, 3 · 1920×1080». */
    static RatioOption monitors(List<Integer> numbers, AspectRatio ratio) {
        String list = numbers.stream().map(String::valueOf).collect(Collectors.joining(", "));
        String key = numbers.size() == 1 ? "ratio.monitor" : "ratio.monitors";
        return new RatioOption(I18n.t(key, list, ratio.toString()), ratio, false);
    }

    static RatioOption preset(AspectRatio ratio) {
        return new RatioOption(ratio.toString(), ratio, false);
    }

    static RatioOption own(AspectRatio ratio) {
        return new RatioOption(I18n.t("ratio.own", ratio.toString()), ratio, false);
    }

    static RatioOption other() {
        return new RatioOption(I18n.t("ratio.other"), null, true);
    }

    @Override
    public String toString() {
        return label;
    }
}
