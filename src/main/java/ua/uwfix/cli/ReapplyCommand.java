package ua.uwfix.cli;

import ua.uwfix.i18n.I18n;
import ua.uwfix.i18n.Language;
import ua.uwfix.patch.PatchException;
import ua.uwfix.patch.PatchRecord;
import ua.uwfix.patch.PatchStore;
import ua.uwfix.patch.Patcher;
import ua.uwfix.util.ProgressListener;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

/**
 * Режим без вікна ({@code UWFix.exe --reapply}): знаходить файли, які змінились
 * після патчу (оновлення гри), і застосовує фікс знову з тими самими налаштуваннями.
 * Результат пишеться у {@code %APPDATA%\UWFix\reapply.log}.
 */
public final class ReapplyCommand {

    /** Підсумок перевірки. */
    public record Result(int checked, int reapplied, int failed) {
        public boolean needsAttention() {
            return failed > 0;
        }
    }

    private final Patcher patcher;
    private final Path logFile;

    public ReapplyCommand(Patcher patcher, Path logFile) {
        this.patcher = patcher;
        this.logFile = logFile;
    }

    public static ReapplyCommand createDefault() {
        PatchStore store = PatchStore.openDefault();
        I18n.setLanguage(Language.detect(store.state().language()));
        return new ReapplyCommand(new Patcher(store), PatchStore.defaultHome().resolve("reapply.log"));
    }

    public Result run() {
        List<String> log = new ArrayList<>();
        int checked = 0;
        int reapplied = 0;
        int failed = 0;
        for (PatchRecord record : patcher.store().all()) {
            Path file = Path.of(record.file());
            if (!Files.isRegularFile(file)) {
                continue; // гру видалено або диск відключено
            }
            checked++;
            if (patcher.quickState(file) != Patcher.FileState.CHANGED_AFTER_PATCH) {
                continue;
            }
            try {
                Patcher.PatchOutcome outcome = patcher.reapply(record, ProgressListener.NONE);
                reapplied++;
                log.add(record.gameName() + " / " + file.getFileName() + ": застосовано знову, замін — "
                        + outcome.replaced());
            } catch (PatchException | IOException | RuntimeException e) {
                failed++;
                log.add(record.gameName() + " / " + file.getFileName() + ": не вдалося — " + e.getMessage());
            }
        }
        log.add("Перевірено файлів: " + checked + ", застосовано знову: " + reapplied + ", помилок: " + failed);
        writeLog(log);
        return new Result(checked, reapplied, failed);
    }

    private void writeLog(List<String> lines) {
        try {
            Files.createDirectories(logFile.getParent());
            StringBuilder sb = new StringBuilder();
            String time = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS).toString();
            for (String line : lines) {
                sb.append(time).append("  ").append(line).append(System.lineSeparator());
            }
            Files.writeString(logFile, sb, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException ignored) {
            // журнал не критичний
        }
    }
}
