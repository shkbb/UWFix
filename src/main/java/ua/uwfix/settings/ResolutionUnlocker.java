package ua.uwfix.settings;

import ua.uwfix.analysis.EngineDetector;
import ua.uwfix.i18n.I18n;
import ua.uwfix.model.AspectRatio;
import ua.uwfix.scan.WindowsRegistry;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Виставляє потрібну роздільну здатність прямо в налаштуваннях гри — для ігор,
 * які не пропонують її у своєму меню.
 * <ul>
 *   <li><b>Unreal Engine 4/5</b>: {@code %LOCALAPPDATA%\<Проєкт>\Saved\Config\Windows\GameUserSettings.ini},
 *       де «Проєкт» — частина назви {@code <Проєкт>-Win64-Shipping.exe};</li>
 *   <li><b>Unreal Engine 3</b>: {@code Документи\My Games\<Гра>\<Назва>Game\Config\<Назва>Engine.ini},
 *       секція [SystemSettings];</li>
 *   <li><b>Unity</b>: реєстр {@code HKCU\Software\<компанія>\<гра>}, назви з файлу {@code <Гра>_Data\app.info}.</li>
 * </ul>
 * Налаштування з'являються після першого запуску гри — до того змінювати нічого.
 */
public final class ResolutionUnlocker {

    public static final String BACKUP_SUFFIX = ".uwfix-backup";

    private static final Pattern SHIPPING_EXE = Pattern.compile("(?i)(.+)-(win64|wingdk)-shipping\\.exe");
    private static final List<String> UNREAL_PLATFORMS = List.of("Windows", "WindowsNoEditor", "WinGDK", "WindowsClient");

    /**
     * Результат пошуку налаштувань.
     *
     * @param supported чи знає програма, де ця гра зберігає налаштування
     * @param settings  знайдені налаштування або {@code null} (гру ще не запускали)
     */
    public record Lookup(boolean supported, GameSettings settings) {
    }

    private final Path localAppData;
    private final Path documents;

    public ResolutionUnlocker() {
        this(Path.of(System.getenv().getOrDefault("LOCALAPPDATA",
                        System.getProperty("user.home") + "\\AppData\\Local")),
                documentsFolder());
    }

    /** Для тестів: власні папки замість справжніх. */
    public ResolutionUnlocker(Path localAppData, Path documents) {
        this.localAppData = localAppData;
        this.documents = documents;
    }

    public Lookup lookup(Path installDir) {
        return lookup(installDir, null);
    }

    /**
     * @param gameName назва гри — допомагає знайти папку налаштувань, якщо вона названа
     *                 не так, як проєкт (SILENT HILL 2 → SilentHill2, а не SHProto)
     */
    public Lookup lookup(Path installDir, String gameName) {
        Optional<String> project = unrealProjectName(installDir);
        if (project.isPresent()) {
            List<String> names = new ArrayList<>(List.of(project.get()));
            if (installDir.getFileName() != null) {
                names.add(installDir.getFileName().toString());
            }
            if (gameName != null) {
                names.add(gameName);
            }
            return new Lookup(true, unreal(installDir, project.get(), names).orElse(null));
        }
        List<String> ue3Folders = unreal3GameFolders(installDir);
        if (!ue3Folders.isEmpty()) {
            Optional<GameSettings> found = unreal3(ue3Folders);
            if (found.isPresent()) {
                return new Lookup(true, found.get());
            }
        }
        Optional<String[]> unity = unityAppInfo(installDir);
        if (unity.isPresent()) {
            return new Lookup(true, unityRegistry(unity.get()[0], unity.get()[1]).orElse(null));
        }
        return new Lookup(false, null);
    }

    // ------------------------------------------------------------------ Unreal Engine 4/5

    /**
     * «SHProto» з «SHProto-Win64-Shipping.exe»; якщо .exe перейменовано —
     * назва папки проєкту з {@code Content\Paks} («OakGame»).
     */
    static Optional<String> unrealProjectName(Path installDir) {
        try (Stream<Path> files = Files.walk(installDir, 5)) {
            Optional<String> fromExe = files
                    .map(p -> p.getFileName() == null ? "" : p.getFileName().toString())
                    .map(SHIPPING_EXE::matcher)
                    .filter(Matcher::matches)
                    .map(m -> m.group(1))
                    .findFirst();
            if (fromExe.isPresent()) {
                return fromExe;
            }
        } catch (IOException | RuntimeException e) {
            return Optional.empty();
        }
        Path project = EngineDetector.unrealProjectFolder(installDir);
        return project == null ? Optional.empty() : Optional.of(project.getFileName().toString());
    }

