package org.textsentry.app;

import android.Manifest;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import androidx.annotation.NonNull;
import androidx.work.Constraints;
import androidx.work.Data;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.ExistingWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.PeriodicWorkRequest;
import androidx.work.Worker;
import androidx.work.WorkerParameters;
import androidx.work.WorkManager;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * Automatic MMS indexing after the existing carrier messaging app downloads it.
 * The only network request is the existing text-only Jev decision in SmsWorker.
 */
public final class MmsScanWorker extends Worker {
    private static final String PERIODIC = "textsentry-mms-background";
    private static final String IMMEDIATE = "textsentry-mms-scan";
    private static final String LEDGER = "textsentry_mms_seen";
    private static final Object LOCK = new Object();

    public MmsScanWorker(@NonNull Context context, @NonNull WorkerParameters args) {
        super(context, args);
    }

    static boolean authorized(Context context) {
        Settings settings = new Settings(context);
        return SetupGate.canEnterApp(context) && settings.monitoring()
                && settings.mmsEnabled() && settings.mmsSinceMs() > 0
                && context.checkSelfPermission(Manifest.permission.READ_SMS)
                    == PackageManager.PERMISSION_GRANTED;
    }

    static void sync(Context context) {
        WorkManager manager = WorkManager.getInstance(context);
        if (!authorized(context)) {
            manager.cancelUniqueWork(PERIODIC);
            manager.cancelUniqueWork(IMMEDIATE);
            return;
        }
        Constraints network = new Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED).build();
        manager.enqueueUniquePeriodicWork(PERIODIC, ExistingPeriodicWorkPolicy.KEEP,
                new PeriodicWorkRequest.Builder(MmsScanWorker.class, 15, TimeUnit.MINUTES)
                        .setConstraints(network).build());
        scanNow(context);
    }

    static void scanNow(Context context) {
        if (!authorized(context)) return;
        manager(context).enqueueUniqueWork(IMMEDIATE, ExistingWorkPolicy.KEEP,
                new OneTimeWorkRequest.Builder(MmsScanWorker.class)
                        .setConstraints(new Constraints.Builder()
                            .setRequiredNetworkType(NetworkType.CONNECTED).build()).build());
    }

    private static WorkManager manager(Context context) {
        return WorkManager.getInstance(context);
    }

    @NonNull @Override public Result doWork() {
        Context context = getApplicationContext();
        if (!authorized(context)) return Result.success();
        Settings settings = new Settings(context);
        String generation = settings.generation();
        try {
            for (MmsInboxReader.Item item : MmsInboxReader.recent(
                    context.getContentResolver(), settings.mmsSinceMs())) {
                if (!authorized(context) || !generation.equals(new Settings(context).generation()))
                    return Result.success();
                if (!MmsInboxPolicy.canClassify(item.body, item.sender)) continue;
                String fingerprint = Long.toString(item.id);
                synchronized (LOCK) {
                    SharedPreferences prefs = context.getSharedPreferences(LEDGER,
                            Context.MODE_PRIVATE);
                    Set<String> processed = new HashSet<>(
                            prefs.getStringSet("ids", java.util.Collections.emptySet()));
                    if (processed.contains(fingerprint)) continue;
                    Data data = new Data.Builder()
                            .putString("sender", item.sender)
                            .putString("sms", item.body)
                            .putString("source", "mms")
                            .putBoolean("reply_allowed", item.allowReply)
                            .putString("generation", generation)
                            .build();
                    manager(context).enqueue(new OneTimeWorkRequest.Builder(SmsWorker.class)
                            .setInputData(data)
                            .setConstraints(new Constraints.Builder()
                                .setRequiredNetworkType(NetworkType.CONNECTED).build())
                            .build()).getResult().get();
                    if (processed.size() >= 400) processed.clear();
                    processed.add(fingerprint);
                    if (!prefs.edit().putStringSet("ids", processed).commit())
                        return Result.retry();
                }
            }
            return Result.success();
        } catch (SecurityException denied) {
            // Permission may be revoked at any time; no processing or fallback.
            return Result.success();
        } catch (Exception temporary) {
            return Result.retry();
        }
    }
}
