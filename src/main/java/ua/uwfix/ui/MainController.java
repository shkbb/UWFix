package ua.uwfix.ui;

import javafx.animation.KeyFrame;
import javafx.animation.PauseTransition;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.SnapshotParameters;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.TextInputDialog;
import javafx.scene.control.TitledPane;
import javafx.scene.control.Tooltip;
import javafx.scene.control.cell.CheckBoxTableCell;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.transform.Transform;
import javafx.stage.DirectoryChooser;
import javafx.stage.Stage;
import javafx.util.Duration;
import ua.uwfix.App;
import ua.uwfix.analysis.BinaryCandidate;
import ua.uwfix.analysis.GameAnalysis;
import ua.uwfix.model.AspectRatio;
import ua.uwfix.model.Game;
import ua.uwfix.model.GameSource;
import ua.uwfix.model.ValueFormat;
import ua.uwfix.patch.ManualGameEntry;
import ua.uwfix.patch.PatchException;
import ua.uwfix.patch.PatchRecord;
import ua.uwfix.patch.PatchStore;
import ua.uwfix.patch.Patcher;
import ua.uwfix.patch.Patcher.FileState;
import ua.uwfix.system.Autostart;
import ua.uwfix.system.Displays;
import ua.uwfix.system.WindowsShell;
import ua.uwfix.util.Plural;
import ua.uwfix.util.ProgressListener;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Контролер головного вікна (MVC: розмітка — main.fxml, дані та логіка — сервіси з {@link AppContext}).
 * <p>
 * Усі довгі операції (пошук ігор, аналіз, патчинг) виконуються у фоновому потоці
 * через {@link Task}, щоб вікно не зависало; результати повертаються в потік JavaFX.
 */
public final class MainController {

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss");

    private final AppContext context;
    private final Stage stage;
    private final PatchStore store;
    private final Patcher patcher;

    // ---- шапка
    @FXML private ComboBox<RatioOption> ratioCombo;
    // ---- список ігор
    @FXML private TextField searchField;
    @FXML private ListView<Game> gameList;
    @FXML private Button addGameButton;
    @FXML private Button refreshButton;
    @FXML private CheckBox autostartCheck;
    // ---- банер «ігри оновились»
    @FXML private HBox outdatedBanner;
    @FXML private Label outdatedLabel;
    @FXML private Button reapplyAllButton;
    // ---- заглушка, коли гру не обрано
    @FXML private VBox placeholder;
    @FXML private Label placeholderTitle;
    @FXML private Label placeholderText;
    // ---- деталі гри
    @FXML private VBox detailsPane;
    @FXML private Label gameTitle;
    @FXML private Hyperlink gamePath;
    @FXML private FlowPane chips;
    @FXML private HBox statusCard;
    @FXML private Label statusIcon;
    @FXML private Label statusTitle;
    @FXML private Label statusText;
    @FXML private Label engineHint;
    @FXML private TableView<FileRow> fileTable;
    @FXML private CheckBox doubleCheck;
    @FXML private Button fixButton;
    @FXML private Button restoreButton;
    @FXML private VBox progressBox;
    @FXML private Label progressLabel;
    @FXML private ProgressBar progressBar;
    // ---- журнал і рядок стану
    @FXML private TitledPane logPane;
    @FXML private TextArea logArea;
    @FXML private Label gamesCountLabel;
    @FXML private Label monitorLabel;
    @FXML private Label adminLabel;
    @FXML private Label versionLabel;

    private final ObservableList<Game> games = FXCollections.observableArrayList();
    private final FilteredList<Game> filteredGames = new FilteredList<>(games, g -> true);
    private final ObservableList<FileRow> rows = FXCollections.observableArrayList();
    private final Map<String, GameCell.Badge> badges = new HashMap<>();

    private Game currentGame;
    private GameAnalysis currentAnalysis;
    private Task<?> analysisTask;
    private boolean busy;
    private boolean loadingGames;
    private boolean updatingCombo;
    private boolean updatingAutostart;
    private RatioOption lastRatioOption;

    public MainController(AppContext context, Stage stage) {
        this.context = context;
        this.stage = stage;
        this.store = context.store();
        this.patcher = context.patcher();
    }

    // ================================================================== ініціалізація

