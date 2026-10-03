package ua.uwfix.search;

import java.util.List;

/**
 * Результат сканування файлу.
 *
 * @param size    розмір файлу в байтах
 * @param sha256  контрольна сума SHA-256 вмісту (hex)
 * @param offsets для кожного шаблону — зміщення знайдених входжень у файлі
 */
public record ScanResult(long size, String sha256, List<List<Long>> offsets) {

    /** Кількість входжень шаблону з індексом {@code patternIndex}. */
    public int count(int patternIndex) {
        return offsets.get(patternIndex).size();
    }

    /** Загальна кількість входжень усіх шаблонів. */
    public int totalMatches() {
        return offsets.stream().mapToInt(List::size).sum();
    }
}
