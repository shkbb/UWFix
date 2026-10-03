package ua.uwfix.ui;

import javafx.scene.image.Image;
import javafx.scene.image.PixelReader;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.CRC32;
import java.util.zip.Deflater;
import java.util.zip.DeflaterOutputStream;

/**
 * Мінімальний кодувальник PNG (RGBA, 8 біт на канал).
 * Потрібен, щоб зберігати знімки вікна для документації без модуля java.desktop.
 * <p>
 * PNG = сигнатура + блоки IHDR (розміри), IDAT (стиснуті zlib рядки пікселів), IEND.
 */
public final class PngWriter {

    private static final byte[] SIGNATURE = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n'};

    private PngWriter() {
    }

    public static void write(Image image, Path target) throws IOException {
        byte[] png = encode(image);
        if (target.getParent() != null) {
            Files.createDirectories(target.getParent());
        }
        Files.write(target, png);
    }

    /** Кодує зображення у байти PNG. */
    public static byte[] encode(Image image) throws IOException {
        int width = (int) image.getWidth();
        int height = (int) image.getHeight();
        PixelReader reader = image.getPixelReader();

        ByteArrayOutputStream raw = new ByteArrayOutputStream();
        try (DeflaterOutputStream z = new DeflaterOutputStream(raw, new Deflater(Deflater.BEST_COMPRESSION))) {
            byte[] row = new byte[1 + width * 4];
            for (int y = 0; y < height; y++) {
                row[0] = 0; // фільтр «None»
                for (int x = 0; x < width; x++) {
                    int argb = reader.getArgb(x, y);
                    int i = 1 + x * 4;
                    row[i] = (byte) (argb >> 16);
                    row[i + 1] = (byte) (argb >> 8);
                    row[i + 2] = (byte) argb;
                    row[i + 3] = (byte) (argb >>> 24);
                }
                z.write(row);
            }
        }

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(SIGNATURE);
        ByteArrayOutputStream header = new ByteArrayOutputStream();
        DataOutputStream h = new DataOutputStream(header);
        h.writeInt(width);
        h.writeInt(height);
        h.writeByte(8);  // біт на канал
        h.writeByte(6);  // тип кольору: RGBA
        h.writeByte(0);  // стиснення deflate
        h.writeByte(0);  // фільтрація стандартна
        h.writeByte(0);  // без черезрядковості
        writeChunk(out, "IHDR", header.toByteArray());
        writeChunk(out, "IDAT", raw.toByteArray());
        writeChunk(out, "IEND", new byte[0]);
        return out.toByteArray();
    }

    private static void writeChunk(ByteArrayOutputStream out, String type, byte[] data) throws IOException {
        DataOutputStream d = new DataOutputStream(out);
        byte[] typeBytes = type.getBytes(StandardCharsets.US_ASCII);
        d.writeInt(data.length);
        d.write(typeBytes);
        d.write(data);
        CRC32 crc = new CRC32();
        crc.update(typeBytes);
        crc.update(data);
        d.writeInt((int) crc.getValue());
    }
}
