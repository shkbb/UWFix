package ua.uwfix.analysis;

import ua.uwfix.model.Game;

import java.util.List;
import java.util.Optional;

/**
 * Результат аналізу папки гри.
 *
 * @param game         гра
 * @param engine       визначений рушій
 * @param antiCheat    назва античиту, якщо знайдено
 * @param candidates   файли, у яких знайдено 16:9 (рекомендовані — першими)
 * @param scannedFiles скільки файлів переглянуто
 */
public record GameAnalysis(Game game, Engine engine, Optional<String> antiCheat,
                           List<BinaryCandidate> candidates, int scannedFiles) {

    public List<BinaryCandidate> recommended() {
        return candidates.stream().filter(BinaryCandidate::recommended).toList();
    }
}
