package ua.uwfix.analysis;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Set;

/**
 * Формат виконуваного файлу. Його визначають не за розширенням, а за першими байтами —
 * «магічним числом», яке кожен формат записує на початку файлу:
 * <pre>
 *   PE   (Windows: .exe, .dll)            4D 5A        «MZ»
 *   ELF  (Linux: програми та бібліотеки .so) 7F 45 4C 46  «\x7fELF»
 * </pre>
 * У Linux програми зазвичай не мають розширення ({@code portal2_linux}) або мають довільне
 * ({@code valheim.x86_64}), тож без перевірки вмісту їх не відрізнити від файлів даних.
 * Самі числа 16:9 в обох форматах записані однаково — процесор той самий (x86-64, little-endian).
 */
public enum BinaryFormat {
    PE, ELF, OTHER;

    /** Розширення, з якими в Linux поширюються програми (Unity, Godot, GameMaker…). */
    private static final Set<String> ELF_PROGRAM_EXTENSIONS = Set.of("x86_64", "x86", "x64", "amd64", "bin", "elf");

    public static BinaryFormat of(Path file) {
        byte[] head = new byte[4];
        try (InputStream in = Files.newInputStream(file)) {
            return of(head, in.readNBytes(head, 0, head.length));
        } catch (IOException e) {
            return OTHER;
        }
    }

    static BinaryFormat of(byte[] head, int length) {
        if (length >= 4 && head[0] == 0x7F && head[1] == 'E' && head[2] == 'L' && head[3] == 'F') {
            return ELF;
        }
        if (length >= 2 && head[0] == 'M' && head[1] == 'Z') {
            return PE;
        }
        return OTHER;
    }

    /** Файли Windows (.exe, .dll) — їх видно за назвою. */
    public static boolean isWindowsName(String fileName) {
        String name = fileName.toLowerCase(Locale.ROOT);
        return name.endsWith(".exe") || name.endsWith(".dll");
    }

    /** Бібліотека Linux: «libfoo.so» або з версією — «libSDL2-2.0.so.0». */
    public static boolean isSharedObjectName(String fileName) {
        String name = fileName.toLowerCase(Locale.ROOT);
        return name.endsWith(".so") || name.matches(".+\\.so(\\.\\d+)+");
    }

    /** Бібліотека (.dll або .so), а не програма. */
    public static boolean isLibraryName(String fileName) {
        return fileName.toLowerCase(Locale.ROOT).endsWith(".dll") || isSharedObjectName(fileName);
    }

    /**
     * Чи може так називатися програма Linux: без розширення ({@code hl2_linux}, {@code Game-Linux-Shipping})
     * або з розширенням на кшталт {@code .x86_64}. Чи це справді ELF — перевіряє {@link #of(Path)}.
     */
    public static boolean mayBeElfProgramName(String fileName) {
        String name = fileName.toLowerCase(Locale.ROOT);
        int dot = name.lastIndexOf('.');
        if (dot <= 0) {
            return true;
        }
        return ELF_PROGRAM_EXTENSIONS.contains(name.substring(dot + 1));
    }
}
