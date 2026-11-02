package org.textsentry.app;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Strict metadata contract for the rolling *signed sideload* GitHub prerelease. */
final class GitHubRelease {
    static final String API_URL =
            "https://api.github.com/repos/TextSentry/text-sentry-android/releases/tags/latest";
    static final String DOWNLOAD_URL =
            "https://github.com/TextSentry/text-sentry-android/releases/download/latest/TextSentry-Android-latest.apk";
    private static final Pattern VERSION =
            Pattern.compile("(?m)^Android versionCode: ([1-9][0-9]*)$");

    final long versionCode;
    final String downloadUrl;

    private GitHubRelease(long versionCode, String downloadUrl) {
        this.versionCode = versionCode;
        this.downloadUrl = downloadUrl;
    }

    static GitHubRelease fromCache(long code, String url) {
        return code > 0 && code <= Integer.MAX_VALUE && DOWNLOAD_URL.equals(url)
                ? new GitHubRelease(code, url) : null;
    }

    static GitHubRelease parse(String json) {
        if (json == null || json.length() > 65_536) return null;
        try {
            JSONObject payload = new JSONObject(json);
            if (!"latest".equals(payload.optString("tag_name"))
                    || payload.optBoolean("draft", true)
                    || !payload.optBoolean("prerelease", false)) return null;
            Matcher matcher = VERSION.matcher(payload.optString("body", ""));
            if (!matcher.find()) return null;
            long code = Long.parseLong(matcher.group(1));
            if (code < 1 || code > Integer.MAX_VALUE) return null;

            JSONArray assets = payload.optJSONArray("assets");
            if (assets == null) return null;
            for (int i = 0; i < assets.length(); i++) {
                JSONObject asset = assets.optJSONObject(i);
                if (asset != null
                        && "TextSentry-Android-latest.apk".equals(asset.optString("name"))
                        && "uploaded".equals(asset.optString("state"))
                        && asset.optLong("size") > 0
                        && DOWNLOAD_URL.equals(asset.optString("browser_download_url"))) {
                    return new GitHubRelease(code, DOWNLOAD_URL);
                }
            }
        } catch (JSONException | NumberFormatException invalid) {
            // Missing or untrusted release data must never trigger an install link.
        }
        return null;
    }

    boolean shouldPrompt(long installedVersionCode, long lastPromptedVersionCode) {
        return versionCode > installedVersionCode && versionCode > lastPromptedVersionCode;
    }
}
