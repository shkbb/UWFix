package ua.uwfix.search;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import ua.uwfix.util.ProgressListener;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CancellationException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class FileScannerTest {

    @TempDir
    Path dir;

    private static final byte[] FLOAT = Hex.parse("39 8E E3 3F");
    private static final byte[] DOUBLE = Hex.parse("1C C7 71 1C C7 71 FC 3F");

    /** Різні розміри блоку, щоб входження потрапляли на межі блоків. */
    @ParameterizedTest
    @ValueSource(ints = {1, 3, 4, 7, 8, 64, 1000, FileScanner.DEFAULT_CHUNK})
    void findsSameOffsetsForAnyChunkSize(int chunkSize) throws Exception {
        byte[] data = randomData(5000, 1);
        List<Long> floats = plant(data, FLOAT, 37, 2);
        List<Long> doubles = plant(data, DOUBLE, 9, 3);
        Path file = dir.resolve("game.exe");
        Files.write(file, data);

        ScanResult result = new FileScanner(new HorspoolSearch(), chunkSize)
                .scan(file, List.of(FLOAT, DOUBLE), ProgressListener.NONE);

        assertEquals(floats, result.offsets().get(0));
        assertEquals(doubles, result.offsets().get(1));
        assertEquals(data.length, result.size());
        assertEquals(sha256(data), result.sha256());
    }

    @Test
    void reportsNonOverlappingMatchesOnly() throws Exception {
        Path file = dir.resolve("a.bin");
        Files.write(file, new byte[]{5, 5, 5, 5, 5});
        ScanResult result = new FileScanner(new KmpSearch(), 2).scan(file, List.of(new byte[]{5, 5}), ProgressListener.NONE);
        assertEquals(List.of(0L, 2L), result.offsets().get(0));
    }

    @Test
    void hashOfEmptyFile() throws Exception {
        Path file = dir.resolve("empty.bin");
        Files.write(file, new byte[0]);
        assertEquals(sha256(new byte[0]), new FileScanner().sha256(file));
    }

    @Test
    void canBeCancelled() throws Exception {
        Path file = dir.resolve("big.bin");
        Files.write(file, new byte[10_000]);
        ProgressListener cancelled = new ProgressListener() {
            @Override
            public void update(double fraction, String message) {
            }

            @Override
            public boolean isCancelled() {
                return true;
            }
        };
        assertThrows(CancellationException.class,
                () -> new FileScanner(new HorspoolSearch(), 100).scan(file, List.of(FLOAT), cancelled));
    }

    // ------------------------------------------------------------------

    private static byte[] randomData(int size, long seed) {
        byte[] data = new byte[size];
        new Random(seed).nextBytes(data);
        // прибираємо випадкові збіги з шаблонами, щоб знати точну відповідь
        for (int i = 0; i < size; i++) {
            if ((data[i] & 0xFF) == 0x39 || (data[i] & 0xFF) == 0x1C) {
                data[i] = 0;
            }
        }
        return data;
    }

    /** Вписує шаблон приблизно через кожні {@code step} байтів і повертає зміщення. */
    private static List<Long> plant(byte[] data, byte[] pattern, int count, int phase) {
        List<Long> offsets = new ArrayList<>();
        int step = data.length / (count + 1);
        for (int k = 1; k <= count; k++) {
            int offset = k * step + phase;
            System.arraycopy(pattern, 0, data, offset, pattern.length);
            offsets.add((long) offset);
        }
        return offsets;
    }

    private static String sha256(byte[] data) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data));
    }
}
