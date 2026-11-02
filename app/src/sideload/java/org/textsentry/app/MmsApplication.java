package org.textsentry.app;

import android.app.Application;
import android.database.ContentObserver;
import android.os.Handler;
import android.os.Looper;
import android.provider.Telephony;

/**
 * React to provider changes while the app process is alive. Periodic WorkManager
 * polling remains the fallback after Android kills the process.
 */
public final class MmsApplication extends Application {
    private ContentObserver observer;

    @Override public void onCreate() {
        super.onCreate();
        observer = new ContentObserver(new Handler(Looper.getMainLooper())) {
            @Override public void onChange(boolean selfChange) {
                MmsScanWorker.scanNow(MmsApplication.this);
            }
        };
        getContentResolver().registerContentObserver(Telephony.Mms.CONTENT_URI,
                true, observer);
        MmsScanWorker.sync(this);
    }

    @Override public void onTerminate() {
        if (observer != null) getContentResolver().unregisterContentObserver(observer);
        super.onTerminate();
    }
}
