package ua.uwfix.i18n;

import java.util.Locale;

/** Мова інтерфейсу. */
public enum Language {

    UK("uk", "Українська"),
    EN("en", "English");

    private final String code;
    private final String nativeName;

    Language(String code, String nativeName) {
        this.code = code;
        this.nativeName = nativeName;
    }

    /** Код мови ISO 639-1: «uk», «en». */
    public String code() {
        return code;
    }

    /** Назва мови нею самою — так її легше знайти у списку. */
    public String nativeName() {
        return nativeName;
    }

    public Locale locale() {
        return Locale.forLanguageTag(code);
    }

    /** Мова за кодом або {@code null}, якщо такої немає. */
    public static Language fromCode(String code) {
        if (code != null) {
            for (Language l : values()) {
                if (l.code.equalsIgnoreCase(code)) {
                    return l;
                }
            }
        }
        return null;
    }

    /**
     * Мова для запуску: збережена користувачем, інакше — мова системи
     * (українська для української Windows, англійська для решти).
     */
    public static Language detect(String saved) {
        Language chosen = fromCode(saved);
        if (chosen != null) {
            return chosen;
        }
        return "uk".equals(Locale.getDefault().getLanguage()) ? UK : EN;
    }

    @Override
    public String toString() {
        return nativeName;
    }
}
