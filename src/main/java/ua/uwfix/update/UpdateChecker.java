package ua.uwfix.update;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Locale;
import java.util.Optional;

/**
 * Перевіряє, чи вийшла нова версія програми.
 * <p>
 * Використовує GitHub REST API: {@code GET /repos/shkbb/UWFix/releases/latest} повертає JSON
 * з номером останнього релізу (tag_name) і списком файлів (assets). Чернетки й попередні
 * версії (pre-release) цей запит не повертає.
 */
public final class UpdateChecker {

    public static final String REPOSITORY = "shkbb/UWFix";
    public static final String DEFAULT_API = "https://api.github.com/repos/" + REPOSITORY + "/releases/latest";

    /** Завантаження дозволені лише з релізів цього репозиторію. */
    static final String TRUSTED_DOWNLOAD_PREFIX = "https://github.com/" + REPOSITORY + "/releases/download/";

    private final String apiUrl;
    private final HttpClient http;

    /**
     * Адресу API можна перевизначити властивістю {@code -Duwfix.update.api=...}
     * (для перевірки оновлення на локальному сервері).
     */
    public UpdateChecker() {
        this(System.getProperty("uwfix.update.api", DEFAULT_API));
    }

    public UpdateChecker(String apiUrl) {
        this.apiUrl = apiUrl;
        this.http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    /** Чи використовується справжній GitHub (а не тестовий сервер). */
    public boolean isDefaultSource() {
        return DEFAULT_API.equals(apiUrl);
    }

    /**
     * @param current поточна версія програми
     * @return реліз, якщо він новіший за поточну версію
     */
    public Optional<ReleaseInfo> check(Version current) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create(apiUrl))
                .timeout(Duration.ofSeconds(10))
                .header("Accept", "application/vnd.github+json")
                .header("User-Agent", "UWFix/" + current)
                .GET()
                .build();
        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new IOException("GitHub API: HTTP " + response.statusCode());
        }
        ReleaseInfo release = parse(response.body(), isDefaultSource());
        return release != null && release.version().isNewerThan(current) ? Optional.of(release) : Optional.empty();
    }

    /**
     * Розбирає відповідь GitHub API.
     *
     * @param trustedOnly приймати лише посилання на завантаження з репозиторію програми
     * @return реліз або {@code null}, якщо це чернетка, pre-release, або в ньому немає портативного архіву
     */
    static ReleaseInfo parse(String json, boolean trustedOnly) {
        JsonObject root = JsonParser.parseString(json).getAsJsonObject();
        if (bool(root, "draft") || bool(root, "prerelease")) {
            return null;
        }
        Version version = Version.parse(string(root, "tag_name"));
        if (version == null) {
            return null;
        }
        String expectedName = assetName(version);
        JsonElement assets = root.get("assets");
        if (assets == null || !assets.isJsonArray()) {
            return null;
        }
        for (JsonElement element : (JsonArray) assets) {
            JsonObject asset = element.getAsJsonObject();
            if (!expectedName.equalsIgnoreCase(string(asset, "name"))) {
                continue;
            }
            String url = string(asset, "browser_download_url");
            if (url == null || (trustedOnly && !url.startsWith(TRUSTED_DOWNLOAD_PREFIX))) {
                return null;
            }
            long size = asset.has("size") ? asset.get("size").getAsLong() : -1;
            return new ReleaseInfo(version, string(root, "html_url"), expectedName, url, size,
                    sha256FromDigest(string(asset, "digest")));
        }
        return null;
    }

    /** Назва архіву портативної версії в релізі: «UWFix-1.2.0-portable.zip». */
    public static String assetName(Version version) {
        return "UWFix-" + version + "-portable.zip";
    }

    /** GitHub повідомляє контрольну суму файлу як «sha256:abc…». */
    static String sha256FromDigest(String digest) {
        if (digest == null) {
            return null;
        }
        String prefix = "sha256:";
        if (!digest.toLowerCase(Locale.ROOT).startsWith(prefix)) {
            return null;
        }
        String hex = digest.substring(prefix.length()).toLowerCase(Locale.ROOT);
        return hex.matches("[0-9a-f]{64}") ? hex : null;
    }

    private static String string(JsonObject o, String key) {
        JsonElement e = o.get(key);
        return e != null && e.isJsonPrimitive() ? e.getAsString() : null;
    }

    private static boolean bool(JsonObject o, String key) {
        JsonElement e = o.get(key);
        return e != null && e.isJsonPrimitive() && e.getAsBoolean();
    }
}