    @FXML
    private void initialize() {
        setupGameList();
        setupFileTable();

        ratioCombo.getSelectionModel().selectedItemProperty().addListener((o, old, now) -> onRatioChanged(old, now));
        doubleCheck.setSelected(store.state().includeDouble());
        doubleCheck.selectedProperty().addListener((o, a, value) -> {
            store.setIncludeDouble(value);
            saveQuietly();
        });
        searchField.textProperty().addListener((o, a, text) -> {
            String q = text == null ? "" : text.strip().toLowerCase(Locale.ROOT);
            filteredGames.setPredicate(g -> q.isEmpty() || g.name().toLowerCase(Locale.ROOT).contains(q));
        });

        autostartCheck.setTooltip(new Tooltip("Steam і інші лаунчери при оновленні гри замінюють пропатчений файл.\n"
                + "З цією опцією UWFix при вході у Windows тихо перевіряє ігри й застосовує фікс знову."));
        autostartCheck.selectedProperty().addListener((o, was, now) -> onAutostartToggled(now));
        refreshButton.setTooltip(new Tooltip("Оновити список ігор"));
        doubleCheck.setTooltip(new Tooltip("Рідкісний випадок: деякі ігри зберігають 16:9 як 64-бітне число."));
        versionLabel.setText(App.NAME + " " + App.VERSION);
        outdatedBanner.managedProperty().bind(outdatedBanner.visibleProperty());
        progressBox.setVisible(false);
        showPlaceholder("Шукаю встановлені ігри…", "Steam, Epic Games, GOG, Ubisoft Connect");
    }

    /** Викликається після показу вікна. */
    public void onShown() {
        if (context.options().renderIcon() != null) {
            renderIconAndExit(context.options().renderIcon());
            return;
        }
        List<AspectRatio> monitors = Displays.monitors();
        fillRatioCombo(monitors);
        monitorLabel.setText(monitors.isEmpty() ? "Монітор не визначено"
                : "Монітор: " + monitors.get(0) + (monitors.size() > 1 ? " (+" + (monitors.size() - 1) + ")" : ""));
        checkAdminAsync();
        loadGames(context.options().select());
        if (context.options().snapshot() != null) {
            scheduleSnapshot(context.options().snapshot());
        }
    }

    private void setupGameList() {
        gameList.setItems(filteredGames);
        gameList.setCellFactory(list -> new GameCell(
                g -> badges.getOrDefault(g.id(), GameCell.Badge.NONE),
                g -> WindowsShell.reveal(g.installDir()),
                this::removeManualGame));
        gameList.getSelectionModel().selectedItemProperty().addListener((o, old, game) -> onGameSelected(game));
    }

    private void setupFileTable() {
        fileTable.setItems(rows);
        fileTable.setEditable(true);
        fileTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_ALL_COLUMNS);
        fileTable.setPlaceholder(new Label("Файлів з числом 16:9 не знайдено"));

        TableColumn<FileRow, Boolean> selectCol = new TableColumn<>("");
        selectCol.setCellValueFactory(c -> c.getValue().selectedProperty());
        selectCol.setCellFactory(CheckBoxTableCell.forTableColumn(selectCol));
        selectCol.setEditable(true);
        selectCol.setSortable(false);
        fixedWidth(selectCol, 40);

        TableColumn<FileRow, String> fileCol = new TableColumn<>("Файл");
        fileCol.setCellValueFactory(c -> new javafx.beans.property.SimpleStringProperty(
                c.getValue().candidate().relative().toString()));
        fileCol.setPrefWidth(380);
        fileCol.setMinWidth(180);

        TableColumn<FileRow, String> sizeCol = new TableColumn<>("Розмір");
        sizeCol.setCellValueFactory(c -> new javafx.beans.property.SimpleStringProperty(
                formatSize(c.getValue().candidate().size())));
        fixedWidth(sizeCol, 92);
        sizeCol.setStyle("-fx-alignment: CENTER-RIGHT;");

        TableColumn<FileRow, String> matchesCol = new TableColumn<>("Знайдено 16:9");
        matchesCol.setCellValueFactory(c -> new javafx.beans.property.SimpleStringProperty(matchesText(c.getValue())));
        fixedWidth(matchesCol, 130);
        matchesCol.setStyle("-fx-alignment: CENTER;");

        TableColumn<FileRow, FileRow> stateCol = new TableColumn<>("Стан");
        stateCol.setCellValueFactory(c -> new javafx.beans.property.SimpleObjectProperty<>(c.getValue()));
        stateCol.setCellFactory(col -> new StateCell());
        stateCol.setPrefWidth(200);
        stateCol.setMinWidth(170);

