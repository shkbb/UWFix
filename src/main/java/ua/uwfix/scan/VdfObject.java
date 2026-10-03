package ua.uwfix.scan;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Вузол формату Valve KeyValues (VDF): впорядкований набір пар «ключ — значення»,
 * де значення — рядок або вкладений {@link VdfObject}. Ключі нечутливі до регістру,
 * як і в самому Steam.
 */
public final class VdfObject {

    private final Map<String, Object> entries = new LinkedHashMap<>();

    void put(String key, Object value) {
        entries.put(key, value);
    }

    /** Рядкове значення за ключем або {@code null}. */
    public String getString(String key) {
        Object value = find(key);
        return value instanceof String s ? s : null;
    }

    /** Вкладений об'єкт за ключем або {@code null}. */
    public VdfObject getObject(String key) {
        Object value = find(key);
        return value instanceof VdfObject o ? o : null;
    }

    /** Усі пари у порядку появи у файлі. */
    public Map<String, Object> entries() {
        return entries;
    }

    private Object find(String key) {
        Object exact = entries.get(key);
        if (exact != null) {
            return exact;
        }
        for (Map.Entry<String, Object> e : entries.entrySet()) {
            if (e.getKey().equalsIgnoreCase(key)) {
                return e.getValue();
            }
        }
        return null;
    }
}
