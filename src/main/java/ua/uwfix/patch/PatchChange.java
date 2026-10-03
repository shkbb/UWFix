package ua.uwfix.patch;

/**
 * Одна заміна у файлі.
 *
 * @param offset      зміщення від початку файлу
 * @param original    байти до заміни (hex)
 * @param replacement байти після заміни (hex)
 */
public record PatchChange(long offset, String original, String replacement) {
}
