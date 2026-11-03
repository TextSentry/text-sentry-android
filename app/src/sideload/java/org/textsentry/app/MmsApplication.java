package org.textsentry.app;

import android.Manifest;
import android.app.Application;
import android.content.pm.PackageManager;
import android.database.ContentObserver;
import android.os.Handler;
import android.os.Looper;
import android.provider.Telephony;

/** Observe new text-only MMS when user starts unified monitoring and Android allows inbox access. */
public final class MmsApplication extends Application {
    private ContentObserver observer;

    @Override public void onCreate() {
        super.onCreate();
        refreshObserver();
        MmsScanWorker.sync(this);
        GitHubUpdateWorker.schedule(this);
    }

    synchronized void refreshObserver() {
        Settings settings = new Settings(this);
        boolean enabled = SetupGate.canEnterApp(this) && settings.monitoring()
                && settings.mmsEnabled()
                && checkSelfPermission(Manifest.permission.READ_SMS)
                == PackageManager.PERMISSION_GRANTED;
        if (!enabled && observer != null) {
            getContentResolver().unregisterContentObserver(observer);
            observer = null;
        }
        if (enabled && observer == null) {
            ContentObserver next = new ContentObserver(new Handler(Looper.getMainLooper())) {
                @Override public void onChange(boolean selfChange) {
                    MmsScanWorker.scanNow(MmsApplication.this);
                }
            };
            try {
                getContentResolver().registerContentObserver(Telephony.Mms.CONTENT_URI,
                        true, next);
                observer = next;
            } catch (SecurityException ignored) {
                // Installer or platform denied read access; scheduled checks also fail closed.
            }
        }
    }

    @Override public void onTerminate() {
        if (observer != null) getContentResolver().unregisterContentObserver(observer);
        super.onTerminate();
    }
}
