package ua.uwfix.ui;

import ua.uwfix.analysis.GameAnalyzer;
import ua.uwfix.patch.PatchStore;
import ua.uwfix.patch.Patcher;
import ua.uwfix.scan.GameLibrary;
import ua.uwfix.update.ReleaseInfo;
import ua.uwfix.update.UpdateChecker;

import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Deque;
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
    private final UpdateChecker updateChecker = new UpdateChecker();
    private final GameIcons icons = new GameIcons();
    private final ExecutorService executor;
    private final LaunchOptions options;
    private final List<String> rawArgs;
    private final Deque<String> pendingActions = new ArrayDeque<>();

    /** Результат перевірки оновлень — зберігається, щоб не питати GitHub знову після зміни мови. */
    private volatile boolean updateChecked;
    private volatile ReleaseInfo availableUpdate;

    private AppContext(PatchStore store, GameLibrary library, LaunchOptions options, List<String> rawArgs) {
        if (options.action() != null) {
            for (String action : options.action().split(",")) {
                if (!action.isBlank()) {
                    pendingActions.add(action.strip());
                }
            }
        }
        this.store = store;
        this.patcher = new Patcher(store);
        this.analyzer = new GameAnalyzer();
        this.library = library;
        this.options = options;
        this.rawArgs = List.copyOf(rawArgs);
        // Один фоновий потік: операції з файлами виконуються строго по черзі
        this.executor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "uwfix-worker");
            t.setDaemon(true);
            return t;
        });
    }

    public static AppContext create(List<String> args) {
        LaunchOptions options = LaunchOptions.parse(args);
        // У демо-режимі лаунчери не опитуються: у списку лише ігри, додані вручну (для знімків екрана)
        GameLibrary library = options.demo() ? new GameLibrary(List.of()) : GameLibrary.withDefaultScanners();
        return new AppContext(PatchStore.openDefault(), library, options, args);
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

    public UpdateChecker updateChecker() {
        return updateChecker;
    }

    /** Іконки ігор — спільні для всіх екземплярів вікна (після зміни мови не вантажаться знову). */
    GameIcons icons() {
        return icons;
    }

    public ExecutorService executor() {
        return executor;
    }

    public LaunchOptions options() {
        return options;
    }

    /** Аргументи командного рядка як є — щоб передати їх новій версії після оновлення. */
    public List<String> rawArgs() {
        return rawArgs;
    }

    /**
     * Черга демо-дій з {@code --action=lang:en,fix}: спільна для всіх екземплярів вікна,
     * щоб після перебудови вікна (зміна мови) решта дій виконалась у новому.
     */
    public Deque<String> pendingActions() {
        return pendingActions;
    }

    public boolean updateChecked() {
        return updateChecked;
    }

    /** Нова версія, якщо вона є (після перевірки). */
    public ReleaseInfo availableUpdate() {
        return availableUpdate;
    }

    void setUpdateResult(ReleaseInfo release) {
        availableUpdate = release;
        updateChecked = true;
    }

    public void shutdown() {
        executor.shutdownNow();
        icons.shutdown();
    }

    /**
     * Параметри командного рядка (демо-режим для знімків екрана в документації).
     *
     * @param snapshot   зберегти знімок вікна у PNG і завершити роботу
     * @param select     після запуску обрати гру, назва якої містить цей текст
     * @param action     перед знімком виконати дії через кому: «fix», «restore», «lang:en», «update»
     * @param renderIcon намалювати іконку програми у PNG і завершити роботу
     * @param demo       не шукати ігри в лаунчерах, показувати лише додані вручну
     * @param updatedTo  програму щойно оновлено до цієї версії (передає скрипт оновлення)
     */
    public record LaunchOptions(Path snapshot, String select, String action, Path renderIcon, boolean demo,
                                String updatedTo) {

        static LaunchOptions parse(List<String> args) {
            Path snapshot = null;
            String select = null;
            String action = null;
            Path renderIcon = null;
            boolean demo = false;
            String updatedTo = null;
            for (String arg : args) {
                if (arg.equals("--demo")) {
                    demo = true;
                } else if (arg.startsWith("--snapshot=")) {
                    snapshot = Path.of(arg.substring("--snapshot=".length()));
                } else if (arg.startsWith("--select=")) {
                    select = arg.substring("--select=".length());
                } else if (arg.startsWith("--action=")) {
                    action = arg.substring("--action=".length());
                } else if (arg.startsWith("--render-icon=")) {
                    renderIcon = Path.of(arg.substring("--render-icon=".length()));
                } else if (arg.startsWith("--updated=")) {
                    updatedTo = arg.substring("--updated=".length());
                }
            }
            return new LaunchOptions(snapshot, select, action, renderIcon, demo, updatedTo);
        }

        /** Чи перевіряти оновлення: у режимі знімків — лише якщо цього просить дія «update». */
        boolean shouldCheckUpdates() {
            return snapshot == null || (action != null && action.contains("update"));
        }
    }
}
