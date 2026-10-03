/**
 * Модуль програми UWFix.
 * <p>
 * Програма знаходить встановлені ігри, шукає у їхніх виконуваних файлах
 * число 16:9 (1.7777778) і замінює його на співвідношення сторін монітора,
 * щоб прибрати чорні смуги в катсценах.
 */
module uwfix {
    requires javafx.controls;
    requires javafx.fxml;
    requires com.google.gson;
    // HTTP-клієнт для перевірки оновлень на GitHub
    requires java.net.http;

    // Клас App запускає JavaFX через рефлексію
    exports ua.uwfix;

    // FXMLLoader створює контролер і заповнює @FXML-поля через рефлексію
    opens ua.uwfix.ui to javafx.fxml;

    // Gson читає/пише поля класів стану через рефлексію
    opens ua.uwfix.patch to com.google.gson;
}
