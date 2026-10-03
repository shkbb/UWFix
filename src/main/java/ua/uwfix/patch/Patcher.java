package ua.uwfix.patch;

import ua.uwfix.i18n.I18n;
import ua.uwfix.model.AspectRatio;
import ua.uwfix.model.ValueFormat;
import ua.uwfix.search.FileScanner;
import ua.uwfix.search.Hex;
import ua.uwfix.search.ScanResult;
import ua.uwfix.util.ProgressListener;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.AccessDeniedException;
import java.nio.file.FileSystemException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Застосовує та скасовує заміну 16:9 у файлах гри.
 * <p>
 * Порядок застосування:
 * <ol>
 *   <li>перевірити, що файл можна змінювати (права, гра не запущена);</li>
 *   <li>якщо файл уже пропатчений програмою — спершу повернути оригінальні байти;</li>
 *   <li>знайти всі входження 16:9 і порахувати SHA-256 оригіналу;</li>
 *   <li>зробити резервну копію {@code <файл>.uwfix-backup};</li>
 *   <li>записати нові байти лише у знайдені місця (решта файлу не переписується);</li>
 *   <li>порахувати SHA-256 результату і зберегти запис про патч.</li>
 * </ol>
 */
public final class Patcher {

    public static final String BACKUP_SUFFIX = ".uwfix-backup";

    /** Стан файлу відносно програми. */
    public enum FileState {
        /** Програма цей файл не змінювала. */
        ORIGINAL,
        /** Файл пропатчений і з того часу не змінювався. */
        PATCHED,
        /** Файл пропатчений, але потім змінився (оновлення гри або перевірка цілісності). */
        CHANGED_AFTER_PATCH
    }

    /** Підсумок застосування патчу до одного файлу. */
    public record PatchOutcome(Path file, int replaced, int previouslyReverted) {
        public boolean nothingFound() {
            return replaced == 0;
        }
    }

    /** Підсумок відновлення одного файлу. */
    public enum RestoreOutcome {
        /** Оригінальні байти повернуто. */
        RESTORED,
        /** Файл уже був оригінальним (наприклад, після перевірки цілісності в Steam). */
        ALREADY_ORIGINAL,
        /** Файл замінило оновлення гри — патчу в ньому вже немає. */
        REPLACED_BY_UPDATE
    }

    private final PatchStore store;
    private final FileScanner scanner;

    public Patcher(PatchStore store) {
        this(store, new FileScanner());
    }

    public Patcher(PatchStore store, FileScanner scanner) {
        this.store = store;
        this.scanner = scanner;
    }

    public PatchStore store() {
        return store;
    }

    // ------------------------------------------------------------------ стан

    /** Швидка перевірка без хешування: за розміром і часом зміни файлу. */
    public FileState quickState(Path file) {
        Optional<PatchRecord> record = store.find(file);
        if (record.isEmpty()) {
            return FileState.ORIGINAL;
        }
        try {
            boolean same = Files.size(file) == record.get().patchedSize()
                    && Files.getLastModifiedTime(file).toMillis() == record.get().patchedModified();
            return same ? FileState.PATCHED : FileState.CHANGED_AFTER_PATCH;
        } catch (IOException e) {
            return FileState.CHANGED_AFTER_PATCH;
        }
    }

    /** Точна перевірка за контрольною сумою. */
    public FileState state(Path file, String currentSha256) {
        Optional<PatchRecord> record = store.find(file);
        if (record.isEmpty()) {
            return FileState.ORIGINAL;
        }
        return record.get().patchedSha256().equalsIgnoreCase(currentSha256)
                ? FileState.PATCHED
                : FileState.CHANGED_AFTER_PATCH;
    }

    // ------------------------------------------------------------------ застосування

