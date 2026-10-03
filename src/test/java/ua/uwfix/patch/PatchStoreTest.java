package ua.uwfix.patch;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ua.uwfix.system.Os;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PatchStoreTest {

    @TempDir
    Path dir;

    @Test
    void savesAndLoadsSettingsGamesAndPatches() throws Exception {
        Path file = dir.resolve("state.json");
        PatchStore store = new PatchStore(file);
        store.setTarget(3440, 1440);
        store.setIncludeDouble(true);
        store.addManualGame(new ManualGameEntry("Моя гра", "D:\\Games\\My"));
        store.put(record("D:\\Games\\My\\game.exe"));
        store.save();

        PatchStore loaded = new PatchStore(file);
        assertEquals(3440, loaded.state().targetWidth());
        assertEquals(1440, loaded.state().targetHeight());
        assertTrue(loaded.state().includeDouble());
        assertEquals("Моя гра", loaded.state().manualGames().get(0).name());
        PatchRecord r = loaded.find(Path.of("D:\\Games\\My\\game.exe")).orElseThrow();
        assertEquals(List.of("FLOAT32"), r.formats());
        // у Windows регістр літер у шляху не важливий, у Linux — це вже інший файл
        assertEquals(Os.isWindows(), loaded.find(Path.of("d:\\games\\MY\\GAME.EXE")).isPresent());
        assertEquals(new PatchChange(16, "398EE33F", "8EE31840"), r.changes().get(0));
    }

    @Test
    void putReplacesRecordForSameFile() {
        PatchStore store = new PatchStore(dir.resolve("state.json"));
        store.put(record("C:\\a.exe"));
        store.put(record("C:\\a.exe"));
        assertEquals(1, store.all().size());
        store.put(record("C:\\A.EXE"));
        assertEquals(Os.isWindows() ? 1 : 2, store.all().size());
    }

    @Test
    void corruptedFileIsMovedAsideAndStateStartsClean() throws Exception {
        Path file = dir.resolve("state.json");
        Files.writeString(file, "{ зламаний json");
        PatchStore store = new PatchStore(file);
        assertTrue(store.all().isEmpty());
        assertTrue(Files.exists(dir.resolve("state.json.broken")));
    }

    private static PatchRecord record(String file) {
        return new PatchRecord("steam:1", "Game", file, file + Patcher.BACKUP_SUFFIX, 3440, 1440,
                List.of("FLOAT32"), "aa", "bb", 100, 200, "2026-10-03T12:00:00",
                List.of(new PatchChange(16, "398EE33F", "8EE31840")));
    }
}
