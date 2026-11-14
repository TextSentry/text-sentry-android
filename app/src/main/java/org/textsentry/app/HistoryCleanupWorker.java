package org.textsentry.app;

import android.content.Context;
import androidx.annotation.NonNull;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;
import androidx.work.Worker;
import androidx.work.WorkerParameters;
import java.util.concurrent.TimeUnit;

/**
 * Best-effort background pruning. The store also prunes synchronously on reads/writes;
 * Android may defer periodic work under battery management.
 */
public final class HistoryCleanupWorker extends Worker {
    private static final String NAME = "textsentry_history_expiration";

    public HistoryCleanupWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    static void schedule(Context context) {
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                new PeriodicWorkRequest.Builder(HistoryCleanupWorker.class, 1, TimeUnit.HOURS)
                        .build());
    }

    @NonNull @Override public Result doWork() {
        try {
            new HistoryStore(getApplicationContext()).pruneExpired();
            return Result.success();
        } catch (Exception failure) {
            return Result.retry();
        }
    }
}
