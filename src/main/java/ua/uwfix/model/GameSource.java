package ua.uwfix.model;

/** Звідки програма дізналася про гру. */
public enum GameSource {
    STEAM("Steam"),
    EPIC("Epic Games"),
    GOG("GOG"),
    UBISOFT("Ubisoft Connect"),
    MANUAL("Додано вручну");

    private final String displayName;

    GameSource(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }
}
