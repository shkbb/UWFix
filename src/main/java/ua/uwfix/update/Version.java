package ua.uwfix.update;

/**
 * Номер версії у форматі «основна.додаткова.виправлення» (Semantic Versioning): 1.2.0.
 * Порівнюється по числах, тому 1.10.0 новіша за 1.9.9 (рядкове порівняння дало б навпаки).
 */
public record Version(int major, int minor, int patch) implements Comparable<Version> {

    /**
     * Розбирає «1.2.0», «v1.2.0», «1.2» (= 1.2.0) або «1.2.0-beta» (суфікс ігнорується).
     *
     * @return версія або {@code null}, якщо текст не схожий на номер версії
     */
    public static Version parse(String text) {
        if (text == null) {
            return null;
        }
        String s = text.strip();
        if (s.startsWith("v") || s.startsWith("V")) {
            s = s.substring(1);
        }
        int suffix = indexOfAny(s, '-', '+');
        if (suffix >= 0) {
            s = s.substring(0, suffix);
        }
        String[] parts = s.split("\\.");
        if (parts.length == 0 || parts.length > 3) {
            return null;
        }
        int[] numbers = new int[3];
        try {
            for (int i = 0; i < parts.length; i++) {
                numbers[i] = Integer.parseInt(parts[i]);
                if (numbers[i] < 0) {
                    return null;
                }
            }
        } catch (NumberFormatException e) {
            return null;
        }
        return new Version(numbers[0], numbers[1], numbers[2]);
    }

    public boolean isNewerThan(Version other) {
        return compareTo(other) > 0;
    }

    @Override
    public int compareTo(Version o) {
        if (major != o.major) {
            return Integer.compare(major, o.major);
        }
        if (minor != o.minor) {
            return Integer.compare(minor, o.minor);
        }
        return Integer.compare(patch, o.patch);
    }

    @Override
    public String toString() {
        return major + "." + minor + "." + patch;
    }

    private static int indexOfAny(String s, char a, char b) {
        int i = s.indexOf(a);
        int j = s.indexOf(b);
        if (i < 0) {
            return j;
        }
        return j < 0 ? i : Math.min(i, j);
    }
}
