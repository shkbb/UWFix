package ua.uwfix.analysis;

import ua.uwfix.model.Game;
import ua.uwfix.model.GameSource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Шукає ознаки античит-систем. Зміна файлів онлайн-гри з античитом
 * може призвести до блокування акаунта, тому програма попереджає користувача.
 */
public final class AntiCheatDetector {

    /** Мережеві ігри Valve, захищені VAC (окремих файлів античиту в них немає). */
    private static final Map<String, String> VAC_GAMES = Map.of(
            "730", "Counter-Strike 2",
            "570", "Dota 2",
            "440", "Team Fortress 2");

    private AntiCheatDetector() {
    }

    /** @return назва античиту, якщо його знайдено */
    public static Optional<String> detect(Game game) {
        if (game.source() == GameSource.STEAM && VAC_GAMES.containsKey(game.sourceId())) {
            return Optional.of("Valve Anti-Cheat (VAC)");
        }
        try (Stream<Path> files = Files.walk(game.installDir(), 4)) {
            return files
                    .map(p -> p.getFileName() == null ? "" : p.getFileName().toString().toLowerCase(Locale.ROOT))
                    .map(AntiCheatDetector::classify)
                    .filter(name -> name != null)
                    .findFirst();
        } catch (IOException | RuntimeException e) {
            return Optional.empty();
        }
    }

    /** За назвою файлу чи папки повертає назву античиту або {@code null}. */
    static String classify(String lowerName) {
        if (lowerName.startsWith("easyanticheat") || lowerName.equals("start_protected_game.exe")) {
            return "Easy Anti-Cheat";
        }
        if (lowerName.equals("battleye") || lowerName.startsWith("beservice") || lowerName.startsWith("bedaisy")) {
            return "BattlEye";
        }
        if (lowerName.startsWith("eaanticheat")) {
            return "EA Javelin Anti-Cheat";
        }
        if (lowerName.startsWith("pnkbstr")) {
            return "PunkBuster";
        }
        if (lowerName.startsWith("mhyprot")) {
            return "miHoYo Anti-Cheat";
        }
        if (lowerName.startsWith("xigncode")) {
            return "XIGNCODE3";
        }
        if (lowerName.equals("gameguard")) {
            return "nProtect GameGuard";
        }
        return null;
    }
}
