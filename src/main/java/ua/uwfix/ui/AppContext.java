package ua.uwfix.ui;

import ua.uwfix.analysis.GameAnalyzer;
import ua.uwfix.patch.PatchStore;
import ua.uwfix.patch.Patcher;
import ua.uwfix.scan.GameLibrary;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Усі сервіси програми в одному місці (проста «ручна» ін'єкція залежностей):
 * контролер отримує їх через конструктор, а не створює сам.
 */
public final class AppContext {

    private final PatchStore store;
    private final Patcher patcher;
    private final GameAnalyzer analyzer;
    private final GameLibrary library;
    private final ExecutorService executor;
    private final LaunchOptions options;

    private AppContext(PatchStore store, GameLibrary library, LaunchOptions options) {
        this.store = store;
        this.patcher = new Patcher(store);
        this.analyzer = new GameAnalyzer();
        this.library = library;
        this.options = options;
        // Один фоновий потік: операції з файлами виконуються строго по черзі
        this.executor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "uwfix-worker");
            t.setDaemon(true);
            return t;
        });
    }

    public static AppContext create(List<String> args) {
        return new AppContext(PatchStore.openDefault(), GameLibrary.withDefaultScanners(), LaunchOptions.parse(args));
    }

    public PatchStore store() {
        return store;
    }

    public Patcher patcher() {
        return patcher;
    }

    public GameAnalyzer analyzer() {
        return analyzer;
    }

    public GameLibrary library() {
        return library;
    }

    public ExecutorService executor() {
        return executor;
    }

    public LaunchOptions options() {
        return options;
    }

    public void shutdown() {
        executor.shutdownNow();
    }

    /**
     * Параметри командного рядка (демо-режим для знімків екрана в документації).
     *
     * @param snapshot   зберегти знімок вікна у PNG і завершити роботу
     * @param select     після запуску обрати гру, назва якої містить цей текст
     * @param action     перед знімком виконати дію: «fix» або «restore»
     * @param renderIcon намалювати іконку програми у PNG і завершити роботу
     */
    public record LaunchOptions(Path snapshot, String select, String action, Path renderIcon) {

        static LaunchOptions parse(List<String> args) {
            Path snapshot = null;
            String select = null;
            String action = null;
            Path renderIcon = null;
            for (String arg : args) {
                if (arg.startsWith("--snapshot=")) {
                    snapshot = Path.of(arg.substring("--snapshot=".length()));
                } else if (arg.startsWith("--select=")) {
                    select = arg.substring("--select=".length());
                } else if (arg.startsWith("--action=")) {
                    action = arg.substring("--action=".length());
                } else if (arg.startsWith("--render-icon=")) {
                    renderIcon = Path.of(arg.substring("--render-icon=".length()));
                }
            }
            return new LaunchOptions(snapshot, select, action, renderIcon);
        }
    }
}
