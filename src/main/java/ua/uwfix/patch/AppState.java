package ua.uwfix.patch;

import java.util.ArrayList;
import java.util.List;

/** Усе, що програма пам'ятає між запусками. Серіалізується в JSON бібліотекою Gson. */
public final class AppState {

    /** Версія формату файлу — на випадок майбутніх змін. */
    int version = 1;

    /** Обране користувачем співвідношення (null — брати з монітора). */
    Integer targetWidth;
    Integer targetHeight;

    /** Чи замінювати також 16:9 у форматі double. */
    boolean includeDouble;

    /** Мова інтерфейсу: «uk», «en» або null — як у системі. */
    String language;

    /** Фільтр і сортування списку ігор (назви елементів переліку). */
    String listFilter;
    String listSort;

    List<ManualGameEntry> manualGames = new ArrayList<>();
    List<PatchRecord> patches = new ArrayList<>();

    public Integer targetWidth() {
        return targetWidth;
    }

    public Integer targetHeight() {
        return targetHeight;
    }

    public boolean includeDouble() {
        return includeDouble;
    }

    public String language() {
        return language;
    }

    public String listFilter() {
        return listFilter;
    }

    public String listSort() {
        return listSort;
    }

    public List<ManualGameEntry> manualGames() {
        return manualGames;
    }

    public List<PatchRecord> patches() {
        return patches;
    }

    /** Після десеріалізації списки можуть бути null, якщо їх немає у файлі. */
    void fixNulls() {
        if (manualGames == null) {
            manualGames = new ArrayList<>();
        }
        if (patches == null) {
            patches = new ArrayList<>();
        }
    }
}
