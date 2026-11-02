package org.textsentry.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.widget.Toast;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Sideload-only update discovery. Does not download or silently install APKs.
 * Background WorkManager checks cache public release metadata; prompts appear
 * only in the foreground, and only once for each newer versionCode.
 */
final class GitHubUpdates {
    static final String PREFS = "textsentry_updates";
    private static final String CHECKED = "checked";
    private static final String LATEST_CODE = "latest_code";
    private static final String LATEST_URL = "latest_url";
    private static final String PROMPTED = "prompted_code";
    private static final long DAY_MS = 24L * 60L * 60L * 1000L;
    private static final AtomicBoolean CHECKING = new AtomicBoolean();

    private GitHubUpdates() {}

    private static SharedPreferences preferences(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    static GitHubRelease fetchAndCache(Context context) throws IOException {
        HttpURLConnection connection = (HttpURLConnection)
                new URL(GitHubRelease.API_URL).openConnection();
        connection.setRequestMethod("GET");
        connection.setRequestProperty("Accept", "application/vnd.github+json");
        connection.setRequestProperty("User-Agent", "TextSentry-Android");
        connection.setConnectTimeout(8000);
        connection.setReadTimeout(8000);
        connection.setInstanceFollowRedirects(false);
        try {
            if (connection.getResponseCode() != HttpURLConnection.HTTP_OK)
                throw new IOException("Release endpoint unavailable");
            ByteArrayOutputStream response = new ByteArrayOutputStream();
            try (InputStream input = connection.getInputStream()) {
                byte[] buffer = new byte[4096];
                int read;
                while ((read = input.read(buffer)) != -1) {
                    if (response.size() + read > 65_536)
                        throw new IOException("Release metadata too large");
                    response.write(buffer, 0, read);
                }
            }
            GitHubRelease release = GitHubRelease.parse(
                    new String(response.toByteArray(), StandardCharsets.UTF_8));
            if (release == null) throw new IOException("Invalid release metadata");
            preferences(context).edit()
                    .putLong(LATEST_CODE, release.versionCode)
                    .putString(LATEST_URL, release.downloadUrl)
                    .putLong(CHECKED, System.currentTimeMillis())
                    .apply();
            return release;
        } finally {
            connection.disconnect();
        }
    }

    static void checkOnOpen(Activity activity) {
        GitHubRelease cached = cached(activity);
        if (cached != null) offer(activity, cached, false);
        long checked = preferences(activity).getLong(CHECKED, 0);
        long now = System.currentTimeMillis();
        if (checked <= 0 || checked > now || now - checked >= DAY_MS)
            check(activity, false);
    }

    static void checkNow(Activity activity) {
        check(activity, true);
    }

    private static GitHubRelease cached(Context context) {
        SharedPreferences prefs = preferences(context);
        return GitHubRelease.fromCache(prefs.getLong(LATEST_CODE, 0),
                prefs.getString(LATEST_URL, ""));
    }

    private static void check(Activity activity, boolean manual) {
        if (!CHECKING.compareAndSet(false, true)) {
            if (manual) Toast.makeText(activity, "Already checking GitHub for updates",
                    Toast.LENGTH_SHORT).show();
            return;
        }
        Context app = activity.getApplicationContext();
        new Thread(() -> {
            GitHubRelease release = null;
            try {
                release = fetchAndCache(app);
            } catch (IOException ignored) {
                // Network errors never interfere with message monitoring.
            } finally {
                CHECKING.set(false);
            }
            GitHubRelease result = release;
            activity.runOnUiThread(() -> {
                if (activity.isFinishing() || activity.isDestroyed()) return;
                if (result == null) {
                    if (manual) Toast.makeText(activity, "Unable to check GitHub updates",
                            Toast.LENGTH_LONG).show();
                } else if (!offer(activity, result, manual) && manual) {
                    Toast.makeText(activity, "TextSentry is up to date",
                            Toast.LENGTH_LONG).show();
                }
            });
        }, "textsentry-update-check").start();
    }

    private static boolean offer(Activity activity, GitHubRelease release, boolean manual) {
        SharedPreferences prefs = preferences(activity);
        long last = prefs.getLong(PROMPTED, 0);
        if (release.versionCode <= BuildConfig.VERSION_CODE
                || (!manual && !release.shouldPrompt(BuildConfig.VERSION_CODE, last)))
            return false;
        // Persist the prompt before showing it so rotating/reopening does not nag.
        prefs.edit().putLong(PROMPTED, release.versionCode).apply();
        new AlertDialog.Builder(activity)
                .setTitle("TextSentry update available")
                .setMessage("A newer signed sideload version is available (build code "
                        + release.versionCode + "). Open the GitHub download page to get "
                        + "the APK? Android will ask you to confirm installation. "
                        + "Your existing app data is preserved when updating normally.")
                .setPositiveButton("Get update", (dialog, which) -> {
                    try {
                        activity.startActivity(new Intent(Intent.ACTION_VIEW,
                                Uri.parse(release.downloadUrl)));
                    } catch (android.content.ActivityNotFoundException absent) {
                        Toast.makeText(activity, "No browser is available",
                                Toast.LENGTH_LONG).show();
                    }
                })
                .setNegativeButton("Later", null)
                .show();
        return true;
    }
}
