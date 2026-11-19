package org.textsentry.app;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import androidx.annotation.NonNull;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;
import androidx.work.Worker;
import androidx.work.WorkerParameters;
import java.util.concurrent.TimeUnit;

/** Local only, no message text or sender in notifications or scheduled work. */
public final class RoundupWorker extends Worker {
    static final String EXTRA_START = "roundup_start", EXTRA_END = "roundup_end";
    private static final String WORK = "textsentry_roundup", CHANNEL = "stop_roundup";
    private static final int NOTIFICATION_ID = 485;
    private static final Object LOCK = new Object();

    public RoundupWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences("textsentry_settings", Context.MODE_PRIVATE);
    }

    static int frequency(Context context) {
        int days = prefs(context).getInt("roundup_days", 0);
        return SetupPermissions.supportsMonitoring() && RoundupPolicy.valid(days) ? days : 0;
    }

    static void setFrequency(Context context, int days) {
        if (!RoundupPolicy.valid(days) || (days != 0 && !SetupPermissions.supportsMonitoring()))
            throw new IllegalArgumentException("Invalid roundup frequency");
        synchronized (LOCK) {
            SharedPreferences.Editor edit = prefs(context).edit().putInt("roundup_days", days);
            if (days == 0) edit.remove("roundup_cursor");
            else if (frequency(context) == 0)
                edit.putLong("roundup_cursor", System.currentTimeMillis());
            if (!edit.commit()) throw new IllegalStateException("Could not save roundup");
            if (days == 0) {
                WorkManager.getInstance(context).cancelUniqueWork(WORK);
                cancelNotification(context);
            } else {
                WorkManager.getInstance(context).enqueueUniquePeriodicWork(WORK,
                        ExistingPeriodicWorkPolicy.KEEP,
                        new PeriodicWorkRequest.Builder(RoundupWorker.class, 1, TimeUnit.HOURS)
                                .build());
            }
        }
    }

    static boolean notificationsAllowed(Context context) {
        NotificationManager manager = context.getSystemService(NotificationManager.class);
        if (manager == null || !manager.areNotificationsEnabled()) return false;
        if (Build.VERSION.SDK_INT >= 33 &&
                context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                        != PackageManager.PERMISSION_GRANTED) return false;
        NotificationChannel channel = manager.getNotificationChannel(CHANNEL);
        return channel == null || channel.getImportance() != NotificationManager.IMPORTANCE_NONE;
    }

    static void cancelNotification(Context context) {
        synchronized (LOCK) {
            NotificationManager manager = context.getSystemService(NotificationManager.class);
            if (manager != null) manager.cancel(NOTIFICATION_ID);
        }
    }

    @NonNull @Override public Result doWork() {
        return runCheck(getApplicationContext(), System.currentTimeMillis());
    }

    static Result runCheck(Context context, long now) {
        synchronized (LOCK) {
            int days = frequency(context);
            if (days == 0 || !SetupGate.canEnterApp(context)) return Result.success();
            long start = prefs(context).getLong("roundup_cursor", now);
            if (!RoundupPolicy.due(days, start, now) || !notificationsAllowed(context))
                return Result.success();
            try {
                if (new HistoryStore(context).roundupCount(start, now) > 0) {
                    NotificationManager manager = context.getSystemService(NotificationManager.class);
                    NotificationChannel channel = new NotificationChannel(CHANNEL, "STOP roundup",
                            NotificationManager.IMPORTANCE_LOW);
                    channel.setDescription("Occasional reminders to review confirmed STOP replies.");
                    channel.setLockscreenVisibility(Notification.VISIBILITY_PRIVATE);
                    manager.createNotificationChannel(channel);
                    Intent intent = new Intent(context, HistoryActivity.class)
                            .putExtra(EXTRA_START, start).putExtra(EXTRA_END, now)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
                    PendingIntent pending = PendingIntent.getActivity(context, NOTIFICATION_ID, intent,
                            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
                    Notification notification = new Notification.Builder(context, CHANNEL)
                            .setSmallIcon(R.drawable.ic_roundup_notification)
                            .setContentTitle("TextSentry roundup")
                            .setContentText("Your STOP replies are ready to review.")
                            .setVisibility(Notification.VISIBILITY_PRIVATE)
                            .setContentIntent(pending).setAutoCancel(true).setOnlyAlertOnce(true)
                            .build();
                    if (!notificationsAllowed(context)) return Result.success();
                    if (Build.VERSION.SDK_INT >= 33 &&
                            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                                    != PackageManager.PERMISSION_GRANTED) return Result.success();
                    manager.notify(NOTIFICATION_ID, notification);
                }
                if (!prefs(context).edit().putLong("roundup_cursor", now).commit())
                    return Result.retry();
                return Result.success();
            } catch (Exception failure) {
                return Result.retry();
            }
        }
    }
}
