package org.textsentry.app;

import static org.junit.Assert.*;
import android.Manifest;
import android.app.Notification;
import android.app.NotificationManager;
import android.content.Context;
import android.os.Build;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Test;

public class RoundupNotificationTest {
    @Test public void noEmptyOrRepeatedNotificationAndDisableCancels() throws Exception {
        Context app = InstrumentationRegistry.getInstrumentation().getTargetContext();
        NotificationManager manager = app.getSystemService(NotificationManager.class);
        HistoryStore store = new HistoryStore(app);
        KeyVault vault = new KeyVault(app);
        vault.save("synthetic-roundup-test");
        SetupFlow.complete(app);
        if (Build.VERSION.SDK_INT >= 33) {
            InstrumentationRegistry.getInstrumentation().getUiAutomation()
                    .grantRuntimePermission(app.getPackageName(), Manifest.permission.POST_NOTIFICATIONS);
        }
        try {
            store.clear();
            RoundupWorker.setFrequency(app, 0);
            // Set the test clock/cadence without racing an asynchronously scheduled worker.
            long start = System.currentTimeMillis() - 2 * 86_400_000L;
            app.getSharedPreferences("textsentry_settings", Context.MODE_PRIVATE)
                    .edit().putInt("roundup_days", 1).putLong("roundup_cursor", start).commit();
            RoundupWorker.runCheck(app, start + 86_400_000L);
            assertNull(find(manager));
            String id = store.add("+15550001111", "Synthetic private content", .99, "STOP", "submitting", "sms");
            store.markSendResult(id, true);
            long now = System.currentTimeMillis() + 86_400_000L;
            RoundupWorker.runCheck(app, now);
            Notification notification = find(manager);
            assertNotNull(notification);
            assertEquals("Your STOP replies are ready to review.",
                    notification.extras.getCharSequence(Notification.EXTRA_TEXT).toString());
            assertNotNull(notification.contentIntent);
            RoundupWorker.cancelNotification(app);
            RoundupWorker.runCheck(app, now);
            assertNull(find(manager));
            // A later interval can report a new send; disabling cancels it immediately.
            long next = now + 86_400_000L;
            HistoryStore later = new HistoryStore(app, () -> now + 1);
            String nextId = later.add("+15550001112", "Second synthetic message", .99,
                    "STOP", "submitting", "sms");
            later.markSendResult(nextId, true);
            RoundupWorker.runCheck(app, next);
            assertNotNull(find(manager));
            RoundupWorker.setFrequency(app, 0);
            RoundupWorker.runCheck(app, next + 7 * 86_400_000L);
            assertNull(find(manager));
        } finally {
            RoundupWorker.setFrequency(app, 0);
            store.clear();
            vault.clear();
            SetupFlow.reset(app);
        }
    }

    private Notification find(NotificationManager manager) {
        for (android.service.notification.StatusBarNotification posted : manager.getActiveNotifications()) {
            if (posted.getId() == 485) return posted.getNotification();
        }
        return null;
    }
}
