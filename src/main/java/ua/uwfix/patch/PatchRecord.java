package ua.uwfix.patch;

import ua.uwfix.model.AspectRatio;

import java.util.List;

/**
 * Запис про пропатчений файл. Зберігається у файлі стану, щоб:
 * <ul>
 *   <li>відновити оригінал навіть без резервної копії (знаємо, що і де змінено);</li>
 *   <li>помітити, що гра оновилась і фікс злетів (контрольна сума не та);</li>
 *   <li>одною кнопкою застосувати фікс знову з тими самими налаштуваннями.</li>
 * </ul>
 *
 * @param gameId          ключ гри ({@link ua.uwfix.model.Game#id()})
 * @param gameName        назва гри
 * @param file            шлях до файлу
 * @param backupFile      шлях до резервної копії оригіналу
 * @param ratioWidth      ширина цільової роздільної здатності
 * @param ratioHeight     висота цільової роздільної здатності
 * @param formats         формати чисел, які замінювались (назви {@link ua.uwfix.model.ValueFormat})
 * @param originalSha256  контрольна сума оригіналу
 * @param patchedSha256   контрольна сума після заміни
 * @param patchedSize     розмір файлу після заміни
 * @param patchedModified час зміни файлу після заміни (мс) — для швидкої перевірки без хешування
 * @param patchedAt       дата й час заміни (ISO-8601)
 * @param changes         список замін
 */
public record PatchRecord(String gameId, String gameName, String file, String backupFile,
                          int ratioWidth, int ratioHeight, List<String> formats,
                          String originalSha256, String patchedSha256,
                          long patchedSize, long patchedModified, String patchedAt,
                          List<PatchChange> changes) {

    public AspectRatio ratio() {
        return new AspectRatio(ratioWidth, ratioHeight);
    }
}
