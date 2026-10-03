package ua.uwfix.model;

import java.nio.file.Path;
import java.util.Locale;

/**
 * Встановлена гра.
 *
 * @param source     лаунчер, у якому знайдено гру
 * @param sourceId   ідентифікатор гри всередині лаунчера (наприклад Steam AppID)
 * @param name       назва для відображення
 * @param installDir папка, куди встановлено гру
 */
public record Game(GameSource source, String sourceId, String name, Path installDir) {

    /** Унікальний ключ гри, наприклад «steam:292030». */
    public String id() {
        return source.name().toLowerCase(Locale.ROOT) + ":" + sourceId;
    }

    @Override
    public String toString() {
        return name;
    }
}
