package ua.uwfix.model;

import ua.uwfix.i18n.I18n;

/** Звідки програма дізналася про гру. */
public enum GameSource {
    STEAM("Steam"),
    EPIC("Epic Games"),
    GOG("GOG"),
    UBISOFT("Ubisoft Connect"),
    /** Назва перекладається (ключ source.manual), решта — торгові марки. */
    MANUAL(null);

    private final String brand;

    GameSource(String brand) {
        this.brand = brand;
    }

    public String displayName() {
        return brand != null ? brand : I18n.t("source.manual");
    }
}
