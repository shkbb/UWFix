package ua.uwfix.analysis;

import ua.uwfix.util.PathsCi;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

/**
 * Визначає рушій гри за характерними файлами та папками.
 * <p>
 * Перевірки йдуть від найточніших ознак до загальніших:
 * <pre>
 *   Unity          UnityPlayer.dll / UnityPlayer.so (+ GameAssembly — IL2CPP);
 *                  у старих версіях — папка «Гра_Data» з globalgamemanagers
 *   REDengine      bin\x64\ і папка ресурсів content / archive\pc\content
 *   Source 2       game\bin\win64\engine2.dll, у Linux — game/bin/linuxsteamrt64/libengine2.so
 *   Source         bin\engine.dll, у Linux — bin/engine.so, bin/linux64/engine_client.so
 *   RE Engine      re_chunk_000.pak
 *   MT Framework   nativePC / nativeDX10 / nativeDX11x64
 *   Unreal 4/5     Engine\Binaries, *-Win64-Shipping.exe або *-Linux-Shipping
 *   Unreal 3       папка CookedPC*
 *   RAGE           архіви *.rpf у корені (GTA V, Red Dead Redemption 2)
 *   CryEngine      CrySystem.dll у bin64 / bin\win_x64
 *   Creation       Data\*.esm (Skyrim, Fallout)
 *   Frostbite      Data\initfs_Win32
 *   GameMaker      data.win, у Linux — game.unx
 *   Godot          *.pck поруч з однойменною програмою (.exe, .x86_64)
 * </pre>
 * Версії ігор для Windows і Linux зібрані з тих самих ресурсів, тож здебільшого відрізняються
 * лише назви виконуваних файлів.
 */
public final class EngineDetector {

    /** Бібліотека рушія Source: Windows і варіанти для Linux (32- і 64-бітні). */
    private static final List<String> SOURCE_ENGINE_FILES = List.of(
            "bin/engine.dll", "bin/engine.so", "bin/engine_client.so",
            "bin/linux64/engine.so", "bin/linux64/engine_client.so");

    /** Файли, у яких Unity зберігає налаштування проєкту (у різних версіях по-різному). */
    private static final List<String> UNITY_DATA_FILES = List.of("globalgamemanagers", "mainData", "data.unity3d");

    /** Програма Godot поруч з архівом «Гра.pck»: Windows, Linux 64/32 біти, ARM або без розширення. */
    private static final List<String> GODOT_PROGRAM_SUFFIXES = List.of(".exe", ".x86_64", ".x86_32", ".x86", ".arm64", "");

    private EngineDetector() {
    }

    public static Engine detect(Path gameDir) {
        if (isUnity(gameDir)) {
            return isUnityIl2Cpp(gameDir) ? Engine.UNITY_IL2CPP : Engine.UNITY_MONO;
        }
        if (isRedEngine(gameDir)) {
            return Engine.RED_ENGINE;
        }
        if (PathsCi.isFile(gameDir, "game/bin/win64/engine2.dll")
                || PathsCi.isFile(gameDir, "game/bin/linuxsteamrt64/libengine2.so")) {
            return Engine.SOURCE_2;
        }
        if (isSource(gameDir)) {
            return Engine.SOURCE;
        }
        if (PathsCi.isFile(gameDir, "re_chunk_000.pak")) {
            return Engine.RE_ENGINE;
        }
        // nativePC, nativePC_MT (Resident Evil 5), nativeDX10, nativeDX11x64
        if (hasDirectory(gameDir, "nativepc", 1) || hasDirectory(gameDir, "nativedx", 1)) {
            return Engine.MT_FRAMEWORK;
        }
        if (PathsCi.isDirectory(gameDir, "Engine/Binaries") || hasShippingExe(gameDir)
                || unrealProjectFolder(gameDir) != null) {
            return Engine.UNREAL_4_5;
        }
        if (hasDirectory(gameDir, "cookedpc", 3)) {
            return Engine.UNREAL_3;
        }
        if (hasFile(gameDir, ".rpf")) {
            return Engine.RAGE;
        }
        if (PathsCi.isFile(gameDir, "bin64/CrySystem.dll") || PathsCi.isFile(gameDir, "bin/win_x64/CrySystem.dll")) {
            return Engine.CRYENGINE;
        }
        if (hasFile(PathsCi.resolve(gameDir, "Data"), ".esm")) {
            return Engine.CREATION;
        }
        if (PathsCi.isFile(gameDir, "Data/initfs_Win32")) {
            return Engine.FROSTBITE;
        }
        if (PathsCi.isFile(gameDir, "data.win") || PathsCi.isFile(gameDir, "game.unx")
                || PathsCi.isFile(gameDir, "assets/game.unx")) {
            return Engine.GAMEMAKER;
        }
        if (isGodot(gameDir)) {
            return Engine.GODOT;
        }
        return Engine.UNKNOWN;
    }

