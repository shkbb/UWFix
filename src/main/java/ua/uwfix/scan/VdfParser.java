package ua.uwfix.scan;

/**
 * Парсер текстового формату Valve KeyValues (VDF), у якому Steam зберігає
 * список бібліотек ({@code libraryfolders.vdf}) і дані про ігри ({@code appmanifest_*.acf}).
 * <p>
 * Граматика (метод рекурсивного спуску):
 * <pre>
 *   object  := { pair }
 *   pair    := token ( token | "{" object "}" ) [ condition ]
 *   token   := "рядок у лапках" | слово_без_пробілів
 *   condition := "[" ... "]"          (наприклад [$WIN32], ігнорується)
 * </pre>
 * Коментарі починаються з «//» і тривають до кінця рядка.
 */
public final class VdfParser {

    private final String text;
    private int pos;

    private VdfParser(String text) {
        this.text = text;
    }

    /** Розбирає текст VDF і повертає кореневий об'єкт. */
    public static VdfObject parse(String text) {
        VdfParser parser = new VdfParser(text);
        return parser.parseObject(true);
    }

    private VdfObject parseObject(boolean topLevel) {
        VdfObject object = new VdfObject();
        while (true) {
            skipWhitespaceAndComments();
            if (pos >= text.length()) {
                if (topLevel) {
                    return object;
                }
                throw error("файл закінчився, а '}' не знайдено");
            }
            char c = text.charAt(pos);
            if (c == '}') {
                if (topLevel) {
                    throw error("зайва '}'");
                }
                pos++;
                return object;
            }

            String key = readToken();
            skipWhitespaceAndComments();
            if (pos < text.length() && text.charAt(pos) == '{') {
                pos++;
                object.put(key, parseObject(false));
            } else {
                object.put(key, readToken());
            }
            skipCondition();
        }
    }

    /** Читає рядок у лапках (з екрануванням) або слово без лапок. */
    private String readToken() {
        if (pos >= text.length()) {
            throw error("очікувався ключ або значення");
        }
        char c = text.charAt(pos);
        if (c == '{' || c == '}') {
            throw error("неочікуваний символ '" + c + "'");
        }
        StringBuilder sb = new StringBuilder();
        if (c == '"') {
            pos++;
            while (true) {
                if (pos >= text.length()) {
                    throw error("рядок не закрито лапками");
                }
                char ch = text.charAt(pos++);
                if (ch == '"') {
                    break;
                }
                if (ch == '\\' && pos < text.length()) {
                    char next = text.charAt(pos++);
                    switch (next) {
                        case 'n' -> sb.append('\n');
                        case 't' -> sb.append('\t');
                        case '\\' -> sb.append('\\');
                        case '"' -> sb.append('"');
                        default -> sb.append('\\').append(next);
                    }
                } else {
                    sb.append(ch);
                }
            }
        } else {
            while (pos < text.length()) {
                char ch = text.charAt(pos);
                if (Character.isWhitespace(ch) || ch == '{' || ch == '}' || ch == '"') {
                    break;
                }
                sb.append(ch);
                pos++;
            }
        }
        return sb.toString();
    }

    /** Пропускає необов'язкову умову платформи на кшталт [$WIN32]. */
    private void skipCondition() {
        int save = pos;
        while (save < text.length() && (text.charAt(save) == ' ' || text.charAt(save) == '\t')) {
            save++;
        }
        if (save < text.length() && text.charAt(save) == '[') {
            int close = text.indexOf(']', save);
            pos = close < 0 ? text.length() : close + 1;
        }
    }

    private void skipWhitespaceAndComments() {
        while (pos < text.length()) {
            char c = text.charAt(pos);
            if (Character.isWhitespace(c) || c == '﻿') {
                pos++;
            } else if (c == '/' && pos + 1 < text.length() && text.charAt(pos + 1) == '/') {
                while (pos < text.length() && text.charAt(pos) != '\n') {
                    pos++;
                }
            } else {
                return;
            }
        }
    }

    private IllegalArgumentException error(String message) {
        int line = 1;
        for (int i = 0; i < Math.min(pos, text.length()); i++) {
            if (text.charAt(i) == '\n') {
                line++;
            }
        }
        return new IllegalArgumentException("Помилка VDF у рядку " + line + ": " + message);
    }
}
