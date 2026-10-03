package ua.uwfix.ui;

import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.paint.Color;
import javafx.scene.paint.CycleMethod;
import javafx.scene.paint.LinearGradient;
import javafx.scene.paint.Stop;
import javafx.scene.shape.Polygon;
import javafx.scene.shape.Rectangle;

/**
 * Іконка програми, намальована засобами JavaFX: ультраширокий екран,
 * з якого «розсуваються» стрілки — смуги зникають.
 */
final class AppIcon {

    private static final Color ACCENT = Color.web("#f2a93b");

    private AppIcon() {
    }

    static Node create(double size) {
        double s = size / 256.0;

        Rectangle background = new Rectangle(256 * s, 256 * s);
        background.setArcWidth(112 * s);
        background.setArcHeight(112 * s);
        background.setFill(new LinearGradient(0, 0, 0, 1, true, CycleMethod.NO_CYCLE,
                new Stop(0, Color.web("#2b313d")), new Stop(1, Color.web("#13161b"))));

        // ультраширокий екран (21:9)
        Rectangle screen = new Rectangle(28 * s, 82 * s, 200 * s, 92 * s);
        screen.setArcWidth(24 * s);
        screen.setArcHeight(24 * s);
        screen.setFill(Color.web("#0d0f13"));
        screen.setStroke(ACCENT);
        screen.setStrokeWidth(10 * s);

        // зображення всередині, розтягнуте на всю ширину
        Rectangle picture = new Rectangle(46 * s, 98 * s, 164 * s, 60 * s);
        picture.setArcWidth(10 * s);
        picture.setArcHeight(10 * s);
        picture.setFill(new LinearGradient(0, 0, 1, 0, true, CycleMethod.NO_CYCLE,
                new Stop(0, Color.web("#3a6ea5")), new Stop(1, Color.web("#7a4fa3"))));

        Polygon left = arrow(s, 60, -1);
        Polygon right = arrow(s, 196, 1);

        // підставка монітора
        Rectangle stand = new Rectangle(112 * s, 182 * s, 32 * s, 16 * s);
        stand.setFill(ACCENT);
        Rectangle base = new Rectangle(84 * s, 196 * s, 88 * s, 12 * s);
        base.setArcWidth(12 * s);
        base.setArcHeight(12 * s);
        base.setFill(ACCENT);

        return new Group(background, screen, picture, left, right, stand, base);
    }

    /** Стрілка, що вказує назовні: direction = −1 ліворуч, 1 праворуч. */
    private static Polygon arrow(double s, double tipX, int direction) {
        double cy = 128;
        double back = tipX - direction * 26;
        Polygon p = new Polygon(
                tipX * s, cy * s,
                back * s, (cy - 20) * s,
                back * s, (cy + 20) * s);
        p.setFill(Color.web("#f5f6f8"));
        return p;
    }
}
