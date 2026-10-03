package ua.uwfix.patch;

/** Помилка, про яку треба зрозуміло повідомити користувача. */
public final class PatchException extends Exception {

    /** Тип помилки визначає, що запропонувати користувачу. */
    public enum Kind {
        /** Немає прав на запис — треба перезапустити програму від імені адміністратора. */
        NEED_ADMIN,
        /** Файл зайнятий — найімовірніше, гра запущена. */
        FILE_IN_USE,
        /** Змінювати нічого (наприклад, обрано 16:9). */
        NOTHING_TO_DO,
        /** Файл не змінювався програмою. */
        NOT_PATCHED,
        /** Вміст файлу не такий, як очікувалось. */
        INTEGRITY
    }

    private final Kind kind;

    public PatchException(Kind kind, String message) {
        super(message);
        this.kind = kind;
    }

    public PatchException(Kind kind, String message, Throwable cause) {
        super(message, cause);
        this.kind = kind;
    }

    public Kind kind() {
        return kind;
    }
}
