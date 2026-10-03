package ua.uwfix.patch;

/** Гра, яку користувач додав вручну (лаунчер невідомий або гра піратська/портативна). */
public record ManualGameEntry(String name, String path) {
}
