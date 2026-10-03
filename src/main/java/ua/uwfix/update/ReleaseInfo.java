package ua.uwfix.update;

/**
 * Опублікований реліз програми на GitHub.
 *
 * @param version   версія релізу
 * @param pageUrl   сторінка релізу (для кнопки «Що нового»)
 * @param zipName   назва архіву портативної версії
 * @param zipUrl    адреса завантаження архіву
 * @param zipSize   розмір архіву в байтах
 * @param zipSha256 SHA-256 архіву, якщо GitHub його повідомив, інакше {@code null}
 */
public record ReleaseInfo(Version version, String pageUrl, String zipName, String zipUrl,
                          long zipSize, String zipSha256) {
}
