package org.textsentry.app;

import static org.junit.Assert.*;
import android.Manifest;
import android.app.Notification;
import android.app.NotificationManager;
import android.content.Context;
import android.os.Build;
import android.os.SystemClock;
import android.content.SharedPreferences;
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
            awaitAbsent(manager);
            SharedPreferences prefs = app.getSharedPreferences("textsentry_settings", Context.MODE_PRIVATE);
            // Set the test clock/cadence without racing an asynchronously scheduled worker.
            long start = System.currentTimeMillis() - 2 * 86_400_000L;
            assertTrue(prefs.edit().putInt("roundup_days", 1).putLong("roundup_cursor", start).commit());
            RoundupWorker.runCheck(app, start + 86_400_000L);
            assertEquals(start + 86_400_000L, prefs.getLong("roundup_cursor", 0));
            assertRemainsAbsent(manager);
            String id = store.add("+15550001111", "Synthetic private content", .99, "STOP", "submitting", "sms");
            store.markSendResult(id, true);
            long now = System.currentTimeMillis() + 86_400_000L;
            RoundupWorker.runCheck(app, now);
            assertEquals(now, prefs.getLong("roundup_cursor", 0));
            Notification notification = awaitPosted(manager);
            assertEquals("Your STOP replies are ready to review.",
                    notification.extras.getCharSequence(Notification.EXTRA_TEXT).toString());
            assertNotNull(notification.contentIntent);
            RoundupWorker.cancelNotification(app);
            awaitAbsent(manager); // NotificationManager cancellation is asynchronous.
            RoundupWorker.runCheck(app, now);
            assertEquals(now, prefs.getLong("roundup_cursor", 0));
            assertRemainsAbsent(manager);
            // A later interval can report a new send; disabling cancels it immediately.
            long next = now + 86_400_000L;
            HistoryStore later = new HistoryStore(app, () -> now + 1);
            String nextId = later.add("+15550001112", "Second synthetic message", .99,
                    "STOP", "submitting", "sms");
            later.markSendResult(nextId, true);
            RoundupWorker.runCheck(app, next);
            assertEquals(next, prefs.getLong("roundup_cursor", 0));
            awaitPosted(manager);
            RoundupWorker.setFrequency(app, 0);
            awaitAbsent(manager);
            RoundupWorker.runCheck(app, next + 7 * 86_400_000L);
            assertEquals(0, RoundupWorker.frequency(app));
            assertFalse(prefs.contains("roundup_cursor"));
            assertRemainsAbsent(manager);
        } finally {
            RoundupWorker.setFrequency(app, 0);
            store.clear();
            vault.clear();
            SetupFlow.reset(app);
        }
    }

    // Android's notification service processes notify/cancel on its own handler.
    // Poll actual state with a strict time limit rather than assuming synchronous IPC.
    private Notification awaitPosted(NotificationManager manager) {
        long deadline = SystemClock.elapsedRealtime() + 5_000;
        Notification notification;
        do {
            notification = find(manager);
            if (notification != null) return notification;
            SystemClock.sleep(25);
        } while (SystemClock.elapsedRealtime() < deadline);
        assertNotNull("Expected roundup notification to be posted", notification);
        return notification;
    }

    private void awaitAbsent(NotificationManager manager) {
        long deadline = SystemClock.elapsedRealtime() + 5_000;
        while (find(manager) != null && SystemClock.elapsedRealtime() < deadline) {
            SystemClock.sleep(25);
        }
        assertNull("Expected roundup notification to be removed", find(manager));
    }

    private void assertRemainsAbsent(NotificationManager manager) {
        // Separate cancellation from the subsequent worker call; catch an unexpected
        // asynchronous post instead of merely checking before the service processes it.
        long deadline = SystemClock.elapsedRealtime() + 500;
        do {
            assertNull("Unexpected roundup notification", find(manager));
            SystemClock.sleep(25);
        } while (SystemClock.elapsedRealtime() < deadline);
    }

    private Notification find(NotificationManager manager) {
        for (android.service.notification.StatusBarNotification posted : manager.getActiveNotifications()) {
            if (posted.getId() == 485) return posted.getNotification();
        }
        return null;
    }
}
