package ua.uwfix.update;

import ua.uwfix.i18n.I18n;

import java.io.BufferedInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.EnumSet;
import java.util.Set;
import java.util.zip.GZIPInputStream;

/**
 * Розпакування архіву .tar.gz (у ньому виходить версія для Linux: на відміну від zip,
 * tar зберігає права «виконуваний файл» для bin/UWFix і бібліотек Java).
 * <p>
 * Формат tar — послідовність блоків по 512 байт: заголовок файлу (назва, права, розмір
 * у вісімковому вигляді, тип), потім вміст, доповнений нулями до кратного 512. Кінець архіву —
 * порожній блок. Підтримуються розширення для довгих назв: поле prefix (ustar),
 * запис «././@LongLink» (GNU tar) і заголовки pax.
 */
final class TarArchive {

    private static final int BLOCK = 512;

    private TarArchive() {
    }

    /** Розпаковує архів, не дозволяючи файлам вийти за межі цільової папки. */
    static void extract(Path tarGz, Path target) throws IOException, UpdateException {
        Path root = target.toAbsolutePath().normalize();
        Files.createDirectories(root);
        try (InputStream in = new BufferedInputStream(new GZIPInputStream(Files.newInputStream(tarGz)))) {
            byte[] header = new byte[BLOCK];
            String longName = null;
            while (true) {
                if (in.readNBytes(header, 0, BLOCK) < BLOCK) {
                    throw new EOFException("tar");
                }
                if (isZero(header)) {
                    return; // кінець архіву
                }
                char type = (char) header[156];
                long size = size(header);
                String name = longName != null ? longName : name(header);
                longName = null;

                switch (type) {
                    case 'L' -> longName = cString(readData(in, size), 0, (int) size); // GNU: назва наступного файлу
                    case 'x' -> longName = paxPath(readData(in, size));                   // pax: те саме
                    case 'g', 'K' -> readData(in, size);                                   // глобальні pax, довгі посилання
                    case '5' -> Files.createDirectories(safe(root, name));
                    case '0', '\0', '7' -> {
                        Path out = safe(root, name);
                        Files.createDirectories(out.getParent());
                        try (OutputStream os = Files.newOutputStream(out)) {
                            copy(in, os, size);
                        }
                        skipPadding(in, size);
                        setMode(out, mode(header));
                    }
                    case '2' -> {
                        // символьне посилання — лише відносне і всередині архіву
                        Path out = safe(root, name);
                        String link = cString(header, 157, 100);
                        Path resolved = out.getParent().resolve(link).normalize();
                        if (Path.of(link).isAbsolute() || !resolved.startsWith(root)) {
                            throw new UpdateException(I18n.t("error.update.badArchive"));
                        }
                        Files.createDirectories(out.getParent());
                        Files.deleteIfExists(out);
                        Files.createSymbolicLink(out, Path.of(link));
                    }
                    default -> {
                        readData(in, size); // жорсткі посилання, пристрої тощо в архіві програми не потрібні
                    }
                }
            }
        }
    }

    // ------------------------------------------------------------------ заголовок

    /** Назва: «prefix/name» (ustar) або просто name. */
    static String name(byte[] header) {
        String name = cString(header, 0, 100);
        boolean ustar = cString(header, 257, 5).equals("ustar");
        String prefix = ustar ? cString(header, 345, 155) : "";
        return prefix.isEmpty() ? name : prefix + "/" + name;
    }

    /** Розмір: вісімкове число в ASCII, або двійкове (base-256), якщо старший біт першого байта встановлено. */
    static long size(byte[] header) throws IOException {
        if ((header[124] & 0x80) != 0) {
            long value = 0;
            for (int i = 125; i < 136; i++) {
                value = (value << 8) | (header[i] & 0xFF);
            }
            return value;
        }
        return octal(header, 124, 12);
    }

    static int mode(byte[] header) throws IOException {
        return (int) octal(header, 100, 8);
    }

