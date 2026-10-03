package ua.uwfix.icon;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Витягує іконку програми з виконуваного файлу Windows (формат PE — Portable Executable).
 * <p>
 * Будова, яку розбирає клас:
 * <pre>
 *   DOS-заголовок «MZ» → за зміщенням 0x3C адреса PE-заголовка
 *   PE-заголовок «PE\0\0» → COFF-заголовок → Optional header → каталог даних №2 (ресурси)
 *   таблиця секцій — щоб перевести віртуальну адресу (RVA) у зміщення у файлі
 *   дерево ресурсів: тип → ідентифікатор → мова → дані
 *     RT_GROUP_ICON (14) — список розмірів іконки та ідентифікаторів зображень
 *     RT_ICON (3)        — самі зображення: PNG або BMP без файлового заголовка
 * </pre>
 * Читаються лише потрібні фрагменти файлу, тож навіть .exe на сотні мегабайтів обробляється миттєво.
 */
public final class PeIconExtractor {

    private static final int RT_ICON = 3;
    private static final int RT_GROUP_ICON = 14;
    private static final int MAX_DATA = 4 * 1024 * 1024;
    private static final byte[] PNG_SIGNATURE = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n'};

    private record Section(long virtualAddress, long virtualSize, long rawPointer, long rawSize) {
    }

    /** Запис каталогу ресурсів: ідентифікатор і зміщення (від кореня ресурсів) підкаталогу або даних. */
    private record DirEntry(boolean named, int id, boolean directory, long offset) {
    }

    /** Опис одного зображення в групі іконок. */
    private record GroupItem(int size, int bitCount, int id) {
    }

    private final FileChannel channel;
    private final List<Section> sections = new ArrayList<>();
    private final long resourceRoot;

    private PeIconExtractor(FileChannel channel) throws IOException {
        this.channel = channel;
        ByteBuffer dos = read(0, 64);
        if (dos.getShort(0) != 0x5A4D) { // «MZ»
            throw new IllegalArgumentException("Не PE-файл");
        }
        long pe = dos.getInt(0x3C) & 0xFFFFFFFFL;
        ByteBuffer coff = read(pe, 24);
        if (coff.getInt(0) != 0x00004550) { // «PE\0\0»
            throw new IllegalArgumentException("Немає PE-заголовка");
        }
        int sectionCount = coff.getShort(6) & 0xFFFF;
        int optionalSize = coff.getShort(20) & 0xFFFF;
        long optional = pe + 24;
        ByteBuffer opt = read(optional, optionalSize);
        int magic = opt.getShort(0) & 0xFFFF;
        int dirsStart = switch (magic) {
            case 0x10B -> 96;  // PE32 (32-бітні програми)
            case 0x20B -> 112; // PE32+ (64-бітні програми)
            default -> throw new IllegalArgumentException("Невідомий формат Optional header");
        };
        int dirCount = opt.getInt(dirsStart - 4);
        if (dirCount <= 2 || dirsStart + 3 * 8 > optionalSize) {
            throw new IllegalArgumentException("Немає каталогу ресурсів");
        }
        long resourceRva = opt.getInt(dirsStart + 2 * 8) & 0xFFFFFFFFL;

        ByteBuffer table = read(optional + optionalSize, sectionCount * 40);
        for (int i = 0; i < sectionCount; i++) {
            int s = i * 40;
            sections.add(new Section(
                    table.getInt(s + 12) & 0xFFFFFFFFL,
                    table.getInt(s + 8) & 0xFFFFFFFFL,
                    table.getInt(s + 20) & 0xFFFFFFFFL,
                    table.getInt(s + 16) & 0xFFFFFFFFL));
        }
        if (resourceRva == 0) {
            throw new IllegalArgumentException("У файлі немає ресурсів");
        }
        this.resourceRoot = rvaToOffset(resourceRva);
    }

    /**
     * @param exe         виконуваний файл
     * @param desiredSize бажаний розмір у пікселях: обирається найменше зображення, не менше за нього
     * @return іконка або порожнє значення, якщо її немає чи файл не є PE
     */
    public static Optional<IconImage> extract(Path exe, int desiredSize) {
        try (FileChannel channel = FileChannel.open(exe, StandardOpenOption.READ)) {
            return new PeIconExtractor(channel).findIcon(desiredSize);
        } catch (IOException | RuntimeException e) {
            return Optional.empty();
        }
    }

