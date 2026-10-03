package ua.uwfix.analysis;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.stream.Stream;

/** Визначає рушій гри за характерними файлами та папками. */
public final class EngineDetector {

    private EngineDetector() {
    }

    public static Engine detect(Path gameDir) {
        if (Files.isRegularFile(gameDir.resolve("UnityPlayer.dll"))) {
            return Files.isRegularFile(gameDir.resolve("GameAssembly.dll")) ? Engine.UNITY_IL2CPP : Engine.UNITY_MONO;
        }
        if (isRedEngine(gameDir)) {
            return Engine.RED_ENGINE;
        }
        if (Files.isDirectory(gameDir.resolve("Engine").resolve("Binaries")) || hasShippingExe(gameDir)) {
            return Engine.UNREAL_4_5;
        }
        if (hasDirectory(gameDir, "cookedpc", 3)) {
            return Engine.UNREAL_3;
        }
        return Engine.UNKNOWN;
    }

    /** The Witcher 3 і Cyberpunk 2077: bin\x64\*.exe поруч з папкою ресурсів. */
    private static boolean isRedEngine(Path dir) {
        boolean hasBin = Files.isDirectory(dir.resolve("bin").resolve("x64"))
                || Files.isDirectory(dir.resolve("bin").resolve("x64_dx12"));
        boolean hasContent = Files.isDirectory(dir.resolve("content"))
                || Files.isDirectory(dir.resolve("archive").resolve("pc").resolve("content"));
        return hasBin && hasContent;
    }

    /** Головний файл ігор на UE4/5 називається «Назва-Win64-Shipping.exe». */
    private static boolean hasShippingExe(Path dir) {
        try (Stream<Path> files = Files.walk(dir, 4)) {
            return files.anyMatch(p -> {
                String name = p.getFileName().toString().toLowerCase(Locale.ROOT);
                return name.endsWith("-shipping.exe");
            });
        } catch (IOException | RuntimeException e) {
            return false;
        }
    }

    /** Чи є папка, назва якої починається з {@code prefix} (UE3: CookedPC, CookedPCConsole). */
    private static boolean hasDirectory(Path dir, String prefix, int depth) {
        try (Stream<Path> files = Files.walk(dir, depth)) {
            return files.anyMatch(p -> Files.isDirectory(p)
                    && p.getFileName() != null
                    && p.getFileName().toString().toLowerCase(Locale.ROOT).startsWith(prefix));
        } catch (IOException | RuntimeException e) {
            return false;
        }
    }
}