    private static long octal(byte[] b, int offset, int length) throws IOException {
        String text = cString(b, offset, length).strip();
        if (text.isEmpty()) {
            return 0;
        }
        try {
            return Long.parseLong(text, 8);
        } catch (NumberFormatException e) {
            throw new IOException("tar: bad number " + text);
        }
    }

    /** Текст до першого нульового байта. */
    private static String cString(byte[] b, int offset, int length) {
        int end = offset;
        while (end < offset + length && end < b.length && b[end] != 0) {
            end++;
        }
        return new String(b, offset, end - offset, StandardCharsets.UTF_8);
    }

    /** Записи pax мають вигляд «довжина ключ=значення\n»; потрібен лише path. */
    static String paxPath(byte[] data) {
        String text = new String(data, StandardCharsets.UTF_8);
        int pos = 0;
        while (pos < text.length()) {
            int space = text.indexOf(' ', pos);
            if (space < 0) {
                break;
            }
            int length;
            try {
                length = Integer.parseInt(text.substring(pos, space));
            } catch (NumberFormatException e) {
                break;
            }
            // довжина рахується в байтах, але назви файлів програми — латиницею
            int end = Math.min(text.length(), pos + length);
            String record = text.substring(space + 1, end).stripTrailing();
            if (record.startsWith("path=")) {
                return record.substring(5);
            }
            if (length <= 0) {
                break;
            }
            pos = end;
        }
        return null;
    }

    // ------------------------------------------------------------------ вміст

    private static byte[] readData(InputStream in, long size) throws IOException {
        if (size > 16 * 1024 * 1024) {
            throw new IOException("tar: header too large");
        }
        byte[] data = in.readNBytes((int) size);
        if (data.length < size) {
            throw new EOFException("tar");
        }
        skipPadding(in, size);
        return data;
    }

    private static void copy(InputStream in, OutputStream out, long size) throws IOException {
        byte[] buffer = new byte[64 * 1024];
        long left = size;
        while (left > 0) {
            int n = in.read(buffer, 0, (int) Math.min(buffer.length, left));
            if (n < 0) {
                throw new EOFException("tar");
            }
            out.write(buffer, 0, n);
            left -= n;
        }
    }

    private static void skipPadding(InputStream in, long size) throws IOException {
        in.skipNBytes((BLOCK - size % BLOCK) % BLOCK);
    }

    private static boolean isZero(byte[] block) {
        for (byte b : block) {
            if (b != 0) {
                return false;
            }
        }
        return true;
    }

    private static Path safe(Path root, String name) throws UpdateException {
        Path out = root.resolve(name).normalize();
        if (!out.startsWith(root) || out.equals(root)) {
            throw new UpdateException(I18n.t("error.update.badArchive"));
        }
        return out;
    }

    /** Права файлу (rwx для власника, групи, інших); у Windows їх немає — пропускаємо. */
    private static void setMode(Path file, int mode) throws IOException {
        Set<PosixFilePermission> perms = EnumSet.noneOf(PosixFilePermission.class);
        PosixFilePermission[] order = {
                PosixFilePermission.OTHERS_EXECUTE, PosixFilePermission.OTHERS_WRITE, PosixFilePermission.OTHERS_READ,
                PosixFilePermission.GROUP_EXECUTE, PosixFilePermission.GROUP_WRITE, PosixFilePermission.GROUP_READ,
                PosixFilePermission.OWNER_EXECUTE, PosixFilePermission.OWNER_WRITE, PosixFilePermission.OWNER_READ};
        for (int bit = 0; bit < order.length; bit++) {
            if ((mode & (1 << bit)) != 0) {
                perms.add(order[bit]);
            }
        }
        perms.add(PosixFilePermission.OWNER_READ);
        perms.add(PosixFilePermission.OWNER_WRITE);
        try {
            Files.setPosixFilePermissions(file, perms);
        } catch (UnsupportedOperationException e) {
            // файлова система без прав POSIX (Windows)
        }
    }
}
