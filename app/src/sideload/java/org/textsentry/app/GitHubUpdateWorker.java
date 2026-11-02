package org.textsentry.app;

import android.content.Context;
import androidx.annotation.NonNull;
import androidx.work.Constraints;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.PeriodicWorkRequest;
import androidx.work.Worker;
import androidx.work.WorkerParameters;
import androidx.work.WorkManager;
import java.io.IOException;
import java.util.concurrent.TimeUnit;

/** Periodically caches GitHub release info; dialogs are deferred until the next app open. */
public final class GitHubUpdateWorker extends Worker {
    private static final String UNIQUE_WORK = "textsentry-sideload-github-updates";

    public GitHubUpdateWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    static void schedule(Context context) {
        Constraints constraints = new Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED).build();
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(UNIQUE_WORK,
                ExistingPeriodicWorkPolicy.KEEP,
                new PeriodicWorkRequest.Builder(GitHubUpdateWorker.class, 24, TimeUnit.HOURS)
                        .setConstraints(constraints).build());
    }

    @NonNull @Override public Result doWork() {
        try {
            GitHubUpdates.fetchAndCache(getApplicationContext());
            return Result.success();
        } catch (IOException unavailable) {
            return Result.retry();
        }
    }
}