    private Optional<IconImage> findIcon(int desiredSize) throws IOException {
        DirEntry iconType = null;
        DirEntry groupType = null;
        for (DirEntry e : entries(0)) {
            if (!e.named() && e.directory() && e.id() == RT_ICON) {
                iconType = e;
            } else if (!e.named() && e.directory() && e.id() == RT_GROUP_ICON) {
                groupType = e;
            }
        }
        if (iconType == null || groupType == null) {
            return Optional.empty();
        }
        // Провідник Windows бере першу групу іконок — так само робимо і ми
        List<DirEntry> groups = entries(groupType.offset());
        if (groups.isEmpty()) {
            return Optional.empty();
        }
        byte[] group = firstData(groups.get(0));
        if (group == null || group.length < 6) {
            return Optional.empty();
        }

        Map<Integer, DirEntry> images = new HashMap<>();
        for (DirEntry e : entries(iconType.offset())) {
            if (!e.named()) {
                images.put(e.id(), e);
            }
        }
        for (GroupItem item : rank(parseGroup(group), desiredSize)) {
            DirEntry entry = images.get(item.id());
            byte[] data = entry == null ? null : firstData(entry);
            if (data != null) {
                try {
                    return Optional.of(decode(data));
                } catch (RuntimeException e) {
                    // це зображення пошкоджене або в непідтримуваному форматі — пробуємо наступне
                }
            }
        }
        return Optional.empty();
    }

    /** GRPICONDIR: 6 байтів заголовка, далі по 14 байтів на кожне зображення. */
    private static List<GroupItem> parseGroup(byte[] group) {
        ByteBuffer b = ByteBuffer.wrap(group).order(ByteOrder.LITTLE_ENDIAN);
        int count = b.getShort(4) & 0xFFFF;
        List<GroupItem> items = new ArrayList<>();
        for (int i = 0; i < count && 6 + (i + 1) * 14 <= group.length; i++) {
            int p = 6 + i * 14;
            int width = group[p] & 0xFF;
            int bitCount = b.getShort(p + 6) & 0xFFFF;
            int id = b.getShort(p + 12) & 0xFFFF;
            items.add(new GroupItem(width == 0 ? 256 : width, bitCount, id));
        }
        return items;
    }

    /** Спершу найменші зображення, не менші за бажаний розмір; потім більші за якістю з решти. */
    static <T> List<T> rankBySize(List<T> items, java.util.function.ToIntFunction<T> size,
                                  java.util.function.ToIntFunction<T> bits, int desired) {
        List<T> sorted = new ArrayList<>(items);
        sorted.sort(Comparator.<T>comparingInt(i -> size.applyAsInt(i) >= desired ? 0 : 1)
                .thenComparingInt(i -> size.applyAsInt(i) >= desired ? size.applyAsInt(i) : -size.applyAsInt(i))
                .thenComparingInt(i -> -bits.applyAsInt(i)));
        return sorted;
    }

    private static List<GroupItem> rank(List<GroupItem> items, int desired) {
        return rankBySize(items, GroupItem::size, GroupItem::bitCount, desired);
    }

    // ------------------------------------------------------------------ дерево ресурсів

