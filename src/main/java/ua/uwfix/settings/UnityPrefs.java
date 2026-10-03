package ua.uwfix.settings;

import java.nio.charset.StandardCharsets;

/**
 * Налаштування Unity-ігор (PlayerPrefs) у реєстрі: {@code HKCU\Software\<компанія>\<гра>}.
 * <p>
 * Unity дописує до імені кожного значення хеш: «Screenmanager Resolution Width_h182942802».
 * Хеш — варіант djb2 з XOR: h = h·33 ⊕ байт, початкове значення 5381, 32 біти без знака.
 * Обчислюючи його, програма не залежить від «магічних» чисел.
 */
public final class UnityPrefs {

    public static final String WIDTH = "Screenmanager Resolution Width";
    public static final String HEIGHT = "Screenmanager Resolution Height";
    public static final String USE_NATIVE = "Screenmanager Resolution Use Native";

    private UnityPrefs() {
    }

    /** Повне ім'я значення в реєстрі: «назва_hХЕШ». */
    public static String valueName(String key) {
        return key + "_h" + hash(key);
    }

    /** djb2-xor по байтах UTF-8. */
    static long hash(String key) {
        long h = 5381;
        for (byte b : key.getBytes(StandardCharsets.UTF_8)) {
            h = ((h * 33) ^ (b & 0xFF)) & 0xFFFFFFFFL;
        }
        return h;
    }
}
