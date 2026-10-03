package ua.uwfix.settings;

import ua.uwfix.scan.WindowsRegistry;

import java.util.Map;

/**
 * Доступ до реєстру, де ігри зберігають налаштування. У Windows це справжній реєстр,
 * у Linux — файл {@code user.reg} префікса Wine/Proton, у якому працює гра.
 */
public interface RegistryAccess {

    /** Значення розділу (числа — у десятковому вигляді); порожня мапа, якщо розділу немає. */
    Map<String, String> readValues(String key);

    /** Записує 32-бітне число (REG_DWORD). */
    boolean setDword(String key, String name, long value);

    /** Реєстру немає (нативні ігри Linux): нічого не читається і не записується. */
    RegistryAccess NONE = new RegistryAccess() {
        @Override
        public Map<String, String> readValues(String key) {
            return Map.of();
        }

        @Override
        public boolean setDword(String key, String name, long value) {
            return false;
        }
    };

    /** Реєстр Windows. */
    RegistryAccess WINDOWS = new RegistryAccess() {
        @Override
        public Map<String, String> readValues(String key) {
            return WindowsRegistry.readValues(key);
        }

        @Override
        public boolean setDword(String key, String name, long value) {
            return WindowsRegistry.setDword(key, name, value);
        }
    };
}
