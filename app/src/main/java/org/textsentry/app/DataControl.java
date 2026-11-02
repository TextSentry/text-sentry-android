package org.textsentry.app;

import android.content.Context;
import androidx.work.WorkManager;

/** Erase all local app-owned preferences, queued SMS work and device BYOK secret. */
public final class DataControl {
    private DataControl() {}

    public static void deleteLocalData(Context context) {
        // Block queued sends before attempting async WorkManager cancellation.
        new Settings(context).pause();
        WorkManager.getInstance(context).cancelAllWork();
        new KeyVault(context).clear();
        // Account refresh/access/ID tokens are stored in a separate Keystore-backed vault.
        ManagedKeyMemory.clear();
        SetupFlow.reset(context);
        new AuthSessionStore(context).clear();
        // Wipe the encrypted message archive and its Android Keystore key as well.
        try {
            new HistoryStore(context).deleteAll();
        } catch (Exception e) {
            throw new IllegalStateException("Could not erase encrypted message history", e);
        }
        for (String name : new String[] {"textsentry_settings", "textsentry_reply_ledger",
                "textsentry_secrets", "textsentry_message_history", "textsentry_auth_session",
                "textsentry_mms_seen", "textsentry_updates",
                Onboarding.PREFS}) {
            context.getSharedPreferences(name, Context.MODE_PRIVATE).edit().clear().commit();
        }
    }
}
