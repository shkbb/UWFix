package ua.uwfix.update;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;
import ua.uwfix.search.FileScanner;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.List;
import java.util.Locale;
import java.util.zip.GZIPOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
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
        String script = UpdateInstaller.powershellScript(4242, Path.of("C:\\Temp\\new\\UWFix"),
                Path.of("C:\\Games Tools\\UWFix"), Path.of("C:\\Temp"), Path.of("C:\\Users\\me\\UWFix\\update.log"),
                List.of("--demo", "--snapshot=C:\\shots\\a b.png", "--updated=1.2.0"));

        assertTrue(script.contains("Wait-Process -Id 4242"));
        assertTrue(script.contains("$dst = 'C:\\Games Tools\\UWFix'"));
        assertTrue(script.contains("(Join-Path $src 'runtime') (Join-Path $dst 'runtime') /MIR"));
        assertTrue(script.contains("-ArgumentList @('--demo','\"--snapshot=C:\\shots\\a b.png\"','--updated=1.2.0')"));
        assertTrue(script.contains("Remove-Item -LiteralPath 'C:\\Temp'"));
    }

    @Test
    void unpacksLinuxTarGzWithLongNamesAndModes() throws Exception {
        String longName = "UWFix/lib/runtime/legal/" + "x".repeat(90) + "/LICENSE";
        byte[] big = new byte[70_000]; // більше за кілька блоків по 512 байт
        for (int i = 0; i < big.length; i++) {
            big[i] = (byte) i;
        }
        Path archive = dir.resolve("UWFix-1.7.0-linux-x64.tar.gz");
        try (OutputStream out = new GZIPOutputStream(Files.newOutputStream(archive))) {
            tarEntry(out, "UWFix/", '5', 0755, new byte[0]);
            tarEntry(out, "UWFix/bin/UWFix", '0', 0755, "#!launcher".getBytes(StandardCharsets.UTF_8));
            tarEntry(out, "UWFix/lib/app/UWFix.cfg", '0', 0644, "[Application]".getBytes(StandardCharsets.UTF_8));
            tarEntry(out, "UWFix/lib/runtime/lib/modules", '0', 0644, big);
            // GNU tar: назва довша за 100 символів іде окремим записом «././@LongLink»
            tarEntry(out, "././@LongLink", 'L', 0644, (longName + "\0").getBytes(StandardCharsets.UTF_8));
            tarEntry(out, longName.substring(0, 100), '0', 0644, "GPL".getBytes(StandardCharsets.UTF_8));
            out.write(new byte[1024]); // кінець архіву — два порожні блоки
        }
        Path target = dir.resolve("new");
        TarArchive.extract(archive, target);

        Path app = target.resolve("UWFix");
        assertTrue(UpdateInstaller.isAppImage(app));
        assertArrayEquals(big, Files.readAllBytes(app.resolve("lib/runtime/lib/modules")));
        assertEquals("GPL", Files.readString(target.resolve(longName)));
        if (Files.getFileStore(app).supportsFileAttributeView("posix")) {
            assertTrue(Files.getPosixFilePermissions(app.resolve("bin/UWFix")).contains(PosixFilePermission.OWNER_EXECUTE));
        }
    }

    /** Як у справжньому архіві з jlink: ліцензії модулів — посилання на ліцензію java.base. */
    @Test
    @DisabledOnOs(OS.WINDOWS) // у Windows посилання створюються лише з особливими правами
    void tarRestoresRelativeSymlinksInsideArchive() throws Exception {
        Path archive = dir.resolve("links.tar.gz");
        try (OutputStream out = new GZIPOutputStream(Files.newOutputStream(archive))) {
            tarLink(out, "UWFix/lib/runtime/legal/java.xml/LICENSE", "../java.base/LICENSE");
            tarEntry(out, "UWFix/lib/runtime/legal/java.base/LICENSE", '0', 0644, "GPL".getBytes(StandardCharsets.UTF_8));
            out.write(new byte[1024]);
        }
        Path target = dir.resolve("new");
        TarArchive.extract(archive, target);
        Path link = target.resolve("UWFix/lib/runtime/legal/java.xml/LICENSE");
        assertTrue(Files.isSymbolicLink(link));
        assertEquals("GPL", Files.readString(link));

        Path evil = dir.resolve("evil-link.tar.gz");
        try (OutputStream out = new GZIPOutputStream(Files.newOutputStream(evil))) {
            tarLink(out, "UWFix/passwd", "../../../etc/passwd");
            out.write(new byte[1024]);
        }
        assertThrows(UpdateException.class, () -> TarArchive.extract(evil, dir.resolve("evil")));
    }

    @Test
    void tarRejectsPathsLeavingTheTargetFolder() throws Exception {
        Path archive = dir.resolve("evil.tar.gz");
        try (OutputStream out = new GZIPOutputStream(Files.newOutputStream(archive))) {
            tarEntry(out, "../../evil.txt", '0', 0644, "boom".getBytes(StandardCharsets.UTF_8));
            out.write(new byte[1024]);
        }
        assertThrows(UpdateException.class, () -> TarArchive.extract(archive, dir.resolve("new")));
    }

    @Test
    void findsLinuxAppImageFromLauncher() throws IOException {
        Path app = dir.resolve("opt/UWFix");
        Files.createDirectories(app.resolve("bin"));
        Files.createDirectories(app.resolve("lib/app"));
        Files.createDirectories(app.resolve("lib/runtime"));
        Files.writeString(app.resolve("bin/UWFix"), "");
        Files.writeString(app.resolve("lib/app/UWFix.cfg"), "");

        assertEquals(app, UpdateInstaller.appDirOf(app.resolve("bin/UWFix")).orElseThrow());
        assertTrue(UpdateInstaller.appDirOf(app.resolve("bin/java")).isEmpty());
        assertTrue(UpdateInstaller.appDirOf(dir.resolve("UWFix")).isEmpty());
    }

    @Test
    void shellScriptReplacesLibAtomicallyAndRestarts() {
        Path appDir = Path.of("/home/me/Apps/UW Fix");
        String script = UpdateInstaller.shellScript(4242, Path.of("/tmp/UWFix-update/new/UWFix"),
                appDir, Path.of("/tmp/UWFix-update"), Path.of("/home/me/.config/uwfix/update.log"),
                List.of("--snapshot=/tmp/it's.png", "--updated=1.7.0"));

        assertTrue(script.contains("kill -0 4242"));
        assertTrue(script.contains("dst=" + UpdateInstaller.shQuote(appDir.toString())));
        assertTrue(script.contains("cp -a \"$src/lib\" \"$dst/lib.new\""));
        assertTrue(script.contains("\"$dst/bin/UWFix\" '--snapshot=/tmp/it'\\''s.png' '--updated=1.7.0' >/dev/null 2>&1 &"));
        assertEquals("'it'\\''s'", UpdateInstaller.shQuote("it's"));
    }

    private static void tarLink(OutputStream out, String name, String target) throws IOException {
        tarEntry(out, name, '2', 0777, new byte[0], target);
    }

    private static void tarEntry(OutputStream out, String name, char type, int mode, byte[] data) throws IOException {
        tarEntry(out, name, type, mode, data, "");
    }

    /** Заголовок ustar (512 байт) + вміст, доповнений нулями. */
    private static void tarEntry(OutputStream out, String name, char type, int mode, byte[] data, String linkName)
            throws IOException {
        byte[] h = new byte[512];
        byte[] n = name.getBytes(StandardCharsets.UTF_8);
        System.arraycopy(n, 0, h, 0, Math.min(100, n.length));
        byte[] l = linkName.getBytes(StandardCharsets.UTF_8);
        System.arraycopy(l, 0, h, 157, Math.min(100, l.length));
        putOctal(h, 100, 8, mode);
        putOctal(h, 108, 8, 0);
        putOctal(h, 116, 8, 0);
        putOctal(h, 124, 12, data.length);
        putOctal(h, 136, 12, 0);
        h[156] = (byte) type;
        System.arraycopy("ustar\0".getBytes(StandardCharsets.US_ASCII), 0, h, 257, 6);
        h[263] = '0';
        h[264] = '0';
        for (int i = 148; i < 156; i++) {
            h[i] = ' ';
        }
        int sum = 0;
        for (byte b : h) {
            sum += b & 0xFF;
        }
        putOctal(h, 148, 7, sum);
        out.write(h);
        out.write(data);
        out.write(new byte[(512 - data.length % 512) % 512]);
    }

    private static void putOctal(byte[] h, int offset, int length, long value) {
        String text = String.format(Locale.ROOT, "%0" + (length - 1) + "o", value);
        System.arraycopy(text.getBytes(StandardCharsets.US_ASCII), 0, h, offset, length - 1);
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
