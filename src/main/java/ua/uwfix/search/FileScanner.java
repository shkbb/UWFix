package ua.uwfix.search;

import ua.uwfix.util.ProgressListener;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;

/**
 * Потокове сканування файлу: пошук кількох шаблонів і підрахунок SHA-256 за один прохід.
 * <p>
 * Файли ігор бувають по кілька сотень мегабайтів, тому файл не читається
 * в пам'ять повністю, а обробляється блоками ({@link #DEFAULT_CHUNK}).
 * Щоб не пропустити входження на межі двох блоків, останні (m − 1) байтів
 * блоку переносяться на початок наступного (m — довжина найдовшого шаблону).
 */
public final class FileScanner {

    /** Розмір блоку читання — 8 МБ. */
    public static final int DEFAULT_CHUNK = 8 * 1024 * 1024;

    private final ByteSearch algorithm;
    private final int chunkSize;

    public FileScanner() {
        this(new HorspoolSearch(), DEFAULT_CHUNK);
    }

    public FileScanner(ByteSearch algorithm, int chunkSize) {
        if (chunkSize < 1) {
            throw new IllegalArgumentException("Розмір блоку має бути додатним");
        }
        this.algorithm = algorithm;
        this.chunkSize = chunkSize;
    }

    /**
     * Шукає всі неперекривні входження кожного шаблону та рахує SHA-256 файлу.
     *
     * @param file     файл для сканування
     * @param patterns шаблони (можуть мати різну довжину)
     * @param progress слухач прогресу; через нього ж можна скасувати сканування
     */
    public ScanResult scan(Path file, List<byte[]> patterns, ProgressListener progress) throws IOException {
        int maxLen = 1;
        for (byte[] p : patterns) {
            if (p.length == 0) {
                throw new IllegalArgumentException("Порожній шаблон");
            }
            maxLen = Math.max(maxLen, p.length);
        }

        List<List<Long>> found = new ArrayList<>();
        long[] nextAllowed = new long[patterns.size()]; // щоб входження одного шаблону не перекривалися
        for (int k = 0; k < patterns.size(); k++) {
            found.add(new ArrayList<>());
        }

        MessageDigest sha = newSha256();
        long size = Files.size(file);
        byte[] buf = new byte[chunkSize + maxLen - 1];

        try (InputStream in = Files.newInputStream(file)) {
            long base = 0;      // зміщення buf[0] у файлі
            int filled = 0;     // скільки байтів у буфері
            boolean eof = false;
            while (true) {
                progress.checkCancelled();
                int requested = buf.length - filled;
                int n = in.readNBytes(buf, filled, requested);
                if (n < requested) {
                    eof = true;
                }
                filled += n;

                // Входження, що починаються до limit, шукаємо зараз; решта — у наступному блоці.
                int limit = eof ? filled : filled - (maxLen - 1);
                final long chunkBase = base;
                for (int k = 0; k < patterns.size(); k++) {
                    byte[] pattern = patterns.get(k);
                    int len = pattern.length;
                    int end = Math.min(filled, limit + len - 1);
                    List<Long> list = found.get(k);
                    final int index = k;
                    algorithm.search(buf, 0, end, pattern, pos -> {
                        long absolute = chunkBase + pos;
                        if (absolute >= nextAllowed[index]) {
                            list.add(absolute);
                            nextAllowed[index] = absolute + len;
                        }
                    });
                }
                sha.update(buf, 0, limit);

                if (size > 0) {
                    progress.update(Math.min(1.0, (double) (base + limit) / size), null);
                }
                if (eof) {
                    break;
                }
                System.arraycopy(buf, limit, buf, 0, filled - limit);
                base += limit;
                filled -= limit;
            }
        }
        return new ScanResult(size, HexFormat.of().formatHex(sha.digest()), found);
    }

    /** Лише контрольна сума SHA-256 файлу. */
    public String sha256(Path file) throws IOException {
        return scan(file, List.of(), ProgressListener.NONE).sha256();
    }

    private static MessageDigest newSha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 недоступний у цій JVM", e);
        }
    }
}
