package ua.uwfix.update;

/** Помилка оновлення з повідомленням для користувача. */
public final class UpdateException extends Exception {

    public UpdateException(String message) {
        super(message);
    }

    public UpdateException(String message, Throwable cause) {
        super(message, cause);
    }
}