        fileTable.getColumns().setAll(List.of(selectCol, fileCol, sizeCol, matchesCol, stateCol));
    }

    private static void fixedWidth(TableColumn<?, ?> col, double width) {
        col.setPrefWidth(width);
        col.setMinWidth(width);
        col.setMaxWidth(width);
    }

    // ================================================================== співвідношення сторін

    private void fillRatioCombo(List<AspectRatio> monitors) {
        List<RatioOption> items = new ArrayList<>();
        Set<AspectRatio> seen = new HashSet<>();
        for (int i = 0; i < monitors.size(); i++) {
            if (seen.add(monitors.get(i))) {
                items.add(RatioOption.monitor(i + 1, monitors.get(i)));
            }
        }
        for (AspectRatio preset : AspectRatio.PRESETS) {
            if (seen.add(preset)) {
                items.add(RatioOption.preset(preset));
            }
        }

        RatioOption selected = null;
        Integer w = store.state().targetWidth();
        Integer h = store.state().targetHeight();
        if (w != null && h != null && w > 0 && h > 0) {
            AspectRatio saved = new AspectRatio(w, h);
            selected = items.stream().filter(o -> saved.equals(o.ratio())).findFirst().orElse(null);
            if (selected == null) {
                selected = RatioOption.own(saved);
                items.add(selected);
            }
        }
        items.add(RatioOption.other());
        if (selected == null) {
            selected = items.get(0);
        }

        updatingCombo = true;
        ratioCombo.getItems().setAll(items);
        ratioCombo.getSelectionModel().select(selected);
        lastRatioOption = selected;
        updatingCombo = false;
    }

    private void onRatioChanged(RatioOption old, RatioOption now) {
        if (updatingCombo || now == null) {
            return;
        }
        if (now.custom()) {
            AspectRatio initial = lastRatioOption != null ? lastRatioOption.ratio() : null;
            Platform.runLater(() -> {
                Optional<AspectRatio> chosen = Dialogs.askResolution(stage, initial);
                updatingCombo = true;
                if (chosen.isPresent()) {
                    AspectRatio r = chosen.get();
                    RatioOption existing = ratioCombo.getItems().stream()
                            .filter(o -> r.equals(o.ratio())).findFirst().orElse(null);
                    if (existing == null) {
                        existing = RatioOption.own(r);
                        ratioCombo.getItems().add(ratioCombo.getItems().size() - 1, existing);
                    }
                    ratioCombo.getSelectionModel().select(existing);
                    lastRatioOption = existing;
                    persistRatio(r);
                } else {
                    ratioCombo.getSelectionModel().select(lastRatioOption);
                }
                updatingCombo = false;
                updateStatus();
            });
            return;
        }
        lastRatioOption = now;
        persistRatio(now.ratio());
        updateStatus();
    }

    private void persistRatio(AspectRatio ratio) {
        store.setTarget(ratio.width(), ratio.height());
        saveQuietly();
        log("Цільова роздільна здатність: " + ratio + ", значення " + ratio.valueText());
    }

    private AspectRatio targetRatio() {
        RatioOption option = ratioCombo.getSelectionModel().getSelectedItem();
        return option == null ? null : option.ratio();
    }

    // ================================================================== список ігор

    private void loadGames(String selectName) {
        loadingGames = true;
        gamesCountLabel.setText("Пошук ігор…");
        List<Game> manual = manualGames();
        Task<List<Game>> task = new Task<>() {
            @Override
            protected List<Game> call() {
                return context.library().scanAll(manual, MainController.this::log);
            }
        };
        task.setOnSucceeded(e -> {
            loadingGames = false;
            String keepId = currentGame != null ? currentGame.id() : null;
            games.setAll(task.getValue());
            refreshBadges();
            gamesCountLabel.setText("Ігор знайдено: " + games.size());
            log("Список ігор оновлено: " + games.size());

            Game toSelect = null;
            for (Game g : games) {
                if ((selectName != null && g.name().toLowerCase(Locale.ROOT).contains(selectName.toLowerCase(Locale.ROOT)))
                        || (selectName == null && g.id().equals(keepId))) {
                    toSelect = g;
                    break;
                }
            }
            if (toSelect != null) {
                gameList.getSelectionModel().select(toSelect);
                gameList.scrollTo(toSelect);
            } else if (games.isEmpty()) {
                showPlaceholder("Ігор не знайдено",
                        "Додай гру вручну кнопкою «＋ Додати вручну» — достатньо вказати її папку.");
            } else {
                showPlaceholder("Обери гру зі списку зліва",
                        "UWFix знайде у файлах гри співвідношення 16:9 і замінить його на співвідношення твого монітора.");
            }
        });
        task.setOnFailed(e -> {
            loadingGames = false;
            gamesCountLabel.setText("Помилка пошуку ігор");
            log("Помилка пошуку ігор: " + task.getException());
        });
        context.executor().submit(task);
    }

    private List<Game> manualGames() {
        List<Game> list = new ArrayList<>();
        for (ManualGameEntry entry : store.state().manualGames()) {
            Path dir = Path.of(entry.path());
            if (Files.isDirectory(dir)) {
                list.add(new Game(GameSource.MANUAL, dir.toString().toLowerCase(Locale.ROOT), entry.name(), dir));
            }
        }
        return list;
    }

    @FXML
    private void onRefresh() {
        if (!busy) {
            loadGames(null);
        }
    }

    @FXML
    private void onAddGame() {
        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle("Обери папку, куди встановлено гру");
        File dir = chooser.showDialog(stage);
        if (dir == null) {
            return;
        }
        TextInputDialog nameDialog = new TextInputDialog(dir.getName());
        nameDialog.initOwner(stage);
        nameDialog.setTitle("Додати гру");
        nameDialog.setHeaderText("Як назвати гру у списку?");
        nameDialog.setContentText("Назва:");
        Dialogs.style(nameDialog.getDialogPane());
        Optional<String> name = nameDialog.showAndWait().map(String::strip).filter(s -> !s.isEmpty());
        if (name.isEmpty()) {
            return;
        }
        store.addManualGame(new ManualGameEntry(name.get(), dir.getAbsolutePath()));
        saveQuietly();
        log("Додано гру вручну: " + name.get() + " (" + dir + ")");
        loadGames(name.get());
    }

    private void removeManualGame(Game game) {
        store.removeManualGame(game.installDir());
        saveQuietly();
        log("Прибрано зі списку: " + game.name());
        if (currentGame != null && currentGame.id().equals(game.id())) {
            currentGame = null;
        }
        loadGames(null);
    }

    /** Позначки біля ігор у списку і банер «гра оновилась». */
    private void refreshBadges() {
        badges.clear();
        Map<String, List<PatchRecord>> byGame = new LinkedHashMap<>();
        for (PatchRecord r : store.all()) {
            byGame.computeIfAbsent(r.gameId(), k -> new ArrayList<>()).add(r);
        }
        int outdated = 0;
        for (Map.Entry<String, List<PatchRecord>> e : byGame.entrySet()) {
            boolean changed = e.getValue().stream().anyMatch(this::isOutdated);
            badges.put(e.getKey(), changed ? GameCell.Badge.OUTDATED : GameCell.Badge.PATCHED);
            if (changed) {
                outdated++;
            }
        }
        outdatedBanner.setVisible(outdated > 0);
        outdatedLabel.setText(outdated == 1
                ? "Одна гра оновилась, і фікс злетів. Застосувати його знову?"
                : "Ігор оновилось: " + outdated + ". Фікс злетів — застосувати знову?");
        gameList.refresh();
    }

    private boolean isOutdated(PatchRecord r) {
        Path file = Path.of(r.file());
        return Files.isRegularFile(file) && patcher.quickState(file) == FileState.CHANGED_AFTER_PATCH;
    }

    // ================================================================== обрана гра

    private void onGameSelected(Game game) {
        if (game == null) {
            if (!busy) {
                currentGame = null;
                showPlaceholder("Обери гру зі списку зліва", "");
            }
            return;
        }
        currentGame = game;
        currentAnalysis = null;
        rows.clear();
        placeholder.setVisible(false);
        detailsPane.setVisible(true);
        gameTitle.setText(game.name());
        gamePath.setText(game.installDir().toString());
        chips.getChildren().setAll(chip(game.source().displayName(), "chip"));
        engineHint.setText("");
        setStatus("…", "status-idle", "Аналіз файлів гри…", "Шукаю виконувані файли та число 16:9 у них.");
        analyze(game);
    }

    private void analyze(Game game) {
        if (analysisTask != null && analysisTask.isRunning()) {
            analysisTask.cancel();
        }
        Set<Path> tracked = new HashSet<>();
        for (PatchRecord r : store.forGame(game.id())) {
            tracked.add(Path.of(r.file()));
        }
        Task<GameAnalysis> task = backgroundTask(listener -> context.analyzer().analyze(game, tracked, listener));
        analysisTask = task;
        showProgress(task);
        task.setOnSucceeded(e -> {
            hideProgress();
            if (game.equals(currentGame)) {
                showAnalysis(task.getValue());
            }
        });
        task.setOnFailed(e -> {
            hideProgress();
            if (game.equals(currentGame)) {
                Throwable ex = task.getException();
                setStatus("!", "status-error", "Не вдалося проаналізувати гру", String.valueOf(ex.getMessage()));
                log("Помилка аналізу " + game.name() + ": " + ex);
            }
        });
        task.setOnCancelled(e -> hideProgress());
        context.executor().submit(task);
    }

    private void showAnalysis(GameAnalysis analysis) {
        currentAnalysis = analysis;
        chips.getChildren().setAll(
                chip(analysis.game().source().displayName(), "chip"),
                chip("Рушій: " + analysis.engine().displayName(), "chip"));
        analysis.antiCheat().ifPresent(ac -> chips.getChildren().add(chip("⚠ Античит: " + ac, "chip-danger")));
        engineHint.setText(analysis.engine().hint());

        List<FileRow> newRows = new ArrayList<>();
        for (BinaryCandidate c : analysis.candidates()) {
            PatchRecord record = store.find(c.file()).orElse(null);
            FileState state = record == null ? FileState.ORIGINAL : patcher.state(c.file(), c.sha256());
            FileRow row = new FileRow(c, state, record);
            row.selectedProperty().addListener((o, a, b) -> updateStatus());
            newRows.add(row);
        }
        rows.setAll(newRows);

        int found = analysis.candidates().stream().mapToInt(BinaryCandidate::totalMatches).sum();
        log(analysis.game().name() + ": переглянуто файлів — " + analysis.scannedFiles()
                + ", знайдено 16:9 — " + found + ", рушій — " + analysis.engine().displayName());
        updateStatus();
    }

    /** Оновлює картку стану та доступність кнопок. */
    private void updateStatus() {
        if (currentAnalysis == null) {
            fixButton.setDisable(true);
            restoreButton.setDisable(true);
            return;
        }
        AspectRatio target = targetRatio();
        List<FileRow> patched = rows.stream().filter(r -> r.state() == FileState.PATCHED).toList();
        List<FileRow> changed = rows.stream().filter(r -> r.state() == FileState.CHANGED_AFTER_PATCH).toList();
        int matches = rows.stream().filter(r -> r.state() == FileState.ORIGINAL)
                .mapToInt(r -> r.candidate().totalMatches()).sum();
        long selectedCount = rows.stream().filter(FileRow::isSelected).count();

        if (rows.isEmpty()) {
            setStatus("∅", "status-idle", "Число 16:9 не знайдено",
                    "У файлах гри немає 16:9 у стандартному вигляді. Можливо, гра вже підтримує твій монітор "
                            + "або зберігає співвідношення інакше — тоді цей метод не допоможе.");
        } else if (!changed.isEmpty()) {
            setStatus("⟳", "status-warn", "Гра оновилась — фікс злетів",
                    "Файли змінились після патчу (оновлення гри або перевірка цілісності). "
                            + "Натисни «Застосувати знову».");
        } else if (!patched.isEmpty()) {
            PatchRecord r = patched.get(0).record();
            int count = patched.stream().mapToInt(p -> p.record().changes().size()).sum();
            setStatus("✓", "status-ok", "Виправлено для " + r.ratio().resolutionText(),
                    "Замінено " + Plural.of(count, "значення", "значення", "значень") + " у "
                            + filesLocative(patched.size()) + ". Резервні копії лежать поруч з оригіналами (*"
                            + Patcher.BACKUP_SUFFIX + ").");
        } else {
            setStatus("○", "status-idle", "Ще не виправлено",
                    "16:9 знайдено " + Plural.of(matches, "раз", "рази", "разів") + " у "
                            + filesLocative(rows.size()) + ". Рекомендовані файли вже позначено.");
        }
        if (target != null && target.isStandard()) {
            statusText.setText(statusText.getText() + "\nОбрано 16:9 — змінювати нічого. Обери вгорі роздільну здатність свого монітора.");
        }

        List<FileRow> selectedRows = rows.stream().filter(FileRow::isSelected).toList();
        boolean alreadyDone = !selectedRows.isEmpty() && selectedRows.stream().allMatch(r ->
                r.state() == FileState.PATCHED && r.record().ratio().equals(target));
        boolean otherRatio = selectedRows.stream().anyMatch(r ->
                r.state() == FileState.PATCHED && !r.record().ratio().equals(target));
        if (!changed.isEmpty()) {
            fixButton.setText("Застосувати знову");
        } else if (alreadyDone) {
            fixButton.setText("✓ Уже виправлено");
        } else if (otherRatio && target != null) {
            fixButton.setText("Перевиправити під " + target.resolutionText());
        } else {
            fixButton.setText("Виправити катсцени");
        }
        fixButton.setDisable(busy || selectedCount == 0 || target == null || target.isStandard() || alreadyDone);
        restoreButton.setDisable(busy || rows.stream().noneMatch(r -> r.record() != null));
    }

    // ================================================================== дії

    @FXML
    private void onFix() {
        Game game = currentGame;
        AspectRatio target = targetRatio();
        if (game == null || currentAnalysis == null || target == null) {
            return;
        }
        List<FileRow> selected = rows.stream().filter(FileRow::isSelected).toList();
        if (selected.isEmpty()) {
            Dialogs.info(stage, "Не обрано жодного файлу", "Познач у таблиці файли, які треба змінити.");
            return;
        }
        Optional<String> antiCheat = currentAnalysis.antiCheat();
        if (antiCheat.isPresent() && !Dialogs.confirm(stage, "У грі є античит: " + antiCheat.get(),
                "Зміна файлів онлайн-гри з античитом може призвести до блокування акаунта.\n"
                        + "Використовуй UWFix лише для одиночних ігор.\n\nВсе одно продовжити?",
                "Так, я розумію ризик")) {
            return;
        }
        for (FileRow row : selected) {
            if (row.candidate().isExe() && WindowsShell.isRunning(row.candidate().file())) {
                Dialogs.error(stage, "Гра запущена", "Закрий " + row.candidate().fileName()
                        + " і спробуй ще раз: запущений файл змінювати не можна.");
                return;
            }
        }

        Set<ValueFormat> formats = doubleCheck.isSelected()
                ? EnumSet.allOf(ValueFormat.class) : EnumSet.of(ValueFormat.FLOAT32);
        List<Path> files = selected.stream().map(r -> r.candidate().file()).toList();
        log("Виправлення " + game.name() + " для " + target + " (" + target.valueText() + ")…");

        runBusy(listener -> {
            List<String> report = new ArrayList<>();
            for (int i = 0; i < files.size(); i++) {
                Path file = files.get(i);
                Patcher.PatchOutcome outcome = patcher.apply(game.id(), game.name(), file, target, formats,
                        part(listener, i, files.size()));
                report.add(outcome.nothingFound()
                        ? file.getFileName() + ": 16:9 не знайдено, файл не змінено"
                        : file.getFileName() + ": замінено значень — " + outcome.replaced());
            }
            return report;
        }, report -> {
            report.forEach(this::log);
            refreshBadges();
            analyze(game);
        }, game);
    }

    @FXML
    private void onRestore() {
        Game game = currentGame;
        if (game == null) {
            return;
        }
        List<Path> files = rows.stream().filter(r -> r.record() != null).map(r -> r.candidate().file()).toList();
        if (files.isEmpty()) {
            return;
        }
        for (Path f : files) {
            if (f.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".exe") && WindowsShell.isRunning(f)) {
                Dialogs.error(stage, "Гра запущена", "Закрий " + f.getFileName() + " і спробуй ще раз.");
                return;
            }
        }
        log("Відновлення оригіналу " + game.name() + "…");
        runBusy(listener -> {
            List<String> report = new ArrayList<>();
            for (int i = 0; i < files.size(); i++) {
                Path file = files.get(i);
                Patcher.RestoreOutcome outcome = patcher.restore(file, part(listener, i, files.size()));
                report.add(file.getFileName() + ": " + switch (outcome) {
                    case RESTORED -> "оригінал відновлено";
                    case ALREADY_ORIGINAL -> "файл уже був оригінальним";
                    case REPLACED_BY_UPDATE -> "файл замінило оновлення гри, патчу в ньому вже немає";
                });
            }
            return report;
        }, report -> {
            report.forEach(this::log);
            refreshBadges();
            analyze(game);
        }, game);
    }

    @FXML
    private void onReapplyAll() {
        List<PatchRecord> outdated = store.all().stream().filter(this::isOutdated).toList();
        if (outdated.isEmpty()) {
            refreshBadges();
            return;
        }
        for (PatchRecord r : outdated) {
            Path f = Path.of(r.file());
            if (f.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".exe") && WindowsShell.isRunning(f)) {
                Dialogs.error(stage, "Гра запущена", "Закрий " + r.gameName() + " і спробуй ще раз.");
                return;
            }
        }
        log("Повторне застосування фіксу для оновлених ігор: " + outdated.size() + "…");
        Game game = currentGame;
        runBusy(listener -> {
            List<String> report = new ArrayList<>();
            for (int i = 0; i < outdated.size(); i++) {
                PatchRecord r = outdated.get(i);
                Patcher.PatchOutcome outcome = patcher.reapply(r, part(listener, i, outdated.size()));
                report.add(r.gameName() + " / " + Path.of(r.file()).getFileName() + ": "
                        + (outcome.nothingFound() ? "16:9 більше не знайдено" : "замінено значень — " + outcome.replaced()));
            }
            return report;
        }, report -> {
            report.forEach(this::log);
            refreshBadges();
            if (game != null) {
                analyze(game);
            }
        }, game);
    }

    @FXML
    private void onOpenFolder() {
        if (currentGame != null) {
            WindowsShell.reveal(currentGame.installDir());
        }
    }

    // ================================================================== фонові операції

    @FunctionalInterface
    private interface Work<T> {
        T run(ProgressListener listener) throws Exception;
    }

    /**
     * Фонове завдання JavaFX, яке саме є {@link ProgressListener}: прогрес сервісів
     * потрапляє у властивості Task, а скасування Task видно сервісам.
     */
    private static final class WorkTask<T> extends Task<T> implements ProgressListener {
        private final Work<T> work;

        WorkTask(Work<T> work) {
            this.work = work;
        }

        @Override
        protected T call() throws Exception {
            return work.run(this);
        }

        @Override
        public void update(double fraction, String message) {
            updateProgress(fraction < 0 ? -1 : fraction, 1.0);
            if (message != null) {
                updateMessage(message);
            }
        }
    }

    private <T> Task<T> backgroundTask(Work<T> work) {
        return new WorkTask<>(work);
    }

    /** Виконує зміну файлів: блокує інтерфейс, показує прогрес, обробляє помилки. */
    private <T> void runBusy(Work<T> work, Consumer<T> onSuccess, Game gameToRefresh) {
        if (analysisTask != null && analysisTask.isRunning()) {
            analysisTask.cancel();
        }
        Task<T> task = backgroundTask(work);
        setBusy(true);
        showProgress(task);
        task.setOnSucceeded(e -> {
            hideProgress();
            setBusy(false);
            onSuccess.accept(task.getValue());
        });
        task.setOnFailed(e -> {
            hideProgress();
            setBusy(false);
            handleError(task.getException());
            refreshBadges();
            if (gameToRefresh != null) {
                analyze(gameToRefresh);
            }
        });
        context.executor().submit(task);
    }

    private void handleError(Throwable ex) {
        if (ex instanceof PatchException pe) {
            log("Помилка: " + pe.getMessage());
            switch (pe.kind()) {
                case NEED_ADMIN -> offerElevation(pe.getMessage());
                case FILE_IN_USE -> Dialogs.error(stage, "Файл зайнятий", pe.getMessage());
                case NOTHING_TO_DO -> Dialogs.info(stage, "Змінювати нічого", pe.getMessage());
                default -> Dialogs.error(stage, "Операцію зупинено", pe.getMessage());
            }
        } else if (ex instanceof IOException io) {
            log("Помилка вводу-виводу: " + io);
            Dialogs.error(stage, "Помилка роботи з файлом", String.valueOf(io.getMessage()));
        } else {
            log("Неочікувана помилка: " + ex);
            Dialogs.error(stage, "Неочікувана помилка", String.valueOf(ex));
        }
    }

    private void offerElevation(String message) {
        if (Dialogs.confirm(stage, "Потрібні права адміністратора",
                message + "\n\nІгри в Program Files можна змінювати лише з правами адміністратора. "
                        + "Перезапустити UWFix від імені адміністратора?", "Перезапустити")) {
            if (WindowsShell.relaunchElevated()) {
                Platform.exit();
            } else {
                Dialogs.error(stage, "Не вдалося перезапустити",
                        "Запусти UWFix вручну: правою кнопкою → «Запуск від імені адміністратора».");
            }
        }
    }

    /** Прогрес i-ї з n однакових підзадач. */
    private static ProgressListener part(ProgressListener parent, int index, int count) {
        return new ProgressListener() {
            @Override
            public void update(double fraction, String message) {
                parent.update((index + Math.max(0, fraction)) / count, message);
            }

            @Override
            public boolean isCancelled() {
                return parent.isCancelled();
            }
        };
    }

    private void setBusy(boolean value) {
        busy = value;
        gameList.setDisable(value);
        searchField.setDisable(value);
        addGameButton.setDisable(value);
        refreshButton.setDisable(value);
        ratioCombo.setDisable(value);
        reapplyAllButton.setDisable(value);
        fileTable.setDisable(value);
        doubleCheck.setDisable(value);
        updateStatus();
    }

    private void showProgress(Task<?> task) {
        progressBox.setVisible(true);
        progressBar.progressProperty().bind(task.progressProperty());
        progressLabel.textProperty().bind(task.messageProperty());
    }

    private void hideProgress() {
        progressBar.progressProperty().unbind();
        progressLabel.textProperty().unbind();
        progressBox.setVisible(false);
    }

    /** Права адміністратора та стан автозапуску перевіряються у фоні — це виклики системних утиліт. */
    private void checkAdminAsync() {
        Task<boolean[]> task = new Task<>() {
            @Override
            protected boolean[] call() {
                return new boolean[]{WindowsShell.isElevated(), Autostart.isEnabled()};
            }
        };
        task.setOnSucceeded(e -> {
            adminLabel.setText(task.getValue()[0] ? "Права адміністратора: так" : "Права адміністратора: ні");
            updatingAutostart = true;
            autostartCheck.setSelected(task.getValue()[1]);
            updatingAutostart = false;
        });
        Thread t = new Thread(task, "uwfix-admin-check");
        t.setDaemon(true);
        t.start();
    }

    private void onAutostartToggled(boolean enable) {
        if (updatingAutostart) {
            return;
        }
        boolean ok = enable ? Autostart.enable() : Autostart.disable();
        if (ok) {
            log(enable ? "Автоперевірку при вході у Windows увімкнено" : "Автоперевірку вимкнено");
        } else {
            log("Не вдалося змінити автозапуск");
            updatingAutostart = true;
            autostartCheck.setSelected(!enable);
            updatingAutostart = false;
        }
    }

    // ================================================================== допоміжне для інтерфейсу

    private void showPlaceholder(String title, String text) {
        detailsPane.setVisible(false);
        placeholder.setVisible(true);
        placeholderTitle.setText(title);
        placeholderText.setText(text);
    }

    private void setStatus(String icon, String styleClass, String title, String text) {
        statusIcon.setText(icon);
        statusCard.getStyleClass().removeAll("status-ok", "status-warn", "status-idle", "status-error");
        statusCard.getStyleClass().add(styleClass);
        statusTitle.setText(title);
        statusText.setText(text);
    }

    private static Label chip(String text, String styleClass) {
        Label label = new Label(text);
        label.getStyleClass().add(styleClass);
        return label;
    }

    /** «1 файлі», «3 файлах» — місцевий відмінок після прийменника «у». */
    private static String filesLocative(int n) {
        return n + (n % 10 == 1 && n % 100 != 11 ? " файлі" : " файлах");
    }

    private static String matchesText(FileRow row) {
        if (row.state() == FileState.PATCHED && row.record() != null) {
            return "замінено " + row.record().changes().size();
        }
        int f = row.candidate().matches(ValueFormat.FLOAT32);
        int d = row.candidate().matches(ValueFormat.FLOAT64);
        if (f == 0 && d == 0) {
            return "—";
        }
        return d == 0 ? String.valueOf(f) : f + " (+" + d + " double)";
    }

    static String formatSize(long bytes) {
        if (bytes >= 1024L * 1024) {
            return String.format(Locale.ROOT, "%.1f МБ", bytes / (1024.0 * 1024));
        }
        return String.format(Locale.ROOT, "%d КБ", Math.max(1, bytes / 1024));
    }

    private void log(String message) {
        String line = LocalTime.now().format(TIME) + "  " + message + System.lineSeparator();
        if (Platform.isFxApplicationThread()) {
            logArea.appendText(line);
        } else {
            Platform.runLater(() -> logArea.appendText(line));
        }
    }

    private void saveQuietly() {
        try {
            store.save();
        } catch (IOException e) {
            log("Не вдалося зберегти налаштування: " + e.getMessage());
        }
    }

    /** Комірка «Стан» з кольоровою позначкою. */
    private final class StateCell extends TableCell<FileRow, FileRow> {
        private final Label label = new Label();

        StateCell() {
            label.getStyleClass().add("badge");
        }

        @Override
        protected void updateItem(FileRow row, boolean empty) {
            super.updateItem(row, empty);
            if (empty || row == null) {
                setGraphic(null);
                return;
            }
            label.getStyleClass().removeAll("badge-ok", "badge-warn", "badge-accent", "badge-muted");
            switch (row.state()) {
                case PATCHED -> {
                    label.setText("✓ Виправлено · " + row.record().ratio().resolutionText());
                    label.getStyleClass().add("badge-ok");
                }
                case CHANGED_AFTER_PATCH -> {
                    label.setText("⟳ Змінено оновленням");
                    label.getStyleClass().add("badge-warn");
                }
                default -> {
                    boolean recommended = row.candidate().recommended();
                    label.setText(recommended ? "★ Рекомендовано" : "Оригінал");
                    label.getStyleClass().add(recommended ? "badge-accent" : "badge-muted");
                }
            }
            setGraphic(label);
        }
    }

    // ================================================================== знімки для документації

    private void scheduleSnapshot(Path target) {
        Timeline[] poll = new Timeline[1];
        String[] pendingAction = {context.options().action()};
        poll[0] = new Timeline(new KeyFrame(Duration.millis(400), e -> {
            boolean analysing = analysisTask != null && !analysisTask.isDone();
            if (!loadingGames && !busy && !analysing) {
                if (pendingAction[0] != null && currentAnalysis != null) {
                    String action = pendingAction[0];
                    pendingAction[0] = null;
                    if (action.equals("fix")) {
                        onFix();
                    } else if (action.equals("restore")) {
                        onRestore();
                    }
                    return; // чекаємо, доки дія завершиться
                }
                poll[0].stop();
                PauseTransition settle = new PauseTransition(Duration.millis(700));
                settle.setOnFinished(ev -> {
                    try {
                        double scale = stage.getOutputScaleX();
                        SnapshotParameters params = new SnapshotParameters();
                        params.setTransform(Transform.scale(scale, scale));
                        logPane.setAnimated(false);
                        logPane.setExpanded(context.options().action() != null);
                        stage.getScene().getRoot().applyCss();
                        stage.getScene().getRoot().layout();
                        WritableImage image = stage.getScene().getRoot().snapshot(params, null);
                        PngWriter.write(image, target);
                        System.out.println("Snapshot saved: " + target.toAbsolutePath());
                    } catch (IOException ex) {
                        System.err.println("Snapshot failed: " + ex);
                    }
                    Platform.exit();
                });
                settle.play();
            }
        }));
        poll[0].setCycleCount(Timeline.INDEFINITE);
        poll[0].play();
    }

    /** Малює іконку: у .png (256×256) або у .ico (16…256 px) — для інсталятора. */
    private void renderIconAndExit(Path target) {
        try {
            SnapshotParameters params = new SnapshotParameters();
            params.setFill(Color.TRANSPARENT);
            if (target.toString().toLowerCase(Locale.ROOT).endsWith(".ico")) {
                List<IcoWriter.Entry> entries = new ArrayList<>();
                for (int size : new int[]{16, 24, 32, 48, 64, 128, 256}) {
                    entries.add(new IcoWriter.Entry(size, PngWriter.encode(AppIcon.create(size).snapshot(params, null))));
                }
                IcoWriter.write(entries, target);
            } else {
                PngWriter.write(AppIcon.create(256).snapshot(params, null), target);
            }
            System.out.println("Icon saved: " + target.toAbsolutePath());
        } catch (IOException ex) {
            System.err.println("Icon render failed: " + ex);
        }
        Platform.exit();
    }
}
