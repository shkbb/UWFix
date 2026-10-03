package ua.uwfix.update;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ua.uwfix.search.FileScanner;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UpdateInstallerTest {

    @TempDir
    Path dir;

    @Test
    void unpacksPortableArchiveIntoAppImage() throws Exception {
        Path zip = zip("update.zip",
                "UWFix\\UWFix.exe", "exe",                    // Compress-Archive пише шляхи через «\»
                "UWFix\\app\\UWFix.cfg", "[Application]",
                "UWFix\\runtime\\legal\\", "",                // і папки теж: «legal\» — це папка, не файл
                "UWFix\\runtime\\legal\\java.base\\LICENSE", "GPL",
                "UWFix/runtime/release", "JAVA_VERSION=17");
        Path target = dir.resolve("new");
        UpdateInstaller.unzip(zip, target);

        assertTrue(UpdateInstaller.isAppImage(target.resolve("UWFix")));
        assertEquals("[Application]", Files.readString(target.resolve("UWFix/app/UWFix.cfg")));
        assertTrue(Files.isDirectory(target.resolve("UWFix/runtime/legal")));
        assertEquals("GPL", Files.readString(target.resolve("UWFix/runtime/legal/java.base/LICENSE")));
    }

    @Test
    void rejectsPathsLeavingTheTargetFolder() throws Exception {
        Path zip = zip("evil.zip", "../../evil.txt", "boom");
        assertThrows(UpdateException.class, () -> UpdateInstaller.unzip(zip, dir.resolve("new")));
        assertFalse(Files.exists(dir.getParent().resolve("evil.txt")));
    }

    @Test
    void verifiesSizeAndChecksum() throws Exception {
        Path zip = zip("ok.zip", "UWFix/UWFix.exe", "exe");
        long size = Files.size(zip);
        String sha = new FileScanner().sha256(zip);
        Version v = Version.parse("1.2.0");

        UpdateInstaller.verify(zip, new ReleaseInfo(v, "", "ok.zip", "", size, sha)); // не кидає
        UpdateInstaller.verify(zip, new ReleaseInfo(v, "", "ok.zip", "", -1, null));  // перевіряти нічим

        assertThrows(UpdateException.class,
                () -> UpdateInstaller.verify(zip, new ReleaseInfo(v, "", "ok.zip", "", size + 1, sha)));
        String wrong = sha.substring(0, 63) + (sha.endsWith("0") ? "1" : "0");
        assertThrows(UpdateException.class,
                () -> UpdateInstaller.verify(zip, new ReleaseInfo(v, "", "ok.zip", "", size, wrong)));
    }

    @Test
    void recognisesAppImageLayout() throws IOException {
        Path app = dir.resolve("UWFix");
        assertFalse(UpdateInstaller.isAppImage(app));
        Files.createDirectories(app.resolve("app"));
        Files.createDirectories(app.resolve("runtime"));
        Files.writeString(app.resolve("UWFix.exe"), "");
        assertFalse(UpdateInstaller.isAppImage(app), "без app\\UWFix.cfg це не зібрана програма");
        Files.writeString(app.resolve("app/UWFix.cfg"), "");
        assertTrue(UpdateInstaller.isAppImage(app));
        assertTrue(UpdateInstaller.canWrite(app));
    }

    @Test
    void scriptWaitsForProcessCopiesFilesAndRestarts() {
        String script = UpdateInstaller.updaterScript(4242, Path.of("C:\\Temp\\new\\UWFix"),
                Path.of("C:\\Games Tools\\UWFix"), Path.of("C:\\Temp"), Path.of("C:\\Users\\me\\UWFix\\update.log"),
                List.of("--demo", "--snapshot=C:\\shots\\a b.png", "--updated=1.2.0"));

        assertTrue(script.contains("Wait-Process -Id 4242"));
        assertTrue(script.contains("$dst = 'C:\\Games Tools\\UWFix'"));
        assertTrue(script.contains("(Join-Path $src 'runtime') (Join-Path $dst 'runtime') /MIR"));
        assertTrue(script.contains("-ArgumentList @('--demo','\"--snapshot=C:\\shots\\a b.png\"','--updated=1.2.0')"));
        assertTrue(script.contains("Remove-Item -LiteralPath 'C:\\Temp'"));
    }

    private Path zip(String name, String... entries) throws IOException {
        Path zip = dir.resolve(name);
        try (OutputStream out = Files.newOutputStream(zip); ZipOutputStream z = new ZipOutputStream(out)) {
            for (int i = 0; i < entries.length; i += 2) {
                z.putNextEntry(new ZipEntry(entries[i]));
                z.write(entries[i + 1].getBytes(StandardCharsets.UTF_8));
                z.closeEntry();
            }
        }
        return zip;
    }
}
