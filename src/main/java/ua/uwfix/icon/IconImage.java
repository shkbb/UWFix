package ua.uwfix.icon;

/**
 * Іконка, витягнута з .exe: або готовий PNG (сучасні іконки 256×256),
 * або розкодовані пікселі ARGB (класичні BMP-іконки).
 *
 * @param png    байти PNG або {@code null}
 * @param width  ширина (для PNG — 0, розмір визначить декодер)
 * @param height висота
 * @param argb   пікселі 0xAARRGGBB по рядках зверху вниз або {@code null}
 */
public record IconImage(byte[] png, int width, int height, int[] argb) {

    public static IconImage png(byte[] data) {
        return new IconImage(data, 0, 0, null);
    }

    public static IconImage pixels(int width, int height, int[] argb) {
        return new IconImage(null, width, height, argb);
    }

    public boolean isPng() {
        return png != null;
    }
}