    /**
     * Замінює 16:9 на {@code target} у файлі.
     *
     * @param gameId   ключ гри (для групування записів)
     * @param gameName назва гри
     * @param file     файл для зміни
     * @param target   цільове співвідношення
     * @param formats  які формати чисел замінювати
     */
    public PatchOutcome apply(String gameId, String gameName, Path file, AspectRatio target,
                              Set<ValueFormat> formats, ProgressListener progress)
            throws IOException, PatchException {
        if (target.isStandard()) {
            throw new PatchException(PatchException.Kind.NOTHING_TO_DO, I18n.t("error.standardRatio"));
        }
        if (formats.isEmpty()) {
            throw new PatchException(PatchException.Kind.NOTHING_TO_DO, I18n.t("error.noFormats"));
        }
        ensureWritable(file);

        // 1. Якщо файл уже змінений нами — повертаємо оригінал, щоб не патчити двічі.
        int reverted = 0;
        Optional<PatchRecord> existing = store.find(file);
        if (existing.isPresent()) {
            progress.update(0, I18n.t("progress.checkingPrevious"));
            String current = scanner.sha256(file);
            PatchRecord old = existing.get();
            if (current.equalsIgnoreCase(old.patchedSha256())) {
                reverted = revert(file, old);
            } else if (!current.equalsIgnoreCase(old.originalSha256())) {
                // файл уже новий (оновлення гри) — стара резервна копія більше не потрібна
                Files.deleteIfExists(Path.of(old.backupFile()));
            }
            store.remove(file);
            store.save();
        }

        // 2. Пошук 16:9.
        List<ValueFormat> formatList = Arrays.stream(ValueFormat.values()).filter(formats::contains).toList();
        List<byte[]> finds = new ArrayList<>();
        List<byte[]> replacements = new ArrayList<>();
        for (ValueFormat f : formatList) {
            finds.add(f.encode(AspectRatio.STANDARD));
            replacements.add(f.encode(target));
        }
        String label = I18n.t("progress.searching", file.getFileName().toString());
        ScanResult scan = scanner.scan(file, finds, scaled(progress, 0.0, 0.45, label));
        if (scan.totalMatches() == 0) {
            return new PatchOutcome(file, 0, reverted);
        }

        List<PatchChange> changes = buildChanges(scan, finds, replacements);

        // 3. Резервна копія.
        progress.update(0.5, I18n.t("progress.backup", file.getFileName().toString()));
        Path backup = backupPath(file);
        Files.copy(file, backup, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES);
        if (Files.size(backup) != scan.size()) {
            throw new PatchException(PatchException.Kind.INTEGRITY, I18n.t("error.backupFailed", backup.toString()));
        }

        // 4. Запис нових байтів у знайдені місця.
        progress.update(0.55, I18n.t("progress.writing", file.getFileName().toString()));
        try {
            writeChanges(file, changes, false);
        } catch (IOException | PatchException e) {
            Files.copy(backup, file, StandardCopyOption.REPLACE_EXISTING);
            throw e;
        }

        // 5. Контрольна сума результату і запис про патч.
        // Файл уже змінено, тому цей крок не можна скасовувати — інакше зміни лишаться без запису.
        String patchedSha = scanner.scan(file, List.of(),
                uncancellable(scaled(progress, 0.6, 1.0, I18n.t("progress.verifying")))).sha256();
        PatchRecord record = new PatchRecord(
                gameId, gameName,
                file.toAbsolutePath().normalize().toString(),
                backup.toAbsolutePath().normalize().toString(),
                target.width(), target.height(),
                formatList.stream().map(Enum::name).toList(),
                scan.sha256(), patchedSha,
                Files.size(file), Files.getLastModifiedTime(file).toMillis(),
                LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS).toString(),
                changes);
        store.put(record);
        store.save();
        progress.update(1, I18n.t("progress.done"));
        return new PatchOutcome(file, changes.size(), reverted);
    }

    /** Повторно застосовує патч після оновлення гри з тими самими налаштуваннями. */
    public PatchOutcome reapply(PatchRecord record, ProgressListener progress) throws IOException, PatchException {
        Set<ValueFormat> formats = java.util.EnumSet.noneOf(ValueFormat.class);
        for (String f : record.formats()) {
            formats.add(ValueFormat.valueOf(f));
        }
        return apply(record.gameId(), record.gameName(), Path.of(record.file()), record.ratio(), formats, progress);
    }

    // ------------------------------------------------------------------ відновлення

    /** Повертає оригінальний вміст файлу і видаляє резервну копію. */
    public RestoreOutcome restore(Path file, ProgressListener progress) throws IOException, PatchException {
        PatchRecord record = store.find(file).orElseThrow(() -> new PatchException(
                PatchException.Kind.NOT_PATCHED, I18n.t("error.notPatched", file.getFileName().toString())));
        ensureWritable(file);

        String current = scanner.scan(file, List.of(), scaled(progress, 0, 0.4, I18n.t("progress.checking", file.getFileName().toString()))).sha256();
        RestoreOutcome outcome;
        if (current.equalsIgnoreCase(record.patchedSha256())) {
            progress.update(0.5, I18n.t("progress.reverting"));
            revert(file, record);
            String after = scanner.scan(file, List.of(),
                    uncancellable(scaled(progress, 0.5, 0.9, I18n.t("progress.verifying")))).sha256();
            if (!after.equalsIgnoreCase(record.originalSha256())) {
                restoreFromBackup(file, record);
            }
            outcome = RestoreOutcome.RESTORED;
        } else if (current.equalsIgnoreCase(record.originalSha256())) {
            outcome = RestoreOutcome.ALREADY_ORIGINAL;
        } else {
            outcome = RestoreOutcome.REPLACED_BY_UPDATE;
        }

        Files.deleteIfExists(Path.of(record.backupFile()));
        store.remove(file);
        store.save();
        progress.update(1, I18n.t("progress.done"));
        return outcome;
    }

    // ------------------------------------------------------------------ допоміжне

    public static Path backupPath(Path file) {
        return file.resolveSibling(file.getFileName() + BACKUP_SUFFIX);
    }

    /** Список замін, відсортований за зміщенням, без перекриттів між різними форматами. */
    static List<PatchChange> buildChanges(ScanResult scan, List<byte[]> finds, List<byte[]> replacements) {
        record Hit(long offset, int index) {
        }
        List<Hit> hits = new ArrayList<>();
        for (int k = 0; k < finds.size(); k++) {
            for (long offset : scan.offsets().get(k)) {
                hits.add(new Hit(offset, k));
            }
        }
        hits.sort(Comparator.comparingLong(Hit::offset));

        List<PatchChange> changes = new ArrayList<>();
        long busyUntil = -1;
        for (Hit h : hits) {
            if (h.offset() < busyUntil) {
                continue;
            }
            byte[] find = finds.get(h.index());
            changes.add(new PatchChange(h.offset(), Hex.compact(find), Hex.compact(replacements.get(h.index()))));
            busyUntil = h.offset() + find.length;
        }
        return changes;
    }

    /**
     * Записує заміни у файл. Перед кожним записом перевіряє, що на місці лежать
     * саме ті байти, які очікуються, — інакше файл змінився і писати небезпечно.
     *
     * @param reverse {@code true} — повертати оригінальні байти замість нових
     * @return скільки місць змінено
     */
    private static int writeChanges(Path file, List<PatchChange> changes, boolean reverse)
            throws IOException, PatchException {
        int written = 0;
        try (FileChannel channel = FileChannel.open(file, StandardOpenOption.READ, StandardOpenOption.WRITE)) {
            for (PatchChange change : changes) {
                byte[] expected = Hex.parse(reverse ? change.replacement() : change.original());
                byte[] desired = Hex.parse(reverse ? change.original() : change.replacement());
                byte[] actual = readAt(channel, change.offset(), expected.length);
                if (Arrays.equals(actual, desired)) {
                    continue; // уже має потрібне значення
                }
                if (!Arrays.equals(actual, expected)) {
                    throw new PatchException(PatchException.Kind.INTEGRITY, I18n.t("error.unexpectedBytes",
                            String.format("0x%X", change.offset()), Hex.spaced(expected), Hex.spaced(actual)));
                }
                ByteBuffer buffer = ByteBuffer.wrap(desired);
                long position = change.offset();
                while (buffer.hasRemaining()) {
                    position += channel.write(buffer, position);
                }
                written++;
            }
            channel.force(true);
        }
        return written;
    }

    private static byte[] readAt(FileChannel channel, long offset, int length) throws IOException {
        ByteBuffer buffer = ByteBuffer.allocate(length);
        long position = offset;
        while (buffer.hasRemaining()) {
            int n = channel.read(buffer, position);
            if (n < 0) {
                break;
            }
            position += n;
        }
        return Arrays.copyOf(buffer.array(), buffer.position());
    }

    /** Повертає оригінальні байти; при невдачі — бере файл з резервної копії. */
    private int revert(Path file, PatchRecord record) throws IOException, PatchException {
        try {
            return writeChanges(file, record.changes(), true);
        } catch (PatchException e) {
            restoreFromBackup(file, record);
            return record.changes().size();
        }
    }

    private void restoreFromBackup(Path file, PatchRecord record) throws IOException, PatchException {
        Path backup = Path.of(record.backupFile());
        if (!Files.isRegularFile(backup)) {
            throw new PatchException(PatchException.Kind.INTEGRITY,
                    I18n.t("error.noBackup", file.getFileName().toString()));
        }
        if (!scanner.sha256(backup).equalsIgnoreCase(record.originalSha256())) {
            throw new PatchException(PatchException.Kind.INTEGRITY,
                    I18n.t("error.backupMismatch", backup.getFileName().toString()));
        }
        Files.copy(backup, file, StandardCopyOption.REPLACE_EXISTING);
    }

    /**
     * Перевіряє, що файл можна відкрити на запис. Запущений .exe Windows не дає
     * відкрити на запис — так ми дізнаємось, що гра запущена.
     */
    static void ensureWritable(Path file) throws PatchException, IOException {
        try (FileChannel ignored = FileChannel.open(file, StandardOpenOption.READ, StandardOpenOption.WRITE)) {
            // відкрилось — усе гаразд
        } catch (NoSuchFileException e) {
            throw new PatchException(PatchException.Kind.INTEGRITY, I18n.t("error.fileNotFound", file.toString()), e);
        } catch (AccessDeniedException e) {
            if (isReadOnly(file)) {
                Files.setAttribute(file, "dos:readonly", false);
                ensureWritable(file);
                return;
            }
            throw new PatchException(PatchException.Kind.NEED_ADMIN,
                    I18n.t("error.needAdmin", file.getFileName().toString()), e);
        } catch (FileSystemException e) {
            throw new PatchException(PatchException.Kind.FILE_IN_USE,
                    I18n.t("error.inUse", file.getFileName().toString()), e);
        }
    }

    private static boolean isReadOnly(Path file) {
        try {
            Object value = Files.getAttribute(file, "dos:readonly");
            return Boolean.TRUE.equals(value);
        } catch (IOException | UnsupportedOperationException e) {
            return false;
        }
    }

    /** Обгортка, яка ігнорує скасування (для кроків після зміни файлу). */
    private static ProgressListener uncancellable(ProgressListener parent) {
        return parent::update;
    }

    /** Перетворює прогрес підзадачі [0..1] у діапазон [from..to] загального прогресу. */
    private static ProgressListener scaled(ProgressListener parent, double from, double to, String message) {
        return new ProgressListener() {
            @Override
            public void update(double fraction, String ignored) {
                parent.update(from + (to - from) * Math.max(0, fraction), message);
            }

            @Override
            public boolean isCancelled() {
                return parent.isCancelled();
            }
        };
    }
}
