package ua.uwfix.settings;

import ua.uwfix.model.AspectRatio;

import java.nio.file.Path;

/**
 * Де гра зберігає свою роздільну здатність.
 *
 * @param kind        тип налаштувань
 * @param file        файл налаштувань (ini, video.txt, prefs) або {@code null}
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
        UNITY_REGISTRY,
        /** Unity у Linux: XML-файл ~/.config/unity3d/&lt;компанія&gt;/&lt;гра&gt;/prefs. */
        UNITY_PREFS_XML,
        /** Source (Portal 2, Left 4 Dead 2): cfg\video.txt, ключі setting.defaultres / setting.defaultresheight. */
        SOURCE_VIDEO_TXT,
        /** Source (Half-Life 2 та старіші): реєстр HKCU\Software\Valve\Source\<мод>\Settings. */
        SOURCE_REGISTRY,
        /** Creation Engine / Gamebryo (Skyrim, Fallout): *Prefs.ini, секція [Display], iSize W / iSize H. */
        CREATION_INI
    }

    /** Коротко для показу: ім'я файлу або розділ реєстру. */
    public String location() {
        if (kind == Kind.UNITY_PREFS_XML && file.getParent() != null && file.getParent().getParent() != null) {
            // файл завжди зветься «prefs» — показуємо ще компанію й гру
            Path product = file.getParent();
            return product.getParent().getFileName() + "/" + product.getFileName() + "/" + file.getFileName();
        }
        return file != null ? file.getFileName().toString() : registryKey;
    }
}
