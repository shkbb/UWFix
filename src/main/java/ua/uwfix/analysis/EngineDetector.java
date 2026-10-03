package ua.uwfix.analysis;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.stream.Stream;

/**
 * Визначає рушій гри за характерними файлами та папками.
 * <p>
 * Перевірки йдуть від найточніших ознак до загальніших:
 * <pre>
 *   Unity          UnityPlayer.dll (+ GameAssembly.dll — IL2CPP)
 *   REDengine      bin\x64\ і папка ресурсів content / archive\pc\content
 *   Source 2       game\bin\win64\engine2.dll
 *   Source         bin\engine.dll
 *   RE Engine      re_chunk_000.pak
 *   MT Framework   nativePC / nativeDX10 / nativeDX11x64
 *   Unreal 4/5     Engine\Binaries або *-Win64-Shipping.exe
 *   Unreal 3       папка CookedPC*
 *   RAGE           архіви *.rpf у корені (GTA V, Red Dead Redemption 2)
 *   CryEngine      CrySystem.dll у bin64 / bin\win_x64
 *   Creation       Data\*.esm (Skyrim, Fallout)
 *   Frostbite      Data\initfs_Win32
 *   GameMaker      data.win
 *   Godot          *.pck поруч з однойменним .exe
 * </pre>
 */
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
        if (Files.isRegularFile(gameDir.resolve("game").resolve("bin").resolve("win64").resolve("engine2.dll"))) {
            return Engine.SOURCE_2;
        }
        if (Files.isRegularFile(gameDir.resolve("bin").resolve("engine.dll"))) {
            return Engine.SOURCE;
        }
        if (Files.isRegularFile(gameDir.resolve("re_chunk_000.pak"))) {
            return Engine.RE_ENGINE;
        }
        // nativePC, nativePC_MT (Resident Evil 5), nativeDX10, nativeDX11x64
        if (hasDirectory(gameDir, "nativepc", 1) || hasDirectory(gameDir, "nativedx", 1)) {
            return Engine.MT_FRAMEWORK;
        }
        if (Files.isDirectory(gameDir.resolve("Engine").resolve("Binaries")) || hasShippingExe(gameDir)
                || unrealProjectFolder(gameDir) != null) {
            return Engine.UNREAL_4_5;
        }
        if (hasDirectory(gameDir, "cookedpc", 3)) {
            return Engine.UNREAL_3;
        }
        if (hasFile(gameDir, "*.rpf")) {
            return Engine.RAGE;
        }
        if (Files.isRegularFile(gameDir.resolve("bin64").resolve("CrySystem.dll"))
                || Files.isRegularFile(gameDir.resolve("bin").resolve("win_x64").resolve("CrySystem.dll"))) {
            return Engine.CRYENGINE;
        }
        if (hasFile(gameDir.resolve("Data"), "*.esm")) {
            return Engine.CREATION;
        }
        if (Files.isRegularFile(gameDir.resolve("Data").resolve("initfs_Win32"))) {
            return Engine.FROSTBITE;
        }
        if (Files.isRegularFile(gameDir.resolve("data.win"))) {
            return Engine.GAMEMAKER;
        }
        if (isGodot(gameDir)) {
            return Engine.GODOT;
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

    /** Godot зберігає ресурси в «Гра.pck» поруч із «Гра.exe». */
    private static boolean isGodot(Path dir) {
        try (DirectoryStream<Path> packs = Files.newDirectoryStream(dir, "*.pck")) {
            for (Path pck : packs) {
                String name = pck.getFileName().toString();
                String base = name.substring(0, name.length() - 4);
                if (Files.isRegularFile(dir.resolve(base + ".exe"))) {
                    return true;
                }
            }
        } catch (IOException e) {
            return false;
        }
        return false;
    }

    /**
     * Папка проєкту Unreal Engine 4/5 — та, в якій лежать архіви рушія {@code Content\Paks}
     * (наприклад «OakGame» у Borderlands). Ознака надійна навіть тоді, коли .exe перейменовано.
     */
    public static Path unrealProjectFolder(Path gameDir) {
        try (DirectoryStream<Path> dirs = Files.newDirectoryStream(gameDir, Files::isDirectory)) {
            for (Path dir : dirs) {
                if (!dir.getFileName().toString().equalsIgnoreCase("Engine")
                        && Files.isDirectory(dir.resolve("Content").resolve("Paks"))) {
                    return dir;
                }
            }
        } catch (IOException e) {
            return null;
        }
        return null;
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

    /** Чи є в папці (без підпапок) файл за маскою, наприклад «*.rpf». */
    private static boolean hasFile(Path dir, String glob) {
        if (!Files.isDirectory(dir)) {
            return false;
        }
        try (DirectoryStream<Path> files = Files.newDirectoryStream(dir, glob)) {
            return files.iterator().hasNext();
        } catch (IOException e) {
            return false;
        }
    }
}
