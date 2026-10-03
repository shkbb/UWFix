package ua.uwfix.settings;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

/**
 * Текстовий файл зі збереженням кодування. Unreal Engine пише ini-файли то в UTF-8,
 * то в UTF-16 з BOM — після редагування файл має лишитися в тому самому кодуванні,
 * інакше гра може його не прочитати.
 */
record TextFile(String text, Charset charset, byte[] bom) {

    private static final byte[] UTF8_BOM = {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};
    private static final byte[] UTF16LE_BOM = {(byte) 0xFF, (byte) 0xFE};
    private static final byte[] UTF16BE_BOM = {(byte) 0xFE, (byte) 0xFF};

    static TextFile read(Path file) throws IOException {
        byte[] bytes = Files.readAllBytes(file);
        if (startsWith(bytes, UTF8_BOM)) {
            return decode(bytes, UTF8_BOM, StandardCharsets.UTF_8);
        }
        if (startsWith(bytes, UTF16LE_BOM)) {
            return decode(bytes, UTF16LE_BOM, StandardCharsets.UTF_16LE);
        }
        if (startsWith(bytes, UTF16BE_BOM)) {
            return decode(bytes, UTF16BE_BOM, StandardCharsets.UTF_16BE);
        }
        return new TextFile(new String(bytes, StandardCharsets.UTF_8), StandardCharsets.UTF_8, new byte[0]);
    }

    /** Записує новий текст у тому самому кодуванні та з тим самим BOM. */
    void write(Path file, String newText) throws IOException {
        byte[] body = newText.getBytes(charset);
        byte[] all = Arrays.copyOf(bom, bom.length + body.length);
        System.arraycopy(body, 0, all, bom.length, body.length);
        Files.write(file, all);
    }

    private static TextFile decode(byte[] bytes, byte[] bom, Charset charset) {
        return new TextFile(new String(bytes, bom.length, bytes.length - bom.length, charset), charset, bom);
    }

    private static boolean startsWith(byte[] data, byte[] prefix) {
        if (data.length < prefix.length) {
            return false;
        }
        for (int i = 0; i < prefix.length; i++) {
            if (data[i] != prefix[i]) {
                return false;
            }
        }
        return true;
    }
}
