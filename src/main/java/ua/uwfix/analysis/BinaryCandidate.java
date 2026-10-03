package ua.uwfix.analysis;

import ua.uwfix.model.ValueFormat;

import java.nio.file.Path;
import java.util.Map;

/**
 * Виконуваний файл гри (.exe, .dll або файл ELF у Linux), у якому може бути число 16:9.
 *
 * @param file        повний шлях
 * @param relative    шлях відносно папки гри — для показу
 * @param size        розмір у байтах
 * @param sha256      контрольна сума на момент аналізу
 * @param matches     скільки разів знайдено 16:9 у кожному форматі
 * @param recommended чи радить програма патчити саме цей файл
 */
public record BinaryCandidate(Path file, Path relative, long size, String sha256,
                              Map<ValueFormat, Integer> matches, boolean recommended) {

    public int matches(ValueFormat format) {
        return matches.getOrDefault(format, 0);
    }

    public int totalMatches() {
        return matches.values().stream().mapToInt(Integer::intValue).sum();
    }

    public String fileName() {
        return file.getFileName().toString();
    }

    /** Програма (.exe або ELF), а не бібліотека (.dll, .so). */
    public boolean isProgram() {
        return !BinaryFormat.isLibraryName(fileName());
    }

    BinaryCandidate withRecommended(boolean value) {
        return new BinaryCandidate(file, relative, size, sha256, matches, value);
    }
}
