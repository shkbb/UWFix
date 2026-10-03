package ua.uwfix.patch;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;
import ua.uwfix.model.AspectRatio;
import ua.uwfix.model.ValueFormat;
import ua.uwfix.patch.Patcher.FileState;
import ua.uwfix.patch.Patcher.RestoreOutcome;
import ua.uwfix.search.FileScanner;
import ua.uwfix.util.ProgressListener;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.FileTime;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.Random;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PatcherTest {

    private static final AspectRatio UW_3440 = new AspectRatio(3440, 1440);
    private static final AspectRatio UW_2560 = new AspectRatio(2560, 1080);
    private static final byte[] F16_9 = ValueFormat.FLOAT32.encode(AspectRatio.STANDARD);
    private static final byte[] D16_9 = ValueFormat.FLOAT64.encode(AspectRatio.STANDARD);
    private static final int[] FLOAT_OFFSETS = {100, 4096, 70_001, 199_996};
    private static final int DOUBLE_OFFSET = 150_000;
    private static final Set<ValueFormat> FLOAT_ONLY = EnumSet.of(ValueFormat.FLOAT32);

    @TempDir
    Path dir;

    private Path exe;
    private byte[] original;
    private PatchStore store;
    private Patcher patcher;

    @BeforeEach
    void setUp() throws IOException {
        original = gameBinary(200_000, 1);
        exe = dir.resolve("game.exe");
        Files.write(exe, original);
        store = new PatchStore(dir.resolve("state/state.json"));
        patcher = new Patcher(store, new FileScanner());
    }

    @Test
    void patchReplacesOnlyFoundValuesAndKeepsBackup() throws Exception {
        Patcher.PatchOutcome outcome = apply(UW_3440, FLOAT_ONLY);

        assertEquals(FLOAT_OFFSETS.length, outcome.replaced());
        byte[] patched = Files.readAllBytes(exe);
        byte[] expected = original.clone();
        for (int offset : FLOAT_OFFSETS) {
            System.arraycopy(ValueFormat.FLOAT32.encode(UW_3440), 0, expected, offset, 4);
        }
        assertArrayEquals(expected, patched, "змінені лише знайдені 4-байтові значення");
        assertArrayEquals(original, Files.readAllBytes(Patcher.backupPath(exe)), "резервна копія = оригінал");
        assertEquals(FileState.PATCHED, patcher.quickState(exe));
        assertEquals(FLOAT_OFFSETS.length, store.find(exe).orElseThrow().changes().size());
    }

    @Test
    void restoreReturnsExactOriginalAndCleansUp() throws Exception {
        apply(UW_3440, EnumSet.allOf(ValueFormat.class));
        RestoreOutcome outcome = patcher.restore(exe, ProgressListener.NONE);

        assertEquals(RestoreOutcome.RESTORED, outcome);
        assertArrayEquals(original, Files.readAllBytes(exe));
        assertFalse(Files.exists(Patcher.backupPath(exe)));
        assertTrue(store.find(exe).isEmpty());
        assertEquals(FileState.ORIGINAL, patcher.quickState(exe));
    }

    /** Linux: програма гри має лишитися виконуваною і після заміни, і після відновлення. */
    @Test
    @DisabledOnOs(OS.WINDOWS)
    void executableBitSurvivesPatchAndRestore() throws Exception {
        Set<PosixFilePermission> mode = PosixFilePermissions.fromString("rwxr-xr-x");
        Files.setPosixFilePermissions(exe, mode);

        apply(UW_3440, FLOAT_ONLY);
        assertEquals(mode, Files.getPosixFilePermissions(exe));
        patcher.restore(exe, ProgressListener.NONE);
        assertEquals(mode, Files.getPosixFilePermissions(exe));
    }

    @Test
    void doubleFormatIsPatchedWhenRequested() throws Exception {
        Patcher.PatchOutcome outcome = apply(UW_3440, EnumSet.allOf(ValueFormat.class));
        assertEquals(FLOAT_OFFSETS.length + 1, outcome.replaced());
        byte[] patched = Files.readAllBytes(exe);
        assertArrayEquals(ValueFormat.FLOAT64.encode(UW_3440),
                Arrays.copyOfRange(patched, DOUBLE_OFFSET, DOUBLE_OFFSET + 8));
    }

    @Test
    void repatchingWithAnotherRatioStartsFromOriginal() throws Exception {
        apply(UW_3440, FLOAT_ONLY);
        Patcher.PatchOutcome second = apply(UW_2560, FLOAT_ONLY);

        assertEquals(FLOAT_OFFSETS.length, second.previouslyReverted());
        assertEquals(FLOAT_OFFSETS.length, second.replaced());
        byte[] patched = Files.readAllBytes(exe);
        for (int offset : FLOAT_OFFSETS) {
            assertArrayEquals(ValueFormat.FLOAT32.encode(UW_2560), Arrays.copyOfRange(patched, offset, offset + 4));
        }
        assertArrayEquals(original, Files.readAllBytes(Patcher.backupPath(exe)), "копія — справжній оригінал");
        assertEquals(1, store.all().size());
    }

    @Test
    void gameUpdateIsDetectedAndPatchCanBeReapplied() throws Exception {
        apply(UW_3440, FLOAT_ONLY);

        // «Оновлення гри»: Steam записав новий файл з іншим вмістом
        byte[] updated = gameBinary(210_000, 2);
        Files.write(exe, updated);
        Files.setLastModifiedTime(exe, FileTime.fromMillis(System.currentTimeMillis() + 5_000));
        assertEquals(FileState.CHANGED_AFTER_PATCH, patcher.quickState(exe));

        Patcher.PatchOutcome outcome = patcher.reapply(store.find(exe).orElseThrow(), ProgressListener.NONE);
        assertEquals(FLOAT_OFFSETS.length, outcome.replaced());
        assertEquals(0, outcome.previouslyReverted(), "старі зміни не можна відкочувати на новому файлі");
        assertEquals(FileState.PATCHED, patcher.quickState(exe));
        assertArrayEquals(updated, Files.readAllBytes(Patcher.backupPath(exe)), "копія — нова версія гри");
    }

    @Test
    void restoreAfterUpdateReportsThatPatchIsGone() throws Exception {
        apply(UW_3440, FLOAT_ONLY);
        Files.write(exe, gameBinary(200_000, 3));

        assertEquals(RestoreOutcome.REPLACED_BY_UPDATE, patcher.restore(exe, ProgressListener.NONE));
        assertTrue(store.find(exe).isEmpty());
        assertFalse(Files.exists(Patcher.backupPath(exe)));
    }

    @Test
    void restoreAfterIntegrityCheckReportsAlreadyOriginal() throws Exception {
        apply(UW_3440, FLOAT_ONLY);
        Files.write(exe, original); // «Перевірити цілісність файлів» у Steam

        assertEquals(RestoreOutcome.ALREADY_ORIGINAL, patcher.restore(exe, ProgressListener.NONE));
        assertArrayEquals(original, Files.readAllBytes(exe));
    }

    @Test
    void restoreFallsBackToBackupWhenBytesWereTampered() throws Exception {
        apply(UW_3440, FLOAT_ONLY);
        PatchRecord record = store.find(exe).orElseThrow();
        // Хтось змінив одне з місць, але хеш ми підробимо, щоб файл виглядав «нашим»
        overwrite(exe, FLOAT_OFFSETS[0], new byte[]{1, 2, 3, 4});
        String tamperedSha = new FileScanner().sha256(exe);
        store.put(new PatchRecord(record.gameId(), record.gameName(), record.file(), record.backupFile(),
                record.ratioWidth(), record.ratioHeight(), record.formats(), record.originalSha256(), tamperedSha,
                record.patchedSize(), record.patchedModified(), record.patchedAt(), record.changes()));

        assertEquals(RestoreOutcome.RESTORED, patcher.restore(exe, ProgressListener.NONE));
        assertArrayEquals(original, Files.readAllBytes(exe));
    }

    @Test
    void fileWithoutMatchesIsLeftUntouched() throws Exception {
        Path other = dir.resolve("other.exe");
        byte[] data = new byte[50_000];
        Files.write(other, data);

        Patcher.PatchOutcome outcome = patcher.apply("g", "Game", other, UW_3440, FLOAT_ONLY, ProgressListener.NONE);
        assertTrue(outcome.nothingFound());
        assertFalse(Files.exists(Patcher.backupPath(other)));
        assertTrue(store.find(other).isEmpty());
    }

    @Test
    void standardRatioIsRejected() {
        PatchException e = assertThrows(PatchException.class, () -> apply(new AspectRatio(1920, 1080), FLOAT_ONLY));
        assertEquals(PatchException.Kind.NOTHING_TO_DO, e.kind());
    }

    @Test
    void restoringUnknownFileIsRejected() {
        PatchException e = assertThrows(PatchException.class, () -> patcher.restore(exe, ProgressListener.NONE));
        assertEquals(PatchException.Kind.NOT_PATCHED, e.kind());
    }

    @Test
    void stateSurvivesRestart() throws Exception {
        apply(UW_3440, FLOAT_ONLY);
        PatchStore reopened = new PatchStore(dir.resolve("state/state.json"));
        PatchRecord record = reopened.find(exe).orElseThrow();
        assertEquals(3440, record.ratioWidth());
        assertEquals(FLOAT_OFFSETS.length, record.changes().size());
        assertEquals(FileState.PATCHED, new Patcher(reopened).quickState(exe));
    }

    // ------------------------------------------------------------------

    private Patcher.PatchOutcome apply(AspectRatio ratio, Set<ValueFormat> formats) throws Exception {
        return patcher.apply("steam:1", "Test Game", exe, ratio, formats, ProgressListener.NONE);
    }

    /** Випадкові байти без 16:9 + 16:9 у відомих місцях. */
    private static byte[] gameBinary(int size, long seed) {
        byte[] data = new byte[size];
        new Random(seed).nextBytes(data);
        for (int i = 0; i < size; i++) {
            if ((data[i] & 0xFF) == 0x39 || (data[i] & 0xFF) == 0x1C) {
                data[i] = 0;
            }
        }
        for (int offset : FLOAT_OFFSETS) {
            System.arraycopy(F16_9, 0, data, offset, 4);
        }
        System.arraycopy(D16_9, 0, data, DOUBLE_OFFSET, 8);
        return data;
    }

    private static void overwrite(Path file, long offset, byte[] bytes) throws IOException {
        try (FileChannel ch = FileChannel.open(file, StandardOpenOption.WRITE)) {
            ch.write(ByteBuffer.wrap(bytes), offset);
        }
    }
}
