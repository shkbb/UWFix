package ua.uwfix.update;

import ua.uwfix.i18n.I18n;
import ua.uwfix.search.FileScanner;
import ua.uwfix.system.WindowsShell;
import ua.uwfix.util.ProgressListener;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.Base64;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Встановлює нову версію програми.
 * <ol>
 *   <li>завантажує портативний архів з GitHub і перевіряє розмір та SHA-256;</li>
 *   <li>розпаковує його у тимчасову папку (із захистом від «zip slip» — шляхів на кшталт ..\..);</li>
 *   <li>запускає невеликий скрипт PowerShell і закриває програму: скрипт чекає завершення процесу,
 *       копіює нові файли поверх старих (robocopy) і запускає нову версію.</li>
 * </ol>
 * Сама програма замінити себе не може — її файли заблоковані, поки вона працює.
 */
public final class UpdateInstaller {

    public static final String EXE_NAME = "UWFix.exe";

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NORMAL) // github.com перенаправляє на сервер файлів
            .build();

    /**
     * Папка зібраної програми ({@code UWFix.exe}, {@code app\}, {@code runtime\}), якщо програма
     * запущена саме з неї. Під час розробки (запуск через java.exe) повертає порожнє значення —
     * тоді автоматичне оновлення вимкнено, щоб випадково не перезаписати чужі файли.
     */
    public static Optional<Path> currentAppDir() {
        Optional<String> command = ProcessHandle.current().info().command();
        if (command.isEmpty()) {
            return Optional.empty();
        }
        Path exe = Path.of(command.get()).toAbsolutePath();
        if (exe.getFileName() == null || !exe.getFileName().toString().equalsIgnoreCase(EXE_NAME)) {
            return Optional.empty();
        }
        Path dir = exe.getParent();
        return isAppImage(dir) ? Optional.of(dir) : Optional.empty();
    }

    /** Чи схожа папка на зібрану програму jpackage. */
    static boolean isAppImage(Path dir) {
        return dir != null
                && Files.isRegularFile(dir.resolve(EXE_NAME))
                && Files.isRegularFile(dir.resolve("app").resolve("UWFix.cfg"))
                && Files.isDirectory(dir.resolve("runtime"));
    }

    /** Чи можна писати в папку без прав адміністратора. */
    public static boolean canWrite(Path dir) {
        try {
            Path probe = Files.createTempFile(dir, ".uwfix-write-test", ".tmp");
            Files.delete(probe);
            return true;
        } catch (IOException | SecurityException e) {
            return false;
        }
    }

    /**
     * Завантажує, перевіряє і розпаковує нову версію.
     *
     * @return папка нової версії (містить UWFix.exe)
     */
    public Path prepare(ReleaseInfo release, Path workDir, ProgressListener progress) throws UpdateException {
        try {
            deleteRecursively(workDir);
            Files.createDirectories(workDir);
            Path zip = workDir.resolve(release.zipName());
            download(release, zip, progress);
            verify(zip, release);
            progress.update(-1, I18n.t("update.unpacking"));
            Path extracted = workDir.resolve("new");
            unzip(zip, extracted);
            Path app = extracted.resolve("UWFix");
            if (!isAppImage(app)) {
                throw new UpdateException(I18n.t("error.update.badArchive"));
            }
            return app;
        } catch (IOException e) {
            throw new UpdateException(I18n.t("error.update.network", String.valueOf(e.getMessage())), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new UpdateException(I18n.t("error.update.network", "interrupted"), e);
        }
    }

    private void download(ReleaseInfo release, Path target, ProgressListener progress)
            throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create(release.zipUrl()))
                .header("User-Agent", "UWFix-updater")
                .GET()
                .build();
        HttpResponse<InputStream> response = http.send(request, HttpResponse.BodyHandlers.ofInputStream());
        if (response.statusCode() != 200) {
            response.body().close();
            throw new IOException("HTTP " + response.statusCode());
        }
        long total = response.headers().firstValueAsLong("Content-Length").orElse(release.zipSize());
        long done = 0;
        byte[] buffer = new byte[64 * 1024];
        try (InputStream in = response.body(); OutputStream out = Files.newOutputStream(target)) {
            int n;
            while ((n = in.read(buffer)) > 0) {
                progress.checkCancelled();
                out.write(buffer, 0, n);
                done += n;
                if (total > 0) {
                    int percent = (int) (done * 100 / total);
                    progress.update((double) done / total, I18n.t("update.downloading", percent + "%"));
                }
            }
        }
    }

    /** Розмір і SHA-256 мають збігатися з тим, що повідомив GitHub. */
    static void verify(Path zip, ReleaseInfo release) throws IOException, UpdateException {
        if (release.zipSize() > 0 && Files.size(zip) != release.zipSize()) {
            throw new UpdateException(I18n.t("error.update.checksum"));
        }
        if (release.zipSha256() != null && !new FileScanner().sha256(zip).equalsIgnoreCase(release.zipSha256())) {
            throw new UpdateException(I18n.t("error.update.checksum"));
        }
    }

    /** Розпаковує архів, не дозволяючи файлам вийти за межі цільової папки. */
    static void unzip(Path zip, Path target) throws IOException, UpdateException {
        Path root = target.toAbsolutePath().normalize();
        Files.createDirectories(root);
        try (ZipInputStream in = new ZipInputStream(Files.newInputStream(zip))) {
            ZipEntry entry;
            while ((entry = in.getNextEntry()) != null) {
                // Compress-Archive з Windows PowerShell 5.1 пише шляхи через «\», навіть для папок
                String name = entry.getName().replace('\\', '/');
                Path out = root.resolve(name).normalize();
                if (!out.startsWith(root) || out.equals(root)) {
                    throw new UpdateException(I18n.t("error.update.badArchive"));
                }
                if (name.endsWith("/")) {
                    Files.createDirectories(out);
                } else {
                    Files.createDirectories(out.getParent());
                    Files.copy(in, out, StandardCopyOption.REPLACE_EXISTING);
                }
            }
        }
    }

    /**
     * Скрипт PowerShell, що замінює програму новою версією.
     * Папки {@code runtime} і {@code app} цілком належать програмі, тому їх дзеркалить robocopy /MIR
     * (зайві старі файли видаляються); у корені копіюються лише файли програми.
     *
     * @param pid         процес, завершення якого треба дочекатися
     * @param newApp      папка нової версії
     * @param appDir      папка встановленої програми
     * @param workDir     тимчасова папка — видаляється після оновлення
     * @param logFile     журнал оновлення
     * @param restartArgs аргументи для запуску нової версії
     */
    public static String updaterScript(long pid, Path newApp, Path appDir, Path workDir, Path logFile,
                                       List<String> restartArgs) {
        StringBuilder args = new StringBuilder();
        for (String arg : restartArgs) {
            if (args.length() > 0) {
                args.append(',');
            }
            // Start-Process склеює аргументи через пробіл, тому аргументи з пробілами беремо в лапки
            args.append(WindowsShell.psQuote(arg.contains(" ") ? "\"" + arg + "\"" : arg));
        }
        String robocopyFlags = "/R:1 /W:1 /NFL /NDL /NJH /NJS /NP";
        return String.join("\n",
                "$log = " + WindowsShell.psQuote(logFile.toString()),
                "function Log($m) { Add-Content -LiteralPath $log -Encoding UTF8 -Value ((Get-Date -Format s) + '  ' + $m) }",
                "$src = " + WindowsShell.psQuote(newApp.toString()),
                "$dst = " + WindowsShell.psQuote(appDir.toString()),
                "try {",
                "    Wait-Process -Id " + pid + " -Timeout 60 -ErrorAction SilentlyContinue",
                "    $ok = $false",
                "    for ($i = 0; $i -lt 30 -and -not $ok; $i++) {",
                "        robocopy (Join-Path $src 'runtime') (Join-Path $dst 'runtime') /MIR " + robocopyFlags + " | Out-Null; $a = $LASTEXITCODE",
                "        robocopy (Join-Path $src 'app') (Join-Path $dst 'app') /MIR " + robocopyFlags + " | Out-Null; $b = $LASTEXITCODE",
                "        robocopy $src $dst 'UWFix.exe' 'UWFix.ico' " + robocopyFlags + " | Out-Null; $c = $LASTEXITCODE",
                "        if ($a -lt 8 -and $b -lt 8 -and $c -lt 8) { $ok = $true } else { Start-Sleep -Seconds 1 }",
                "    }",
                "    if ($ok) { Log 'update installed' } else { Log 'update failed: files are locked' }",
                "} catch { Log ('update failed: ' + $_) }",
                "Start-Process -FilePath (Join-Path $dst 'UWFix.exe')"
                        + (args.length() > 0 ? " -ArgumentList @(" + args + ")" : ""),
                "Remove-Item -LiteralPath " + WindowsShell.psQuote(workDir.toString())
                        + " -Recurse -Force -ErrorAction SilentlyContinue");
    }

    /**
     * Запускає скрипт оновлення окремим процесом (він переживе закриття програми).
     *
     * @param elevated запустити з правами адміністратора (якщо папка програми захищена від запису)
     */
    public void launch(String script, boolean elevated) throws UpdateException {
        String encoded = Base64.getEncoder().encodeToString(script.getBytes(StandardCharsets.UTF_16LE));
        try {
            if (!elevated) {
                new ProcessBuilder("powershell", "-NoProfile", "-ExecutionPolicy", "Bypass",
                        "-WindowStyle", "Hidden", "-EncodedCommand", encoded).start();
                return;
            }
        } catch (IOException e) {
            throw new UpdateException(I18n.t("error.update.launch", String.valueOf(e.getMessage())), e);
        }
        boolean started = WindowsShell.powershell("Start-Process powershell -Verb RunAs -WindowStyle Hidden"
                + " -ArgumentList @('-NoProfile','-ExecutionPolicy','Bypass','-EncodedCommand','" + encoded + "')");
        if (!started) {
            throw new UpdateException(I18n.t("error.update.launch", "UAC"));
        }
    }

    static void deleteRecursively(Path dir) throws IOException {
        if (!Files.exists(dir)) {
            return;
        }
        try (Stream<Path> walk = Files.walk(dir)) {
            for (Path p : walk.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(p);
            }
        }
    }
}
