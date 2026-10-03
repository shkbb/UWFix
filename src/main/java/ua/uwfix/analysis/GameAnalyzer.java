package ua.uwfix.analysis;

import ua.uwfix.model.AspectRatio;
import ua.uwfix.model.Game;
import ua.uwfix.model.ValueFormat;
import ua.uwfix.search.FileScanner;
import ua.uwfix.search.ScanResult;
import ua.uwfix.util.ProgressListener;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Аналізує папку гри: знаходить .exe і .dll, рахує в них входження 16:9
 * та обирає файли, які варто патчити.
 */
public final class GameAnalyzer {

    private static final long MIN_SIZE = 64 * 1024L;              // менші файли — дрібні утиліти
    private static final long MAX_SIZE = 2L * 1024 * 1024 * 1024; // більших виконуваних файлів не буває
    private static final int MAX_DEPTH = 8;

    /** Папки, у яких лежать інсталятори бібліотек, античити, звіти про збої тощо. */
    private static final Set<String> SKIP_DIRS = Set.of(
            "_commonredist", "commonredist", "redist", "redistributables", "_redist",
            "__installer", "installer", "installers", "prerequisites", "prereqs",
            "directx", "dxsetup", "vcredist", "dotnet", "support",
            "easyanticheat", "easyanticheat_eos", "battleye",
            "crashreporter", "crashpad", "monobleedingedge", "thirdparty", "extras");

    /** Початки назв сторонніх бібліотек (графіка, звук, Steam, рантайми), які не містять логіки гри. */
    private static final List<String> SKIP_FILE_PREFIXES = List.of(
            "unins", "vc_redist", "vcredist", "dxsetup", "dotnet",
            "steam_api", "steamclient", "steamworks", "eossdk", "galaxy", "discord",
            "d3dcompiler", "d3d12", "dxil", "dxcompiler", "dstorage", "nvngx", "sl.", "nvapi",
            "nvtoolsext", "nvlowlatency", "gfsdk_", "amd_", "ffx_", "libxess", "xess",
            "bink", "physx", "fmod", "aksoundengine", "openal", "sdl2", "oo2core", "oo2net",
            "vcruntime", "msvcp", "concrt", "api-ms-", "ucrtbase", "tbb", "embree",
            "qt5", "qt6", "libcef", "chrome_elf", "cef", "icudt", "icuuc", "icuin",
            "libegl", "libglesv2", "openvr_api", "sentry", "crashpad", "crashreport", "bugsplat",
            "unitycrashhandler", "unrealcefsubprocess", "epicwebhelper", "7za", "d3dconfig",
            "mono-2.0", "system.", "mono.", "unityengine", "unity.", "microsoft.", "newtonsoft",
            "mscorlib", "netstandard", "easyanticheat", "beservice");

    /** Проксі-бібліотеки модів (ReShade, ASI Loader) — це не файли гри. */
    private static final Set<String> SKIP_FILE_NAMES = Set.of(
            "dxgi.dll", "d3d11.dll", "d3d9.dll", "winmm.dll", "version.dll", "dsound.dll", "dinput8.dll");

    /** Частини назв допоміжних програм (лаунчери, утиліти), які не варто рекомендувати. */
    private static final List<String> AUXILIARY_MARKERS = List.of(
            "launcher", "crash", "report", "setup", "install", "helper", "update", "uploader",
            "config", "server", "editor", "benchmark", "tool", "handler", "compiler");

    private final FileScanner scanner;

    public GameAnalyzer() {
        this(new FileScanner());
    }

    public GameAnalyzer(FileScanner scanner) {
        this.scanner = scanner;
    }

    /**
     * @param game         гра
     * @param trackedFiles файли, які вже змінені програмою: показуються завжди,
     *                     навіть якщо 16:9 у них більше не знайдено
     * @param progress     прогрес і скасування
     */
    public GameAnalysis analyze(Game game, Set<Path> trackedFiles, ProgressListener progress) throws IOException {
        Path root = game.installDir();
        progress.update(-1, "Пошук виконуваних файлів…");
        List<Path> files = findBinaries(root);
        Set<Path> tracked = normalize(trackedFiles);
        for (Path t : tracked) {
            if (Files.isRegularFile(t) && !normalize(files).contains(t)) {
                files.add(t);
            }
        }

        long totalBytes = 0;
        for (Path f : files) {
            totalBytes += Files.size(f);
        }

        List<byte[]> patterns = new ArrayList<>();
        for (ValueFormat format : ValueFormat.values()) {
            patterns.add(format.encode(AspectRatio.STANDARD));
        }

        List<BinaryCandidate> candidates = new ArrayList<>();
        long doneBytes = 0;
        for (Path file : files) {
            progress.checkCancelled();
            long size = Files.size(file);
            final long before = doneBytes;
            final long total = Math.max(1, totalBytes);
            String label = "Сканування " + root.relativize(file);
            progress.update((double) before / total, label);
            ScanResult result;
            try {
                result = scanner.scan(file, patterns, new ProgressListener() {
                    @Override
                    public void update(double fraction, String message) {
                        progress.update((before + fraction * size) / total, label);
                    }

                    @Override
                    public boolean isCancelled() {
                        return progress.isCancelled();
                    }
                });
            } catch (IOException e) {
                continue; // файл заблоковано або немає доступу — пропускаємо
            }
            doneBytes += size;

            Map<ValueFormat, Integer> matches = new EnumMap<>(ValueFormat.class);
            ValueFormat[] formats = ValueFormat.values();
            for (int k = 0; k < formats.length; k++) {
                matches.put(formats[k], result.count(k));
            }
            boolean isTracked = tracked.contains(normalize(file));
            if (result.totalMatches() > 0 || isTracked) {
                candidates.add(new BinaryCandidate(file, root.relativize(file), size, result.sha256(),
                        matches, isTracked));
            }
        }

        Engine engine = EngineDetector.detect(root);
        List<BinaryCandidate> withRecommendation = recommend(candidates, engine);
        progress.update(1, "Готово");
        return new GameAnalysis(game, engine, AntiCheatDetector.detect(game), withRecommendation, files.size());
    }

