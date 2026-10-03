package ua.uwfix.search;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Усі три алгоритми мають знаходити однакові входження. */
class ByteSearchTest {

    static Stream<ByteSearch> algorithms() {
        return Stream.of(new NaiveSearch(), new KmpSearch(), new HorspoolSearch());
    }

    private static List<Integer> find(ByteSearch algorithm, byte[] text, int from, int to, byte[] pattern) {
        List<Integer> result = new ArrayList<>();
        algorithm.search(text, from, to, pattern, result::add);
        return result;
    }

    /** Еталон: перевірка кожної позиції без жодних оптимізацій. */
    private static List<Integer> reference(byte[] text, int from, int to, byte[] pattern) {
        List<Integer> result = new ArrayList<>();
        outer:
        for (int i = from; i + pattern.length <= to; i++) {
            for (int j = 0; j < pattern.length; j++) {
                if (text[i + j] != pattern[j]) {
                    continue outer;
                }
            }
            result.add(i);
        }
        return result;
    }

    @ParameterizedTest
    @MethodSource("algorithms")
    void findsPatternAtStartMiddleAndEnd(ByteSearch algorithm) {
        byte[] p = Hex.parse("39 8E E3 3F");
        byte[] text = Hex.parse("39 8E E3 3F 00 11 39 8E E3 3F 22 39 8E E3 3F");
        assertEquals(List.of(0, 6, 11), find(algorithm, text, 0, text.length, p));
    }

    @ParameterizedTest
    @MethodSource("algorithms")
    void findsOverlappingOccurrences(ByteSearch algorithm) {
        byte[] text = {7, 7, 7, 7, 7};
        byte[] p = {7, 7, 7};
        assertEquals(List.of(0, 1, 2), find(algorithm, text, 0, text.length, p));
    }

    @ParameterizedTest
    @MethodSource("algorithms")
    void respectsSearchBounds(ByteSearch algorithm) {
        byte[] p = {1, 2};
        byte[] text = {1, 2, 0, 1, 2, 0, 1, 2};
        // входження на позиції 6 не вміщується в [0, 7)
        assertEquals(List.of(3), find(algorithm, text, 2, 7, p));
    }

    @ParameterizedTest
    @MethodSource("algorithms")
    void patternLongerThanTextFindsNothing(ByteSearch algorithm) {
        assertEquals(List.of(), find(algorithm, new byte[]{1, 2}, 0, 2, new byte[]{1, 2, 3}));
    }

    @ParameterizedTest
    @MethodSource("algorithms")
    void handlesNegativeByteValues(ByteSearch algorithm) {
        byte[] p = {(byte) 0xFF, (byte) 0x80};
        byte[] text = {0, (byte) 0xFF, (byte) 0x80, (byte) 0xFF, (byte) 0xFF, (byte) 0x80};
        assertEquals(List.of(1, 4), find(algorithm, text, 0, text.length, p));
    }

    @ParameterizedTest
    @MethodSource("algorithms")
    void matchesReferenceOnRandomData(ByteSearch algorithm) {
        Random random = new Random(12345);
        for (int round = 0; round < 300; round++) {
            int alphabet = 1 + random.nextInt(4); // малий алфавіт — багато збігів і перекриттів
            byte[] text = new byte[random.nextInt(400)];
            for (int i = 0; i < text.length; i++) {
                text[i] = (byte) random.nextInt(alphabet);
            }
            byte[] pattern = new byte[1 + random.nextInt(6)];
            for (int i = 0; i < pattern.length; i++) {
                pattern[i] = (byte) random.nextInt(alphabet);
            }
            int from = text.length == 0 ? 0 : random.nextInt(text.length);
            int to = from + (text.length == from ? 0 : random.nextInt(text.length - from + 1));
            assertEquals(reference(text, from, to, pattern), find(algorithm, text, from, to, pattern),
                    () -> algorithm.name() + " помилився");
        }
    }
}
