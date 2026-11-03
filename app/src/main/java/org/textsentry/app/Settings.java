package org.textsentry.app;

import android.content.Context;
import android.content.SharedPreferences;
import java.util.UUID;

/**
 * Device-only configuration. Provider and model intentionally cannot be changed:
 * this release sends AI requests exclusively to TypeSafe Jev latest.
 */
public final class Settings {
    public static final String DEFAULT_ENDPOINT = SystemOneClient.ENDPOINT;
    public static final String DEFAULT_MODEL = SystemOneClient.MODEL;
    /** Optional restrictions, combined after selected categories. */
    public static final String DEFAULT_PROMPT = "";
    private static final String LEGACY_DEFAULT_PROMPT =
            "Only reply STOP to unmistakably opt-in marketing messages that explicitly " +
            "describe a STOP unsubscribe option. Ignore personal messages, scams, " +
            "transactional notices, and uncertainty.";
    public static final int DEFAULT_CONFIDENCE = 90;
    private final SharedPreferences prefs;
    private final Context appContext;

    public Settings(Context context) {
        appContext = context.getApplicationContext();
        prefs = appContext
                .getSharedPreferences("textsentry_settings", Context.MODE_PRIVATE);
        // Never reuse an old chat-completions key with the new, fixed TypeSafe endpoint.
        if (prefs.contains("system_one") && !prefs.getBoolean("system_one", false)) {
            new KeyVault(context).clear();
            prefs.edit().putBoolean("monitor", false).putBoolean("auto_send", false)
                    .putBoolean("system_one", true)
                    .putString("generation", UUID.randomUUID().toString()).commit();
        }
    }

    public String endpoint() { return DEFAULT_ENDPOINT; }
    public String model() { return DEFAULT_MODEL; }
    public boolean systemOne() { return true; }
    public String prompt() {
        String value = prefs.getString("prompt", DEFAULT_PROMPT);
        // The former default was marketing-only; leave category choices free to expand.
        return LEGACY_DEFAULT_PROMPT.equals(value) ? DEFAULT_PROMPT : value;
    }
    public int categoryMask() {
        int stored = prefs.getInt("category_mask", MessageCategories.DEFAULT_MASK);
        return MessageCategories.valid(stored) ? stored : 0;
    }
    /** Exactly one set of instructions per eligible incoming text. */
    public String effectivePrompt() {
        return MessageCategories.compose(categoryMask(), prompt());
    }
    public String cannedReply() { return prefs.getString("canned_reply", "STOP"); }
    public int confidence() {
        return Math.max(50, Math.min(100,
                prefs.getInt("confidence_percent", DEFAULT_CONFIDENCE)));
    }
    public boolean monitoring() { return prefs.getBoolean("monitor", false); }
    public boolean autoSend() { return prefs.getBoolean("auto_send", false); }
    /** Text-only MMS scanning shares the Start Monitoring opt-in, not a second toggle. */
    public boolean mmsEnabled() { return prefs.getBoolean("mms_monitor", false); }
    public long mmsSinceMs() { return prefs.getLong("mms_since", 0L); }

    /** One explicit action starts new SMS and text-only MMS monitoring in preview mode. */
    public void startMonitoring() {
        if (!prefs.edit().putBoolean("monitor", true)
                .putBoolean("auto_send", false)
                .putBoolean("mms_monitor", true)
                .putLong("mms_since", System.currentTimeMillis())
                .putString("generation", UUID.randomUUID().toString()).commit()) {
            throw new IllegalStateException("Could not start monitoring");
        }
    }

    public String generation() { return prefs.getString("generation", "initial-disabled"); }

    /** Hidden diagnostics are an explicit, persistent local opt-in, never a build flag. */
    public boolean developerMode() { return prefs.getBoolean("developer_mode", false); }

    public void setDeveloperMode(boolean enabled) {
        if (!prefs.edit().putBoolean("developer_mode", enabled).commit()) {
            throw new IllegalStateException("Could not update developer mode");
        }
    }
    public String history() { return prefs.getString("history", "No decisions yet."); }

    /** A policy change invalidates work that was queued using older rules. */
    public void saveCategoryPolicy(String extraPrompt, int confidence, int categories) {
        if (extraPrompt == null || extraPrompt.length() > 2000) {
            throw new IllegalArgumentException("Additional rules must be at most 2000 characters");
        }
        if (confidence < 50 || confidence > 100) {
            throw new IllegalArgumentException("Confidence must be 50–100%");
        }
        // Validate the complete request, including all checked categories, before committing.
        MessageCategories.compose(categories, extraPrompt);
        if (!prefs.edit().putString("prompt", extraPrompt.trim())
                .putInt("confidence_percent", confidence)
                .putInt("category_mask", categories)
                .putString("canned_reply", "STOP")
                .putBoolean("system_one", true)
                .putString("generation", UUID.randomUUID().toString()).commit()) {
            throw new IllegalStateException("Could not save category rules");
        }
    }

    /** Compatibility for old saves; preserves category selection. */
    public void savePolicy(String prompt, int confidence, String fixedReply) {
        if (!SystemOneClient.validReply(fixedReply)) {
            throw new IllegalArgumentException("Invalid fixed reply");
        }
        saveCategoryPolicy(prompt, confidence, categoryMask());
    }

    /** Remains distinct from policy saves so toggling monitoring cannot change the AI model. */
    public void setAutomation(boolean monitor, boolean send) {
        SharedPreferences.Editor edit = prefs.edit()
                .putBoolean("monitor", monitor)
                .putBoolean("auto_send", monitor && send)
                .putString("generation", UUID.randomUUID().toString());
        if (!monitor) {
            // Pausing also ends MMS consent and prevents scanning while monitoring was off.
            edit.putBoolean("mms_monitor", false).putLong("mms_since", 0L);
        }
        edit.commit();
    }

    public void pause() { setAutomation(false, false); }

    /** Legacy wrapper retained for older tests/callers; provider and model are ignored. */
    public void save(String endpoint, String model, String prompt, boolean monitor, boolean send) {
        save(endpoint, model, prompt, monitor, send, true, "STOP");
    }
    public void save(String endpoint, String model, String prompt, boolean monitor,
                     boolean send, boolean useSystemOne, String reply) {
        savePolicy(prompt, confidence(), reply);
        setAutomation(monitor, send);
    }

    public synchronized void addHistory(String description) {
        String cleaned = description.replace("\r", " ").replace("\n", " ");
        String history = prefs.getString("history", "");
        String entry = java.time.LocalDateTime.now() + " " + cleaned + "\n" + history;
        prefs.edit().putString("history", entry.substring(0, Math.min(2400, entry.length()))).apply();
    }

    /** Deletes TextSentry's local decision log only; never modifies the SMS inbox. */
    public void clearHistory() {
        new HistoryStore(appContext).clear();
        prefs.edit().remove("history").commit();
    }
}
