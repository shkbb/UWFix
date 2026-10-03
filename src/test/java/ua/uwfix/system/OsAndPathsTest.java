package ua.uwfix.system;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ua.uwfix.util.PathsCi;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OsAndPathsTest {

    @TempDir
    Path dir;

    @Test
    void detectsOperatingSystem() {
        assertEquals(Os.WINDOWS, Os.detect("Windows 11"));
        assertEquals(Os.LINUX, Os.detect("Linux"));
        assertEquals(Os.OTHER, Os.detect("Mac OS X"));
    }

    @Test
    void onlyKnownLinkSchemesAreOpened() {
        assertTrue(SystemShell.isAllowedScheme("https://github.com/shkbb/UWFix"));
        assertTrue(SystemShell.isAllowedScheme("steam://rungameid/292030"));
        assertFalse(SystemShell.isAllowedScheme("file:///etc/passwd"));
        assertFalse(SystemShell.isAllowedScheme("http://example.com"));
        assertFalse(SystemShell.isAllowedScheme("cmd.exe /c calc"));
    }

    @Test
    void pathsAreFoundRegardlessOfLetterCase() throws IOException {
        Path real = dir.resolve("Bin64").resolve("CrySystem.dll");
        Files.createDirectories(real.getParent());
        Files.write(real, new byte[0]);

        assertTrue(PathsCi.isFile(dir, "bin64/crysystem.dll"));
        assertTrue(PathsCi.isDirectory(dir, "BIN64"));
        assertEquals(real.toAbsolutePath(), PathsCi.resolve(dir, "bin64/CRYSYSTEM.DLL").toAbsolutePath());
        assertFalse(PathsCi.isFile(dir, "bin64/missing.dll"));
    }
}
