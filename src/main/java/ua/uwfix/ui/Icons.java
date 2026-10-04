package ua.uwfix.ui;

import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.shape.SVGPath;

/**
 * Векторні значки. Вони не залежать від шрифтів: символ «⟳», наприклад, є в шрифтах Windows,
 * але в типовому Linux (шрифти Noto) його немає — замість нього з'являється квадратик.
 * Колір задається в style.css (класи icon-stroke та icon-fill).
 */
final class Icons {

    private Icons() {
    }

    /**
     * Кругова стрілка «оновити» у квадраті 24×24: дуга кола з центром (12, 12) і радіусом 6.5
     * від 0° за годинниковою стрілкою до −60° та трикутний наконечник у напрямку руху.
     */
    static Node refresh() {
        SVGPath arc = new SVGPath();
        arc.setContent("M18.5 12 A6.5 6.5 0 1 1 15.25 6.37");
        arc.getStyleClass().add("icon-stroke");
        SVGPath head = new SVGPath();
        head.setContent("M17.9 7.9 L13.9 8.7 L16.6 3.9 Z");
        head.getStyleClass().add("icon-fill");
        return new Group(arc, head);
    }
}
