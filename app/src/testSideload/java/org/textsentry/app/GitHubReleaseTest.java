package org.textsentry.app;

import static org.junit.Assert.*;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

/** Sideload-only regression tests: never offer unrelated, unsigned or older releases. */
public class GitHubReleaseTest {
    private static final String DOWNLOAD =
            "https://github.com/TextSentry/text-sentry-android/releases/download/latest/TextSentry-Android-latest.apk";

    private JSONObject release(long code) throws Exception {
        JSONObject asset = new JSONObject()
                .put("name", "TextSentry-Android-latest.apk")
                .put("state", "uploaded")
                .put("size", 1024)
                .put("browser_download_url", DOWNLOAD);
        return new JSONObject()
                .put("tag_name", "latest")
                .put("draft", false)
                .put("prerelease", true)
                .put("body", "Experimental build.\nAndroid versionCode: " + code)
                .put("assets", new JSONArray().put(asset));
    }

    @Test public void acceptsOnlyNewerSignedSideloadBuilds() throws Exception {
        GitHubRelease update = GitHubRelease.parse(release(438).toString());
        assertNotNull(update);
        assertEquals(438L, update.versionCode);
        assertEquals(DOWNLOAD, update.downloadUrl);
        assertTrue(update.shouldPrompt(437, 0));
        assertFalse(update.shouldPrompt(438, 0));
        assertFalse(update.shouldPrompt(439, 0));
        assertFalse(update.shouldPrompt(437, 438)); // Already notified of this build.
        assertTrue(update.shouldPrompt(437, 437)); // Another newer build.
    }

    @Test public void neverUsesGitHubGenericLatestReleaseOrReleaseDate() {
        assertEquals("https://api.github.com/repos/TextSentry/text-sentry-android/releases/tags/latest",
                GitHubRelease.API_URL);
    }

    @Test public void ignoresDraftsAndUnexpectedReleaseTags() throws Exception {
        assertNull(GitHubRelease.parse(release(123).put("draft", true).toString()));
        assertNull(GitHubRelease.parse(release(123).put("prerelease", false).toString()));
        assertNull(GitHubRelease.parse(release(123).put("tag_name", "dev-150").toString()));
    }

    @Test public void rejectsMissingOrMalformedNumericVersionMetadata() throws Exception {
        assertNull(GitHubRelease.parse(release(123).put("body", "build 123").toString()));
        for (String metadata : new String[]{
                "Android versionCode: 0", "Android versionCode: -1",
                "Android versionCode: 1.2", "Android versionCode: 2147483648",
                "Android versionCode: 9999999999999999999999", "Android versionCode: 12x"}) {
            assertNull(metadata, GitHubRelease.parse(release(123)
                    .put("body", metadata).toString()));
        }
    }

    @Test public void rejectsMissingAssetsAndUnexpectedDownloadUrls() throws Exception {
        assertNull(GitHubRelease.parse(release(123).put("assets", new JSONArray()).toString()));
        for (String url : new String[]{
                "http://github.com/TextSentry/text-sentry-android/releases/download/latest/TextSentry-Android-latest.apk",
                "https://github.com/TextSentry/text-sentry-android/releases/download/dev-123/TextSentry-Android-latest.apk",
                "https://evil.example/offer.apk"}) {
            JSONObject r = release(123);
            r.getJSONArray("assets").getJSONObject(0).put("browser_download_url", url);
            assertNull(url, GitHubRelease.parse(r.toString()));
        }
        JSONObject missing = release(123);
        missing.getJSONArray("assets").getJSONObject(0).put("size", 0);
        assertNull(GitHubRelease.parse(missing.toString()));
        JSONObject unready = release(123);
        unready.getJSONArray("assets").getJSONObject(0).put("state", "starter");
        assertNull(GitHubRelease.parse(unready.toString()));
    }

    @Test public void invalidPayloadsAreNotInstallableUpdates() throws Exception {
        assertNull(GitHubRelease.parse("{}"));
        assertNull(GitHubRelease.parse("not json"));
        assertNull(GitHubRelease.parse(null));
    }
}