    private List<DirEntry> entries(long directoryOffset) throws IOException {
        ByteBuffer header = read(resourceRoot + directoryOffset, 16);
        int count = (header.getShort(12) & 0xFFFF) + (header.getShort(14) & 0xFFFF);
        if (count > 10_000) {
            throw new IllegalArgumentException("Пошкоджений каталог ресурсів");
        }
        ByteBuffer table = read(resourceRoot + directoryOffset + 16, count * 8);
        List<DirEntry> list = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            int name = table.getInt(i * 8);
            int target = table.getInt(i * 8 + 4);
            list.add(new DirEntry(name < 0, name & 0xFFFF, target < 0, target & 0x7FFFFFFFL));
        }
        return list;
    }

    /** Спускається по першому підкаталогу (мові) до даних і читає їх. */
    private byte[] firstData(DirEntry entry) throws IOException {
        DirEntry current = entry;
        for (int depth = 0; current.directory(); depth++) {
            List<DirEntry> children = entries(current.offset());
            if (children.isEmpty() || depth > 4) {
                return null;
            }
            current = children.get(0);
        }
        ByteBuffer dataEntry = read(resourceRoot + current.offset(), 16);
        long rva = dataEntry.getInt(0) & 0xFFFFFFFFL;
        int size = dataEntry.getInt(4);
        if (size <= 0 || size > MAX_DATA) {
            return null;
        }
        return read(rvaToOffset(rva), size).array();
    }

    private long rvaToOffset(long rva) {
        for (Section s : sections) {
            long span = Math.max(s.virtualSize(), s.rawSize());
            if (rva >= s.virtualAddress() && rva < s.virtualAddress() + span) {
                return s.rawPointer() + (rva - s.virtualAddress());
            }
        }
        throw new IllegalArgumentException("Адреса поза секціями: " + rva);
    }

    private ByteBuffer read(long offset, int length) throws IOException {
        if (offset < 0 || length < 0 || offset + length > channel.size()) {
            throw new IllegalArgumentException("Вихід за межі файлу");
        }
        ByteBuffer buffer = ByteBuffer.allocate(length).order(ByteOrder.LITTLE_ENDIAN);
        long position = offset;
        while (buffer.hasRemaining()) {
            int n = channel.read(buffer, position);
            if (n < 0) {
                throw new IOException("Несподіваний кінець файлу");
            }
            position += n;
        }
        buffer.flip();
        return buffer;
    }

    // ------------------------------------------------------------------ зображення

    /** Зображення іконки: PNG як є або BMP (DIB) — розкодовується у пікселі. */
    static IconImage decode(byte[] data) {
        if (data.length >= PNG_SIGNATURE.length) {
            boolean png = true;
            for (int i = 0; i < PNG_SIGNATURE.length; i++) {
                png &= data[i] == PNG_SIGNATURE[i];
            }
            if (png) {
                return IconImage.png(data);
            }
        }
        return decodeDib(data);
    }

    /**
     * BMP-іконка: BITMAPINFOHEADER, палітра (для 1/4/8 біт), рядки кольорів знизу вгору
     * і маска прозорості (1 біт на піксель: 1 — прозоро). Висота в заголовку подвоєна:
     * кольори + маска.
     */
    static IconImage decodeDib(byte[] d) {
        ByteBuffer b = ByteBuffer.wrap(d).order(ByteOrder.LITTLE_ENDIAN);
        int headerSize = b.getInt(0);
        int width = b.getInt(4);
        int height = Math.abs(b.getInt(8)) / 2;
        int bpp = b.getShort(14) & 0xFFFF;
        int compression = b.getInt(16);
        int colorsUsed = b.getInt(32);
        if (headerSize < 40 || width <= 0 || width > 512 || height <= 0 || height > 512 || compression != 0) {
            throw new IllegalArgumentException("Непідтримувана BMP-іконка");
        }
        if (bpp != 1 && bpp != 4 && bpp != 8 && bpp != 24 && bpp != 32) {
            throw new IllegalArgumentException("Непідтримувана глибина кольору: " + bpp);
        }
        int paletteColors = bpp <= 8 ? (colorsUsed != 0 ? colorsUsed : 1 << bpp) : 0;
        int pixels = headerSize + paletteColors * 4;
        int xorStride = ((width * bpp + 31) / 32) * 4;
        int andStride = ((width + 31) / 32) * 4;
        int mask = pixels + xorStride * height;
        if (mask > d.length) {
            throw new IllegalArgumentException("Обрізана BMP-іконка");
        }
        boolean hasMask = mask + andStride * height <= d.length;

        int[] argb = new int[width * height];
        boolean anyAlpha = false;
        for (int y = 0; y < height; y++) {
            int row = height - 1 - y; // рядки зберігаються знизу вгору
            int rowStart = pixels + row * xorStride;
            for (int x = 0; x < width; x++) {
                int blue;
                int green;
                int red;
                int alpha = 255;
                if (bpp == 32) {
                    int p = rowStart + x * 4;
                    blue = d[p] & 0xFF;
                    green = d[p + 1] & 0xFF;
                    red = d[p + 2] & 0xFF;
                    alpha = d[p + 3] & 0xFF;
                    anyAlpha |= alpha != 0;
                } else if (bpp == 24) {
                    int p = rowStart + x * 3;
                    blue = d[p] & 0xFF;
                    green = d[p + 1] & 0xFF;
                    red = d[p + 2] & 0xFF;
                } else {
                    int index = paletteIndex(d, rowStart, x, bpp);
                    int p = headerSize + Math.min(index, paletteColors - 1) * 4;
                    blue = d[p] & 0xFF;
                    green = d[p + 1] & 0xFF;
                    red = d[p + 2] & 0xFF;
                }
                argb[y * width + x] = (alpha << 24) | (red << 16) | (green << 8) | blue;
            }
        }

        // Прозорість з маски: для 1–24 біт завжди, для 32 біт — якщо альфа-канал порожній (старі іконки)
        if (hasMask && (bpp < 32 || !anyAlpha)) {
            for (int y = 0; y < height; y++) {
                int rowStart = mask + (height - 1 - y) * andStride;
                for (int x = 0; x < width; x++) {
                    boolean transparent = ((d[rowStart + x / 8] >> (7 - x % 8)) & 1) == 1;
                    int i = y * width + x;
                    argb[i] = transparent ? 0 : (argb[i] | 0xFF000000);
                }
            }
        }
        return IconImage.pixels(width, height, argb);
    }

    private static int paletteIndex(byte[] d, int rowStart, int x, int bpp) {
        return switch (bpp) {
            case 8 -> d[rowStart + x] & 0xFF;
            case 4 -> {
                int v = d[rowStart + x / 2] & 0xFF;
                yield x % 2 == 0 ? v >> 4 : v & 0x0F;
            }
            default -> (d[rowStart + x / 8] >> (7 - x % 8)) & 1;
        };
    }
}