    /** Рушій Source (Half-Life 2, Portal 2): бібліотека engine у папці bin. */
    public static boolean isSource(Path dir) {
        return SOURCE_ENGINE_FILES.stream().anyMatch(f -> PathsCi.isFile(dir, f));
    }

    /** UnityPlayer.dll / .so; у старих версіях Unity (до 2017) плеєр вбудований у саму програму. */
    private static boolean isUnity(Path dir) {
        return PathsCi.isFile(dir, "UnityPlayer.dll") || PathsCi.isFile(dir, "UnityPlayer.so")
                || unityDataFolder(dir) != null;
    }

    /** IL2CPP: код гри скомпільований у GameAssembly (.dll / .so) або лежить у «Гра_Data/il2cpp_data». */
    private static boolean isUnityIl2Cpp(Path dir) {
        if (PathsCi.isFile(dir, "GameAssembly.dll") || PathsCi.isFile(dir, "GameAssembly.so")) {
            return true;
        }
        Path data = unityDataFolder(dir);
        return data != null && PathsCi.isDirectory(data, "il2cpp_data");
    }

    /** Папка ресурсів Unity «Гра_Data» (у ній globalgamemanagers, mainData або data.unity3d). */
    public static Path unityDataFolder(Path dir) {
        try (DirectoryStream<Path> dirs = Files.newDirectoryStream(dir, "*_Data")) {
            for (Path data : dirs) {
                if (Files.isDirectory(data) && UNITY_DATA_FILES.stream().anyMatch(f -> PathsCi.isFile(data, f))) {
                    return data;
                }
            }
        } catch (IOException e) {
            return null;
        }
        return null;
    }

    /** The Witcher 3 і Cyberpunk 2077: bin\x64\*.exe поруч з папкою ресурсів. */
    private static boolean isRedEngine(Path dir) {
        boolean hasBin = PathsCi.isDirectory(dir, "bin/x64") || PathsCi.isDirectory(dir, "bin/x64_dx12");
        boolean hasContent = PathsCi.isDirectory(dir, "content") || PathsCi.isDirectory(dir, "archive/pc/content");
        return hasBin && hasContent;
    }

    /** Godot зберігає ресурси в «Гра.pck» поруч із програмою «Гра.exe» (у Linux — «Гра.x86_64»). */
    private static boolean isGodot(Path dir) {
        try (DirectoryStream<Path> packs = Files.newDirectoryStream(dir, "*.pck")) {
            for (Path pck : packs) {
                String name = pck.getFileName().toString();
                String base = name.substring(0, name.length() - 4);
                for (String suffix : GODOT_PROGRAM_SUFFIXES) {
                    if (Files.isRegularFile(dir.resolve(base + suffix))) {
                        return true;
                    }
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
                        && PathsCi.isDirectory(dir, "Content/Paks")) {
                    return dir;
                }
            }
        } catch (IOException e) {
            return null;
        }
        return null;
    }

    /** Головний файл ігор на UE4/5: «Назва-Win64-Shipping.exe», у Linux — «Назва-Linux-Shipping». */
    private static boolean hasShippingExe(Path dir) {
        try (Stream<Path> files = Files.walk(dir, 4)) {
            return files.anyMatch(p -> {
                String name = p.getFileName().toString().toLowerCase(Locale.ROOT);
                return name.endsWith("-shipping.exe") || name.matches(".+-linux(arm64)?-shipping");
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

    /** Чи є в папці (без підпапок) файл з таким розширенням, наприклад «.rpf» (регістр не важливий). */
    private static boolean hasFile(Path dir, String extension) {
        if (!Files.isDirectory(dir)) {
            return false;
        }
        try (DirectoryStream<Path> files = Files.newDirectoryStream(dir)) {
            for (Path f : files) {
                if (f.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(extension) && Files.isRegularFile(f)) {
                    return true;
                }
            }
        } catch (IOException e) {
            return false;
        }
        return false;
    }
}