    private Optional<GameSettings> unreal(Path installDir, String project, List<String> names) {
        List<Path> bases = new ArrayList<>(List.of(
                localAppData.resolve(project),
                documents.resolve("My Games").resolve(project),
                installDir.resolve(project)));
        bases.addAll(matchingFolders(localAppData, names));
        bases.addAll(matchingFolders(documents.resolve("My Games"), names));
        List<Path> candidates = new ArrayList<>();
        for (Path base : bases) {
            for (String platform : UNREAL_PLATFORMS) {
                candidates.add(base.resolve("Saved").resolve("Config").resolve(platform).resolve("GameUserSettings.ini"));
            }
        }
        return newest(candidates).map(file -> {
            AspectRatio current = readIni(file, IniEditor.endingWith("GameUserSettings"),
                    "ResolutionSizeX", "ResolutionSizeY");
            return new GameSettings(GameSettings.Kind.UNREAL_INI, file, null, current);
        });
    }

    /** Папки з налаштуваннями Unreal ({@code Saved\Config}), назва яких схожа на назву гри. */
    private static List<Path> matchingFolders(Path parent, List<String> names) {
        List<Path> result = new ArrayList<>();
        try (DirectoryStream<Path> dirs = Files.newDirectoryStream(parent, Files::isDirectory)) {
            for (Path dir : dirs) {
                if (nameMatches(dir.getFileName().toString(), names)
                        && Files.isDirectory(dir.resolve("Saved").resolve("Config"))) {
                    result.add(dir);
                }
            }
        } catch (IOException e) {
            return result;
        }
        return result;
    }

    /**
     * Чи схожа назва папки на одну з назв гри: після нормалізації (лише літери й цифри,
     * малими) назви збігаються або назва гри починається з назви папки.
     * «SilentHill2» ↔ «SILENT HILL 2», «Stalker2» ↔ «S.T.A.L.K.E.R. 2: Heart of Chornobyl».
     */
    static boolean nameMatches(String folder, List<String> names) {
        String f = normalize(folder);
        if (f.length() < 3) {
            return false;
        }
        for (String name : names) {
            String n = normalize(name);
            if (!n.isEmpty() && (n.equals(f) || n.startsWith(f))) {
                return true;
            }
        }
        return false;
    }

