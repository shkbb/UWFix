package ua.uwfix.update;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/** Розбір відповіді GitHub API (скорочений реальний формат). */
class UpdateCheckerTest {

    private static final String DIGEST = "a3f1c0de8b5e4f6a7b8c9d0e1f2a3b4c5d6e7f8091a2b3c4d5e6f708192a3b4c";

    private static String release(String tag, boolean draft, boolean prerelease, String assetName, String url) {
        return """
                {
                  "html_url": "https://github.com/shkbb/UWFix/releases/tag/%1$s",
                  "tag_name": "%1$s",
                  "draft": %2$s,
                  "prerelease": %3$s,
                  "assets": [
                    {"name": "UWFix-1.2.0.exe", "size": 30000000,
                     "browser_download_url": "https://github.com/shkbb/UWFix/releases/download/%1$s/UWFix-1.2.0.exe"},
                    {"name": "%4$s", "size": 28512345, "digest": "sha256:%6$s",
                     "browser_download_url": "%5$s"}
                  ]
                }
                """.formatted(tag, draft, prerelease, assetName, url, DIGEST);
    }

    private static final String ZIP_URL = "https://github.com/shkbb/UWFix/releases/download/v1.2.0/UWFix-1.2.0-portable.zip";

    @Test
    void readsVersionPortableArchiveAndChecksum() {
        ReleaseInfo r = UpdateChecker.parse(release("v1.2.0", false, false, "UWFix-1.2.0-portable.zip", ZIP_URL), true);
        assertEquals("1.2.0", r.version().toString());
        assertEquals(ZIP_URL, r.zipUrl());
        assertEquals(28512345, r.zipSize());
        assertEquals(DIGEST, r.zipSha256());
        assertEquals("https://github.com/shkbb/UWFix/releases/tag/v1.2.0", r.pageUrl());
    }

    @Test
    void ignoresDraftsAndPrereleases() {
        assertNull(UpdateChecker.parse(release("v1.2.0", true, false, "UWFix-1.2.0-portable.zip", ZIP_URL), true));
        assertNull(UpdateChecker.parse(release("v1.2.0", false, true, "UWFix-1.2.0-portable.zip", ZIP_URL), true));
    }

    @Test
    void requiresPortableArchiveOfTheSameVersion() {
        assertNull(UpdateChecker.parse(release("v1.2.0", false, false, "UWFix-1.1.0-portable.zip", ZIP_URL), true));
    }

    @Test
    void rejectsDownloadsFromOtherPlaces() {
        String foreign = "https://evil.example.com/UWFix-1.2.0-portable.zip";
        assertNull(UpdateChecker.parse(release("v1.2.0", false, false, "UWFix-1.2.0-portable.zip", foreign), true));
        // для тестового сервера (адреса API перевизначена) перевірка джерела вимикається
        assertEquals(foreign,
                UpdateChecker.parse(release("v1.2.0", false, false, "UWFix-1.2.0-portable.zip", foreign), false).zipUrl());
    }

    @Test
    void digestFormat() {
        assertEquals(DIGEST, UpdateChecker.sha256FromDigest("sha256:" + DIGEST.toUpperCase()));
        assertNull(UpdateChecker.sha256FromDigest("md5:abc"));
        assertNull(UpdateChecker.sha256FromDigest("sha256:not-hex"));
        assertNull(UpdateChecker.sha256FromDigest(null));
    }
}
