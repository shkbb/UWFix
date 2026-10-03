package ua.uwfix.ui;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Записує іконку Windows (.ico) з кількох PNG-зображень різного розміру.
 * <p>
 * Формат: заголовок (6 байтів), таблиця записів по 16 байтів на кожне зображення,
 * далі самі PNG. Windows Vista і новіші підтримують PNG усередині .ico.
 */
final class IcoWriter {

    /** Одне зображення іконки. */
    record Entry(int size, byte[] png) {
    }

    private IcoWriter() {
    }

    static void write(List<Entry> entries, Path target) throws IOException {
        int headerSize = 6 + 16 * entries.size();
        ByteBuffer header = ByteBuffer.allocate(headerSize).order(ByteOrder.LITTLE_ENDIAN);
        header.putShort((short) 0);               // зарезервовано
        header.putShort((short) 1);               // тип: іконка
        header.putShort((short) entries.size());  // кількість зображень
        int offset = headerSize;
        for (Entry e : entries) {
            header.put((byte) (e.size() >= 256 ? 0 : e.size())); // 0 означає 256
            header.put((byte) (e.size() >= 256 ? 0 : e.size()));
            header.put((byte) 0);                 // кількість кольорів палітри
            header.put((byte) 0);                 // зарезервовано
            header.putShort((short) 1);           // площини
            header.putShort((short) 32);          // біт на піксель
            header.putInt(e.png().length);
            header.putInt(offset);
            offset += e.png().length;
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(header.array());
        for (Entry e : entries) {
            out.write(e.png());
        }
        if (target.getParent() != null) {
            Files.createDirectories(target.getParent());
        }
        Files.write(target, out.toByteArray());
    }
}