    static String normalize(String text) {
        StringBuilder sb = new StringBuilder();
        for (char c : text.toLowerCase(Locale.ROOT).toCharArray()) {
            if (Character.isLetterOrDigit(c)) {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    // ------------------------------------------------------------------ Unreal Engine 3

    /** Папки на кшталт «LifeIsStrangeGame» у корені гри. */
    static List<String> unreal3GameFolders(Path installDir) {
        List<String> names = new ArrayList<>();
        try (DirectoryStream<Path> dirs = Files.newDirectoryStream(installDir, Files::isDirectory)) {
            for (Path dir : dirs) {
                String name = dir.getFileName().toString();
                if (name.toLowerCase(Locale.ROOT).endsWith("game") && name.length() > 4) {
                    names.add(name);
                }
            }
        } catch (IOException e) {
            return names;
        }
        return names;
    }

    private Optional<GameSettings> unreal3(List<String> gameFolders) {
        Path myGames = documents.resolve("My Games");
        List<Path> candidates = new ArrayList<>();
        try (DirectoryStream<Path> titles = Files.newDirectoryStream(myGames, Files::isDirectory)) {
            for (Path title : titles) {
                for (String folder : gameFolders) {
                    Path config = title.resolve(folder).resolve("Config");
                    if (Files.isDirectory(config)) {
                        try (DirectoryStream<Path> inis = Files.newDirectoryStream(config, "*Engine.ini")) {
                            inis.forEach(candidates::add);
                        }
                    }
                }
            }
        } catch (IOException e) {
            return Optional.empty();
        }
        return newest(candidates).map(file -> new GameSettings(GameSettings.Kind.UNREAL3_INI, file, null,
                readIni(file, IniEditor.named("SystemSettings"), "ResX", "ResY")));
    }

    // ------------------------------------------------------------------ Unity

    /** Компанія і назва гри з {@code <Гра>_Data\app.info} (два рядки). */
    static Optional<String[]> unityAppInfo(Path installDir) {
        try (DirectoryStream<Path> dirs = Files.newDirectoryStream(installDir, "*_Data")) {
            for (Path dir : dirs) {
                Path info = dir.resolve("app.info");
                if (Files.isRegularFile(info)) {
                    List<String> lines = Files.readAllLines(info, StandardCharsets.UTF_8);
                    if (lines.size() >= 2 && !lines.get(0).isBlank() && !lines.get(1).isBlank()) {
                        return Optional.of(new String[]{lines.get(0).strip(), lines.get(1).strip()});
                    }
                }
            }
        } catch (IOException | RuntimeException e) {
            return Optional.empty();
        }
        return Optional.empty();
    }

    static String unityRegistryKey(String company, String product) {
        return "HKCU\\Software\\" + company + "\\" + product;
    }

    private static Optional<GameSettings> unityRegistry(String company, String product) {
        String key = unityRegistryKey(company, product);
        Map<String, String> values = WindowsRegistry.readValues(key);
        if (values.isEmpty()) {
            return Optional.empty(); // гру ще не запускали
        }
        AspectRatio current = ratio(values.get(UnityPrefs.valueName(UnityPrefs.WIDTH)),
                values.get(UnityPrefs.valueName(UnityPrefs.HEIGHT)));
        return Optional.of(new GameSettings(GameSettings.Kind.UNITY_REGISTRY, null, key, current));
    }

    // ------------------------------------------------------------------ запис

    /** Записує роздільну здатність у налаштування гри. Перед першою зміною ini-файлу робить копію. */
    public void apply(GameSettings settings, AspectRatio target) throws IOException {
        String w = String.valueOf(target.width());
        String h = String.valueOf(target.height());
        switch (settings.kind()) {
            case UNREAL_INI -> {
                Map<String, String> always = new LinkedHashMap<>();
                always.put("ResolutionSizeX", w);
                always.put("ResolutionSizeY", h);
                always.put("LastUserConfirmedResolutionSizeX", w);
                always.put("LastUserConfirmedResolutionSizeY", h);
                Map<String, String> ifPresent = new LinkedHashMap<>();
                ifPresent.put("DesiredScreenWidth", w);
                ifPresent.put("DesiredScreenHeight", h);
                ifPresent.put("LastUserConfirmedDesiredScreenWidth", w);
                ifPresent.put("LastUserConfirmedDesiredScreenHeight", h);
                editIni(settings.file(), IniEditor.endingWith("GameUserSettings"),
                        "/Script/Engine.GameUserSettings", always, ifPresent);
            }
            case UNREAL3_INI -> {
                Map<String, String> always = new LinkedHashMap<>();
                always.put("ResX", w);
                always.put("ResY", h);
                editIni(settings.file(), IniEditor.named("SystemSettings"), "SystemSettings", always, Map.of());
            }
            case UNITY_REGISTRY -> {
                String key = settings.registryKey();
                boolean ok = WindowsRegistry.setDword(key, UnityPrefs.valueName(UnityPrefs.WIDTH), target.width())
                        && WindowsRegistry.setDword(key, UnityPrefs.valueName(UnityPrefs.HEIGHT), target.height());
                // «Use Native» = 1 змушує гру брати роздільну здатність робочого столу замість записаної
                String useNative = UnityPrefs.valueName(UnityPrefs.USE_NATIVE);
                if (ok && WindowsRegistry.readValues(key).containsKey(useNative)) {
                    ok = WindowsRegistry.setDword(key, useNative, 0);
                }
                if (!ok) {
                    throw new IOException(I18n.t("error.settings.registry", key));
                }
            }
        }
    }

    private static void editIni(Path file, java.util.function.Predicate<String> section, String defaultSection,
                                Map<String, String> always, Map<String, String> ifPresent) throws IOException {
        Path backup = file.resolveSibling(file.getFileName() + BACKUP_SUFFIX);
        if (!Files.exists(backup)) {
            Files.copy(file, backup, StandardCopyOption.COPY_ATTRIBUTES);
        }
        TextFile ini = TextFile.read(file);
        String updated = IniEditor.update(ini.text(), section, defaultSection, always, ifPresent);
        // Деякі гравці роблять файл «лише для читання», щоб гра не скидала налаштування — зберігаємо це
        boolean readOnly = isReadOnly(file);
        if (readOnly) {
            Files.setAttribute(file, "dos:readonly", false);
        }
        try {
            ini.write(file, updated);
        } finally {
            if (readOnly) {
                Files.setAttribute(file, "dos:readonly", true);
            }
        }
    }

    // ------------------------------------------------------------------ допоміжне

    private static AspectRatio readIni(Path file, java.util.function.Predicate<String> section,
                                       String widthKey, String heightKey) {
        try {
            String text = TextFile.read(file).text();
            return ratio(IniEditor.get(text, section, widthKey), IniEditor.get(text, section, heightKey));
        } catch (IOException e) {
            return null;
        }
    }

    private static AspectRatio ratio(String width, String height) {
        try {
            int w = Integer.parseInt(width.strip());
            int h = Integer.parseInt(height.strip());
            return w > 0 && h > 0 ? new AspectRatio(w, h) : null;
        } catch (RuntimeException e) {
            return null;
        }
    }

    /** Із кількох знайдених файлів — той, що змінювався останнім (його й читає гра). */
    private static Optional<Path> newest(List<Path> candidates) {
        return candidates.stream()
                .filter(Files::isRegularFile)
                .max(Comparator.comparing(ResolutionUnlocker::modified));
    }

    private static FileTime modified(Path p) {
        try {
            return Files.getLastModifiedTime(p);
        } catch (IOException e) {
            return FileTime.fromMillis(0);
        }
    }

    private static boolean isReadOnly(Path file) {
        try {
            return Boolean.TRUE.equals(Files.getAttribute(file, "dos:readonly"));
        } catch (IOException | UnsupportedOperationException e) {
            return false;
        }
    }

    /** Папка «Документи» (може бути перенесена, наприклад в OneDrive) — беремо з реєстру. */
    private static Path documentsFolder() {
        String personal = WindowsRegistry
                .readValues("HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Explorer\\Shell Folders")
                .get("Personal");
        return personal != null ? Path.of(personal) : Path.of(System.getProperty("user.home"), "Documents");
    }
}
