package ua.uwfix.model;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Формат, у якому число співвідношення сторін записане у файлі гри.
 * <p>
 * Процесори x86/x64 зберігають числа у порядку little-endian (молодший байт першим),
 * тому 16:9 у форматі float (IEEE 754, 4 байти) виглядає у файлі як {@code 39 8E E3 3F}.
 */
public enum ValueFormat {

    /** 32-бітне число IEEE 754 (float). Найпоширеніший варіант. */
    FLOAT32("float, 4 байти") {
        @Override
        public byte[] encode(double value) {
            return ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putFloat((float) value).array();
        }
    },

    /** 64-бітне число IEEE 754 (double). Трапляється рідше. */
    FLOAT64("double, 8 байтів") {
        @Override
        public byte[] encode(double value) {
            return ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN).putDouble(value).array();
        }
    };

    private final String description;

    ValueFormat(String description) {
        this.description = description;
    }

    /** Перетворює число у байти так, як воно лежить у пам'яті та у файлі. */
    public abstract byte[] encode(double value);

    public byte[] encode(AspectRatio ratio) {
        return encode(ratio.value());
    }

    public String description() {
        return description;
    }
}
