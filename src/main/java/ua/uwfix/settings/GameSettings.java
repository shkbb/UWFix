package ua.uwfix.settings;

import ua.uwfix.model.AspectRatio;

import java.nio.file.Path;

/**
 * Де гра зберігає свою роздільну здатність.
 *
 * @param kind        тип налаштувань
 * @param file        ini-файл (для Unreal Engine) або {@code null}
 * @param registryKey розділ реєстру (для Unity) або {@code null}
 * @param current     роздільна здатність, що зараз записана, або {@code null}, якщо невідомо
 */
public record GameSettings(Kind kind, Path file, String registryKey, AspectRatio current) {

    public enum Kind {
        /** Unreal Engine 4/5: GameUserSettings.ini, ключі ResolutionSizeX/Y. */
        UNREAL_INI,
        /** Unreal Engine 3: *Engine.ini, секція [SystemSettings], ключі ResX/ResY. */
        UNREAL3_INI,
        /** Unity: реєстр, значення «Screenmanager Resolution Width/Height». */
        UNITY_REGISTRY
    }

    /** Коротко для показу: ім'я файлу або розділ реєстру. */
    public String location() {
        return file != null ? file.getFileName().toString() : registryKey;
    }
}
