package ua.uwfix.analysis;

import ua.uwfix.i18n.I18n;

import java.util.Locale;

/**
 * Ігровий рушій. Від нього залежить, у якому файлі шукати 16:9 і чи спрацює метод.
 * Назва й порада користувачу беруться зі словника: {@code engine.<рушій>.name} / {@code .hint}.
 */
public enum Engine {

    UNREAL_4_5,
    UNREAL_3,
    UNITY_IL2CPP,
    UNITY_MONO,
    RED_ENGINE,
    UNKNOWN;

    public String displayName() {
        return I18n.t(keyPrefix() + ".name");
    }

    /** Порада користувачу щодо цього рушія. */
    public String hint() {
        return I18n.t(keyPrefix() + ".hint");
    }

    /** Наприклад «engine.unreal_4_5». */
    String keyPrefix() {
        return "engine." + name().toLowerCase(Locale.ROOT);
    }
}
