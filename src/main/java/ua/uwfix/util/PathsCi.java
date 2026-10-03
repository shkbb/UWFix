package ua.uwfix.util;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Шляхи без урахування регістру літер.
 * <p>
 * Windows не розрізняє «Bin64» і «bin64», а Linux — розрізняє. Ігри для Windows
 * (зокрема ті, що на Linux працюють через Proton) можуть називати папки як завгодно,
 * тому програма шукає файли так, ніби регістр не має значення.
 */
public final class PathsCi {

    private PathsCi() {
    }

    /**
     * Шлях {@code base/relative} з уточненим регістром кожної частини. Якщо частини немає,
     * повертається шлях як є (тоді {@code Files.exists} просто дасть false).
     *
     * @param relative частини через «/», наприклад {@code "game/bin/win64/engine2.dll"}
     */
    public static Path resolve(Path base, String relative) {
        Path current = base;
        for (String part : relative.split("/")) {
            if (part.isEmpty()) {
                continue;
            }
            Path exact = current.resolve(part);
            if (Files.exists(exact)) {
                current = exact;
                continue;
            }
            Path match = findIgnoringCase(current, part);
            current = match != null ? match : exact;
        }
        return current;
    }

    public static boolean isFile(Path base, String relative) {
        return Files.isRegularFile(resolve(base, relative));
    }

    public static boolean isDirectory(Path base, String relative) {
        return Files.isDirectory(resolve(base, relative));
    }

    private static Path findIgnoringCase(Path dir, String name) {
        if (!Files.isDirectory(dir)) {
            return null;
        }
        try (DirectoryStream<Path> children = Files.newDirectoryStream(dir)) {
            for (Path child : children) {
                if (child.getFileName().toString().equalsIgnoreCase(name)) {
                    return child;
                }
            }
        } catch (IOException e) {
            return null;
        }
        return null;
    }
}
