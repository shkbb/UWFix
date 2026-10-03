package ua.uwfix.icon;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PeIconExtractorTest {

    @TempDir
    Path dir;

    /** Одне зображення іконки для синтетичного .exe: розмір, біти на піксель, вміст. */
    private record Icon(int size, int bitCount, byte[] data) {
    }

    @Test
    void picksSmallestPngNotLessThanDesiredSize() throws IOException {
        byte[] png32 = fakePng(32);
        byte[] png256 = fakePng(256);
        byte[] png64 = fakePng(64);
        Path exe = writePe(List.of(new Icon(32, 32, png32), new Icon(256, 32, png256), new Icon(64, 32, png64)));

        IconImage icon = PeIconExtractor.extract(exe, 48).orElseThrow();
        assertTrue(icon.isPng());
        assertArrayEquals(png64, icon.png(), "для 48 px найкраще підходить 64, а не 256");

        assertArrayEquals(png256, PeIconExtractor.extract(exe, 200).orElseThrow().png());
        assertArrayEquals(png256, PeIconExtractor.extract(exe, 512).orElseThrow().png(), "більшого немає — беремо найбільший");
    }

    @Test
    void decodes32BitBitmapWithAlphaBottomUp() throws IOException {
        // 2×2: верхній рядок червоний і зелений, нижній синій і напівпрозорий білий
        int[] topDown = {0xFFFF0000, 0xFF00FF00, 0xFF0000FF, 0x80FFFFFF};
        Path exe = writePe(List.of(new Icon(2, 32, dib32(2, 2, topDown))));

        IconImage icon = PeIconExtractor.extract(exe, 48).orElseThrow();
        assertFalse(icon.isPng());
        assertEquals(2, icon.width());
        assertEquals(2, icon.height());
        assertArrayEquals(topDown, icon.argb());
    }

    @Test
    void decodes4BitPaletteBitmapWithTransparencyMask() {
        // палітра: 0 — чорний, 1 — червоний; маска робить правий верхній піксель прозорим
        byte[] dib = dib4WithMask();
        IconImage icon = PeIconExtractor.decodeDib(dib);
        assertArrayEquals(new int[]{0xFFFF0000, 0x00000000, 0xFF000000, 0xFFFF0000}, icon.argb());
    }

    @Test
    void notAnExecutableGivesNothing() throws IOException {
        Path file = dir.resolve("readme.exe");
        Files.writeString(file, "це не програма");
        assertEquals(Optional.empty(), PeIconExtractor.extract(file, 32));
        assertEquals(Optional.empty(), PeIconExtractor.extract(dir.resolve("missing.exe"), 32));
    }

    @Test
    void truncatedResourceGivesNothingInsteadOfCrashing() throws IOException {
        Path exe = writePe(List.of(new Icon(32, 32, fakePng(32))));
        byte[] bytes = Files.readAllBytes(exe);
        Files.write(exe, java.util.Arrays.copyOf(bytes, bytes.length - 40));
        assertEquals(Optional.empty(), PeIconExtractor.extract(exe, 32));
    }

    @Test
    void realJavaLauncherDoesNotBreakTheParser() {
        // Справжній PE-файл з цієї JDK: іконки може не бути, але розбір не має падати
        Path java = Path.of(System.getProperty("java.home"), "bin", "java.exe");
        if (Files.isRegularFile(java)) {
            PeIconExtractor.extract(java, 32).ifPresent(icon ->
                    assertTrue(icon.isPng() || icon.width() > 0));
        }
    }

    // ------------------------------------------------------------------ синтетичний .exe

    /**
     * Мінімальний 64-бітний PE: DOS-заголовок, PE-заголовок, одна секція .rsrc
     * з деревом ресурсів RT_ICON (3) та RT_GROUP_ICON (14).
     */
    private Path writePe(List<Icon> icons) throws IOException {
        int n = icons.size();
        int rootDir = 0;
        int iconTypeDir = 16 + 2 * 8;
        int groupTypeDir = iconTypeDir + 16 + n * 8;
        int firstIconLang = groupTypeDir + 16 + 8;
        int groupLang = firstIconLang + n * 24;
        int firstDataEntry = groupLang + 24;
        int raw = firstDataEntry + (n + 1) * 16;

        List<Integer> rawOffsets = new ArrayList<>();
        int cursor = raw;
        for (Icon icon : icons) {
            rawOffsets.add(cursor);
            cursor = align(cursor + icon.data().length);
        }
        byte[] group = groupData(icons);
        int groupRaw = cursor;
        int size = align(groupRaw + group.length);

        int rva = 0x1000;
        ByteBuffer r = ByteBuffer.allocate(size).order(ByteOrder.LITTLE_ENDIAN);
        directory(r, rootDir, 2);
        entry(r, rootDir + 16, 3, 0x80000000 | iconTypeDir);
        entry(r, rootDir + 24, 14, 0x80000000 | groupTypeDir);
        directory(r, iconTypeDir, n);
        for (int i = 0; i < n; i++) {
            int lang = firstIconLang + i * 24;
            entry(r, iconTypeDir + 16 + i * 8, i + 1, 0x80000000 | lang);
            directory(r, lang, 1);
            entry(r, lang + 16, 0x409, firstDataEntry + i * 16);
            r.putInt(firstDataEntry + i * 16, rva + rawOffsets.get(i));
            r.putInt(firstDataEntry + i * 16 + 4, icons.get(i).data().length);
            r.put(rawOffsets.get(i), icons.get(i).data());
        }
        directory(r, groupTypeDir, 1);
        entry(r, groupTypeDir + 16, 1, 0x80000000 | groupLang);
        directory(r, groupLang, 1);
        entry(r, groupLang + 16, 0x409, firstDataEntry + n * 16);
        r.putInt(firstDataEntry + n * 16, rva + groupRaw);
        r.putInt(firstDataEntry + n * 16 + 4, group.length);
        r.put(groupRaw, group);

        int peOffset = 0x40;
        int optionalSize = 240;
        int sectionTable = peOffset + 24 + optionalSize;
        int rawPointer = 0x200;
        ByteBuffer f = ByteBuffer.allocate(rawPointer + size).order(ByteOrder.LITTLE_ENDIAN);
        f.putShort(0, (short) 0x5A4D);                 // «MZ»
        f.putInt(0x3C, peOffset);
        f.putInt(peOffset, 0x00004550);                // «PE\0\0»
        f.putShort(peOffset + 4, (short) 0x8664);      // x64
        f.putShort(peOffset + 6, (short) 1);           // одна секція
        f.putShort(peOffset + 20, (short) optionalSize);
        int opt = peOffset + 24;
        f.putShort(opt, (short) 0x20B);                // PE32+
        f.putInt(opt + 108, 16);                       // кількість каталогів даних
        f.putInt(opt + 112 + 2 * 8, rva);              // каталог №2 — ресурси
        f.putInt(opt + 112 + 2 * 8 + 4, size);
        f.put(sectionTable, ".rsrc".getBytes());
        f.putInt(sectionTable + 8, size);              // VirtualSize
        f.putInt(sectionTable + 12, rva);              // VirtualAddress
        f.putInt(sectionTable + 16, size);             // SizeOfRawData
        f.putInt(sectionTable + 20, rawPointer);       // PointerToRawData
        f.put(rawPointer, r.array());

        Path exe = dir.resolve("game-" + System.nanoTime() + ".exe");
        Files.write(exe, f.array());
        return exe;
    }

    private static byte[] groupData(List<Icon> icons) {
        ByteBuffer g = ByteBuffer.allocate(6 + icons.size() * 14).order(ByteOrder.LITTLE_ENDIAN);
        g.putShort((short) 0).putShort((short) 1).putShort((short) icons.size());
        for (int i = 0; i < icons.size(); i++) {
            Icon icon = icons.get(i);
            g.put((byte) (icon.size() >= 256 ? 0 : icon.size()));
            g.put((byte) (icon.size() >= 256 ? 0 : icon.size()));
            g.put((byte) 0).put((byte) 0);
            g.putShort((short) 1).putShort((short) icon.bitCount());
            g.putInt(icon.data().length);
            g.putShort((short) (i + 1));
        }
        return g.array();
    }

    private static void directory(ByteBuffer r, int at, int idEntries) {
        r.putShort(at + 14, (short) idEntries);
    }

    private static void entry(ByteBuffer r, int at, int id, int target) {
        r.putInt(at, id);
        r.putInt(at + 4, target);
    }

    private static int align(int value) {
        return (value + 3) & ~3;
    }

    /** Не справжній PNG, але з правильною сигнатурою — розбірнику цього досить. */
    private static byte[] fakePng(int marker) {
        byte[] data = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n', (byte) marker, 1, 2, 3};
        return data;
    }

    /** 32-бітний BMP без файлового заголовка: рядки знизу вгору, BGRA, плюс порожня маска. */
    private static byte[] dib32(int w, int h, int[] topDown) {
        int maskStride = ((w + 31) / 32) * 4;
        ByteBuffer b = ByteBuffer.allocate(40 + w * h * 4 + maskStride * h).order(ByteOrder.LITTLE_ENDIAN);
        b.putInt(40).putInt(w).putInt(h * 2).putShort((short) 1).putShort((short) 32);
        b.position(40);
        for (int row = h - 1; row >= 0; row--) {
            for (int x = 0; x < w; x++) {
                int p = topDown[row * w + x];
                b.put((byte) p).put((byte) (p >> 8)).put((byte) (p >> 16)).put((byte) (p >>> 24));
            }
        }
        return b.array();
    }

    /** 2×2, 4 біти на піксель: верх — [червоний, червоний(прозорий за маскою)], низ — [чорний, червоний]. */
    private static byte[] dib4WithMask() {
        ByteBuffer b = ByteBuffer.allocate(40 + 16 * 4 + 2 * 4 + 2 * 4).order(ByteOrder.LITTLE_ENDIAN);
        b.putInt(40).putInt(2).putInt(4).putShort((short) 1).putShort((short) 4);
        int palette = 40;
        b.put(palette + 4 + 2, (byte) 0xFF);           // колір 1 — червоний (B, G, R, 0)
        int pixels = palette + 16 * 4;
        b.put(pixels, (byte) 0x01);                    // нижній рядок: 0, 1
        b.put(pixels + 4, (byte) 0x11);                // верхній рядок: 1, 1
        int mask = pixels + 2 * 4;
        b.put(mask + 4, (byte) 0x40);                  // верхній рядок: другий піксель прозорий
        return b.array();
    }
}