    /** Усі .exe та .dll гри, крім сторонніх бібліотек і службових папок. */
    static List<Path> findBinaries(Path root) throws IOException {
        List<Path> result = new ArrayList<>();
        Files.walkFileTree(root, EnumSet.noneOf(java.nio.file.FileVisitOption.class), MAX_DEPTH,
                new SimpleFileVisitor<>() {
                    @Override
                    public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                        if (!dir.equals(root) && dir.getFileName() != null) {
                            String name = dir.getFileName().toString().toLowerCase(Locale.ROOT);
                            if (SKIP_DIRS.contains(name) || name.startsWith("d3d12")) {
                                return FileVisitResult.SKIP_SUBTREE;
                            }
                        }
                        return FileVisitResult.CONTINUE;
                    }

                    @Override
                    public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                        if (attrs.isRegularFile() && attrs.size() >= MIN_SIZE && attrs.size() <= MAX_SIZE
                                && isInteresting(file.getFileName().toString())) {
                            result.add(file);
                        }
                        return FileVisitResult.CONTINUE;
                    }

                    @Override
                    public FileVisitResult visitFileFailed(Path file, IOException exc) {
                        return FileVisitResult.CONTINUE; // немає доступу — пропускаємо
                    }
                });
        return result;
    }

    static boolean isInteresting(String fileName) {
        String name = fileName.toLowerCase(Locale.ROOT);
        if (!name.endsWith(".exe") && !name.endsWith(".dll")) {
            return false;
        }
        if (SKIP_FILE_NAMES.contains(name)) {
            return false;
        }
        for (String prefix : SKIP_FILE_PREFIXES) {
            if (name.startsWith(prefix)) {
                return false;
            }
        }
        return true;
    }

    static boolean isAuxiliary(String fileName) {
        String name = fileName.toLowerCase(Locale.ROOT);
        for (String marker : AUXILIARY_MARKERS) {
            if (name.contains(marker)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Обирає файли для патчингу:
     * <ol>
     *   <li>головний .exe — найбільший не допоміжний .exe, у якому знайдено 16:9;
     *       також усі .exe з такою самою назвою (наприклад, версії для DX11 і DX12);</li>
     *   <li>для Unity — бібліотеки з кодом гри (GameAssembly.dll, Assembly-CSharp.dll);</li>
     *   <li>якщо нічого не обрано — найбільший файл із збігами.</li>
     * </ol>
     * Файли, які вже змінені програмою, лишаються рекомендованими.
     */
    static List<BinaryCandidate> recommend(List<BinaryCandidate> candidates, Engine engine) {
        Set<Path> chosen = new HashSet<>();
        for (BinaryCandidate c : candidates) {
            if (c.recommended()) {
                chosen.add(c.file());
            }
        }

        BinaryCandidate mainExe = candidates.stream()
                .filter(c -> c.isExe() && c.totalMatches() > 0 && !isAuxiliary(c.fileName()))
                .max(Comparator.comparingLong(BinaryCandidate::size))
                .orElse(null);
        if (mainExe != null) {
            for (BinaryCandidate c : candidates) {
                if (c.isExe() && c.totalMatches() > 0 && c.fileName().equalsIgnoreCase(mainExe.fileName())) {
                    chosen.add(c.file());
                }
            }
        }

        if (engine == Engine.UNITY_IL2CPP || engine == Engine.UNITY_MONO) {
            for (BinaryCandidate c : candidates) {
                String name = c.fileName().toLowerCase(Locale.ROOT);
                if (c.totalMatches() > 0 && (name.equals("gameassembly.dll") || name.equals("assembly-csharp.dll"))) {
                    chosen.add(c.file());
                }
            }
        }

        if (chosen.isEmpty()) {
            candidates.stream()
                    .filter(c -> c.totalMatches() > 0)
                    .max(Comparator.comparingLong(BinaryCandidate::size))
                    .ifPresent(c -> chosen.add(c.file()));
        }

        List<BinaryCandidate> result = new ArrayList<>();
        for (BinaryCandidate c : candidates) {
            result.add(c.withRecommended(chosen.contains(c.file())));
        }
        result.sort(Comparator.comparing((BinaryCandidate c) -> !c.recommended())
                .thenComparing(Comparator.comparingInt(BinaryCandidate::totalMatches).reversed())
                .thenComparing(Comparator.comparingLong(BinaryCandidate::size).reversed()));
        return result;
    }

    private static Set<Path> normalize(Iterable<Path> paths) {
        Set<Path> set = new HashSet<>();
        for (Path p : paths) {
            set.add(normalize(p));
        }
        return set;
    }

    private static Path normalize(Path p) {
        return p.toAbsolutePath().normalize();
    }
}
