package org.textsentry.app;

import android.Manifest;
import android.app.Application;
import android.content.pm.PackageManager;
import android.database.ContentObserver;
import android.os.Handler;
import android.os.Looper;
import android.provider.Telephony;

/** Register MMS provider observer only after separate explicit opt-in and permission. */
public final class MmsApplication extends Application {
    private ContentObserver observer;

    @Override public void onCreate() {
        super.onCreate();
        refreshObserver();
        MmsScanWorker.sync(this);
    }

    synchronized void refreshObserver() {
        boolean enabled = new Settings(this).mmsEnabled()
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
