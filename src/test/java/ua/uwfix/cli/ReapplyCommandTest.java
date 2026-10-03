package ua.uwfix.cli;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ua.uwfix.model.AspectRatio;
import ua.uwfix.model.ValueFormat;
import ua.uwfix.patch.PatchStore;
import ua.uwfix.patch.Patcher;
import ua.uwfix.util.ProgressListener;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.EnumSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReapplyCommandTest {

    @TempDir
    Path dir;

    @Test
    void reappliesOnlyFilesChangedByUpdates() throws Exception {
        byte[] original = new byte[10_000];
        System.arraycopy(ValueFormat.FLOAT32.encode(AspectRatio.STANDARD), 0, original, 500, 4);
        Path updated = dir.resolve("updated.exe");
        Path untouched = dir.resolve("untouched.exe");
        Files.write(updated, original);
        Files.write(untouched, original);

        PatchStore store = new PatchStore(dir.resolve("state.json"));
        Patcher patcher = new Patcher(store);
        AspectRatio target = new AspectRatio(3440, 1440);
        for (Path f : new Path[]{updated, untouched}) {
            patcher.apply("g", "Game", f, target, EnumSet.of(ValueFormat.FLOAT32), ProgressListener.NONE);
        }

        // оновлення гри повертає оригінальний вміст одного з файлів
        Files.write(updated, original);
        Files.setLastModifiedTime(updated, FileTime.fromMillis(System.currentTimeMillis() + 10_000));

        Path log = dir.resolve("reapply.log");
        ReapplyCommand.Result result = new ReapplyCommand(patcher, log).run();

        assertEquals(2, result.checked());
        assertEquals(1, result.reapplied());
        assertEquals(0, result.failed());
        assertFalse(result.needsAttention());
        assertEquals(Patcher.FileState.PATCHED, patcher.quickState(updated));
        assertTrue(Files.readString(log, StandardCharsets.UTF_8).contains("застосовано знову: 1"));
    }
}
