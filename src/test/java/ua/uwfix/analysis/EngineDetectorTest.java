package ua.uwfix.analysis;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Кожен рушій — за характерним файлом чи папкою (структури взято зі справжніх ігор). */
class EngineDetectorTest {

    @TempDir
    Path dir;

    @ParameterizedTest(name = "{0}")
    @CsvSource({
            "UNITY_MONO,    UnityPlayer.dll",
            "UNITY_IL2CPP,  UnityPlayer.dll|GameAssembly.dll",
            "RED_ENGINE,    bin/x64/witcher3.exe|content/",
            "SOURCE_2,      game/bin/win64/engine2.dll|game/bin/win64/cs2.exe",
            "SOURCE,        bin/engine.dll|hl2.exe|hl2/gameinfo.txt",
            "RE_ENGINE,     re_chunk_000.pak|re2.exe",
            "MT_FRAMEWORK,  nativePC/|re6.exe",
            "MT_FRAMEWORK,  nativePC_MT/|re5dx9.exe",
            "UNREAL_4_5,    Game/Binaries/Win64/Game-Win64-Shipping.exe",
            "UNREAL_4_5,    OakGame/Content/Paks/|OakGame/Binaries/Win64/Borderlands4.exe",
            "UNREAL_3,      LifeIsStrangeGame/CookedPCConsole/",
            "RAGE,          RDR2.exe|x64a.rpf",
            "CRYENGINE,     bin/win_x64/CrySystem.dll",
            "CREATION,      Data/Skyrim.esm|SkyrimSE.exe",
            "FROSTBITE,     Data/initfs_Win32",
            "GAMEMAKER,     data.win|DELTARUNE.exe",
            "GODOT,         Brotato.pck|Brotato.exe",
            "UNKNOWN,       game.exe"
    })
    void detectsEngineByItsFiles(Engine expected, String layout) throws IOException {
        for (String entry : layout.split("\\|")) {
            Path p = dir.resolve(entry.strip());
            if (entry.strip().endsWith("/")) {
                Files.createDirectories(p);
            } else {
                Files.createDirectories(p.getParent());
                Files.write(p, new byte[0]);
            }
        }
        assertEquals(expected, EngineDetector.detect(dir));
    }
}
