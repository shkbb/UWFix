package ua.uwfix.search;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Random;

/**
 * Порівняння швидкості алгоритмів пошуку (для розділу «Тестування» курсової).
 * <p>
 * Не запускається разом зі звичайними тестами. Запуск:
 * <pre>
 *   mvnw test -Pbenchmark
 *   mvnw test -Pbenchmark -Dbench.file="D:\path\game.exe"
 * </pre>
 * Результати також записуються у {@code target/benchmark-results.txt}.
 */
@Tag("benchmark")
class SearchBenchmarkTest {

    private static final int RUNS = 7;

    /** Шаблони різної довжини: float 16:9, double 16:9 і довший 16-байтовий фрагмент. */
    private static final List<byte[]> PATTERNS = List.of(
            Hex.parse("39 8E E3 3F"),
            Hex.parse("1C C7 71 1C C7 71 FC 3F"),
            Hex.parse("39 8E E3 3F 00 00 80 3F 00 00 00 00 39 8E E3 3F"));

    @Test
    void compareAlgorithms() throws IOException {
        String path = System.getProperty("bench.file");
        byte[] data;
        String source;
        if (path != null && !path.isBlank()) {
            data = Files.readAllBytes(Path.of(path));
            source = Path.of(path).getFileName().toString();
        } else {
            data = syntheticExe(128 * 1024 * 1024);
            source = "синтетичні дані, схожі на машинний код";
        }
        double megabytes = data.length / (1024.0 * 1024.0);

        StringBuilder report = new StringBuilder();
        report.append(String.format(Locale.ROOT, "Дані: %s, %.1f МБ, Java %s%n%n",
                source, megabytes, System.getProperty("java.version")));
        report.append(String.format("%-24s %8s %12s %10s %8s%n", "Алгоритм", "m, байт", "Медіана, мс", "МБ/с", "Збігів"));

        for (byte[] pattern : PATTERNS) {
            for (ByteSearch algorithm : List.of(new NaiveSearch(), new KmpSearch(), new HorspoolSearch())) {
                int[] count = new int[1];
                algorithm.search(data, 0, data.length, pattern, i -> count[0]++); // прогрів JIT
                long[] times = new long[RUNS];
                for (int r = 0; r < RUNS; r++) {
                    count[0] = 0;
                    long start = System.nanoTime();
                    algorithm.search(data, 0, data.length, pattern, i -> count[0]++);
                    times[r] = System.nanoTime() - start;
                }
                Arrays.sort(times);
                double medianMs = times[RUNS / 2] / 1_000_000.0;
                report.append(String.format(Locale.ROOT, "%-24s %8d %12.1f %10.0f %8d%n",
                        algorithm.name(), pattern.length, medianMs, megabytes / (medianMs / 1000.0), count[0]));
            }
            report.append(System.lineSeparator());
        }

        System.out.println(report);
        Path out = Path.of("target", "benchmark-results.txt");
        Files.createDirectories(out.getParent());
        Files.writeString(out, report, StandardCharsets.UTF_8);
    }

    /**
     * Дані, схожі на машинний код: багато нулів і повторюваних байтів,
     * випадкові вставки та по кілька сотень входжень кожного шаблону.
     */
    private static byte[] syntheticExe(int size) {
        Random random = new Random(7);
        byte[] data = new byte[size];
        for (int i = 0; i < size; i++) {
            int r = random.nextInt(100);
            data[i] = r < 30 ? 0 : r < 40 ? (byte) 0xFF : r < 50 ? (byte) 0x48 : (byte) random.nextInt(256);
        }
        for (byte[] pattern : PATTERNS) {
            for (int k = 0; k < 300; k++) {
                int offset = random.nextInt(size - pattern.length);
                System.arraycopy(pattern, 0, data, offset, pattern.length);
            }
        }
        return data;
    }
}
