package ua.uwfix.analysis;

/** Ігровий рушій. Від нього залежить, у якому файлі шукати 16:9 і чи спрацює метод. */
public enum Engine {

    UNREAL_4_5("Unreal Engine 4/5",
            "Катсцени з обмеженням співвідношення сторін зазвичай використовують 16:9 з головного .exe "
                    + "(*-Win64-Shipping.exe). Метод часто спрацьовує."),
    UNREAL_3("Unreal Engine 3",
            "Ігри на UE3 зберігають 16:9 у головному .exe — заміна зазвичай прибирає смуги."),
    UNITY_IL2CPP("Unity (IL2CPP)",
            "Смуги в Unity-іграх додає код самої гри — він скомпільований у GameAssembly.dll."),
    UNITY_MONO("Unity (Mono)",
            "Смуги в Unity-іграх додає код самої гри — він лежить в Assembly-CSharp.dll."),
    RED_ENGINE("REDengine",
            "Для The Witcher 3 (next-gen) спільнота перевірила саме цей метод — заміну 16:9 у witcher3.exe."),
    UNKNOWN("Не визначено",
            "Рушій не вдалося визначити. Спробувати можна: перед зміною створюється резервна копія.");

    private final String displayName;
    private final String hint;

    Engine(String displayName, String hint) {
        this.displayName = displayName;
        this.hint = hint;
    }

    public String displayName() {
        return displayName;
    }

    /** Порада користувачу щодо цього рушія. */
    public String hint() {
        return hint;
    }
}
